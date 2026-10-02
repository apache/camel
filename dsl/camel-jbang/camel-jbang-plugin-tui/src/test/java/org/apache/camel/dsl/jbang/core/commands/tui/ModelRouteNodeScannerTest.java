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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.List;
import java.util.Map;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.tui.YamlRouteNodeScanner.EntryKind;
import org.apache.camel.dsl.jbang.core.commands.tui.YamlRouteNodeScanner.NodeEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Go to Node (Ctrl+G) lists the routes of Java and XML files too, read into the Camel model.
 */
class ModelRouteNodeScannerTest {

    /** Each node as depth, type[label] @ line (from 1). */
    private static List<String> describe(List<NodeEntry> nodes) {
        return nodes.stream()
                .map(n -> n.indent() + " " + n.type() + "[" + n.label() + "] @" + (n.lineIndex() + 1))
                .toList();
    }

    @Test
    void theNodesOfAJavaRoute() {
        String java = """
                import org.apache.camel.builder.RouteBuilder;

                public class OrderRoute extends RouteBuilder {
                    @Override
                    public void configure() throws Exception {
                        from("timer:orders?period=500")
                            .routeId("orders")
                            .filter(simple("${body} > 3"))
                                .to("log:big")
                            .end()
                            .choice()
                                .when(simple("${body} == 9"))
                                    .throwException(new IllegalStateException("Out of stock"))
                                .otherwise()
                                    .to("seda:shipping");
                    }
                }
                """;
        List<NodeEntry> nodes = ModelRouteNodeScanner.scan(
                "/p/OrderRoute.java", "OrderRoute.java", java, Map.of(), new DefaultCamelCatalog());

        assertThat(nodes.get(0).kind()).isEqualTo(EntryKind.ROUTE);
        assertThat(nodes.get(0).routeId()).isEqualTo("orders");
        assertThat(nodes.get(0).fromUri()).isEqualTo("timer:orders");
        assertThat(nodes.get(0).lineIndex()).isEqualTo(5);
        assertThat(nodes.get(0).filePath()).isEqualTo("/p/OrderRoute.java");
        assertThat(describe(nodes.subList(1, nodes.size()))).containsExactly(
                "1 filter[simple{${body} > 3}] @8",
                "2 to[log:big] @9",
                "1 choice[] @11",
                "2 when[simple{${body} == 9}] @12",
                "3 throwException[java.lang.IllegalStateException] @13",
                "2 otherwise[] @14",
                "3 to[seda:shipping] @15");
    }

    @Test
    void theNodesOfAnXmlRoute() {
        String xml = """
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                    <route id="shipping">
                        <from uri="seda:shipping"/>
                        <log message="Shipping ${body}"/>
                        <to uri="log:shipped"/>
                    </route>
                </routes>
                """;
        List<NodeEntry> nodes = ModelRouteNodeScanner.scan(
                "/p/routes.xml", "routes.xml", xml, Map.of(), new DefaultCamelCatalog());

        assertThat(nodes.get(0).routeId()).isEqualTo("shipping");
        assertThat(describe(nodes.subList(1, nodes.size()))).containsExactly(
                "1 log[Shipping ${body}] @4",
                "1 to[log:shipped] @5");
    }

    @Test
    void aFileThatIsNotARouteHasNoNodes() {
        assertThat(ModelRouteNodeScanner.scan(
                "/p/Util.java", "Util.java", "public class Util {}", Map.of(), new DefaultCamelCatalog())).isEmpty();
    }
}
