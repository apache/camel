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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectCapabilities;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mini panel of the architecture view: the inside of a group as a tree from its entry, routes of other groups as
 * leaves named by their group (CAMEL-25147).
 */
class GroupPreviewTest {

    private static final String ROUTES = """
            - route:
                id: invoice
                group: billing
                from:
                  uri: direct:invoice
                  steps:
                    - to: kafka:invoices
            - route:
                id: order
                from:
                  uri: platform-http:/orders
                  steps:
                    - wireTap: seda:trace
                    - to: direct:validate
                    - to: direct:invoice
                    - doTry:
                        steps:
                          - to: direct:audit
                        doCatch:
                          - exception:
                              - java.lang.Exception
                            steps:
                              - to: direct:dlq
            - route:
                id: validate
                from:
                  uri: direct:validate
                  steps:
                    - to: direct:audit
            - route:
                id: audit
                from:
                  uri: direct:audit
                  steps:
                    - to: mongodb:audit
            - route:
                id: dlq
                from:
                  uri: direct:dlq
                  steps:
                    - to: kafka:dlq
            - route:
                id: trace
                from:
                  uri: seda:trace
                  steps:
                    - log: "${body}"
            """;

    private static String text(List<Line> lines) {
        return lines.stream().map(l -> l.spans().stream().map(Span::content).collect(Collectors.joining()))
                .collect(Collectors.joining("\n"));
    }

    @Test
    void theInsideOfAGroupFromItsEntry() {
        ProjectOverview.Overview o
                = ProjectOverview.analyze(Path.of("shop"), Map.of("shop.camel.yaml", ROUTES), new DefaultCamelCatalog());
        ProjectCapabilities.Capabilities caps = ProjectCapabilities.build(o, new IntegrationSummary.AiContent(
                null, List.of(new IntegrationSummary.Capability("Orders", List.of("order", "validate"), "Takes orders.")),
                Map.of()));
        ProjectCapabilities.Group orders = caps.group("capability:Orders");

        String panel = text(GroupPreview.lines(orders, caps, o, 60, 20));
        assertThat(panel.lines().toList()).first().asString().contains("⇢", "platform-http:/orders");
        assertThat(panel).contains(" order\n", "call → validate", "call → invoice  billing",
                "on error → dlq  Utility");
        // validate has a block of its own after order, which hands off to it; only this group calls audit, and the
        // AI did not place it
        assertThat(panel).contains(" validate\n \u2514\u2500 call \u2192 audit  Other");
        assertThat(panel.indexOf(" order\n")).isLessThan(panel.indexOf(" validate\n"));
        // cut to the height, with what is left counted
        List<Line> cut = GroupPreview.lines(orders, caps, o, 60, 3);
        assertThat(cut).hasSize(3);
        assertThat(text(cut)).contains("more");
        // and to the width
        assertThat(text(GroupPreview.lines(orders, caps, o, 12, 20)).lines()).allMatch(l -> l.length() <= 12);
    }

    @Test
    void sendsOutsideTheProjectAreLeaves() {
        String order = """
                public class OrderRoute extends RouteBuilder {
                    public void configure() {
                        from("file:src/main/data?noop=true").to("amqp:queue:order.queue");
                    }
                }
                """;
        String widgetGadget = """
                public class WidgetGadgetRoute extends RouteBuilder {
                    public void configure() {
                        from("amqp:queue:order.queue")
                            .choice()
                                .when().jsonpath("$.order[?(@.product=='widget')]")
                                    .to("log:widget").to("amqp:queue:widget.queue")
                                .otherwise()
                                    .to("log:gadget").to("amqp:queue:gadget.queue");
                    }
                }
                """;
        ProjectOverview.Overview o = ProjectOverview.analyze(Path.of("wg"),
                Map.of("OrderRoute.java", order, "WidgetGadgetRoute.java", widgetGadget), new DefaultCamelCatalog());
        ProjectCapabilities.Capabilities caps = ProjectCapabilities.build(o, null);
        ProjectCapabilities.Group other = caps.group(ProjectCapabilities.OTHER);

        String panel = text(GroupPreview.lines(other, caps, o, 60, 20, r -> r.replaceAll("Route.java:\\d+", ""),
                scheme -> ProjectOverview.isRemote(scheme, new DefaultCamelCatalog())));
        // a block per route in the order messages flow; the logs stay inside the integration and are left out
        assertThat(panel).isEqualTo(String.join("\n",
                " \u21e2 file:src/main/data",
                " Order",
                " \u2514\u2500 event \u2192 WidgetGadget",
                " \u21e2 amqp:queue:order.queue",
                " WidgetGadget",
                " \u251c\u2500 to \u2192 amqp:queue:widget.queue",
                " \u2514\u2500 to \u2192 amqp:queue:gadget.queue"));
    }
}
