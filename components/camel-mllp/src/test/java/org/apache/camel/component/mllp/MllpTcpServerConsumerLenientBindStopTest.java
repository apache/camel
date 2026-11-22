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

import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With lenientBind the consumer binds its port in a background thread, which retries while the port is busy. When the
 * port is freed while the consumer stops, the bind can complete after doStop looked for an accept thread to stop: no
 * accept thread must start after the stop, and the port must be released.
 */
class MllpTcpServerConsumerLenientBindStopTest extends CamelTestSupport {

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();

    // released when the consumer has stopped, or when the test ends (so the bind thread that waits for it never hangs)
    private final CountDownLatch stopped = new CountDownLatch(1);
    private MllpTcpServerConsumer consumer;

    @AfterEach
    void stopConsumer() {
        stopped.countDown();
        if (consumer != null) {
            consumer.stop();
        }
    }

    @Test
    void testBindCompletingDuringStopDoesNotStartAcceptThread() throws Exception {
        CountDownLatch bound = new CountDownLatch(1);
        AtomicReference<ServerSocket> boundSocket = new AtomicReference<>();
        consumer = new MllpTcpServerConsumer(lenientEndpoint(), exchange -> {
        }) {
            @Override
            public void startAcceptThread(ServerSocket serverSocket) {
                // the bind thread has bound the port: let the consumer stop before it starts the accept thread
                boundSocket.set(serverSocket);
                bound.countDown();
                awaitUninterruptibly(stopped);
                super.startAcceptThread(serverSocket);
            }
        };

        try (ServerSocket busy = new ServerSocket(port.getPort())) {
            consumer.start();
            // the port is busy: the bind thread retries
            await().atMost(5, TimeUnit.SECONDS).until(() -> threads("TcpServerBindThread") == 1);
        }

        // the port is free now: the next attempt binds it, and the route stops at the same time
        assertTrue(bound.await(10, TimeUnit.SECONDS), "the bind thread must bind the freed port");
        consumer.stop();
        stopped.countDown();

        await().atMost(10, TimeUnit.SECONDS).until(() -> threads("TcpServerBindThread") == 0);
        assertEquals(0, threads("TcpServerAcceptThread"), "no accept thread must run after the stop");
        assertTrue(boundSocket.get().isClosed(), "the stopped consumer must release its port");
    }

    @Test
    void testStopWhileBindRetriesReleasesPort() throws Exception {
        consumer = new MllpTcpServerConsumer(lenientEndpoint(), exchange -> {
        });

        try (ServerSocket busy = new ServerSocket(port.getPort())) {
            consumer.start();
            await().atMost(5, TimeUnit.SECONDS).until(() -> threads("TcpServerBindThread") == 1);
            consumer.stop();
            await().atMost(10, TimeUnit.SECONDS).until(() -> threads("TcpServerBindThread") == 0);
        }

        assertEquals(0, threads("TcpServerAcceptThread"), "the stopped consumer must not bind the freed port");
    }

    private MllpEndpoint lenientEndpoint() {
        return context.getEndpoint(
                "mllp://localhost:" + port.getPort() + "?lenientBind=true&bindRetryInterval=100&bindTimeout=30000",
                MllpEndpoint.class);
    }

    private long threads(String prefix) {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.isAlive() && t.getName().startsWith(prefix)
                        && t.getName().contains("localhost:" + port.getPort()))
                .count();
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        // doStop interrupts the bind thread while it waits here; keep the interrupt for the code that follows
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
}
