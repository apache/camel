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
package org.apache.camel.dsl.jbang.core.commands.action;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies CamelRouteTopologyAction.renderTopology against the exact JSON shape that
 * DefaultDumpRoutesStrategy#doDumpTopologyAsJSon (camel-core-engine) writes to route-topology.json. This is the
 * contract the source-file rendering path relies on: whether the JSON comes from a running integration (route-topology
 * dev console) or from a source-file dump, rendering must produce the same connections summary, proving cross-route
 * topology (not just per-route node counts) is correctly wired from source.
 */
class CamelRouteTopologyActionRenderTest {

    @Test
    void testRendersTwoRoutesWithEdgeAsPlainTopology() throws Exception {
        StringPrinter printer = new StringPrinter();
        CamelRouteTopologyAction command = new CamelRouteTopologyAction(new CamelJBangMain().withPrinter(printer));

        int exit = command.renderTopology(twoRouteTopologyWithEdge());

        assertEquals(0, exit);
        String out = printer.getOutput();
        assertTrue(out.contains("Route Topology (2 routes, 1 connections)"),
                "should print the topology summary header, was: " + out);
        assertTrue(out.contains("producer (direct:start) type=route"), "should list the producer node, was: " + out);
        assertTrue(out.contains("--> consumer via direct:linked [internal]"),
                "should list the edge between the two routes, was: " + out);
    }

    @Test
    void testRendersAsJson() throws Exception {
        StringPrinter printer = new StringPrinter();
        CamelRouteTopologyAction command = new CamelRouteTopologyAction(new CamelJBangMain().withPrinter(printer));
        command.jsonOutput = true;

        int exit = command.renderTopology(twoRouteTopologyWithEdge());

        assertEquals(0, exit);
        String out = printer.getOutput();
        assertTrue(out.contains("\"fromRouteId\": \"producer\""), "should dump raw json edges, was: " + out);
    }

    private static JsonObject twoRouteTopologyWithEdge() {
        JsonObject producer = new JsonObject();
        producer.put("routeId", "producer");
        producer.put("from", "direct:start");
        producer.put("fromScheme", "direct");
        producer.put("nodeType", "route");

        JsonObject consumer = new JsonObject();
        consumer.put("routeId", "consumer");
        consumer.put("from", "direct:linked");
        consumer.put("fromScheme", "direct");
        consumer.put("nodeType", "route");

        JsonArray nodes = new JsonArray();
        nodes.add(producer);
        nodes.add(consumer);

        JsonObject edge = new JsonObject();
        edge.put("fromRouteId", "producer");
        edge.put("toRouteId", "consumer");
        edge.put("endpoint", "direct:linked");
        edge.put("connectionType", "internal");

        JsonArray edges = new JsonArray();
        edges.add(edge);

        JsonObject root = new JsonObject();
        root.put("nodes", nodes);
        root.put("edges", edges);
        return root;
    }
}
