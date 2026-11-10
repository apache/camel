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
package org.apache.camel.component.zookeepermaster.policy;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MasterRoutePolicy} when its route is removed, added again, started or resumed, with two CamelContexts (two
 * nodes) and a ZooKeeper server in this JVM.
 */
class MasterRoutePolicyRouteLifecycleTest {

    private static final String ROUTE = "master";

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
        // the CamelContexts first, so their policies leave the group while the server runs
        contexts.forEach(CamelContext::stop);
        if (factory != null) {
            factory.shutdown();
        }
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    void removedRouteGivesUpTheMastership() throws Exception {
        CamelContext a = startNode(newPolicy());
        awaitConsuming(a);
        CamelContext b = startNode(newPolicy());
        awaitSlave(b);

        // the route is removed on the master: the other node must take over, otherwise the route runs nowhere
        a.getRouteController().stopRoute(ROUTE);
        assertTrue(a.removeRoute(ROUTE));

        awaitConsuming(b);
        assertConsumes(b);
    }

    @Test
    void removedRouteOfASlaveLeavesTheGroup() throws Exception {
        CamelContext a = startNode(newPolicy());
        awaitConsuming(a);
        CamelContext b = startNode(newPolicy());
        awaitSlave(b);
        await().atMost(30, TimeUnit.SECONDS).until(() -> groupMembers() == 2);

        // the route is removed on the slave: its policy must not be elected later, as it would start the consumer of
        // the removed route
        b.getRouteController().stopRoute(ROUTE);
        assertTrue(b.removeRoute(ROUTE));
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(1, groupMembers(), "The policy of a removed route must leave the group"));
    }

    @Test
    void routeAddedToARunningContextJoinsTheGroupWithItsConsumer() throws Exception {
        CamelContext a = startNode(newPolicy());
        awaitConsuming(a);

        // a route with a new policy is added to the running CamelContext: the policy must join the group only once the
        // consumer of the route is created, as a master that is elected before cannot start it
        MasterRoutePolicy policy = newPolicy();
        policy.setGroupName("added");
        AtomicBoolean joinedBeforeConsumer = new AtomicBoolean();
        a.addEndpoint("watched:in", new WatchedEndpoint(() -> joinedBeforeConsumer.set(policy.thisNode() != null)));
        a.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("watched:in").routeId("added").autoStartup(false).routePolicy(policy).to("mock:added");
            }
        });

        assertFalse(joinedBeforeConsumer.get(), "The policy joined the group before the consumer was created");
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(
                policy.isMaster() && ServiceHelper.isStarted(a.getRoute("added").getConsumer()),
                "The node must become the master and start its consumer"));
    }

    @Test
    void restartedContextRunsTheRouteAgain() throws Exception {
        // a control (passes on main too): the policy leaves the group when the CamelContext stops, and joins it again
        CamelContext a = startNode(newPolicy());
        awaitConsuming(a);

        a.stop();
        a.start();

        awaitConsuming(a);
        assertConsumes(a);
    }

    @Test
    void routeAddedAgainWithANewPolicyRunsAgain() throws Exception {
        CamelContext a = startNode(newPolicy());
        awaitConsuming(a);

        // a route reload: the route is removed and added again, with a new policy instance
        a.getRouteController().stopRoute(ROUTE);
        assertTrue(a.removeRoute(ROUTE));
        a.addRoutes(route(newPolicy()));

        awaitConsuming(a);
        assertConsumes(a);
    }

    @Test
    void routeAddedAgainWithTheSamePolicyRunsAgain() throws Exception {
        MasterRoutePolicy policy = newPolicy();
        CamelContext a = startNode(policy);
        awaitConsuming(a);

        a.getRouteController().stopRoute(ROUTE);
        assertTrue(a.removeRoute(ROUTE));
        a.addRoutes(route(policy));

        awaitConsuming(a);
        assertConsumes(a);
    }

    @Test
    void routeOfASlaveIsKeptStoppedWhenStarted() throws Exception {
        CamelContext a = startNode(newPolicy());
        awaitConsuming(a);
        CamelContext b = startNode(newPolicy());
        awaitSlave(b);

        // the route of the slave is started (from JMX or the controlbus): its consumer must stay stopped
        b.getRouteController().startRoute(ROUTE);
        assertFalse(isConsumerStarted(b), "The slave must not consume");

        // the master goes away: the slave takes over and consumes
        a.stop();
        awaitConsuming(b);
        assertConsumes(b);
    }

    @Test
    void routeOfASlaveIsKeptStoppedWhenResumed() throws Exception {
        CamelContext a = startNode(newPolicy());
        awaitConsuming(a);
        CamelContext b = startNode(newPolicy());
        awaitSlave(b);

        // the route of the slave is resumed, which starts the consumer of a route that is not started, and then it is
        // started, suspended and resumed: the consumer must stay stopped
        b.getRouteController().resumeRoute(ROUTE);
        assertFalse(isConsumerStarted(b), "The slave must not consume after a resume");
        b.getRouteController().startRoute(ROUTE);
        b.getRouteController().suspendRoute(ROUTE);
        b.getRouteController().resumeRoute(ROUTE);
        assertFalse(isConsumerStarted(b), "The slave must not consume after a suspend and resume");

        a.stop();
        awaitConsuming(b);
        assertConsumes(b);
    }

    @Test
    void routeWithAutoStartupRunsOnlyOnTheMaster() throws Exception {
        CamelContext a = startNode(newPolicy(), true);
        awaitConsuming(a);
        CamelContext b = startNode(newPolicy(), true);
        awaitSlave(b);

        a.stop();
        awaitConsuming(b);
        assertConsumes(b);
    }

    private MasterRoutePolicy newPolicy() {
        MasterRoutePolicy policy = new MasterRoutePolicy();
        policy.setZooKeeperUrl("127.0.0.1:" + port);
        policy.setZkRoot("/camel/lifecycle");
        policy.setGroupName("lifecycle");
        return policy;
    }

    private static RouteBuilder route(MasterRoutePolicy policy) {
        return route(policy, false);
    }

    private static RouteBuilder route(MasterRoutePolicy policy, boolean autoStartup) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:in").routeId(ROUTE).autoStartup(autoStartup).routePolicy(policy).to("mock:out");
            }
        };
    }

    private CamelContext startNode(MasterRoutePolicy policy) throws Exception {
        return startNode(policy, false);
    }

    private CamelContext startNode(MasterRoutePolicy policy, boolean autoStartup) throws Exception {
        CamelContext context = new DefaultCamelContext();
        contexts.add(context);
        context.addRoutes(route(policy, autoStartup));
        context.start();
        return context;
    }

    // the members of the group in ZooKeeper
    private int groupMembers() throws Exception {
        try (CuratorFramework client
                = CuratorFrameworkFactory.newClient("127.0.0.1:" + port, new RetryOneTime(100))) {
            client.start();
            client.blockUntilConnected(20, TimeUnit.SECONDS);
            return client.getChildren().forPath("/camel/lifecycle/lifecycle").size();
        }
    }

    private static MasterRoutePolicy policyOf(CamelContext context) {
        return (MasterRoutePolicy) context.getRoute(ROUTE).getRoutePolicyList().get(0);
    }

    private static boolean isConsumerStarted(CamelContext context) {
        Consumer consumer = context.getRoute(ROUTE).getConsumer();
        return ServiceHelper.isStarted(consumer);
    }

    private static void awaitConsuming(CamelContext context) {
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(
                () -> assertTrue(policyOf(context).isMaster() && isConsumerStarted(context),
                        "The node must become the master and start its consumer"));
    }

    private static void awaitSlave(CamelContext context) {
        await().atMost(30, TimeUnit.SECONDS)
                .until(() -> policyOf(context).isConnected() && policyOf(context).thisNode() != null);
        assertFalse(policyOf(context).isMaster());
        assertFalse(isConsumerStarted(context));
    }

    // a message sent to the node is routed, so the consumer runs and is not only marked as started
    private static void assertConsumes(CamelContext context) throws Exception {
        MockEndpoint mock = context.getEndpoint("mock:out", MockEndpoint.class);
        mock.reset();
        mock.expectedBodiesReceived("hello");
        mock.setResultWaitTime(20000);
        try (ProducerTemplate template = context.createProducerTemplate()) {
            template.sendBody("seda:in", "hello");
        }
        mock.assertIsSatisfied();
    }

    // an endpoint that runs a check when its consumer is created
    private static final class WatchedEndpoint extends DefaultEndpoint {
        private final Runnable onCreateConsumer;

        WatchedEndpoint(Runnable onCreateConsumer) {
            this.onCreateConsumer = onCreateConsumer;
        }

        @Override
        protected String createEndpointUri() {
            return "watched:in";
        }

        @Override
        public Producer createProducer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            onCreateConsumer.run();
            return new DefaultConsumer(this, processor);
        }
    }
}
