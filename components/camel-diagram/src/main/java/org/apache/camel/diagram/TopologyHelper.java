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
package org.apache.camel.diagram;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.diagram.TopologyLayoutEngine.TopologyEdgeInfo;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyNodeInfo;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * Helper for parsing topology JSON from the route-topology dev console and enriching with per-route metrics from the
 * route-structure dev console.
 */
public final class TopologyHelper {

    private TopologyHelper() {
    }

    public static List<TopologyNodeInfo> parseNodes(JsonObject jo) {
        List<TopologyNodeInfo> nodes = new ArrayList<>();
        JsonArray arr = jo.getJsonArray("nodes");
        if (arr == null) {
            return nodes;
        }
        for (int i = 0; i < arr.size(); i++) {
            JsonObject no = arr.getJsonObject(i);
            TopologyNodeInfo node = new TopologyNodeInfo();
            node.routeId = no.getString("routeId");
            node.description = no.getString("description");
            node.from = no.getString("from");
            node.fromScheme = no.getString("fromScheme");
            node.nodeType = no.getStringOrDefault("nodeType", "route");
            node.exchangesTotal = no.getLongOrDefault("exchangesTotal", 0);
            node.exchangesFailed = no.getLongOrDefault("exchangesFailed", 0);
            nodes.add(node);
        }
        return nodes;
    }

    public static List<TopologyEdgeInfo> parseEdges(JsonObject jo) {
        List<TopologyEdgeInfo> edges = new ArrayList<>();
        JsonArray arr = jo.getJsonArray("edges");
        if (arr == null) {
            return edges;
        }
        for (int i = 0; i < arr.size(); i++) {
            JsonObject eo = arr.getJsonObject(i);
            TopologyEdgeInfo edge = new TopologyEdgeInfo();
            edge.fromRouteId = eo.getString("fromRouteId");
            edge.toRouteId = eo.getString("toRouteId");
            edge.endpoint = eo.getString("endpoint");
            edge.connectionType = eo.getStringOrDefault("connectionType", "internal");
            edges.add(edge);
        }
        return edges;
    }

    /**
     * Parses external endpoints from the JSON and adds them as nodes and edges to the existing lists. External
     * endpoints with direction "in" (consumers) become nodes connected TO their route. External endpoints with
     * direction "out" (producers) become nodes connected FROM their route.
     */
    public static void addExternalEndpoints(List<TopologyNodeInfo> nodes, List<TopologyEdgeInfo> edges, JsonObject jo) {
        JsonArray arr = jo.getJsonArray("externalEndpoints");
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.size(); i++) {
            JsonObject eo = arr.getJsonObject(i);
            String id = eo.getString("id");
            String uri = eo.getString("uri");
            String scheme = eo.getString("scheme");
            String direction = eo.getString("direction");
            String routeId = eo.getString("routeId");

            // Create a node for this external endpoint
            TopologyNodeInfo node = new TopologyNodeInfo();
            node.routeId = id;
            node.from = uri;
            node.fromScheme = scheme;
            node.nodeType = "in".equals(direction) ? "external-in" : "external-out";

            // Extract context-path from URI for use as description
            int colonIdx = uri.indexOf(':');
            node.description = colonIdx > 0 ? uri.substring(colonIdx + 1) : uri;

            node.exchangesTotal = eo.getLongOrDefault("exchangesTotal", 0);
            node.exchangesFailed = eo.getLongOrDefault("exchangesFailed", 0);

            nodes.add(node);

            // Create an edge connecting this external endpoint to/from its route
            TopologyEdgeInfo edge = new TopologyEdgeInfo();
            edge.endpoint = uri;
            edge.connectionType = "external";
            if ("in".equals(direction)) {
                edge.fromRouteId = id;
                edge.toRouteId = routeId;
            } else {
                edge.fromRouteId = routeId;
                edge.toRouteId = id;
            }
            edges.add(edge);
        }
    }

    public static void enrichWithMetrics(List<TopologyNodeInfo> nodes, JsonObject routeStructureJson) {
        if (routeStructureJson == null) {
            return;
        }
        JsonArray routes = routeStructureJson.getJsonArray("routes");
        if (routes == null) {
            return;
        }
        for (int i = 0; i < routes.size(); i++) {
            JsonObject ro = routes.getJsonObject(i);
            String routeId = ro.getString("routeId");
            JsonObject stats = ro.getJsonObject("statistics");
            if (routeId != null && stats != null) {
                for (TopologyNodeInfo node : nodes) {
                    if (routeId.equals(node.routeId)) {
                        node.exchangesTotal = stats.getLongOrDefault("exchangesTotal", 0);
                        node.exchangesFailed = stats.getLongOrDefault("exchangesFailed", 0);
                        break;
                    }
                }
            }
        }
    }

}
