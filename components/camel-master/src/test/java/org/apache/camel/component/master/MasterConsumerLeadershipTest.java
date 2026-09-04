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
package org.apache.camel.component.master;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.cluster.AbstractCamelClusterService;
import org.apache.camel.support.cluster.AbstractCamelClusterView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that the delegated consumer only ever runs while this node holds the leadership, also when the leadership
 * changes while the start of the delegated consumer is still pending.
 */
public class MasterConsumerLeadershipTest {

    private DefaultCamelContext context;
    private TestClusterService clusterService;
    private ProbeComponent probe;

    @BeforeEach
    void setUp() throws Exception {
        probe = new ProbeComponent();
        clusterService = new TestClusterService();

        context = new DefaultCamelContext();
        context.disableJMX();
        context.addService(clusterService);
        context.addComponent("probe", probe);

        MasterComponent master = context.getComponent("master", MasterComponent.class);
        // keep the retries short so an exhausted start does not dominate the test time
        master.setBackOffDelay(200);
        master.setBackOffMaxAttempts(2);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("master:ns:probe:test").routeId("master-route").to("mock:result");
            }
        });

        context.start();
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    @Timeout(60)
    void testLeadershipLostWhilePendingStartDoesNotStartConsumer() {
        TestClusterView view = clusterService.getTestView();

        view.setLeader(true);
        // the start is scheduled with an initial delay, so it is still pending here
        view.setLeader(false);

        // outlast the initial delay of the start task and verify the consumer was never even created
        await().during(3, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, probe.created.get()));

        // taking the leadership again must still work, which also proves the events did reach the consumer
        view.setLeader(true);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, probe.started.get()));
        assertEquals(1, probe.created.get(), "The cancelled start must not have created a second consumer");
    }

    @Test
    @Timeout(60)
    void testLeadershipTakenStartsConsumerAndLostStopsIt() {
        TestClusterView view = clusterService.getTestView();

        view.setLeader(true);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, probe.started.get()));

        view.setLeader(false);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, probe.stopped.get()));
    }

    @Test
    @Timeout(60)
    void testConsumerIsRestartedWhenLeadershipFlapsAfterASuccessfulStart() {
        TestClusterView view = clusterService.getTestView();

        view.setLeader(true);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, probe.started.get()));

        // the membership flap seen in production: the consumer must stop and then come back
        view.setLeader(false);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, probe.stopped.get()));

        view.setLeader(true);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(2, probe.started.get()));
        assertEquals(1, probe.stopped.get());
    }

    @Test
    @Timeout(60)
    void testLeadershipLostWhileStartIsInProgressStopsTheConsumer() throws Exception {
        TestClusterView view = clusterService.getTestView();
        CountDownLatch startGate = new CountDownLatch(1);
        probe.startGate.set(startGate);

        view.setLeader(true);
        // wait until the start is running and blocked inside the delegated consumer
        await().atMost(10, TimeUnit.SECONDS).until(() -> probe.startAttempts.get() == 1);

        // the leadership is lost while the start is in progress, this must not be able to interleave
        Thread loser = new Thread(() -> view.setLeader(false), "leadership-lost");
        loser.start();
        startGate.countDown();
        loser.join(TimeUnit.SECONDS.toMillis(20));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(1, probe.started.get());
            assertEquals(1, probe.stopped.get(), "The consumer started on a node that lost the leadership must be stopped");
        });
    }

    @Test
    @Timeout(60)
    void testRepeatedLeadershipTakenStartsOnlyOneConsumer() {
        TestClusterView view = clusterService.getTestView();

        view.setLeader(true);
        view.setLeader(true);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, probe.started.get()));
        await().during(2, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(1, probe.created.get()));
    }

    @Test
    @Timeout(60)
    void testStoppingTheConsumerCancelsAPendingStart() {
        TestClusterView view = clusterService.getTestView();

        view.setLeader(true);
        // the start is still pending, stopping must cancel it instead of letting it start afterwards
        context.stop();

        await().during(3, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, probe.created.get()));
    }

    @Test
    @Timeout(60)
    void testConsumerStartsAfterLeadershipIsTakenAgainWhenAnEarlierStartFailed() {
        TestClusterView view = clusterService.getTestView();

        probe.failStart.set(true);
        view.setLeader(true);

        // every start attempt fails, then the task runs out of budget and stops attempting
        await().atMost(20, TimeUnit.SECONDS).until(() -> probe.startAttempts.get() == 2);
        await().during(1, TimeUnit.SECONDS).atMost(20, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(2, probe.startAttempts.get()));
        assertEquals(0, probe.started.get());

        // a failed start must not leave state behind that makes a later leadership event a no-op,
        // not even without an intervening leadership lost event
        probe.failStart.set(false);
        view.setLeader(true);

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, probe.started.get()));
    }

    @Test
    @Timeout(60)
    void testAllConfiguredStartAttemptsAreMade() {
        TestClusterView view = clusterService.getTestView();
        MasterComponent master = context.getComponent("master", MasterComponent.class);
        // the attempts have to outlast the default 5s duration of the iteration time budget
        master.setBackOffDelay(3000);
        master.setBackOffMaxAttempts(3);

        probe.failStart.set(true);
        view.setLeader(true);

        // every configured attempt must be made, the task must not end on a time budget of its own
        await().atMost(30, TimeUnit.SECONDS).until(() -> probe.startAttempts.get() == 3);
        assertEquals(0, probe.started.get());
    }

    // ************************************
    // Delegated endpoint under observation
    // ************************************

    private static final class ProbeComponent extends DefaultComponent {
        private final AtomicInteger created = new AtomicInteger();
        private final AtomicInteger startAttempts = new AtomicInteger();
        private final AtomicInteger started = new AtomicInteger();
        private final AtomicInteger stopped = new AtomicInteger();
        private final AtomicBoolean failStart = new AtomicBoolean();
        private final AtomicReference<CountDownLatch> startGate = new AtomicReference<>();

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new ProbeEndpoint(uri, this);
        }
    }

    private static final class ProbeEndpoint extends DefaultEndpoint {
        private final ProbeComponent component;

        ProbeEndpoint(String uri, ProbeComponent component) {
            super(uri, component);
            this.component = component;
        }

        @Override
        public Producer createProducer() {
            throw new UnsupportedOperationException("Cannot produce from this endpoint");
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            component.created.incrementAndGet();
            return new ProbeConsumer(this, processor, component);
        }

        @Override
        public boolean isSingleton() {
            return true;
        }
    }

    private static final class ProbeConsumer extends DefaultConsumer {
        private final ProbeComponent component;

        ProbeConsumer(Endpoint endpoint, Processor processor, ProbeComponent component) {
            super(endpoint, processor);
            this.component = component;
        }

        @Override
        protected void doStart() throws Exception {
            super.doStart();
            // counted before the failure flag is read, so a test can await an attempt that has made its decision
            component.startAttempts.incrementAndGet();
            CountDownLatch gate = component.startGate.get();
            if (gate != null) {
                gate.await();
            }
            if (component.failStart.get()) {
                throw new IllegalStateException("Simulated failure to start the delegated consumer");
            }
            component.started.incrementAndGet();
        }

        @Override
        protected void doStop() throws Exception {
            super.doStop();
            component.stopped.incrementAndGet();
        }
    }

    // ************************************
    // Cluster with a leadership we control
    // ************************************

    private static final class TestClusterService extends AbstractCamelClusterService<TestClusterView> {
        private volatile TestClusterView view;

        TestClusterService() {
            super("test-cluster-service");
        }

        TestClusterView getTestView() {
            return view;
        }

        @Override
        protected TestClusterView createView(String namespace) {
            view = new TestClusterView(this, namespace);
            return view;
        }
    }

    private static final class TestClusterView extends AbstractCamelClusterView {
        private final TestClusterMember localMember = new TestClusterMember();

        TestClusterView(TestClusterService clusterService, String namespace) {
            super(clusterService, namespace);
        }

        void setLeader(boolean leader) {
            localMember.leader = leader;
            fireLeadershipChangedEvent(leader ? localMember : null);
        }

        @Override
        public Optional<CamelClusterMember> getLeader() {
            return localMember.isLeader() ? Optional.of(localMember) : Optional.empty();
        }

        @Override
        public CamelClusterMember getLocalMember() {
            return localMember;
        }

        @Override
        public List<CamelClusterMember> getMembers() {
            return List.of(localMember);
        }
    }

    private static final class TestClusterMember implements CamelClusterMember {
        private final String id = UUID.randomUUID().toString();
        private volatile boolean leader;

        @Override
        public boolean isLeader() {
            return leader;
        }

        @Override
        public boolean isLocal() {
            return true;
        }

        @Override
        public String getId() {
            return id;
        }
    }
}
