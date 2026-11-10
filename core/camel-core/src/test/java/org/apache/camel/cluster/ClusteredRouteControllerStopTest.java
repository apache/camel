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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.cluster.ClusteredRouteController;
import org.apache.camel.impl.event.DefaultEventFactory;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.LifecycleStrategySupport;
import org.apache.camel.support.cluster.AbstractCamelClusterService;
import org.apache.camel.support.cluster.AbstractCamelClusterView;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CamelContext stops its route controller before the routes, and the ClusteredRouteController must not stop the
 * cluster service (giving up the leadership) before the consumers of the routes have stopped.
 */
public class ClusteredRouteControllerStopTest {

    @Test
    public void testCamelContextClusterServiceStoppedAfterRoutes() throws Exception {
        TestClusterService cs = new TestClusterService("cs");
        CamelContext context = new DefaultCamelContext();
        context.addService(cs);
        ClusteredRouteController controller = new ClusteredRouteController();

        doTestStopWithInflightExchange(context, controller, cs);
    }

    @Test
    public void testControllerClusterServiceStoppedAfterRoutes() throws Exception {
        TestClusterService cs = new TestClusterService("cs");
        CamelContext context = new DefaultCamelContext();
        ClusteredRouteController controller = new ClusteredRouteController();
        // a cluster service that is not a service of the CamelContext, so the route controller starts and stops it
        controller.setClusterService(cs);

        doTestStopWithInflightExchange(context, controller, cs);
    }

    @Test
    public void testControllerStoppedOnItsOwnStopsClusterService() throws Exception {
        TestClusterService cs = new TestClusterService("cs");
        CamelContext context = new DefaultCamelContext();
        ClusteredRouteController controller = new ClusteredRouteController();
        controller.setClusterService(cs);
        controller.setNamespace("my-ns");
        context.setRouteController(controller);
        context.start();
        try {
            assertTrue(cs.isStarted());

            // the CamelContext keeps running, so the cluster service is stopped right away
            controller.stop();
            assertFalse(cs.isStarted());
        } finally {
            context.stop();
        }
    }

    @Test
    public void testControllerClusterServiceStoppedOnEachCamelContextStop() throws Exception {
        TestClusterService cs = new TestClusterService("cs");
        CamelContext context = new DefaultCamelContext();
        ClusteredRouteController controller = new ClusteredRouteController();
        controller.setClusterService(cs);
        controller.setNamespace("my-ns");
        context.setRouteController(controller);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo").routeId("foo").to("mock:foo");
            }
        });

        AtomicReference<Boolean> startedWhenContextStopped = probeClusterServiceWhenContextStopped(context, cs);

        try {
            for (int i = 0; i < 2; i++) {
                context.start();
                assertTrue(cs.isStarted());
                // the route controller registers its listeners once, also when the CamelContext is started again
                assertEquals(1, countControllerListeners(context));
                startedWhenContextStopped.set(null);
                context.stop();
                assertFalse(cs.isStarted());
                // stopped once the routes have stopped, not by the fallback when the CamelContext has stopped
                assertEquals(Boolean.FALSE, startedWhenContextStopped.get());
                // and unregistered when the CamelContext has stopped
                assertEquals(0, countControllerListeners(context));
            }
        } finally {
            context.stop();
        }
    }

    @Test
    public void testControllerClusterServiceStoppedWithoutRoutesStoppedEvent() throws Exception {
        TestClusterService cs = new TestClusterService("cs");
        CamelContext context = new DefaultCamelContext();
        // a custom EventFactory that does not create the CamelContextRoutesStoppedEvent
        context.getManagementStrategy().setEventFactory(new DefaultEventFactory() {
            @Override
            public CamelEvent createCamelContextRoutesStoppedEvent(CamelContext context) {
                return null;
            }
        });
        ClusteredRouteController controller = new ClusteredRouteController();
        // a cluster service that is not a service of the CamelContext, so only the route controller stops it
        controller.setClusterService(cs);
        controller.setNamespace("my-ns");
        context.setRouteController(controller);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo").routeId("foo").to("mock:foo");
            }
        });

        try {
            context.start();
            assertTrue(cs.isStarted());
            context.stop();
            // the cluster service is stopped when the CamelContext has stopped
            assertFalse(cs.isStarted());
        } finally {
            context.stop();
        }
    }

    @Test
    public void testControllerShutdownOnItsOwnUnregistersListeners() throws Exception {
        TestClusterService cs = new TestClusterService("cs");
        CamelContext context = new DefaultCamelContext();
        ClusteredRouteController controller = new ClusteredRouteController();
        controller.setClusterService(cs);
        controller.setNamespace("my-ns");
        context.setRouteController(controller);

        try {
            context.start();
            assertEquals(1, countControllerListeners(context));

            // the route controller is shut down while the CamelContext keeps running
            controller.shutdown();
            assertFalse(cs.isStarted());
            assertEquals(0, countControllerListeners(context));

            // the cluster service is used elsewhere: stopping the CamelContext must not stop it through this controller
            cs.start();
            context.stop();
            assertTrue(cs.isStarted());
        } finally {
            cs.stop();
            context.stop();
        }
    }

    /**
     * Records whether the cluster service is still started when the CamelContext has stopped, before the fallback of
     * the route controller (whose lifecycle strategy is added after this one, when the route controller starts).
     */
    private static AtomicReference<Boolean> probeClusterServiceWhenContextStopped(
            CamelContext context, TestClusterService cs) {
        AtomicReference<Boolean> started = new AtomicReference<>();
        context.addLifecycleStrategy(new LifecycleStrategySupport() {
            @Override
            public void onContextStopped(CamelContext context) {
                started.set(cs.isStarted());
            }
        });
        return started;
    }

    private static long countControllerListeners(CamelContext context) {
        long notifiers = context.getManagementStrategy().getEventNotifiers().stream()
                .filter(n -> n.getClass().getEnclosingClass() == ClusteredRouteController.class)
                .count();
        long strategies = context.getLifecycleStrategies().stream()
                .filter(s -> s.getClass().getEnclosingClass() == ClusteredRouteController.class)
                .count();
        assertEquals(notifiers, strategies);
        return notifiers;
    }

    private void doTestStopWithInflightExchange(
            CamelContext context, ClusteredRouteController controller, TestClusterService cs)
            throws Exception {
        CountDownLatch processing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        controller.setNamespace("my-ns");
        context.setRouteController(controller);
        context.getShutdownStrategy().setTimeout(30);
        AtomicReference<Boolean> startedWhenContextStopped = probeClusterServiceWhenContextStopped(context, cs);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo").routeId("foo")
                        .process(e -> {
                            processing.countDown();
                            release.await(20, TimeUnit.SECONDS);
                        })
                        .to("mock:foo");
            }
        });
        context.start();

        Thread stopper = new Thread(context::stop, "CamelContextStopper");
        try {
            cs.getView().setLeader(true);
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo")));

            context.createProducerTemplate().sendBody("seda:foo", "Hello");
            assertTrue(processing.await(10, TimeUnit.SECONDS));

            stopper.start();
            // the CamelContext stops the route controller first, and then waits for the in-flight exchange
            await().atMost(10, TimeUnit.SECONDS).until(controller::isStopped);

            // the route still processes its exchange, so this node must still be the leader
            assertTrue(cs.isStarted());
            assertTrue(cs.getView().isRunAllowed());
            assertTrue(cs.getView().getLocalMember().isLeader());
        } finally {
            release.countDown();
            if (!stopper.isAlive()) {
                // the test failed before the stop
                context.stop();
            }
            stopper.join(TimeUnit.SECONDS.toMillis(30));
        }

        assertFalse(stopper.isAlive());
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("foo"));
        // the cluster service is stopped once the routes have stopped
        assertFalse(cs.isStarted());
        assertFalse(cs.getView().isRunAllowed());
        assertEquals(Boolean.FALSE, startedWhenContextStopped.get());
    }

    // *********************************
    // Helpers
    // *********************************

    private static class TestClusterView extends AbstractCamelClusterView {
        private volatile boolean leader;

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
        protected void doStart() {
        }

        @Override
        protected void doStop() {
            // a view gives up the leadership when it stops
            leader = false;
        }

        void setLeader(boolean leader) {
            this.leader = leader;
            if (isRunAllowed()) {
                fireLeadershipChangedEvent(getLeader().orElse(null));
            }
        }
    }

    private static class TestClusterService extends AbstractCamelClusterService<TestClusterView> {
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
