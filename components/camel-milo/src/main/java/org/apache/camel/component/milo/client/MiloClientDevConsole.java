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
package org.apache.camel.component.milo.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Route;
import org.apache.camel.component.milo.client.MiloClientConnection.MonitoringState;
import org.apache.camel.component.milo.client.MiloClientConnection.RefusedNode;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.json.JsonRecordSupport;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;

/**
 * The connections of the OPC UA client consumers, with their refused monitored items. It reads the state of the
 * connections without waiting for a service call in progress.
 */
@DevConsole(name = "milo-client", displayName = "OPC UA Client",
            description = "OPC UA client connections and their refused monitored items")
public class MiloClientDevConsole extends AbstractDevConsole {

    public record RefusedNodeEntry(
            @Metadata(description = "The node ID") String nodeId,
            @Metadata(description = "The status of the latest attempt to create the monitored item of the node: the refusal, or the failure of the attempt") String status,
            @Metadata(description = "Why the client refused the node without asking the server (only present when it did)") String reason) {
    }

    public record ConnectionEntry(
            @Metadata(description = "The OPC UA server endpoint, with its password masked") String endpointUri,
            @Metadata(description = "The client id of the connection (only present when one is set)") String clientId,
            @Metadata(description = "The ids of the routes whose consumers use the connection") List<String> routeIds,
            @Metadata(description = "Whether the session with the server is active") boolean sessionActive,
            @Metadata(description = "The number of nodes that have a monitored item on the server") int monitoredNodes,
            @Metadata(description = "The nodes whose monitored item was refused, which are created again every reconnectTimeout milliseconds") List<RefusedNodeEntry> refusedNodes) {
    }

    public record Response(@Metadata(description = "The connections of the consumers") List<ConnectionEntry> connections) {
    }

    public MiloClientDevConsole() {
        super("camel", "milo-client", "OPC UA Client",
              "OPC UA client connections and their refused monitored items");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();
        for (ConnectionEntry connection : connections()) {
            sb.append(String.format("%n    Endpoint: %s", connection.endpointUri()));
            if (connection.clientId() != null) {
                sb.append(String.format("%n    Client Id: %s", connection.clientId()));
            }
            sb.append(String.format("%n    Routes: %s", String.join(", ", connection.routeIds())));
            sb.append(String.format("%n    Session Active: %b", connection.sessionActive()));
            sb.append(String.format("%n    Monitored Nodes: %d", connection.monitoredNodes()));
            sb.append(String.format("%n    Refused Nodes: %d", connection.refusedNodes().size()));
            for (RefusedNodeEntry node : connection.refusedNodes()) {
                sb.append(String.format("%n        %s: %s", node.nodeId(), node.status()));
                if (node.reason() != null) {
                    sb.append(String.format(" (%s)", node.reason()));
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        Response response = new Response(connections());
        return JsonRecordSupport.toJsonObject(response);
    }

    /**
     * The connections of the started consumers, each with the routes that use it, in the order of the routes
     */
    private List<ConnectionEntry> connections() {
        Map<MiloClientConnection, List<String>> routeIds = new LinkedHashMap<>();
        for (Route route : getCamelContext().getRoutes()) {
            if (route.getConsumer() instanceof MiloClientConsumer consumer && consumer.isStarted()) {
                routeIds.computeIfAbsent(consumer.getConnection(), connection -> new ArrayList<>())
                        .add(route.getRouteId());
            }
        }

        List<ConnectionEntry> connections = new ArrayList<>();
        for (Map.Entry<MiloClientConnection, List<String>> entry : routeIds.entrySet()) {
            MonitoringState state = entry.getKey().getMonitoringState();
            if (state == null) {
                // closed while the consumers stop
                continue;
            }
            List<RefusedNodeEntry> refusedNodes = new ArrayList<>();
            for (RefusedNode node : state.refusedNodes()) {
                refusedNodes.add(new RefusedNodeEntry(node.nodeId(), statusName(node.status()), node.reason()));
            }
            MiloClientConfiguration configuration = entry.getKey().getConfiguration();
            connections.add(new ConnectionEntry(
                    URISupport.sanitizeUri(configuration.getEndpointUri()), configuration.getClientId(), entry.getValue(),
                    state.sessionActive(), state.monitoredNodes(), refusedNodes));
        }
        return connections;
    }

    /**
     * The symbolic name of a status code, or its hexadecimal value if it has none
     */
    private static String statusName(StatusCode status) {
        return StatusCodes.lookup(status.getValue()).map(nameAndDescription -> nameAndDescription[0])
                .orElseGet(() -> String.format("0x%08X", status.getValue()));
    }
}
