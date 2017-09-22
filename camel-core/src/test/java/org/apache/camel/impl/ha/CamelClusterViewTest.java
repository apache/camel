/**
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
package org.apache.camel.impl.ha;

import java.util.List;
import java.util.Optional;

import org.apache.camel.ServiceStatus;
import org.apache.camel.ha.CamelClusterMember;
import org.apache.camel.ha.CamelClusterService;
import org.junit.Assert;
import org.junit.Test;

public class CamelClusterViewTest {

    @Test
    public void testEquality() throws Exception {
        TestClusterService service = new TestClusterService();
        TestClusterView view1 = service.getView("ns1").unwrap(TestClusterView.class);
        TestClusterView view2 = service.getView("ns1").unwrap(TestClusterView.class);
        TestClusterView view3 = service.getView("ns2").unwrap(TestClusterView.class);

        Assert.assertEquals(view1, view2);
        Assert.assertNotEquals(view1, view3);
    }

    @Test
    public void testReferences() throws Exception {
        TestClusterService service = new TestClusterService();
        service.start();

        TestClusterView view1 = service.getView("ns1").unwrap(TestClusterView.class);
        TestClusterView view2 = service.getView("ns1").unwrap(TestClusterView.class);
        TestClusterView view3 = service.getView("ns2").unwrap(TestClusterView.class);

        Assert.assertEquals(ServiceStatus.Started, view1.getStatus());
        Assert.assertEquals(ServiceStatus.Started, view2.getStatus());
        Assert.assertEquals(ServiceStatus.Started, view3.getStatus());

        service.releaseView(view1);

        Assert.assertEquals(ServiceStatus.Started, view1.getStatus());
        Assert.assertEquals(ServiceStatus.Started, view2.getStatus());
        Assert.assertEquals(ServiceStatus.Started, view3.getStatus());

        service.releaseView(view2);

        Assert.assertEquals(ServiceStatus.Stopped, view1.getStatus());
        Assert.assertEquals(ServiceStatus.Stopped, view2.getStatus());
        Assert.assertEquals(ServiceStatus.Started, view3.getStatus());

        service.releaseView(view3);

        TestClusterView newView1 = service.getView("ns1").unwrap(TestClusterView.class);
        TestClusterView newView2 = service.getView("ns1").unwrap(TestClusterView.class);

        Assert.assertEquals(newView1, newView2);
        Assert.assertEquals(view1, newView1);
        Assert.assertEquals(view1, newView2);

        Assert.assertEquals(ServiceStatus.Started, newView1.getStatus());
        Assert.assertEquals(ServiceStatus.Started, newView2.getStatus());
        Assert.assertEquals(ServiceStatus.Stopped, view3.getStatus());

        service.stop();

        Assert.assertEquals(ServiceStatus.Stopped, view1.getStatus());
        Assert.assertEquals(ServiceStatus.Stopped, view2.getStatus());
        Assert.assertEquals(ServiceStatus.Stopped, view3.getStatus());
        Assert.assertEquals(ServiceStatus.Stopped, newView1.getStatus());
        Assert.assertEquals(ServiceStatus.Stopped, newView2.getStatus());
    }

    // *********************************
    // Helpers
    // *********************************

    private static class TestClusterView extends AbstractCamelClusterView {

        public TestClusterView(CamelClusterService cluster, String namespace) {
            super(cluster, namespace);
        }

        @Override
        public Optional<CamelClusterMember> getMaster() {
            return null;
        }

        @Override
        public CamelClusterMember getLocalMember() {
            return null;
        }

        @Override
        public List<CamelClusterMember> getMembers() {
            return null;
        }

        @Override
        protected void doStart() throws Exception {
        }

        @Override
        protected void doStop() throws Exception {
        }
    }

    private static class TestClusterService extends AbstractCamelClusterService<TestClusterView> {
        @Override
        protected TestClusterView createView(String namespace) throws Exception {
            return new TestClusterView(this, namespace);
        }
    }
}
