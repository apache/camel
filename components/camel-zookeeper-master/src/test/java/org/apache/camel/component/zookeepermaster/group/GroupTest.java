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
package org.apache.camel.component.zookeepermaster.group;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.component.zookeepermaster.ZKContainer;
import org.apache.camel.component.zookeepermaster.group.internal.ChildData;
import org.apache.camel.component.zookeepermaster.group.internal.ZooKeeperGroup;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryNTimes;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.SelinuxContext;


import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.util.AssertionErrors.assertNotEquals;

public class GroupTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(GroupTest.class);

    private static String beforeTmpdir;

    private GroupListener listener = new GroupListener<NodeState>() {
        @Override
        public void groupEvent(Group<NodeState> group, GroupListener.GroupEvent event) {
            boolean connected = group.isConnected();
            boolean master = group.isMaster();
            if (connected) {
                Collection<NodeState> members = group.members().values();
                LOGGER.info("GroupEvent: " + event + " (connected=" + connected + ", master=" + master + ", members=" + members + ")");
            } else {
                LOGGER.info("GroupEvent: " + event + " (connected=" + connected + ", master=false)");
            }
        }
    };

    private ZKContainer startZooKeeper(int port, Path root) throws Exception {
        LOGGER.info("****************************************");
        LOGGER.info("* Starting ZooKeeper container         *");
        LOGGER.info("****************************************");

        ZKContainer container = new ZKContainer(port);
        container.withNetworkAliases("zk-" + port);

        if (root != null) {
            Path data = root.resolve("data");
            Path datalog = root.resolve("datalog");

            if (!Files.exists(data)) {
                Files.createDirectories(data);
            }
            if (!Files.exists(datalog)) {
                Files.createDirectories(datalog);
            }

            LOGGER.debug("data: {}", data);
            LOGGER.debug("datalog: {}", datalog);

            container.addFileSystemBind(data.toAbsolutePath().toString(), "/data", BindMode.READ_WRITE, SelinuxContext.SHARED);
            container.addFileSystemBind(datalog.toAbsolutePath().toString(), "/datalog", BindMode.READ_WRITE, SelinuxContext.SHARED);
        }

        container.start();

        LOGGER.info("****************************************");
        LOGGER.info("* ZooKeeper container started          *");
        LOGGER.info("****************************************");

        return container;
    }

    @BeforeClass
    public static void before() {
        // workaround macos issue with docker/testcontainers expecting to use /tmp/ folder
        beforeTmpdir = System.setProperty("java.io.tmpdir", "/tmp/");
    }

    @AfterClass
    public static void after() {
        if (beforeTmpdir != null) {
            System.setProperty("java.io.tmpdir", beforeTmpdir);
        }
    }

    @Test
    public void testOrder() throws Exception {
        int port = AvailablePortFinder.getNextAvailable();

        CuratorFramework curator = CuratorFrameworkFactory.builder()
                .connectString("localhost:" + port)
                .retryPolicy(new RetryNTimes(10, 100))
                .build();
        curator.start();


        final String path = "/singletons/test/Order" + System.currentTimeMillis();
        ArrayList<ZooKeeperGroup> members = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ZooKeeperGroup<NodeState> group = new ZooKeeperGroup<>(curator, path, NodeState.class);
            group.add(listener);
            members.add(group);
        }

        for (ZooKeeperGroup group : members) {
            assertFalse(group.isConnected());
            assertFalse(group.isMaster());
        }

        ZKContainer container = null;
        Path dataDir = Files.createTempDirectory("zk-");

        try {
            container = startZooKeeper(port, dataDir);

            curator.getZookeeperClient().blockUntilConnectedOrTimedOut();

            // first to start should be master if members are ordered...
            int i = 0;
            for (ZooKeeperGroup group : members) {
                group.start();
                group.update(new NodeState("foo" + i));
                i++;

                // wait for registration
                while (group.getId() == null) {
                    TimeUnit.MILLISECONDS.sleep(100);
                }
            }

            boolean firsStartedIsMaster = members.get(0).isMaster();

            for (ZooKeeperGroup group : members) {
                group.close();
            }
            curator.close();

            assertTrue("first started is master", firsStartedIsMaster);
        } finally {
            if (container != null) {
                container.stop();
            }

            CamelTestSupport.deleteDirectory(dataDir.toFile());
        }

    }

    @Test
    public void testJoinAfterConnect() throws Exception {
        int port = AvailablePortFinder.getNextAvailable();

        CuratorFramework curator = CuratorFrameworkFactory.builder()
                .connectString("localhost:" + port)
                .retryPolicy(new RetryNTimes(10, 100))
                .build();
        curator.start();

        final Group<NodeState> group = new ZooKeeperGroup<>(curator, "/singletons/test" + System.currentTimeMillis(), NodeState.class);
        group.add(listener);
        group.start();

        assertFalse(group.isConnected());
        assertFalse(group.isMaster());

        GroupCondition groupCondition = new GroupCondition();
        group.add(groupCondition);

        ZKContainer container = null;
        Path dataDir = Files.createTempDirectory("zk-");

        try {
            container = startZooKeeper(port, dataDir);

            curator.getZookeeperClient().blockUntilConnectedOrTimedOut();

            assertTrue(groupCondition.waitForConnected(5, TimeUnit.SECONDS));
            assertFalse(group.isMaster());

            group.update(new NodeState("foo"));
            assertTrue(groupCondition.waitForMaster(5, TimeUnit.SECONDS));


            group.close();
            curator.close();
        } finally {
            if (container != null) {
                container.stop();
            }

            try {
                CamelTestSupport.deleteDirectory(dataDir.toFile());
            } catch (Throwable e) {
                // ignore
            }
        }
    }

    @Test
    public void testJoinBeforeConnect() throws Exception {
        int port = AvailablePortFinder.getNextAvailable();

        CuratorFramework curator = CuratorFrameworkFactory.builder()
                .connectString("localhost:" + port)
                .retryPolicy(new RetryNTimes(10, 100))
                .build();
        curator.start();

        Group<NodeState> group = new ZooKeeperGroup<>(curator, "/singletons/test" + System.currentTimeMillis(), NodeState.class);
        group.add(listener);
        group.start();

        GroupCondition groupCondition = new GroupCondition();
        group.add(groupCondition);

        assertFalse(group.isConnected());
        assertFalse(group.isMaster());
        group.update(new NodeState("foo"));

        ZKContainer container = null;
        Path dataDir = Files.createTempDirectory("zk-");

        try {
            container = startZooKeeper(port, dataDir);

            curator.getZookeeperClient().blockUntilConnectedOrTimedOut();

            assertTrue(groupCondition.waitForConnected(5, TimeUnit.SECONDS));
            assertTrue(groupCondition.waitForMaster(5, TimeUnit.SECONDS));

            group.close();
            curator.close();
        } finally {
            if (container != null) {
                container.stop();
            }

            CamelTestSupport.deleteDirectory(dataDir.toFile());
        }
    }

    @Test
    public void testRejoinAfterDisconnect() throws Exception {
        int port = AvailablePortFinder.getNextAvailable();

        CuratorFramework curator = CuratorFrameworkFactory.builder()
                .connectString("localhost:" + port)
                .retryPolicy(new RetryNTimes(10, 100))
                .build();
        curator.start();

        ZKContainer container = null;
        Path dataDir = Files.createTempDirectory("zk-");

        try {
            container = startZooKeeper(port, dataDir);
            Group<NodeState> group = new ZooKeeperGroup<>(curator, "/singletons/test" + System.currentTimeMillis(), NodeState.class);
            group.add(listener);
            group.update(new NodeState("foo"));
            group.start();

            GroupCondition groupCondition = new GroupCondition();
            group.add(groupCondition);

            curator.getZookeeperClient().blockUntilConnectedOrTimedOut();
            assertTrue(groupCondition.waitForConnected(5, TimeUnit.SECONDS));
            assertTrue(groupCondition.waitForMaster(5, TimeUnit.SECONDS));

            container.stop();

            groupCondition.waitForDisconnected(5, TimeUnit.SECONDS);
            group.remove(groupCondition);

            assertFalse(group.isConnected());
            assertFalse(group.isMaster());

            groupCondition = new GroupCondition();
            group.add(groupCondition);

            container = startZooKeeper(port, dataDir);

            curator.getZookeeperClient().blockUntilConnectedOrTimedOut();
            curator.getZookeeperClient().blockUntilConnectedOrTimedOut();
            assertTrue(groupCondition.waitForConnected(5, TimeUnit.SECONDS));
            assertTrue(groupCondition.waitForMaster(5, TimeUnit.SECONDS));

            group.close();
            curator.close();
        } finally {
            if (container != null) {
                container.stop();
            }

            CamelTestSupport.deleteDirectory(dataDir.toFile());
        }
    }

    //Tests that if close() is executed right after start(), there are no left over entries.
    //(see  https://github.com/jboss-fuse/fuse/issues/133)
    @Test
    public void testGroupClose() throws Exception {
        int port = AvailablePortFinder.getNextAvailable();
        ZKContainer container = null;
        Path dataDir = Files.createTempDirectory("zk-");

        try {
            container = startZooKeeper(port, dataDir);

            CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
                .connectString("localhost:" + port)
                .connectionTimeoutMs(6000)
                .sessionTimeoutMs(6000)
                .retryPolicy(new RetryNTimes(10, 100));
            CuratorFramework curator = builder.build();
            curator.start();
            curator.getZookeeperClient().blockUntilConnectedOrTimedOut();
            String groupNode = "/singletons/test" + System.currentTimeMillis();
            curator.create().creatingParentsIfNeeded().forPath(groupNode);

            for (int i = 0; i < 100; i++) {
                ZooKeeperGroup<NodeState> group = new ZooKeeperGroup<>(curator, groupNode, NodeState.class);
                group.add(listener);
                group.update(new NodeState("foo"));
                group.start();
                group.close();
                List<String> entries = curator.getChildren().forPath(groupNode);
                assertTrue(entries.isEmpty() || group.isUnstable());
                if (group.isUnstable()) {
                    // let's wait for session timeout
                    curator.close();
                    curator = builder.build();
                    curator.start();
                    curator.getZookeeperClient().blockUntilConnectedOrTimedOut();
                }
            }

            curator.close();
        } finally {
            if (container != null) {
                container.stop();
            }

            CamelTestSupport.deleteDirectory(dataDir.toFile());
        }
    }

    @Test
    public void testAddFieldIgnoredOnParse() throws Exception {

        int port = AvailablePortFinder.getNextAvailable();
        ZKContainer container = null;
        Path dataDir = Files.createTempDirectory("zk-");

        try {
            container = startZooKeeper(port, dataDir);

            CuratorFramework curator = CuratorFrameworkFactory.builder()
                    .connectString("localhost:" + port)
                    .retryPolicy(new RetryNTimes(10, 100))
                    .build();
            curator.start();
            curator.getZookeeperClient().blockUntilConnectedOrTimedOut();
            String groupNode = "/singletons/test" + System.currentTimeMillis();
            curator.create().creatingParentsIfNeeded().forPath(groupNode);

            curator.getZookeeperClient().blockUntilConnectedOrTimedOut();

            final ZooKeeperGroup<NodeState> group = new ZooKeeperGroup<>(curator, groupNode, NodeState.class);
            group.add(listener);
            group.start();

            GroupCondition groupCondition = new GroupCondition();
            group.add(groupCondition);

            group.update(new NodeState("foo"));

            assertTrue(groupCondition.waitForConnected(5, TimeUnit.SECONDS));
            assertTrue(groupCondition.waitForMaster(5, TimeUnit.SECONDS));

            ChildData currentData = group.getCurrentData().get(0);
            final int version = currentData.getStat().getVersion();

            NodeState lastState = group.getLastState();
            String json = lastState.toString();
            LOGGER.info("JSON:" + json);

            String newValWithNewField = json.substring(0, json.lastIndexOf('}')) + ",\"Rubbish\":\"Rubbish\"}";
            curator.getZookeeperClient().getZooKeeper().setData(group.getId(), newValWithNewField.getBytes(), version);

            assertTrue(group.isMaster());

            int attempts = 0;
            while (attempts++ < 5 && version == group.getCurrentData().get(0).getStat().getVersion()) {
                TimeUnit.SECONDS.sleep(1);
            }

            assertNotEquals("We see the updated version", version, group.getCurrentData().get(0).getStat().getVersion());

            LOGGER.info("CurrentData:" + group.getCurrentData());

            group.close();
            curator.close();
        } finally {
            if (container != null) {
                container.stop();
            }

            CamelTestSupport.deleteDirectory(dataDir.toFile());
        }
    }

    private class GroupCondition implements GroupListener<NodeState> {
        private CountDownLatch connected = new CountDownLatch(1);
        private CountDownLatch master = new CountDownLatch(1);
        private CountDownLatch disconnected = new CountDownLatch(1);

        @Override
        public void groupEvent(Group<NodeState> group, GroupEvent event) {
            switch (event) {
                case CONNECTED:
                case CHANGED:
                    connected.countDown();
                    if (group.isMaster()) {
                        master.countDown();
                    }
                    break;
                case DISCONNECTED:
                    disconnected.countDown();
                    break;
                default:
                    // noop
            }
        }

        public boolean waitForConnected(long time, TimeUnit timeUnit) throws InterruptedException {
            return connected.await(time, timeUnit);
        }

        public boolean waitForDisconnected(long time, TimeUnit timeUnit) throws InterruptedException {
            return disconnected.await(time, timeUnit);
        }

        public boolean waitForMaster(long time, TimeUnit timeUnit) throws InterruptedException {
            return master.await(time, timeUnit);
        }
    }
}
