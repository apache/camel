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
package org.apache.camel.component.zookeeper.integration;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.zookeeper.ZooKeeperMessage;
import org.apache.camel.test.infra.zookeeper.services.ZooKeeperService;
import org.apache.camel.test.infra.zookeeper.services.ZooKeeperServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ZooKeeperITSupport extends CamelTestSupport {
    @RegisterExtension
    static ZooKeeperService service = ZooKeeperServiceFactory.createService();

    protected String testPayload = "This is a test";
    protected byte[] testPayloadBytes = testPayload.getBytes();
    protected TestZookeeperClient client;

    @Override
    public void doPreSetup() throws Exception {
        client = new TestZookeeperClient(getConnectionString(), getTestClientSessionTimeout());
    }

    @Override
    protected void doPostTearDown() throws Exception {
        client.shutdown();
    }

    public String getConnectionString() {
        return service.getConnectionString();
    }

    public ZooKeeper getConnection() {
        return client.getConnection();
    }

    protected static int getTestClientSessionTimeout() {
        return 100000;
    }

    public static class TestZookeeperClient implements Watcher {
        private final Logger log = LoggerFactory.getLogger(getClass());
        private ZooKeeper zk;
        private CountDownLatch connected = new CountDownLatch(1);

        public TestZookeeperClient(String connectString, int timeout) throws Exception {
            zk = new ZooKeeper(connectString, timeout, this);
            connected.await();
        }

        public ZooKeeper getConnection() {
            return zk;
        }

        public void shutdown() throws Exception {
            zk.close();
        }

        public byte[] waitForNodeChange(String node) throws Exception {
            Stat stat = new Stat();
            return zk.getData(node, this, stat);
        }

        public void create(String node, String data) throws Exception {
            log.debug(String.format("Creating node '%s' with data '%s' ", node, data));
            create(node, data, Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        }

        public void createPersistent(String node, String data) throws Exception {
            log.debug(String.format("Creating node '%s' with data '%s' ", node, data));
            create(node, data, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }

        public void create(String znode, String data, List<ACL> access, CreateMode mode) throws Exception {
            delay(200);
            String created = zk.create(znode, data != null ? data.getBytes() : null, access, mode);
            if (log.isInfoEnabled()) {
                log.info(String.format("Created znode named '%s'", created));
            }
        }

        public Stat setData(String node, String data, int version) throws Exception {
            log.debug(String.format("TestClient Updating data of node %s to %s", node, data));
            return zk.setData(node, data.getBytes(), version);
        }

        public byte[] getData(String znode) throws Exception {
            return zk.getData(znode, false, new Stat());
        }

        @Override
        public void process(WatchedEvent event) {
            if (event.getState() == KeeperState.SyncConnected) {
                log.info("TestClient connected");
                connected.countDown();
            } else {
                if (event.getState() == KeeperState.Disconnected) {
                    log.info("TestClient connected ? {}", zk.getState());
                }
            }
        }

        public void deleteAll(String node) throws Exception {
            delay(200);
            log.debug("Deleting {} and it's immediate children", node);
            for (String child : zk.getChildren(node, false)) {
                delete(node + "/" + child);
            }
            delete(node);
        }

        public void delete(String node) throws Exception {
            delay(200);
            log.debug("Deleting node {}", node);
            zk.delete(node, -1);
        }
    }

    public static void delay(int wait) throws InterruptedException {
        Thread.sleep(wait);
    }

    protected List<String> createChildListing(String... children) {
        return Arrays.asList(children);
    }

    protected void validateExchangesReceivedInOrderWithIncreasingVersion(MockEndpoint mock) {
        int lastVersion = -1;
        List<Exchange> received = mock.getReceivedExchanges();
        for (int x = 0; x < received.size(); x++) {
            Message zkm = mock.getReceivedExchanges().get(x).getIn();
            int version = ZooKeeperMessage.getStatistics(zkm).getVersion();
            assertTrue(lastVersion < version, "Version did not increase");
            lastVersion = version;
        }
    }

    protected void verifyAccessControlList(String node) throws Exception {
        getConnection().getACL(node, new Stat());
    }

    protected void verifyNodeContainsData(String node, byte[] expected) throws Exception {
        if (expected == null) {
            assertNull(client.getData(node));
        } else {
            assertEquals(new String(expected), new String(client.getData(node)));
        }
    }
}
