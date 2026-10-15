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
package org.apache.camel.component.openai;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import com.openai.models.decisions.DecisionChoiceOption;
import com.openai.models.decisions.DecisionCreateParams;
import com.openai.models.decisions.DecisionInputImage;
import com.openai.models.decisions.DecisionInputMessage;
import com.openai.models.decisions.DecisionInputPart;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.Exchange;
import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.apache.camel.component.ai.observability.GenAiErrorProperties;
import org.apache.camel.component.ai.observability.GenAiObservabilityProperties;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAIDecisionsHttpTest {
    private HttpServer server;
    private DefaultCamelContext context;
    private OpenAIEndpoint endpoint;
    private final AtomicReference<String> received = new AtomicReference<>();
    private final AtomicReference<String> method = new AtomicReference<>();
    private volatile String response;
    private volatile int status = 200;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/decisions", exchange -> {
            method.set(exchange.getRequestMethod());
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.start();
        context = new DefaultCamelContext();
        OpenAIObservabilityTestSupport.registerRecordingTracer(context);
        endpoint = context.getEndpoint("openai:decisions?apiKey=test&maxRetries=0&baseUrl=http://127.0.0.1:"
                                       + server.getAddress().getPort() + "/v1&model=endpoint-model",
                OpenAIEndpoint.class);
        context.start();
    }

    @AfterEach
    void stop() throws Exception {
        try {
            if (context != null) {
                context.close();
            }
        } finally {
            if (server != null) {
                server.stop(0);
            }
        }
    }

    @Test
    void producerRecordsModelsAndTokenUsage() throws Exception {
        response = """
                {"model":"response-model","answers":[{"type":"predicate","name":"test","probability":0.9}],
                 "usage":{"input_tokens":12,"output_tokens":3,"total_tokens":15}}
                """;
        Exchange result = sendRequest();
        assertThat(result.getException()).isNull();
        var spans = OpenAIObservabilityTestSupport.tracer(context).genAiSpans();
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).tags())
                .containsEntry(GenAiAttributes.OPERATION_NAME, "decisions")
                .containsEntry(GenAiAttributes.SYSTEM, "openai")
                .containsEntry(GenAiAttributes.REQUEST_MODEL, "gpt-6-luna")
                .containsEntry(GenAiAttributes.RESPONSE_MODEL, "response-model")
                .containsEntry(GenAiAttributes.INPUT_TOKENS, "12")
                .containsEntry(GenAiAttributes.OUTPUT_TOKENS, "3")
                .containsEntry(GenAiAttributes.CAMEL_COMPONENT, "openai");
        assertThat(received.get()).contains("gpt-6-luna").doesNotContain("endpoint-model");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", ",\"usage\":null" })
    void producerAllowsAbsentUsage(String usage) throws Exception {
        response = """
                {"model":"gpt-6-luna","answers":[{"type":"predicate","name":"test","probability":0.9}]%s}
                """.formatted(usage);
        assertThat(sendRequest().getException()).isNull();
        var spans = OpenAIObservabilityTestSupport.tracer(context).genAiSpans();
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).tags())
                .containsEntry(GenAiAttributes.RESPONSE_MODEL, "gpt-6-luna")
                .doesNotContainKeys(GenAiAttributes.INPUT_TOKENS, GenAiAttributes.OUTPUT_TOKENS);
    }

    @Test
    void producerRecordsAndClosesFailedObservation() throws Exception {
        status = 503;
        response = "{\"error\":{\"message\":\"unavailable\"}}";
        Exchange result = sendRequest();
        assertThat(result.getException()).isInstanceOf(OpenAIDecisionServiceException.class);
        assertThat(result.getProperty(GenAiErrorProperties.ERROR_CATEGORY)).isEqualTo("SERVER_ERROR");
        var spans = OpenAIObservabilityTestSupport.tracer(context).genAiSpans();
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).tags())
                .containsEntry(GenAiAttributes.ERROR_TYPE, "OpenAIDecisionServiceException");
    }

    @Test
    void producerRespectsObservabilityToggle() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(GenAiObservabilityProperties.ENABLED, "false");
        context.getPropertiesComponent().setOverrideProperties(properties);
        response = """
                {"model":"gpt-6-luna","answers":[{"type":"predicate","name":"test","probability":0.9}]}
                """;
        assertThat(sendRequest().getException()).isNull();
        assertThat(OpenAIObservabilityTestSupport.tracer(context).genAiSpans()).isEmpty();
    }

    private Exchange sendRequest() throws Exception {
        try (var template = context.createProducerTemplate()) {
            return template.request(endpoint.getEndpointUri(), exchange -> exchange.getMessage().setBody(request()));
        }
    }

    @Test
    void serializesNativeImageAndDeserializesPredicate() throws Exception {
        response = """
                {"model":"gpt-6-luna","answers":[{"type":"predicate","name":"test","probability":0.96}]}
                """;
        var image = DecisionInputPart.ofInputImage(DecisionInputImage.builder()
                .imageUrl("data:image/png;base64,AA==").build());
        var request = request().toBuilder().inputOfDecisionInputMessages(List.of(
                DecisionInputMessage.builder().contentOfParts(List.of(image)).build())).build();
        assertThat(endpoint.decide(request).answers().get(0).asPredicate().probability()).isEqualTo(0.96);
        assertThat(method.get()).isEqualTo("POST");
        assertThat(received.get()).contains("input_image", "data:image/png;base64,AA==", "predicate");
    }

    @Test
    void absentOptionalChoiceUncertaintyRemainsAbsent() throws Exception {
        response = """
                {"model":"gpt-6-luna","answers":[{"type":"choice","name":"test","choice":"a"}]}
                """;
        var request = DecisionCreateParams.builder().model("gpt-6-luna").input("Evidence")
                .addQuestion(DecisionCreateParams.Question.Choice.builder().name("test").instructions("Choose")
                        .addChoice(DecisionChoiceOption.builder().value("a").build())
                        .addChoice(DecisionChoiceOption.builder().value("b").build()).build())
                .build();
        var answer = endpoint.decide(request).answers().get(0).asChoice();
        assertThat(answer._confidence().isMissing()).isTrue();
        assertThat(answer._probabilities().isMissing()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "probability", "model", "usage" })
    void malformedResponsesHaveDedicatedException(String field) {
        response = """
                {"model":"gpt-6-luna","answers":[{"type":"predicate","name":"test","probability":0.9}]}
                """;
        response = switch (field) {
            case "probability" -> response.replace("0.9", "\"invalid\"");
            case "model" -> response.replace("\"model\":\"gpt-6-luna\",", "");
            default -> response.replace("}]}", "}],\"usage\":{\"input_tokens\":\"invalid\"}}");
        };
        assertThatThrownBy(() -> endpoint.decide(request())).isInstanceOf(OpenAIDecisionResponseException.class);
    }

    @Test
    void serviceFailuresHaveDedicatedException() {
        status = 503;
        response = "{\"error\":{\"message\":\"unavailable\"}}";
        assertThatThrownBy(() -> endpoint.decide(request())).isInstanceOf(OpenAIDecisionServiceException.class);
    }

    private static DecisionCreateParams request() {
        return DecisionCreateParams.builder().model("gpt-6-luna").input("Evidence")
                .addQuestion(DecisionCreateParams.Question.Predicate.builder()
                        .name("test").instructions("Is it relevant?").build())
                .build();
    }
}
