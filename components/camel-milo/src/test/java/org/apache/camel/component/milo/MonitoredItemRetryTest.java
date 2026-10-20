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

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milo.client.MiloClientConnection;
import org.apache.camel.component.milo.client.MiloClientEndpoint;
import org.apache.camel.component.milo.server.MiloServerComponent;
import org.apache.camel.component.milo.server.internal.CamelNamespace;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A node that the server refuses to monitor is reported once and created again on the same connection, every 10
 * seconds, until the server has it or its consumer stops
 */
public class MonitoredItemRetryTest extends AbstractMiloServerTest {

    private static final String DIRECT_START_1 = "direct:start1";

    private static final String MILO_SERVER_ITEM_1 = "milo-server:myitem1";

    // a namespace that the server does not have until a test adds it
    private static final String LATE_NAMESPACE_URI = "urn:org:apache:camel:late";

    // the client endpoints only differ in the node, so they share one connection
    private static final String MILO_CLIENT = "milo-client:opc.tcp://foo:bar@localhost:@@port@@"
                                              + "?allowedSecurityPolicies=None&overrideHost=true"
                                              + "&samplingInterval=50&requestedPublishingInterval=100&node=";

    private static final String MILO_CLIENT_ITEM_1
            = MILO_CLIENT + NodeIds.nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "myitem1");

    private static final String MILO_CLIENT_LATE_ITEM
            = MILO_CLIENT + NodeIds.nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "lateItem");

    private static final String MILO_CLIENT_LATE_NAMESPACE_ITEM
            = MILO_CLIENT + NodeIds.nodeValue(LATE_NAMESPACE_URI, "lateNamespaceItem");

    private static final String MILO_CLIENT_STOPPED_ITEM
            = MILO_CLIENT + NodeIds.nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "stoppedItem");

    @EndpointInject("mock:item1")
    protected MockEndpoint item1Endpoint;

    @EndpointInject("mock:lateItem")
    protected MockEndpoint lateItemEndpoint;

    @EndpointInject("mock:lateNamespaceItem")
    protected MockEndpoint lateNamespaceItemEndpoint;

    @EndpointInject("mock:stoppedItem")
    protected MockEndpoint stoppedItemEndpoint;

    @Produce(DIRECT_START_1)
    protected ProducerTemplate producer1;

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(DIRECT_START_1).to(MILO_SERVER_ITEM_1);

                // the consumers deliver the current values while they start, so the tests start these routes
                from(resolve(MILO_CLIENT_ITEM_1)).routeId("item1").autoStartup(false).to("mock:item1");
                from(resolve(MILO_CLIENT_LATE_ITEM)).routeId("lateItem").autoStartup(false).to("mock:lateItem");
                from(resolve(MILO_CLIENT_LATE_NAMESPACE_ITEM)).routeId("lateNamespaceItem").autoStartup(false)
                        .to("mock:lateNamespaceItem");
                from(resolve(MILO_CLIENT_STOPPED_ITEM)).routeId("stoppedItem").autoStartup(false)
                        .to("mock:stoppedItem");
            }
        };
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void nodeCreatedOnTheServerLaterDeliversItsValue() throws Exception {
        lateItemEndpoint.expectedMessageCount(1);
        testBody(lateItemEndpoint.message(0), assertStatus(StatusCodes.Bad_NodeIdUnknown));
        context.getRouteController().startRoute("lateItem");
        lateItemEndpoint.assertIsSatisfied();

        final MiloClientEndpoint endpoint = context.getEndpoint(resolve(MILO_CLIENT_LATE_ITEM), MiloClientEndpoint.class);
        final MiloClientConnection connection = endpoint.createConnection();
        try {
            final OpcUaClient client = connection.getOpcUaClient();

            // the node is created on the server: its first value follows the refusal, without another bad status
            lateItemEndpoint.reset();
            lateItemEndpoint.expectedMessageCount(1);
            lateItemEndpoint.setResultWaitTime(30_000);
            lateItemEndpoint.setAssertPeriod(2_000);
            testBody(lateItemEndpoint.message(0), assertGoodValue("Foo"));
            template.sendBody("milo-server:lateItem", "Foo");
            lateItemEndpoint.assertIsSatisfied();

            // on the same connection
            assertSame(client, connection.getOpcUaClient());
        } finally {
            endpoint.releaseConnection(connection);
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void nodeOfANamespaceAddedLaterDeliversItsValue() throws Exception {
        lateNamespaceItemEndpoint.expectedMessageCount(1);
        testBody(lateNamespaceItemEndpoint.message(0), assertStatus(StatusCodes.Bad_NodeIdUnknown));
        context.getRouteController().startRoute("lateNamespaceItem");
        lateNamespaceItemEndpoint.assertIsSatisfied();

        final MiloClientEndpoint endpoint
                = context.getEndpoint(resolve(MILO_CLIENT_LATE_NAMESPACE_ITEM), MiloClientEndpoint.class);
        final MiloClientConnection connection = endpoint.createConnection();
        // the server adds the namespace URI to its namespace table and the node to the namespace
        final CamelNamespace namespace = new CamelNamespace(LATE_NAMESPACE_URI, getServer().getServer());
        try {
            final OpcUaClient client = connection.getOpcUaClient();

            lateNamespaceItemEndpoint.reset();
            lateNamespaceItemEndpoint.expectedMessageCount(1);
            lateNamespaceItemEndpoint.setResultWaitTime(30_000);
            lateNamespaceItemEndpoint.setAssertPeriod(2_000);
            testBody(lateNamespaceItemEndpoint.message(0), assertGoodValue("Foo"));
            namespace.startup();
            namespace.getOrAddItem("lateNamespaceItem").update("Foo");
            lateNamespaceItemEndpoint.assertIsSatisfied();

            assertSame(client, connection.getOpcUaClient());
        } finally {
            endpoint.releaseConnection(connection);
            namespace.shutdown();
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void stoppedConsumerIsNotCreatedAgain() throws Exception {
        // the consumer of another node keeps the connection open
        producer1.sendBody("Foo");
        item1Endpoint.expectedMessageCount(1);
        testBody(item1Endpoint.message(0), assertGoodValue("Foo"));
        context.getRouteController().startRoute("item1");
        item1Endpoint.assertIsSatisfied();

        stoppedItemEndpoint.expectedMessageCount(1);
        testBody(stoppedItemEndpoint.message(0), assertStatus(StatusCodes.Bad_NodeIdUnknown));
        context.getRouteController().startRoute("stoppedItem");
        stoppedItemEndpoint.assertIsSatisfied();

        final MiloClientEndpoint endpoint = context.getEndpoint(resolve(MILO_CLIENT_ITEM_1), MiloClientEndpoint.class);
        final MiloClientConnection connection = endpoint.createConnection();
        try {
            final OpcUaClient client = connection.getOpcUaClient();

            context.getRouteController().stopRoute("stoppedItem");
            stoppedItemEndpoint.reset();
            stoppedItemEndpoint.expectedMessageCount(0);
            template.sendBody("milo-server:stoppedItem", "Foo");

            // the next attempt would have created it by now
            await().during(15, TimeUnit.SECONDS).atMost(20, TimeUnit.SECONDS)
                    .until(() -> client.getSubscriptions().get(0).getMonitoredItems().stream()
                            .noneMatch(item -> "stoppedItem"
                                    .equals(item.getReadValueId().getNodeId().getIdentifier())));
            stoppedItemEndpoint.assertIsSatisfied();
            assertSame(client, connection.getOpcUaClient());

            // the other consumer keeps receiving
            item1Endpoint.reset();
            item1Endpoint.expectedMessageCount(1);
            testBody(item1Endpoint.message(0), assertGoodValue("Bar"));
            producer1.sendBody("Bar");
            item1Endpoint.assertIsSatisfied();
        } finally {
            endpoint.releaseConnection(connection);
        }
    }

    private MiloServerComponent getServer() {
        return context.getComponent("milo-server", MiloServerComponent.class);
    }

    private static Consumer<DataValue> assertStatus(final long statusCode) {
        return value -> assertEquals(new StatusCode(statusCode), value.getStatusCode());
    }
}
