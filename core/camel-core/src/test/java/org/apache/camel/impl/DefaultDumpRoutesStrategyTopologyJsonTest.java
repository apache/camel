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
 * running route) also produces a route-topology.json describing how routes connect to each other via direct:. This is
 * the piece CAMEL-23850 relies on to render topology diagrams from source files: the topology data must be derivable
 * from the route model alone, without starting any route.
 */
public class DefaultDumpRoutesStrategyTopologyJsonTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setDumpRoutes("json");

        DefaultDumpRoutesStrategy drd = new DefaultDumpRoutesStrategy();
        drd.setLog(false);
        drd.setOutput(testDirectory().toString());
        context.addService(drd);

        return context;
    }

    @Test
    public void testDumpTopologyJsonHasNodesAndEdge() throws Exception {
        File file = new File(testDirectory().toFile(), "route-topology.json");
        assertThat(file).as("route-topology.json should be dumped alongside the route structure").exists();

        String text = IOHelper.loadText(new FileInputStream(file));
        JsonObject jo = (JsonObject) Jsoner.deserialize(text);

        JsonArray nodes = (JsonArray) jo.get("nodes");
        assertThat(nodes).hasSize(2);

        JsonArray edges = (JsonArray) jo.get("edges");
        assertThat(edges).hasSize(1);

        JsonObject edge = (JsonObject) edges.get(0);
        assertThat(edge.getString("fromRouteId")).isEqualTo("producer");
        assertThat(edge.getString("toRouteId")).isEqualTo("consumer");
        assertThat(edge.getString("endpoint")).isEqualTo("direct:linked");
        assertThat(edge.getString("connectionType")).isEqualTo("internal");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("producer")
                        .to("direct:linked");

                from("direct:linked").routeId("consumer")
                        .setBody().constant("Hello World");
            }
        };
    }
}
