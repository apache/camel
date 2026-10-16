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

import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview.Finding;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview.Overview;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectRoutes.Route;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mistakes in the error handling that camel overview reports as findings (CAMEL-25467): an error route that uses the
 * error handling that sends to it, a route with noErrorHandler whose failures no caller gets back, and error handling
 * that sends to an endpoint no route consumes.
 */
class ErrorHandlingFindingsTest {

    /** fail-well/error-handling: the dead letter channel and the onException send to parked, which uses them too. */
    private static final String SHOP = """
            - errorHandler:
                deadLetterChannel:
                  deadLetterUri: direct:parked
            - onException:
                exception:
                  - java.lang.IllegalStateException
                handled:
                  constant: "true"
                steps:
                  - to:
                      uri: direct:parked
            - route:
                id: checkout
                from:
                  uri: file:orders
                  steps:
                    - to:
                        uri: direct:charge
            - route:
                id: payment-provider
                errorHandler:
                  noErrorHandler: {}
                from:
                  uri: direct:charge
                  steps:
                    - log:
                        message: charging
            - route:
                id: parked
                from:
                  uri: direct:parked
                  steps:
                    - to:
                        uri: file:parked
            """;

    private static Overview analyze(Map<String, String> sources) {
        return ProjectOverview.analyze(Path.of("shop"), sources, ProjectOverviewTest.CATALOG);
    }

    private static List<Finding> findings(Overview o, String kind) {
        return o.findings().stream().filter(f -> kind.equals(f.kind())).toList();
    }

    /** Where an error handler (which has no id) is written, by the start of what it is. */
    private static String handler(Overview o, String label) {
        Route r = o.routes().stream()
                .filter(h -> ProjectOverview.isErrorHandler(h) && h.description().startsWith(label)).findFirst()
                .orElseThrow();
        return r.file() + ":" + r.line();
    }

    @Test
    void anErrorRouteThatUsesTheErrorHandlingSendingToIt() {
        Overview o = analyze(Map.of("shop.camel.yaml", SHOP));

        assertThat(findings(o, "error-route-loop")).singleElement().satisfies(f -> {
            assertThat(f.level()).isEqualTo("warning");
            assertThat(f.route()).isEqualTo("parked");
            assertThat(f.message()).isEqualTo(
                    "parked (shop.camel.yaml:" + o.route("parked").line() + ") receives the failures of the dead letter"
                                              + " channel (" + handler(o, "dead letter channel") + ") and onException"
                                              + " IllegalStateException (" + handler(o, "onException") + ") and uses"
                                              + " them too: a failure in parked goes back to parked, with its own"
                                              + " exception in place of the first; give parked an error handler"
                                              + " of its own, such as a dead letter channel to a log");
        });
    }

    @Test
    void anErrorRouteWithNoErrorHandlerIsFine() {
        String yaml = SHOP.replace("    id: parked\n    from:",
                "    id: parked\n    errorHandler:\n      noErrorHandler: {}\n    from:");
        assertThat(yaml).isNotEqualTo(SHOP);

        assertThat(findings(analyze(Map.of("shop.camel.yaml", yaml)), "error-route-loop")).isEmpty();
    }

    @Test
    void theOwnDeadLetterChannelOfARouteSendsWhereNoRouteConsumes() {
        String yaml = """
                - route:
                    id: parked
                    errorHandler:
                      deadLetterChannel:
                        deadLetterUri: direct:nowhere
                    from:
                      uri: direct:parked
                      steps:
                        - to:
                            uri: file:parked
                """;

        assertThat(findings(analyze(Map.of("parked.camel.yaml", yaml)), "missing-route")).singleElement()
                .extracting(Finding::message).asString()
                .isEqualTo("parked sends to direct:nowhere but no route in the project consumes from it: the failed"
                           + " messages it handles are lost");
    }

    @Test
    void anErrorRouteWithAnErrorHandlerOfItsOwnStillHasTheOnException() {
        // its own dead letter channel replaces the file's, but the file's onException still acts on its failures
        String yaml = SHOP.replace("    id: parked\n    from:",
                "    id: parked\n    errorHandler:\n      deadLetterChannel:\n        deadLetterUri: log:dead\n    from:");
        assertThat(yaml).isNotEqualTo(SHOP);
        Overview o = analyze(Map.of("shop.camel.yaml", yaml));

        assertThat(findings(o, "error-route-loop")).singleElement().extracting(Finding::message).asString()
                .contains("receives the failures of onException IllegalStateException (" + handler(o, "onException")
                          + ") and uses it too")
                .doesNotContain("the dead letter channel (");
    }

    @Test
    void theErrorHandlingOfAnotherFileDoesNotActOnARoute() {
        // the errorHandler and onException of a YAML file are those of its routes
        String handlers = SHOP.substring(0, SHOP.indexOf("- route:\n    id: parked"));
        String parked = SHOP.substring(SHOP.indexOf("- route:\n    id: parked"));
        Overview o = analyze(Map.of("shop.camel.yaml", handlers, "parked.camel.yaml", parked));

        assertThat(o.route("parked")).isNotNull();
        assertThat(findings(o, "error-route-loop")).isEmpty();
    }

    @Test
    void anErrorRouteLoopInJava() {
        String java = """
                import org.apache.camel.builder.RouteBuilder;

                public class Shop extends RouteBuilder {
                    public void configure() {
                        errorHandler(deadLetterChannel("direct:parked"));

                        from("file:orders").routeId("checkout")
                            .to("direct:charge");

                        from("direct:charge").routeId("payment-provider").errorHandler(noErrorHandler())
                            .log("charging");

                        from("direct:parked").routeId("parked")
                            .to("file:parked");
                    }
                }
                """;
        Overview o = analyze(Map.of("src/main/java/Shop.java", java));

        assertThat(findings(o, "error-route-loop")).singleElement().extracting(Finding::message).asString()
                .startsWith("parked (src/main/java/Shop.java:")
                .contains("receives the failures of the dead letter channel (src/main/java/Shop.java:");
    }

    @Test
    void aRouteWithNoErrorHandlerThatNoRouteCalls() {
        String yaml = """
                - route:
                    id: lonely
                    errorHandler:
                      noErrorHandler: {}
                    from:
                      uri: timer:lonely
                      steps:
                        - log:
                            message: tick
                - route:
                    id: intake
                    from:
                      uri: file:orders
                      steps:
                        - to:
                            uri: seda:audit
                        - to:
                            uri: direct:charge
                - route:
                    id: audit
                    errorHandler:
                      noErrorHandler: {}
                    from:
                      uri: seda:audit
                      steps:
                        - log:
                            message: audit
                - route:
                    id: charge
                    errorHandler:
                      noErrorHandler: {}
                    from:
                      uri: direct:charge
                      steps:
                        - log:
                            message: charging
                - route:
                    id: api
                    errorHandler:
                      noErrorHandler: {}
                    from:
                      uri: platform-http:/orders
                      steps:
                        - log:
                            message: order
                """;
        Overview o = analyze(Map.of("app.camel.yaml", yaml));

        // charge is called: its failures go back to intake; the HTTP client of api gets its failures as the answer
        assertThat(findings(o, "unhandled-failures")).extracting(Finding::route).containsExactly("lonely", "audit");
        assertThat(findings(o, "unhandled-failures")).extracting(Finding::message).containsExactly(
                "lonely (app.camel.yaml:" + o.route("lonely").line() + ") has noErrorHandler and no route calls it:"
                                                                                                   + " its failures go back to the consumer of timer:lonely, and no error handler or"
                                                                                                   + " onException acts on them",
                "audit (app.camel.yaml:" + o.route("audit").line() + ") has noErrorHandler and no route waits for it"
                                                                                                                                  + " (intake hands off to it over seda:audit): its failures go back to the"
                                                                                                                                  + " consumer of seda:audit, and no error handler or onException acts on them");
    }

    @Test
    void errorHandlingThatSendsToAnEndpointNoRouteConsumes() {
        String yaml = """
                - errorHandler:
                    deadLetterChannel:
                      deadLetterUri: direct:nowhere
                - onException:
                    exception:
                      - java.lang.IllegalStateException
                    steps:
                      - to:
                          uri: seda:gone
                - route:
                    id: tick
                    from:
                      uri: timer:tick
                      steps:
                        - log:
                            message: tick
                """;
        Overview o = analyze(Map.of("gone.camel.yaml", yaml));

        assertThat(findings(o, "missing-route")).extracting(Finding::message).containsExactly(
                "the dead letter channel (" + handler(o, "dead letter channel") + ") sends to direct:nowhere but no route"
                                                                                              + " in the project consumes from it: the failed messages it handles are lost",
                "onException IllegalStateException (" + handler(o, "onException") + ") sends to seda:gone but no route"
                                                                                                                                                                             + " in the project consumes from it: the failed messages it handles are lost");
    }

    @Test
    void theJsonSaysWhereAFindingIs() {
        Overview o = analyze(Map.of("shop.camel.yaml", SHOP));
        JsonArray findings = (JsonArray) ProjectOverview.toJson(o, null).get("findings");

        JsonObject loop = findings.stream().map(JsonObject.class::cast)
                .filter(f -> "error-route-loop".equals(f.getString("kind"))).findFirst().orElseThrow();
        assertThat(loop.getString("route")).isEqualTo("parked");
        assertThat(loop.getString("source")).isEqualTo("shop.camel.yaml:" + o.route("parked").line());
    }
}
