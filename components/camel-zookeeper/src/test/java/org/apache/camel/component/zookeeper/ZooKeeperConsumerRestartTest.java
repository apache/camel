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

import org.apache.camel.Consumer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.zookeeper.operations.FutureEventDrivenOperation;
import org.apache.camel.support.service.ServiceHelper;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The consumer is stopped and started again in place, as a route policy (ThrottlingInflightRoutePolicy,
 * ThrottlingExceptionRoutePolicy) or JMX does: it must consume again, on one operations thread.
 */
class ZooKeeperConsumerRestartTest extends CamelTestSupport {

    private static final String NODE = "/camel-consumer-restart";

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
        client = new ZooKeeper("127.0.0.1:" + port, 10000, e -> {
            if (e.getState() == KeeperState.SyncConnected) {
                connected.countDown();
            }
        });
        assertTrue(connected.await(20, TimeUnit.SECONDS), "the test client did not connect");
        client.create(NODE, bytes("one"), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
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

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("zookeeper://127.0.0.1:" + port + NODE + "?repeat=true&backoff=200").routeId("watch")
                        .to("mock:data");
            }
        };
    }

    @Test
    void consumesAgainAfterTheConsumerWasStoppedAndStarted() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:data");
        await().atMost(20, TimeUnit.SECONDS).until(() -> received(mock, "one"));

        Consumer consumer = context.getRoute("watch").getConsumer();
        ServiceHelper.stopService(consumer);
        ServiceHelper.startService(consumer);

        // the started consumer reads the node again
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(count(mock, "one") >= 2,
                "the consumer did not read the node after it was started again"));
        // and the thread of the first start has ended
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(operationsThreads() == 1,
                "the consumer must have one operations thread, not " + operationsThreads()));

        // update through the consumer's own session once it waits for a change, so the change cannot be missed
        ZooKeeperEndpoint endpoint = (ZooKeeperEndpoint) context.getRoute("watch").getEndpoint();
        await().atMost(20, TimeUnit.SECONDS).until(ZooKeeperConsumerRestartTest::operationsThreadWaitsForAChange);
        endpoint.getConnectionManager().getConnection().setData(NODE, bytes("two"), -1);
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(received(mock, "two"),
                "the consumer did not consume the update made after it was started again"));
    }

    private static long operationsThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().contains("Camel-Zookeeper OperationsExecutor"))
                .filter(Thread::isAlive)
                .count();
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
        return count(mock, body) > 0;
    }

    private static long count(MockEndpoint mock, String body) {
        return mock.getReceivedExchanges().stream()
                .filter(e -> body.equals(new String(e.getIn().getBody(byte[].class), StandardCharsets.UTF_8)))
                .count();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
