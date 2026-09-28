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
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.cluster.ClusteredRouteController;
import org.apache.camel.impl.cluster.ClusteredRoutePolicy;
import org.apache.camel.support.cluster.AbstractCamelClusterService;
import org.apache.camel.support.cluster.AbstractCamelClusterView;
import org.apache.camel.support.cluster.ClusterServiceSelectors;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClusteredEdgeCasesTest {

    @Test
    public void testInitialDelayWhenLeadershipTakenAfterStart() throws Exception {
        TestClusterService cs = new TestClusterService("cs");
        CamelContext context = new DefaultCamelContext();
        context.addService(cs);
        ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace("my-ns");
        policy.setInitialDelay(Duration.ofSeconds(3));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo").routeId("foo").routePolicy(policy).to("mock:foo");
            }
        });
        context.start();
        try {
            cs.getView().setLeader(true);

            // the route must not start before the initial delay has elapsed
            await().during(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("foo")));
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo")));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testRemovedRouteIdReusedByOtherRoute() throws Exception {
        TestClusterService cs = new TestClusterService("cs");
        CamelContext context = new DefaultCamelContext();
        context.addService(cs);
        ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace("my-ns");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo").routeId("foo").routePolicy(policy).to("mock:foo");
                from("seda:bar").routeId("bar").routePolicy(policy).to("mock:bar");
            }
        });
        context.start();
        try {
            cs.getView().setLeader(true);
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo")));

            context.getRouteController().stopRoute("foo");
            context.removeRoute("foo");

            // a route that is not clustered with the same id
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("seda:foo2").routeId("foo").to("mock:foo2");
                }
            });
            assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo"));

            cs.getView().setLeader(false);
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("bar")));
            await().during(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo")));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testServiceRestartDoesNotStartReleasedView() throws Exception {
        TestClusterService cs = new TestClusterService("cs");
        CamelContext context = new DefaultCamelContext();
        context.addService(cs);
        ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace("my-ns");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo").routeId("foo").routePolicy(policy).to("mock:foo");
            }
        });
        context.start();
        try {
            assertTrue(cs.getView().isRunning());

            context.getRouteController().stopRoute("foo");
            context.removeRoute("foo");
            assertFalse(cs.getView().isRunning());

            cs.stop();
            cs.start();
            assertFalse(cs.getView().isRunning());
        } finally {
            context.stop();
        }
    }

    @Test
    public void testClusteredRouteControllerWithSelector() throws Exception {
        TestClusterService cs1 = new TestClusterService("cs1");
        cs1.setOrder(1);
        TestClusterService cs2 = new TestClusterService("cs2");
        cs2.setOrder(2);

        CamelContext context = new DefaultCamelContext();
        context.addService(cs1);
        context.addService(cs2);

        ClusteredRouteController controller = new ClusteredRouteController();
        assertDoesNotThrow(() -> controller.setClusterServiceSelector(ClusterServiceSelectors.order()));
        controller.setNamespace("my-ns");
        context.setRouteController(controller);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo").routeId("foo").to("mock:foo");
            }
        });
        context.start();
        try {
            // the service selected by the order is used
            cs1.getView().setLeader(true);
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo")));
        } finally {
            context.stop();
        }
    }

    // *********************************
    // Helpers
    // *********************************

    private static class TestClusterView extends AbstractCamelClusterView {
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
