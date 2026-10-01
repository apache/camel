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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary.AiContent;
import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary.Capability;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectCapabilities.Capabilities;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectCapabilities.Group;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectCapabilities.GroupLink;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview.Overview;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The capability view (CAMEL-25147): the source's route group wins, shared routes are derived from the links, utility
 * routes come from the sources or the AI, and everything else is Other.
 */
class ProjectCapabilitiesTest {

    static final String ROUTES = """
            - route:
                id: invoice
                group: billing
                from:
                  uri: direct:invoice
                  steps:
                    - to: direct:audit
                    - to: kafka:invoices
            - route:
                id: pay
                group: billing
                from:
                  uri: kafka:invoices
                  steps:
                    - to: direct:audit
            - route:
                id: order
                from:
                  uri: platform-http:/orders
                  steps:
                    - wireTap: seda:trace
                    - doTry:
                        steps:
                          - to: direct:invoice
                          - to: direct:audit
                        doCatch:
                          - exception:
                              - java.lang.Exception
                            steps:
                              - to: direct:dlq
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
            - route:
                id: cleanup
                from:
                  uri: timer:cleanup
                  steps:
                    - to: sql:delete from orders where done = true
            - route:
                id: stray
                from:
                  uri: direct:stray
                  steps:
                    - setBody:
                        constant: x
            """;

    static final AiContent AI = new AiContent(
            "Takes orders.",
            List.of(new Capability("Orders", List.of("order", "audit", "invoice"), "Takes orders over HTTP.")),
            Map.of(), List.of("cleanup"));

    private static Overview overview() {
        return ProjectOverview.analyze(Path.of("shop"), Map.of("shop.camel.yaml", ROUTES), ProjectOverviewTest.CATALOG);
    }

    @Test
    void aRouteThatStartsAFlowIsNeverUtility() {
        // CAMEL-25161: a model called the file intake of widget-gadget plumbing
        Overview o = ProjectOverview.analyze(Path.of("widget-gadget"), Map.of(
                "src/main/java/OrderRoute.java", """
                        public class OrderRoute extends RouteBuilder {
                            public void configure() {
                                from("file:src/main/data?noop=true")
                                        .to("amqp:queue:order.queue");
                            }
                        }
                        """,
                "src/main/java/WidgetGadgetRoute.java", """
                        public class WidgetGadgetRoute extends RouteBuilder {
                            public void configure() {
                                from("amqp:queue:order.queue")
                                    .choice()
                                        .when().jsonpath("$.order[?(@.product=='widget')]").to("amqp:queue:widget.queue")
                                        .otherwise().to("amqp:queue:gadget.queue");
                            }
                        }
                        """), ProjectOverviewTest.CATALOG);
        String intake = "src/main/java/OrderRoute.java:3";
        String distribution = "src/main/java/WidgetGadgetRoute.java:3";
        assertThat(ProjectCapabilities.startsAFlow(o.route(intake), o)).isTrue();
        assertThat(ProjectCapabilities.startsAFlow(o.route(distribution), o)).isFalse();

        // what the model answered
        AiContent ai = IntegrationSummary.parseAnswer("""
                CAPABILITIES:
                - Order Distribution: %s | Routes incoming orders to queues by product.
                UTILITY:
                - %s
                """.formatted(distribution, intake), o);
        assertThat(ai.utility()).isEmpty();

        // an older summary that has it as utility: it goes with the capability it feeds
        AiContent old = new AiContent(
                null, List.of(new Capability("Order Distribution", List.of(distribution), "x")),
                Map.of(), List.of(intake));
        Capabilities caps = ProjectCapabilities.build(o, old);
        assertThat(caps.group("capability:Order Distribution").routes()).containsExactlyInAnyOrder(intake, distribution);
        assertThat(caps.group(ProjectCapabilities.UTILITY)).isNull();
    }

    @Test
    void rolesOfEveryRoute() {
        Capabilities caps = ProjectCapabilities.build(overview(), AI);
        assertThat(caps.groups()).extracting(Group::id)
                .containsExactly("group:billing", "capability:Orders", "shared", "other", "utility");

        Group billing = caps.group("group:billing");
        assertThat(billing.routes()).containsExactly("invoice", "pay");
        assertThat(billing.ai()).as("the source's group is a fact").isFalse();

        // invoice is in billing by its group, whatever the AI said; audit is called from billing and Orders
        Group orders = caps.group("capability:Orders");
        assertThat(orders.routes()).containsExactly("order");
        assertThat(orders.ai()).isTrue();
        assertThat(orders.text()).isEqualTo("Takes orders over HTTP.");
        assertThat(orders.entryPoints()).containsExactly("platform-http:/orders");

        assertThat(caps.group("shared").routes()).containsExactly("audit");
        assertThat(caps.group("shared").systems()).containsExactly("MongoDB");
        assertThat(caps.group("other").routes()).containsExactly("stray");

        // dlq only on error and trace only logs: facts; cleanup because the AI said so
        Group utility = caps.group("utility");
        assertThat(utility.routes()).containsExactly("dlq", "trace", "cleanup");
        assertThat(utility.aiRoutes()).containsExactly("cleanup");
        assertThat(utility.ai()).isTrue();
    }

    @Test
    void linksBetweenGroupsKeepTheStrongestKind() {
        Capabilities caps = ProjectCapabilities.build(overview(), AI);
        assertThat(caps.links()).contains(
                new GroupLink("capability:Orders", "group:billing", "call"),
                new GroupLink("capability:Orders", "shared", "call"),
                new GroupLink("group:billing", "shared", "call"),
                new GroupLink("capability:Orders", "utility", "call"));
        // invoice -> pay is inside billing
        assertThat(caps.links()).noneMatch(l -> l.from().equals(l.to()));
    }

    @Test
    void factsAloneWithoutAi() {
        Capabilities caps = ProjectCapabilities.build(overview(), null);
        assertThat(caps.hasAi()).isFalse();
        assertThat(caps.group("other").routes()).containsExactly("order", "cleanup", "stray");
        assertThat(caps.group("utility").routes()).containsExactly("dlq", "trace");
        assertThat(caps.group("shared").routes()).containsExactly("audit");
    }

    @Test
    void errorPathsAreMarked() {
        Overview o = overview();
        assertThat(o.links()).filteredOn(ProjectOverview.Link::onError).extracting(ProjectOverview.Link::to)
                .containsExactly("dlq");
    }

    @Test
    void errorHandlersOutsideRoutes() {
        String yaml = """
                - onException:
                    exception:
                      - java.lang.Exception
                    steps:
                      - to: direct:parking
                - route:
                    id: parking
                    from:
                      uri: direct:parking
                      steps:
                        - to: kafka:parked
                """;
        String xml = """
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                  <onException><exception>java.lang.Exception</exception><to uri="direct:parking"/></onException>
                  <route id="other"><from uri="direct:other"/><to uri="direct:parking"/></route>
                </routes>
                """;
        String java = """
                public class R extends RouteBuilder {
                    public void configure() {
                        errorHandler(deadLetterChannel("direct:parking"));
                        from("direct:j").routeId("j").to("log:j");
                    }
                }
                """;
        Overview o = ProjectOverview.analyze(Path.of("x"), Map.of("a.camel.yaml", yaml), ProjectOverviewTest.CATALOG);
        assertThat(o.flows()).extracting(ProjectRoutes.Route::key).containsExactly("parking");
        assertThat(ProjectCapabilities.isUtility(o.route("parking"), o)).isTrue();

        // a route that also sends there normally keeps it a business route
        o = ProjectOverview.analyze(Path.of("x"), Map.of("a.camel.yaml", yaml, "b.xml", xml),
                ProjectOverviewTest.CATALOG);
        assertThat(ProjectCapabilities.isUtility(o.route("parking"), o)).isFalse();

        o = ProjectOverview.analyze(Path.of("x"), Map.of("a.camel.yaml", yaml, "R.java", java),
                ProjectOverviewTest.CATALOG);
        assertThat(o.routes()).extracting(ProjectRoutes.Route::kind).contains("errorHandler");
        assertThat(ProjectCapabilities.isUtility(o.route("parking"), o)).isTrue();
        // j only logs, but it is not where messages enter only if something calls it: here direct:j is unreferenced
        assertThat(o.route("j").logOnly()).isTrue();
    }

    @Test
    void summaryCarriesUtilityAndTheMap() {
        Overview o = overview();
        String text = IntegrationSummary.render(o, AI, o.fingerprint(), "m", "2026-09-29");
        assertThat(text).contains("## Utility routes " + IntegrationSummary.AI_MARK, "## Architecture",
                "- **billing** (route group): `invoice`, `pay`",
                "- **Orders** ✦ (capability): `order`; entry: platform-http:/orders",
                "- **Shared services** (used by several): `audit`",
                "- **Utility** ✦ (plumbing): `dlq`, `trace`, `cleanup`");
        assertThat(IntegrationSummary.parse(text).utility()).containsExactly("cleanup");
        // the AI section agrees with the map: invoice is billing's (source group) and audit is shared
        assertThat(text).contains("- **Orders** (routes: `order`): Takes orders over HTTP.");
        assertThat(IntegrationSummary.parse(text).capabilities().get(0).routes()).containsExactly("order");
    }

    @Test
    void aCapabilityLeftWithoutRoutesIsNotListed() {
        Overview o = overview();
        AiContent ai = new AiContent(
                null, List.of(new Capability("Billing", List.of("invoice", "pay"), "Bills.")),
                Map.of());
        String text = IntegrationSummary.render(o, ai, o.fingerprint(), "m", "2026-09-29");
        assertThat(text).doesNotContain("Billing**");
    }

    @Test
    void answerWithUtility() {
        Overview o = overview();
        AiContent ai = IntegrationSummary.parseAnswer("""
                OVERVIEW:
                Shop.
                CAPABILITIES:
                - Orders: order, invoice, audit | Takes orders.
                UTILITY:
                - cleanup: housekeeping
                - `pay`
                DESCRIPTIONS:
                """, o);
        // invoice and pay are grouped by the source: the AI does not place them
        assertThat(ai.capabilities().get(0).routes()).containsExactly("order", "audit");
        assertThat(ai.utility()).containsExactly("cleanup");
        assertThat(IntegrationSummary.userPrompt(o, Map.of()))
                .contains("Routes already grouped by the source: invoice [billing], pay [billing]",
                        "Routes needing grouping: order, audit, dlq, trace, cleanup, stray");
    }
}
