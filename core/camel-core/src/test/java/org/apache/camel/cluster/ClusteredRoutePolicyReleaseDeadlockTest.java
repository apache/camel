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

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.cluster.ClusteredRoutePolicyFactory;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.support.cluster.AbstractCamelClusterService;
import org.apache.camel.support.cluster.AbstractCamelClusterView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A CamelContext stop or a route removal must not deadlock with a leadership change that is starting the routes of a
 * {@link org.apache.camel.impl.cluster.ClusteredRoutePolicy}.
 * <p/>
 * Each route gets its own policy from the factory, so the view notifies one listener per route. The route "slow" takes
 * the leadership first and its start blocks until the test lets it go. A gate listener, registered between the
 * listeners of "slow" and "other", holds the leadership notification until the view is being released, so the policies
 * of "other" and "late" are always notified while a route is being removed. The policy of "late" is still registered
 * then, and has to start its route, which needs the CamelContext route lock that removeRoute holds.
 * <p/>
 * This test does not use ContextTestSupport, so a regression makes the test fail instead of hanging the build in the
 * tear down.
 */
public class ClusteredRoutePolicyReleaseDeadlockTest {

    private static final String NAMESPACE = "my-ns";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final AtomicBoolean slowStartArmed = new AtomicBoolean();
    private final CountDownLatch slowStarting = new CountDownLatch(1);
    private final CountDownLatch slowProceed = new CountDownLatch(1);

    private final AtomicBoolean gateArmed = new AtomicBoolean();
    private final CountDownLatch viewReleasing = new CountDownLatch(1);

    private DefaultCamelContext context;
    private TestClusterService cs;

    @BeforeEach
    public void setUp() throws Exception {
        cs = new TestClusterService("my-cluster-service");

        context = new DefaultCamelContext();
        context.disableJMX();
        context.addService(cs);
        context.addComponent("slow", new SlowComponent());
        context.addRoutePolicyFactory(ClusteredRoutePolicyFactory.forNamespace(NAMESPACE));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // stopping the CamelContext shuts down "late" and "other" before "slow"
                from("slow:start").routeId("slow").startupOrder(1)
                        .to("mock:slow");
                // the gate policy comes before the policy created by the factory, so its listener is registered
                // after the one of "slow" and before the one of "other"
                from("seda:other").routeId("other").startupOrder(2).routePolicy(new GatePolicy())
                        .to("mock:other");
                // its listener is registered after the gate
                from("seda:late").routeId("late").startupOrder(3)
                        .to("mock:late");
            }
        });
        context.start();
    }

    @AfterEach
    public void tearDown() throws InterruptedException {
        // never leave a test thread blocked on a latch
        slowProceed.countDown();
        viewReleasing.countDown();
        if (context != null && !context.isStopped()) {
            // stop on another thread, so a regression cannot hang the build here
            Thread stopper = newThread("stop-after-test", context::stop);
            stopper.start();
            stopper.join(TIMEOUT.toMillis());
        }
    }

    @Test
    public void testCamelContextStopDuringLeadershipChange() throws Exception {
        slowStartArmed.set(true);
        gateArmed.set(true);

        Thread dispatcher = newThread("leadership", () -> cs.getView().setLeader(true));
        dispatcher.start();
        assertTrue(slowStarting.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS), "route slow is not being started");

        Thread operator = newThread("operator", context::stop);
        operator.start();
        awaitBlocked(operator);

        // the start of "slow" completes while the CamelContext is being stopped
        slowProceed.countDown();

        assertTimeoutPreemptively(TIMEOUT, () -> {
            operator.join();
            dispatcher.join();
        }, "CamelContext stop deadlocked with the leadership change");

        assertTrue(context.isStopped());
        // the policies released the view
        assertFalse(cs.getView().isRunning());
    }

    @Test
    public void testRemoveRouteDuringLeadershipChange() throws Exception {
        slowStartArmed.set(true);
        gateArmed.set(true);

        Thread dispatcher = newThread("leadership", () -> cs.getView().setLeader(true));
        dispatcher.start();
        assertTrue(slowStarting.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS), "route slow is not being started");

        AtomicReference<Boolean> removed = new AtomicReference<>();
        Thread operator = newThread("operator", () -> {
            try {
                context.getRouteController().stopRoute("other");
                removed.set(context.removeRoute("other"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        operator.start();
        awaitBlocked(operator);

        // the start of "slow" completes while "other" is being removed
        slowProceed.countDown();

        assertTimeoutPreemptively(TIMEOUT, () -> {
            operator.join();
            dispatcher.join();
        }, "removeRoute deadlocked with the leadership change");

        assertEquals(Boolean.TRUE, removed.get());
        assertNull(context.getRoute("other"));

        // the other routes still have the leadership and keep running
        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("slow"));
            assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("late"));
        });
        assertTrue(cs.getView().isRunning());
    }

    @Test
    public void testLeadershipChangesStartAndStopRoutes() throws Exception {
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("slow"));
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("other"));
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("late"));

        cs.getView().setLeader(true);
        awaitRouteStatus(ServiceStatus.Started);

        cs.getView().setLeader(false);
        awaitRouteStatus(ServiceStatus.Stopped);

        // the policy thread applies the changes in order and ends with the last one
        cs.getView().setLeader(true);
        cs.getView().setLeader(false);
        cs.getView().setLeader(true);
        // the routes may be started for a moment by an earlier change, so wait until they stay started
        await().during(Duration.ofMillis(500)).atMost(TIMEOUT).untilAsserted(() -> assertRouteStatus(ServiceStatus.Started));

        context.getRouteController().stopRoute("other");
        assertTrue(context.removeRoute("other"));
        assertTrue(cs.getView().isRunning());

        context.stop();
        assertFalse(cs.getView().isRunning());
    }

    private void awaitRouteStatus(ServiceStatus status) {
        await().atMost(TIMEOUT).untilAsserted(() -> assertRouteStatus(status));
    }

    private void assertRouteStatus(ServiceStatus status) {
        assertEquals(status, context.getRouteController().getRouteStatus("slow"));
        assertEquals(status, context.getRouteController().getRouteStatus("other"));
        assertEquals(status, context.getRouteController().getRouteStatus("late"));
    }

    // Waits until the thread waits for a lock or a latch. This makes the race likely, not certain, as the thread may
    // wait for something else first (for example the shutdown strategy), but the test does not rely on it: the gate
    // listener holds the dispatch until the view is being released, so the deadlock is reached anyway without the fix.
    private static void awaitBlocked(Thread thread) {
        await().atMost(TIMEOUT).until(() -> {
            Thread.State state = thread.getState();
            return state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING
                    || state == Thread.State.BLOCKED;
        });
    }

    private static Thread newThread(String name, Runnable task) {
        Thread thread = new Thread(task, "ClusteredRoutePolicyReleaseDeadlockTest-" + name);
        thread.setDaemon(true);
        return thread;
    }

    // *********************************
    // Helpers
    // *********************************

    private final class SlowComponent extends DefaultComponent {
        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new SlowEndpoint(uri, this);
        }
    }

    private final class SlowEndpoint extends DefaultEndpoint {
        SlowEndpoint(String endpointUri, Component component) {
            super(endpointUri, component);
        }

        @Override
        public Producer createProducer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            return new DefaultConsumer(this, processor) {
                @Override
                protected void doStart() throws Exception {
                    // a consumer that takes a while to connect, like a broker connection or a subscription
                    if (slowStartArmed.compareAndSet(true, false)) {
                        slowStarting.countDown();
                        slowProceed.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                    }
                    super.doStart();
                }
            };
        }
    }

    private final class GatePolicy extends RoutePolicySupport {
        @Override
        public void onInit(Route route) {
            super.onInit(route);
            // the test service caches its single view, so this is the view of the policies, without retaining it
            cs.createView(NAMESPACE).addEventListener((CamelClusterEventListener.Leadership) (view, leader) -> {
                if (leader != null && gateArmed.compareAndSet(true, false)) {
                    try {
                        viewReleasing.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }
    }

    private final class TestClusterView extends AbstractCamelClusterView {
        private volatile boolean leader;
        private volatile boolean running;

        TestClusterView(CamelClusterService cluster, String namespace) {
            super(cluster, namespace);
        }

        @Override
        public Optional<CamelClusterMember> getLeader() {
            return leader ? Optional.of(getLocalMember()) : Optional.empty();
        }

        @Override
        public CamelClusterMember getLocalMember() {
            return new CamelClusterMember() {
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
                    return getClusterService().getId();
                }
            };
        }

        @Override
        public List<CamelClusterMember> getMembers() {
            return Collections.emptyList();
        }

        @Override
        public void removeEventListener(CamelClusterEventListener listener) {
            // a policy is releasing the view: let the leadership notification go on
            viewReleasing.countDown();
            super.removeEventListener(listener);
        }

        @Override
        protected void doStart() {
            running = true;
        }

        @Override
        protected void doStop() {
            running = false;
        }

        void setLeader(boolean leader) {
            this.leader = leader;

            if (isRunAllowed()) {
                fireLeadershipChangedEvent(getLeader().orElse(null));
            }
        }

        boolean isRunning() {
            return running;
        }
    }

    private final class TestClusterService extends AbstractCamelClusterService<TestClusterView> {
        private TestClusterView view;

        TestClusterService(String id) {
            super(id);
        }

        @Override
        protected TestClusterView createView(String namespace) {
            if (view == null) {
                view = new TestClusterView(this, namespace);
            }
            return view;
        }

        TestClusterView getView() {
            return view;
        }
    }
}
