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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class AboutTest {

    private static final DefaultCamelCatalog CATALOG = new DefaultCamelCatalog();

    @TempDir
    Path dir;

    private About.Model shop() throws Exception {
        Files.writeString(dir.resolve("shop.camel.yaml"), """
                # Every order is checked against the stock and shipped.
                - route:
                    id: orders
                    from:
                      uri: timer:orders
                      parameters:
                        period: 500
                      steps:
                        - to: direct:stock
                        - to: seda:ship
                        - to: http://localhost:8765/pay
                - route:
                    id: stock
                    from:
                      uri: direct:stock
                      steps:
                        - log: checked
                - route:
                    id: ship
                    from:
                      uri: seda:ship
                      steps:
                        - log: shipped
                """);
        return About.of(dir, ProjectOverview.analyze(dir, CATALOG));
    }

    @Test
    void whatComesInTheRoutesUnderTheirCallerAndWhatGoesOut() throws Exception {
        About.Model m = shop();
        assertThat(m.purpose()).isEqualTo("Every order is checked against the stock and shipped.");
        assertThat(m.purposeFrom()).isEqualTo("from the comment at the top of shop.camel.yaml");
        assertThat(m.in()).extracting(About.In::route).containsExactly("orders");
        assertThat(m.in().get(0).kind()).isEqualTo("schedule");
        assertThat(m.routes()).containsExactly(
                new About.RouteLine("orders", 0, null, "timer", 3),
                new About.RouteLine("stock", 1, "call", "direct", 1),
                new About.RouteLine("ship", 1, "async", "seda", 1));
        assertThat(m.out()).singleElement().satisfies(o -> {
            assertThat(o.uri()).contains("localhost:8765/pay");
            assertThat(o.route()).isEqualTo("orders");
        });
    }

    @Test
    void thePurposeIsTheCommentAtTheTopOfTheFile() {
        assertThat(About.leadingComment("a.yaml", "# Licensed to the Apache Software Foundation (ASF)\n- route:")).isNull();
        assertThat(About.leadingComment("Shop.java", "/** Takes the orders. */\npublic class Shop {}"))
                .isEqualTo("Takes the orders.");
        assertThat(About.leadingComment("a.yaml", null)).isNull();
    }

    @Test
    void withoutACommentThePurposeIsTheFirstParagraphOfTheReadme() throws Exception {
        // quick-start/routes: the bean file comes first, and neither file has a comment
        Files.writeString(dir.resolve("beans.yaml"), "- beans:\n    - name: greeter\n      type: camel.example.Greeter\n");
        Files.writeString(dir.resolve("routes.camel.yaml"), """
                - route:
                    id: greeting
                    from:
                      uri: timer:greet
                      steps:
                        - log: hello
                """);
        assertThat(About.of(dir, ProjectOverview.analyze(dir, CATALOG)).purpose()).isNull();

        // what the AI wrote comes last
        Files.writeString(dir.resolve(IntegrationSummary.FILE_NAME), """
                # greeting

                ## Overview ✦ AI-assisted

                <!-- ai:begin -->
                A timer greets every second.

                It has one route.
                <!-- ai:end -->
                """);
        About.Model m = About.of(dir, ProjectOverview.analyze(dir, CATALOG));
        assertThat(m.purpose()).isEqualTo("A timer greets every second.");
        assertThat(m.purposeFrom()).isEqualTo("from camel-summary.md (AI-assisted)");

        // the author's words before: the README
        Files.writeString(dir.resolve("README.md"), """
                # Routes

                [![build](https://example.org/badge.svg)](https://example.org)

                The first step from YAML into your own code: a timer route in YAML calls a Java bean, `Greeter`, that
                builds the message, and logs what the [bean](Greeter.java) returned.

                ## What you will see
                """);
        m = About.of(dir, ProjectOverview.analyze(dir, CATALOG));
        assertThat(m.purpose()).isEqualTo("The first step from YAML into your own code: a timer route in YAML calls a"
                                          + " Java bean, Greeter, that builds the message, and logs what the bean"
                                          + " returned.");
        assertThat(m.purposeFrom()).isEqualTo("from README.md");
        assertThat(About.readme(dir)).isEqualTo(dir.resolve("README.md"));
    }

    @Test
    void theFirstParagraphOfAsciiDoc() {
        assertThat(About.firstParagraph("""
                = Orders
                :toc:

                // the shop
                [NOTE]
                Each order is reserved, paid and shipped, see link:docs/flow.adoc[the flow] and *the* _routes_.

                == Run
                """)).isEqualTo("Each order is reserved, paid and shipped, see the flow and the routes.");
        // a heading or a list only, or too short to say anything
        assertThat(About.firstParagraph("# Title\n\n- one\n- two\n")).isNull();
        assertThat(About.firstParagraph("# Title\n\nSee below.\n")).isNull();
        assertThat(About.firstParagraph(null)).isNull();
        // snake_case is no emphasis
        assertThat(About.firstParagraph("Reads the order_lines table of the shop database.")).contains("order_lines");
    }

    @Test
    void thePurposeIsTheCommentAfterTheLicenseHeader() {
        String yaml = "# Licensed to the Apache Software Foundation (ASF)\n# under the License.\n\n"
                      + "# Takes the orders\n# and ships them.\n\n- route:\n    from: {}\n";
        assertThat(About.leadingComment("a.yaml", yaml)).isEqualTo("Takes the orders and ships them.");
        String xml = "<?xml version=\"1.0\"?>\n<!-- Licensed to the Apache Software Foundation (ASF) -->\n"
                     + "<!-- Takes the orders. -->\n<routes>\n  <!-- not this one -->\n</routes>";
        assertThat(About.leadingComment("a.xml", xml)).isEqualTo("Takes the orders.");
        // a comment inside the routes says nothing about the integration
        assertThat(About.leadingComment("a.xml", "<!-- Licensed to the Apache -->\n<routes><!-- inner --></routes>"))
                .isNull();
    }

    @Test
    void theSameEndpointWithOneOrThreeSlashes() {
        assertThat(AboutReport.baseUri("file:/tmp/in?noop=true")).isEqualTo(AboutReport.baseUri("file:///tmp/in"));
        assertThat(AboutReport.baseUri("kafka://orders")).isEqualTo("kafka:orders");
        assertThat(AboutReport.baseUri("direct:orders")).isEqualTo("direct:orders");
        // the runtime has it encoded, the sources as written (contracts/openapi-client, connect/stock-api)
        assertThat(AboutReport.baseUri("rest-openapi://stock-api.json%23reserveStock"))
                .isEqualTo(AboutReport.baseUri("rest-openapi:stock-api.json#reserveStock"));
        assertThat(AboutReport.baseUri("platform-http:///stock/%7Bsku%7D")).isEqualTo("platform-http:stock/{sku}");
        assertThat(AboutReport.baseUri("http:shop/a+b/100%")).isEqualTo("http:shop/a+b/100%");
    }

    @Test
    void whatHasHappenedFromTheStatusAndTheTimeline() throws Exception {
        About.Model m = shop();
        JsonObject status = (JsonObject) Jsoner.deserialize("""
                {"routes": [{"routeId": "orders", "statistics": {"exchangesTotal": 120, "exchangesFailed": 2}}],
                 "endpoints": {"endpoints": [
                   {"uri": "http://localhost:8765/pay", "direction": "out", "routeId": "orders", "hits": 118}]}}
                """);
        JsonObject timeline = (JsonObject) Jsoner.deserialize("""
                {"end": 1800000000, "seconds": 4, "exchangesDone": [2, 2, 2, 2],
                 "routes": [
                   {"routeId": "orders", "completed": [2, 2, 1, 2], "recovered": [0, 0, 1, 0],
                    "handled": [0, 0, 0, 0], "failed": [0, 0, 0, 0]},
                   {"routeId": "stock", "completed": [2, 2, 2, 2], "recovered": [0, 0, 0, 0],
                    "handled": [0, 0, 0, 0], "failed": [0, 0, 0, 0]}],
                 "errors": [{"routeId": "orders", "exception": "java.net.ConnectException", "counts": [0, 0, 1, 0]}],
                 "circuitBreakers": [{"routeId": "stock", "id": "cb1", "kind": "resilience4j", "states": [2, 2, 1, 0]}],
                 "exchanges": [{"exchangeId": "A", "routeId": "orders", "redelivered": true, "failed": false,
                                "handled": false, "exception": "Connection refused"}]}
                """);
        JsonObject about = AboutReport.json(m, status, timeline);
        JsonObject in = (JsonObject) ((List<?>) about.get("in")).get(0);
        assertThat(in.getLong("exchangesTotal")).isEqualTo(120);
        JsonObject out = (JsonObject) ((List<?>) about.get("out")).get(0);
        assertThat(out.getLong("sends")).isEqualTo(118);

        JsonObject last = (JsonObject) about.get("lastMinutes");
        assertThat(last.getLong("exchanges")).isEqualTo(8);
        assertThat(last.getLong("recovered")).isEqualTo(1);
        JsonObject cause = (JsonObject) last.get("cause");
        assertThat(cause.getString("label")).isEqualTo("ConnectException");
        assertThat(cause.getLong("lastSecondsAgo")).isEqualTo(1);

        String text = AboutReport.text("shop (pid 1)", about);
        assertThat(text).contains("Every order is checked against the stock and shipped.")
                .contains("timer:orders").contains("120 (2 failed)")
                .contains("  ↳ stock  direct → 1 step").contains("  ⇢ ship  seda → 1 step")
                .contains("localhost:8765/pay  from orders").contains("118")
                .contains("Last 4s").contains("8 exchanges · 1 recovered · ConnectException ×1 (last 1s ago)")
                .contains("1 recovered 1s ago · Connection refused")
                .contains("circuit breaker open 2s");
    }

    @Test
    void notRunning() throws Exception {
        String text = AboutReport.text("shop", AboutReport.json(shop(), null, null));
        assertThat(text).contains("Routes").doesNotContain("Last ");
    }
}
