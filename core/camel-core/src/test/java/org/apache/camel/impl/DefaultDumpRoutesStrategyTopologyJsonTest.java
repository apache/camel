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
package org.apache.camel.impl;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that dumping routes as JSon (which is what the camel-jbang CLI uses at source/design time, i.e. without a
 * running route) also produces a route-topology.json describing how routes connect to each other via direct:, and which
 * external systems (endpoints outside the Camel context) they talk to. This is the piece the camel-jbang
 * route-topology/route-diagram source-file rendering path depends on: the topology data must be derivable from the
 * route model alone, without starting any route.
 */
public class DefaultDumpRoutesStrategyTopologyJsonTest extends ContextTestSupport {

    private DefaultDumpRoutesStrategy drd;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setDumpRoutes("json");

        drd = new DefaultDumpRoutesStrategy();
        drd.setLog(false);
        drd.setOutput(testDirectory().toString());
        context.addService(drd);

        return context;
    }

    @Test
    public void testDumpTopologyJsonHasNodesAndEdge() throws Exception {
        JsonObject jo = readTopologyJson();

        JsonArray nodes = (JsonArray) jo.get("nodes");
        assertThat(nodes).hasSize(2);

        JsonObject producerNode = findNode(nodes, "producer");
        assertThat(producerNode.getString("from")).isEqualTo("direct:start");
        assertThat(producerNode.getString("fromScheme")).isEqualTo("direct");
        assertThat(producerNode.getString("nodeType")).isEqualTo("route");

        JsonObject consumerNode = findNode(nodes, "consumer");
        assertThat(consumerNode.getString("from")).isEqualTo("direct:linked");
        assertThat(consumerNode.getString("fromScheme")).isEqualTo("direct");
        assertThat(consumerNode.getString("nodeType")).isEqualTo("route");

        JsonArray edges = (JsonArray) jo.get("edges");
        assertThat(edges).hasSize(1);

        JsonObject edge = (JsonObject) edges.get(0);
        assertThat(edge.getString("fromRouteId")).isEqualTo("producer");
        assertThat(edge.getString("toRouteId")).isEqualTo("consumer");
        assertThat(edge.getString("endpoint")).isEqualTo("direct:linked");
        assertThat(edge.getString("connectionType")).isEqualTo("internal");
    }

    @Test
    public void testDumpTopologyJsonHasExternalEndpoint() throws Exception {
        JsonObject jo = readTopologyJson();

        JsonArray external = (JsonArray) jo.get("externalEndpoints");
        assertThat(external).as("stub:external-system is not consumed by any route, so it must be reported "
                                + "as an external endpoint, not folded into the internal edges")
                .hasSize(1);

        JsonObject ep = (JsonObject) external.get(0);
        assertThat(ep.getString("uri")).isEqualTo("stub:external-system");
        assertThat(ep.getString("scheme")).isEqualTo("stub");
        assertThat(ep.getString("direction")).isEqualTo("out");
        assertThat(ep.getString("routeId")).isEqualTo("producer");
    }

    @Test
    public void testDumpTopologyJsonOmitsExternalEndpointsWhenDisabled() throws Exception {
        // re-dump onto the same already-started context with external endpoints turned off
        drd.setTopologyExternal(false);
        drd.doDumpTopologyAsJSon(context);

        JsonObject jo = readTopologyJson();
        assertThat(jo.get("externalEndpoints")).as("externalEndpoints must be omitted entirely, not just empty, "
                                                   + "when topologyExternal is disabled")
                .isNull();
        // nodes/edges are unaffected by the flag
        assertThat((JsonArray) jo.get("nodes")).hasSize(2);
    }

    @Test
    public void testDumpTopologyJsonWrittenWithEmptyArraysWhenNoRoutes() throws Exception {
        DefaultCamelContext empty = new DefaultCamelContext();
        empty.build();
        try {
            Path emptyOutput = testDirectory().resolve("empty");
            DefaultDumpRoutesStrategy emptyDrd = new DefaultDumpRoutesStrategy();
            try {
                emptyDrd.setLog(false);
                emptyDrd.setOutput(emptyOutput.toString());

                emptyDrd.doDumpTopologyAsJSon(empty);

                File file = new File(emptyOutput.toFile(), "route-topology.json");
                assertThat(file).as("route-topology.json must still be written when there are no routes, so its "
                                    + "presence reliably signals that the dump completed")
                        .exists();

                String text = IOHelper.loadText(new FileInputStream(file));
                JsonObject jo = (JsonObject) Jsoner.deserialize(text);
                assertThat((JsonArray) jo.get("nodes")).isEmpty();
                assertThat((JsonArray) jo.get("edges")).isEmpty();
            } finally {
                emptyDrd.stop();
            }
        } finally {
            empty.stop();
        }
    }

    private JsonObject readTopologyJson() throws Exception {
        File file = new File(testDirectory().toFile(), "route-topology.json");
        assertThat(file).as("route-topology.json should be dumped alongside the route structure").exists();

        String text = IOHelper.loadText(new FileInputStream(file));
        return (JsonObject) Jsoner.deserialize(text);
    }

    private static JsonObject findNode(JsonArray nodes, String routeId) {
        for (Object o : nodes) {
            JsonObject node = (JsonObject) o;
            if (routeId.equals(node.getString("routeId"))) {
                return node;
            }
        }
        throw new AssertionError("No node found for route: " + routeId);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("producer")
                        .to("direct:linked")
                        .to("stub:external-system");

                from("direct:linked").routeId("consumer")
                        .setBody().constant("Hello World");
            }
        };
    }
}
