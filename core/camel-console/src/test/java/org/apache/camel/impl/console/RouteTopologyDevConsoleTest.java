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
package org.apache.camel.impl.console;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteTopologyDevConsoleTest extends ContextTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("timer:tick").routeId("trigger")
                        .to("direct:process");

                from("direct:process").routeId("processor")
                        .to("direct:validate")
                        .to("mock:result");

                from("direct:validate").routeId("validator")
                        .to("mock:validated");

                from("seda:loop").routeId("looper")
                        .choice()
                        .when(simple("${body} == 'retry'"))
                        .to("seda:loop")
                        .otherwise()
                        .to("mock:done");

                from("direct:isolated").routeId("isolated")
                        .to("mock:nowhere");
            }
        };
    }

    @Test
    void testConsoleExists() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-topology");
        assertNotNull(console);
        assertEquals("route-topology", console.getId());
    }

    @Test
    void testJsonOutput() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-topology");
        assertNotNull(console);

        JsonObject json = (JsonObject) console.call(DevConsole.MediaType.JSON);
        assertNotNull(json);

        JsonArray nodes = (JsonArray) json.get("nodes");
        assertNotNull(nodes);
        assertEquals(5, nodes.size());

        JsonArray edges = (JsonArray) json.get("edges");
        assertNotNull(edges);
        assertTrue(edges.size() >= 3);
    }

    @Test
    void testTriggerNodeType() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-topology");
        JsonObject json = (JsonObject) console.call(DevConsole.MediaType.JSON);
        JsonArray nodes = (JsonArray) json.get("nodes");

        JsonObject triggerNode = findNode(nodes, "trigger");
        assertNotNull(triggerNode);
        assertEquals("trigger", triggerNode.getString("nodeType"));
        assertEquals("timer", triggerNode.getString("fromScheme"));

        JsonObject processorNode = findNode(nodes, "processor");
        assertNotNull(processorNode);
        assertEquals("route", processorNode.getString("nodeType"));
    }

    @Test
    void testInternalDirectConnection() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-topology");
        JsonObject json = (JsonObject) console.call(DevConsole.MediaType.JSON);
        JsonArray edges = (JsonArray) json.get("edges");

        JsonObject edge = findEdge(edges, "trigger", "processor");
        assertNotNull(edge, "Expected edge from trigger to processor via direct:process");
        assertEquals("direct:process", edge.getString("endpoint"));
        assertEquals("internal", edge.getString("connectionType"));
    }

    @Test
    void testChainedConnections() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-topology");
        JsonObject json = (JsonObject) console.call(DevConsole.MediaType.JSON);
        JsonArray edges = (JsonArray) json.get("edges");

        JsonObject edge = findEdge(edges, "processor", "validator");
        assertNotNull(edge, "Expected edge from processor to validator via direct:validate");
        assertEquals("direct:validate", edge.getString("endpoint"));
        assertEquals("internal", edge.getString("connectionType"));
    }

    @Test
    void testSelfReferencingRoute() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-topology");
        JsonObject json = (JsonObject) console.call(DevConsole.MediaType.JSON);
        JsonArray edges = (JsonArray) json.get("edges");

        JsonObject selfEdge = findEdge(edges, "looper", "looper");
        assertNotNull(selfEdge, "Expected self-referencing edge for looper via seda:loop");
        assertEquals("seda:loop", selfEdge.getString("endpoint"));
        assertEquals("internal", selfEdge.getString("connectionType"));
    }

    @Test
    void testIsolatedRouteHasNoEdges() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-topology");
        JsonObject json = (JsonObject) console.call(DevConsole.MediaType.JSON);
        JsonArray edges = (JsonArray) json.get("edges");

        for (int i = 0; i < edges.size(); i++) {
            JsonObject edge = (JsonObject) edges.get(i);
            if ("isolated".equals(edge.getString("fromRouteId")) || "isolated".equals(edge.getString("toRouteId"))) {
                org.junit.jupiter.api.Assertions.fail("Isolated route should have no edges but found: " + edge);
            }
        }
    }

    @Test
    void testTextOutput() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-topology");
        String text = (String) console.call(DevConsole.MediaType.TEXT);
        assertNotNull(text);
        assertTrue(text.contains("Route Topology"));
        assertTrue(text.contains("trigger"));
        assertTrue(text.contains("processor"));
        assertTrue(text.contains("direct:process"));
    }

    private JsonObject findNode(JsonArray nodes, String routeId) {
        for (int i = 0; i < nodes.size(); i++) {
            JsonObject node = (JsonObject) nodes.get(i);
            if (routeId.equals(node.getString("routeId"))) {
                return node;
            }
        }
        return null;
    }

    private JsonObject findEdge(JsonArray edges, String fromRouteId, String toRouteId) {
        for (int i = 0; i < edges.size(); i++) {
            JsonObject edge = (JsonObject) edges.get(i);
            if (fromRouteId.equals(edge.getString("fromRouteId"))
                    && toRouteId.equals(edge.getString("toRouteId"))) {
                return edge;
            }
        }
        return null;
    }

}
