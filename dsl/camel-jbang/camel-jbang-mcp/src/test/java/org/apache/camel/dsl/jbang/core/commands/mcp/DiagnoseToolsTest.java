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
    void identifiesNoSuchEndpointException() {
        String error = "org.apache.camel.NoSuchEndpointException: No endpoint could be found for: kafak:myTopic";

        DiagnoseTools.DiagnoseResult result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(result.identifiedExceptions()).isNotEmpty();
        assertThat(result.identifiedExceptions().get(0).exception()).isEqualTo("NoSuchEndpointException");
    }

    @Test
    void identifiesResolveEndpointFailedException() {
        String error = "org.apache.camel.ResolveEndpointFailedException: "
                       + "Failed to resolve endpoint: kafka:myTopic?unknownOption=value due to: "
                       + "There are 1 parameters that couldn't be set on the endpoint.";

        DiagnoseTools.DiagnoseResult result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(result.identifiedExceptions()).isNotEmpty();
        assertThat(result.identifiedExceptions().get(0).exception()).isEqualTo("ResolveEndpointFailedException");
    }

    @Test
    void identifiesFailedToCreateRouteException() {
        String error = "org.apache.camel.FailedToCreateRouteException: "
                       + "Failed to create route route1: Route(route1)[From[direct:start] -> [To[log:out]]]";

        DiagnoseTools.DiagnoseResult result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(result.identifiedExceptions()).isNotEmpty();
        assertThat(result.identifiedExceptions().get(0).exception()).isEqualTo("FailedToCreateRouteException");
    }

    @Test
    void identifiesMultipleExceptions() {
        String error = "org.apache.camel.FailedToCreateRouteException: Failed to create route\n"
                       + "Caused by: org.apache.camel.ResolveEndpointFailedException: Failed to resolve endpoint";

        DiagnoseTools.DiagnoseResult result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(result.identifiedExceptions().size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void identifiesNoTypeConversionAvailableException() {
        String error = "org.apache.camel.NoTypeConversionAvailableException: "
                       + "No type converter available to convert from type: java.lang.String "
                       + "to the required type: java.io.InputStream";

        DiagnoseTools.DiagnoseResult result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(result.identifiedExceptions()).isNotEmpty();
        assertThat(result.identifiedExceptions().get(0).exception()).isEqualTo("NoTypeConversionAvailableException");
    }

    @Test
    void identifiesExchangeTimedOutException() {
        String error = "org.apache.camel.ExchangeTimedOutException: "
                       + "The OUT message was not received within: 30000 millis";

        DiagnoseTools.DiagnoseResult result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(result.identifiedExceptions()).isNotEmpty();
        assertThat(result.identifiedExceptions().get(0).exception()).isEqualTo("ExchangeTimedOutException");
    }

    @Test
    void identifiesDirectConsumerNotAvailableException() {
        String error = "org.apache.camel.component.direct.DirectConsumerNotAvailableException: "
                       + "No consumers available on endpoint: direct://myEndpoint";

        DiagnoseTools.DiagnoseResult result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(result.identifiedExceptions()).isNotEmpty();
        assertThat(result.identifiedExceptions().get(0).exception()).isEqualTo("DirectConsumerNotAvailableException");
    }

    @Test
    void identifiesPropertyBindingException() {
        String error = "org.apache.camel.PropertyBindingException: "
                       + "Error binding property (brokerz=localhost:9092) with name: brokerz on bean";

        DiagnoseTools.DiagnoseResult result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(result.identifiedExceptions()).isNotEmpty();
        assertThat(result.identifiedExceptions().get(0).exception()).isEqualTo("PropertyBindingException");
    }

    @Test
    void identifiesNoSuchBeanException() {
        String error = "org.apache.camel.NoSuchBeanException: "
                       + "No bean could be found in the registry for: myProcessor";

        DiagnoseTools.DiagnoseResult result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(result.identifiedExceptions()).isNotEmpty();
        assertThat(result.identifiedExceptions().get(0).exception()).isEqualTo("NoSuchBeanException");
    }

    // ---- Component identification ----

    @Test
    void identifiesKafkaComponent() {
        String error = "org.apache.camel.ResolveEndpointFailedException: "
                       + "Failed to resolve endpoint: kafka:myTopic?brokers=localhost:9092";

        DiagnoseTools.DiagnoseResult result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(result.identifiedComponents()).isNotEmpty();
        assertThat(result.identifiedComponents().stream()
                .map(DiagnoseTools.IdentifiedComponent::name)
                .toList())
                .contains("kafka");
    }

    @Test
    void identifiesDirectComponent() {
        String error = "No consumers available on endpoint: direct://start";

        DiagnoseTools.DiagnoseResult result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(result.identifiedComponents().stream()
                .map(DiagnoseTools.IdentifiedComponent::name)
                .toList())
                .contains("direct");
    }

    // ---- Result structure ----

    @Test
    void resultContainsCommonCauses() {
        String error = "org.apache.camel.NoSuchEndpointException: No endpoint could be found for: xyz:test";

        DiagnoseTools.DiagnoseResult result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(result.identifiedExceptions().get(0).commonCauses()).isNotEmpty();
    }

    @Test
    void resultContainsSuggestedFixes() {
        String error = "org.apache.camel.NoSuchEndpointException: No endpoint could be found for: xyz:test";

        DiagnoseTools.DiagnoseResult result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(result.identifiedExceptions().get(0).suggestedFixes()).isNotEmpty();
    }

    @Test
    void resultContainsDocumentationLinks() {
        String error = "org.apache.camel.NoSuchEndpointException: No endpoint could be found for: xyz:test";

        DiagnoseTools.DiagnoseResult result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(result.identifiedExceptions().get(0).documentationLinks()).isNotEmpty();
        assertThat(result.identifiedExceptions().get(0).documentationLinks().get(0))
                .startsWith("https://camel.apache.org/");
    }

    @Test
    void resultContainsSummary() {
        String error = "org.apache.camel.NoSuchEndpointException: No endpoint";

        DiagnoseTools.DiagnoseResult result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(result.summary()).isNotNull();
        assertThat(result.summary().diagnosed()).isTrue();
        assertThat(result.summary().exceptionCount()).isGreaterThan(0);
    }

    @Test
    void componentDocumentationUrlPresent() {
        String error = "Failed to resolve endpoint: kafka:myTopic";

        DiagnoseTools.DiagnoseResult result = tools.camel_error_diagnose(error, null, null, null);

        if (!result.identifiedComponents().isEmpty()) {
            assertThat(result.identifiedComponents().get(0).documentationUrl()).contains("camel.apache.org");
        }
    }

    // ---- Unrecognized errors ----

    @Test
    void unrecognizedErrorReturnsDiagnosedFalse() {
        String error = "Some random error that is not a Camel exception";

        DiagnoseTools.DiagnoseResult result = tools.camel_error_diagnose(error, null, null, null);

        assertThat(result.summary().diagnosed()).isFalse();
        assertThat(result.summary().exceptionCount()).isEqualTo(0);
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

        DiagnoseTools.DiagnoseResult result = tools.camel_error_diagnose(stackTrace, null, null, null);

        // Should identify all three exceptions in the chain
        assertThat(result.identifiedExceptions().size()).isGreaterThanOrEqualTo(3);

        assertThat(result.identifiedComponents().stream()
                .map(DiagnoseTools.IdentifiedComponent::name)
                .toList())
                .contains("kafka");
    }
}
