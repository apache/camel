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

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milo.client.MiloClientConnection;
import org.apache.camel.component.milo.client.MiloClientEndpoint;
import org.apache.camel.component.milo.server.MiloServerComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaSubscription;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.Session;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.awaitility.Awaitility.await;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testing the monitored items of consumers sharing a connection, when a consumer is restarted, its node does not exist,
 * the server rejects the CreateMonitoredItems service call or the subscription stops publishing or times out
 */
public class MonitorItemLifecycleTest extends AbstractMiloServerTest {

    private static final String DIRECT_START_1 = "direct:start1";

    private static final String DIRECT_START_2 = "direct:start2";

    private static final String MILO_SERVER_ITEM_1 = "milo-server:myitem1";

    private static final String MILO_SERVER_ITEM_2 = "milo-server:myitem2";

    private static final String NODE_ITEM_1 = "nsu=" + MiloServerComponent.DEFAULT_NAMESPACE_URI + ";s=myitem1";

    private static final String NODE_ITEM_2 = "nsu=" + MiloServerComponent.DEFAULT_NAMESPACE_URI + ";s=myitem2";

    private static final String NODE_UNKNOWN_ITEM_WRITE_BACK
            = "nsu=" + MiloServerComponent.DEFAULT_NAMESPACE_URI + ";s=unknownItemWriteBack";

    // the client endpoints only differ in the node and the consumer options, so they share one connection
    private static final String MILO_CLIENT_BASE = "milo-client:opc.tcp://foo:bar@localhost:@@port@@"
                                                   + "?allowedSecurityPolicies=None&overrideHost=true"
                                                   + "&samplingInterval=50&requestedPublishingInterval=100";

    private static final String MILO_CLIENT = MILO_CLIENT_BASE + "&node=";

    private static final String MILO_CLIENT_ITEM_1
            = MILO_CLIENT + NodeIds.nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "myitem1");

    private static final String MILO_CLIENT_ITEM_2
            = MILO_CLIENT + NodeIds.nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "myitem2");

    private static final String MILO_CLIENT_UNKNOWN_ITEM
            = MILO_CLIENT + NodeIds.nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "unknownItem");

    private static final String MILO_CLIENT_UNKNOWN_ITEM_WRITE_BACK
            = MILO_CLIENT + NodeIds.nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "unknownItemWriteBack");

    private static final String MILO_CLIENT_ITEMS_1_2
            = MILO_CLIENT_BASE + "&nodes=RAW(" + NODE_ITEM_1 + "," + NODE_ITEM_2 + ")";

    private static final String MOCK_ITEM_1 = "mock:item1";

    private static final String MOCK_ITEM_2 = "mock:item2";

    private static final String MOCK_UNKNOWN_ITEM = "mock:unknownItem";

    private static final String MOCK_WRITE_BACK = "mock:writeBack";

    private static final String MOCK_ITEMS_1_2 = "mock:items12";

    // the route of the first item takes longer than the watchdog of a shortened subscription for this value, once
    private static final String SLOW_VALUE = "Slow";

    private final AtomicBoolean slowValueProcessed = new AtomicBoolean();

    @EndpointInject(MOCK_ITEM_1)
    protected MockEndpoint item1Endpoint;

    @EndpointInject(MOCK_ITEM_2)
    protected MockEndpoint item2Endpoint;

    @EndpointInject(MOCK_UNKNOWN_ITEM)
    protected MockEndpoint unknownItemEndpoint;

    @EndpointInject(MOCK_WRITE_BACK)
    protected MockEndpoint writeBackEndpoint;

    @EndpointInject(MOCK_ITEMS_1_2)
    protected MockEndpoint items12Endpoint;

    @Produce(DIRECT_START_1)
    protected ProducerTemplate producer1;

    @Produce(DIRECT_START_2)
    protected ProducerTemplate producer2;

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(DIRECT_START_1).to(MILO_SERVER_ITEM_1);
                from(DIRECT_START_2).to(MILO_SERVER_ITEM_2);

                from(resolve(MILO_CLIENT_UNKNOWN_ITEM)).routeId("unknownItem").to(MOCK_UNKNOWN_ITEM);

                // the consumers deliver the current values while they start, so the tests start these routes
                from(resolve(MILO_CLIENT_ITEM_1)).routeId("item1").autoStartup(false)
                        // the slow route under test: a synchronous delay holds the delivery thread, once
                        .filter(exchange -> isSlowValueOnce(exchange))
                        .delay(3000).syncDelayed().end() // the delay is a block of its own
                        .end()
                        .to(MOCK_ITEM_1);
                from(resolve(MILO_CLIENT_ITEM_2)).routeId("item2").autoStartup(false).to(MOCK_ITEM_2);
                from(resolve(MILO_CLIENT_ITEMS_1_2)).routeId("items12").autoStartup(false).to(MOCK_ITEMS_1_2);

                // the bad status of the unknown node is processed on another thread, which writes to the same connection
                from(resolve(MILO_CLIENT_UNKNOWN_ITEM_WRITE_BACK)).routeId("writeBack").autoStartup(false)
                        .threads(1)
                        .setBody(constant("Foo"))
                        .to(resolve(MILO_CLIENT_ITEM_1 + "&defaultAwaitWrites=true"))
                        .to(MOCK_WRITE_BACK);
            }
        };
    }

    private boolean isSlowValueOnce(Exchange exchange) {
        final Object value = exchange.getMessage().getBody(DataValue.class).getValue().getValue();
        return SLOW_VALUE.equals(value) && slowValueProcessed.compareAndSet(false, true);
    }

    @Test
    void restartedConsumerReceivesEachValueOnce() throws Exception {
        producer1.sendBody("Foo");

        item1Endpoint.expectedMessageCount(1);
        testBody(item1Endpoint.message(0), assertGoodValue("Foo"));
        context.getRouteController().startRoute("item1");
        item1Endpoint.assertIsSatisfied();

        // the other consumers keep the shared connection open
        context.getRouteController().stopRoute("item1");

        item1Endpoint.reset();
        item1Endpoint.expectedMessageCount(0);
        item1Endpoint.setAssertPeriod(1000);
        producer1.sendBody("Bar");
        item1Endpoint.assertIsSatisfied();

        item1Endpoint.reset();
        item1Endpoint.expectedMessageCount(1);
        testBody(item1Endpoint.message(0), assertGoodValue("Bar"));
        context.getRouteController().startRoute("item1");
        item1Endpoint.assertIsSatisfied();

        item1Endpoint.reset();
        item1Endpoint.expectedMessageCount(1);
        item1Endpoint.setAssertPeriod(1000);
        testBody(item1Endpoint.message(0), assertGoodValue("Baz"));
        producer1.sendBody("Baz");
        item1Endpoint.assertIsSatisfied();
    }

    @Test
    void unknownNodeReceivesBadStatus() throws Exception {
        unknownItemEndpoint.expectedMinimumMessageCount(1);
        testBody(unknownItemEndpoint.message(0), assertStatus(StatusCodes.Bad_NodeIdUnknown));
        unknownItemEndpoint.assertIsSatisfied();

        // the other consumers of the connection are not affected
        producer1.sendBody("Foo");

        item1Endpoint.expectedMessageCount(1);
        testBody(item1Endpoint.message(0), assertGoodValue("Foo"));
        context.getRouteController().startRoute("item1");
        item1Endpoint.assertIsSatisfied();
    }

    @Test
    void itemThatWasNotWrittenYetReportsABadStatusAtStartup() throws Exception {
        // the first notification carries the current value of the item, which is a Bad status without a value until
        // the item is written; the consumer delivers it like any other Bad status
        item2Endpoint.expectedMessageCount(1);
        item2Endpoint.expectedHeaderReceived(MiloConstants.HEADER_NODE_ID, NODE_ITEM_2);
        testBody(item2Endpoint.message(0), value -> {
            assertTrue(value.getStatusCode().isBad(), value.getStatusCode().toString());
            assertNull(value.getValue().getValue());
        });
        context.getRouteController().startRoute("item2");
        item2Endpoint.assertIsSatisfied();

        item2Endpoint.reset();
        item2Endpoint.expectedMessageCount(1);
        testBody(item2Endpoint.message(0), assertGoodValue("Bar"));
        producer2.sendBody("Bar");
        item2Endpoint.assertIsSatisfied();
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void badStatusIsDeliveredWithoutHoldingTheConnectionLock() throws Exception {
        // the route of the bad status writes to the shared connection from another thread while the consumer starts
        writeBackEndpoint.expectedMessageCount(1);
        writeBackEndpoint.expectedHeaderReceived(MiloConstants.HEADER_NODE_ID, NODE_UNKNOWN_ITEM_WRITE_BACK);
        testBody(writeBackEndpoint.message(0), StatusCode.class, status -> assertTrue(status.isGood(), status.toString()));

        context.getRouteController().startRoute("writeBack");

        writeBackEndpoint.assertIsSatisfied();
    }

    @Test
    void failedCreateMonitoredItemsCallRebuildsTheConnection() throws Exception {
        producer1.sendBody("Foo");
        producer2.sendBody("Bar");

        item2Endpoint.expectedMessageCount(1);
        testBody(item2Endpoint.message(0), assertGoodValue("Bar"));
        context.getRouteController().startRoute("item2");
        item2Endpoint.assertIsSatisfied();

        // the server deletes the subscription, so the next CreateMonitoredItems service call fails as a whole
        final MiloClientEndpoint endpoint = context.getEndpoint(resolve(MILO_CLIENT_ITEM_1), MiloClientEndpoint.class);
        final MiloClientConnection connection = endpoint.createConnection();
        try {
            final OpcUaClient client = connection.getOpcUaClient();
            final UInteger subscriptionId = client.getSubscriptions().get(0).getSubscriptionId().orElseThrow();
            assertTrue(client.deleteSubscriptions(List.of(subscriptionId)).getResults()[0].isGood());
        } finally {
            endpoint.releaseConnection(connection);
        }

        // the connection is rebuilt: the new consumer reports its nodes without communication, unless the first values
        // of the new connection get there first, and ends with the current values, not a creation status
        items12Endpoint.expectedMinimumMessageCount(2);
        items12Endpoint.allMessages().predicate(exchange -> {
            final StatusCode status = exchange.getMessage().getBody(DataValue.class).getStatusCode();
            return status.isGood() || status.getValue() == StatusCodes.Bad_NoCommunication;
        });
        context.getRouteController().startRoute("items12");
        items12Endpoint.assertIsSatisfied();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertGoodValue("Foo").accept(lastExchange(items12Endpoint, NODE_ITEM_1).getMessage().getBody(DataValue.class));
            assertGoodValue("Bar").accept(lastExchange(items12Endpoint, NODE_ITEM_2).getMessage().getBody(DataValue.class));
        });

        producer1.sendBody("Baz");
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            final Exchange last = lastExchange(items12Endpoint);
            assertEquals(NODE_ITEM_1, last.getMessage().getHeader(MiloConstants.HEADER_NODE_ID, String.class));
            assertGoodValue("Baz").accept(last.getMessage().getBody(DataValue.class));
        });

        // the consumer that was running before keeps working on the rebuilt connection
        producer2.sendBody("Qux");
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertGoodValue("Qux").accept(lastExchange(item2Endpoint).getMessage()
                        .getBody(DataValue.class)));
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void subscriptionThatStopsPublishingIsRecreatedInTheSameSession() throws Exception {
        producer1.sendBody("Foo");

        item1Endpoint.expectedMinimumMessageCount(1);
        testBody(item1Endpoint.message(0), assertGoodValue("Foo"));
        context.getRouteController().startRoute("item1");
        item1Endpoint.assertIsSatisfied();

        final MiloClientEndpoint endpoint = context.getEndpoint(resolve(MILO_CLIENT_ITEM_1), MiloClientEndpoint.class);
        final MiloClientConnection connection = endpoint.createConnection();
        try {
            final OpcUaClient client = connection.getOpcUaClient();
            final OpcUaSubscription subscription = client.getSubscriptions().get(0);
            // shortens the watchdog of the subscription to a few publishing intervals
            subscription.setMaxKeepAliveCount(uint(1));
            subscription.setLifetimeCount(uint(3));
            subscription.modify();

            // the server deletes the subscription and answers the pending publish requests without one
            final UInteger subscriptionId = subscription.getSubscriptionId().orElseThrow();
            assertTrue(client.deleteSubscriptions(List.of(subscriptionId)).getResults()[0].isGood());

            producer1.sendBody("Bar");
            await().atMost(30, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertGoodValue("Bar").accept(lastExchange(item1Endpoint).getMessage()
                            .getBody(DataValue.class)));

            assertSame(client, connection.getOpcUaClient());
            assertNotEquals(subscriptionId, subscription.getSubscriptionId().orElseThrow());
        } finally {
            endpoint.releaseConnection(connection);
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void subscriptionThatTimesOutOnTheServerIsRecreatedInTheSameSession() throws Exception {
        producer1.sendBody("Foo");

        item1Endpoint.expectedMinimumMessageCount(1);
        testBody(item1Endpoint.message(0), assertGoodValue("Foo"));
        context.getRouteController().startRoute("item1");
        item1Endpoint.assertIsSatisfied();

        final MiloClientEndpoint endpoint = context.getEndpoint(resolve(MILO_CLIENT_ITEM_1), MiloClientEndpoint.class);
        final MiloClientConnection connection = endpoint.createConnection();
        try {
            final OpcUaClient client = connection.getOpcUaClient();
            final UInteger subscriptionId = client.getSubscriptions().get(0).getSubscriptionId().orElseThrow();

            timeOutSubscription(subscriptionId);

            producer1.sendBody("Bar");
            await().atMost(30, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertGoodValue("Bar").accept(lastExchange(item1Endpoint).getMessage()
                            .getBody(DataValue.class)));

            assertSame(client, connection.getOpcUaClient());
            assertNotEquals(subscriptionId, client.getSubscriptions().get(0).getSubscriptionId().orElseThrow());
        } finally {
            endpoint.releaseConnection(connection);
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void subscriptionThatASlowRouteHeldUpIsKept() throws Exception {
        producer1.sendBody("Foo");

        item1Endpoint.expectedMinimumMessageCount(1);
        testBody(item1Endpoint.message(0), assertGoodValue("Foo"));
        context.getRouteController().startRoute("item1");
        item1Endpoint.assertIsSatisfied();

        final MiloClientEndpoint endpoint = context.getEndpoint(resolve(MILO_CLIENT_ITEM_1), MiloClientEndpoint.class);
        final MiloClientConnection connection = endpoint.createConnection();
        try {
            final OpcUaClient client = connection.getOpcUaClient();
            final OpcUaSubscription subscription = client.getSubscriptions().get(0);
            // shortens the watchdog of the subscription to a few publishing intervals
            subscription.setMaxKeepAliveCount(uint(1));
            subscription.modify();
            final UInteger subscriptionId = subscription.getSubscriptionId().orElseThrow();
            // replaced when the server confirms the subscription
            final OpcUaSubscription.ServerState stateBefore = subscription.getServerState().orElseThrow();

            // the client sends no publish request while the route processes the value, so the watchdog elapses
            producer1.sendBody(SLOW_VALUE);
            await().atMost(20, TimeUnit.SECONDS).until(() -> countValue(item1Endpoint, SLOW_VALUE) >= 1);

            // a recreated subscription would deliver the current value again
            await().during(3, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS)
                    .until(() -> countValue(item1Endpoint, SLOW_VALUE) == 1);
            assertEquals(subscriptionId, subscription.getSubscriptionId().orElse(null));
            assertNotSame(stateBefore, subscription.getServerState().orElseThrow(), "the subscription was not checked");
            producer1.sendBody("Bar");
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertGoodValue("Bar").accept(lastExchange(item1Endpoint).getMessage()
                            .getBody(DataValue.class)));
        } finally {
            endpoint.releaseConnection(connection);
        }
    }

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void nodeThatCannotBeMonitoredAgainIsReportedAndCreatedOnceItIsBackWithoutRebuildingTheConnection() throws Exception {
        producer1.sendBody("Foo");
        producer2.sendBody("Bar");
        context.getRouteController().startRoute("item1");

        items12Endpoint.expectedMessageCount(2);
        context.getRouteController().startRoute("items12");
        items12Endpoint.assertIsSatisfied();

        final MiloClientEndpoint endpoint = context.getEndpoint(resolve(MILO_CLIENT_ITEM_1), MiloClientEndpoint.class);
        final MiloClientConnection connection = endpoint.createConnection();
        try {
            final OpcUaClient client = connection.getOpcUaClient();
            final OpcUaSubscription subscription = client.getSubscriptions().get(0);
            final UInteger subscriptionId = subscription.getSubscriptionId().orElseThrow();

            // the second node is removed on the server before the subscription times out there
            final UaNode node2 = getServer().getAddressSpaceManager().getManagedNode(ExpandedNodeId.parse(NODE_ITEM_2))
                    .orElseThrow();
            node2.delete();
            timeOutSubscription(subscriptionId);

            // the subscription is recreated in the same session, with the monitored items of the first node only
            await().atMost(30, TimeUnit.SECONDS)
                    .until(() -> subscription.getSubscriptionId().filter(id -> !id.equals(subscriptionId)).isPresent());
            assertSame(client, connection.getOpcUaClient());
            // the items are added one by one before they are created, so the count alone passes 2 on the way to 3
            await().atMost(10, TimeUnit.SECONDS).until(() -> subscription.getMonitoredItems().size() == 2
                    && subscription.isMonitoredItemsSynchronized());

            // the consumer of the second node is told that it is gone, the consumers of the first node keep receiving
            await().atMost(10, TimeUnit.SECONDS).until(() -> items12Endpoint.getReceivedExchanges().stream()
                    .anyMatch(exchange -> NODE_ITEM_2.equals(exchange.getMessage().getHeader(MiloConstants.HEADER_NODE_ID))
                            && new StatusCode(StatusCodes.Bad_NodeIdUnknown)
                                    .equals(exchange.getMessage().getBody(DataValue.class).getStatusCode())));
            producer1.sendBody("Baz");
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertGoodValue("Baz").accept(lastExchange(item1Endpoint).getMessage()
                            .getBody(DataValue.class)));
            assertSame(client, connection.getOpcUaClient());

            // the node is added again on the server: its item is created again and delivers its current value
            node2.getNodeManager().addNode(node2);
            await().atMost(30, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertGoodValue("Bar").accept(lastExchange(items12Endpoint, NODE_ITEM_2).getMessage()
                            .getBody(DataValue.class)));
            assertSame(client, connection.getOpcUaClient());
        } finally {
            endpoint.releaseConnection(connection);
        }
    }

    private OpcUaServer getServer() {
        return context.getComponent("milo-server", MiloServerComponent.class).getServer();
    }

    /**
     * The server reports the lifetime of the subscription as expired and removes it
     */
    private void timeOutSubscription(final UInteger subscriptionId) {
        for (final Session session : getServer().getSessionManager().getAllSessions()) {
            final var subscriptions = session.getSubscriptionManager();
            final var subscription = subscriptions.getSubscription(subscriptionId);
            if (subscription != null) {
                subscriptions.sendStatusChangeNotification(subscription, new StatusCode(StatusCodes.Bad_Timeout));
                subscriptions.removeSubscription(subscriptionId);
            }
        }
    }

    private static long countValue(final MockEndpoint endpoint, final Object value) {
        return endpoint.getReceivedExchanges().stream()
                .filter(exchange -> value.equals(exchange.getMessage().getBody(DataValue.class).getValue().getValue()))
                .count();
    }

    private static Exchange lastExchange(final MockEndpoint endpoint) {
        final List<Exchange> exchanges = endpoint.getReceivedExchanges();
        return exchanges.get(exchanges.size() - 1);
    }

    private static Exchange lastExchange(final MockEndpoint endpoint, final String nodeId) {
        final List<Exchange> exchanges = endpoint.getReceivedExchanges();
        for (int i = exchanges.size() - 1; i >= 0; i--) {
            if (nodeId.equals(exchanges.get(i).getMessage().getHeader(MiloConstants.HEADER_NODE_ID, String.class))) {
                return exchanges.get(i);
            }
        }
        throw new AssertionError("No exchange of node " + nodeId);
    }

    private static Consumer<DataValue> assertStatus(final long statusCode) {
        return value -> assertEquals(new StatusCode(statusCode), value.getStatusCode());
    }
}
