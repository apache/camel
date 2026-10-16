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
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.zookeeper.operations.FutureEventDrivenOperation;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A ZooKeeper consumer must keep consuming after the server expired its session. The server runs in the test JVM. The
 * session is expired the usual way: a second client opens the same session and closes it; the consumer's client then
 * gets the "Expired" event when it reconnects, and its handle is closed for good. Every wait is bounded (20 seconds).
 */
class ZooKeeperConsumerSessionExpiryTest extends CamelTestSupport {

    private static final String NODE = "/camel-session-expiry";
    private static final String BLOCKED_NODE = "/camel-session-expiry-blocked";
    private static final String ONCE_NODE = "/camel-session-expiry-once";

    @TempDir
    Path dataDir;

    private final int port = AvailablePortFinder.getNextAvailable();

    private final CountDownLatch processing = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

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
        client = connect(0, null);
        client.create(NODE, bytes("one"), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        client.create(BLOCKED_NODE, bytes("one"), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        client.create(ONCE_NODE, bytes("one"), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
    }

    @Override
    protected void doPostTearDown() throws Exception {
        release.countDown();
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

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // each test starts the route it needs, so that only one consumer runs at a time
                from("zookeeper://127.0.0.1:" + port + NODE + "?repeat=true&backoff=200").routeId("watch")
                        .autoStartup(false)
                        .to("mock:data");

                // the first exchange holds the operations thread of the consumer until the test releases it
                from("zookeeper://127.0.0.1:" + port + BLOCKED_NODE + "?repeat=true&backoff=200").routeId("blocked")
                        .autoStartup(false)
                        .process(e -> {
                            if (processing.getCount() > 0) {
                                processing.countDown();
                                assertTrue(release.await(20, TimeUnit.SECONDS), "the test did not release the route");
                            }
                        })
                        .to("mock:blocked");

                from("zookeeper://127.0.0.1:" + port + ONCE_NODE + "?backoff=200").routeId("once")
                        .autoStartup(false)
                        .to("mock:once");
            }
        };
    }

    @Test
    void consumesAgainAfterTheSessionExpired() throws Exception {
        context.getRouteController().startRoute("watch");
        MockEndpoint mock = getMockEndpoint("mock:data");
        await().atMost(20, TimeUnit.SECONDS).until(() -> received(mock, "one"));

        ZooKeeperEndpoint endpoint = (ZooKeeperEndpoint) context.getRoute("watch").getEndpoint();
        ZooKeeper consumerHandle = endpoint.getConnectionManager().getConnection();
        expire(consumerHandle);
        await().atMost(20, TimeUnit.SECONDS).until(() -> !consumerHandle.getState().isAlive());

        client.setData(NODE, bytes("two"), -1);

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(received(mock, "two"),
                "the consumer did not consume the update made after its session had expired"));

        // "two" may come from the read of the restarted sequence; "three" can only come from its new watch
        updateWhenTheConsumerWaitsForAChange(endpoint, NODE, "three");
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(received(mock, "three"),
                "the consumer did not watch the node again on its new session"));
    }

    @Test
    void consumesAgainAfterTheSessionExpiredWhileAnExchangeWasProcessed() throws Exception {
        context.getRouteController().startRoute("blocked");
        assertTrue(processing.await(20, TimeUnit.SECONDS), "the route did not receive the first data");

        // the session expires while the operations thread routes the first exchange: no watch of the consumer is
        // registered at that time, so the consumer gets no Expired event; the next watch request fails instead
        ZooKeeperEndpoint endpoint = (ZooKeeperEndpoint) context.getRoute("blocked").getEndpoint();
        ZooKeeper consumerHandle = endpoint.getConnectionManager().getConnection();
        expire(consumerHandle);
        await().atMost(20, TimeUnit.SECONDS).until(() -> !consumerHandle.getState().isAlive());
        client.setData(BLOCKED_NODE, bytes("two"), -1);
        release.countDown();

        MockEndpoint mock = getMockEndpoint("mock:blocked");
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(received(mock, "two"),
                "the consumer did not consume the update made after its session had expired during an exchange"));

        updateWhenTheConsumerWaitsForAChange(endpoint, BLOCKED_NODE, "three");
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(received(mock, "three"),
                "the consumer did not watch the node again on its new session"));
    }

    @Test
    void emitsTheCurrentDataAgainAfterTheSessionExpiredWithoutRepeat() throws Exception {
        context.getRouteController().startRoute("once");
        MockEndpoint mock = getMockEndpoint("mock:once");
        await().atMost(20, TimeUnit.SECONDS).until(() -> mock.getReceivedCounter() == 1);

        ZooKeeperEndpoint endpoint = (ZooKeeperEndpoint) context.getRoute("once").getEndpoint();
        ZooKeeper consumerHandle = endpoint.getConnectionManager().getConnection();
        expire(consumerHandle);

        // the consumer starts its sequence again on a new session, which reads the (unchanged) data once more
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(2, mock.getReceivedCounter(),
                "the consumer did not read the data again after its session had expired"));
        assertTrue(mock.getReceivedExchanges().stream()
                .allMatch(e -> "one".equals(new String(e.getIn().getBody(byte[].class), StandardCharsets.UTF_8))));
    }

    @Test
    void producerWritesAgainAfterTheSessionExpired() throws Exception {
        template.sendBody("zookeeper://127.0.0.1:" + port + NODE, "three");
        ZooKeeperEndpoint endpoint = context.getEndpoint("zookeeper://127.0.0.1:" + port + NODE, ZooKeeperEndpoint.class);
        ZooKeeper producerHandle = endpoint.getConnectionManager().getConnection();
        expire(producerHandle);
        await().atMost(20, TimeUnit.SECONDS).until(() -> !producerHandle.getState().isAlive());

        Exchange reply = template.request("zookeeper://127.0.0.1:" + port + NODE, e -> e.getIn().setBody("four"));
        assertNull(reply.getException(), "the producer failed after its session had expired");
        assertEquals("four", new String(client.getData(NODE, false, null), StandardCharsets.UTF_8));
    }

    /**
     * Waits until the consumer's operations thread waits for a change of a node (its watch request is sent), then
     * updates the node through the consumer's own session: ZooKeeper handles the requests of one session in order, so
     * the update comes after the watch is registered and the change cannot be missed.
     */
    private static void updateWhenTheConsumerWaitsForAChange(ZooKeeperEndpoint endpoint, String node, String data)
            throws Exception {
        await().atMost(20, TimeUnit.SECONDS).until(ZooKeeperConsumerSessionExpiryTest::operationsThreadWaitsForAChange);
        endpoint.getConnectionManager().getConnection().setData(node, bytes(data), -1);
    }

    private static boolean operationsThreadWaitsForAChange() {
        return Thread.getAllStackTraces().entrySet().stream()
                .filter(e -> e.getKey().getName().contains("Camel-Zookeeper OperationsExecutor"))
                .filter(e -> e.getKey().getState() == Thread.State.WAITING)
                .anyMatch(e -> Arrays.stream(e.getValue())
                        .anyMatch(f -> FutureEventDrivenOperation.class.getName().equals(f.getClassName())
                                && "get".equals(f.getMethodName())));
    }

    private static boolean received(MockEndpoint mock, String body) {
        return mock.getReceivedExchanges().stream()
                .anyMatch(e -> body.equals(new String(e.getIn().getBody(byte[].class), StandardCharsets.UTF_8)));
    }

    private ZooKeeper connect(long sessionId, byte[] password) throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        ZooKeeper zk = password == null
                ? new ZooKeeper("127.0.0.1:" + port, 10000, e -> countDownWhenConnected(e.getState(), connected))
                : new ZooKeeper(
                        "127.0.0.1:" + port, 10000, e -> countDownWhenConnected(e.getState(), connected), sessionId,
                        password);
        assertTrue(connected.await(20, TimeUnit.SECONDS), "the test client did not connect");
        return zk;
    }

    private static void countDownWhenConnected(KeeperState state, CountDownLatch connected) {
        if (state == KeeperState.SyncConnected) {
            connected.countDown();
        }
    }

    /**
     * Closes the session of the given handle from a second client, so that the server expires it for the handle.
     */
    private void expire(ZooKeeper handle) throws Exception {
        ZooKeeper other = connect(handle.getSessionId(), handle.getSessionPasswd());
        other.close();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
