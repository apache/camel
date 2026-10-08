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
package org.apache.camel.component.typesafeai;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.apache.camel.semantic.SemanticEvaluation;
import org.apache.camel.semantic.SemanticEvaluations;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeSafeAiApiPathTest extends TypeSafeAiTestSupport {
    private final ConcurrentLinkedQueue<String> paths = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void capturePaths() {
        server.removeContext("/v1/systemone");
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().toString());
            handle(exchange);
        });
    }

    @ParameterizedTest
    @CsvSource({
            "'', /v1/systemone", "/, /v1/systemone",
            "/gateway, /gateway/v1/systemone", "/gateway/, /gateway/v1/systemone",
            "/gateway//, /gateway//v1/systemone" })
    void defaultPathPreservesBaseUrlBehavior(String prefix, String expectedPath) {
        TypeSafeAiConfiguration configuration
                = context.getComponent("typesafe-ai", TypeSafeAiComponent.class).getConfiguration();
        configuration.setBaseUrl(configuration.getBaseUrl() + prefix);

        JsonObject response = template.requestBody("typesafe-ai:default", request("Refund"), JsonObject.class);

        assertThat(response.getJsonObject("answers")).containsKey("predicate");
        assertThat(paths).containsExactly(expectedPath);
    }

    @ParameterizedTest
    @CsvSource({
            "'', /v1/decision, /v1/decision", "'', v1/decision, /v1/decision",
            "/, /v1/decision, /v1/decision", "/, v1/decision, /v1/decision",
            "/gateway, /v1/decision, /gateway/v1/decision", "/gateway, v1/decision, /gateway/v1/decision",
            "/gateway/, /v1/decision, /gateway/v1/decision", "/gateway/, v1/decision, /gateway/v1/decision",
            "/gateway/, /v1/decision/, /gateway/v1/decision/", "/gateway/, /, /gateway/" })
    void endpointPathOverridesComponentDefault(String prefix, String apiPath, String expectedPath) {
        TypeSafeAiConfiguration configuration
                = context.getComponent("typesafe-ai", TypeSafeAiComponent.class).getConfiguration();
        configuration.setBaseUrl(configuration.getBaseUrl() + prefix);
        configuration.setApiPath("/component");

        JsonObject response
                = template.requestBody("typesafe-ai:custom?apiPath=" + apiPath, request("Refund"), JsonObject.class);

        assertThat(response.getJsonObject("answers")).containsKey("predicate");
        assertThat(paths).containsExactly(expectedPath);
        assertThat(configuration.getApiPath()).isEqualTo("/component");
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void componentPropertiesConfigureProducerAndSemanticAdapter(boolean semantic) throws Exception {
        Main main = new Main();
        main.addProperty("camel.component.typesafe-ai.api-key", "test-key");
        main.addProperty("camel.component.typesafe-ai.base-url",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/gateway/");
        main.addProperty("camel.component.typesafe-ai.api-path", "/v1/decision");
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                if (semantic) {
                    SemanticEvaluations.get(getContext()).replace("test",
                            Map.of("refund",
                                    new SemanticEvaluation(
                                            "boolean", null, null, Map.of("instructions", "Refund requested?", "threshold", 0.5,
                                                    "uncertainty", 0.0, "uncertaintyPolicy", "fail"))));
                    from("direct:request").setBody().language("semantic", "ref:refund");
                } else {
                    from("direct:request").to("typesafe-ai:configured");
                }
            }
        });
        respond = request -> result(Map.of(semantic ? "question" : "predicate", Map.of("type", "noul", "noul", 0.9)));
        try {
            main.start();
            try (var producer = main.getCamelContext().createProducerTemplate()) {
                Object response = producer.requestBody("direct:request", semantic ? "Refund" : request("Refund"));
                if (semantic) {
                    assertThat(response).isEqualTo(true);
                } else {
                    assertThat(((JsonObject) response).getJsonObject("answers")).containsKey("predicate");
                }
                assertThat(paths).containsExactly("/gateway/v1/decision");
                assertThat(requests).hasSize(1);
                assertThat(requests.peek().get("state")).isEqualTo("Refund");
                assertThat(authorization).containsExactly("Bearer test-key");
            }
        } finally {
            main.stop();
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ", "https://example.com/v1/decision", "//example.com/v1/decision", "/v1/decision?query=value",
            "/v1/decision#fragment", "/invalid path", "/invalid%path" })
    void rejectsInvalidPathsBeforeSending(String apiPath) {
        context.getComponent("typesafe-ai", TypeSafeAiComponent.class).getConfiguration().setApiPath(apiPath);

        assertThatThrownBy(() -> context.getEndpoint("typesafe-ai:invalid")).hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(paths).isEmpty();
    }
}
