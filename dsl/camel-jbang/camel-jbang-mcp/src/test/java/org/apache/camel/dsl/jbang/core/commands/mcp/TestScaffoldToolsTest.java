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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.List;

import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestScaffoldToolsTest {

    private final TestScaffoldTools tools;

    TestScaffoldToolsTest() {
        tools = new TestScaffoldTools();
        tools.catalogService = createCatalogService();
        tools.testInfraData = new TestInfraData();
    }

    private static CatalogService createCatalogService() {
        CatalogService service = new CatalogService();
        service.catalogRepos = java.util.Optional.empty();
        return service;
    }

    // ---- Input validation ----

    @Test
    void nullRouteThrows() {
        assertThatThrownBy(() -> tools.camel_route_test_scaffold(null, null, null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("required");
    }

    @Test
    void blankRouteThrows() {
        assertThatThrownBy(() -> tools.camel_route_test_scaffold("   ", "yaml", "main", null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("required");
    }

    // ---- YAML endpoint extraction ----

    @Test
    void extractsFromEndpointFromYaml() {
        String route = """
                - route:
                    from:
                      uri: timer:tick?period=5000
                      steps:
                        - to: log:done
                """;

        List<String> from = tools.extractFromEndpoints(route, "yaml");
        assertThat(from).containsExactly("timer:tick?period=5000");
    }

    @Test
    void extractsToEndpointsFromYaml() {
        String route = """
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - to: kafka:orders?brokers=localhost:9092
                        - to: log:done
                """;

        List<String> to = tools.extractToEndpoints(route, "yaml");
        assertThat(to).containsExactly("kafka:orders?brokers=localhost:9092", "log:done");
    }

    @Test
    void extractsQuotedYamlEndpoints() {
        String route = """
                - route:
                    from:
                      uri: "direct:start"
                      steps:
                        - to: "kafka:orders?brokers=localhost:9092"
                """;

        List<String> from = tools.extractFromEndpoints(route, "yaml");
        assertThat(from).hasSize(1);
        assertThat(from.get(0)).startsWith("direct:start");

        List<String> to = tools.extractToEndpoints(route, "yaml");
        assertThat(to).hasSize(1);
        assertThat(to.get(0)).startsWith("kafka:orders");
    }

    // ---- XML endpoint extraction ----

    @Test
    void extractsFromEndpointFromXml() {
        String route = """
                <route>
                  <from uri="timer:tick?period=5000"/>
                  <to uri="log:done"/>
                </route>
                """;

        List<String> from = tools.extractFromEndpoints(route, "xml");
        assertThat(from).containsExactly("timer:tick?period=5000");
    }

    @Test
    void extractsToEndpointsFromXml() {
        String route = """
                <route>
                  <from uri="direct:start"/>
                  <to uri="kafka:orders?brokers=localhost:9092"/>
                  <to uri="log:done"/>
                </route>
                """;

        List<String> to = tools.extractToEndpoints(route, "xml");
        assertThat(to).containsExactly("kafka:orders?brokers=localhost:9092", "log:done");
    }

    @Test
    void extractsToDEndpointFromXml() {
        String route = """
                <route>
                  <from uri="direct:start"/>
                  <toD uri="http:api.example.com/notify"/>
                </route>
                """;

        List<String> to = tools.extractToEndpoints(route, "xml");
        assertThat(to).containsExactly("http:api.example.com/notify");
    }

    // ---- Scheme extraction ----

    @Test
    void extractsSchemeFromUri() {
        assertThat(tools.extractScheme("kafka:orders?brokers=localhost")).isEqualTo("kafka");
        assertThat(tools.extractScheme("timer:tick")).isEqualTo("timer");
        assertThat(tools.extractScheme("direct:start")).isEqualTo("direct");
        assertThat(tools.extractScheme("https://api.example.com")).isEqualTo("https");
        assertThat(tools.extractScheme("http://api.example.com")).isEqualTo("http");
    }

    @Test
    void extractsSchemeReturnsNullForInvalid() {
        assertThat(tools.extractScheme(null)).isNull();
        assertThat(tools.extractScheme("")).isNull();
        assertThat(tools.extractScheme("noscheme")).isNull();
    }

    // ---- Mock naming ----

    @Test
    void generatesMockNames() {
        assertThat(tools.toMockName("kafka:orders?brokers=localhost:9092")).isEqualTo("mock:kafka-orders");
        assertThat(tools.toMockName("http://api.example.com/notify")).isEqualTo("mock:http-api-example-com-notify");
        assertThat(tools.toMockName("timer:tick")).isEqualTo("mock:timer-tick");
    }

    @Test
    void generatesMockVariableNames() {
        assertThat(tools.toMockVariableName("mock:kafka-orders")).isEqualTo("mockKafkaOrders");
        assertThat(tools.toMockVariableName("mock:http-api")).isEqualTo("mockHttpApi");
    }

    // ---- Main runtime test generation ----

    @Test
    void generatesMainTestWithDirectFrom() throws Exception {
        String route = """
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - to: kafka:orders?brokers=localhost:9092
                        - to: log:done
                """;

        String json = tools.camel_route_test_scaffold(route, "yaml", "main", null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);

        assertThat(result.getString("runtime")).isEqualTo("main");

        String testCode = result.getString("testCode");
        assertThat(testCode).contains("extends CamelTestSupport");
        assertThat(testCode).contains("createRouteBuilder");
        assertThat(testCode).contains("mock:kafka-orders");
        assertThat(testCode).contains("template.sendBody(\"direct:start\"");
        assertThat(testCode).contains("MockEndpoint.assertIsSatisfied(context)");
    }

    @Test
    void generatesMainTestWithTimerFrom() throws Exception {
        String route = """
                - route:
                    from:
                      uri: timer:tick?period=5000
                      steps:
                        - to: kafka:orders
                """;

        String json = tools.camel_route_test_scaffold(route, "yaml", "main", null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);

        String testCode = result.getString("testCode");
        assertThat(testCode).contains("NotifyBuilder");
        assertThat(testCode).contains("whenDone(1)");
        assertThat(testCode).doesNotContain("template.sendBody");
    }

    @Test
    void doesNotMockLogEndpoint() throws Exception {
        String route = """
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - to: log:done
                """;

        String json = tools.camel_route_test_scaffold(route, "yaml", "main", null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);

        JsonArray mocks = result.getCollection("mockEndpoints");
        assertThat(mocks).isEmpty();
    }

    @Test
    void doesNotMockDirectOrSedaEndpoints() throws Exception {
        String route = """
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - to: direct:next
                        - to: seda:async
                """;

        String json = tools.camel_route_test_scaffold(route, "yaml", "main", null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);

        JsonArray mocks = result.getCollection("mockEndpoints");
        assertThat(mocks).isEmpty();
    }

    // ---- Spring Boot runtime test generation ----

    @Test
    void generatesSpringBootTest() throws Exception {
        String route = """
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - to: kafka:orders
                """;

        String json = tools.camel_route_test_scaffold(route, "yaml", "spring-boot", null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);

        assertThat(result.getString("runtime")).isEqualTo("spring-boot");

        String testCode = result.getString("testCode");
        assertThat(testCode).contains("@CamelSpringBootTest");
        assertThat(testCode).contains("@SpringBootTest");
        assertThat(testCode).contains("@Autowired");
        assertThat(testCode).doesNotContain("extends CamelTestSupport");
    }

    // ---- Test-infra detection ----

    @Test
    void detectsKafkaTestInfra() throws Exception {
        String route = """
                - route:
                    from:
                      uri: kafka:orders?brokers=localhost:9092
                      steps:
                        - to: log:done
                """;

        String json = tools.camel_route_test_scaffold(route, "yaml", "main", null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);

        JsonArray infra = result.getCollection("testInfraServices");
        assertThat(infra).isNotEmpty();
        assertThat(infra.getMap(0).get("service")).isEqualTo("KafkaService");
        assertThat(infra.getMap(0).get("factory")).isEqualTo("KafkaServiceFactory");
        assertThat(infra.getMap(0).get("artifactId")).isEqualTo("camel-test-infra-kafka");

        String testCode = result.getString("testCode");
        assertThat(testCode).contains("@RegisterExtension");
        assertThat(testCode).contains("KafkaServiceFactory.createService()");
    }

    @Test
    void detectsJmsTestInfra() throws Exception {
        String route = """
                <route>
                  <from uri="jms:queue:orders"/>
                  <to uri="log:done"/>
                </route>
                """;

        String json = tools.camel_route_test_scaffold(route, "xml", "main", null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);

        JsonArray infra = result.getCollection("testInfraServices");
        assertThat(infra).isNotEmpty();
        assertThat(infra.getMap(0).get("service")).isEqualTo("ArtemisService");
        assertThat(infra.getMap(0).get("artifactId")).isEqualTo("camel-test-infra-artemis");
    }

    @Test
    void detectsMultipleTestInfraServices() throws Exception {
        String route = """
                - route:
                    from:
                      uri: kafka:orders
                      steps:
                        - to: mongodb:myDb?collection=orders
                """;

        String json = tools.camel_route_test_scaffold(route, "yaml", "main", null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);

        JsonArray infra = result.getCollection("testInfraServices");
        assertThat(infra.size()).isEqualTo(2);

        List<String> services = infra.stream()
                .map(i -> ((JsonObject) i).getString("service"))
                .toList();
        assertThat(services).contains("KafkaService", "MongoDBService");
    }

    @Test
    void deduplicatesTestInfraForSameService() throws Exception {
        String route = """
                - route:
                    from:
                      uri: jms:queue:input
                      steps:
                        - to: activemq:queue:output
                """;

        String json = tools.camel_route_test_scaffold(route, "yaml", "main", null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);

        JsonArray infra = result.getCollection("testInfraServices");
        // jms and activemq both map to ArtemisService, should be deduplicated
        assertThat(infra.size()).isEqualTo(1);
        assertThat(infra.getMap(0).get("service")).isEqualTo("ArtemisService");
    }

    // ---- Maven dependencies ----

    @Test
    void includesTestDependencies() throws Exception {
        String route = """
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - to: kafka:orders
                """;

        String json = tools.camel_route_test_scaffold(route, "yaml", "main", null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);

        JsonArray deps = result.getCollection("mavenDependencies");
        List<String> artifactIds = deps.stream()
                .map(d -> ((JsonObject) d).getString("artifactId"))
                .toList();
        assertThat(artifactIds).contains("camel-test-junit5", "camel-mock", "camel-test-infra-kafka");
    }

    @Test
    void includesSpringBootTestDependency() throws Exception {
        String route = """
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - to: kafka:orders
                """;

        String json = tools.camel_route_test_scaffold(route, "yaml", "spring-boot", null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);

        JsonArray deps = result.getCollection("mavenDependencies");
        List<String> artifactIds = deps.stream()
                .map(d -> ((JsonObject) d).getString("artifactId"))
                .toList();
        assertThat(artifactIds).contains("camel-test-spring-junit5");
    }

    // ---- Summary ----

    @Test
    void resultContainsSummary() throws Exception {
        String route = """
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - to: kafka:orders
                        - to: log:done
                """;

        String json = tools.camel_route_test_scaffold(route, "yaml", "main", null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);
        JsonObject summary = result.getMap("summary");

        assertThat(summary).isNotNull();
        assertThat(summary.getInteger("fromEndpointCount")).isEqualTo(1);
        assertThat(summary.getInteger("toEndpointCount")).isEqualTo(2);
        assertThat(summary.getInteger("mockEndpointCount")).isEqualTo(1); // kafka only, log is trivial
        assertThat(summary.getInteger("testInfraServiceCount")).isEqualTo(1); // kafka
    }

    // ---- Default format and runtime ----

    @Test
    void defaultsToYamlAndMain() throws Exception {
        String route = """
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - to: log:done
                """;

        String json = tools.camel_route_test_scaffold(route, null, null, null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);

        assertThat(result.getString("format")).isEqualTo("yaml");
        assertThat(result.getString("runtime")).isEqualTo("main");
    }

    // ---- XML full test ----

    @Test
    void generatesTestFromXmlRoute() throws Exception {
        String route = """
                <route>
                  <from uri="timer:tick?period=5000"/>
                  <to uri="kafka:orders?brokers=localhost:9092"/>
                  <to uri="log:done"/>
                </route>
                """;

        String json = tools.camel_route_test_scaffold(route, "xml", "main", null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);

        String testCode = result.getString("testCode");
        assertThat(testCode).contains("extends CamelTestSupport");
        assertThat(testCode).contains("mock:kafka-orders");
        assertThat(testCode).contains("NotifyBuilder"); // timer from
        assertThat(testCode).contains("@RegisterExtension");
        assertThat(testCode).contains("KafkaServiceFactory");
    }
}
