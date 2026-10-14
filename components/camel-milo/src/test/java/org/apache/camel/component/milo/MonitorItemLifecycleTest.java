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
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testing the monitored items of consumers sharing a connection, when a consumer is restarted, its node does not exist
 * or the server rejects the CreateMonitoredItems service call
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
            = MILO_CLIENT + NodeIds.nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "unknownItem")
              + "&omitNullValues=false";

    private static final String MILO_CLIENT_UNKNOWN_ITEM_WRITE_BACK
            = MILO_CLIENT + NodeIds.nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "unknownItemWriteBack")
              + "&omitNullValues=false";

    private static final String MILO_CLIENT_ITEMS_1_2
            = MILO_CLIENT_BASE + "&omitNullValues=false&nodes=RAW(" + NODE_ITEM_1 + "," + NODE_ITEM_2 + ")";

    private static final String MOCK_ITEM_1 = "mock:item1";

    private static final String MOCK_ITEM_2 = "mock:item2";

    private static final String MOCK_UNKNOWN_ITEM = "mock:unknownItem";

    private static final String MOCK_WRITE_BACK = "mock:writeBack";

    private static final String MOCK_ITEMS_1_2 = "mock:items12";

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

                from(resolve(MILO_CLIENT_ITEM_1)).routeId("item1").to(MOCK_ITEM_1);
                from(resolve(MILO_CLIENT_UNKNOWN_ITEM)).routeId("unknownItem").to(MOCK_UNKNOWN_ITEM);

                // the consumers deliver the current values while they start, so the tests start these routes
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

    @Test
    void restartedConsumerReceivesEachValueOnce() throws Exception {
        item1Endpoint.expectedMessageCount(1);
        testBody(item1Endpoint.message(0), assertGoodValue("Foo"));
        producer1.sendBody("Foo");
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
        item1Endpoint.expectedMessageCount(1);
        testBody(item1Endpoint.message(0), assertGoodValue("Foo"));
        producer1.sendBody("Foo");
        item1Endpoint.assertIsSatisfied();
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

        // the connection is rebuilt and the new consumer receives the current values, not a bad status
        items12Endpoint.expectedMessageCount(2);
        items12Endpoint.allMessages().predicate(exchange -> exchange.getMessage().getBody(DataValue.class).getStatusCode()
                .isGood());
        context.getRouteController().startRoute("items12");
        items12Endpoint.assertIsSatisfied();

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

    private static Exchange lastExchange(final MockEndpoint endpoint) {
        final List<Exchange> exchanges = endpoint.getReceivedExchanges();
        return exchanges.get(exchanges.size() - 1);
    }

    private static Consumer<DataValue> assertStatus(final long statusCode) {
        return value -> assertEquals(new StatusCode(statusCode), value.getStatusCode());
    }
}
