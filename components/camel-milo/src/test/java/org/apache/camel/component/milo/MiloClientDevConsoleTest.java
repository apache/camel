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
package org.apache.camel.component.milo;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milo.server.MiloServerComponent;
import org.apache.camel.component.milo.server.internal.CamelNamespace;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.console.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The milo-client dev console lists the connections of the consumers, with their refused monitored items
 */
public class MiloClientDevConsoleTest extends AbstractMiloServerTest {

    // the client endpoints only differ in their nodes, so they share one connection, which creates the refused nodes
    // again every second
    private static final String MILO_CLIENT_BASE = "milo-client:opc.tcp://foo:bar@localhost:@@port@@"
                                                   + "?allowedSecurityPolicies=None&overrideHost=true"
                                                   + "&samplingInterval=50&requestedPublishingInterval=100"
                                                   + "&reconnectTimeout=1000";

    // namespaces that the server does not have until a test adds them
    private static final String LATE_NAMESPACE_URI = "urn:org:apache:camel:late";

    private static final String HELD_NAMESPACE_URI = "urn:org:apache:camel:held";

    private static final String MONITORED_NODE = "nsu=" + MiloServerComponent.DEFAULT_NAMESPACE_URI + ";s=monitoredItem";

    private static final String MISSING_NODE = "nsu=" + MiloServerComponent.DEFAULT_NAMESPACE_URI + ";s=missingItem";

    private static final String LATE_NAMESPACE_NODE = "nsu=" + LATE_NAMESPACE_URI + ";s=lateNamespaceItem";

    private static final String HELD_NODE = "nsu=" + HELD_NAMESPACE_URI + ";s=heldItem";

    private static final String MILO_CLIENT_NODES
            = MILO_CLIENT_BASE + "&nodes=RAW(" + MONITORED_NODE + "," + MISSING_NODE + "," + LATE_NAMESPACE_NODE + ")";

    private static final String MILO_CLIENT_OTHER
            = MILO_CLIENT_BASE + "&node=" + NodeIds.nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "otherItem");

    private static final String MILO_CLIENT_HELD = MILO_CLIENT_BASE + "&node=RAW(" + HELD_NODE + ")";

    // a connection of its own, whose requests time out after a second
    private static final String MILO_CLIENT_TIMEOUT_BASE = MILO_CLIENT_BASE + "&clientId=timeout&requestTimeout=1000";

    private static final String MILO_CLIENT_TIMEOUT = MILO_CLIENT_TIMEOUT_BASE + "&node=RAW(" + HELD_NODE + ")";

    private static final String MILO_CLIENT_TIMEOUT_MONITORED
            = MILO_CLIENT_TIMEOUT_BASE + "&node=RAW(" + MONITORED_NODE + ")";

    private static final String MILO_CLIENT_TIMEOUT_LATE_NAMESPACE
            = MILO_CLIENT_TIMEOUT_BASE + "&node=RAW(" + LATE_NAMESPACE_NODE + ")";

    // a connection of its own, through the relay: the client uses the port of the relay, not the one of the server
    private static final String MILO_CLIENT_RELAYED
            = MILO_CLIENT_BASE + "&overridePort=true&nodes=RAW(" + MONITORED_NODE + "," + MISSING_NODE + ")";

    // nothing listens on this port
    @RegisterExtension
    AvailablePortFinder.Port unusedPortHolder = AvailablePortFinder.find();

    @RegisterExtension
    AvailablePortFinder.Port relayPortHolder = AvailablePortFinder.find();

    private final TcpRelay relay = new TcpRelay();

    @EndpointInject("mock:nodes")
    protected MockEndpoint nodesEndpoint;

    @EndpointInject("mock:other")
    protected MockEndpoint otherEndpoint;

    @EndpointInject("mock:timeout")
    protected MockEndpoint timeoutEndpoint;

    @EndpointInject("mock:timeoutMonitored")
    protected MockEndpoint timeoutMonitoredEndpoint;

    @EndpointInject("mock:timeoutLateNamespace")
    protected MockEndpoint timeoutLateNamespaceEndpoint;

    @EndpointInject("mock:relayed")
    protected MockEndpoint relayedEndpoint;

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the consumers deliver the current values while they start, so the tests start these routes
                from(resolve(MILO_CLIENT_NODES)).routeId("nodes").autoStartup(false).to("mock:nodes");
                from(resolve(MILO_CLIENT_OTHER)).routeId("other").autoStartup(false).to("mock:other");
                from(resolve(MILO_CLIENT_HELD)).routeId("held").autoStartup(false).to("mock:held");
                from(resolve(MILO_CLIENT_TIMEOUT)).routeId("timeout").autoStartup(false).to("mock:timeout");
                from(resolve(MILO_CLIENT_TIMEOUT_MONITORED)).routeId("timeoutMonitored").autoStartup(false)
                        .to("mock:timeoutMonitored");
                from(resolve(MILO_CLIENT_TIMEOUT_LATE_NAMESPACE)).routeId("timeoutLateNamespace").autoStartup(false)
                        .to("mock:timeoutLateNamespace");
                from("milo-client:opc.tcp://localhost:" + unusedPortHolder.getPort()
                     + "?allowedSecurityPolicies=None&node=RAW(" + MONITORED_NODE + ")")
                        .routeId("unreachable").autoStartup(false).to("mock:unreachable");
                from(MILO_CLIENT_RELAYED.replace("@@port@@", Integer.toString(relayPortHolder.getPort())))
                        .routeId("relayed").autoStartup(false).to("mock:relayed");
            }
        };
    }

    @Override
    protected void doPostTearDown() throws Exception {
        // after the context, so that its client disconnects through the relay
        relay.stop();
    }

    @Test
    void consoleWithoutConsumersListsNoConnection() {
        final DevConsole console = resolveConsole();
        assertEquals("camel", console.getGroup());
        assertEquals("milo-client", console.getId());

        assertEquals("", console.call(DevConsole.MediaType.TEXT));
        final JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON);
        final JsonArray connections = out.getCollection("connections");
        assertTrue(connections.isEmpty());
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void consoleListsTheRefusedNodesOfAConnection() throws Exception {
        startNodes();

        final DevConsole console = resolveConsole();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            final JsonObject connection = singleConnection(console);
            assertEquals("opc.tcp://foo:xxxxxx@localhost:" + getServerPort(), connection.getString("endpointUri"));
            assertEquals(List.of("nodes"), connection.getCollection("routeIds"));
            assertTrue(connection.getBoolean("sessionActive"));
            assertEquals(1, connection.getInteger("monitoredNodes"));

            final JsonArray refusedNodes = connection.getCollection("refusedNodes");
            assertEquals(2, refusedNodes.size());
            // refused by the server
            assertRefusedNode(refusedNodes.get(0), MISSING_NODE, "Bad_NodeIdUnknown", null);
            // refused by the client, without asking the server
            assertRefusedNode(refusedNodes.get(1), LATE_NAMESPACE_NODE, "Bad_NodeIdUnknown",
                    "the namespace URI is unknown to the server");
        });

        assertEquals(String.format("%n    Endpoint: opc.tcp://foo:xxxxxx@localhost:%d", getServerPort())
                     + String.format("%n    Routes: nodes")
                     + String.format("%n    Session Active: true")
                     + String.format("%n    Monitored Nodes: 1")
                     + String.format("%n    Refused Nodes: 2")
                     + String.format("%n        %s: Bad_NodeIdUnknown", MISSING_NODE)
                     + String.format("%n        %s: Bad_NodeIdUnknown (the namespace URI is unknown to the server)",
                             LATE_NAMESPACE_NODE)
                     + "\n",
                console.call(DevConsole.MediaType.TEXT));
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void nodeCreatedOnTheServerLaterLeavesTheRefusedNodes() throws Exception {
        startNodes();
        final DevConsole console = resolveConsole();

        // the server gets the missing node, which the next attempt creates
        template.sendBody("milo-server:missingItem", "Foo");
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            final JsonObject connection = singleConnection(console);
            assertEquals(2, connection.getInteger("monitoredNodes"));
            final JsonArray refusedNodes = connection.getCollection("refusedNodes");
            assertEquals(1, refusedNodes.size());
            assertRefusedNode(refusedNodes.get(0), LATE_NAMESPACE_NODE, "Bad_NodeIdUnknown",
                    "the namespace URI is unknown to the server");
        });

        // the server gets the namespace without the node: the next attempt asks the server, which refuses it
        final CamelNamespace namespace = new CamelNamespace(LATE_NAMESPACE_URI, getServer().getServer());
        try {
            namespace.startup();
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                final JsonArray refusedNodes = singleConnection(console).getCollection("refusedNodes");
                assertEquals(1, refusedNodes.size());
                assertRefusedNode(refusedNodes.get(0), LATE_NAMESPACE_NODE, "Bad_NodeIdUnknown", null);
            });

            // and then the node
            namespace.getOrAddItem("lateNamespaceItem").update("Foo");
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                final JsonObject connection = singleConnection(console);
                assertEquals(3, connection.getInteger("monitoredNodes"));
                assertTrue(connection.<JsonArray> getCollection("refusedNodes").isEmpty());
            });
        } finally {
            namespace.shutdown();
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void failedAttemptIsTheStatusOfItsNodes() throws Exception {
        // refused by the client, the server does not have the namespace yet
        timeoutEndpoint.expectedMessageCount(1);
        context.getRouteController().startRoute("timeout");
        timeoutEndpoint.assertIsSatisfied();
        // and a node of a namespace that the server does not get in this test
        timeoutLateNamespaceEndpoint.expectedMessageCount(1);
        context.getRouteController().startRoute("timeoutLateNamespace");
        timeoutLateNamespaceEndpoint.assertIsSatisfied();

        // the server gets the node, but holds the CreateMonitoredItems calls of the next attempts until they time out
        final CountDownLatch release = new CountDownLatch(1);
        final CamelNamespace namespace = holdingNamespace(new CountDownLatch(1), new CountDownLatch(1), release);
        try {
            namespace.startup();
            namespace.getOrAddItem("heldItem").update("Foo");

            final DevConsole console = resolveConsole();
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertRefusedNode(
                    singleConnection(console).<JsonArray> getCollection("refusedNodes").get(0), HELD_NODE, "Bad_Timeout",
                    null));
            // the session stays active, and the other node keeps the refusal of the same attempt, which did not send it
            // to the server
            final JsonObject connection = singleConnection(console);
            assertTrue(connection.getBoolean("sessionActive"));
            final JsonArray refusedNodes = connection.getCollection("refusedNodes");
            assertEquals(2, refusedNodes.size());
            assertRefusedNode(refusedNodes.get(1), LATE_NAMESPACE_NODE, "Bad_NodeIdUnknown",
                    "the namespace URI is unknown to the server");

            // the server answers again, the next attempt creates the node
            release.countDown();
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                final JsonObject created = singleConnection(console);
                assertEquals(1, created.getInteger("monitoredNodes"));
                final JsonArray stillRefused = created.getCollection("refusedNodes");
                assertEquals(1, stillRefused.size());
                assertRefusedNode(stillRefused.get(0), LATE_NAMESPACE_NODE, "Bad_NodeIdUnknown",
                        "the namespace URI is unknown to the server");
            });
        } finally {
            release.countDown();
            namespace.shutdown();
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void consumersOfAConnectionAreListedTogether() throws Exception {
        startNodes();
        template.sendBody("milo-server:otherItem", "Bar");
        otherEndpoint.expectedMessageCount(1);
        context.getRouteController().startRoute("other");
        otherEndpoint.assertIsSatisfied();

        final DevConsole console = resolveConsole();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            final JsonObject connection = singleConnection(console);
            assertEquals(List.of("nodes", "other"), connection.getCollection("routeIds"));
            assertEquals(2, connection.getInteger("monitoredNodes"));
        });

        // a consumer that stops is not listed, and its node is not monitored anymore
        context.getRouteController().stopRoute("other");
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            final JsonObject connection = singleConnection(console);
            assertEquals(List.of("nodes"), connection.getCollection("routeIds"));
            assertEquals(1, connection.getInteger("monitoredNodes"));
        });
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void clientIdTellsTheConnectionsOfAServerApart() throws Exception {
        template.sendBody("milo-server:otherItem", "Bar");
        otherEndpoint.expectedMessageCount(1);
        context.getRouteController().startRoute("other");
        otherEndpoint.assertIsSatisfied();
        template.sendBody("milo-server:monitoredItem", "Foo");
        timeoutMonitoredEndpoint.expectedMessageCount(1);
        context.getRouteController().startRoute("timeoutMonitored");
        timeoutMonitoredEndpoint.assertIsSatisfied();

        // both connections have the endpoint of the server, the second one has a client id
        final DevConsole console = resolveConsole();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            final JsonArray connections = connections(console);
            assertEquals(2, connections.size());
            final JsonObject other = (JsonObject) connections.get(0);
            assertEquals("opc.tcp://foo:xxxxxx@localhost:" + getServerPort(), other.getString("endpointUri"));
            // omitted when none is set
            assertFalse(other.containsKey("clientId"));
            assertEquals(List.of("other"), other.getCollection("routeIds"));
            assertEquals(1, other.getInteger("monitoredNodes"));
            final JsonObject timeout = (JsonObject) connections.get(1);
            assertEquals(other.getString("endpointUri"), timeout.getString("endpointUri"));
            assertEquals("timeout", timeout.getString("clientId"));
            assertEquals(List.of("timeoutMonitored"), timeout.getCollection("routeIds"));
            assertEquals(1, timeout.getInteger("monitoredNodes"));
        });

        assertEquals(String.format("%n    Endpoint: opc.tcp://foo:xxxxxx@localhost:%d", getServerPort())
                     + String.format("%n    Routes: other")
                     + String.format("%n    Session Active: true")
                     + String.format("%n    Monitored Nodes: 1")
                     + String.format("%n    Refused Nodes: 0")
                     + "\n"
                     + String.format("%n    Endpoint: opc.tcp://foo:xxxxxx@localhost:%d", getServerPort())
                     + String.format("%n    Client Id: timeout")
                     + String.format("%n    Routes: timeoutMonitored")
                     + String.format("%n    Session Active: true")
                     + String.format("%n    Monitored Nodes: 1")
                     + String.format("%n    Refused Nodes: 0")
                     + "\n",
                console.call(DevConsole.MediaType.TEXT));
    }

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void lostSessionKeepsTheNodesOfItsConnection() throws Exception {
        startNodes();
        relay.start(relayPortHolder.getPort(), getServerPort());
        relayedEndpoint.expectedMessageCount(2);
        context.getRouteController().startRoute("relayed");
        relayedEndpoint.assertIsSatisfied();

        // each connection is listed with the routes that use it, in the order of the routes
        final DevConsole console = resolveConsole();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            final JsonArray connections = connections(console);
            assertEquals(2, connections.size());
            assertEquals(List.of("nodes"), ((JsonObject) connections.get(0)).getCollection("routeIds"));
            final JsonObject relayed = (JsonObject) connections.get(1);
            assertEquals("opc.tcp://foo:xxxxxx@localhost:" + relayPortHolder.getPort(), relayed.getString("endpointUri"));
            assertEquals(List.of("relayed"), relayed.getCollection("routeIds"));
            assertTrue(relayed.getBoolean("sessionActive"));
            assertEquals(1, relayed.getInteger("monitoredNodes"));
            final JsonArray refusedNodes = relayed.getCollection("refusedNodes");
            assertEquals(1, refusedNodes.size());
            assertRefusedNode(refusedNodes.get(0), MISSING_NODE, "Bad_NodeIdUnknown", null);
        });

        // the relay goes down: the session of its connection is lost, which keeps its nodes until it is active again
        relay.stop();
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            final JsonArray connections = connections(console);
            assertEquals(2, connections.size());
            assertTrue(((JsonObject) connections.get(0)).getBoolean("sessionActive"));
            final JsonObject relayed = (JsonObject) connections.get(1);
            assertFalse(relayed.getBoolean("sessionActive"));
            assertEquals(1, relayed.getInteger("monitoredNodes"));
            final JsonArray refusedNodes = relayed.getCollection("refusedNodes");
            assertEquals(1, refusedNodes.size());
            // its status is the one of the latest attempt, which the drop may have failed
            assertEquals(MISSING_NODE, ((JsonObject) refusedNodes.get(0)).getString("nodeId"));
        });

        relay.start(relayPortHolder.getPort(), getServerPort());
        await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
            final JsonArray connections = connections(console);
            assertEquals(2, connections.size());
            assertTrue(((JsonObject) connections.get(1)).getBoolean("sessionActive"));
        });
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void connectionWithoutSessionMonitorsNoNode() throws Exception {
        context.getRouteController().startRoute("unreachable");

        final JsonObject connection = singleConnection(resolveConsole());
        assertEquals("opc.tcp://localhost:" + unusedPortHolder.getPort(), connection.getString("endpointUri"));
        assertEquals(List.of("unreachable"), connection.getCollection("routeIds"));
        assertFalse(connection.getBoolean("sessionActive"));
        assertEquals(0, connection.getInteger("monitoredNodes"));
        assertTrue(connection.<JsonArray> getCollection("refusedNodes").isEmpty());
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void connectionBeingClosedMonitorsNoNode() throws Exception {
        template.sendBody("milo-server:monitoredItem", "Foo");
        timeoutMonitoredEndpoint.expectedMessageCount(1);
        context.getRouteController().startRoute("timeoutMonitored");
        timeoutMonitoredEndpoint.assertIsSatisfied();

        // the server holds the CreateMonitoredItems call of the next consumer until it times out, which closes the
        // connection, and then the CloseSession call, which deletes the monitored items
        final CountDownLatch deleting = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CamelNamespace namespace = holdingNamespace(new CountDownLatch(1), deleting, release);
        CompletableFuture<Void> start = null;
        try {
            namespace.startup();
            namespace.getOrAddItem("heldItem").update("Foo");
            start = startRouteAsync("timeout");
            assertTrue(deleting.await(10, TimeUnit.SECONDS));

            // its nodes have been reported lost while the client waits for the server
            final DevConsole console = resolveConsole();
            final JsonObject connection = singleConnection(console);
            assertFalse(connection.getBoolean("sessionActive"));
            assertEquals(0, connection.getInteger("monitoredNodes"));
            assertTrue(connection.<JsonArray> getCollection("refusedNodes").isEmpty());

            // the server answers again, the connection is built up again with both nodes
            release.countDown();
            start.get(30, TimeUnit.SECONDS);
            await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
                final JsonObject rebuilt = singleConnection(console);
                assertEquals(List.of("timeout", "timeoutMonitored"), rebuilt.getCollection("routeIds"));
                assertTrue(rebuilt.getBoolean("sessionActive"));
                assertEquals(2, rebuilt.getInteger("monitoredNodes"));
            });
        } finally {
            release.countDown();
            if (start != null) {
                // the start ends once the client stops waiting for the server, its failure fails the test above
                start.handle((result, e) -> null).get(30, TimeUnit.SECONDS);
            }
            namespace.shutdown();
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void consoleDoesNotWaitForAServiceCallInProgress() throws Exception {
        startNodes();

        // the server holds the CreateMonitoredItems call of the next consumer, which holds the lock of the connection
        final CountDownLatch creating = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CamelNamespace namespace = holdingNamespace(creating, new CountDownLatch(1), release);
        CompletableFuture<Void> start = null;
        try {
            namespace.startup();
            namespace.getOrAddItem("heldItem").update("Foo");
            start = startRouteAsync("held");
            assertTrue(creating.await(10, TimeUnit.SECONDS));

            final DevConsole console = resolveConsole();
            final JsonObject connection = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> singleConnection(console));
            // the call was still in progress
            assertFalse(start.isDone());
            assertEquals(List.of("nodes"), connection.getCollection("routeIds"));
            assertEquals(1, connection.getInteger("monitoredNodes"));
            assertEquals(2, connection.<JsonArray> getCollection("refusedNodes").size());

            release.countDown();
            start.get(30, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            if (start != null) {
                // the start ends once the server answers, its failure fails the test above
                start.handle((result, e) -> null).get(30, TimeUnit.SECONDS);
            }
            namespace.shutdown();
        }
    }

    /**
     * Starts the consumer of a node that the server has, one that it does not have and one of a namespace that it does
     * not have, and waits for the value of the first and the refusals of the others
     */
    private void startNodes() throws Exception {
        template.sendBody("milo-server:monitoredItem", "Foo");
        nodesEndpoint.expectedMessageCount(3);
        context.getRouteController().startRoute("nodes");
        nodesEndpoint.assertIsSatisfied();
    }

    /**
     * Starts a route off the test thread, its consumer waits for the server while it starts
     */
    private CompletableFuture<Void> startRouteAsync(final String routeId) {
        return CompletableFuture.runAsync(() -> {
            try {
                context.getRouteController().startRoute(routeId);
            } catch (final Exception e) {
                throw new CompletionException(e);
            }
        });
    }

    /**
     * A namespace of the server that holds the creation and the deletion of its monitored items until it is released.
     * The server answers a CreateMonitoredItems call once the items are created, and a CloseSession call once the items
     * of the session are deleted.
     */
    private CamelNamespace holdingNamespace(
            final CountDownLatch creating, final CountDownLatch deleting, final CountDownLatch release) {
        return new CamelNamespace(HELD_NAMESPACE_URI, getServer().getServer()) {
            @Override
            public void onDataItemsCreated(final List<DataItem> dataItems) {
                hold(creating, release);
                super.onDataItemsCreated(dataItems);
            }

            @Override
            public void onDataItemsDeleted(final List<DataItem> dataItems) {
                hold(deleting, release);
                super.onDataItemsDeleted(dataItems);
            }
        };
    }

    private static void hold(final CountDownLatch holding, final CountDownLatch release) {
        holding.countDown();
        try {
            release.await(30, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private DevConsole resolveConsole() {
        final DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("milo-client");
        assertNotNull(console);
        return console;
    }

    private static JsonArray connections(final DevConsole console) {
        final JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON);
        return out.getCollection("connections");
    }

    private static JsonObject singleConnection(final DevConsole console) {
        final JsonArray connections = connections(console);
        assertEquals(1, connections.size());
        return (JsonObject) connections.get(0);
    }

    private static void assertRefusedNode(
            final Object refusedNode, final String nodeId, final String status, final String reason) {
        final JsonObject node = (JsonObject) refusedNode;
        assertEquals(nodeId, node.getString("nodeId"));
        assertEquals(status, node.getString("status"));
        if (reason == null) {
            // omitted when the server was asked
            assertFalse(node.containsKey("reason"));
        } else {
            assertEquals(reason, node.getString("reason"));
        }
    }

    private MiloServerComponent getServer() {
        return context.getComponent("milo-server", MiloServerComponent.class);
    }
}
