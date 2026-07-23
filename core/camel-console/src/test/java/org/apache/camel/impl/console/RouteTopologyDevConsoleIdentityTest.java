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

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.spi.EndpointUriFactory;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.component.EndpointUriFactorySupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Integration test for route topology with endpoint identity query parameters, exercising the full DevConsole JSON
 * output path.
 */
class RouteTopologyDevConsoleIdentityTest extends ContextTestSupport {

    @Override
    public boolean isUseAdviceWith() {
        // skip context start so seda does not enforce same-name/same-size constraint
        return true;
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("sedaIdentityFactory", new SedaWithSizeIdentityFactory());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:work?size=100").routeId("small-queue")
                        .to("mock:small");

                from("seda:work?size=500").routeId("large-queue")
                        .to("mock:large");

                from("direct:dispatch").routeId("dispatcher")
                        .to("seda:work?size=100");
            }
        };
    }

    @Test
    void testIdentityParamsInTopologyJson() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-topology");
        assertNotNull(console);

        JsonObject json = (JsonObject) console.call(DevConsole.MediaType.JSON);
        JsonArray edges = (JsonArray) json.get("edges");

        // dispatcher -> small-queue should exist (same size=100)
        JsonObject toSmall = findEdge(edges, "dispatcher", "small-queue");
        assertNotNull(toSmall, "dispatcher should connect to small-queue via seda:work?size=100");
        assertEquals("seda:work?size=100", toSmall.getString("endpoint"));

        // dispatcher -> large-queue should NOT exist (different size)
        JsonObject toLarge = findEdge(edges, "dispatcher", "large-queue");
        assertNull(toLarge, "dispatcher should NOT connect to large-queue (different size)");
    }

    @Test
    void testNodesIncludeIdentityParams() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-topology");
        JsonObject json = (JsonObject) console.call(DevConsole.MediaType.JSON);
        JsonArray nodes = (JsonArray) json.get("nodes");

        JsonObject smallNode = findNode(nodes, "small-queue");
        assertNotNull(smallNode);
        assertEquals("seda:work?size=100", smallNode.getString("from"));

        JsonObject largeNode = findNode(nodes, "large-queue");
        assertNotNull(largeNode);
        assertEquals("seda:work?size=500", largeNode.getString("from"));
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

    /**
     * A custom EndpointUriFactory for seda that declares "size" as an endpoint identity parameter.
     */
    static class SedaWithSizeIdentityFactory extends EndpointUriFactorySupport implements EndpointUriFactory {

        @Override
        public boolean isEnabled(String scheme) {
            return "seda".equals(scheme);
        }

        @Override
        public String buildUri(String scheme, Map<String, Object> properties, boolean encode) throws URISyntaxException {
            return scheme + ":" + properties.getOrDefault("name", "");
        }

        @Override
        public Set<String> propertyNames() {
            return Set.of("name", "size", "concurrentConsumers", "timeout");
        }

        @Override
        public Set<String> secretPropertyNames() {
            return Collections.emptySet();
        }

        @Override
        public Set<String> endpointIdentityPropertyNames() {
            return Set.of("size");
        }

        @Override
        public Map<String, String> multiValuePrefixes() {
            return Collections.emptyMap();
        }

        @Override
        public boolean isLenientProperties() {
            return false;
        }
    }
}
