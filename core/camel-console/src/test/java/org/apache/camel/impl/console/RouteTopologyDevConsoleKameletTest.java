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

import java.util.List;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Routes created by Kamelets are hidden from the topology unless asked for with {@code kamelets=true}.
 */
class RouteTopologyDevConsoleKameletTest extends ContextTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // simulates the route a Kamelet creates internally, feeding the user route
                RouteDefinition kamelet = from("timer:tick").routeId("mysource-1")
                        .to("direct:mysource");
                kamelet.setKamelet(true);

                from("direct:mysource").routeId("route1")
                        .to("mock:result");
            }
        };
    }

    @Test
    void testKameletRoutesHiddenByDefault() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-topology");
        JsonObject json = (JsonObject) console.call(DevConsole.MediaType.JSON);

        assertEquals(List.of("route1"), routeIds((JsonArray) json.get("nodes")));
        JsonArray edges = (JsonArray) json.get("edges");
        assertTrue(edges == null || edges.isEmpty(), "edges to hidden kamelet routes must be removed");

        String text = (String) console.call(DevConsole.MediaType.TEXT);
        assertTrue(text.contains("Route Topology (1 routes"), text);
        assertFalse(text.contains("mysource-1"), text);
    }

    @Test
    void testKameletRoutesIncludedOnRequest() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-topology");
        JsonObject json = (JsonObject) console.call(DevConsole.MediaType.JSON, Map.of("kamelets", "true"));

        assertEquals(List.of("mysource-1", "route1"), routeIds((JsonArray) json.get("nodes")));
        JsonArray edges = (JsonArray) json.get("edges");
        assertEquals(1, edges.size());
        JsonObject edge = (JsonObject) edges.get(0);
        assertEquals("mysource-1", edge.getString("fromRouteId"));
        assertEquals("route1", edge.getString("toRouteId"));
    }

    private static List<String> routeIds(JsonArray nodes) {
        return nodes.stream().map(o -> ((JsonObject) o).getString("routeId")).sorted().toList();
    }
}
