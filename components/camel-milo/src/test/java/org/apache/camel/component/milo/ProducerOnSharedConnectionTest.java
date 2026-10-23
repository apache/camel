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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milo.client.MiloClientConnection;
import org.apache.camel.component.milo.client.MiloClientEndpoint;
import org.apache.camel.component.milo.client.MonitorFilterConfiguration;
import org.apache.camel.component.milo.client.internal.SubscriptionManager;
import org.apache.camel.component.milo.server.MiloServerComponent;
import org.apache.camel.component.milo.server.internal.CamelNamespace;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.server.AddressSpace;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.milo.NodeIds.nodeValue;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The producers of a connection that consumers share do not wait while a consumer creates its monitored items, and a
 * request that fails sets its failure on the exchange without rebuilding the connection: the consumers keep their
 * monitored items and their session.
 */
public class ProducerOnSharedConnectionTest extends AbstractMiloServerTest {

    // a namespace that the server does not have until a test adds it
    private static final String HELD_NAMESPACE_URI = "urn:org:apache:camel:held";

    private static final String HELD_NODE = "nsu=" + HELD_NAMESPACE_URI + ";s=heldItem";

    // a namespace whose node the server does not have, so it refuses to monitor it
    private static final String REFUSED_NAMESPACE_URI = "urn:org:apache:camel:refused";

    private static final String REFUSED_NODE = "refusedItem";

    // the client endpoints only differ in their nodes, so they share one connection
    private static final String MILO_CLIENT_BASE = "milo-client:opc.tcp://foo:bar@localhost:@@port@@"
                                                   + "?allowedSecurityPolicies=None&overrideHost=true"
                                                   + "&samplingInterval=50&requestedPublishingInterval=100";

    private static final String ITEM_1 = "&node=" + nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "myitem1");

    private static final String ITEM_2 = "&node=" + nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "myitem2");

    private static final String HELD = "&node=RAW(" + HELD_NODE + ")";

    // a connection of its own, whose requests time out after a second
    private static final String MILO_CLIENT_TIMEOUT_BASE = MILO_CLIENT_BASE + "&clientId=timeout&requestTimeout=1000";

    // a connection of its own, through the relay: the client uses the port of the relay, not the one of the server
    private static final String MILO_CLIENT_RELAYED_BASE = MILO_CLIENT_BASE + "&overridePort=true";

    @RegisterExtension
    AvailablePortFinder.Port relayPortHolder = AvailablePortFinder.find();

    private final TcpRelay relay = new TcpRelay();

    @EndpointInject("mock:item1")
    protected MockEndpoint item1Endpoint;

    @EndpointInject("mock:item2")
    protected MockEndpoint item2Endpoint;

    @EndpointInject("mock:timeoutItem1")
    protected MockEndpoint timeoutItem1Endpoint;

    @EndpointInject("mock:relayedItem1")
    protected MockEndpoint relayedItem1Endpoint;

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the consumers deliver the current values while they start, so the tests start these routes
                from(resolve(MILO_CLIENT_BASE + ITEM_1)).routeId("item1").autoStartup(false).to("mock:item1");
                from(resolve(MILO_CLIENT_BASE + ITEM_2)).routeId("item2").autoStartup(false).to("mock:item2");
                from(resolve(MILO_CLIENT_TIMEOUT_BASE + ITEM_1)).routeId("timeoutItem1").autoStartup(false)
                        .to("mock:timeoutItem1");
                from(relayed(MILO_CLIENT_RELAYED_BASE + ITEM_1)).routeId("relayedItem1").autoStartup(false)
                        .to("mock:relayedItem1");
                from(relayed(MILO_CLIENT_RELAYED_BASE + ITEM_2)).routeId("relayedItem2").autoStartup(false)
                        .to("mock:relayedItem2");

                // a write that the route does not wait for
                from("direct:relayedWrite").to(relayed(MILO_CLIENT_RELAYED_BASE + ITEM_1));
            }
        };
    }

    private String relayed(final String uri) {
        return uri.replace("@@port@@", Integer.toString(relayPortHolder.getPort()));
    }

    @Override
    protected void doPostSetup() throws Exception {
        relay.start(relayPortHolder.getPort(), getServerPort());
    }

    @Override
    protected void doPostTearDown() throws Exception {
        // after the context, so that its client disconnects through the relay
        relay.stop();
    }

    /**
     * A consumer that starts while the link to the server is silent waits for the server until the keep-alive of the
     * client declares the session lost. A producer and {@link MiloClientConnection#getOpcUaClient()} of the same
     * connection do not wait for it.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void producerDoesNotWaitForAConsumerThatStartsOnASilentLink() throws Exception {
        template.sendBody("milo-server:myitem1", "Foo");
        relayedItem1Endpoint.expectedMessageCount(1);
        testBody(relayedItem1Endpoint.message(0), assertGoodValue("Foo"));
        context.getRouteController().startRoute("relayedItem1");
        relayedItem1Endpoint.assertIsSatisfied();

        final MiloClientEndpoint endpoint
                = context.getEndpoint(relayed(MILO_CLIENT_RELAYED_BASE + ITEM_1), MiloClientEndpoint.class);
        final MiloClientConnection connection = endpoint.createConnection();
        final CompletableFuture<Void> start = new CompletableFuture<>();
        try {
            final OpcUaClient client = connection.getOpcUaClient();
            assertNotNull(client);

            // the link drops silently, then the next consumer starts: it waits for the answer to its
            // CreateMonitoredItems call, which does not come
            relay.silence();
            final Thread starter = new Thread(() -> {
                try {
                    context.getRouteController().startRoute("relayedItem2");
                    start.complete(null);
                } catch (final Exception e) {
                    start.completeExceptionally(e);
                }
            }, "start-relayedItem2");
            starter.start();
            // the consumer holds the lock of the manager while it waits for the server
            await().atMost(10, TimeUnit.SECONDS).until(() -> waitsInPutSubscriptions(starter));

            assertSame(client, assertTimeoutPreemptively(Duration.ofSeconds(2), connection::getOpcUaClient));
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> template.sendBody("direct:relayedWrite", "Bar"));
            // the consumer was still waiting for the server
            assertFalse(start.isDone());
        } finally {
            // the connections are dropped, which fails the call of the consumer
            relay.dropConnections();
            start.handle((result, e) -> null).get(30, TimeUnit.SECONDS);
            endpoint.releaseConnection(connection);
        }
    }

    private static boolean waitsInPutSubscriptions(final Thread thread) {
        final Thread.State state = thread.getState();
        return (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING)
                && Arrays.stream(thread.getStackTrace())
                        .anyMatch(frame -> frame.getClassName().startsWith(SubscriptionManager.class.getName())
                                && frame.getMethodName().equals("putSubscriptions"));
    }

    /**
     * A write that times out because the server does not answer it in time fails the exchange. The session is still
     * active, so the connection is kept: its consumer reports no loss and keeps receiving values in the same session.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void writeThatTimesOutKeepsTheConnection() throws Exception {
        template.sendBody("milo-server:myitem1", "Foo");
        timeoutItem1Endpoint.expectedMessageCount(1);
        testBody(timeoutItem1Endpoint.message(0), assertGoodValue("Foo"));
        context.getRouteController().startRoute("timeoutItem1");
        timeoutItem1Endpoint.assertIsSatisfied();

        final CountDownLatch writing = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CamelNamespace namespace = holdingNamespace(writing, release);
        final MiloClientEndpoint endpoint
                = context.getEndpoint(resolve(MILO_CLIENT_TIMEOUT_BASE + ITEM_1), MiloClientEndpoint.class);
        final MiloClientConnection connection = endpoint.createConnection();
        try {
            namespace.startup();
            namespace.getOrAddItem("heldItem").update("Foo");
            final OpcUaClient client = connection.getOpcUaClient();
            final NodeId sessionId = client.getSession().getSessionId();

            // the consumer receives the next value only, no loss report and not its value again
            timeoutItem1Endpoint.reset();
            timeoutItem1Endpoint.expectedMessageCount(1);
            timeoutItem1Endpoint.setAssertPeriod(2_000);
            testBody(timeoutItem1Endpoint.message(0), assertGoodValue("Bar"));

            // the server holds the write
            final Exchange written = template.send(resolve(MILO_CLIENT_TIMEOUT_BASE + HELD),
                    exchange -> {
                        exchange.getMessage().setBody("Baz");
                        exchange.getMessage().setHeader(MiloConstants.HEADER_AWAIT, true);
                    });
            assertTrue(writing.await(10, TimeUnit.SECONDS));
            assertStatus(StatusCodes.Bad_Timeout, written);

            template.sendBody("milo-server:myitem1", "Bar");
            timeoutItem1Endpoint.assertIsSatisfied();

            assertSame(client, connection.getOpcUaClient());
            assertEquals(sessionId, client.getSession().getSessionId());
        } finally {
            release.countDown();
            endpoint.releaseConnection(connection);
            namespace.shutdown();
        }
    }

    /**
     * A write in flight on a connection that Camel replaces fails with the status of its closed connection. Its failure
     * does not rebuild the connection that replaced it.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void writeOnAReplacedConnectionFailsAndKeepsItsReplacement() throws Exception {
        template.sendBody("milo-server:myitem1", "Foo");
        template.sendBody("milo-server:myitem2", "Foo");
        item1Endpoint.expectedMessageCount(1);
        testBody(item1Endpoint.message(0), assertGoodValue("Foo"));
        context.getRouteController().startRoute("item1");
        item1Endpoint.assertIsSatisfied();

        final CountDownLatch writing = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CamelNamespace namespace = holdingNamespace(writing, release);
        final MiloClientEndpoint endpoint = context.getEndpoint(resolve(MILO_CLIENT_BASE + ITEM_1), MiloClientEndpoint.class);
        final MiloClientConnection connection = endpoint.createConnection();
        try {
            namespace.startup();
            namespace.getOrAddItem("heldItem").update("Foo");
            final OpcUaClient client = connection.getOpcUaClient();
            item1Endpoint.reset();

            // the server holds the write
            final CompletableFuture<Exchange> written = template.asyncSend(resolve(MILO_CLIENT_BASE + HELD),
                    exchange -> {
                        exchange.getMessage().setBody("Baz");
                        exchange.getMessage().setHeader(MiloConstants.HEADER_AWAIT, true);
                    });
            assertTrue(writing.await(10, TimeUnit.SECONDS));

            // the server deletes the subscription, so the CreateMonitoredItems call of the next consumer fails as a
            // whole, which rebuilds the connection
            final UInteger subscriptionId = client.getSubscriptions().get(0).getSubscriptionId().orElseThrow();
            assertTrue(client.deleteSubscriptions(List.of(subscriptionId)).getResults()[0].isGood());
            context.getRouteController().startRoute("item2");

            assertStatus(StatusCodes.Bad_ConnectionClosed, written.get(30, TimeUnit.SECONDS));
            final OpcUaClient replacement = await().atMost(30, TimeUnit.SECONDS)
                    .until(connection::getOpcUaClient, current -> current != null && current != client);
            final NodeId sessionId = replacement.getSession().getSessionId();
            // the consumer receives its current value from the replacement
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                final List<Exchange> received = item1Endpoint.getReceivedExchanges();
                assertFalse(received.isEmpty());
                assertGoodValue("Foo").accept(received.get(received.size() - 1).getMessage().getBody(DataValue.class));
            });

            // the replacement is kept: the consumer receives the next value only
            item1Endpoint.reset();
            item1Endpoint.expectedMessageCount(1);
            item1Endpoint.setAssertPeriod(2_000);
            testBody(item1Endpoint.message(0), assertGoodValue("Bar"));
            template.sendBody("milo-server:myitem1", "Bar");
            item1Endpoint.assertIsSatisfied();

            assertSame(replacement, connection.getOpcUaClient());
            assertNotSame(client, replacement);
            assertEquals(sessionId, replacement.getSession().getSessionId());
        } finally {
            release.countDown();
            endpoint.releaseConnection(connection);
            namespace.shutdown();
        }
    }

    /**
     * Asserts that an awaited request failed with the given status code of the client
     */
    /**
     * A connection creates a node that the server refused again while its producer sends a request. With one processor
     * the executor that completes the requests has a single thread, as here: a write completes while the server holds
     * the attempt, which waits for its answer.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void writeDoesNotWaitForTheAttemptToCreateARefusedNodeAgain() throws Exception {
        template.sendBody("milo-server:myitem1", "Foo");

        final AtomicBoolean holding = new AtomicBoolean();
        final CountDownLatch creating = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CamelNamespace namespace = new CamelNamespace(REFUSED_NAMESPACE_URI, getServer().getServer()) {
            @Override
            public List<DataValue> read(
                    final AddressSpace.ReadContext context, final Double maxAge, final TimestampsToReturn timestamps,
                    final List<ReadValueId> readValueIds) {
                // the server reads the attributes of a node before it creates its monitored item
                if (holding.get() && readValueIds.stream()
                        .anyMatch(id -> REFUSED_NODE.equals(id.getNodeId().getIdentifier()))) {
                    creating.countDown();
                    try {
                        release.await(30, TimeUnit.SECONDS);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return super.read(context, maxAge, timestamps, readValueIds);
            }
        };
        namespace.startup();

        final MiloClientEndpoint endpoint
                = context.getEndpoint(resolve(MILO_CLIENT_BASE + ITEM_1), MiloClientEndpoint.class);
        // a connection of its own that completes its requests on a single thread, as with one processor, and tries to
        // create the refused node again every 500 ms
        final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final SubscriptionManager manager = new SubscriptionManager(
                endpoint.getConfiguration(), reconnectExecutor, executor, 500);
        try {
            final List<DataValue> refusals = new CopyOnWriteArrayList<>();
            manager.registerItems(
                    Map.of(ExpandedNodeId.parse("nsu=" + REFUSED_NAMESPACE_URI + ";s=" + REFUSED_NODE), refusals::add),
                    50.0, null, new MonitorFilterConfiguration());
            await().atMost(10, TimeUnit.SECONDS).until(() -> !refusals.isEmpty());
            assertEquals(new StatusCode(StatusCodes.Bad_NodeIdUnknown), refusals.get(0).getStatusCode());

            // the server holds the next attempt to create the node
            holding.set(true);
            assertTrue(creating.await(10, TimeUnit.SECONDS));

            final ExpandedNodeId item = ExpandedNodeId.parse(endpoint.getNode());
            assertTimeoutPreemptively(Duration.ofSeconds(2),
                    () -> manager.write(item, new DataValue(new Variant("Bar"))).get());
            // the attempt was still waiting for the server
            assertEquals(1, release.getCount());
        } finally {
            release.countDown();
            manager.dispose();
            reconnectExecutor.shutdownNow();
            executor.shutdownNow();
            namespace.shutdown();
        }
    }

    private static void assertStatus(final long statusCode, final Exchange exchange) {
        final UaException failure = ObjectHelper.getException(UaException.class, exchange.getException());
        assertNotNull(failure, () -> "Expected a UaException, but the exchange failed with " + exchange.getException());
        assertEquals(new StatusCode(statusCode), failure.getStatusCode());
    }

    /**
     * A namespace of the server that holds the writes to its nodes until it is released
     */
    private CamelNamespace holdingNamespace(final CountDownLatch writing, final CountDownLatch release) {
        return new CamelNamespace(HELD_NAMESPACE_URI, getServer().getServer()) {
            @Override
            public List<StatusCode> write(final AddressSpace.WriteContext context, final List<WriteValue> writeValues) {
                writing.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return super.write(context, writeValues);
            }
        };
    }

    private MiloServerComponent getServer() {
        return context.getComponent("milo-server", MiloServerComponent.class);
    }
}
