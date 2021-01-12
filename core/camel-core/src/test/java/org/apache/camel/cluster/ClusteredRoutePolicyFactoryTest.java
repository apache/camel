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

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.cluster.ClusteredRoutePolicyFactory;
import org.apache.camel.support.cluster.AbstractCamelClusterService;
import org.apache.camel.support.cluster.AbstractCamelClusterView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClusteredRoutePolicyFactoryTest extends ContextTestSupport {

    private ClusteredRoutePolicyFactory factory;
    private TestClusterService cs;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        cs = new TestClusterService("my-cluster-service");
        context.addService(cs);

        factory = ClusteredRoutePolicyFactory.forNamespace("my-ns");
        context.addRoutePolicyFactory(factory);

        return context;
    }

    @Test
    public void testClusteredRoutePolicyFactory() throws Exception {
        // route is stopped as we are not leader yet
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("foo"));

        MockEndpoint mock = getMockEndpoint("mock:foo");
        mock.expectedBodiesReceived("Hello Foo");

        cs.getView().setLeader(true);

        template.sendBody("seda:foo", "Hello Foo");

        assertMockEndpointsSatisfied();

        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo"));
    }

    @Test
    public void testClusteredRoutePolicyFactoryAddRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:bar").routeId("bar")
                        .to("mock:bar");
            }
        });

        // route is stopped as we are not leader yet
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("foo"));
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("bar"));

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello Foo");
        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello Bar");

        cs.getView().setLeader(true);

        template.sendBody("seda:foo", "Hello Foo");
        template.sendBody("seda:bar", "Hello Bar");

        assertMockEndpointsSatisfied();

        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo"));
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("bar"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo").routeId("foo")
                        .to("mock:foo");
            }
        };
    }

    @Test
    public void testClusteredRoutePolicyFactoryAddRouteAlreadyLeader() throws Exception {
        cs.getView().setLeader(true);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:bar").routeId("bar")
                        .to("mock:bar");
            }
        });

        // route is started as we are leader
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo"));
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("bar"));

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello Foo");
        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello Bar");

        template.sendBody("seda:foo", "Hello Foo");
        template.sendBody("seda:bar", "Hello Bar");

        assertMockEndpointsSatisfied();

        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo"));
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("bar"));
    }

    // *********************************
    // Helpers
    // *********************************

    private static class TestClusterView extends AbstractCamelClusterView {
        private boolean leader;

        public TestClusterView(CamelClusterService cluster, String namespace) {
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
        protected void doStart() throws Exception {
        }

        @Override
        protected void doStop() throws Exception {
        }

        public boolean isLeader() {
            return leader;
        }

        public void setLeader(boolean leader) {
            this.leader = leader;

            if (isRunAllowed()) {
                fireLeadershipChangedEvent(getLeader());
            }
        }
    }

    private static class TestClusterService extends AbstractCamelClusterService<TestClusterView> {

        private TestClusterView view;

        public TestClusterService(String id) {
            super(id);
        }

        @Override
        protected TestClusterView createView(String namespace) throws Exception {
            if (view == null) {
                view = new TestClusterView(this, namespace);
            }
            return view;
        }

        public TestClusterView getView() {
            return view;
        }
    }
}
