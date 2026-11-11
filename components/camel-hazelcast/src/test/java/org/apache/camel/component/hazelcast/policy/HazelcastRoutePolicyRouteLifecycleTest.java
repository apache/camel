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
package org.apache.camel.component.hazelcast.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.cluster.Address;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HazelcastRoutePolicy} on two nodes (two CamelContexts on one embedded Hazelcast member, the lock is held by
 * the leadership thread of a node) when a route is removed, added again, suspended and resumed.
 */
class HazelcastRoutePolicyRouteLifecycleTest {

    private static final String ROUTE_ID = "clustered";

    private final List<CamelContext> contexts = new ArrayList<>();
    private HazelcastInstance member;
    private HazelcastInstance client;

    @BeforeEach
    void startMember() {
        member = Hazelcast.newHazelcastInstance(memberConfig("route-policy-" + UUID.randomUUID()));
    }

    @AfterEach
    void stopNodes() {
        contexts.forEach(CamelContext::stop);
        if (client != null) {
            client.shutdown();
        }
        if (member != null) {
            member.shutdown();
        }
    }

    @Test
    void removedRouteGivesUpTheLeadership() throws Exception {
        HazelcastRoutePolicy policyA = newPolicy("a");
        CamelContext a = startNode("a", policyA);
        awaitLeader(a, policyA);
        HazelcastRoutePolicy policyB = newPolicy("b");
        CamelContext b = startNode("b", policyB);
        assertFollower(b, policyB);

        a.getRouteController().stopRoute(ROUTE_ID);
        a.removeRoute(ROUTE_ID);

        // the route runs nowhere on node a any more, so node b must take over
        awaitLeader(b, policyB);
        assertFalse(policyA.isLeader(), "The policy of the removed route is still the leader");
        assertConsumes(b);
    }

    @Test
    void removedRouteGivesUpTheLeadershipWithAClientInstance() throws Exception {
        // node a uses a Hazelcast client: its thread must release the lock (an interrupted client call fails)
        HazelcastRoutePolicy policyA = newPolicy(newClient(), "a");
        CamelContext a = startNode("a", policyA);
        awaitLeader(a, policyA);
        HazelcastRoutePolicy policyB = newPolicy("b");
        CamelContext b = startNode("b", policyB);
        assertFollower(b, policyB);

        a.getRouteController().stopRoute(ROUTE_ID);
        a.removeRoute(ROUTE_ID);

        awaitLeader(b, policyB);
        assertFalse(policyA.isLeader(), "The policy of the removed route is still the leader");
        assertConsumes(b);
    }

    @Test
    void policySharedByTwoRoutesKeepsTheLeadershipUntilBothAreRemoved() throws Exception {
        HazelcastRoutePolicy policyA = newPolicy("a");
        CamelContext a = startNode("a", policyA);
        a.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:other").routeId("other").routePolicy(policyA).to("mock:other");
            }
        });
        awaitLeader(a, policyA);
        HazelcastRoutePolicy policyB = newPolicy("b");
        CamelContext b = startNode("b", policyB);
        assertFollower(b, policyB);

        // the policy still has a route on node a
        a.getRouteController().stopRoute("other");
        a.removeRoute("other");
        // JMX shows a route that still uses the policy
        assertEquals(ROUTE_ID, policyA.getRouteId());
        await().during(1, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertTrue(policyA.isLeader(), "Node a gave up the leadership while a route still uses the policy");
                    assertFalse(policyB.isLeader(), "Node b took the leadership while node a has a route");
                });
        assertConsumes(a);

        a.getRouteController().stopRoute(ROUTE_ID);
        a.removeRoute(ROUTE_ID);
        assertNull(policyA.getRouteId());
        awaitLeader(b, policyB);
        assertConsumes(b);
    }

    @Test
    void routeReloadedWithANewPolicyRunsOnOneNode() throws Exception {
        HazelcastRoutePolicy policyA = newPolicy("a");
        CamelContext a = startNode("a", policyA);
        awaitLeader(a, policyA);
        HazelcastRoutePolicy policyB = newPolicy("b");
        CamelContext b = startNode("b", policyB);
        assertFollower(b, policyB);

        // a reload on the leader: the route is removed and added again, with a new policy
        a.getRouteController().stopRoute(ROUTE_ID);
        a.removeRoute(ROUTE_ID);
        HazelcastRoutePolicy policyA2 = newPolicy("a");
        a.addRoutes(route(policyA2));

        awaitOneLeader(a, policyA2, b, policyB);
        assertFalse(policyA.isLeader(), "The policy of the removed route is still the leader");
    }

    @Test
    void routeAddedAgainWithTheSamePolicyRunsOnOneNode() throws Exception {
        HazelcastRoutePolicy policyA = newPolicy("a");
        CamelContext a = startNode("a", policyA);
        awaitLeader(a, policyA);
        HazelcastRoutePolicy policyB = newPolicy("b");
        CamelContext b = startNode("b", policyB);
        assertFollower(b, policyB);

        // the same policy instance (for example a bean referenced by the route) for the route added again
        a.getRouteController().stopRoute(ROUTE_ID);
        a.removeRoute(ROUTE_ID);
        a.addRoutes(route(policyA));

        awaitOneLeader(a, policyA, b, policyB);
    }

    @Test
    void resumedRouteOnAFollowerKeepsItsConsumerStopped() throws Exception {
        HazelcastRoutePolicy policyA = newPolicy("a");
        CamelContext a = startNode("a", policyA);
        awaitLeader(a, policyA);
        HazelcastRoutePolicy policyB = newPolicy("b");
        CamelContext b = startNode("b", policyB);
        assertFollower(b, policyB);

        b.getRouteController().suspendRoute(ROUTE_ID);
        b.getRouteController().resumeRoute(ROUTE_ID);

        assertFalse(consumerStarted(b), "The consumer of the follower runs after the route was resumed");
        assertFalse(policyB.isLeader());

        // and the follower still starts its consumer when it takes over
        a.stop();
        awaitLeader(b, policyB);
        assertConsumes(b);
    }

    @Test
    void routeResumedAfterTheFollowerTookOverConsumes() throws Exception {
        HazelcastRoutePolicy policyA = newPolicy("a");
        CamelContext a = startNode("a", policyA);
        awaitLeader(a, policyA);
        HazelcastRoutePolicy policyB = newPolicy("b");
        CamelContext b = startNode("b", policyB);
        assertFollower(b, policyB);

        // the route of the follower is suspended when the follower takes over
        b.getRouteController().suspendRoute(ROUTE_ID);
        a.stop();
        await().atMost(20, TimeUnit.SECONDS).until(policyB::isLeader);

        b.getRouteController().resumeRoute(ROUTE_ID);
        assertTrue(consumerStarted(b), "The consumer of the leader is not started after the route was resumed");
        assertConsumes(b);
    }

    @Test
    void restartedNodeCompetesForTheLeadershipAgain() throws Exception {
        HazelcastRoutePolicy policyA = newPolicy("a");
        CamelContext a = startNode("a", policyA);
        awaitLeader(a, policyA);
        HazelcastRoutePolicy policyB = newPolicy("b");
        CamelContext b = startNode("b", policyB);
        assertFollower(b, policyB);

        // the stop of the CamelContext removes the route from the policy (onRemove), and the start gives it back
        a.stop();
        awaitLeader(b, policyB);
        a.start();
        assertFollower(a, policyA);

        // node a waits for the lock again, so it takes over when node b stops
        b.stop();
        awaitLeader(a, policyA);
        assertConsumes(a);
    }

    @Test
    void stopAfterTheRouteWasRemovedDoesNotWaitForTheLock() throws Exception {
        HazelcastRoutePolicy policyA = newPolicy("a");
        CamelContext a = startNode("a", policyA);
        awaitLeader(a, policyA);
        // node b uses a Hazelcast client, whose wait in tryLock ends when the thread is interrupted (on a member it
        // ends after the tryLock timeout)
        HazelcastRoutePolicy policyB = newPolicy(newClient(), "b");
        // the follower waits a long time for the lock, longer than the graceful shutdown of its thread pool
        policyB.setTryLockTimeout(60, TimeUnit.SECONDS);
        CamelContext b = startNode("b", policyB);
        b.getExecutorServiceManager().setShutdownAwaitTermination(TimeUnit.SECONDS.toMillis(30));
        assertFollower(b, policyB);

        // the route is removed while the leadership thread of node b waits for the lock
        b.getRouteController().stopRoute(ROUTE_ID);
        b.removeRoute(ROUTE_ID);

        // the stop of the policy ends that thread too, instead of waiting for the end of its tryLock
        long start = System.nanoTime();
        b.stop();
        long took = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(took < TimeUnit.SECONDS.toMillis(10), "The stop of node b took " + took + " ms");
    }

    private HazelcastRoutePolicy newPolicy(String node) {
        return newPolicy(member, node);
    }

    private static HazelcastRoutePolicy newPolicy(HazelcastInstance instance, String node) {
        HazelcastRoutePolicy policy = new HazelcastRoutePolicy(instance);
        policy.setLockMapName("camel-route-policy");
        policy.setLockKey("route-lock");
        policy.setLockValue(node);
        policy.setTryLockTimeout(1, TimeUnit.SECONDS);
        return policy;
    }

    private CamelContext startNode(String node, HazelcastRoutePolicy policy) throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        context.disableJMX();
        context.getCamelContextExtension().setName(node);
        contexts.add(context);
        context.addRoutes(route(policy));
        context.start();
        return context;
    }

    private static RouteBuilder route(HazelcastRoutePolicy policy) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:in").routeId(ROUTE_ID).routePolicy(policy).to("mock:out");
            }
        };
    }

    private static boolean consumerStarted(CamelContext context) {
        Route route = context.getRoute(ROUTE_ID);
        return route != null && ServiceHelper.isStarted(route.getConsumer());
    }

    private static void awaitLeader(CamelContext context, HazelcastRoutePolicy policy) {
        await().atMost(20, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertTrue(policy.isLeader(), "Node " + context.getName() + " is not the leader");
                    assertTrue(consumerStarted(context), "The consumer of the leader " + context.getName() + " is not started");
                });
    }

    private static void assertFollower(CamelContext context, HazelcastRoutePolicy policy) {
        assertFalse(policy.isLeader(), "Node " + context.getName() + " is the leader");
        assertFalse(consumerStarted(context), "The consumer of the follower " + context.getName() + " is started");
    }

    private static void awaitOneLeader(
            CamelContext a, HazelcastRoutePolicy policyA, CamelContext b, HazelcastRoutePolicy policyB)
            throws InterruptedException {
        await().atMost(20, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertNotEquals(policyA.isLeader(), policyB.isLeader(), "Exactly one node must be the leader");
                    assertTrue(consumerStarted(policyA.isLeader() ? a : b), "The consumer of the leader is not started");
                    assertFalse(consumerStarted(policyA.isLeader() ? b : a), "The consumer of the follower is started");
                });
        assertConsumes(policyA.isLeader() ? a : b);
    }

    private static void assertConsumes(CamelContext context) throws InterruptedException {
        MockEndpoint mock = context.getEndpoint("mock:out", MockEndpoint.class);
        mock.reset();
        mock.setResultWaitTime(TimeUnit.SECONDS.toMillis(20));
        mock.expectedBodiesReceived("hello");
        context.createFluentProducerTemplate().to("seda:in").withBody("hello").send();
        mock.assertIsSatisfied();
    }

    private HazelcastInstance newClient() {
        Address address = member.getCluster().getLocalMember().getAddress();
        ClientConfig config = new ClientConfig();
        config.setClusterName(member.getConfig().getClusterName());
        config.getNetworkConfig().addAddress(address.getHost() + ":" + address.getPort());
        config.getConnectionStrategyConfig().getConnectionRetryConfig().setClusterConnectTimeoutMillis(30000);
        client = HazelcastClient.newHazelcastClient(config);
        return client;
    }

    private static Config memberConfig(String clusterName) {
        Config config = new Config();
        config.setClusterName(clusterName);
        // a client of the test connects to the member on the loopback address
        config.setProperty("hazelcast.local.localAddress", "127.0.0.1");
        config.getNetworkConfig().getInterfaces().setEnabled(true).addInterface("127.0.0.1");
        config.getMetricsConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        return config;
    }
}
