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

        Map<Integer, JumpLink> links = tab.computeJumpLinks(tickets);
        assertThat(links.get(11).routeId()).isEqualTo("billing");
        assertThat(links.get(12).routeId()).isEqualTo("technical");
        // a route without an id is named by its from endpoint
        assertThat(links.get(15).routeId()).isEqualTo("direct:review");
    }

    @Test
    void aKameletUsedByTwoRoutesLinksToAPopupOfItsCallers() throws Exception {
        // CAMEL-25411: the template of a Kamelet is the route of kamelet:<its name>
        Path kamelet = Files.writeString(dir.resolve("tag-order-action.kamelet.yaml"), """
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: tag-order-action
                spec:
                  template:
                    from:
                      uri: kamelet:source
                      steps:
                        - setBody:
                            simple: "${body} [{{tag}}]"
                """);
        Path orders = Files.writeString(dir.resolve("orders.camel.yaml"), """
                - route:
                    id: orders
                    from:
                      uri: timer:orders
                      steps:
                        - to:
                            uri: kamelet:tag-order-action
                """);
        SourceTab tab = newTab();
        assertThat(tab.loadDirectory(dir)).isTrue();
        // the route links to the Kamelet, and one caller is linked from the template
        assertThat(tab.computeJumpLinks(orders).get(6).routeId()).isEqualTo("tag-order-action");
        assertThat(tab.computeJumpLinks(kamelet).get(6).routeId()).isEqualTo("orders");

        Files.writeString(dir.resolve("refunds.camel.yaml"), """
                - route:
                    id: refunds
                    from:
                      uri: timer:refunds
                      steps:
                        - to:
                            uri: kamelet:tag-order-action
                """);
        assertThat(tab.loadDirectory(dir)).isTrue();
        JumpLink link = tab.computeJumpLinks(kamelet).get(6);
        assertThat(link.routeId()).isEqualTo("2 callers");
        assertThat(link.callersOf()).isEqualTo("kamelet:tag-order-action");
        assertThat(tab.callersOf("kamelet:tag-order-action"))
                .extracting(GotoRoutePopup.RouteItem::routeId).containsExactlyInAnyOrder("orders", "refunds");
        // the usages popup opened on the from: line of the template does not list that line itself
        List<GotoRoutePopup.RouteItem> usages = SourceTab.withoutLine(
                tab.usagesOf("kamelet:tag-order-action"), kamelet.toString(), 6);
        assertThat(usages).extracting(GotoRoutePopup.RouteItem::routeId).containsExactlyInAnyOrder("orders", "refunds");
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

        Map<Integer, JumpLink> links = tab.computeJumpLinks(orders);
        assertThat(links.get(5).routeId()).isEqualTo("billing");
        assertThat(links.get(5).filePath()).isEqualTo(billing.toString());
        assertThat(links.get(9).routeId()).isEqualTo("billing");

        // and back: the from: line of the YAML route has two Java callers, to choose from in a popup (CAMEL-25411)
        JumpLink back = tab.computeJumpLinks(billing).get(2);
        assertThat(back.routeId()).isEqualTo("2 callers");
        assertThat(back.callersOf()).isEqualTo("direct:billing");
        assertThat(tab.callersOf("direct:billing")).extracting(GotoRoutePopup.RouteItem::filePath)
                .containsOnly(orders.toString());
    }

    @Test
    void aHintSaysWhetherTheRouteWaitsForTheAnswer() throws Exception {
        // CAMEL-26823: ↳ the route waits for the answer, ⇢ it hands the message off, as the About view marks them
        Path orders = Files.writeString(dir.resolve("orders.camel.yaml"), """
                - route:
                    id: reserve-order
                    from:
                      uri: seda:orders
                      steps:
                        - to:
                            uri: seda:stock
                            parameters:
                              exchangePattern: InOut
                        - to: direct:audit
                        - wireTap:
                            uri: seda:metrics
                        - to: seda:reserved
                - route:
                    id: stock-service
                    from:
                      uri: seda:stock
                      steps:
                        - log: stock
                - route:
                    id: audit
                    from:
                      uri: direct:audit
                      steps:
                        - log: audit
                - route:
                    id: metrics
                    from:
                      uri: seda:metrics
                      steps:
                        - log: metrics
                - route:
                    id: shipping
                    from:
                      uri: seda:reserved
                      steps:
                        - log: ship
                """);
        Path quotes = Files.writeString(dir.resolve("Quotes.java"), """
                import org.apache.camel.builder.RouteBuilder;

                public class Quotes extends RouteBuilder {
                    public void configure() {
                        from("timer:quote").routeId("quotes")
                            .to("seda:stock?exchangePattern=InOut")
                            .to("seda:reserved");
                    }
                }
                """);
        SourceTab tab = newTab();
        assertThat(tab.loadDirectory(dir)).isTrue();

        List<String> lines = Files.readAllLines(orders);
        Map<Integer, JumpLink> links = tab.computeJumpLinks(orders);
        assertThat(links.get(lineOf(lines, "uri: seda:stock")).hint()).isEqualTo(" ↵ ↳ stock-service");
        assertThat(links.get(lineOf(lines, "- to: direct:audit")).hint()).isEqualTo(" ↵ ↳ audit");
        assertThat(links.get(lineOf(lines, "uri: seda:metrics")).hint()).isEqualTo(" ↵ ⇢ metrics");
        assertThat(links.get(lineOf(lines, "- to: seda:reserved")).hint()).isEqualTo(" ↵ ⇢ shipping");
        // and back: the from line of a route called from one place says how its caller reaches it
        assertThat(links.get(lineOf(lines, "uri: direct:audit") - 1).hint()).isEqualTo(" ↵ ↳ reserve-order");

        // a Java route asks for the answer in the uri
        Map<Integer, JumpLink> java = tab.computeJumpLinks(quotes);
        assertThat(java.get(5).hint()).isEqualTo(" ↵ ↳ stock-service");
        assertThat(java.get(6).hint()).isEqualTo(" ↵ ⇢ shipping");
    }

    @Test
    void howAStepReachesTheRouteItSendsTo() {
        assertThat(SendHow.of("to", "direct:audit", false)).isEqualTo("call");
        assertThat(SendHow.of("to", "kamelet:my-sink", false)).isEqualTo("call");
        assertThat(SendHow.of("to", "jms:queue:stock?exchangePattern=InOut", false)).isEqualTo("call");
        assertThat(SendHow.of("to", "jms:queue:stock?exchangePattern=InOnly", false)).isEqualTo("async");
        assertThat(SendHow.of("to", "seda:stock", true)).isEqualTo("call");
        assertThat(SendHow.of("enrich", "seda:prices", false)).isEqualTo("call");
        assertThat(SendHow.of("wireTap", "direct:audit", false)).isEqualTo("async");
        assertThat(SendHow.of("to", "seda:reserved", false)).isEqualTo("async");
        assertThat(SendHow.of("deadLetterChannel", "seda:dead", false)).isNull();
        assertThat(SendHow.of("pollEnrich", "seda:prices", false)).isNull();

        List<String> block = List.of(
                "        - to:",
                "            uri: seda:stock",
                "            pattern: \"InOut\"",
                "        - to: seda:next");
        assertThat(SendHow.blockSaysInOut(block, 0, 8)).isTrue();
        assertThat(SendHow.blockSaysInOut(block.subList(0, 2), 0, 8)).isFalse();
        // the next step is not part of the block
        assertThat(SendHow.blockSaysInOut(List.of("        - to:", "            uri: seda:a", "        - to:",
                "            pattern: InOut"), 0, 8)).isFalse();
    }

    private static int lineOf(List<String> lines, String text) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equals(text)) {
                return i;
            }
        }
        throw new IllegalArgumentException(text);
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

        JumpLink link = tab.computeJumpLinks(orders).get(3);
        assertThat(link.routeId()).isEqualTo("billing");
        assertThat(link.filePath()).isEqualTo(billing.toString());
        JumpLink back = tab.computeJumpLinks(billing).get(2);
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

        JumpLink link = tab.computeJumpLinks(orders).get(5);
        assertThat(link.routeId()).isEqualTo("billing");
        assertThat(link.filePath()).isEqualTo(billing.toString());
        assertThat(tab.computeJumpLinks(billing).get(4).filePath()).isEqualTo(orders.toString());
    }

    @Test
    void theInfoPanelDescribesTheOpenFileWhenTheViewerHasTheFocus() throws Exception {
        Files.writeString(dir.resolve("application.properties"), "greeting=hello\n");
        Path routes = Files.writeString(dir.resolve("orders.camel.yaml"), """
                - route:
                    id: orders
                    from:
                      uri: direct:orders
                      steps:
                        - to:
                            uri: log:orders
                """);
        SourceTab tab = newTab();
        assertThat(tab.loadDirectory(dir)).isTrue();
        // the list has its cursor on its first entry, not on the routes
        assertThat(tab.infoPath()).isNotNull().isNotEqualTo(routes);

        // the routes are opened another way (as g does): the list keeps its cursor, the viewer has the focus
        tab.openFileForReplay(routes);
        assertThat(tab.infoPath()).isEqualTo(routes);
    }

    private static SourceTab newTab() {
        return new SourceTab(new MonitorContext(new AtomicReference<>(List.of()), new AtomicReference<>(List.of())));
    }
}
