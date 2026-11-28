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
package org.apache.camel.impl;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.engine.DefaultAsyncProcessorAwaitManager;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An interrupt of the await manager (JMX, or the stop of the CamelContext) races with the callback of the processor it
 * waits for, and with other threads that wait for the same exchange.
 */
class DefaultAsyncProcessorAwaitManagerInterruptRaceTest {

    // threads also start threads
    private final List<Thread> threads = new CopyOnWriteArrayList<>();
    private final List<ControlledProcessor> processors = new ArrayList<>();
    private CamelContext context;
    private DefaultAsyncProcessorAwaitManager manager;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
        context.start();
        manager = new DefaultAsyncProcessorAwaitManager();
        manager.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        // the callbacks that did not fire yet release the threads still waiting
        for (ControlledProcessor processor : processors) {
            processor.complete();
        }
        for (Thread thread : threads) {
            thread.join(TimeUnit.SECONDS.toMillis(10));
        }
        manager.stop();
        context.stop();
    }

    @Test
    void interruptLeavesAnExchangeAloneWhoseCallbackFiredWhileItLoggedTheBlockedThread() throws Exception {
        Gate gate = new Gate();
        Exchange exchange = gate.attach(new DefaultExchange(context));
        ControlledProcessor processor = processor(null);
        AtomicReference<Exception> exceptionAtReturn = new AtomicReference<>();
        Thread waiter = thread("waiter", () -> {
            manager.process(processor, exchange);
            exceptionAtReturn.set(exchange.getException());
        });
        await().atMost(10, TimeUnit.SECONDS).until(() -> manager.size() == 1);

        // the interrupt is making the WARN line of the blocked thread when the callback fires
        Thread interrupter = new Thread(() -> manager.interrupt(exchange), "interrupter");
        gate.gatedThread = interrupter;
        interrupter.start();
        assertTrue(gate.inside.await(10, TimeUnit.SECONDS), "the interrupt reads the route of the blocked thread");
        processor.complete();
        waiter.join(TimeUnit.SECONDS.toMillis(10));
        assertFalse(waiter.isAlive(), "the waiter returned once the callback fired");
        assertNull(exceptionAtReturn.get(), "the waiter returned the exchange as done");

        gate.proceed.countDown();
        interrupter.join(TimeUnit.SECONDS.toMillis(10));
        // on main the exchange that the caller already goes on with was failed and marked interrupted
        assertNull(exchange.getException());
        assertFalse(exchange.getExchangeExtension().isInterrupted());
    }

    @Test
    void twoThreadsWaitingForTheSameExchangeAreBothListed() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        ControlledProcessor inner = processor(null);
        AtomicReference<Thread> nested = new AtomicReference<>();
        // the processor goes on with the exchange on another thread, which calls a step that waits for it too (such as
        // ProducerTemplate.send(uri, exchange))
        ControlledProcessor outer = processor(() -> {
            nested.set(Thread.currentThread());
            await().atMost(10, TimeUnit.SECONDS).until(() -> manager.size() == 1);
            manager.process(inner, exchange);
        });
        Thread waiter = thread("waiter", () -> manager.process(outer, exchange));
        assertTrue(inner.called.await(10, TimeUnit.SECONDS));
        await().atMost(10, TimeUnit.SECONDS).until(() -> waiter.getState() == Thread.State.WAITING
                && nested.get().getState() == Thread.State.WAITING);

        // on main the second thread replaced the entry of the first
        assertEquals(2, manager.size(), "both blocked threads are listed");

        inner.complete();
        nested.get().join(TimeUnit.SECONDS.toMillis(10));
        // on main the second thread removed the entry of the first when it returned
        assertEquals(1, manager.size(), "the thread still blocked stays listed");
        assertEquals(waiter, manager.browse().iterator().next().getBlockedThread());

        manager.interrupt(exchange);
        waiter.join(TimeUnit.SECONDS.toMillis(10));
        assertFalse(waiter.isAlive(), "the interrupt released the thread still blocked");
        assertInstanceOf(RejectedExecutionException.class, exchange.getException());
        assertEquals(0, manager.size());
    }

    @Test
    void interruptOfNestedWaitersReleasesTheInnerOneAndTheOuterOneThroughItsCallback() throws Exception {
        manager.getStatistics().setStatisticsEnabled(true);
        Exchange exchange = new DefaultExchange(context);
        Routing routing = new Routing();
        ControlledProcessor inner = processor(null);
        AtomicReference<Exception> innerException = new AtomicReference<>();
        CountDownLatch innerReturned = new CountDownLatch(1);
        CountDownLatch routeEnds = new CountDownLatch(1);
        AtomicBoolean outerCallbackFired = new AtomicBoolean();
        // the outer processor goes on with the exchange on another thread, which calls a step that waits for it too
        // (such as ProducerTemplate.send(uri, exchange)); when that step returns, the route ends and fires the callback
        // of the outer processor. The outer processor returns only once the inner thread waits, so the outer thread is
        // listed after the inner one: what counts is which call started first, not which thread blocked first.
        AsyncProcessorSupport outer = new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange ex, AsyncCallback callback) {
                thread("route-worker", () -> {
                    manager.process(inner, ex);
                    routing.enter();
                    innerException.set(ex.getException());
                    innerReturned.countDown();
                    awaitLatch(routeEnds);
                    routing.leave();
                    outerCallbackFired.set(true);
                    callback.done(false);
                });
                await().atMost(10, TimeUnit.SECONDS).until(() -> manager.size() == 1);
                return false;
            }
        };
        AtomicBoolean outerReleasedByCallback = new AtomicBoolean();
        AtomicReference<Exception> outerException = new AtomicReference<>();
        Thread waiter = thread("waiter", () -> {
            manager.process(outer, exchange);
            routing.enter();
            outerReleasedByCallback.set(outerCallbackFired.get());
            outerException.set(exchange.getException());
            routing.leave();
        });
        try {
            await().atMost(10, TimeUnit.SECONDS).until(() -> manager.size() == 2);

            manager.interrupt(exchange);
            // releasing every waiting thread fails here, and releasing the thread that blocked last (the outer one)
            // fails next
            assertEquals(1, manager.getStatistics().getThreadsInterrupted(), "only the inner thread is released");
            assertTrue(innerReturned.await(10, TimeUnit.SECONDS), "the inner thread was released");
            assertInstanceOf(RejectedExecutionException.class, innerException.get());
            assertEquals(1, manager.size(), "the outer thread still waits");
            assertEquals(waiter, manager.browse().iterator().next().getBlockedThread());
        } finally {
            routeEnds.countDown();
        }
        waiter.join(TimeUnit.SECONDS.toMillis(10));
        assertFalse(waiter.isAlive(), "the outer thread was released");
        assertTrue(outerReleasedByCallback.get(), "the outer thread was released by the callback of the route");
        assertInstanceOf(RejectedExecutionException.class, outerException.get());
        assertEquals(1, routing.most.get(), "the interrupt did not release both threads of the nested wait");
        assertEquals(0, manager.size());
    }

    @Test
    void threadThatWaitsWhileTheManagerStopsIsReleasedOrStaysListed() throws Exception {
        // the first exchange is in a bin of the map of main after the one of the second, so the iteration of the stop
        // that is at the first has passed the bin of the second (DefaultExchange does not override hashCode). The bins
        // only matter for the implementation of main, which kept the entries in a map keyed by the exchange: with an
        // entry per thread the test passes for any two exchanges.
        Gate gate = new Gate();
        Exchange first = new DefaultExchange(context);
        while (bin(first) == 0) {
            first = new DefaultExchange(context);
        }
        Exchange second = new DefaultExchange(context);
        while (bin(second) >= bin(first)) {
            second = new DefaultExchange(context);
        }
        gate.attach(first);
        Exchange e1 = first;
        Exchange e2 = second;
        ControlledProcessor p1 = processor(null);
        ControlledProcessor p2 = processor(null);
        Thread w1 = thread("waiter-1", () -> manager.process(p1, e1));
        await().atMost(10, TimeUnit.SECONDS).until(() -> manager.size() == 1);

        Thread stopper = new Thread(manager::stop, "stopper");
        gate.gatedThread = stopper;
        stopper.start();
        assertTrue(gate.inside.await(10, TimeUnit.SECONDS), "the stop interrupts the first thread");
        Thread w2 = thread("waiter-2", () -> manager.process(p2, e2));
        await().atMost(10, TimeUnit.SECONDS).until(() -> w2.getState() == Thread.State.WAITING);
        gate.proceed.countDown();
        stopper.join(TimeUnit.SECONDS.toMillis(10));
        w1.join(TimeUnit.SECONDS.toMillis(10));
        assertFalse(w1.isAlive(), "the stop released the first thread");

        // the second thread waits since the stop listed the threads: the stop released it, or it is still listed, so
        // that it can be interrupted (on main its entry was cleared and it could no longer be found)
        if (w2.isAlive()) {
            assertEquals(1, manager.size(), "the thread still blocked stays listed");
            manager.interrupt(e2);
        }
        w2.join(TimeUnit.SECONDS.toMillis(10));
        assertFalse(w2.isAlive(), "the second thread was released");
        assertTrue(e2.getExchangeExtension().isInterrupted());
    }

    private ControlledProcessor processor(Runnable onProcess) {
        ControlledProcessor processor = new ControlledProcessor(onProcess);
        processors.add(processor);
        return processor;
    }

    private Thread thread(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        threads.add(thread);
        thread.start();
        return thread;
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** How many threads route the exchange at once, and the most seen. */
    private static final class Routing {
        final AtomicInteger now = new AtomicInteger();
        final AtomicInteger most = new AtomicInteger();

        void enter() {
            most.accumulateAndGet(now.incrementAndGet(), Math::max);
        }

        void leave() {
            now.decrementAndGet();
        }
    }

    /** The bin of a key in a ConcurrentHashMap of 16 bins (the spread and mask of ConcurrentHashMap). */
    private static int bin(Object key) {
        int h = key.hashCode();
        return ((h ^ (h >>> 16)) & 0x7fffffff) & 15;
    }

    /**
     * Parks a given thread when the await manager reads the route of the exchange while it interrupts a blocked thread
     * (the WARN line of the blocked thread has its route id), until the test lets it go on.
     */
    private static final class Gate {
        volatile Thread gatedThread;
        final CountDownLatch inside = new CountDownLatch(1);
        final CountDownLatch proceed = new CountDownLatch(1);

        Exchange attach(Exchange exchange) {
            UnitOfWork uow = (UnitOfWork) Proxy.newProxyInstance(UnitOfWork.class.getClassLoader(),
                    new Class<?>[] { UnitOfWork.class }, (proxy, method, args) -> {
                        String name = method.getName();
                        if ("getRoute".equals(name) && Thread.currentThread() == gatedThread && calledFromInterrupt()) {
                            gatedThread = null;
                            inside.countDown();
                            proceed.await(10, TimeUnit.SECONDS);
                        }
                        if ("hashCode".equals(name)) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(name)) {
                            return proxy == args[0];
                        }
                        if ("toString".equals(name)) {
                            return "GateUnitOfWork";
                        }
                        Class<?> type = method.getReturnType();
                        if (type == boolean.class) {
                            return false;
                        } else if (type == int.class) {
                            return 0;
                        } else if (type == long.class) {
                            return 0L;
                        }
                        return null;
                    });
            exchange.getExchangeExtension().setUnitOfWork(uow);
            return exchange;
        }

        private static boolean calledFromInterrupt() {
            return StackWalker.getInstance().walk(frames -> frames.anyMatch(
                    f -> f.getClassName().equals(DefaultAsyncProcessorAwaitManager.class.getName())
                            && "interrupt".equals(f.getMethodName())));
        }
    }

    /**
     * An asynchronous processor whose callback the test fires, from another thread, when it chooses. An inner class, so
     * that the thread it starts in process() is tracked in threads and joined by tearDown.
     */
    private final class ControlledProcessor extends AsyncProcessorSupport {
        final AtomicReference<AsyncCallback> callback = new AtomicReference<>();
        final CountDownLatch called = new CountDownLatch(1);
        private final Runnable onProcess;

        ControlledProcessor(Runnable onProcess) {
            this.onProcess = onProcess;
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            this.callback.set(callback);
            called.countDown();
            if (onProcess != null) {
                thread("processor-worker", onProcess);
            }
            return false;
        }

        void complete() throws InterruptedException {
            AsyncCallback cb = callback.getAndSet(null);
            if (cb != null) {
                Thread thread = new Thread(() -> cb.done(false), "async-completer");
                thread.start();
                thread.join(TimeUnit.SECONDS.toMillis(10));
            }
        }
    }
}
