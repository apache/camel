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
import org.apache.camel.component.file.cluster.FileLockClusterService;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.cluster.AbstractCamelClusterService;
import org.apache.camel.support.cluster.AbstractCamelClusterView;
import org.apache.camel.support.cluster.ClusterServiceSelectors;
import org.junit.jupiter.api.Test;

import static org.apache.camel.support.cluster.ClusterServiceHelper.lookupService;
import static org.apache.camel.support.cluster.ClusterServiceHelper.mandatoryLookupService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClusterServiceSelectorTest {

    @Test
    public void testDefaultSelector() throws Exception {
        CamelContext context = null;

        try {
            DummyClusterService1 service1 = new DummyClusterService1();

            context = new DefaultCamelContext();
            context.addService(service1);

            Optional<CamelClusterService> lookup = lookupService(context);

            assertTrue(lookup.isPresent());
            assertEquals(service1, lookup.get());
        } finally {
            if (context != null) {
                context.stop();
                assertTrue(context.isStopped());
            }
        }
    }

    @Test
    public void testDefaultSelectorFailure() throws Exception {
        CamelContext context = null;

        try {
            context = new DefaultCamelContext();
            context.addService(new DummyClusterService1());
            context.addService(new DummyClusterService2());

            Optional<CamelClusterService> lookup = lookupService(context);

            assertFalse(lookup.isPresent());
        } finally {
            if (context != null) {
                context.stop();
                assertTrue(context.isStopped());
            }
        }
    }

    @Test
    public void testSelectSingle() throws Exception {
        CamelContext context = null;

        try {
            DummyClusterService1 service1 = new DummyClusterService1();

            context = new DefaultCamelContext();
            context.addService(service1);

            CamelClusterService.Selector selector = ClusterServiceSelectors.single();
            Optional<CamelClusterService> lookup = lookupService(context, selector);

            assertTrue(lookup.isPresent());
            assertEquals(service1, lookup.get());
        } finally {
            if (context != null) {
                context.stop();
                assertTrue(context.isStopped());
            }
        }
    }

    @Test
    public void testSelectSingleFailure() throws Exception {
        CamelContext context = null;

        try {
            context = new DefaultCamelContext();
            context.addService(new DummyClusterService1());
            context.addService(new DummyClusterService2());

            CamelClusterService.Selector selector = ClusterServiceSelectors.single();
            Optional<CamelClusterService> lookup = lookupService(context, selector);

            assertFalse(lookup.isPresent());
        } finally {
            if (context != null) {
                context.stop();
                assertTrue(context.isStopped());
            }
        }
    }

    @Test
    public void testSelectFirst() throws Exception {
        CamelContext context = null;

        try {
            context = new DefaultCamelContext();
            context.addService(new DummyClusterService1());
            context.addService(new DummyClusterService2());

            CamelClusterService.Selector selector = ClusterServiceSelectors.first();
            Optional<CamelClusterService> lookup = lookupService(context, selector);

            assertTrue(lookup.isPresent());
        } finally {
            if (context != null) {
                context.stop();
                assertTrue(context.isStopped());
            }
        }
    }

    @Test
    public void testSelectByType() throws Exception {
        CamelContext context = null;

        try {
            context = new DefaultCamelContext();
            context.addService(new DummyClusterService1());
            context.addService(new DummyClusterService2());

            assertTrue(lookupService(context, ClusterServiceSelectors.type(DummyClusterService1.class)).isPresent());
            assertTrue(lookupService(context, ClusterServiceSelectors.type(DummyClusterService2.class)).isPresent());
            assertFalse(lookupService(context, ClusterServiceSelectors.type(FileLockClusterService.class)).isPresent());

        } finally {
            if (context != null) {
                context.stop();
                assertTrue(context.isStopped());
            }
        }
    }

    @Test
    public void testSelectByAttribute() throws Exception {
        CamelContext context = null;

        try {
            DummyClusterService1 service1 = new DummyClusterService1();
            service1.setAttribute("service.type", "zookeeper");

            DummyClusterService2 service2 = new DummyClusterService2();
            service2.setAttribute("service.type", "file");

            context = new DefaultCamelContext();
            context.addService(service1);
            context.addService(service2);

            Optional<CamelClusterService> lookup;

            lookup = lookupService(context, ClusterServiceSelectors.attribute("service.type", "zookeeper"));
            assertTrue(lookup.isPresent());
            assertEquals(service1, lookup.get());

            lookup = lookupService(context, ClusterServiceSelectors.attribute("service.type", "file"));
            assertTrue(lookup.isPresent());
            assertEquals(service2, lookup.get());

            lookup = lookupService(context, ClusterServiceSelectors.attribute("service.type", "consul"));
            assertFalse(lookup.isPresent());

        } finally {
            if (context != null) {
                context.stop();
                assertTrue(context.isStopped());
            }
        }
    }

    @Test
    public void testSelectByOrder() throws Exception {
        CamelContext context = null;

        try {
            DummyClusterService1 service1 = new DummyClusterService1();
            service1.setOrder(1);

            DummyClusterService2 service2 = new DummyClusterService2();
            service2.setOrder(0);

            context = new DefaultCamelContext();
            context.addService(service1);
            context.addService(service2);

            CamelClusterService.Selector selector = ClusterServiceSelectors.order();
            Optional<CamelClusterService> lookup = lookupService(context, selector);

            assertTrue(lookup.isPresent());
            assertEquals(service2, lookup.get());

        } finally {
            if (context != null) {
                context.stop();
                assertTrue(context.isStopped());
            }
        }
    }

    @Test
    public void testSelectByOrderFailure() throws Exception {
        CamelContext context = null;

        try {
            DummyClusterService1 service1 = new DummyClusterService1();
            service1.setOrder(1);

            DummyClusterService2 service2 = new DummyClusterService2();
            service2.setOrder(0);

            DummyClusterService2 service3 = new DummyClusterService2();
            service3.setOrder(0);

            context = new DefaultCamelContext();
            context.addService(service1);
            context.addService(service2);
            context.addService(service3);

            CamelClusterService.Selector selector = ClusterServiceSelectors.order();
            Optional<CamelClusterService> lookup = lookupService(context, selector);

            assertFalse(lookup.isPresent());
        } finally {
            if (context != null) {
                context.stop();
                assertTrue(context.isStopped());
            }
        }
    }

    @Test
    public void testMandatoryLookup() throws Exception {
        CamelContext context = null;

        try {
            DummyClusterService1 service1 = new DummyClusterService1();

            context = new DefaultCamelContext();
            context.addService(service1);

            CamelClusterService.Selector selector = ClusterServiceSelectors.single();
            CamelClusterService lookup = mandatoryLookupService(context, selector);

            assertNotNull(lookup);
            assertEquals(service1, lookup);
        } finally {
            if (context != null) {
                context.stop();
                assertTrue(context.isStopped());
            }
        }
    }

    @Test
    public void testMandatoryLookupWithoutSelector() throws Exception {
        CamelContext context = null;

        try {
            DummyClusterService1 service1 = new DummyClusterService1();

            context = new DefaultCamelContext();
            context.addService(service1);

            CamelClusterService lookup = mandatoryLookupService(context);

            assertNotNull(lookup);
            assertEquals(service1, lookup);
        } finally {
            if (context != null) {
                context.stop();
                assertTrue(context.isStopped());
            }
        }
    }

    @Test
    public void testMandatoryLookupFailure() throws Exception {
        CamelContext context = null;

        try {
            CamelContext ctx = context = new DefaultCamelContext();

            assertThrows(IllegalStateException.class, () -> mandatoryLookupService(ctx, ClusterServiceSelectors.single()));
        } finally {
            if (context != null) {
                context.stop();
                assertTrue(context.isStopped());
            }
        }
    }

    @Test
    public void testMandatoryLookupFailureWithoutSelector() throws Exception {
        CamelContext context = null;

        try {
            CamelContext ctx = context = new DefaultCamelContext();

            assertThrows(IllegalStateException.class, () -> mandatoryLookupService(ctx));
        } finally {
            if (context != null) {
                context.stop();
                assertTrue(context.isStopped());
            }
        }
    }

    // **************************************
    // Helpers
    // **************************************

    private static final class DummyClusterService1 extends AbstractCamelClusterService {
        public DummyClusterService1() {
        }

        @Override
        protected CamelClusterView createView(String namespace) throws Exception {
            return new DummyClusterServiceView(this, namespace);
        }
    }

    private static final class DummyClusterService2 extends AbstractCamelClusterService {
        public DummyClusterService2() {
        }

        @Override
        protected CamelClusterView createView(String namespace) throws Exception {
            return new DummyClusterServiceView(this, namespace);
        }
    }

    private static final class DummyClusterServiceView extends AbstractCamelClusterView {

        public DummyClusterServiceView(CamelClusterService cluster, String namespace) {
            super(cluster, namespace);
        }

        @Override
        public Optional<CamelClusterMember> getLeader() {
            return Optional.empty();
        }

        @Override
        public CamelClusterMember getLocalMember() {
            return new DummyClusterServiceMember(false, true);
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

        private final class DummyClusterServiceMember implements CamelClusterMember {
            private final boolean leader;
            private final boolean local;

            public DummyClusterServiceMember(boolean leader, boolean local) {
                this.leader = leader;
                this.local = local;
            }

            @Override
            public boolean isLeader() {
                return leader;
            }

            @Override
            public boolean isLocal() {
                return local;
            }

            @Override
            public String getId() {
                return getClusterService().getId();
            }
        }
    }
}
