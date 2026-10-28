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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.spi.StepHook;
import org.apache.camel.util.TimeSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The callback contract of {@link StepHelper#process(Exchange, String, org.apache.camel.AsyncProcessor, AsyncCallback)}
 * with a {@link StepHook}: the callback is called exactly once on every path, with the value that process returned.
 * <p/>
 * A test installs a virtual clock in {@link TimeSource}, which is JVM-wide, so these tests run isolated from the other
 * tests.
 */
@Isolated
class StepHelperTest extends ContextTestSupport {

    private static final String NODE_ID = "myStep";
    private static final String MODE = "hookMode";
    private static final String KIND = "processorKind";
    private static final String PROCESSOR_COUNT = "processorCount";
    private static final String FIXED_DELAY = "fixedDelay";
    private static final String RESERVED = "StepHook.beforeStep returned -5: negative values are reserved";

    private ExecutorService processorThreads;

    enum Mode {
        PROCEED,
        NEGATIVE,
        FAIL,
        THROW,
        DELAY
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
        processorThreads = Executors.newFixedThreadPool(4);
        context.getCamelContextExtension().setStepHook(new ModeHook());
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        processorThreads.shutdownNow();
        super.tearDown();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void testSyncProcessor(Mode mode) throws Exception {
        assertOneStep(mode, Kind.SYNC, false);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void testAsyncProcessor(Mode mode) throws Exception {
        assertOneStep(mode, Kind.ASYNC, false);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void testProcessorThrows(Mode mode) throws Exception {
        assertOneStep(mode, Kind.THROWS, false);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void testTransacted(Mode mode) throws Exception {
        assertOneStep(mode, Kind.SYNC, true);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void testSynchronousProcess(Mode mode) throws Exception {
        Exchange exchange = newExchange(mode, Kind.SYNC);
        List<Thread> processorThread = new ArrayList<>();

        StepHelper.process(exchange, NODE_ID, e -> {
            processorThread.add(Thread.currentThread());
            e.getMessage().setBody("Reply");
        });

        switch (mode) {
            case FAIL -> assertEquals("Failed by the hook", exchange.getException().getMessage());
            case THROW -> assertEquals("Hook failed", exchange.getException().getMessage());
            case NEGATIVE -> assertEquals(RESERVED, exchange.getException().getMessage());
            default -> {
                // a delay waits on this thread
                assertNull(exchange.getException());
                assertEquals("Reply", exchange.getMessage().getBody());
                assertEquals(List.of(Thread.currentThread()), processorThread);
            }
        }
        boolean failed = mode == Mode.FAIL || mode == Mode.THROW || mode == Mode.NEGATIVE;
        assertEquals(failed ? 0 : 1, processorThread.size());
    }

    @Test
    void testSynchronousProcessorThrows() {
        Exchange exchange = newExchange(Mode.PROCEED, Kind.THROWS);
        Exception e = assertThrows(IllegalArgumentException.class,
                () -> StepHelper.process(exchange, NODE_ID, ex -> {
                    throw new IllegalArgumentException("Processor failed");
                }));
        assertEquals("Processor failed", e.getMessage());
    }

    @Test
    void testSynchronousDelayInterrupted() throws Exception {
        Exchange exchange = newExchange(Mode.DELAY, Kind.SYNC);
        AtomicInteger calls = new AtomicInteger();

        Thread.currentThread().interrupt();
        boolean interrupted;
        try {
            StepHelper.process(exchange, NODE_ID, e -> calls.incrementAndGet());
        } finally {
            interrupted = Thread.interrupted();
        }

        // the exchange is marked as interrupted, so Camel stops routing it
        assertTrue(interrupted);
        assertInstanceOf(InterruptedException.class, exchange.getException());
        assertTrue(exchange.getExchangeExtension().isInterrupted());
        assertEquals(0, calls.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void testDelayWaitsOnTheCallerThreadThroughTimeSource(boolean transacted) {
        Exchange exchange = newExchange(Mode.DELAY, Kind.SYNC);
        exchange.setProperty(FIXED_DELAY, 2500L);
        exchange.getExchangeExtension().setTransacted(transacted);
        List<Thread> processorThread = new ArrayList<>();
        Completion completion = new Completion();
        VirtualClock clock = new VirtualClock();

        TimeSource.Provider previous = TimeSource.install(clock);
        boolean sync;
        try {
            sync = StepHelper.process(exchange, NODE_ID, new AsyncProcessorSupport() {
                @Override
                public boolean process(Exchange exchange, AsyncCallback callback) {
                    processorThread.add(Thread.currentThread());
                    callback.done(true);
                    return true;
                }
            }, completion);
        } finally {
            TimeSource.install(previous);
        }

        // this thread waited for the delay through TimeSource (in virtual time here), and then called the processor
        assertEquals(List.of(Thread.currentThread()), clock.sleepers);
        assertEquals(List.of(2500L), clock.sleeps);
        assertEquals(List.of(Thread.currentThread()), processorThread);
        assertTrue(sync);
        assertEquals(1, completion.count.get());
        assertTrue(completion.doneSync);
        assertNull(exchange.getException());
    }

    @Test
    void testDelayInterrupted() {
        Exchange exchange = newExchange(Mode.DELAY, Kind.SYNC);
        Completion completion = new Completion();

        // the thread is interrupted, so the wait for the delay ends right away
        Thread.currentThread().interrupt();
        boolean sync;
        boolean interrupted;
        try {
            sync = StepHelper.process(exchange, NODE_ID, new TestProcessor(), completion);
        } finally {
            // the helper keeps the interrupted status of the thread; clear it for the next tests
            interrupted = Thread.interrupted();
        }

        // the exchange is marked as interrupted, so Camel stops routing it
        assertTrue(interrupted);
        assertTrue(sync);
        assertEquals(1, completion.count.get());
        assertTrue(completion.doneSync);
        assertInstanceOf(InterruptedException.class, exchange.getException());
        assertTrue(exchange.getExchangeExtension().isInterrupted());
        assertEquals(0, count(exchange));
    }

    @Test
    void testFailingClockFailsTheStep() {
        Exchange exchange = newExchange(Mode.DELAY, Kind.SYNC);
        Completion completion = new Completion();
        IllegalStateException failure = new IllegalStateException("Clock failed");
        VirtualClock clock = new VirtualClock();
        clock.failure = failure;

        TimeSource.Provider previous = TimeSource.install(clock);
        boolean sync;
        try {
            sync = StepHelper.process(exchange, NODE_ID, new TestProcessor(), completion);
        } finally {
            TimeSource.install(previous);
        }

        assertTrue(sync);
        assertEquals(1, completion.count.get());
        assertTrue(completion.doneSync);
        assertSame(failure, exchange.getException());
        assertEquals(0, count(exchange));
    }

    @Test
    void testReservedValueFailsTheStep() {
        Exchange exchange = newExchange(Mode.NEGATIVE, Kind.SYNC);
        Completion completion = new Completion();

        boolean sync = StepHelper.process(exchange, NODE_ID, new TestProcessor(), completion);

        assertTrue(sync);
        assertEquals(1, completion.count.get());
        assertTrue(completion.doneSync);
        assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertEquals(RESERVED, exchange.getException().getMessage());
        assertEquals(0, count(exchange));
    }

    @Test
    void testNoHookCallsTheProcessorOnly() {
        context.getCamelContextExtension().setStepHook(null);
        Exchange exchange = newExchange(Mode.FAIL, Kind.SYNC);
        Completion completion = new Completion();

        boolean sync = StepHelper.process(exchange, NODE_ID, new TestProcessor(), completion);

        assertTrue(sync);
        assertEquals(1, completion.count.get());
        assertEquals(1, count(exchange));
        assertNull(exchange.getException());
    }

    @Test
    void testExchangeThatHasFailedIsProcessedAsWithoutHook() {
        Exchange exchange = newExchange(Mode.PROCEED, Kind.SYNC);
        IllegalArgumentException failure = new IllegalArgumentException("Failed before the step");
        exchange.setException(failure);
        Completion completion = new Completion();

        boolean sync = StepHelper.process(exchange, NODE_ID, new TestProcessor(), completion);

        // the exception was there before the hook, so the hook did not fail the step
        assertTrue(sync);
        assertEquals(1, completion.count.get());
        assertEquals(1, count(exchange));
        assertSame(failure, exchange.getException());
    }

    /**
     * Many concurrent steps, each with a random hook outcome and a random processor: every step completes exactly once,
     * with the value process returned.
     */
    @Test
    void testStressExactlyOnce() throws Exception {
        int threads = 16;
        int perThread = 500;
        List<Step> steps = new ArrayList<>();
        ExecutorService callers = Executors.newFixedThreadPool(threads);
        try {
            List<Future<List<Step>>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(callers.submit(() -> {
                    List<Step> mine = new ArrayList<>();
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    for (int i = 0; i < perThread; i++) {
                        Mode mode = Mode.values()[random.nextInt(Mode.values().length)];
                        Kind kind = Kind.values()[random.nextInt(Kind.values().length)];
                        Step step = new Step(newExchange(mode, kind), mode, kind);
                        step.start(new TestProcessor());
                        mine.add(step);
                    }
                    return mine;
                }));
            }
            for (Future<List<Step>> future : futures) {
                steps.addAll(future.get(60, TimeUnit.SECONDS));
            }
        } finally {
            callers.shutdownNow();
        }

        for (Step step : steps) {
            assertTrue(step.completion.latch.await(20, TimeUnit.SECONDS), "step not completed: " + step);
        }
        // stop the threads that complete steps, so a second completion would have happened by now
        processorThreads.shutdown();
        assertTrue(processorThreads.awaitTermination(20, TimeUnit.SECONDS));
        context.stop();

        assertEquals(threads * perThread, steps.size());
        for (Step step : steps) {
            step.assertCompletedOnce();
        }
    }

    private void assertOneStep(Mode mode, Kind kind, boolean transacted) throws Exception {
        Exchange exchange = newExchange(mode, kind);
        exchange.getExchangeExtension().setTransacted(transacted);
        Step step = new Step(exchange, mode, kind);
        step.start(new TestProcessor());
        assertTrue(step.completion.latch.await(20, TimeUnit.SECONDS));
        processorThreads.shutdown();
        assertTrue(processorThreads.awaitTermination(20, TimeUnit.SECONDS));
        context.stop();
        step.assertCompletedOnce();
        if (transacted) {
            // a transacted exchange (with a synchronous processor) completes on the thread that called the step
            assertTrue(step.returned == null || step.returned, step::toString);
        }
    }

    private Exchange newExchange(Mode mode, Kind kind) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("Hello");
        exchange.setProperty(MODE, mode);
        exchange.setProperty(KIND, kind);
        exchange.setProperty(PROCESSOR_COUNT, new AtomicInteger());
        return exchange;
    }

    private static int count(Exchange exchange) {
        return exchange.getProperty(PROCESSOR_COUNT, AtomicInteger.class).get();
    }

    /**
     * One call of StepHelper.process, done the way the callers of a step do it.
     */
    private static final class Step {
        private final Exchange exchange;
        private final Mode mode;
        private final Kind kind;
        private final Completion completion = new Completion();
        // null when process threw
        private volatile Boolean returned;
        private volatile int countWhenReturned;
        private volatile RuntimeException thrown;

        private Step(Exchange exchange, Mode mode, Kind kind) {
            this.exchange = exchange;
            this.mode = mode;
            this.kind = kind;
        }

        void start(TestProcessor processor) {
            try {
                boolean sync = StepHelper.process(exchange, NODE_ID, processor, completion);
                countWhenReturned = completion.count.get();
                returned = sync;
            } catch (RuntimeException e) {
                // the processor threw: the callers then set the exception and complete the step themselves
                thrown = e;
                countWhenReturned = completion.count.get();
                exchange.setException(e);
                completion.done(true);
            }
        }

        void assertCompletedOnce() {
            assertEquals(1, completion.count.get(), this::toString);
            boolean failedByHook = mode == Mode.FAIL || mode == Mode.THROW || mode == Mode.NEGATIVE;
            assertEquals(failedByHook ? 0 : 1, count(exchange), this::toString);

            if (thrown != null) {
                // only a processor that throws on the caller thread is thrown to the caller, and the callback was not
                // called before
                assertEquals(Kind.THROWS, kind, this::toString);
                assertEquals(0, countWhenReturned, this::toString);
                assertSame(thrown, exchange.getException(), this::toString);
                return;
            }
            // process returned the value the callback was called with, and a step that returned true was complete
            assertEquals(returned, completion.doneSync, this::toString);
            if (returned) {
                assertEquals(1, countWhenReturned, this::toString);
            }

            switch (mode) {
                case FAIL -> assertEquals("Failed by the hook", exchange.getException().getMessage(), this::toString);
                case THROW -> assertEquals("Hook failed", exchange.getException().getMessage(), this::toString);
                case NEGATIVE -> assertEquals(RESERVED, exchange.getException().getMessage(), this::toString);
                default -> {
                    if (kind == Kind.THROWS) {
                        assertEquals("Processor failed", exchange.getException().getMessage(), this::toString);
                    } else {
                        assertNull(exchange.getException(), this::toString);
                        assertEquals("Reply", exchange.getMessage().getBody(), this::toString);
                    }
                }
            }
        }

        @Override
        public String toString() {
            return "Step[mode=" + mode + ", kind=" + kind + ", transacted=" + exchange.isTransacted() + ", returned="
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
     * A processor that is synchronous, asynchronous (completes on another thread) or throws, by the exchange.
     */
    private final class TestProcessor extends AsyncProcessorSupport {

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            exchange.getProperty(PROCESSOR_COUNT, AtomicInteger.class).incrementAndGet();
            Kind kind = exchange.getProperty(KIND, Kind.class);
            switch (kind) {
                case SYNC:
                    exchange.getMessage().setBody("Reply");
                    callback.done(true);
                    return true;
                case ASYNC:
                    processorThreads.execute(() -> {
                        exchange.getMessage().setBody("Reply");
                        callback.done(false);
                    });
                    return false;
                default:
                    throw new IllegalArgumentException("Processor failed");
            }
        }
    }

    /**
     * A clock that records the sleeps of the test thread, and the threads that sleep, without waiting. Any other thread
     * waits as usual.
     */
    private static final class VirtualClock implements TimeSource.Provider {
        private final Thread testThread = Thread.currentThread();
        private final List<Long> sleeps = new CopyOnWriteArrayList<>();
        private final List<Thread> sleepers = new CopyOnWriteArrayList<>();
        // thrown by the sleep of the test thread, when set
        private volatile RuntimeException failure;

        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }

        @Override
        public long nanoTime() {
            return System.nanoTime();
        }

        @Override
        public void sleep(long millis) throws InterruptedException {
            if (Thread.currentThread() != testThread) {
                new CountDownLatch(1).await(millis, TimeUnit.MILLISECONDS);
                return;
            }
            sleeps.add(millis);
            sleepers.add(Thread.currentThread());
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static final class ModeHook implements StepHook {

        @Override
        public long beforeStep(Exchange exchange, String nodeId) {
            assertEquals(NODE_ID, nodeId);
            Mode mode = exchange.getProperty(MODE, Mode.class);
            switch (mode) {
                case NEGATIVE:
                    return -5;
                case FAIL:
                    exchange.setException(new IllegalStateException("Failed by the hook"));
                    return PROCEED;
                case THROW:
                    throw new IllegalStateException("Hook failed");
                case DELAY:
                    Long fixed = exchange.getProperty(FIXED_DELAY, Long.class);
                    return fixed != null ? fixed : 1 + ThreadLocalRandom.current().nextInt(3);
                default:
                    return PROCEED;
            }
        }
    }
}
