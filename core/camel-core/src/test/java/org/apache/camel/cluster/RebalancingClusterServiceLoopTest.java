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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.cluster.AbstractCamelClusterService;
import org.apache.camel.support.cluster.AbstractCamelClusterView;
import org.apache.camel.support.cluster.RebalancingCamelClusterService;
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

        // one chain: at most one pass per period (fewer on a busy machine), and a few more for the window edges
        int before = service.passes.get();
        long start = System.nanoTime();
        await().during(2, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS).until(() -> service.passes.get() - before
                                                                                     <= TimeUnit.NANOSECONDS.toMillis(
                                                                                             System.nanoTime() - start)
                                                                                        / PERIOD_MILLIS + 3);
        assertEquals(1, service.maxConcurrent.get(), "Passes ran at the same time");
    }

    // *********************************
    // Helpers
    // *********************************

    private static final class CountingRebalancingClusterService extends RebalancingCamelClusterService {
        private final AtomicInteger passes = new AtomicInteger();
        private final AtomicInteger running = new AtomicInteger();
        private final AtomicInteger maxConcurrent = new AtomicInteger();
        private final Queue<Thread> threads = new ConcurrentLinkedQueue<>();
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
                super.reconcile();
            } finally {
                running.decrementAndGet();
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
