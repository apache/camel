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
import java.util.TreeMap;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview.Finding;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview.Link;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview.Overview;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectRoutes.Route;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The source-level project overview (CAMEL-25143): routes read from YAML, XML and Java without starting Camel, and how
 * they connect.
 */
class ProjectOverviewTest {

    static final CamelCatalog CATALOG = new DefaultCamelCatalog();

    static final String INTAKE = """
            - rest:
                path: /api
                post:
                  - path: /orders
                    to: direct:intake
            - route:
                id: intake
                description: Accepts new orders over HTTP
                from:
                  uri: direct:intake
                  steps:
                    - to:
                        uri: kafka:orders?brokers=localhost:9092&saslJaasConfig=secret
                    - wireTap:
                        uri: seda:audit
            - route:
                id: audit
                from:
                  uri: seda:audit
                  steps:
                    - to: log:audit
            """;

    static final String PROCESSING = """
            - route:
                id: process-order
                group: orders
                from:
                  uri: kafka
                  parameters:
                    topic: orders
                  steps:
                    - choice:
                        when:
                          - simple: "${header.priority} == 'high'"
                            steps:
                              - to: direct:urgent
                        otherwise:
                          steps:
                            - to:
                                uri: sql:insert into orders values (:#${body})
                    - toD: "http://inventory/${header.sku}"
            - route:
                id: nightly
                from:
                  uri: timer:nightly
                  parameters:
                    period: 86400000
                  steps:
                    - to: direct:report
            """;

    static final String URGENT_XML = """
            <routes xmlns="http://camel.apache.org/schema/xml-io">
              <route id="urgent">
                <from uri="direct:urgent"/>
                <to uri="openai:chat-completion?apiKey=abc"/>
                <to uri="direct:intake"/>
              </route>
            </routes>
            """;

    static final String REPORT_JAVA = """
            import org.apache.camel.builder.RouteBuilder;

            public class Report extends RouteBuilder {
                @Override
                public void configure() {
                    // from("direct:ignored") is a comment
                    from("direct:report").routeId("report")
                        .to("file:reports")
                        .to("direct:nowhere");
                }
            }
            """;

    static Map<String, String> sources() {
        Map<String, String> sources = new TreeMap<>();
        sources.put("intake.camel.yaml", INTAKE);
        sources.put("processing.camel.yaml", PROCESSING);
        sources.put("urgent.camel.xml", URGENT_XML);
        sources.put("Report.java", REPORT_JAVA);
        return sources;
    }

    static Overview overview() {
        return ProjectOverview.analyze(Path.of("orders-app"), sources(), CATALOG);
    }

    @Test
    void readsRoutesOfEveryDsl() {
        Overview o = overview();
        assertThat(o.flows()).extracting(Route::key)
                .containsExactlyInAnyOrder("intake", "audit", "process-order", "nightly", "urgent", "report");

        Route process = o.route("process-order");
        assertThat(process.group()).isEqualTo("orders");
        // Kaoto style: the topic comes from the parameters
        assertThat(process.from().uri()).isEqualTo("kafka:orders");
        assertThat(process.produces()).extracting(ProjectRoutes.Endpoint::uri)
                .contains("direct:urgent", "sql:insert into orders values (:#${body})");
        // toD with ${...} is dynamic, a ${...} inside a plain to is component syntax
        assertThat(process.produces()).filteredOn(ProjectRoutes.Endpoint::dynamic).hasSize(1);

        Route report = o.route("report");
        assertThat(report.format()).isEqualTo("java");
        assertThat(report.heuristic()).isTrue();
        assertThat(report.produces()).extracting(ProjectRoutes.Endpoint::uri).containsExactly("file:reports",
                "direct:nowhere");

        assertThat(o.route("intake").description()).isEqualTo("Accepts new orders over HTTP");
        assertThat(o.route("intake").line()).isEqualTo(6);
    }

    @Test
    void linksRoutesOverSharedEndpoints() {
        List<Link> links = overview().links();
        assertThat(links).contains(
                new Link("POST /api/orders", "intake", "direct:intake", "call"),
                new Link("intake", "audit", "seda:audit", "async"),
                new Link("intake", "process-order", "kafka:orders", "event"),
                new Link("process-order", "urgent", "direct:urgent", "call"),
                new Link("urgent", "intake", "direct:intake", "call"),
                new Link("nightly", "report", "direct:report", "call"));
    }

    @Test
    void entryPointsAndSystems() {
        Overview o = overview();
        assertThat(o.entryPoints()).extracting(ProjectOverview.EntryPoint::label)
                .contains("POST /api/orders", "timer:nightly (period=86400000)");
        // the catalog says which components are remote: a timer is not, the work starts inside the integration
        assertThat(o.entryPoints()).filteredOn(ProjectOverview::isInternal)
                .extracting(ProjectOverview.EntryPoint::route).containsExactly("nightly");
        assertThat(o.systems()).extracting(ProjectOverview.SystemUse::category)
                .contains("messaging", "database", "ai", "file");
        // the query is never kept: it may carry credentials
        assertThat(o.systems()).extracting(ProjectOverview.SystemUse::uri)
                .contains("kafka:orders", "openai:chat-completion")
                .noneMatch(u -> u.contains("secret") || u.contains("abc"));
    }

    @Test
    void findings() {
        List<Finding> findings = overview().findings();
        assertThat(findings).extracting(Finding::kind)
                .contains("missing-route", "no-description", "java-dsl", "pass-through");
        assertThat(findings).filteredOn(f -> "missing-route".equals(f.kind())).extracting(Finding::message)
                .containsExactly("report sends to direct:nowhere but no route in the project consumes from it");
        // process-order -> urgent -> intake calls back, but intake reaches process-order over kafka: not a call cycle
        assertThat(findings).filteredOn(f -> "cycle".equals(f.kind())).isEmpty();
        assertThat(findings).filteredOn(f -> "pass-through".equals(f.kind())).extracting(Finding::route)
                .containsExactly("nightly");
    }

    @Test
    void cycleOverCalls() {
        List<Link> links = List.of(new Link("a", "b", "direct:b", "call"), new Link("b", "a", "direct:a", "call"),
                new Link("b", "c", "kafka:c", "event"));
        assertThat(ProjectOverview.cycles(links)).containsExactly(List.of("a", "b", "a"));
    }

    @Test
    void fingerprintFollowsTheSources() {
        Map<String, String> sources = sources();
        String before = ProjectOverview.fingerprint(sources);
        assertThat(ProjectOverview.fingerprint(sources())).isEqualTo(before);
        sources.put("intake.camel.yaml", INTAKE.replace("seda:audit", "seda:audit2"));
        assertThat(ProjectOverview.fingerprint(sources)).isNotEqualTo(before);
        sources.put("intake.camel.yaml", INTAKE.replace("\n", "\r\n"));
        assertThat(ProjectOverview.fingerprint(sources)).isEqualTo(before);
    }

    @Test
    void jsonMarksAiContent() {
        Overview o = overview();
        IntegrationSummary.Summary summary = new IntegrationSummary.Summary(
                o.fingerprint(), "llama3.2", "2026-09-29",
                "Takes orders.", List.of(), Map.of("audit", "Keeps an audit trail of orders."));
        JsonObject json = ProjectOverview.toJson(o, summary);
        String text = json.toJson();
        assertThat(text).contains("\"aiDescription\":\"Keeps an audit trail of orders.\"", "\"aiOverview\"",
                "\"upToDate\":true");
        // the source description wins and is not marked
        assertThat(text).contains("\"description\":\"Accepts new orders over HTTP\"");
    }

    @Test
    void unreadableFilesAreSkipped() {
        Overview o = ProjectOverview.analyze(Path.of("x"), Map.of("broken.camel.yaml", "- route: [unclosed",
                "broken.xml", "<routes><route id=\"a\">"), CATALOG);
        assertThat(o.routes()).isEmpty();
    }

    @Test
    void xmlDoctypeIsRefused() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE routes [<!ENTITY x SYSTEM "file:///etc/passwd">]>
                <routes><route id="a"><from uri="direct:&x;"/></route></routes>
                """;
        assertThat(ProjectRoutes.parse("evil.xml", xxe, CATALOG)).isEmpty();
    }
}
