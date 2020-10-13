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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.apache.camel.ServiceStatus;
import org.apache.camel.support.cluster.AbstractCamelClusterService;
import org.apache.camel.support.cluster.AbstractCamelClusterView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ClusterServiceViewTest {

    @Test
    public void testViewEquality() throws Exception {
        TestClusterService service = new TestClusterService(UUID.randomUUID().toString());
        TestClusterView view1 = service.getView("ns1").unwrap(TestClusterView.class);
        TestClusterView view2 = service.getView("ns1").unwrap(TestClusterView.class);
        TestClusterView view3 = service.getView("ns2").unwrap(TestClusterView.class);

        assertEquals(view1, view2);
        assertNotEquals(view1, view3);
    }

    @Test
    public void testViewReferences() throws Exception {
        TestClusterService service = new TestClusterService(UUID.randomUUID().toString());
        service.start();

        TestClusterView view1 = service.getView("ns1").unwrap(TestClusterView.class);
        TestClusterView view2 = service.getView("ns1").unwrap(TestClusterView.class);
        TestClusterView view3 = service.getView("ns2").unwrap(TestClusterView.class);

        assertEquals(ServiceStatus.Started, view1.getStatus());
        assertEquals(ServiceStatus.Started, view2.getStatus());
        assertEquals(ServiceStatus.Started, view3.getStatus());

        service.releaseView(view1);

        assertEquals(ServiceStatus.Started, view1.getStatus());
        assertEquals(ServiceStatus.Started, view2.getStatus());
        assertEquals(ServiceStatus.Started, view3.getStatus());

        service.releaseView(view2);

        assertEquals(ServiceStatus.Stopped, view1.getStatus());
        assertEquals(ServiceStatus.Stopped, view2.getStatus());
        assertEquals(ServiceStatus.Started, view3.getStatus());

        service.releaseView(view3);

        TestClusterView newView1 = service.getView("ns1").unwrap(TestClusterView.class);
        TestClusterView newView2 = service.getView("ns1").unwrap(TestClusterView.class);

        assertEquals(newView1, newView2);
        assertEquals(view1, newView1);
        assertEquals(view1, newView2);

        assertEquals(ServiceStatus.Started, newView1.getStatus());
        assertEquals(ServiceStatus.Started, newView2.getStatus());
        assertEquals(ServiceStatus.Stopped, view3.getStatus());

        service.stop();

        assertEquals(ServiceStatus.Stopped, view1.getStatus());
        assertEquals(ServiceStatus.Stopped, view2.getStatus());
        assertEquals(ServiceStatus.Stopped, view3.getStatus());
        assertEquals(ServiceStatus.Stopped, newView1.getStatus());
        assertEquals(ServiceStatus.Stopped, newView2.getStatus());
    }

    @Test
    public void testViewForceOperations() throws Exception {
        TestClusterService service = new TestClusterService(UUID.randomUUID().toString());
        TestClusterView view = service.getView("ns1").unwrap(TestClusterView.class);

        assertEquals(ServiceStatus.Stopped, view.getStatus());

        // This should not start the view as the service has not yet started.
        service.startView(view.getNamespace());

        assertEquals(ServiceStatus.Stopped, view.getStatus());

        // This should start the view.
        service.start();

        assertEquals(ServiceStatus.Started, view.getStatus());

        service.stopView(view.getNamespace());
        assertEquals(ServiceStatus.Stopped, view.getStatus());

        service.startView(view.getNamespace());
        assertEquals(ServiceStatus.Started, view.getStatus());

        service.releaseView(view);
        assertEquals(ServiceStatus.Stopped, view.getStatus());
    }

    @Test
    public void testMultipleViewListeners() throws Exception {
        final TestClusterService service = new TestClusterService(UUID.randomUUID().toString());
        final TestClusterView view = service.getView("ns1").unwrap(TestClusterView.class);
        final int events = 1 + new Random().nextInt(10);
        final Set<Integer> results = new HashSet<>();
        final CountDownLatch latch = new CountDownLatch(events);

        IntStream.range(0, events).forEach(i -> view.addEventListener((CamelClusterEventListener.Leadership) (v, l) -> {
            results.add(i);
            latch.countDown();
        }));

        service.start();
        view.setLeader(true);

        latch.await(10, TimeUnit.SECONDS);

        IntStream.range(0, events).forEach(i -> assertTrue(results.contains(i)));
    }

    @Test
    public void testLateViewListeners() throws Exception {
        final TestClusterService service = new TestClusterService(UUID.randomUUID().toString());
        final TestClusterView view = service.getView("ns1").unwrap(TestClusterView.class);
        final int events = 1 + new Random().nextInt(10);
        final Set<Integer> results = new HashSet<>();
        final CountDownLatch latch = new CountDownLatch(events * 2);

        IntStream.range(0, events).forEach(i -> view.addEventListener((CamelClusterEventListener.Leadership) (v, l) -> {
            results.add(i);
            latch.countDown();
        }));

        service.start();
        view.setLeader(true);

        IntStream.range(events, events * 2)
                .forEach(i -> view.addEventListener((CamelClusterEventListener.Leadership) (v, l) -> {
                    results.add(i);
                    latch.countDown();
                }));

        latch.await(10, TimeUnit.SECONDS);

        IntStream.range(0, events * 2).forEach(i -> assertTrue(results.contains(i)));
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
        public TestClusterService(String id) {
            super(id);
        }

        @Override
        protected TestClusterView createView(String namespace) throws Exception {
            return new TestClusterView(this, namespace);
        }
    }
}
