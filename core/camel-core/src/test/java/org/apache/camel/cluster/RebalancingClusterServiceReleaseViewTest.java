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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.cluster.ClusteredRoutePolicy;
import org.apache.camel.support.cluster.AbstractCamelClusterService;
import org.apache.camel.support.cluster.AbstractCamelClusterView;
import org.apache.camel.support.cluster.RebalancingCamelClusterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rebalancing cluster service looks at the views of the delegate on every pass; it must not keep a reference to
 * them, nor start a view again that its last user has released.
 */
public class RebalancingClusterServiceReleaseViewTest {

    private CamelContext context;
    private TestClusterService delegate;
    private TestRebalancingClusterService service;

    @BeforeEach
    public void setUp() throws Exception {
        context = new DefaultCamelContext();
        delegate = new TestClusterService("node-1");
        // a long period, so the only passes are the one at start-up and the ones the tests run
        service = new TestRebalancingClusterService(delegate);
        context.addService(service);
    }

    @AfterEach
    public void tearDown() {
        context.stop();
    }

    @Test
    public void testRemovedRouteStopsView() throws Exception {
        ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace(service, "my-ns");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo").routeId("foo").routePolicy(policy).to("mock:foo");
            }
        });
        context.start();
        assertTrue(service.firstPass.await(10, TimeUnit.SECONDS));

        TestClusterView view = delegate.view("my-ns");
        assertTrue(view.isRunAllowed());

        // a rebalancing pass while the route uses the view
        service.reconcile();
        assertTrue(view.isRunAllowed());

        context.getRouteController().stopRoute("foo");
        context.removeRoute("foo");

        // no route uses the namespace any more, so this node must leave the election
        assertEquals(ServiceStatus.Stopped, view.getStatus());
    }

    @Test
    public void testRebalancingDoesNotStartReleasedView() throws Exception {
        context.start();
        assertTrue(service.firstPass.await(10, TimeUnit.SECONDS));

        CamelClusterView used = delegate.getView("my-ns");
        TestClusterView view = delegate.view("my-ns");
        assertTrue(view.isRunAllowed());
        delegate.releaseView(used);
        assertEquals(ServiceStatus.Stopped, view.getStatus());
        int starts = view.starts.get();

        service.reconcile();

        // not even started and stopped again within the pass (that would take the leadership for a moment)
        assertEquals(ServiceStatus.Stopped, view.getStatus());
        assertEquals(starts, view.starts.get());
    }

    @Test
    public void testRebalancingStillOpensViewInUse() throws Exception {
        context.start();
        assertTrue(service.firstPass.await(10, TimeUnit.SECONDS));

        CamelClusterView used = delegate.getView("my-ns");
        TestClusterView view = delegate.view("my-ns");
        view.setDisabled(true);

        // one member and one partition that this member does not own: the pass opens it
        service.reconcile();

        assertFalse(view.isDisabled());
        assertTrue(view.isRunAllowed());
        delegate.releaseView(used);
        assertEquals(ServiceStatus.Stopped, view.getStatus());
    }

    // *********************************
    // Helpers
    // *********************************

    private final class TestRebalancingClusterService extends RebalancingCamelClusterService {
        private final CountDownLatch firstPass = new CountDownLatch(1);

        TestRebalancingClusterService(CamelPreemptiveClusterService delegate) {
            super(delegate, TimeUnit.HOURS.toMillis(1));
        }

        @Override
        protected void reconcile() {
            try {
                super.reconcile();
            } finally {
                firstPass.countDown();
            }
        }
    }

    private static final class TestClusterView extends AbstractCamelClusterView implements CamelPreemptiveClusterView {
        private final CamelClusterMember local;
        private final AtomicInteger starts = new AtomicInteger();
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
            starts.incrementAndGet();
        }

        @Override
        protected void doStop() {
        }
    }

    private static final class TestClusterService extends AbstractCamelClusterService<TestClusterView>
            implements CamelPreemptiveClusterService {
        private final Map<String, TestClusterView> created = new ConcurrentHashMap<>();

        TestClusterService(String id) {
            super(id);
        }

        @Override
        protected TestClusterView createView(String namespace) {
            return created.computeIfAbsent(namespace, ns -> new TestClusterView(this, ns));
        }

        @Override
        public CamelPreemptiveClusterView getView(String namespace) throws Exception {
            return (CamelPreemptiveClusterView) super.getView(namespace);
        }

        TestClusterView view(String namespace) {
            return created.get(namespace);
        }
    }
}
