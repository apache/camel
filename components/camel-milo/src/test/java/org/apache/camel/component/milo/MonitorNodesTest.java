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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milo.client.MiloClientConnection;
import org.apache.camel.component.milo.client.MiloClientEndpoint;
import org.apache.camel.component.milo.server.MiloServerComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testing one consumer that monitors several nodes with the nodes option
 */
public class MonitorNodesTest extends AbstractMiloServerTest {

    private static final String NODE_1 = "nsu=" + MiloServerComponent.DEFAULT_NAMESPACE_URI + ";s=node1";

    private static final String NODE_2 = "nsu=" + MiloServerComponent.DEFAULT_NAMESPACE_URI + ";s=node2";

    private static final String UNKNOWN_NODE = "nsu=" + MiloServerComponent.DEFAULT_NAMESPACE_URI + ";s=unknownNode";

    // a namespace URI that is not in the namespace table of the server
    private static final String UNKNOWN_NAMESPACE_NODE = "nsu=urn:not:there;s=node1";

    // the same node as NODE_1 with the default server index spelled out
    private static final String NODE_1_WITH_SERVER_INDEX = "svr=0;" + NODE_1;

    // the client endpoints only differ in the node options, so they share one connection
    private static final String MILO_CLIENT = "milo-client:opc.tcp://foo:bar@localhost:@@port@@"
                                              + "?allowedSecurityPolicies=None&overrideHost=true"
                                              + "&samplingInterval=50&requestedPublishingInterval=100";

    private static final String MILO_CLIENT_NODES = MILO_CLIENT + "&nodes=RAW(" + NODE_1 + "," + NODE_2 + ")";

    private static final String MILO_CLIENT_UNKNOWN_NODE
            = MILO_CLIENT + "&nodes=RAW(" + NODE_1 + "," + UNKNOWN_NODE + "," + NODE_2 + ")";

    private static final String MILO_CLIENT_UNKNOWN_NAMESPACE
            = MILO_CLIENT + "&nodes=RAW(" + NODE_1 + "," + UNKNOWN_NAMESPACE_NODE + "," + NODE_2 + ")";

    private static final String MILO_CLIENT_DUPLICATES
            = MILO_CLIENT + "&nodes=RAW( " + NODE_1 + " ,, " + NODE_2 + " ," + NODE_1 + ")";

    private static final String MILO_CLIENT_NODE = MILO_CLIENT + "&node=RAW(" + NODE_1 + ")";

    private static final String MILO_CLIENT_SAME_NODE
            = MILO_CLIENT + "&nodes=RAW(" + NODE_1 + "," + NODE_1_WITH_SERVER_INDEX + ")";

    @EndpointInject("mock:nodes")
    protected MockEndpoint nodesEndpoint;

    @EndpointInject("mock:unknownNode")
    protected MockEndpoint unknownNodeEndpoint;

    @EndpointInject("mock:unknownNamespace")
    protected MockEndpoint unknownNamespaceEndpoint;

    @EndpointInject("mock:duplicates")
    protected MockEndpoint duplicatesEndpoint;

    @EndpointInject("mock:node")
    protected MockEndpoint nodeEndpoint;

    @EndpointInject("mock:sameNode")
    protected MockEndpoint sameNodeEndpoint;

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the consumers deliver the current values while they start, so each test starts its route
                from(resolve(MILO_CLIENT_NODES)).routeId("nodes").autoStartup(false).to("mock:nodes");
                from(resolve(MILO_CLIENT_UNKNOWN_NODE)).routeId("unknownNode").autoStartup(false).to("mock:unknownNode");
                from(resolve(MILO_CLIENT_UNKNOWN_NAMESPACE)).routeId("unknownNamespace").autoStartup(false)
                        .to("mock:unknownNamespace");
                from(resolve(MILO_CLIENT_DUPLICATES)).routeId("duplicates").autoStartup(false).to("mock:duplicates");
                from(resolve(MILO_CLIENT_NODE)).routeId("node").autoStartup(false).to("mock:node");
                from(resolve(MILO_CLIENT_SAME_NODE)).routeId("sameNode").autoStartup(false).to("mock:sameNode");
            }
        };
    }

    @Test
    void consumerMonitorsSeveralNodes() throws Exception {
        writeServerValues();

        nodesEndpoint.expectedMessageCount(2);
        context.getRouteController().startRoute("nodes");
        nodesEndpoint.assertIsSatisfied();

        final Map<String, DataValue> values = valuesByNode(nodesEndpoint);
        assertEquals(Set.of(NODE_1, NODE_2), values.keySet());
        assertGoodValue("Foo").accept(values.get(NODE_1));
        assertGoodValue("Bar").accept(values.get(NODE_2));

        nodesEndpoint.reset();
        nodesEndpoint.expectedMessageCount(1);
        nodesEndpoint.expectedHeaderReceived(MiloConstants.HEADER_NODE_ID, NODE_2);
        testBody(nodesEndpoint.message(0), assertGoodValue("Baz"));
        template.sendBody("milo-server:node2", "Baz");
        nodesEndpoint.assertIsSatisfied();
    }

    @Test
    void unknownNodeReceivesBadStatusAndTheOtherNodesKeepWorking() throws Exception {
        writeServerValues();

        unknownNodeEndpoint.expectedMessageCount(3);
        context.getRouteController().startRoute("unknownNode");
        unknownNodeEndpoint.assertIsSatisfied();

        final Map<String, DataValue> values = valuesByNode(unknownNodeEndpoint);
        assertEquals(Set.of(NODE_1, UNKNOWN_NODE, NODE_2), values.keySet());
        assertEquals(new StatusCode(StatusCodes.Bad_NodeIdUnknown), values.get(UNKNOWN_NODE).getStatusCode());
        assertGoodValue("Foo").accept(values.get(NODE_1));
        assertGoodValue("Bar").accept(values.get(NODE_2));

        unknownNodeEndpoint.reset();
        unknownNodeEndpoint.expectedMessageCount(1);
        unknownNodeEndpoint.expectedHeaderReceived(MiloConstants.HEADER_NODE_ID, NODE_1);
        testBody(unknownNodeEndpoint.message(0), assertGoodValue("Baz"));
        template.sendBody("milo-server:node1", "Baz");
        unknownNodeEndpoint.assertIsSatisfied();
    }

    @Test
    void notGoodStatusWithNullValueIsDeliveredByDefault() throws Exception {
        // a null value with a Good status is omitted; a Bad or Uncertain status with a null value is delivered
        template.sendBody("milo-server:node1", new DataValue(Variant.NULL_VALUE, StatusCode.GOOD, null));
        template.sendBody("milo-server:node2", new DataValue(new StatusCode(StatusCodes.Uncertain_LastUsableValue)));

        unknownNodeEndpoint.expectedMessageCount(2);
        unknownNodeEndpoint.setAssertPeriod(1000);
        context.getRouteController().startRoute("unknownNode");
        unknownNodeEndpoint.assertIsSatisfied();

        final Map<String, DataValue> values = valuesByNode(unknownNodeEndpoint);
        assertEquals(Set.of(UNKNOWN_NODE, NODE_2), values.keySet());
        assertEquals(new StatusCode(StatusCodes.Bad_NodeIdUnknown), values.get(UNKNOWN_NODE).getStatusCode());
        assertEquals(new StatusCode(StatusCodes.Uncertain_LastUsableValue), values.get(NODE_2).getStatusCode());
        assertNull(values.get(NODE_2).getValue().getValue());

        unknownNodeEndpoint.reset();
        unknownNodeEndpoint.expectedMessageCount(1);
        unknownNodeEndpoint.expectedHeaderReceived(MiloConstants.HEADER_NODE_ID, NODE_1);
        testBody(unknownNodeEndpoint.message(0), assertGoodValue("Baz"));
        template.sendBody("milo-server:node1", "Baz");
        unknownNodeEndpoint.assertIsSatisfied();
    }

    @Test
    void unknownNamespaceUriReceivesBadStatusAndTheOtherNodesKeepWorking() throws Exception {
        writeServerValues();

        // the bad status is reported once, the connection stays up for the other nodes
        unknownNamespaceEndpoint.expectedMessageCount(3);
        unknownNamespaceEndpoint.setAssertPeriod(1000);
        context.getRouteController().startRoute("unknownNamespace");
        unknownNamespaceEndpoint.assertIsSatisfied();

        final Map<String, DataValue> values = valuesByNode(unknownNamespaceEndpoint);
        assertEquals(Set.of(NODE_1, UNKNOWN_NAMESPACE_NODE, NODE_2), values.keySet());
        assertEquals(new StatusCode(StatusCodes.Bad_NodeIdUnknown), values.get(UNKNOWN_NAMESPACE_NODE).getStatusCode());
        assertGoodValue("Foo").accept(values.get(NODE_1));
        assertGoodValue("Bar").accept(values.get(NODE_2));

        unknownNamespaceEndpoint.reset();
        unknownNamespaceEndpoint.expectedMessageCount(1);
        unknownNamespaceEndpoint.expectedHeaderReceived(MiloConstants.HEADER_NODE_ID, NODE_1);
        testBody(unknownNamespaceEndpoint.message(0), assertGoodValue("Baz"));
        template.sendBody("milo-server:node1", "Baz");
        unknownNamespaceEndpoint.assertIsSatisfied();
    }

    @Test
    void duplicateNodesAreMonitoredOnce() throws Exception {
        writeServerValues();

        // entries are trimmed and empty entries are skipped
        duplicatesEndpoint.expectedMessageCount(2);
        duplicatesEndpoint.setAssertPeriod(1000);
        context.getRouteController().startRoute("duplicates");
        duplicatesEndpoint.assertIsSatisfied();

        final Map<String, DataValue> values = valuesByNode(duplicatesEndpoint);
        assertEquals(Set.of(NODE_1, NODE_2), values.keySet());
        assertGoodValue("Foo").accept(values.get(NODE_1));
        assertGoodValue("Bar").accept(values.get(NODE_2));
    }

    @Test
    void spellingsOfOneNodeAreMonitoredOnce() throws Exception {
        // the entries are de-duplicated by the parsed node ID, not by their spelling
        assertEquals(ExpandedNodeId.parse("ns=0;i=2258"), ExpandedNodeId.parse("i=2258"));
        assertEquals(ExpandedNodeId.parse(NODE_1), ExpandedNodeId.parse(NODE_1_WITH_SERVER_INDEX));

        writeServerValues();

        // one exchange per value, the header carries the first spelling
        sameNodeEndpoint.expectedMessageCount(1);
        sameNodeEndpoint.setAssertPeriod(1000);
        sameNodeEndpoint.expectedHeaderReceived(MiloConstants.HEADER_NODE_ID, NODE_1);
        testBody(sameNodeEndpoint.message(0), assertGoodValue("Foo"));
        context.getRouteController().startRoute("sameNode");
        sameNodeEndpoint.assertIsSatisfied();

        // and one monitored item on the server
        final MiloClientEndpoint endpoint = context.getEndpoint(resolve(MILO_CLIENT_SAME_NODE), MiloClientEndpoint.class);
        final MiloClientConnection connection = endpoint.createConnection();
        try {
            assertEquals(1, connection.getOpcUaClient().getSubscriptions().get(0).getMonitoredItems().size());
        } finally {
            endpoint.releaseConnection(connection);
        }
    }

    @Test
    void nodeAndNodesTogetherAreRejected() {
        final Endpoint endpoint = context.getEndpoint(resolve(MILO_CLIENT_NODE + "&nodes=RAW(" + NODE_2 + ")"));

        assertThrows(IllegalArgumentException.class, () -> endpoint.createConsumer(exchange -> {
        }));
    }

    @Test
    void nodesWithoutNodeIdsAreRejected() throws Exception {
        final Consumer consumer = context.getEndpoint(resolve(MILO_CLIENT + "&nodes=RAW( , )")).createConsumer(exchange -> {
        });

        assertThrows(IllegalArgumentException.class, consumer::start);
    }

    @Test
    void invalidNodeIdIsNamed() throws Exception {
        final Consumer consumer = context.getEndpoint(resolve(MILO_CLIENT + "&nodes=RAW(" + NODE_1 + ",ns=2;x=foo)"))
                .createConsumer(exchange -> {
                });

        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, consumer::start);
        assertEquals("Invalid node ID: ns=2;x=foo", e.getMessage());
    }

    @Test
    void singleNodeConsumerSetsTheNodeHeader() throws Exception {
        writeServerValues();

        nodeEndpoint.expectedMessageCount(1);
        nodeEndpoint.expectedHeaderReceived(MiloConstants.HEADER_NODE_ID, NODE_1);
        testBody(nodeEndpoint.message(0), assertGoodValue("Foo"));
        context.getRouteController().startRoute("node");
        nodeEndpoint.assertIsSatisfied();
    }

    private void writeServerValues() {
        // the server creates the nodes when the server endpoints start
        template.sendBody("milo-server:node1", "Foo");
        template.sendBody("milo-server:node2", "Bar");
    }

    private static Map<String, DataValue> valuesByNode(final MockEndpoint endpoint) {
        final Map<String, DataValue> values = new HashMap<>();
        for (final Exchange exchange : endpoint.getReceivedExchanges()) {
            values.put(exchange.getMessage().getHeader(MiloConstants.HEADER_NODE_ID, String.class),
                    exchange.getMessage().getBody(DataValue.class));
        }
        return values;
    }
}
