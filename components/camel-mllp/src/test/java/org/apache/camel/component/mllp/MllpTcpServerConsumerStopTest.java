/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.mllp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Processor;
import org.apache.camel.component.mllp.internal.MllpSocketBuffer;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A connection that the consumer accepted, and was still validating (its first read) when the consumer stopped, must
 * not be served after the stop, nor next to the connections of the next start. A route stop, or a suspend and resume by
 * a route policy, stops and starts the consumer, as the tests do.
 */
class MllpTcpServerConsumerStopTest extends CamelTestSupport {

    static final String MESSAGE = "MSH|^~\\&|ADT|EPIC|JCAPS|CC|20160902123950|RISTECH|ADT^A08|00001|D|2.3|||||||\r";

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();

    // when set, the next connection that ends its validation read waits for this latch before startConsumer runs
    private final AtomicReference<CountDownLatch> holdNextValidated = new AtomicReference<>();
    private final CountDownLatch validated = new CountDownLatch(1);
    private MllpTcpServerConsumer consumer;

    @AfterEach
    void stopConsumer() {
        CountDownLatch hold = holdNextValidated.getAndSet(null);
        if (hold != null) {
            hold.countDown();
        }
        if (consumer != null) {
            consumer.shutdown();
        }
    }

    @Test
    void testConnectionValidatedDuringStopIsNotServed() throws Exception {
        consumer = new HeldConsumer(endpoint(), exchange -> {
        });
        consumer.start();
        CountDownLatch release = new CountDownLatch(1);
        try (Socket client = connectAndHoldAfterValidation(release)) {
            consumer.stop();
            release.countDown();

            // the validation read ended before the stop, and startConsumer runs after it: the connection is reset
            await().atMost(10, TimeUnit.SECONDS).until(() -> threads("TcpServerConsumerValidationRunnable") == 0);
            assertEquals(0, threads("TcpSocketConsumerRunnable"));
            assertTrue(consumer.getConsumerRunnables().isEmpty());
            assertEquals("", sendAndReadAck(client), "the stopped consumer must not answer");
        }
    }

    @Test
    void testConnectionValidatedDuringStopIsNotServedAfterRestart() throws Exception {
        consumer = new HeldConsumer(endpoint(), exchange -> {
        });
        consumer.start();
        CountDownLatch release = new CountDownLatch(1);
        try (Socket client1 = connectAndHoldAfterValidation(release)) {
            consumer.stop();
            release.countDown();
            await().atMost(10, TimeUnit.SECONDS).until(() -> threads("TcpServerConsumerValidationRunnable") == 0);
            consumer.start();

            // a new connection is served after the restart, and the old one is not (maxConcurrentConsumers=1)
            try (Socket client2 = new Socket("localhost", port.getPort())) {
                assertTrue(sendAndReadAck(client2).contains("MSA|AA"));
                assertEquals("", sendAndReadAck(client1), "the connection of the previous start must not be served");
                assertEquals(1, threads("TcpSocketConsumerRunnable"));
            }
        }
    }

    @Test
    void testConnectionValidatedDuringRestartIsNotServedAfterRestart() throws Exception {
        consumer = new HeldConsumer(endpoint(), exchange -> {
        });
        consumer.start();
        CountDownLatch release = new CountDownLatch(1);
        try (Socket client1 = connectAndHoldAfterValidation(release)) {
            // the consumer stops and starts again (such as a suspend and resume by a route policy) before the
            // connection of the previous start reaches startConsumer, so the consumer is run-allowed again by then
            consumer.stop();
            consumer.start();
            release.countDown();
            await().atMost(10, TimeUnit.SECONDS).until(() -> threads("TcpServerConsumerValidationRunnable") == 0);

            // it is not served next to the connections of the new start, nor counted against its maxConcurrentConsumers
            assertEquals(0, threads("TcpSocketConsumerRunnable"));
            assertTrue(consumer.getConsumerRunnables().isEmpty());
            try (Socket client2 = new Socket("localhost", port.getPort())) {
                assertTrue(sendAndReadAck(client2).contains("MSA|AA"));
                assertEquals("", sendAndReadAck(client1), "the connection of the previous start must not be served");
                assertEquals(1, threads("TcpSocketConsumerRunnable"));
            }
        }
    }

    @Test
    void testConnectionServedBeforeStopEndsWithinReceiveTimeout() throws Exception {
        consumer = new HeldConsumer(endpoint(), exchange -> {
        });
        consumer.start();
        try (Socket client = new Socket("localhost", port.getPort())) {
            assertTrue(sendAndReadAck(client).contains("MSA|AA"));
            assertEquals(1, threads("TcpSocketConsumerRunnable"));

            consumer.stop();

            await().atMost(10, TimeUnit.SECONDS).until(() -> threads("TcpSocketConsumerRunnable") == 0);
        }
    }

    @Test
    void testShutdownInterruptsTheRunnableServingAConnection() throws Exception {
        CountDownLatch processing = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        consumer = new HeldConsumer(endpoint(), exchange -> {
            processing.countDown();
            try {
                Thread.sleep(20000);
            } catch (InterruptedException e) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
        });
        consumer.start();
        try (Socket client = new Socket("localhost", port.getPort())) {
            send(client);
            assertTrue(processing.await(10, TimeUnit.SECONDS), "the message must be processed");

            // the stop shuts the executors down without interrupting the runnables, the shutdown must interrupt them
            // (with virtual threads, the interrupt also closes the socket of a connection blocked in its read)
            consumer.stop();
            consumer.shutdown();
            assertTrue(interrupted.await(5, TimeUnit.SECONDS),
                    "the shutdown must interrupt the runnable that serves the connection");
        }
    }

    private MllpEndpoint endpoint() {
        return context.getEndpoint(
                "mllp://localhost:" + port.getPort()
                                   + "?maxConcurrentConsumers=1&reuseAddress=true&receiveTimeout=2000&autoAck=true",
                MllpEndpoint.class);
    }

    /**
     * Connects without sending: the accept thread hands the socket to a validation runnable, whose read (500 ms) ends
     * without data. It then waits for the latch before it calls startConsumer, so the test can stop (and start) the
     * consumer in between, standing in for a stop that lands during the validation read.
     */
    private Socket connectAndHoldAfterValidation(CountDownLatch release) throws Exception {
        holdNextValidated.set(release);
        Socket client = new Socket("localhost", port.getPort());
        assertTrue(validated.await(10, TimeUnit.SECONDS), "the validation read must end");
        return client;
    }

    private final class HeldConsumer extends MllpTcpServerConsumer {

        HeldConsumer(MllpEndpoint endpoint, Processor processor) {
            super(endpoint, processor);
        }

        @Override
        public void startConsumer(Socket clientSocket, MllpSocketBuffer mllpBuffer, ExecutorService executor) {
            CountDownLatch hold = holdNextValidated.getAndSet(null);
            if (hold != null) {
                validated.countDown();
                awaitUninterruptibly(hold);
            }
            super.startConsumer(clientSocket, mllpBuffer, executor);
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        // the shutdown of the validation executor at the end of a test interrupts the thread that waits here
        boolean interrupted = false;
        while (true) {
            try {
                if (latch.await(10, TimeUnit.SECONDS)) {
                    break;
                }
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static ConditionFactory await() {
        // the tests of the module run in parallel: an exception in a thread of another test must not fail the wait
        return Awaitility.await().dontCatchUncaughtExceptions();
    }

    private long threads(String prefix) {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.isAlive() && t.getName().startsWith(prefix)
                        && t.getName().contains(":" + port.getPort() + "]"))
                .count();
    }

    /**
     * Sends a message and returns the acknowledgement, or an empty string when the connection is closed or reset.
     */
    private static String sendAndReadAck(Socket socket) throws IOException {
        ByteArrayOutputStream ack = new ByteArrayOutputStream();
        try {
            send(socket);
            socket.setSoTimeout(5000);
            InputStream in = socket.getInputStream();
            int b;
            while ((b = in.read()) != -1 && b != 0x1c) {
                if (b != 0x0b) {
                    ack.write(b);
                }
            }
        } catch (IOException e) {
            // reset by the consumer
            return "";
        }
        return ack.toString(StandardCharsets.ISO_8859_1);
    }

    private static void send(Socket socket) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write(0x0b);
        out.write(MESSAGE.getBytes(StandardCharsets.ISO_8859_1));
        out.write(0x1c);
        out.write(0x0d);
        out.flush();
    }
}
