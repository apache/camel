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
package org.apache.camel.component.zookeepermaster;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.component.zookeepermaster.group.Group;
import org.apache.camel.component.zookeepermaster.group.ManagedGroupFactory;
import org.apache.camel.component.zookeepermaster.group.NodeState;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.curator.framework.AuthInfo;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.retry.RetryOneTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The client and the group factory of a ZookeeperGroupSupport across a stop and a start (no ZooKeeper: the client is
 * created and started, and does not connect).
 */
class ZookeeperGroupSupportLifecycleTest extends CamelTestSupport {

    ZookeeperGroupSupportLifecycleTest() {
        testConfiguration().withUseRouteBuilder(false);
    }

    @Test
    void testCreatedClientIsNotReusedAfterAStop() {
        ZookeeperGroupSupport<CamelNodeState> support = newSupport();
        support.start();
        CuratorFramework first = support.getCurator();
        assertEquals(CuratorFrameworkState.STARTED, first.getState());

        support.stop();
        assertEquals(CuratorFrameworkState.STOPPED, first.getState());

        // started again, it creates a new client, not the one its stop closed
        support.start();
        try {
            CuratorFramework second = support.getCurator();
            assertNotSame(first, second);
            assertEquals(CuratorFrameworkState.STARTED, second.getState());
        } finally {
            support.stop();
        }
    }

    @Test
    void testClientSetByTheUserIsKeptAfterAStop() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.builder().connectString("localhost:1")
                .retryPolicy(new RetryOneTime(100)).build();
        client.start();
        try {
            ZookeeperGroupSupport<CamelNodeState> support = newSupport();
            support.setCurator(client);
            support.start();
            assertSame(client, support.getCurator());
            support.stop();

            // the client of the user is not closed, and is used again by the next start
            assertEquals(CuratorFrameworkState.STARTED, client.getState());
            support.start();
            assertSame(client, support.getCurator());
            support.stop();
            assertEquals(CuratorFrameworkState.STARTED, client.getState());
        } finally {
            client.close();
        }
    }

    @Test
    void testGroupFactoryOfTheRegistryIsNotClosedByAStop() {
        FakeGroupFactory factory = new FakeGroupFactory();
        context.getRegistry().bind("groupFactory", factory);

        ZookeeperGroupSupport<CamelNodeState> support = newSupport();
        support.start();
        assertSame(factory, support.getManagedGroupFactory());
        support.stop();

        // the factory of the registry may be shared by other consumers and route policies: it stays open
        assertEquals(0, factory.closes.get());
        support.start();
        assertSame(factory, support.getManagedGroupFactory());
        support.stop();
        assertEquals(0, factory.closes.get());
    }

    @Test
    void testPasswordFromTheSystemProperty() throws Exception {
        String old = System.getProperty("zookeeper.password");
        System.setProperty("zookeeper.password", "secret");
        CuratorFramework curator = null;
        try {
            ZookeeperGroupSupport<CamelNodeState> support = newSupport();
            curator = support.call();

            Field field = curator.getClass().getDeclaredField("authInfos");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<AuthInfo> authInfos = (List<AuthInfo>) field.get(curator);
            assertEquals(1, authInfos.size());
            assertEquals("digest", authInfos.get(0).getScheme());
            assertEquals("fabric:secret", new String(authInfos.get(0).getAuth(), StandardCharsets.UTF_8));
        } finally {
            if (curator != null) {
                curator.close();
            }
            if (old == null) {
                System.clearProperty("zookeeper.password");
            } else {
                System.setProperty("zookeeper.password", old);
            }
        }
    }

    private ZookeeperGroupSupport<CamelNodeState> newSupport() {
        ZookeeperGroupSupport<CamelNodeState> support = new ZookeeperGroupSupport<>();
        support.setCamelContext(context);
        // no ZooKeeper there: the client starts and does not connect
        support.setZooKeeperUrl("localhost:1");
        support.setMaximumConnectionTimeout(100);
        return support;
    }

    private static final class FakeGroupFactory implements ManagedGroupFactory {
        final AtomicInteger closes = new AtomicInteger();

        @Override
        public <T extends NodeState> Group<T> createGroup(String path, Class<T> clazz) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T extends NodeState> Group<T> createGroup(String path, Class<T> clazz, ThreadFactory threadFactory) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T extends NodeState> Group<T> createMultiGroup(String path, Class<T> clazz) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T extends NodeState> Group<T> createMultiGroup(String path, Class<T> clazz, ThreadFactory threadFactory) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CuratorFramework getCurator() {
            return null;
        }

        @Override
        public void close() {
            closes.incrementAndGet();
        }
    }
}
