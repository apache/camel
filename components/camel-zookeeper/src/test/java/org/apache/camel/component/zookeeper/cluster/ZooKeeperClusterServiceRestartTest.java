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
package org.apache.camel.component.zookeeper.cluster;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.cluster.CamelClusterView;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A ZooKeeper cluster service that is stopped and started again (for example from JMX) must take part in the leader
 * election again. The ZooKeeper server runs in the test JVM. Every wait is bounded (20 seconds).
 */
class ZooKeeperClusterServiceRestartTest {

    private static final String NAMESPACE = "restart-ns";

    @TempDir
    Path dataDir;

    private final int port = AvailablePortFinder.getNextAvailable();
    private final List<CamelContext> contexts = new ArrayList<>();

    private ZooKeeperServer server;
    private NIOServerCnxnFactory factory;

    @BeforeEach
    void startServer() throws Exception {
        File dir = dataDir.toFile();
        server = new ZooKeeperServer(dir, dir, 2000);
        factory = new NIOServerCnxnFactory();
        factory.configure(new InetSocketAddress("127.0.0.1", port), 60);
        factory.startup(server);
    }

    @AfterEach
    void stopServer() {
        contexts.forEach(CamelContext::stop);
        if (factory != null) {
            factory.shutdown();
        }
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    void restartedServiceTakesTheLeadershipAgain() throws Exception {
        ZooKeeperClusterService serviceA = newService("node-a");
        CamelClusterView viewA = serviceA.getView(NAMESPACE);
        await().atMost(20, TimeUnit.SECONDS).until(() -> viewA.getLocalMember().isLeader());

        ZooKeeperClusterService serviceB = newService("node-b");
        CamelClusterView viewB = serviceB.getView(NAMESPACE);
        // node b takes part in the election: it is one of the members, and not the leader
        await().atMost(20, TimeUnit.SECONDS).until(() -> viewB.getMembers().size() == 2);
        assertFalse(viewB.getLocalMember().isLeader());

        // the service of node b is stopped and started again: it closes its client and creates a new one
        serviceB.stop();
        serviceB.start();

        // node a goes away: node b must take over
        serviceA.stop();
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(
                () -> assertTrue(viewB.getLocalMember().isLeader(), "The restarted service did not take the leadership"));
    }

    private ZooKeeperClusterService newService(String id) throws Exception {
        ZooKeeperClusterService service = new ZooKeeperClusterService();
        service.setId(id);
        service.setNodes("127.0.0.1:" + port);
        service.setBasePath("/camel/restart");
        service.setSessionTimeout(10000);
        service.setConnectTimeout(10000);

        CamelContext context = new DefaultCamelContext();
        context.addService(service);
        context.start();
        contexts.add(context);
        return service;
    }
}
