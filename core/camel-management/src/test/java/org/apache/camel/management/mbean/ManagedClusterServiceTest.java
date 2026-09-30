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
package org.apache.camel.management.mbean;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.cluster.AbstractCamelClusterService;
import org.apache.camel.support.cluster.AbstractCamelClusterView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ManagedClusterServiceTest {

    @Test
    public void testOperationsUseTheManagedService() throws Exception {
        TestClusterService cs1 = new TestClusterService("cs1");
        TestClusterService cs2 = new TestClusterService("cs2");

        CamelContext context = new DefaultCamelContext();
        context.addService(cs1);
        context.addService(cs2);
        context.start();
        try {
            TestClusterView view = (TestClusterView) cs1.getView("ns1");
            cs2.getView("ns2");

            ManagedClusterService mbean = new ManagedClusterService(context, cs1);
            assertEquals(Set.of("ns1"), new HashSet<>(mbean.getNamespaces()));
            assertTrue(mbean.isLeader("ns1"));
            assertFalse(mbean.isLeader("ns2"));

            mbean.stopView("ns1");
            assertFalse(view.isRunAllowed());
            mbean.startView("ns1");
            assertTrue(view.isRunAllowed());
        } finally {
            context.stop();
        }
    }

    private static class TestClusterView extends AbstractCamelClusterView {

        TestClusterView(CamelClusterService cluster, String namespace) {
            super(cluster, namespace);
        }

        @Override
        public Optional<CamelClusterMember> getLeader() {
            return Optional.of(getLocalMember());
        }

        @Override
        public CamelClusterMember getLocalMember() {
            return new CamelClusterMember() {
                @Override
                public boolean isLeader() {
                    return true;
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
            // noop
        }

        @Override
        protected void doStop() {
            // noop
        }
    }

    private static class TestClusterService extends AbstractCamelClusterService<TestClusterView> {

        TestClusterService(String id) {
            super(id);
        }

        @Override
        protected TestClusterView createView(String namespace) {
            return new TestClusterView(this, namespace);
        }
    }
}
