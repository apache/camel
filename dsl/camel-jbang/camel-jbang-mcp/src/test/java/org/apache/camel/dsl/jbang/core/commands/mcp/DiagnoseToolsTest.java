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

import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiagnoseToolsTest {

    private final DiagnoseTools tools;

    DiagnoseToolsTest() {
        tools = new DiagnoseTools();
        CatalogService catalogService = new CatalogService();
        catalogService.catalogRepos = java.util.Optional.empty();
        tools.catalogService = catalogService;
        tools.diagnoseData = new DiagnoseData();
    }

    // ---- Input validation ----

    @Test
    void nullErrorThrows() {
        assertThatThrownBy(() -> tools.camel_error_diagnose(null, null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("required");
    }

    @Test
    void blankErrorThrows() {
        assertThatThrownBy(() -> tools.camel_error_diagnose("   ", null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("required");
    }

    // ---- Exception identification ----

    @Test
    void identifiesNoSuchEndpointException() throws Exception {
        String error = "org.apache.camel.NoSuchEndpointException: No endpoint could be found for: kafak:myTopic";

        String json = tools.camel_error_diagnose(error, null, null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);
        JsonArray exceptions = result.getCollection("identifiedExceptions");

        assertThat(exceptions).isNotEmpty();
        assertThat(exceptions.getMap(0).get("exception")).isEqualTo("NoSuchEndpointException");
    }

    @Test
    void identifiesResolveEndpointFailedException() throws Exception {
        String error = "org.apache.camel.ResolveEndpointFailedException: "
                       + "Failed to resolve endpoint: kafka:myTopic?unknownOption=value due to: "
                       + "There are 1 parameters that couldn't be set on the endpoint.";

        String json = tools.camel_error_diagnose(error, null, null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);
        JsonArray exceptions = result.getCollection("identifiedExceptions");

        assertThat(exceptions).isNotEmpty();
        assertThat(exceptions.getMap(0).get("exception")).isEqualTo("ResolveEndpointFailedException");
    }

    @Test
    void identifiesFailedToCreateRouteException() throws Exception {
        String error = "org.apache.camel.FailedToCreateRouteException: "
                       + "Failed to create route route1: Route(route1)[From[direct:start] -> [To[log:out]]]";

        String json = tools.camel_error_diagnose(error, null, null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);
        JsonArray exceptions = result.getCollection("identifiedExceptions");

        assertThat(exceptions).isNotEmpty();
        JsonObject first = (JsonObject) exceptions.get(0);
        assertThat(first.getString("exception")).isEqualTo("FailedToCreateRouteException");
    }

    @Test
    void identifiesMultipleExceptions() throws Exception {
        String error = "org.apache.camel.FailedToCreateRouteException: Failed to create route\n"
                       + "Caused by: org.apache.camel.ResolveEndpointFailedException: Failed to resolve endpoint";

        String json = tools.camel_error_diagnose(error, null, null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);
        JsonArray exceptions = result.getCollection("identifiedExceptions");

        assertThat(exceptions.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void identifiesNoTypeConversionAvailableException() throws Exception {
        String error = "org.apache.camel.NoTypeConversionAvailableException: "
                       + "No type converter available to convert from type: java.lang.String "
                       + "to the required type: java.io.InputStream";

        String json = tools.camel_error_diagnose(error, null, null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);
        JsonArray exceptions = result.getCollection("identifiedExceptions");

        assertThat(exceptions).isNotEmpty();
        assertThat(exceptions.getMap(0).get("exception")).isEqualTo("NoTypeConversionAvailableException");
    }

    @Test
    void identifiesExchangeTimedOutException() throws Exception {
        String error = "org.apache.camel.ExchangeTimedOutException: "
                       + "The OUT message was not received within: 30000 millis";

        String json = tools.camel_error_diagnose(error, null, null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);
        JsonArray exceptions = result.getCollection("identifiedExceptions");

        assertThat(exceptions).isNotEmpty();
        assertThat(exceptions.getMap(0).get("exception")).isEqualTo("ExchangeTimedOutException");
    }

    @Test
    void identifiesDirectConsumerNotAvailableException() throws Exception {
        String error = "org.apache.camel.component.direct.DirectConsumerNotAvailableException: "
                       + "No consumers available on endpoint: direct://myEndpoint";

        String json = tools.camel_error_diagnose(error, null, null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);
        JsonArray exceptions = result.getCollection("identifiedExceptions");

        assertThat(exceptions).isNotEmpty();
        assertThat(exceptions.getMap(0).get("exception")).isEqualTo("DirectConsumerNotAvailableException");
    }

    @Test
    void identifiesPropertyBindingException() throws Exception {
        String error = "org.apache.camel.PropertyBindingException: "
                       + "Error binding property (brokerz=localhost:9092) with name: brokerz on bean";

        String json = tools.camel_error_diagnose(error, null, null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);
        JsonArray exceptions = result.getCollection("identifiedExceptions");

        assertThat(exceptions).isNotEmpty();
        assertThat(exceptions.getMap(0).get("exception")).isEqualTo("PropertyBindingException");
    }

    @Test
    void identifiesNoSuchBeanException() throws Exception {
        String error = "org.apache.camel.NoSuchBeanException: "
                       + "No bean could be found in the registry for: myProcessor";

        String json = tools.camel_error_diagnose(error, null, null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);
        JsonArray exceptions = result.getCollection("identifiedExceptions");

        assertThat(exceptions).isNotEmpty();
        assertThat(exceptions.getMap(0).get("exception")).isEqualTo("NoSuchBeanException");
    }

    // ---- Component identification ----

    @Test
    void identifiesKafkaComponent() throws Exception {
        String error = "org.apache.camel.ResolveEndpointFailedException: "
                       + "Failed to resolve endpoint: kafka:myTopic?brokers=localhost:9092";

        String json = tools.camel_error_diagnose(error, null, null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);
        JsonArray components = result.getCollection("identifiedComponents");

        assertThat(components).isNotEmpty();
        assertThat(components.stream()
                .map(c -> ((JsonObject) c).getString("name"))
                .toList())
                .contains("kafka");
    }

    @Test
    void identifiesDirectComponent() throws Exception {
        String error = "No consumers available on endpoint: direct://start";

        String json = tools.camel_error_diagnose(error, null, null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);
        JsonArray components = result.getCollection("identifiedComponents");

        assertThat(components.stream()
                .map(c -> ((JsonObject) c).getString("name"))
                .toList())
                .contains("direct");
    }

    // ---- Result structure ----

    @Test
    void resultContainsCommonCauses() throws Exception {
        String error = "org.apache.camel.NoSuchEndpointException: No endpoint could be found for: xyz:test";

        String json = tools.camel_error_diagnose(error, null, null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);
        JsonArray exceptions = result.getCollection("identifiedExceptions");
        JsonObject first = (JsonObject) exceptions.get(0);

        JsonArray causes = (JsonArray) first.get("commonCauses");
        assertThat(causes).isNotEmpty();
    }

    @Test
    void resultContainsSuggestedFixes() throws Exception {
        String error = "org.apache.camel.NoSuchEndpointException: No endpoint could be found for: xyz:test";

        String json = tools.camel_error_diagnose(error, null, null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);
        JsonArray exceptions = result.getCollection("identifiedExceptions");
        JsonObject first = (JsonObject) exceptions.get(0);

        JsonArray fixes = (JsonArray) first.get("suggestedFixes");
        assertThat(fixes).isNotEmpty();
    }

    @Test
    void resultContainsDocumentationLinks() throws Exception {
        String error = "org.apache.camel.NoSuchEndpointException: No endpoint could be found for: xyz:test";

        String json = tools.camel_error_diagnose(error, null, null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);
        JsonArray exceptions = result.getCollection("identifiedExceptions");
        JsonObject first = (JsonObject) exceptions.get(0);

        JsonArray docs = first.getCollection("documentationLinks");
        assertThat(docs).isNotEmpty();
        assertThat(docs.get(0).toString()).startsWith("https://camel.apache.org/");
    }

    @Test
    void resultContainsSummary() throws Exception {
        String error = "org.apache.camel.NoSuchEndpointException: No endpoint";

        String json = tools.camel_error_diagnose(error, null, null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);
        JsonObject summary = result.getMap("summary");

        assertThat(summary).isNotNull();
        assertThat(summary.getBoolean("diagnosed")).isTrue();
        assertThat(summary.getInteger("exceptionCount")).isGreaterThan(0);
    }

    @Test
    void componentDocumentationUrlPresent() throws Exception {
        String error = "Failed to resolve endpoint: kafka:myTopic";

        String json = tools.camel_error_diagnose(error, null, null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);
        JsonArray components = result.getCollection("identifiedComponents");

        if (!components.isEmpty()) {
            JsonObject comp = (JsonObject) components.get(0);
            assertThat(comp.getString("documentationUrl")).contains("camel.apache.org");
        }
    }

    // ---- Unrecognized errors ----

    @Test
    void unrecognizedErrorReturnsDiagnosedFalse() throws Exception {
        String error = "Some random error that is not a Camel exception";

        String json = tools.camel_error_diagnose(error, null, null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);
        JsonObject summary = result.getMap("summary");

        assertThat(summary.getBoolean("diagnosed")).isFalse();
        assertThat(summary.getInteger("exceptionCount")).isEqualTo(0);
    }

    // ---- Full stack trace ----

    @Test
    void handlesFullStackTrace() throws Exception {
        String stackTrace
                = """
                        org.apache.camel.FailedToCreateRouteException: Failed to create route route1 at: >>> To[kafka:myTopic] <<< in route: Route(route1)[From[timer:tick] -> [To[kafka:myTopic]]] because of Failed to resolve endpoint: kafka:myTopic due to: No component found with scheme: kafka
                        \tat org.apache.camel.reifier.RouteReifier.doCreateRoute(RouteReifier.java:230)
                        \tat org.apache.camel.reifier.RouteReifier.createRoute(RouteReifier.java:71)
                        \tat org.apache.camel.impl.DefaultCamelContext.startRouteDefinitions(DefaultCamelContext.java:852)
                        Caused by: org.apache.camel.ResolveEndpointFailedException: Failed to resolve endpoint: kafka:myTopic due to: No component found with scheme: kafka
                        \tat org.apache.camel.impl.engine.AbstractCamelContext.getEndpoint(AbstractCamelContext.java:893)
                        Caused by: org.apache.camel.NoSuchEndpointException: No endpoint could be found for: kafka:myTopic
                        \tat org.apache.camel.component.direct.DirectComponent.createEndpoint(DirectComponent.java:62)
                        """;

        String json = tools.camel_error_diagnose(stackTrace, null, null, null);
        JsonObject result = (JsonObject) Jsoner.deserialize(json);
        JsonArray exceptions = result.getCollection("identifiedExceptions");

        // Should identify all three exceptions in the chain
        assertThat(exceptions.size()).isGreaterThanOrEqualTo(3);

        JsonArray components = result.getCollection("identifiedComponents");
        assertThat(components.stream()
                .map(c -> ((JsonObject) c).getString("name"))
                .toList())
                .contains("kafka");
    }
}
