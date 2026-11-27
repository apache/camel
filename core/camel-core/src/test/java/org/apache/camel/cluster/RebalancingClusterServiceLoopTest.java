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
package org.apache.camel.cluster;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.cluster.AbstractCamelClusterService;
import org.apache.camel.support.cluster.AbstractCamelClusterView;
import org.apache.camel.support.cluster.RebalancingCamelClusterService;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The passes of the rebalancing cluster service form one chain: a failing pass does not end it, and a stop and start of
 * the service does not leave the chain of the previous start running next to the new one.
 */
class RebalancingClusterServiceLoopTest {

    private static final long PERIOD_MILLIS = 200;

    private CamelContext context;
    private TestClusterService delegate;
    private CountingRebalancingClusterService service;

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        delegate = new TestClusterService("node-1");
        service = new CountingRebalancingClusterService(delegate);
        context.addService(service);
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    void failingPassDoesNotEndRebalancing() throws Exception {
        context.start();
        delegate.getView("my-ns");
        await().atMost(10, TimeUnit.SECONDS).until(() -> service.passes.get() >= 2);

        delegate.failures.set(1);
        await().atMost(10, TimeUnit.SECONDS).until(() -> delegate.failures.get() == 0);
        int passes = service.passes.get();

        // the next passes run, and they still rebalance: a disabled partition of the only member is opened
        TestClusterView view = delegate.view("my-ns");
        view.setDisabled(true);
        await().atMost(10, TimeUnit.SECONDS).until(() -> service.passes.get() >= passes + 3 && !view.isDisabled());
    }

    @Test
    void restartDoesNotAddChainOfPasses() throws Exception {
        context.start();
        delegate.getView("my-ns");
        await().atMost(10, TimeUnit.SECONDS).until(() -> service.passes.get() >= 2);

        for (int i = 0; i < 3; i++) {
            service.stop();
            Thread stopped = service.lastThread;
            service.threads.clear();
            service.start();
            await().atMost(10, TimeUnit.SECONDS).until(() -> service.threads.size() >= 3);
            // no pass starts on the executor of the previous start once the service is stopped
            assertTrue(service.threads.stream().noneMatch(t -> t == stopped),
                    "A pass ran on the executor of the previous start after the stop");
        }

        // one chain: the next pass is scheduled a period after the previous one ends, so at most elapsed / period + 1
        // passes start in the window (fewer on a busy machine). The 3 also allows for the pass counter and the clock
        // not being read at the same instant, at the start and at each check. A second chain would add one pass per
        // period, about 10 in the 2 seconds, which is well above it
        int before = service.passes.get();
        long start = System.nanoTime();
        await().during(2, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS).until(() -> service.passes.get() - before
                                                                                     <= TimeUnit.NANOSECONDS.toMillis(
                                                                                             System.nanoTime() - start)
                                                                                        / PERIOD_MILLIS + 3);
        assertEquals(1, service.maxConcurrent.get(), "Passes ran at the same time");
    }

    @Test
    void stopWaitsForTheRunningPass() throws Exception {
        context.start();
        delegate.getView("my-ns");
        await().atMost(10, TimeUnit.SECONDS).until(() -> service.passes.get() >= 1);

        // the next pass takes a quarter of the period, and does not stop on the interrupt of the stop
        CountDownLatch entered = new CountDownLatch(1);
        service.blockNextPass.set(entered);
        assertTrue(entered.await(10, TimeUnit.SECONDS), "The slow pass did not start");
        service.stop();
        service.start();

        // the first pass after the start runs once the slow pass is over, not next to it
        int passes = service.passes.get();
        await().atMost(10, TimeUnit.SECONDS).until(() -> service.passes.get() >= passes + 2);
        assertEquals(1, service.maxConcurrent.get(), "The first pass after the start ran next to the pass of the stop");
    }

    @Test
    void failuresAreLoggedWithTheStackTrace() throws Exception {
        List<LogEvent> events = new CopyOnWriteArrayList<>();
        AbstractAppender appender = new AbstractAppender(
                "rebalancing-errors-" + UUID.randomUUID(), null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (RebalancingCamelClusterService.class.getName().equals(event.getLoggerName())) {
                    events.add(event.toImmutable());
                }
            }
        };
        appender.start();
        LoggerContext logging = (LoggerContext) LogManager.getContext(false);
        logging.getConfiguration().getRootLogger().addAppender(appender, Level.WARN, null);
        logging.updateLoggers();
        try {
            context.start();
            delegate.getView("my-ns");
            await().atMost(10, TimeUnit.SECONDS).until(() -> service.passes.get() >= 1);

            // a VirtualMachineError is logged at ERROR, with the stack trace, and another failure in the pass right
            // after it is still logged at WARN, with the stack trace
            delegate.nextFailures.add(new OutOfMemoryError("Simulated out of memory while listing the namespaces"));
            delegate.nextFailures.add(new IllegalStateException("Simulated failure right after the out of memory"));
            await().atMost(10, TimeUnit.SECONDS).until(() -> events.size() >= 2);
            assertEquals(Level.ERROR, events.get(0).getLevel());
            assertTrue(events.get(0).getThrown() instanceof OutOfMemoryError, "The stack trace was not logged");
            assertEquals(Level.WARN, events.get(1).getLevel());
            assertTrue(events.get(1).getThrown() instanceof IllegalStateException, "The stack trace was not logged");

            // after a pass that works (the passes run one at a time, so the one after it has started), another
            // failure is logged at WARN, with the stack trace
            int passes = service.passes.get();
            await().atMost(10, TimeUnit.SECONDS).until(() -> service.passes.get() >= passes + 2);
            delegate.nextFailures.add(new IllegalStateException("Simulated failure while listing the namespaces"));
            await().atMost(10, TimeUnit.SECONDS).until(() -> events.size() >= 3);
            assertEquals(Level.WARN, events.get(2).getLevel());
            assertTrue(events.get(2).getThrown() instanceof IllegalStateException, "The stack trace was not logged");
        } finally {
            logging.getConfiguration().getRootLogger().removeAppender(appender.getName());
            logging.updateLoggers();
            appender.stop();
        }
    }

    // *********************************
    // Helpers
    // *********************************

    private static final class CountingRebalancingClusterService extends RebalancingCamelClusterService {
        private final AtomicInteger passes = new AtomicInteger();
        private final AtomicInteger running = new AtomicInteger();
        private final AtomicInteger maxConcurrent = new AtomicInteger();
        private final Queue<Thread> threads = new ConcurrentLinkedQueue<>();
        // the next pass counts this down and then takes a quarter of the period
        private final AtomicReference<CountDownLatch> blockNextPass = new AtomicReference<>();
        private volatile Thread lastThread;

        CountingRebalancingClusterService(CamelPreemptiveClusterService delegate) {
            super(delegate, PERIOD_MILLIS);
        }

        @Override
        protected void reconcile() {
            passes.incrementAndGet();
            lastThread = Thread.currentThread();
            threads.add(Thread.currentThread());
            maxConcurrent.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                CountDownLatch block = blockNextPass.getAndSet(null);
                if (block != null) {
                    block.countDown();
                    workIgnoringInterrupts(PERIOD_MILLIS / 4);
                }
                super.reconcile();
            } finally {
                running.decrementAndGet();
            }
        }

        // a pass that does not stop on an interrupt (as one that waits for I/O)
        private static void workIgnoringInterrupts(long millis) {
            CountDownLatch never = new CountDownLatch(1);
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
            boolean interrupted = false;
            long left;
            while ((left = deadline - System.nanoTime()) > 0) {
                try {
                    never.await(left, TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class TestClusterView extends AbstractCamelClusterView implements CamelPreemptiveClusterView {
        private final CamelClusterMember local;
        private volatile boolean disabled;

        TestClusterView(CamelClusterService cluster, String namespace) {
            super(cluster, namespace);
            this.local = new CamelClusterMember() {
                @Override
                public boolean isLeader() {
                    return false;
                }

                @Override
                public boolean isLocal() {
                    return true;
                }

                @Override
                public String getId() {
                    return getClusterService().getId();
                }
            };
        }

        @Override
        public Optional<CamelClusterMember> getLeader() {
            return Optional.empty();
        }

        @Override
        public CamelClusterMember getLocalMember() {
            return local;
        }

        @Override
        public List<CamelClusterMember> getMembers() {
            return List.of(local);
        }

        @Override
        public void setDisabled(boolean disabled) {
            this.disabled = disabled;
        }

        @Override
        public boolean isDisabled() {
            return disabled;
        }

        @Override
        protected void doStart() {
        }

        @Override
        protected void doStop() {
        }
    }

    private static final class TestClusterService extends AbstractCamelClusterService<TestClusterView>
            implements CamelPreemptiveClusterService {
        private final AtomicInteger failures = new AtomicInteger();
        // each is thrown once, in order, by the next listings of the namespaces
        private final Queue<Throwable> nextFailures = new ConcurrentLinkedQueue<>();
        private volatile TestClusterView view;

        TestClusterService(String id) {
            super(id);
        }

        @Override
        protected TestClusterView createView(String namespace) {
            view = new TestClusterView(this, namespace);
            return view;
        }

        @Override
        public CamelPreemptiveClusterView getView(String namespace) throws Exception {
            return (CamelPreemptiveClusterView) super.getView(namespace);
        }

        @Override
        public Collection<String> getNamespaces() {
            Throwable failure = nextFailures.poll();
            if (failure instanceof Error error) {
                throw error;
            } else if (failure instanceof RuntimeException e) {
                throw e;
            }
            if (failures.getAndUpdate(f -> Math.max(0, f - 1)) > 0) {
                throw new IllegalStateException("Simulated failure while listing the namespaces");
            }
            return super.getNamespaces();
        }

        TestClusterView view(String namespace) {
            return view;
        }
    }
}
