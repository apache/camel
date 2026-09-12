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
import java.util.Optional;

import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiagnoseToolsTest {

    private final DiagnoseTools tools;

    DiagnoseToolsTest() {
        tools = new DiagnoseTools();
        CatalogService catalogService = new CatalogService();
        catalogService.catalogRepos = Optional.empty();
        tools.catalogService = catalogService;
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
    void identifiesNoSuchEndpointException() {
        String error = "org.apache.camel.NoSuchEndpointException: No endpoint could be found for: kafak:myTopic";

        JsonObject result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(exceptions(result)).isNotEmpty();
        assertThat(firstException(result)).isEqualTo("NoSuchEndpointException");
    }

    @Test
    void identifiesResolveEndpointFailedException() {
        String error = "org.apache.camel.ResolveEndpointFailedException: "
                       + "Failed to resolve endpoint: kafka:myTopic?unknownOption=value due to: "
                       + "There are 1 parameters that couldn't be set on the endpoint.";

        JsonObject result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(exceptions(result)).isNotEmpty();
        assertThat(firstException(result)).isEqualTo("ResolveEndpointFailedException");
    }

    @Test
    void identifiesFailedToCreateRouteException() {
        String error = "org.apache.camel.FailedToCreateRouteException: "
                       + "Failed to create route route1: Route(route1)[From[direct:start] -> [To[log:out]]]";

        JsonObject result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(exceptions(result)).isNotEmpty();
        assertThat(firstException(result)).isEqualTo("FailedToCreateRouteException");
    }

    @Test
    void identifiesMultipleExceptions() {
        String error = "org.apache.camel.FailedToCreateRouteException: Failed to create route\n"
                       + "Caused by: org.apache.camel.ResolveEndpointFailedException: Failed to resolve endpoint";

        JsonObject result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(exceptions(result).size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void identifiesNoTypeConversionAvailableException() {
        String error = "org.apache.camel.NoTypeConversionAvailableException: "
                       + "No type converter available to convert from type: java.lang.String "
                       + "to the required type: java.io.InputStream";

        JsonObject result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(exceptions(result)).isNotEmpty();
        assertThat(firstException(result)).isEqualTo("NoTypeConversionAvailableException");
    }

    @Test
    void identifiesExchangeTimedOutException() {
        String error = "org.apache.camel.ExchangeTimedOutException: "
                       + "The OUT message was not received within: 30000 millis";

        JsonObject result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(exceptions(result)).isNotEmpty();
        assertThat(firstException(result)).isEqualTo("ExchangeTimedOutException");
    }

    @Test
    void identifiesDirectConsumerNotAvailableException() {
        String error = "org.apache.camel.component.direct.DirectConsumerNotAvailableException: "
                       + "No consumers available on endpoint: direct://myEndpoint";

        JsonObject result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(exceptions(result)).isNotEmpty();
        assertThat(firstException(result)).isEqualTo("DirectConsumerNotAvailableException");
    }

    @Test
    void identifiesPropertyBindingException() {
        String error = "org.apache.camel.PropertyBindingException: "
                       + "Error binding property (brokerz=localhost:9092) with name: brokerz on bean";

        JsonObject result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(exceptions(result)).isNotEmpty();
        assertThat(firstException(result)).isEqualTo("PropertyBindingException");
    }

    @Test
    void identifiesNoSuchBeanException() {
        String error = "org.apache.camel.NoSuchBeanException: "
                       + "No bean could be found in the registry for: myProcessor";

        JsonObject result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(exceptions(result)).isNotEmpty();
        assertThat(firstException(result)).isEqualTo("NoSuchBeanException");
    }

    // ---- Component identification ----

    @Test
    void identifiesKafkaComponent() {
        String error = "org.apache.camel.ResolveEndpointFailedException: "
                       + "Failed to resolve endpoint: kafka:myTopic?brokers=localhost:9092";

        JsonObject result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(components(result)).isNotEmpty();
        assertThat(components(result).stream().map(c -> c.getString("name")).toList()).contains("kafka");
    }

    @Test
    void identifiesDirectComponent() {
        String error = "No consumers available on endpoint: direct://start";

        JsonObject result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(components(result).stream().map(c -> c.getString("name")).toList()).contains("direct");
    }

    // ---- Result structure ----

    @Test
    void resultContainsCommonCauses() {
        String error = "org.apache.camel.NoSuchEndpointException: No endpoint could be found for: xyz:test";

        JsonObject result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(List.copyOf(exceptions(result).get(0).getCollection("commonCauses"))).isNotEmpty();
    }

    @Test
    void resultContainsSuggestedFixes() {
        String error = "org.apache.camel.NoSuchEndpointException: No endpoint could be found for: xyz:test";

        JsonObject result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(List.copyOf(exceptions(result).get(0).getCollection("suggestedFixes"))).isNotEmpty();
    }

    @Test
    void resultContainsDocumentationLinks() {
        String error = "org.apache.camel.NoSuchEndpointException: No endpoint could be found for: xyz:test";

        JsonObject result = tools.camel_error_diagnose(error, null, null, null);

        List<String> links = List.copyOf(exceptions(result).get(0).getCollection("documentationLinks"));
        assertThat(links).isNotEmpty();
        assertThat(links.get(0)).startsWith("https://camel.apache.org/");
    }

    @Test
    void resultContainsSummary() {
        String error = "org.apache.camel.NoSuchEndpointException: No endpoint";

        JsonObject result = tools.camel_error_diagnose(error, null, null, null);

        JsonObject summary = result.getMap("summary");
        assertThat(summary).isNotNull();
        assertThat(summary.getBoolean("diagnosed")).isTrue();
        assertThat(summary.getInteger("exceptionCount")).isGreaterThan(0);
    }

    @Test
    void componentDocumentationUrlPresent() {
        String error = "Failed to resolve endpoint: kafka:myTopic";

        JsonObject result = tools.camel_error_diagnose(error, null, null, null);

        if (!components(result).isEmpty()) {
            assertThat(components(result).get(0).getString("documentationUrl")).contains("camel.apache.org");
        }
    }

    // ---- Unrecognized errors ----

    @Test
    void unrecognizedErrorReturnsDiagnosedFalse() {
        String error = "Some random error that is not a Camel exception";

        JsonObject result = tools.camel_error_diagnose(error, null, null, null);

        JsonObject summary = result.getMap("summary");
        assertThat(summary.getBoolean("diagnosed")).isFalse();
        assertThat(summary.getInteger("exceptionCount")).isEqualTo(0);
    }

    // ---- Full stack trace ----

    @Test
    void handlesFullStackTrace() {
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

        JsonObject result = tools.camel_error_diagnose(stackTrace, null, null, null);

        // Should identify all three exceptions in the chain
        assertThat(exceptions(result).size()).isGreaterThanOrEqualTo(3);

        assertThat(components(result).stream().map(c -> c.getString("name")).toList()).contains("kafka");
    }

    private static List<JsonObject> exceptions(JsonObject result) {
        return List.copyOf(result.getCollection("identifiedExceptions"));
    }

    private static List<JsonObject> components(JsonObject result) {
        return List.copyOf(result.getCollection("identifiedComponents"));
    }

    private static String firstException(JsonObject result) {
        return exceptions(result).get(0).getString("exception");
    }
}
