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
import java.util.function.Supplier;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JavaRouteScannerTest {

    private static final String ORDERS = """
            package com.acme;

            import org.apache.camel.builder.RouteBuilder;

            public class Orders extends RouteBuilder {
                @Override
                public void configure() {
                    from("platform-http:/orders").routeId("intake")
                        .choice()
                            .when(header("type").isEqualTo("vip"))
                                .to("direct:vip")
                            .otherwise()
                                .to(Endpoints.STANDARD)
                        .end()
                        .wireTap("direct:audit?block=false");

                    from("direct:vip")
                        .doTry()
                            .toD("kafka:vip-${header.region}")
                        .doCatch(Exception.class)
                            .to("direct:audit")
                        .end();
                }
            }
            """;

    private static final String ENDPOINTS = """
            package com.acme;

            public final class Endpoints {
                public static final String STANDARD = "direct:standard";
            }
            """;

    @Test
    void routesAndTheirStepsWithLines() {
        Map<String, Supplier<String>> sources = Map.of("/p/Orders.java", () -> ORDERS, "/p/Endpoints.java", () -> ENDPOINTS);
        List<ScannedRoute> routes = JavaRouteScanner.scan(ORDERS, sources, new DefaultCamelCatalog());

        assertThat(routes).extracting(ScannedRoute::id).containsExactly("intake", null);
        ScannedRoute intake = routes.get(0);
        assertThat(intake.fromUri()).isEqualTo("platform-http:/orders");
        assertThat(intake.line()).isEqualTo(7);
        // lines from 0, as the Source tab counts them; the constant of the other class is resolved
        assertThat(intake.tos()).containsExactly(
                new ScannedRoute.To("direct:vip", 10),
                new ScannedRoute.To("direct:standard", 12),
                new ScannedRoute.To("direct:audit?block=false", 14));

        ScannedRoute vip = routes.get(1);
        assertThat(vip.fromUri()).isEqualTo("direct:vip");
        assertThat(vip.line()).isEqualTo(16);
        assertThat(vip.tos()).containsExactly(
                new ScannedRoute.To("kafka:vip-${header.region}", 18),
                new ScannedRoute.To("direct:audit", 20));
    }

    @Test
    void theEndpointDsl() {
        String source = """
                public class R extends EndpointRouteBuilder {
                    public void configure() {
                        from(direct("in")).to(direct("out"));
                    }
                }
                """;
        List<ScannedRoute> routes = JavaRouteScanner.scan(source, Map.of(), new DefaultCamelCatalog());
        // direct://in as a route written as text has it, so it links to from("direct:in") in another file
        assertThat(routes.get(0).fromUri()).isEqualTo("direct:in");
        assertThat(routes.get(0).tos()).containsExactly(new ScannedRoute.To("direct:out", 2));
    }

    @Test
    void switchCasesAndFallback() {
        String source = """
                public class R extends RouteBuilder {
                    public void configure() {
                        from("direct:tickets")
                            .doSwitch(header("department"))
                                .doCase("billing", "direct:billing")
                                .doCase("technical").to("direct:technical")
                                .otherwise("direct:review")
                            .end();
                    }
                }
                """;
        List<ScannedRoute> routes = JavaRouteScanner.scan(source, Map.of(), new DefaultCamelCatalog());
        assertThat(routes.get(0).tos()).containsExactly(
                new ScannedRoute.To("direct:billing", 4),
                new ScannedRoute.To("direct:technical", 5),
                new ScannedRoute.To("direct:review", 6));
    }

    @Test
    void onlyJavaSourcesWithARouteBuilder() {
        assertThat(JavaRouteScanner.isJavaRoutes("Orders.java", ORDERS)).isTrue();
        assertThat(JavaRouteScanner.isJavaRoutes("Endpoints.java", ENDPOINTS)).isFalse();
        assertThat(JavaRouteScanner.isJavaRoutes("orders.yaml", ORDERS)).isFalse();
    }
}
