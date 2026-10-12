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
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview.Finding;
import org.apache.camel.dsl.yaml.validator.EndpointRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * camel overview and camel validate report the same direct:, seda: and disruptor: endpoints that a route sends to and
 * no route consumes, on the same project of YAML, Java and XML routes: both use the {@link EndpointRules}
 * (CAMEL-25180).
 */
class OverviewValidateParityTest {

    private static final CamelCatalog CATALOG = new DefaultCamelCatalog();

    private static final Pattern OVERVIEW = Pattern.compile("sends to (\\S+) but no route in the project consumes");
    private static final Pattern VALIDATE = Pattern.compile("sends to (\\S+), and no route consumes it");

    private static final String ORDERS_YAML = """
            - route:
                id: intake
                from:
                  uri: timer:orders
                  steps:
                    - to: direct:billing
                    - to: direct:shipping
                    - wireTap:
                        uri: seda:audit
                    - to: disruptor:events
                    - toD: direct:fixed
                    - toD: "direct:${header.target}"
                    - enrich:
                        constant: direct:prices
            - route:
                id: events
                from:
                  uri: seda:events
                  steps:
                    - log: "${body}"
            - rest:
                get:
                  - path: /orders
                    to: direct:lookup
            - errorHandler:
                deadLetterChannel:
                  deadLetterUri: seda:dead
            """;

    private static final String BILLING_JAVA = """
            import org.apache.camel.builder.RouteBuilder;

            public class Billing extends RouteBuilder {
                @Override
                public void configure() {
                    from("direct:billing").routeId("billing")
                        .errorHandler(deadLetterChannel("direct:dlq"))
                        .to("seda:invoices")
                        .toD("direct:fixed");
                }
            }
            """;

    private static final String AUDIT_XML = """
            <routes xmlns="http://camel.apache.org/schema/xml-io">
                <!-- not a route: <route><from uri="direct:shipping"/></route> -->
                <route id="audit">
                    <from uri="timer:audit"/>
                    <pollEnrich>
                        <constant>seda:audit</constant>
                    </pollEnrich>
                    <enrich>
                        <constant>direct:prices</constant>
                    </enrich>
                </route>
            </routes>
            """;

    @TempDir
    Path dir;

    @Test
    void overviewAndValidateReportTheSameEndpoints() throws Exception {
        Files.writeString(dir.resolve("orders.camel.yaml"), ORDERS_YAML);
        Files.writeString(dir.resolve("Billing.java"), BILLING_JAVA);
        Files.writeString(dir.resolve("audit.xml"), AUDIT_XML);

        Set<String> overview = new TreeSet<>();
        for (Finding f : ProjectOverview.analyze(dir, CATALOG).findings()) {
            if ("missing-route".equals(f.kind())) {
                overview.add(endpoint(OVERVIEW, f.message()));
            }
        }
        Set<String> validate = new TreeSet<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.toList()) {
                String name = p.getFileName().toString();
                for (String msg : SourceValidator.validate(name, Files.readString(p), CATALOG, null, dir)) {
                    if (VALIDATE.matcher(msg).find()) {
                        validate.add(endpoint(VALIDATE, msg));
                    }
                }
            }
        }

        // direct:billing, seda:events (not disruptor:events), seda:audit (by a pollEnrich) are consumed; a toD with an
        // expression is only known at runtime; the commented-out XML route is no route
        assertThat(validate).containsExactly(
                "direct:dlq", "direct:fixed", "direct:lookup", "direct:prices", "direct:shipping", "disruptor:events",
                "seda:dead", "seda:invoices");
        assertThat(overview).isEqualTo(validate);
    }

    @Test
    void bothAreQuietWhenARouteConsumesAnEndpointOnlyKnownAtRuntime() throws Exception {
        Files.writeString(dir.resolve("orders.camel.yaml"), ORDERS_YAML);
        Files.writeString(dir.resolve("any.camel.yaml"), """
                - from:
                    uri: "direct:{{name}}"
                    steps:
                      - log: any
                """);

        assertThat(ProjectOverview.analyze(dir, CATALOG).findings())
                .noneMatch(f -> "missing-route".equals(f.kind()));
        assertThat(SourceValidator.validate("orders.camel.yaml", ORDERS_YAML, CATALOG, null, dir))
                .noneMatch(m -> VALIDATE.matcher(m).find());
    }

    private static String endpoint(Pattern pattern, String message) {
        Matcher m = pattern.matcher(message);
        assertThat(m.find()).as(message).isTrue();
        return EndpointRules.endpoint(m.group(1));
    }
}
