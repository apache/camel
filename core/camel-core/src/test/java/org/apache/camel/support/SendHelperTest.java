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
package org.apache.camel.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.spi.SendHook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The callback contract of
 * {@link SendHelper#process(Exchange, Endpoint, org.apache.camel.AsyncProcessor, AsyncCallback)} with a
 * {@link SendHook}: the callback is called exactly once on every path, with the value that process returned.
 */
class SendHelperTest extends ContextTestSupport {

    private static final String MODE = "hookMode";
    private static final String KIND = "producerKind";
    private static final String AFTER_COUNT = "afterCount";
    private static final String PRODUCER_COUNT = "producerCount";

    private ExecutorService producerThreads;
    private Endpoint endpoint;

    enum Mode {
        PROCEED,
        CHANGE,
        STOP,
        SKIP,
        DELAY,
        BEFORE_THROWS,
        AFTER_THROWS
    }

    enum Kind {
        SYNC,
        ASYNC,
        THROWS
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        producerThreads = Executors.newFixedThreadPool(4);
        endpoint = context.getEndpoint("mock:target");
        context.getCamelContextExtension().setSendHook(new ModeHook());
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        producerThreads.shutdownNow();
        super.tearDown();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void testSyncProducer(Mode mode) throws Exception {
        assertOneSend(mode, Kind.SYNC, false);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void testAsyncProducer(Mode mode) throws Exception {
        assertOneSend(mode, Kind.ASYNC, false);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void testProducerThrows(Mode mode) throws Exception {
        assertOneSend(mode, Kind.THROWS, false);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void testTransacted(Mode mode) throws Exception {
        assertOneSend(mode, Kind.SYNC, true);
    }

    @Test
    void testTransactedDelayWaitsOnTheCallerThread() {
        Exchange exchange = newExchange(Mode.DELAY, Kind.SYNC);
        exchange.getExchangeExtension().setTransacted(true);
        List<Thread> producerThread = new ArrayList<>();
        Completion completion = new Completion();

        boolean sync = SendHelper.process(exchange, endpoint, new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                producerThread.add(Thread.currentThread());
                callback.done(true);
                return true;
            }
        }, completion);

        assertTrue(sync);
        assertEquals(1, completion.count.get());
        assertEquals(List.of(Thread.currentThread()), producerThread);
    }

    @Test
    void testTransactedDelayInterrupted() {
        Exchange exchange = newExchange(Mode.DELAY, Kind.SYNC);
        exchange.getExchangeExtension().setTransacted(true);
        Completion completion = new Completion();

        // the thread is interrupted, so the wait for the delay ends right away
        Thread.currentThread().interrupt();
        boolean sync;
        boolean interrupted;
        try {
            sync = SendHelper.process(exchange, endpoint, new TestProducer(), completion);
        } finally {
            // the helper keeps the interrupted status of the thread; clear it for the next tests
            interrupted = Thread.interrupted();
        }

        assertTrue(interrupted);
        assertTrue(sync);
        assertEquals(1, completion.count.get());
        assertTrue(completion.doneSync);
        assertInstanceOf(InterruptedException.class, exchange.getException());
        assertEquals(0, count(exchange, PRODUCER_COUNT));
        assertEquals(1, count(exchange, AFTER_COUNT));
    }

    @Test
    void testNoHookCallsTheProducerOnly() {
        context.getCamelContextExtension().setSendHook(null);
        Exchange exchange = newExchange(Mode.STOP, Kind.SYNC);
        Completion completion = new Completion();

        boolean sync = SendHelper.process(exchange, endpoint, new TestProducer(), completion);

        assertTrue(sync);
        assertEquals(1, completion.count.get());
        assertEquals(1, count(exchange, PRODUCER_COUNT));
        assertEquals(0, count(exchange, AFTER_COUNT));
        assertNull(exchange.getException());
    }

    @Test
    void testExchangeThatHasFailedIsSentAsWithoutHook() {
        Exchange exchange = newExchange(Mode.PROCEED, Kind.SYNC);
        IllegalArgumentException failure = new IllegalArgumentException("Failed before the send");
        exchange.setException(failure);
        Completion completion = new Completion();

        boolean sync = SendHelper.process(exchange, endpoint, new TestProducer(), completion);

        // the exception was there before the hook, so the hook did not stop the send
        assertTrue(sync);
        assertEquals(1, completion.count.get());
        assertEquals(1, count(exchange, PRODUCER_COUNT));
        assertEquals(1, count(exchange, AFTER_COUNT));
        assertSame(failure, exchange.getException());
    }

    @Test
    void testDelayRejectedWhenStopping() {
        PluginHelper.getErrorHandlerExecutorService(context).shutdownNow();
        Exchange exchange = newExchange(Mode.DELAY, Kind.SYNC);
        Completion completion = new Completion();

        boolean sync = SendHelper.process(exchange, endpoint, new TestProducer(), completion);

        assertTrue(sync);
        assertEquals(1, completion.count.get());
        assertTrue(completion.doneSync);
        assertInstanceOf(RejectedExecutionException.class, exchange.getException());
        assertEquals(0, count(exchange, PRODUCER_COUNT));
        assertEquals(1, count(exchange, AFTER_COUNT));
    }

    /**
     * Many concurrent sends, each with a random hook outcome and a random producer: every send completes exactly once,
     * with the value process returned, and the after hook runs once for each before hook that returned.
     */
    @Test
    void testStressExactlyOnce() throws Exception {
        int threads = 16;
        int perThread = 500;
        List<Send> sends = new ArrayList<>();
        ExecutorService senders = Executors.newFixedThreadPool(threads);
        try {
            List<Future<List<Send>>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(senders.submit(() -> {
                    List<Send> mine = new ArrayList<>();
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    for (int i = 0; i < perThread; i++) {
                        Mode mode = Mode.values()[random.nextInt(Mode.values().length)];
                        Kind kind = Kind.values()[random.nextInt(Kind.values().length)];
                        Send send = new Send(newExchange(mode, kind), mode, kind);
                        send.start(endpoint, new TestProducer());
                        mine.add(send);
                    }
                    return mine;
                }));
            }
            for (Future<List<Send>> future : futures) {
                sends.addAll(future.get(60, TimeUnit.SECONDS));
            }
        } finally {
            senders.shutdownNow();
        }

        for (Send send : sends) {
            assertTrue(send.completion.latch.await(20, TimeUnit.SECONDS), "send not completed: " + send);
        }
        // stop the threads that complete sends, so a second completion would have happened by now
        producerThreads.shutdown();
        assertTrue(producerThreads.awaitTermination(20, TimeUnit.SECONDS));
        context.stop();

        assertEquals(threads * perThread, sends.size());
        for (Send send : sends) {
            send.assertCompletedOnce();
        }
    }

    private void assertOneSend(Mode mode, Kind kind, boolean transacted) throws Exception {
        Exchange exchange = newExchange(mode, kind);
        exchange.getExchangeExtension().setTransacted(transacted);
        Send send = new Send(exchange, mode, kind);
        send.start(endpoint, new TestProducer());
        assertTrue(send.completion.latch.await(20, TimeUnit.SECONDS));
        producerThreads.shutdown();
        assertTrue(producerThreads.awaitTermination(20, TimeUnit.SECONDS));
        context.stop();
        send.assertCompletedOnce();
        if (transacted) {
            // a transacted exchange is never continued on another thread
            assertTrue(send.returned == null || send.returned, send::toString);
        }
    }

    private Exchange newExchange(Mode mode, Kind kind) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("Hello");
        exchange.setProperty(MODE, mode);
        exchange.setProperty(KIND, kind);
        exchange.setProperty(AFTER_COUNT, new AtomicInteger());
        exchange.setProperty(PRODUCER_COUNT, new AtomicInteger());
        return exchange;
    }

    private static int count(Exchange exchange, String name) {
        return exchange.getProperty(name, AtomicInteger.class).get();
    }

    /**
     * One call of SendHelper.process, done the way the callers of a producer do it.
     */
    private static final class Send {
        private final Exchange exchange;
        private final Mode mode;
        private final Kind kind;
        private final Completion completion = new Completion();
        // null when process threw
        private volatile Boolean returned;
        private volatile int countWhenReturned;
        private volatile RuntimeException thrown;

        private Send(Exchange exchange, Mode mode, Kind kind) {
            this.exchange = exchange;
            this.mode = mode;
            this.kind = kind;
        }

        void start(Endpoint endpoint, TestProducer producer) {
            try {
                boolean sync = SendHelper.process(exchange, endpoint, producer, completion);
                countWhenReturned = completion.count.get();
                returned = sync;
            } catch (RuntimeException e) {
                // the producer threw: the callers then set the exception and complete the send themselves
                thrown = e;
                countWhenReturned = completion.count.get();
                exchange.setException(e);
                completion.done(true);
            }
        }

        void assertCompletedOnce() {
            assertEquals(1, completion.count.get(), this::toString);
            boolean producerCalled = count(exchange, PRODUCER_COUNT) == 1;
            assertTrue(count(exchange, PRODUCER_COUNT) <= 1, this::toString);
            boolean stoppedByHook = mode == Mode.STOP || mode == Mode.SKIP || mode == Mode.BEFORE_THROWS;
            assertEquals(!stoppedByHook, producerCalled, this::toString);
            // the after hook runs once for every before hook that returned
            assertEquals(mode == Mode.BEFORE_THROWS ? 0 : 1, count(exchange, AFTER_COUNT), this::toString);

            if (thrown != null) {
                // only a producer that throws on the caller thread is thrown to the caller, and the callback was not
                // called before
                assertEquals(Kind.THROWS, kind, this::toString);
                assertFalse(mode == Mode.DELAY && !exchange.isTransacted(), this::toString);
                assertEquals(0, countWhenReturned, this::toString);
                assertSame(thrown, exchange.getException(), this::toString);
                return;
            }
            // process returned the value the callback was called with, and a send that returned true was complete
            assertEquals(returned, completion.doneSync, this::toString);
            if (returned) {
                assertEquals(1, countWhenReturned, this::toString);
            }
            if (mode == Mode.DELAY && !exchange.isTransacted()) {
                assertFalse(returned, this::toString);
            }

            switch (mode) {
                case STOP -> assertEquals("Stopped by the hook", exchange.getException().getMessage());
                case BEFORE_THROWS -> assertEquals("Before failed", exchange.getException().getMessage());
                case AFTER_THROWS -> {
                    assertNotNull(exchange.getException(), this::toString);
                    if (kind == Kind.THROWS) {
                        assertEquals("Producer failed", exchange.getException().getMessage());
                        assertEquals("After failed", exchange.getException().getSuppressed()[0].getMessage());
                    } else {
                        assertEquals("After failed", exchange.getException().getMessage());
                    }
                }
                case SKIP -> {
                    assertNull(exchange.getException(), this::toString);
                    assertEquals("Hello", exchange.getMessage().getBody());
                }
                default -> {
                    if (kind == Kind.THROWS) {
                        assertEquals("Producer failed", exchange.getException().getMessage(), this::toString);
                    } else {
                        assertNull(exchange.getException(), this::toString);
                        String expected = mode == Mode.CHANGE ? "Changed reply" : "Reply";
                        assertEquals(expected, exchange.getMessage().getBody(), this::toString);
                    }
                }
            }
        }

        @Override
        public String toString() {
            return "Send[mode=" + mode + ", kind=" + kind + ", transacted=" + exchange.isTransacted() + ", returned="
                   + returned + ", thrown=" + thrown + ", calls=" + completion.count + ", doneSync=" + completion.doneSync
                   + ", exception=" + exchange.getException() + "]";
        }
    }

    private static final class Completion implements AsyncCallback {
        private final AtomicInteger count = new AtomicInteger();
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile boolean doneSync;

        @Override
        public void done(boolean doneSync) {
            this.doneSync = doneSync;
            count.incrementAndGet();
            latch.countDown();
        }
    }

    /**
     * A producer that is synchronous, asynchronous (completes on another thread) or throws, by the exchange.
     */
    private final class TestProducer extends AsyncProcessorSupport {

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            exchange.getProperty(PRODUCER_COUNT, AtomicInteger.class).incrementAndGet();
            Kind kind = exchange.getProperty(KIND, Kind.class);
            switch (kind) {
                case SYNC:
                    reply(exchange);
                    callback.done(true);
                    return true;
                case ASYNC:
                    producerThreads.execute(() -> {
                        reply(exchange);
                        callback.done(false);
                    });
                    return false;
                default:
                    throw new IllegalArgumentException("Producer failed");
            }
        }

        private void reply(Exchange exchange) {
            if ("Changed".equals(exchange.getMessage().getBody())) {
                exchange.getMessage().setBody("Changed reply");
            } else {
                exchange.getMessage().setBody("Reply");
            }
        }
    }

    private static final class ModeHook implements SendHook {

        @Override
        public long beforeSend(Exchange exchange, Endpoint endpoint) {
            Mode mode = exchange.getProperty(MODE, Mode.class);
            switch (mode) {
                case CHANGE:
                    exchange.getMessage().setBody("Changed");
                    return PROCEED;
                case STOP:
                    exchange.setException(new IllegalStateException("Stopped by the hook"));
                    return PROCEED;
                case SKIP:
                    return SKIP;
                case DELAY:
                    return 1 + ThreadLocalRandom.current().nextInt(3);
                case BEFORE_THROWS:
                    throw new IllegalStateException("Before failed");
                default:
                    return PROCEED;
            }
        }

        @Override
        public void afterSend(Exchange exchange, Endpoint endpoint) {
            exchange.getProperty(AFTER_COUNT, AtomicInteger.class).incrementAndGet();
            if (exchange.getProperty(MODE, Mode.class) == Mode.AFTER_THROWS) {
                throw new IllegalStateException("After failed");
            }
        }
    }
}
