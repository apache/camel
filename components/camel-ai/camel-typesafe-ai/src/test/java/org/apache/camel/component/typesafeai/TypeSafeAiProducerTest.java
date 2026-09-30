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

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.apache.camel.Exchange;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeSafeAiProducerTest extends TypeSafeAiTestSupport {
    @Test
    void batchesAllPrimitivesAndPreservesApiFields() {
        respond = request -> mixedResponse();
        Object state = Map.of("text", "Refund my payment", "events", List.of("failed", "retried"));
        JsonObject response = template.requestBody("typesafe-ai:decisions", mixedRequest(state), JsonObject.class);

        assertThat(requests).hasSize(1);
        assertThat(authorization).containsExactly("Bearer test-key");
        assertThat(requests.peek()).containsEntry("model", "jev-1.13.0").containsEntry("state", state)
                .containsEntry("questions", mixedQuestions());
        JsonObject choice = (JsonObject) response.path("answers.department");
        JsonObject score = (JsonObject) response.path("answers.urgency");
        assertThat(choice.get("choice")).isEqualTo("billing");
        assertThat(((Number) choice.get("confidence")).doubleValue()).isEqualTo(0.8);
        assertThat(((Number) choice.path("probabilities.billing")).doubleValue()).isEqualTo(0.9);
        assertThat(((Number) score.get("score")).doubleValue()).isEqualTo(1.2);
        assertThat(score.get("legend")).isEqualTo(Map.of("0", "Routine", "1", "Urgent", "2", "Critical"));
        assertThat(((Number) score.path("probabilities.1")).doubleValue()).isEqualTo(0.8);
        assertThat(((Number) response.path("answers.refund.noul")).doubleValue()).isEqualTo(0.9);
        assertThat(((Number) response.path("usage.input_tokens")).longValue()).isEqualTo(100);
        assertThat(((Number) response.path("usage.output_tokens")).longValue()).isEqualTo(20);
        assertThat(response.get("model")).isEqualTo("jev-1.13.0");
    }

    @Test
    void preservesBodyUsingResultPropertyAndOverridesComponentDefaults() {
        Map<String, Object> input = request(List.of("Refund requested", "Payment failed"));
        Exchange exchange = template.request("typesafe-ai:decisions?resultProperty=evaluation&model=jev-preview",
                e -> e.getMessage().setBody(input));
        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage().getBody()).isSameAs(input);
        assertThat(exchange.getProperty("evaluation")).isInstanceOf(JsonObject.class);
        assertThat(requests.peek()).containsEntry("model", "jev-preview");
        assertThat(context.getComponent("typesafe-ai", TypeSafeAiComponent.class).getConfiguration().getModel())
                .isEqualTo("jev-1.13.0");
    }

    @Test
    void requestCanExplicitlyPinItsModel() {
        Map<String, Object> request = new HashMap<>(request("Refund"));
        request.put("model", "jev-1.13.0");
        template.requestBody("typesafe-ai:decisions?model=jev-latest", request);
        assertThat(requests.peek()).containsEntry("model", "jev-1.13.0");
    }

    @Test
    void connectionFailureIsAnError() {
        server.stop(0);
        Exchange exchange
                = template.request("typesafe-ai:decisions?requestTimeout=1000", e -> e.getMessage().setBody(request("hello")));
        assertThat(exchange.getException()).isInstanceOf(IOException.class);
        assertThat(requests).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "requestTimeout=0", "requestTimeout=-1", "maxConcurrentRequests=0", "maxConcurrentRequests=-1",
            "model=", "apiKey=", "baseUrl=ftp://localhost" })
    void invalidConfigurationFailsBeforeEvaluation(String options) {
        assertThatThrownBy(() -> context.getEndpoint("typesafe-ai:invalid?" + options)).isInstanceOf(Exception.class);
        assertThat(requests).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = { 302, 401, 422, 429, 500, 529 })
    void propagatesHttpFailuresWithoutBusinessFallbackOrResponseBody(int code) {
        status = code;
        respond = request -> "private submitted state";
        Exchange exchange = template.request("typesafe-ai:decisions?resultProperty=evaluation", e -> {
            e.setProperty("evaluation", "stale");
            e.getMessage().setBody(request("hello"));
        });
        assertThat(exchange.getException()).isInstanceOf(TypeSafeAiHttpException.class)
                .hasMessageContaining(Integer.toString(code))
                .hasMessageNotContaining("private");
        assertThat(((TypeSafeAiHttpException) exchange.getException()).getRetryAfter()).isEqualTo("2");
        assertThat(exchange.getProperty("evaluation")).isNull();
        assertThat(requests).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void boundsHeadersAndEntireBody(boolean afterHeaders) {
        holdBody = afterHeaders;
        holdHeaders = !afterHeaders;
        long started = System.nanoTime();
        Exchange exchange
                = template.request("typesafe-ai:decisions?requestTimeout=300", e -> e.getMessage().setBody(request("hello")));
        assertThat(exchange.getException()).isInstanceOf(TimeoutException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not json", "null", "[]", "{}",
            "{\"model\":\"jev-1.13.0\",\"usage\":{\"input_tokens\":1,\"output_tokens\":1},\"answers\":{}}" })
    void rejectsMalformedOrIncompleteResponses(String body) {
        respond = request -> body;
        Exchange exchange = template.request("typesafe-ai:decisions", e -> e.getMessage().setBody(request("hello")));
        assertThat(exchange.getException()).isInstanceOf(IOException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"type\":\"noul\"}", "{\"type\":\"noul\",\"noul\":null}",
            "{\"type\":\"noul\",\"noul\":\"0.9\"}", "{\"type\":\"noul\",\"noul\":1.1}",
            "{\"type\":\"noul\",\"noul\":-0.1}", "{\"type\":\"choice\",\"noul\":0.9}" })
    void rejectsInvalidNoulAnswers(String answer) throws Exception {
        respond = request -> "{\"model\":\"jev-1.13.0\",\"usage\":{\"input_tokens\":1,\"output_tokens\":1},\"answers\":{\"predicate\":"
                             + answer + "}}";
        Exchange exchange = template.request("typesafe-ai:decisions", e -> e.getMessage().setBody(request("hello")));
        assertThat(exchange.getException()).isInstanceOf(IOException.class);
    }

    @Test
    void rejectsInvalidChoiceAndScoreMappings() throws Exception {
        for (String field : List.of("choice", "confidence", "probabilities", "score", "legend")) {
            JsonObject invalid = (JsonObject) Jsoner.deserialize(mixedResponse());
            JsonObject answer = (JsonObject) invalid
                    .path(field.equals("score") || field.equals("legend") ? "answers.urgency" : "answers.department");
            answer.remove(field);
            respond = request -> Jsoner.serialize(invalid);
            Exchange exchange = template.request("typesafe-ai:decisions",
                    e -> e.getMessage().setBody(mixedRequest("hello")));
            assertThat(exchange.getException()).as(field).isInstanceOf(IOException.class);
        }
        for (Object choice : List.of("unknown")) {
            JsonObject invalid = (JsonObject) Jsoner.deserialize(mixedResponse());
            ((JsonObject) invalid.path("answers.department")).put("choice", choice);
            respond = request -> Jsoner.serialize(invalid);
            Exchange exchange = template.request("typesafe-ai:decisions",
                    e -> e.getMessage().setBody(mixedRequest("hello")));
            assertThat(exchange.getException()).isInstanceOf(IOException.class);
        }
    }

    @Test
    void rejectsBadInputBeforeCallingService() {
        List<Object> invalid = List.of("not a request map", Map.of("state", "hello"));
        for (Object body : invalid) {
            Exchange exchange = template.request("typesafe-ai:decisions", e -> e.getMessage().setBody(body));
            assertThat(exchange.getException()).isNotNull();
        }
        assertThat(requests).isEmpty();
    }

    @Test
    void rejectsInvalidApiKeyWithoutIncludingItsValueInDiagnostics() {
        TypeSafeAiConfiguration configuration = new TypeSafeAiConfiguration();
        configuration.setApiKey("private-token\r\ninvalid");
        assertThatThrownBy(configuration::validate).isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("private-token");
    }

}
