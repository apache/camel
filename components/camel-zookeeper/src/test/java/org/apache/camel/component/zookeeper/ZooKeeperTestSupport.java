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
package org.apache.camel.component.zookeeper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.persistence.FileTxnSnapLog;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZooKeeperTestSupport extends CamelTestSupport {

    protected static TestZookeeperServer server;
    protected static TestZookeeperClient client;

    private static volatile int port;
    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperTestSupport.class);
 
    protected String testPayload = "This is a test";
    protected byte[] testPayloadBytes = testPayload.getBytes();
        
    @BeforeClass
    public static void setupTestServer() throws Exception {
        port = AvailablePortFinder.getNextAvailable(39913);
        
        LOG.info("Starting Zookeeper Test Infrastructure");
        server = new TestZookeeperServer(getServerPort(), clearServerData());
        waitForServerUp("localhost:" + getServerPort(), 1000);
        client = new TestZookeeperClient(getServerPort(), getTestClientSessionTimeout());
        LOG.info("Started Zookeeper Test Infrastructure on port " + getServerPort());
    }

    public ZooKeeper getConnection() {
        return client.getConnection();
    }

    @AfterClass
    public static void shutdownServer() throws Exception {
        LOG.info("Stopping Zookeeper Test Infrastructure");
        client.shutdown();
        server.shutdown();
        waitForServerDown("localhost:" + getServerPort(), 1000);
        LOG.info("Stopped Zookeeper Test Infrastructure");
    }

    protected static int getServerPort() {
        return port;
    }

    protected static int getTestClientSessionTimeout() {
        return 100000;
    }

    protected static boolean clearServerData() {
        return true;
    }

    public static class TestZookeeperServer {
        private static int count;
        private NIOServerCnxnFactory connectionFactory;
        private ZooKeeperServer zkServer;
        private File zookeeperBaseDir;
        
        public TestZookeeperServer(int clientPort, boolean clearServerData) throws Exception {
            // TODO This is necessary as zookeeper does not delete the log dir when it shuts down. Remove as soon as zookeeper shutdown works
            zookeeperBaseDir = new File("./target/zookeeper" + count++);
            if (clearServerData) {
                cleanZookeeperDir();
            }
            zkServer = new ZooKeeperServer();
            File dataDir = new File(zookeeperBaseDir, "log");
            File snapDir = new File(zookeeperBaseDir, "data");
            FileTxnSnapLog ftxn = new FileTxnSnapLog(dataDir, snapDir);
            zkServer.setTxnLogFactory(ftxn);
            zkServer.setTickTime(1000);
            connectionFactory = new NIOServerCnxnFactory();
            connectionFactory.configure(new InetSocketAddress("localhost", clientPort), 0);
            connectionFactory.startup(zkServer);
        }
        
        private void cleanZookeeperDir() throws Exception {
            File working = zookeeperBaseDir;
            deleteDir(working);
        }

        public void shutdown() throws Exception {
            connectionFactory.shutdown();
            connectionFactory.join();
            zkServer.shutdown();
            while (zkServer.isRunning()) {
                zkServer.shutdown();
                Thread.sleep(100);
            }
            cleanZookeeperDir();
        }
    }

    public static class TestZookeeperClient implements Watcher {

        public static int x;

        private final Logger log = LoggerFactory.getLogger(getClass());
        private ZooKeeper zk;
        private CountDownLatch connected = new CountDownLatch(1);


        public TestZookeeperClient(int port, int timeout) throws Exception {
            zk = new ZooKeeper("localhost:" + port, timeout, this);
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

        public void process(WatchedEvent event) {
            if (event.getState() == KeeperState.SyncConnected) {
                log.info("TestClient connected");
                connected.countDown();
            } else {
                if (event.getState() == KeeperState.Disconnected) {
                    log.info("TestClient connected ?" + zk.getState());
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
            log.debug("Deleting node " + node);
            zk.delete(node, -1);
        }
    }

    // Wait methods are taken directly from the Zookeeper tests. A tests jar
    // would be nice! Another good reason the keeper folks should move to maven.
    public static boolean waitForServerUp(String hp, long timeout) {
        long start = System.currentTimeMillis();
        while (true) {
            try {
                // if there are multiple hostports, just take the first one
                hp = hp.split(",")[0];
                String result = send4LetterWord(hp, "stat");
                if (result.startsWith("Zookeeper version:")) {
                    return true;
                }
            } catch (IOException e) {
                LOG.info("server {} not up {}", hp, e);
            }

            if (System.currentTimeMillis() > start + timeout) {
                break;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        return false;
    }

    private static String send4LetterWord(String hp, String cmd) throws IOException {
        String split[] = hp.split(":");
        String host = split[0];
        int port;
        try {
            port = Integer.parseInt(split[1]);
        } catch (RuntimeException e) {
            throw new RuntimeException("Problem parsing " + hp + e.toString());
        }

        Socket sock = new Socket(host, port);
        BufferedReader reader = null;
        try {
            OutputStream outstream = sock.getOutputStream();
            outstream.write(cmd.getBytes());
            outstream.flush();

            reader = IOHelper.buffered(new InputStreamReader(sock.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
            return sb.toString();
        } finally {
            sock.close();
            if (reader != null) {
                reader.close();
            }
        }
    }

    private static boolean waitForServerDown(String hp, long timeout) {
        long start = System.currentTimeMillis();
        while (true) {
            try {
                send4LetterWord(hp, "stat");
            } catch (IOException e) {
                return true;
            }

            if (System.currentTimeMillis() > start + timeout) {
                break;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        return false;
    }

    public static void deleteDir(File f) {
        LinkedList<File> deleteStack = new LinkedList<File>();
        deleteStack.addLast(f);
        deleteDir(deleteStack);
    }

    private static void deleteDir(Deque<File> deleteStack) {
        File f = deleteStack.pollLast();
        if (f != null) {
            if (f.isDirectory()) {
                File[] files = f.listFiles();
                if (files != null) {
                    for (File child : files) {
                        deleteStack.addLast(child);
                    }
                }
            }
            deleteDir(deleteStack);
            FileUtil.deleteFile(f);
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
            assertTrue("Version did not increase", lastVersion < version);
            lastVersion = version;
        }
    }

    protected void verifyAccessControlList(String node, List<ACL> expected) throws Exception {
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
