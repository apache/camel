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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25192: the jump links of the Source tab between the routes of a folder, in YAML and Java, and to the
 * destinations of a switch.
 */
class SourceTabJumpLinksTest {

    @TempDir
    Path dir;

    @Test
    void yamlSwitchLinksToTheRoutesOfItsCasesAndFallback() throws Exception {
        Path tickets = Files.writeString(dir.resolve("tickets.camel.yaml"), """
                - route:
                    id: tickets
                    from:
                      uri: direct:tickets
                      steps:
                        - switch:
                            selector:
                              header:
                                expression: department
                            case:
                              - value: billing
                                uri: direct:billing
                              - uri: direct:technical
                                value: technical
                            otherwise:
                              uri: direct:review
                """);
        Files.writeString(dir.resolve("handlers.camel.yaml"), """
                - route:
                    id: billing
                    from:
                      uri: direct:billing
                      steps:
                        - to:
                            uri: log:billing
                - route:
                    id: technical
                    from:
                      uri: direct:technical
                      steps:
                        - to:
                            uri: log:technical
                - route:
                    from:
                      uri: direct:review
                      steps:
                        - to:
                            uri: log:review
                """);
        SourceTab tab = newTab();
        assertThat(tab.loadDirectory(dir)).isTrue();

        Map<Integer, SourceViewer.JumpLink> links = tab.computeJumpLinks(tickets);
        assertThat(links.get(11).routeId()).isEqualTo("billing");
        assertThat(links.get(12).routeId()).isEqualTo("technical");
        // a route without an id is named by its from endpoint
        assertThat(links.get(15).routeId()).isEqualTo("direct:review");
    }

    @Test
    void javaRoutesLinkToAndFromYamlRoutes() throws Exception {
        Path orders = Files.writeString(dir.resolve("Orders.java"), """
                import org.apache.camel.builder.RouteBuilder;

                public class Orders extends RouteBuilder {
                    public void configure() {
                        from("file:inbox?noop=true")
                            .to("direct:billing");

                        from("direct:tickets")
                            .doSwitch(header("department"))
                                .doCase("billing", "direct:billing")
                            .end();
                    }
                }
                """);
        Path billing = Files.writeString(dir.resolve("billing.camel.yaml"), """
                - route:
                    id: billing
                    from:
                      uri: direct:billing
                      steps:
                        - to:
                            uri: log:billing
                """);
        SourceTab tab = newTab();
        assertThat(tab.loadDirectory(dir)).isTrue();

        Map<Integer, SourceViewer.JumpLink> links = tab.computeJumpLinks(orders);
        assertThat(links.get(5).routeId()).isEqualTo("billing");
        assertThat(links.get(5).filePath()).isEqualTo(billing.toString());
        assertThat(links.get(9).routeId()).isEqualTo("billing");

        // and back: the from: line of the YAML route links to a Java caller
        SourceViewer.JumpLink back = tab.computeJumpLinks(billing).get(2);
        assertThat(back.filePath()).isEqualTo(orders.toString());
        assertThat(back.routeId()).isEqualTo("file:inbox");
    }

    @Test
    void xmlRoutesLinkToAndFromTheOthers() throws Exception {
        // CAMEL-25196: XML routes have the jump links of the YAML and Java ones
        Path orders = Files.writeString(dir.resolve("orders.xml"), """
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                  <route id="orders">
                    <from uri="file:inbox"/>
                    <to uri="direct:billing"/>
                  </route>
                </routes>
                """);
        Path billing = Files.writeString(dir.resolve("billing.camel.yaml"), """
                - route:
                    id: billing
                    from:
                      uri: direct:billing
                      steps:
                        - to:
                            uri: log:billing
                """);
        SourceTab tab = newTab();
        assertThat(tab.loadDirectory(dir)).isTrue();

        SourceViewer.JumpLink link = tab.computeJumpLinks(orders).get(3);
        assertThat(link.routeId()).isEqualTo("billing");
        assertThat(link.filePath()).isEqualTo(billing.toString());
        SourceViewer.JumpLink back = tab.computeJumpLinks(billing).get(2);
        assertThat(back.routeId()).isEqualTo("orders");
        assertThat(back.targetLine()).isEqualTo(3);
    }

    @Test
    void routesOfEveryFolderOfTheProject() throws Exception {
        // CAMEL-25198: a Java route links to a route of another package; tests and build output are left out
        Path orders = dir.resolve("src/main/java/com/acme/orders/OrderRoute.java");
        Path billing = dir.resolve("src/main/java/com/acme/billing/BillingRoute.java");
        Path fake = dir.resolve("src/test/java/com/acme/FakeBilling.java");
        Path copy = dir.resolve("target/classes/BillingRoute.java");
        for (Path p : List.of(orders, billing, fake, copy)) {
            Files.createDirectories(p.getParent());
        }
        Files.writeString(orders, """
                package com.acme.orders;

                public class OrderRoute extends RouteBuilder {
                    public void configure() {
                        from("file:inbox").routeId("orders")
                            .to("direct:billing");
                    }
                }
                """);
        String billingRoute = """
                package com.acme.billing;

                public class BillingRoute extends RouteBuilder {
                    public void configure() {
                        from("direct:billing").routeId("billing")
                            .to("log:billing");
                    }
                }
                """;
        Files.writeString(billing, billingRoute);
        Files.writeString(copy, billingRoute);
        Files.writeString(fake, billingRoute.replace("\"billing\")", "\"fake\")"));

        SourceTab tab = newTab();
        tab.showSourceDirectory(dir);
        assertThat(tab.routeSources()).containsExactlyInAnyOrder(orders, billing);

        SourceViewer.JumpLink link = tab.computeJumpLinks(orders).get(5);
        assertThat(link.routeId()).isEqualTo("billing");
        assertThat(link.filePath()).isEqualTo(billing.toString());
        assertThat(tab.computeJumpLinks(billing).get(4).filePath()).isEqualTo(orders.toString());
    }

    private static SourceTab newTab() {
        return new SourceTab(new MonitorContext(new AtomicReference<>(List.of()), new AtomicReference<>(List.of())));
    }
}
