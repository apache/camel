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
package org.apache.camel.component.zookeeper;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ZooKeeper producer against a ZooKeeper server that runs in the test JVM: the body that is stored, and the
 * failures of InOnly and InOut writes and deletes.
 */
class ZooKeeperProducerTest extends CamelTestSupport {

    private static final String NODE = "/camel-producer";
    private static final String PARENT = "/camel-producer-parent";
    private static final String MISSING = "/camel-producer-missing";

    @TempDir
    Path dataDir;

    private final int port = AvailablePortFinder.getNextAvailable();

    private ZooKeeperServer server;
    private NIOServerCnxnFactory factory;
    private ZooKeeper client;

    @Override
    protected void doPreSetup() throws Exception {
        File dir = dataDir.toFile();
        server = new ZooKeeperServer(dir, dir, 2000);
        factory = new NIOServerCnxnFactory();
        factory.configure(new InetSocketAddress("127.0.0.1", port), 60);
        factory.startup(server);

        CountDownLatch connected = new CountDownLatch(1);
        client = new ZooKeeper("127.0.0.1:" + port, 20000, e -> {
            if (e.getState() == KeeperState.SyncConnected) {
                connected.countDown();
            }
        });
        assertTrue(connected.await(20, TimeUnit.SECONDS), "the test client did not connect");
        client.create(NODE, bytes("one"), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        client.create(PARENT, bytes("parent"), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        client.create(PARENT + "/child", bytes("child"), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
    }

    @Override
    protected void doPostTearDown() throws Exception {
        if (client != null) {
            client.close();
        }
        if (factory != null) {
            factory.shutdown();
        }
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    void mapBodyFailsAndKeepsTheData() throws Exception {
        Exchange exchange = send(ExchangePattern.InOnly, uri(NODE), Map.of("key", "value"), Map.of());

        assertInstanceOf(InvalidPayloadException.class, exchange.getException());
        assertEquals("one", data(NODE));
    }

    @Test
    void pojoBodyFailsAndKeepsTheData() throws Exception {
        Exchange exchange = send(ExchangePattern.InOut, uri(NODE), new Object(), Map.of());

        assertInstanceOf(InvalidPayloadException.class, exchange.getException());
        assertEquals("one", data(NODE));
    }

    @Test
    void integerBodyFailsAndKeepsTheData() throws Exception {
        Exchange exchange = send(ExchangePattern.InOnly, uri(NODE), 42, Map.of());

        assertInstanceOf(InvalidPayloadException.class, exchange.getException());
        assertEquals("one", data(NODE));
    }

    @Test
    void textBodyIsStored() throws Exception {
        Exchange exchange = send(ExchangePattern.InOnly, uri(NODE), "two", Map.of());

        assertNull(exchange.getException());
        assertEquals("two", data(NODE));
        assertEquals("two", exchange.getMessage().getBody(String.class), "InOnly leaves the body unchanged");
    }

    @Test
    void nullBodyStoresNoData() throws Exception {
        Exchange exchange = send(ExchangePattern.InOut, uri(NODE), null, Map.of());

        assertNull(exchange.getException());
        assertNull(client.getData(NODE, false, null));
    }

    @Test
    void inOnlyWriteToMissingNodeFails() throws Exception {
        Exchange exchange = send(ExchangePattern.InOnly, uri(MISSING), "two", Map.of());

        assertInstanceOf(KeeperException.NoNodeException.class, exchange.getException());
        assertNull(client.exists(MISSING, false));
    }

    @Test
    void inOnlyWriteWithStaleVersionFails() throws Exception {
        Exchange exchange = send(ExchangePattern.InOnly, uri(NODE), "two", Map.of(ZooKeeperMessage.ZOOKEEPER_NODE_VERSION, 7));

        assertInstanceOf(KeeperException.BadVersionException.class, exchange.getException());
        assertEquals("one", data(NODE));
    }

    @Test
    void inOnlyWriteWithCreateCreatesTheNodeBeforeTheExchangeCompletes() throws Exception {
        Exchange exchange = send(ExchangePattern.InOnly, uri(MISSING) + "?create=true&createMode=PERSISTENT", "two", Map.of());

        assertNull(exchange.getException());
        assertEquals("two", data(MISSING));
    }

    @Test
    void inOnlyDeleteOfMissingNodeFails() {
        Exchange exchange = send(ExchangePattern.InOnly, uri(MISSING), "two", delete());

        assertInstanceOf(KeeperException.NoNodeException.class, exchange.getException());
    }

    @Test
    void deleteOfMissingNodeWithCreateDoesNotCreateIt() throws Exception {
        Exchange exchange = send(ExchangePattern.InOut, uri(MISSING) + "?create=true&createMode=PERSISTENT", "two", delete());

        assertInstanceOf(KeeperException.NoNodeException.class, exchange.getException());
        assertNull(client.exists(MISSING, false), "a DELETE created the node");
    }

    @Test
    void deleteDoesNotReadTheBody() throws Exception {
        Exchange exchange = send(ExchangePattern.InOnly, uri(NODE), Map.of("key", "value"), delete());

        assertNull(exchange.getException());
        assertNull(client.exists(NODE, false));
    }

    @Test
    void listChildrenDoesNotHideAFailedWrite() throws Exception {
        Exchange exchange = send(ExchangePattern.InOut, uri(PARENT) + "?listChildren=true", "two",
                Map.of(ZooKeeperMessage.ZOOKEEPER_NODE, PARENT + "/missing"));

        assertInstanceOf(KeeperException.NoNodeException.class, exchange.getException());
    }

    @Test
    void listChildrenAfterAWrite() throws Exception {
        Exchange exchange = send(ExchangePattern.InOut, uri(PARENT) + "?listChildren=true", "two",
                Map.of(ZooKeeperMessage.ZOOKEEPER_NODE, PARENT + "/child"));

        assertNull(exchange.getException());
        assertEquals(List.of("child"), exchange.getMessage().getBody(List.class));
        assertArrayEquals(bytes("two"), client.getData(PARENT + "/child", false, null));
    }

    private Exchange send(ExchangePattern pattern, String uri, Object body, Map<String, Object> headers) {
        return template.send(uri, pattern, e -> {
            e.getIn().setBody(body);
            e.getIn().setHeaders(new HashMap<>(headers));
        });
    }

    private static Map<String, Object> delete() {
        return Map.of(ZooKeeperMessage.ZOOKEEPER_OPERATION, ZooKeeperProducer.ZK_OPERATION_DELETE);
    }

    private String uri(String node) {
        return "zookeeper://127.0.0.1:" + port + node;
    }

    private String data(String node) throws Exception {
        return new String(client.getData(node, false, null), StandardCharsets.UTF_8);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
