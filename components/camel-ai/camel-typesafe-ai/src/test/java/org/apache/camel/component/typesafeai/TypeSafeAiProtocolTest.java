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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.camel.Exchange;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class TypeSafeAiProtocolTest extends TypeSafeAiTestSupport {
    @Test
    void preservesStructuredCriteriaNullChoiceDescriptionsAndAdditionalResponseFields() throws Exception {
        Map<String, Object> choices = new HashMap<>();
        choices.put("yes", null);
        choices.put("no", List.of("Anything else"));
        List<Object> levels = List.of(Map.of("description", "Routine"), List.of("Urgent", "Needs action"));
        Map<String, Object> input = Map.of("state", List.of(Map.of("text", "Please help")), "questions", Map.of(
                "team", Map.of("type", "choice", "instructions", List.of("Select a category"), "criteria", choices),
                "urgency", Map.of("type", "score", "instructions", Map.of("question", "How urgent?"), "criteria", levels)));
        JsonObject expected = (JsonObject) Jsoner.deserialize(result(Map.of(
                "team", Map.of("type", "choice", "choice", "yes", "confidence", 0.7,
                        "probabilities", Map.of("yes", 0.9, "no", 0.1)),
                "urgency", Map.of("type", "score", "score", 0.8, "confidence", 0.6,
                        "probabilities", Map.of("0", 0.2, "1", 0.8), "legend",
                        Map.of("0", levels.get(0), "1", levels.get(1))))));
        expected.put("extra_metadata", Map.of("region", "test"));
        respond = request -> Jsoner.serialize(expected);
        JsonObject actual = template.requestBody("typesafe-ai:structured", input, JsonObject.class);
        assertThat(actual).isEqualTo(expected);
        assertThat(requests.peek().path("questions.urgency.criteria")).isEqualTo(levels);
        assertThat(((Map<?, ?>) requests.peek().path("questions.team.criteria")).containsKey("yes")).isTrue();
        assertThat(((Map<?, ?>) requests.peek().path("questions.team.criteria")).get("yes")).isNull();
        assertThat(input).doesNotContainKey("model");
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Map.of("state", "hello"),
                Map.of("state", "hello", "questions", Map.of()),
                Map.of("state", "hello", "questions", Map.of("q", Map.of("type", "unknown", "instructions", "Question?"))),
                Map.of("state", "hello", "questions", Map.of("q", Map.of("type", "noul", "instructions", 42))),
                Map.of("state", "hello", "questions", Map.of("q", noulQuestion("Question?")), "model", " "),
                Map.of("state", "hello", "questions",
                        Map.of("q", Map.of("type", "score", "instructions", "Level?", "criteria", List.of()))),
                Map.of("state", "hello", "questions",
                        Map.of("q", Map.of("type", "choice", "instructions", "Pick?", "criteria", Map.of()))),
                Map.of("state", "hello", "questions",
                        Map.of("q",
                                Map.of("type", "noul", "instructions", "Question?", "criteria", Map.of("maybe", "unknown")))),
                Map.of("state", 1, "questions", Map.of("q", noulQuestion("Question?"))),
                Map.of("state", List.of(new Object()), "questions", Map.of("q", noulQuestion("Question?"))),
                Map.of("state", Map.of(1, "value"), "questions", Map.of("q", noulQuestion("Question?"))),
                Map.of("state", List.of(Double.NaN), "questions", Map.of("q", noulQuestion("Question?"))))
                .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidRequestsBeforeHttp(Map<String, Object> request) {
        Exchange exchange = template.request("typesafe-ai:invalid", e -> e.getMessage().setBody(request));
        assertThat(exchange.getException()).isInstanceOf(IllegalArgumentException.class);
        assertThat(requests).isEmpty();
    }

    static Stream<Arguments> invalidResponses() {
        return Stream.of(
                Arguments.of("answers.department", "choice", "technical"),
                Arguments.of("answers.department", "confidence", 1.1),
                Arguments.of("answers.department", "probabilities", Map.of("billing", 1)),
                Arguments.of("answers.department", "probabilities", Map.of("billing", 1.1, "technical", -0.1, "other", 0)),
                Arguments.of("answers.urgency", "score", 3),
                Arguments.of("answers.urgency", "legend", Map.of("0", "Routine", "1", 3, "2", "Critical")),
                Arguments.of("usage", "input_tokens", -1),
                Arguments.of("usage", "input_tokens", 0.5),
                Arguments.of("usage", "output_tokens", "private response text"));
    }

    @ParameterizedTest
    @MethodSource("invalidResponses")
    void rejectsInconsistentAnswersAndUsage(String path, String field, Object value) throws Exception {
        JsonObject invalid = (JsonObject) Jsoner.deserialize(mixedResponse());
        ((JsonObject) invalid.path(path)).put(field, value);
        respond = request -> Jsoner.serialize(invalid);
        Exchange exchange = template.request("typesafe-ai:invalid", e -> e.getMessage().setBody(mixedRequest("hello")));
        assertThat(exchange.getException()).isInstanceOf(IOException.class).hasMessageNotContaining("private response text");
    }

    @Test
    void preservesRoundedProbabilitiesAndIndependentlyRoundedScore() throws Exception {
        JsonObject expected = (JsonObject) Jsoner.deserialize(mixedResponse());
        ((JsonObject) expected.path("answers.department")).put("probabilities",
                Map.of("billing", 0.33, "technical", 0.33, "other", 0.33));
        JsonObject score = (JsonObject) expected.path("answers.urgency");
        score.put("probabilities", Map.of("0", 0.33, "1", 0.33, "2", 0.34));
        score.put("score", 1.0);
        respond = request -> Jsoner.serialize(expected);

        assertThat(template.requestBody("typesafe-ai:rounded", mixedRequest("hello"), JsonObject.class))
                .isEqualTo(Jsoner.deserialize(Jsoner.serialize(expected)));
    }

    static Stream<Map<String, Object>> optionalTokenCounts() {
        Map<String, Object> nulls = new HashMap<>();
        nulls.put("input_tokens", null);
        nulls.put("output_tokens", null);
        return Stream.of(Map.of(), Map.of("input_tokens", 12), Map.of("output_tokens", 3), nulls);
    }

    @ParameterizedTest
    @MethodSource("optionalTokenCounts")
    void preservesMissingAndNullTokenCounts(Map<String, Object> usage) throws Exception {
        JsonObject expected = (JsonObject) Jsoner.deserialize(mixedResponse());
        expected.put("usage", usage);
        respond = request -> Jsoner.serialize(expected);

        JsonObject actual = template.requestBody("typesafe-ai:usage", mixedRequest("hello"), JsonObject.class);
        assertThat(actual).isEqualTo(Jsoner.deserialize(Jsoner.serialize(expected)));
        assertThat(actual.getJsonObject("usage").keySet()).isEqualTo(usage.keySet());
    }

    static Stream<String> sdkQuestions() {
        return Stream.of(
                "{\"type\":\"noul\"}",
                "{\"type\":\"noul\",\"instructions\":null}",
                "{\"type\":\"noul\",\"criteria\":null}",
                "{\"type\":\"noul\",\"criteria\":{\"true\":null,\"false\":null}}",
                "{\"type\":\"choice\",\"criteria\":{\"a\":null,\"b\":null}}",
                "{\"type\":\"choice\",\"instructions\":null,\"criteria\":{\"a\":null,\"b\":null}}",
                "{\"type\":\"score\",\"criteria\":[\"one\"]}",
                "{\"type\":\"score\",\"instructions\":null,\"criteria\":[\"one\"]}");
    }

    @ParameterizedTest
    @MethodSource("sdkQuestions")
    void acceptsOptionalInstructionsNullNoulCriteriaAndSingleScoreLevel(String json) throws Exception {
        JsonObject question = (JsonObject) Jsoner.deserialize(json);
        Map<String, Object> answer = switch (question.getString("type")) {
            case "noul" -> Map.of("type", "noul", "noul", 0.9);
            case "choice" -> Map.of("type", "choice", "choice", "a", "confidence", 0.9,
                    "probabilities", Map.of("a", 0.9, "b", 0.1));
            default -> Map.of("type", "score", "score", 0, "confidence", 1,
                    "legend", Map.of("0", "one"), "probabilities", Map.of("0", 1));
        };
        respond = request -> result(Map.of("q", answer));

        JsonObject actual = template.requestBody("typesafe-ai:optional",
                Map.of("state", "test", "questions", Map.of("q", question)), JsonObject.class);
        assertThat(actual.path("answers.q.type")).isEqualTo(question.get("type"));
        assertThat(requests.peek().path("questions.q")).isEqualTo(question);
    }
}
