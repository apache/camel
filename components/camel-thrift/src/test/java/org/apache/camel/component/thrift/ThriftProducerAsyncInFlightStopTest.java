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
package org.apache.camel.component.thrift;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stopping the client manager of the asynchronous producer ends its selector thread without completing the calls it
 * still has: the producer must fail their exchanges when it stops, else they never complete.
 */
class ThriftProducerAsyncInFlightStopTest extends CamelTestSupport {

    @Test
    void stoppingTheProducerFailsAnInFlightCall() throws Exception {
        CountDownLatch requestReceived = new CountDownLatch(1);
        try (ServerSocket server = new ServerSocket(0)) {
            startSilentServer(server, requestReceived);

            String uri = pingUri(server.getLocalPort());
            ThriftProducer producer = (ThriftProducer) context.getEndpoint(uri).createProducer();
            producer.start();
            try {
                Exchange exchange = context.getEndpoint(uri).createExchange();
                CountDownLatch done = new CountDownLatch(1);
                producer.process(exchange, doneSync -> done.countDown());
                assertTrue(requestReceived.await(10, TimeUnit.SECONDS), "the call must be sent");
                assertEquals(1, producer.getPendingCallbackCount());

                producer.stop();

                assertTrue(done.await(10, TimeUnit.SECONDS), "the exchange of the call must complete");
                assertStoppedBeforeTheCallCompleted(exchange);
                assertEquals(0, producer.getPendingCallbackCount());
            } finally {
                producer.stop();
            }
        }
    }

    @Test
    void aContinuationThatThrowsDoesNotKeepTheSelectorThreadRunning() throws Exception {
        CountDownLatch requestReceived = new CountDownLatch(1);
        try (ServerSocket server = new ServerSocket(0)) {
            startSilentServer(server, requestReceived);

            String uri = pingUri(server.getLocalPort());
            ThriftProducer producer = (ThriftProducer) context.getEndpoint(uri).createProducer();
            Set<Thread> before = Thread.getAllStackTraces().keySet();
            producer.start();
            try {
                List<Thread> selectors = Thread.getAllStackTraces().keySet().stream()
                        .filter(t -> !before.contains(t) && t.getName().startsWith("TAsyncClientManager#SelectorThread"))
                        .toList();
                assertEquals(1, selectors.size(), "the selector thread of the producer");

                Exchange exchange = context.getEndpoint(uri).createExchange();
                CountDownLatch done = new CountDownLatch(1);
                producer.process(exchange, doneSync -> {
                    done.countDown();
                    throw new IllegalStateException("the continuation of the exchange fails");
                });
                assertTrue(requestReceived.await(10, TimeUnit.SECONDS), "the call must be sent");

                // the continuation runs on the stopping thread and throws
                assertDoesNotThrow(producer::stop);

                assertTrue(done.await(10, TimeUnit.SECONDS), "the exchange of the call must complete");
                assertStoppedBeforeTheCallCompleted(exchange);
                assertEquals(0, producer.getPendingCallbackCount());
                selectors.get(0).join(TimeUnit.SECONDS.toMillis(10));
                assertFalse(selectors.get(0).isAlive(), "the selector thread must end");
            } finally {
                producer.stop();
            }
        }
    }

    @Test
    void aCompletedCallIsNotPending() throws Exception {
        // control: the callback of a call that completed (here with an error: nothing listens) is not kept
        int port;
        try (ServerSocket closed = new ServerSocket(0)) {
            port = closed.getLocalPort();
        }
        String uri = pingUri(port);
        ThriftProducer producer = (ThriftProducer) context.getEndpoint(uri).createProducer();
        producer.start();
        try {
            Exchange exchange = context.getEndpoint(uri).createExchange();
            CountDownLatch done = new CountDownLatch(1);
            producer.process(exchange, doneSync -> done.countDown());

            assertTrue(done.await(10, TimeUnit.SECONDS));
            assertTrue(exchange.isFailed());
            assertEquals(0, producer.getPendingCallbackCount());
        } finally {
            producer.stop();
        }
    }

    @Test
    void aCallThatFailsToStartWhileTheProducerStopsIsCompletedOnce() throws Exception {
        ThriftProducer producer = startProducerOnClosedPort();
        try {
            // the stop runs between the registration of the call and the failure to start it: it completes the
            // exchange (asynchronously), so the failure must not complete it again
            setClient(producer, new StoppingClient(producer));
            Exchange exchange = producer.getEndpoint().createExchange();
            List<Boolean> done = new CopyOnWriteArrayList<>();

            boolean sync = producer.process(exchange, done::add);

            assertEquals(List.of(false), done, "the exchange must complete once, asynchronously");
            assertFalse(sync, "process must return the doneSync passed to the callback");
            assertStoppedBeforeTheCallCompleted(exchange);
            assertEquals(0, producer.getPendingCallbackCount());
        } finally {
            producer.stop();
        }
    }

    @Test
    void aCallThatFailsToStartIsCompletedSynchronously() throws Exception {
        // control: without a stop, the failure to start the call completes the exchange synchronously
        ThriftProducer producer = startProducerOnClosedPort();
        try {
            setClient(producer, new FailingClient());
            Exchange exchange = producer.getEndpoint().createExchange();
            List<Boolean> done = new CopyOnWriteArrayList<>();

            boolean sync = producer.process(exchange, done::add);

            assertEquals(List.of(true), done);
            assertTrue(sync);
            assertInstanceOf(TException.class, exchange.getException());
            assertEquals(0, producer.getPendingCallbackCount());
        } finally {
            producer.stop();
        }
    }

    @Test
    void aCallAcceptedAfterTheProducerFailedItsPendingCallsIsFailed() throws Exception {
        aCallTheStopDoesNotSeeIsFailed(false);
    }

    @Test
    void aCallAcceptedAfterTheProducerFailedItsPendingCallsIsFailedAlsoWhenTheProducerStartsAgain() throws Exception {
        aCallTheStopDoesNotSeeIsFailed(true);
    }

    private void aCallTheStopDoesNotSeeIsFailed(boolean startAgain) throws Exception {
        ThriftProducer producer = startProducerOnClosedPort();
        try {
            // the stop fails the pending calls while this call is being registered, so it does not see it, and a
            // selector thread that is still ending accepts the call and never completes it
            setClient(producer, new UnseenByTheStopClient(producer, startAgain));
            Exchange exchange = producer.getEndpoint().createExchange();
            List<Boolean> done = new CopyOnWriteArrayList<>();

            boolean sync = producer.process(exchange, done::add);

            assertEquals(List.of(false), done, "the exchange must complete once");
            assertFalse(sync);
            assertStoppedBeforeTheCallCompleted(exchange);
            assertEquals(0, producer.getPendingCallbackCount());
        } finally {
            producer.stop();
        }
    }

    @Test
    void aCallToAStoppedProducerIsFailed() throws Exception {
        ThriftProducer producer = startProducerOnClosedPort();
        producer.stop();

        Exchange exchange = producer.getEndpoint().createExchange();
        List<Boolean> done = new CopyOnWriteArrayList<>();
        boolean sync = producer.process(exchange, done::add);

        assertEquals(List.of(false), done, "the exchange must complete once");
        assertFalse(sync);
        assertStoppedBeforeTheCallCompleted(exchange);
        assertEquals(0, producer.getPendingCallbackCount());
    }

    private ThriftProducer startProducerOnClosedPort() throws Exception {
        int port;
        try (ServerSocket closed = new ServerSocket(0)) {
            port = closed.getLocalPort();
        }
        ThriftProducer producer = (ThriftProducer) context.getEndpoint(pingUri(port)).createProducer();
        producer.start();
        return producer;
    }

    private static void setClient(ThriftProducer producer, Object client) throws Exception {
        Field field = ThriftProducer.class.getDeclaredField("thriftClient");
        field.setAccessible(true);
        field.set(producer, client);
    }

    // stops the producer, then fails to start the call as a stopped client manager does
    public static class StoppingClient {
        private final ThriftProducer producer;

        StoppingClient(ThriftProducer producer) {
            this.producer = producer;
        }

        public void ping(AsyncMethodCallback<Void> resultHandler) throws TException {
            producer.stop();
            throw new TException("SelectThread is not running");
        }
    }

    public static class FailingClient {
        public void ping(AsyncMethodCallback<Void> resultHandler) throws TException {
            throw new TException("SelectThread is not running");
        }
    }

    // accepts the call and never completes it, while the producer stops without seeing the call (and may start again)
    public static class UnseenByTheStopClient {
        private final ThriftProducer producer;
        private final boolean startAgain;

        UnseenByTheStopClient(ThriftProducer producer, boolean startAgain) {
            this.producer = producer;
            this.startAgain = startAgain;
        }

        public void ping(AsyncMethodCallback<Void> resultHandler) throws Exception {
            // the stop fails the pending calls before this one is registered
            Field field = ThriftProducer.class.getDeclaredField("pendingCallbacks");
            field.setAccessible(true);
            ((Set<?>) field.get(producer)).clear();
            producer.stop();
            if (startAgain) {
                producer.start();
            }
        }
    }

    private static String pingUri(int port) {
        return "thrift://localhost:" + port + "/org.apache.camel.component.thrift.generated.Calculator?method=ping";
    }

    // a server that reads the request and never answers
    private static void startSilentServer(ServerSocket server, CountDownLatch requestReceived) {
        Thread silent = new Thread(() -> {
            try (Socket socket = server.accept(); InputStream in = socket.getInputStream()) {
                if (in.read() != -1) {
                    requestReceived.countDown();
                }
                while (in.read() != -1) {
                    // keep the connection open until the client closes it
                }
            } catch (Exception e) {
                // the test ends
            }
        });
        silent.setDaemon(true);
        silent.start();
    }

    private static void assertStoppedBeforeTheCallCompleted(Exchange exchange) {
        assertInstanceOf(CamelExchangeException.class, exchange.getException());
        assertTrue(exchange.getException().getMessage().startsWith(
                "The Thrift producer was stopped before the call of method ping completed"),
                exchange.getException().getMessage());
    }
}
