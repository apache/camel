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
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutNode;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutResult;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyNodeInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopologyDiagramTest {

    @Test
    void testLinearChainLayout() {
        List<TopologyNodeInfo> nodes = List.of(
                node("routeA", "direct:a", "route"),
                node("routeB", "direct:b", "route"),
                node("routeC", "direct:c", "route"));
        List<TopologyEdgeInfo> edges = List.of(
                edge("routeA", "routeB", "direct:b", "internal"),
                edge("routeB", "routeC", "direct:c", "internal"));

        TopologyLayoutEngine engine = new TopologyLayoutEngine();
        TopologyLayoutResult result = engine.layout(nodes, edges);

        assertNotNull(result);
        assertEquals(3, result.nodes.size());
        assertEquals(2, result.edges.size());

        TopologyLayoutNode a = findNode(result, "routeA");
        TopologyLayoutNode b = findNode(result, "routeB");
        TopologyLayoutNode c = findNode(result, "routeC");

        assertEquals(0, a.layer);
        assertEquals(1, b.layer);
        assertEquals(2, c.layer);
        assertTrue(a.y < b.y);
        assertTrue(b.y < c.y);
    }

    @Test
    void testFanOutLayout() {
        List<TopologyNodeInfo> nodes = List.of(
                node("routeA", "timer:tick", "trigger"),
                node("routeB", "direct:b", "route"),
                node("routeC", "direct:c", "route"));
        List<TopologyEdgeInfo> edges = List.of(
                edge("routeA", "routeB", "direct:b", "internal"),
                edge("routeA", "routeC", "direct:c", "internal"));

        TopologyLayoutEngine engine = new TopologyLayoutEngine();
        TopologyLayoutResult result = engine.layout(nodes, edges);

        TopologyLayoutNode a = findNode(result, "routeA");
        TopologyLayoutNode b = findNode(result, "routeB");
        TopologyLayoutNode c = findNode(result, "routeC");

        assertEquals(0, a.layer);
        assertEquals(1, b.layer);
        assertEquals(1, c.layer);
        assertTrue(b.y > a.y);
        assertTrue(c.y > a.y);
    }

    @Test
    void testFanInLayout() {
        List<TopologyNodeInfo> nodes = List.of(
                node("routeA", "timer:a", "trigger"),
                node("routeB", "timer:b", "trigger"),
                node("routeC", "direct:c", "route"));
        List<TopologyEdgeInfo> edges = List.of(
                edge("routeA", "routeC", "direct:c", "internal"),
                edge("routeB", "routeC", "direct:c", "internal"));

        TopologyLayoutEngine engine = new TopologyLayoutEngine();
        TopologyLayoutResult result = engine.layout(nodes, edges);

        TopologyLayoutNode a = findNode(result, "routeA");
        TopologyLayoutNode b = findNode(result, "routeB");
        TopologyLayoutNode c = findNode(result, "routeC");

        assertEquals(0, a.layer);
        assertEquals(0, b.layer);
        assertEquals(1, c.layer);
    }

    @Test
    void testDiamondLayout() {
        List<TopologyNodeInfo> nodes = List.of(
                node("routeA", "timer:tick", "trigger"),
                node("routeB", "direct:b", "route"),
                node("routeC", "direct:c", "route"),
                node("routeD", "direct:d", "route"));
        List<TopologyEdgeInfo> edges = List.of(
                edge("routeA", "routeB", "direct:b", "internal"),
                edge("routeA", "routeC", "direct:c", "internal"),
                edge("routeB", "routeD", "direct:d", "internal"),
                edge("routeC", "routeD", "direct:d", "internal"));

        TopologyLayoutEngine engine = new TopologyLayoutEngine();
        TopologyLayoutResult result = engine.layout(nodes, edges);

        TopologyLayoutNode a = findNode(result, "routeA");
        TopologyLayoutNode b = findNode(result, "routeB");
        TopologyLayoutNode c = findNode(result, "routeC");
        TopologyLayoutNode d = findNode(result, "routeD");

        assertEquals(0, a.layer);
        assertEquals(1, b.layer);
        assertEquals(1, c.layer);
        assertEquals(2, d.layer);
    }

    @Test
    void testTriggerNodesAtLayerZero() {
        List<TopologyNodeInfo> nodes = List.of(
                node("timer-route", "timer:tick", "trigger"),
                node("process", "direct:process", "route"));
        List<TopologyEdgeInfo> edges = List.of(
                edge("timer-route", "process", "direct:process", "internal"));

        TopologyLayoutEngine engine = new TopologyLayoutEngine();
        TopologyLayoutResult result = engine.layout(nodes, edges);

        TopologyLayoutNode timer = findNode(result, "timer-route");
        assertEquals(0, timer.layer);
        assertEquals("trigger", timer.nodeType);
    }

    @Test
    void testIsolatedNodes() {
        List<TopologyNodeInfo> nodes = List.of(
                node("routeA", "timer:a", "trigger"),
                node("routeB", "direct:b", "route"));
        List<TopologyEdgeInfo> edges = List.of();

        TopologyLayoutEngine engine = new TopologyLayoutEngine();
        TopologyLayoutResult result = engine.layout(nodes, edges);

        assertEquals(2, result.nodes.size());
        assertEquals(0, result.edges.size());
    }

    @Test
    void testSelfReferencingCycle() {
        List<TopologyNodeInfo> nodes = List.of(
                node("loop", "seda:loop", "route"));
        List<TopologyEdgeInfo> edges = List.of(
                edge("loop", "loop", "seda:loop", "internal"));

        TopologyLayoutEngine engine = new TopologyLayoutEngine();
        TopologyLayoutResult result = engine.layout(nodes, edges);

        assertEquals(1, result.nodes.size());
        assertEquals(1, result.edges.size());
        assertTrue(result.edges.get(0).selfLoop);
    }

    @Test
    void testAsciiRendering() {
        List<TopologyNodeInfo> nodes = List.of(
                node("routeA", "timer:tick", "trigger"),
                node("routeB", "direct:process", "route"));
        List<TopologyEdgeInfo> edges = List.of(
                edge("routeA", "routeB", "direct:process", "internal"));

        TopologyLayoutEngine engine = new TopologyLayoutEngine();
        TopologyLayoutResult result = engine.layout(nodes, edges);

        TopologyAsciiRenderer renderer = new TopologyAsciiRenderer(
                TopologyLayoutEngine.DEFAULT_NODE_WIDTH * TopologyLayoutEngine.SCALE, false);
        String output = renderer.renderDiagram(result);

        assertNotNull(output);
        assertFalse(output.isEmpty());
        assertTrue(output.contains("routeA"));
        assertTrue(output.contains("routeB"));
    }

    @Test
    void testUnicodeRendering() {
        List<TopologyNodeInfo> nodes = List.of(
                node("routeA", "timer:tick", "trigger"),
                node("routeB", "direct:process", "route"));
        List<TopologyEdgeInfo> edges = List.of(
                edge("routeA", "routeB", "direct:process", "internal"));

        TopologyLayoutEngine engine = new TopologyLayoutEngine();
        TopologyLayoutResult result = engine.layout(nodes, edges);

        TopologyAsciiRenderer renderer = new TopologyAsciiRenderer(
                TopologyLayoutEngine.DEFAULT_NODE_WIDTH * TopologyLayoutEngine.SCALE, true);
        String output = renderer.renderDiagram(result);

        assertNotNull(output);
        assertTrue(output.contains("┌"));
        assertTrue(output.contains("routeA"));
    }

    @Test
    void testExternalEdgeDashed() {
        List<TopologyNodeInfo> nodes = List.of(
                node("producer", "timer:tick", "trigger"),
                node("consumer", "kafka:orders", "route"));
        List<TopologyEdgeInfo> edges = List.of(
                edge("producer", "consumer", "kafka:orders", "external"));

        TopologyLayoutEngine engine = new TopologyLayoutEngine();
        TopologyLayoutResult result = engine.layout(nodes, edges);

        assertEquals(1, result.edges.size());
        assertEquals("external", result.edges.get(0).connectionType);
    }

    @Test
    void testEmptyTopology() {
        TopologyLayoutEngine engine = new TopologyLayoutEngine();
        TopologyLayoutResult result = engine.layout(List.of(), List.of());

        assertNotNull(result);
        assertEquals(0, result.nodes.size());
        assertEquals(0, result.edges.size());
    }

    @Test
    void testJsonParsing() {
        String json = """
                {
                  "nodes": [
                    {"routeId": "r1", "from": "timer:tick", "fromScheme": "timer", "nodeType": "trigger"},
                    {"routeId": "r2", "from": "direct:process", "fromScheme": "direct", "nodeType": "route"}
                  ],
                  "edges": [
                    {"fromRouteId": "r1", "toRouteId": "r2", "endpoint": "direct:process", "connectionType": "internal"}
                  ]
                }
                """;
        org.apache.camel.util.json.JsonObject jo = new org.apache.camel.util.json.JsonObject();
        try {
            jo = (org.apache.camel.util.json.JsonObject) org.apache.camel.util.json.Jsoner.deserialize(json);
        } catch (Exception e) {
            // ignore
        }

        List<TopologyNodeInfo> nodes = TopologyHelper.parseNodes(jo);
        List<TopologyEdgeInfo> edges = TopologyHelper.parseEdges(jo);

        assertEquals(2, nodes.size());
        assertEquals("r1", nodes.get(0).routeId);
        assertEquals("trigger", nodes.get(0).nodeType);
        assertEquals(1, edges.size());
        assertEquals("internal", edges.get(0).connectionType);
    }

    @Test
    void testOrderProcessingTopology() {
        List<TopologyNodeInfo> nodes = List.of(
                node("order-generator", "timer:orders", "trigger"),
                node("order-api", "platform-http:/api/orders", "route"),
                node("process-order", "direct:process-order", "route"),
                node("validate-order", "direct:validate-order", "route"),
                node("order-dispatcher", "kafka:orders", "route"),
                node("fulfillment", "kafka:fulfillment", "route"),
                node("notification", "kafka:notifications", "route"));
        List<TopologyEdgeInfo> edges = List.of(
                edge("order-generator", "process-order", "direct:process-order", "internal"),
                edge("order-api", "process-order", "direct:process-order", "internal"),
                edge("process-order", "validate-order", "direct:validate-order", "internal"),
                edge("process-order", "order-dispatcher", "kafka:orders", "external"),
                edge("order-dispatcher", "fulfillment", "kafka:fulfillment", "external"),
                edge("order-dispatcher", "notification", "kafka:notifications", "external"));

        TopologyLayoutEngine engine = new TopologyLayoutEngine();
        TopologyLayoutResult result = engine.layout(nodes, edges);

        assertEquals(7, result.nodes.size());
        assertEquals(6, result.edges.size());

        TopologyLayoutNode gen = findNode(result, "order-generator");
        TopologyLayoutNode api = findNode(result, "order-api");
        TopologyLayoutNode process = findNode(result, "process-order");

        assertEquals(0, gen.layer);
        assertEquals(0, api.layer);
        assertTrue(process.layer > 0);

        TopologyAsciiRenderer renderer = new TopologyAsciiRenderer(
                TopologyLayoutEngine.DEFAULT_NODE_WIDTH * TopologyLayoutEngine.SCALE, true);
        String output = renderer.renderDiagram(result);
        assertNotNull(output);
        assertTrue(output.contains("order-generator"));
        assertTrue(output.contains("process-order"));
        assertTrue(output.contains("fulfillment"));
    }

    @Test
    void testExternalEndpointBands() {
        // Routes
        List<TopologyNodeInfo> nodes = new ArrayList<>(
                List.of(
                        node("order-api", "platform-http:/api/orders", "route"),
                        node("process-order", "direct:process-order", "route")));

        // Inter-route edges
        List<TopologyEdgeInfo> edges = new ArrayList<>(
                List.of(
                        edge("order-api", "process-order", "direct:process-order", "internal")));

        // External endpoints: 1 consumer (in) and 1 producer (out)
        nodes.add(node("in-order-api", "platform-http:/api/orders", "external-in"));
        edges.add(edge("in-order-api", "order-api", "platform-http:/api/orders", "external"));
        nodes.add(node("out-process-order-0", "kafka:orders", "external-out"));
        edges.add(edge("process-order", "out-process-order-0", "kafka:orders", "external"));

        TopologyLayoutEngine engine = new TopologyLayoutEngine();
        TopologyLayoutResult result = engine.layout(nodes, edges);

        assertEquals(4, result.nodes.size());

        TopologyLayoutNode extIn = findNode(result, "in-order-api");
        TopologyLayoutNode orderApi = findNode(result, "order-api");
        TopologyLayoutNode processOrder = findNode(result, "process-order");
        TopologyLayoutNode extOut = findNode(result, "out-process-order-0");

        // Three-band layout: external-in at top, routes in middle, external-out at bottom
        assertEquals(0, extIn.layer);
        assertTrue(orderApi.layer > extIn.layer, "Route should be below external-in");
        assertTrue(processOrder.layer > extIn.layer, "Route should be below external-in");
        assertTrue(extOut.layer > orderApi.layer, "External-out should be below routes");
        assertTrue(extOut.layer > processOrder.layer, "External-out should be below routes");

        // Verify Y coordinates follow the band ordering
        assertTrue(extIn.y < orderApi.y, "External-in should be visually above routes");
        assertTrue(extOut.y > processOrder.y, "External-out should be visually below routes");
    }

    @Test
    void testExternalEndpointRendering() {
        List<TopologyNodeInfo> nodes = new ArrayList<>(
                List.of(
                        node("myroute", "direct:start", "route")));
        List<TopologyEdgeInfo> edges = new ArrayList<>();

        // Add external-out node
        nodes.add(node("out-myroute-0", "kafka:events", "external-out"));
        edges.add(edge("myroute", "out-myroute-0", "kafka:events", "external"));

        TopologyLayoutEngine engine = new TopologyLayoutEngine();
        TopologyLayoutResult result = engine.layout(nodes, edges);

        TopologyAsciiRenderer renderer = new TopologyAsciiRenderer(
                TopologyLayoutEngine.DEFAULT_NODE_WIDTH * TopologyLayoutEngine.SCALE, true);
        String output = renderer.renderDiagram(result);

        assertNotNull(output);
        assertTrue(output.contains("myroute"));
        assertTrue(output.contains("kafka:events"));
    }

    @Test
    void testOrderProcessingWithExternalEndpoints() {
        // Full order processing topology
        // Only platform-http is truly external (no route sends to it).
        // All kafka topics link routes internally, so they are NOT external.
        List<TopologyNodeInfo> nodes = new ArrayList<>(
                List.of(
                        node("order-generator", "timer:orders", "trigger"),
                        node("order-api", "platform-http:/api/orders", "route"),
                        node("process-order", "direct:process-order", "route"),
                        node("validate-order", "direct:validate-order", "route"),
                        node("order-dispatcher", "kafka:orders", "route"),
                        node("fulfillment", "kafka:fulfillment", "route"),
                        node("notification", "kafka:notifications", "route")));

        List<TopologyEdgeInfo> edges = new ArrayList<>(
                List.of(
                        edge("order-generator", "process-order", "direct:process-order", "internal"),
                        edge("order-api", "process-order", "direct:process-order", "internal"),
                        edge("process-order", "validate-order", "direct:validate-order", "internal"),
                        edge("process-order", "order-dispatcher", "kafka:orders", "external"),
                        edge("order-dispatcher", "fulfillment", "kafka:fulfillment", "external"),
                        edge("order-dispatcher", "notification", "kafka:notifications", "external")));

        // Only platform-http is truly external (messages arrive from outside Camel)
        nodes.add(node("in-order-api", "platform-http:/api/orders", "external-in"));
        edges.add(edge("in-order-api", "order-api", "platform-http:/api/orders", "external"));

        TopologyLayoutEngine engine = new TopologyLayoutEngine();
        TopologyLayoutResult result = engine.layout(nodes, edges);

        // 7 routes + 1 external consumer = 8 nodes
        assertEquals(8, result.nodes.size());

        // Verify three-band ordering
        TopologyLayoutNode extIn = findNode(result, "in-order-api");
        TopologyLayoutNode route = findNode(result, "process-order");

        assertEquals(0, extIn.layer, "External-in should be at layer 0");
        assertTrue(route.layer > extIn.layer, "Routes should be below external-in band");
    }

    @Test
    void testJsonParsingWithExternalEndpoints() {
        String json = """
                {
                  "nodes": [
                    {"routeId": "r1", "from": "direct:start", "fromScheme": "direct", "nodeType": "route"}
                  ],
                  "edges": [],
                  "externalEndpoints": [
                    {"id": "in-r1", "uri": "kafka:input", "scheme": "kafka", "direction": "in", "routeId": "r1"},
                    {"id": "out-r1-0", "uri": "kafka:output", "scheme": "kafka", "direction": "out", "routeId": "r1"}
                  ]
                }
                """;
        org.apache.camel.util.json.JsonObject jo;
        try {
            jo = (org.apache.camel.util.json.JsonObject) org.apache.camel.util.json.Jsoner.deserialize(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        List<TopologyNodeInfo> nodes = TopologyHelper.parseNodes(jo);
        List<TopologyEdgeInfo> edges = TopologyHelper.parseEdges(jo);
        TopologyHelper.addExternalEndpoints(nodes, edges, jo);

        // 1 route + 2 external endpoints = 3 nodes
        assertEquals(3, nodes.size());
        // 2 edges (one for each external endpoint)
        assertEquals(2, edges.size());

        // Verify external-in node
        TopologyNodeInfo extIn = nodes.stream().filter(n -> "in-r1".equals(n.routeId)).findFirst().orElse(null);
        assertNotNull(extIn);
        assertEquals("external-in", extIn.nodeType);
        assertEquals("kafka:input", extIn.from);

        // Verify external-out node
        TopologyNodeInfo extOut = nodes.stream().filter(n -> "out-r1-0".equals(n.routeId)).findFirst().orElse(null);
        assertNotNull(extOut);
        assertEquals("external-out", extOut.nodeType);
        assertEquals("kafka:output", extOut.from);

        // Verify edges: in -> r1, r1 -> out
        TopologyEdgeInfo inEdge = edges.stream().filter(e -> "in-r1".equals(e.fromRouteId)).findFirst().orElse(null);
        assertNotNull(inEdge);
        assertEquals("r1", inEdge.toRouteId);

        TopologyEdgeInfo outEdge = edges.stream().filter(e -> "out-r1-0".equals(e.toRouteId)).findFirst().orElse(null);
        assertNotNull(outEdge);
        assertEquals("r1", outEdge.fromRouteId);
    }

    private static TopologyNodeInfo node(String routeId, String from, String nodeType) {
        TopologyNodeInfo n = new TopologyNodeInfo();
        n.routeId = routeId;
        n.from = from;
        n.fromScheme = from.contains(":") ? from.substring(0, from.indexOf(':')) : from;
        n.nodeType = nodeType;
        return n;
    }

    private static TopologyEdgeInfo edge(String from, String to, String endpoint, String connType) {
        TopologyEdgeInfo e = new TopologyEdgeInfo();
        e.fromRouteId = from;
        e.toRouteId = to;
        e.endpoint = endpoint;
        e.connectionType = connType;
        return e;
    }

    private static TopologyLayoutNode findNode(TopologyLayoutResult result, String routeId) {
        return result.nodes.stream()
                .filter(n -> routeId.equals(n.routeId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Node not found: " + routeId));
    }

}
