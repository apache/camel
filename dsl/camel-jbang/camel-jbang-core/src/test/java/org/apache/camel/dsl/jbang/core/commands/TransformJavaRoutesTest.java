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
package org.apache.camel.dsl.jbang.core.commands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25202: camel transform route reads Java routes without compiling them, and writes what the same routes compiled
 * and dumped give.
 */
class TransformJavaRoutesTest {

    private static final String ORDERS = """
            import org.apache.camel.builder.RouteBuilder;

            public class Orders extends RouteBuilder {
                private static final String QUEUE = "orders";

                @Override
                public void configure() {
                    from("direct:orders").routeId("intake").description("Order entry point")
                        .setHeader("source").constant("web")
                        .choice()
                            .when(simple("${header.vip} == true"))
                                .to("seda:" + QUEUE + "-vip")
                            .otherwise()
                                .to("seda:" + QUEUE)
                        .end()
                        .log("${body}");

                    from("direct:audit").routeId("audit")
                        .split(body().tokenize(","))
                            .to("mock:audit")
                        .end();
                }
            }
            """;

    /** What camel transform route wrote for these routes before, compiling them (Camel 4.23). */
    private static final String YAML = """
            - route:
                id: intake
                description: Order entry point
                from:
                  uri: direct
                  parameters:
                    name: orders
                  steps:
                    - setHeader:
                        name: source
                        expression:
                          constant:
                            expression: web
                    - choice:
                        when:
                          - expression:
                              simple:
                                expression: "${header.vip} == true"
                            steps:
                              - to:
                                  uri: seda
                                  parameters:
                                    name: orders-vip
                        otherwise:
                          steps:
                            - to:
                                uri: seda
                                parameters:
                                  name: orders
                    - log:
                        message: "${body}"
            - route:
                id: audit
                from:
                  uri: direct
                  parameters:
                    name: audit
                  steps:
                    - split:
                        expression:
                          expressionDefinition:
                            expression: "tokenize(simple{${body}}, ,)"
                        steps:
                          - to:
                              uri: mock
                              parameters:
                                name: audit
            """;

    @TempDir
    Path dir;

    @Test
    void writesWhatCompilingTheRoutesGives() throws Exception {
        Path source = Files.writeString(dir.resolve("Orders.java"), ORDERS);
        Path out = dir.resolve("out.yaml");
        TransformJavaRoutes.Result result = TransformJavaRoutes.transform(List.of(source.toString()), "yaml", out.toString(),
                true);
        assertThat(result.transformed()).as(result.reason()).isTrue();
        assertThat(Files.readString(out)).isEqualToIgnoringNewLines(YAML);

        Path xml = dir.resolve("out.xml");
        assertThat(TransformJavaRoutes.transform(List.of(source.toString()), "xml", xml.toString(), true).transformed())
                .isTrue();
        assertThat(Files.readString(xml)).contains("<route id=\"intake\" description=\"Order entry point\">",
                "<constant>web</constant>", "<to uri=\"seda:orders-vip\"/>");
    }

    @Test
    void aDirectoryGetsAFileNamedAfterEachSource() throws Exception {
        Path source = Files.writeString(dir.resolve("Orders.java"), ORDERS);
        Path out = dir.resolve("out");
        assertThat(TransformJavaRoutes.transform(List.of(source.toString()), "yaml", out.toString(), true).transformed())
                .isTrue();
        assertThat(out.resolve("Orders.yaml")).exists();
    }

    @Test
    void aRouteThatCannotBeReadIsCompiled() throws Exception {
        Path source = Files.writeString(dir.resolve("Hey.java"), """
                public class Hey extends org.apache.camel.builder.RouteBuilder {
                    @Override
                    public void configure() {
                        from("timer:java").process(e -> e.getMessage().setBody("Hello")).log("${body}");
                    }
                }
                """);
        Path out = dir.resolve("out.yaml");
        TransformJavaRoutes.Result result = TransformJavaRoutes.transform(List.of(source.toString()), "yaml", out.toString(),
                true);
        assertThat(result.transformed()).isFalse();
        assertThat(result.reason()).contains("Hey.java:4").contains("lambda");
        assertThat(out).doesNotExist();
    }

    @Test
    void onlyJavaSources() throws Exception {
        Path java = Files.writeString(dir.resolve("Orders.java"), ORDERS);
        Path yaml = Files.writeString(dir.resolve("r.camel.yaml"), "- route: {}");
        assertThat(TransformJavaRoutes.applies(List.of(java.toString()))).isTrue();
        assertThat(TransformJavaRoutes.applies(List.of(java.toString(), yaml.toString()))).isFalse();
        assertThat(TransformJavaRoutes.applies(List.of())).isFalse();
    }
}
