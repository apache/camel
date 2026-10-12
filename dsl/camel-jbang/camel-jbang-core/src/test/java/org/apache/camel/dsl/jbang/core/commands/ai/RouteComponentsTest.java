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

import java.util.List;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25586: the components of a route are the components of its endpoints, not every component name in its text.
 */
class RouteComponentsTest {

    private static final CamelCatalog CATALOG = new DefaultCamelCatalog();

    @Test
    void yamlRouteHasTheComponentsOfItsEndpoints() {
        String yaml = """
                - route:
                    description: "reads from kafka: no, from a timer"
                    from:
                      uri: timer:tick
                      steps:
                        - log: "sent to the jms: queue"
                        - to:
                            uri: kafka
                            parameters:
                              topic: orders
                """;
        // the log EIP is not the log component, and names in a description or a message are no endpoints
        assertThat(RouteComponents.of(yaml, null, CATALOG)).containsExactly("timer", "kafka");
    }

    @Test
    void javaRouteReadsTheEndpointDslAndConstants() {
        String java = """
                import org.apache.camel.builder.endpoint.EndpointRouteBuilder;

                public class Orders extends EndpointRouteBuilder {
                    private static final String QUEUE = "sql:select * from orders";

                    @Override
                    public void configure() {
                        // to("ftp:old") is commented out
                        from(timer("tick"))
                            .to(kafka("orders"))
                            .to(QUEUE);
                    }
                }
                """;
        assertThat(RouteComponents.of(java, null, CATALOG)).containsExactly("timer", "kafka", "sql");
    }

    @Test
    void xmlRouteAndTheRestDsl() {
        String xml = """
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                    <route>
                        <from uri="platform-http:/orders"/>
                        <to uri="https://example.com/api"/>
                    </route>
                </routes>
                """;
        assertThat(RouteComponents.of(xml, "xml", CATALOG)).containsExactly("platform-http", "https");

        String rest = """
                - rest:
                    get:
                      - path: /orders
                        to: direct:orders
                """;
        assertThat(RouteComponents.of(rest, "yaml", CATALOG)).contains("rest", "direct");
    }

    @Test
    void textThatIsNoRouteFallsBackToTheNamesInIt() {
        // a fragment, not a route file: look for the component names in the text as before
        assertThat(RouteComponents.of("send it to \"kafka:orders\" and then 'sql'", null, CATALOG))
                .contains("kafka", "sql");
    }

    @Test
    void onlyTheGivenComponents() {
        String yaml = """
                - from:
                    uri: timer:tick
                    steps:
                      - to: exec:ls
                      - to: kafka:orders
                """;
        assertThat(RouteComponents.of(yaml, null, CATALOG, List.of("kafka", "exec", "sql")))
                .containsExactly("kafka", "exec");
    }

    @Test
    void formatFromTheContent() {
        assertThat(RouteComponents.extension(null, "  <routes/>")).isEqualTo("xml");
        assertThat(RouteComponents.extension(null, "class A extends RouteBuilder {}")).isEqualTo("java");
        assertThat(RouteComponents.extension(null, "- from: timer:x")).isEqualTo("yaml");
        assertThat(RouteComponents.extension("YML", "")).isEqualTo("yaml");
    }
}
