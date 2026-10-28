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
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milo.browse.MiloBrowseEndpoint;
import org.apache.camel.component.milo.client.MiloClientConnection;
import org.apache.camel.component.milo.client.MiloClientEndpoint;
import org.apache.camel.component.milo.server.MiloServerComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testing producer requests with a node ID whose namespace URI is not in the namespace table of the server, which are
 * answered like requests for a node that the server does not have
 */
public class ProducerUnknownNamespaceTest extends AbstractMiloServerTest {

    private static final String NODE = "nsu=" + MiloServerComponent.DEFAULT_NAMESPACE_URI + ";s=node1";

    // namespace URIs that are not in the namespace table of the server
    private static final String UNKNOWN_NAMESPACE_NODE = "nsu=urn:not:there;s=node1";

    private static final String UNKNOWN_NAMESPACE_METHOD = "nsu=urn:not:there;s=method1";

    private static final String ROOT_FOLDER = "ns=0;i=84";

    private static final String SERVER = "opc.tcp://foo:bar@localhost:@@port@@"
                                         + "?allowedSecurityPolicies=None&overrideHost=true";

    // the client endpoints only differ in the node and the producer options, so they share one connection
    private static final String MILO_CLIENT = "milo-client:" + SERVER + "&requestedPublishingInterval=100";

    private static final String MILO_CLIENT_NODE = MILO_CLIENT + "&node=RAW(" + NODE + ")&samplingInterval=50";

    private static final String MILO_CLIENT_AWAIT = MILO_CLIENT + "&defaultAwaitWrites=true";

    private static final String MILO_CLIENT_WRITE = MILO_CLIENT_AWAIT + "&node=RAW(" + UNKNOWN_NAMESPACE_NODE + ")";

    private static final String MILO_CLIENT_CALL
            = MILO_CLIENT_AWAIT + "&node=RAW(" + NODE + ")&method=RAW(" + UNKNOWN_NAMESPACE_METHOD + ")";

    private static final String MILO_CLIENT_CALL_UNKNOWN_OBJECT
            = MILO_CLIENT_AWAIT + "&node=RAW(" + UNKNOWN_NAMESPACE_NODE + ")&method=RAW(" + UNKNOWN_NAMESPACE_METHOD + ")";

    private static final String MILO_BROWSE = "milo-browse:" + SERVER;

    @EndpointInject("mock:consumer")
    protected MockEndpoint consumerEndpoint;

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the consumer delivers the current value while it starts, so each test starts the route
                from(resolve(MILO_CLIENT_NODE)).routeId("consumer").autoStartup(false).to("mock:consumer");
            }
        };
    }

    @Test
    void writeIsAnsweredWithBadNodeIdUnknown() throws Exception {
        final OpcUaClient client = startConsumer();

        final Exchange exchange = template.send(resolve(MILO_CLIENT_WRITE), e -> e.getMessage().setBody("Baz"));

        assertNull(exchange.getException());
        assertEquals(new StatusCode(StatusCodes.Bad_NodeIdUnknown), exchange.getMessage().getBody(StatusCode.class));
        assertConnectionKept(client);
    }

    @Test
    void readAnswersOnlyThatNodeWithBadNodeIdUnknown() throws Exception {
        final OpcUaClient client = startConsumer();

        final Exchange exchange = template.send(resolve(MILO_CLIENT_AWAIT),
                e -> e.getMessage().setHeader(MiloConstants.HEADER_NODE_IDS, List.of(NODE, UNKNOWN_NAMESPACE_NODE)));

        assertNull(exchange.getException());
        final List<?> values = exchange.getMessage().getBody(List.class);
        assertNotNull(values);
        assertEquals(2, values.size());
        assertGoodValue("Foo").accept((DataValue) values.get(0));
        assertEquals(new StatusCode(StatusCodes.Bad_NodeIdUnknown), ((DataValue) values.get(1)).getStatusCode());

        // a read of only such a node sends no request: the server would answer an empty one with Bad_NothingToDo
        final Exchange onlyUnknown = template.send(resolve(MILO_CLIENT_AWAIT),
                e -> e.getMessage().setHeader(MiloConstants.HEADER_NODE_IDS, List.of(UNKNOWN_NAMESPACE_NODE)));

        assertNull(onlyUnknown.getException());
        assertEquals(new StatusCode(StatusCodes.Bad_NodeIdUnknown),
                ((DataValue) onlyUnknown.getMessage().getBody(List.class).get(0)).getStatusCode());
        assertConnectionKept(client);
    }

    @Test
    void methodCallKeepsTheConnection() throws Exception {
        final OpcUaClient client = startConsumer();

        // an unknown method and an unknown object: the result is not checked, because the call guard drops every call
        // result (CAMEL-25694)
        assertNull(template.send(resolve(MILO_CLIENT_CALL), e -> e.getMessage().setBody(null)).getException());
        assertNull(template.send(resolve(MILO_CLIENT_CALL_UNKNOWN_OBJECT), e -> e.getMessage().setBody(null))
                .getException());
        assertConnectionKept(client);
    }

    @Test
    void browseAnswersOnlyThatNodeWithBadNodeIdUnknown() throws Exception {
        // the browse component has a connection of its own
        final MiloBrowseEndpoint endpoint = context.getEndpoint(resolve(MILO_BROWSE), MiloBrowseEndpoint.class);
        final MiloClientConnection connection = endpoint.createConnection();
        try {
            final OpcUaClient client = connection.getOpcUaClient();
            assertNotNull(client);

            final Exchange exchange = template.send(endpoint, e -> e.getMessage()
                    .setHeader(MiloConstants.HEADER_NODE_IDS, List.of(ROOT_FOLDER, UNKNOWN_NAMESPACE_NODE)));

            assertNull(exchange.getException());
            final Map<?, ?> results = exchange.getMessage().getBody(Map.class);
            assertEquals(2, results.size());
            final BrowseResult unknown = (BrowseResult) results.get(ExpandedNodeId.parse(UNKNOWN_NAMESPACE_NODE));
            assertEquals(new StatusCode(StatusCodes.Bad_NodeIdUnknown), unknown.getStatusCode());
            assertEquals(0, unknown.getReferences().length);
            final BrowseResult rootFolder = (BrowseResult) results.get(ExpandedNodeId.parse(ROOT_FOLDER));
            assertTrue(rootFolder.getStatusCode().isGood());
            assertTrue(rootFolder.getReferences().length > 0);

            // a browse of only such a node sends no request: the server would answer an empty one with Bad_NothingToDo
            final Exchange onlyUnknown = template.send(endpoint,
                    e -> e.getMessage().setHeader(MiloConstants.HEADER_NODE_IDS, List.of(UNKNOWN_NAMESPACE_NODE)));

            assertNull(onlyUnknown.getException());
            assertEquals(new StatusCode(StatusCodes.Bad_NodeIdUnknown), ((BrowseResult) onlyUnknown.getMessage()
                    .getBody(Map.class).get(ExpandedNodeId.parse(UNKNOWN_NAMESPACE_NODE))).getStatusCode());

            // the connection was not rebuilt
            assertSame(client, connection.getOpcUaClient());
        } finally {
            endpoint.releaseConnection(connection);
        }
    }

    private OpcUaClient startConsumer() throws Exception {
        template.sendBody("milo-server:node1", "Foo");

        consumerEndpoint.expectedMessageCount(1);
        testBody(consumerEndpoint.message(0), assertGoodValue("Foo"));
        context.getRouteController().startRoute("consumer");
        consumerEndpoint.assertIsSatisfied();

        final OpcUaClient client = currentClient();
        assertNotNull(client);
        return client;
    }

    private void assertConnectionKept(final OpcUaClient client) throws Exception {
        // the connection was not rebuilt and the consumer that shares it receives the next value
        assertSame(client, currentClient());

        consumerEndpoint.reset();
        consumerEndpoint.expectedMessageCount(1);
        testBody(consumerEndpoint.message(0), assertGoodValue("Bar"));
        template.sendBody("milo-server:node1", "Bar");
        consumerEndpoint.assertIsSatisfied();
    }

    private OpcUaClient currentClient() {
        final MiloClientEndpoint endpoint = context.getEndpoint(resolve(MILO_CLIENT_NODE), MiloClientEndpoint.class);
        final MiloClientConnection connection = endpoint.createConnection();
        try {
            return connection.getOpcUaClient();
        } finally {
            endpoint.releaseConnection(connection);
        }
    }
}
