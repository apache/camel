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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/** Validates the documented JSON contract without changing the API's response structure. */
final class TypeSafeAiJson {
    private TypeSafeAiJson() {
    }

    static JsonObject request(Map<String, Object> input, String model) throws IOException {
        Map<String, Object> request = new LinkedHashMap<>(input);
        request.putIfAbsent("model", model);
        text(request.get("model"));
        content(request.get("state"));
        validateQuestions(request.get("questions"));
        jsonValue(request);
        // Detach nested criteria so later caller mutations cannot change response validation.
        return parse(Jsoner.serialize(request));
    }

    static JsonObject questions(String json) throws IOException {
        try {
            JsonObject questions = parse(json);
            validateQuestions(questions);
            return questions;
        } catch (IOException | IllegalArgumentException e) {
            throw new IOException("Invalid questions option: " + e.getMessage());
        }
    }

    private static void validateQuestions(Object value) {
        Map<?, ?> questions = object(value);
        require(!questions.isEmpty(), "questions must not be empty");
        questions.forEach((key, definition) -> {
            text(key);
            question(object(definition));
        });
        jsonValue(questions);
    }

    private static void question(Map<?, ?> question) {
        if (question.get("instructions") != null) {
            content(question.get("instructions"));
        }
        Object type = question.get("type");
        if ("noul".equals(type)) {
            if (question.get("criteria") != null) {
                object(question.get("criteria")).forEach((key, value) -> {
                    require("true".equals(key) || "false".equals(key), "Noul criteria keys must be true or false");
                    if (value != null) {
                        content(value);
                    }
                });
            }
        } else if ("choice".equals(type)) {
            Map<?, ?> criteria = object(question.get("criteria"));
            require(!criteria.isEmpty() && criteria.size() <= 255, "Choice requires 1 to 255 options");
            criteria.forEach((key, value) -> {
                text(key);
                if (value != null) {
                    content(value);
                }
            });
        } else if ("score".equals(type)) {
            require(question.get("criteria") instanceof List<?>, "Score criteria must be a list");
            List<?> criteria = (List<?>) question.get("criteria");
            require(!criteria.isEmpty() && criteria.size() <= 10, "Score requires 1 to 10 levels");
            criteria.forEach(TypeSafeAiJson::content);
        } else {
            throw new IllegalArgumentException("Question type must be noul, choice or score");
        }
    }

    static JsonObject response(String body, JsonObject request) throws IOException {
        try {
            JsonObject response = parse(body);
            text(response.get("model"));
            Map<?, ?> usage = object(response.get("usage"));
            tokens(usage.get("input_tokens"));
            tokens(usage.get("output_tokens"));
            Map<?, ?> answers = object(response.get("answers"));
            Map<?, ?> questions = object(request.get("questions"));
            require(answers.keySet().equals(questions.keySet()), "Answer names must match question names");
            questions.forEach((key, value) -> answer(object(answers.get(key)), object(value)));
            return response;
        } catch (IOException | IllegalArgumentException e) {
            // No response fragments in exceptions: the service may echo private submitted state.
            throw new IOException("Invalid TypeSafe AI response: " + e.getMessage());
        }
    }

    private static void answer(Map<?, ?> answer, Map<?, ?> question) {
        Object type = question.get("type");
        require(type.equals(answer.get("type")), "Answer type must match question type");
        if ("noul".equals(type)) {
            probability(answer.get("noul"));
            return;
        }
        probability(answer.get("confidence"));
        Map<?, ?> probabilities = object(answer.get("probabilities"));
        probabilities.values().forEach(TypeSafeAiJson::probability);
        if ("choice".equals(type)) {
            Set<?> options = object(question.get("criteria")).keySet();
            require(options.contains(answer.get("choice")), "Choice must be one of the requested options");
            require(probabilities.keySet().equals(options), "Choice probabilities must cover the requested options");
            double selected = number(probabilities.get(answer.get("choice")));
            require(probabilities.values().stream().allMatch(value -> number(value) <= selected),
                    "Choice must select a highest-probability option");
        } else {
            List<?> criteria = (List<?>) question.get("criteria");
            double score = number(answer.get("score"));
            require(score >= 0 && score <= criteria.size() - 1, "Score must be within the requested levels");
            Set<String> levels = IntStream.range(0, criteria.size()).mapToObj(Integer::toString).collect(Collectors.toSet());
            require(probabilities.keySet().equals(levels), "Score probabilities must cover the requested levels");
            Map<?, ?> legend = object(answer.get("legend"));
            require(legend.keySet().equals(levels), "Score legend must cover the requested levels");
            legend.values().forEach(TypeSafeAiJson::content);
        }
    }

    static JsonObject parse(String json) throws IOException {
        try {
            Object parsed = Jsoner.deserialize(json);
            if (parsed instanceof JsonObject object) {
                return object;
            }
        } catch (DeserializationException e) {
            // Do not expose parser diagnostics containing submitted or response data.
        }
        throw new IOException("Expected a JSON object");
    }

    private static Map<?, ?> object(Object value) {
        require(value instanceof Map<?, ?>, "Expected an object");
        return (Map<?, ?>) value;
    }

    private static void text(Object value) {
        require(value instanceof String text && !text.isBlank(), "Expected a nonblank string");
    }

    private static void content(Object value) {
        require(value instanceof String || value instanceof Map<?, ?> || value instanceof List<?>,
                "Content must be a string, map or list");
        jsonValue(value);
    }

    private static void jsonValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, child) -> {
                require(key instanceof String, "JSON object keys must be strings");
                jsonValue(child);
            });
        } else if (value instanceof List<?> list) {
            list.forEach(TypeSafeAiJson::jsonValue);
        } else if (value instanceof Number) {
            number(value);
        } else {
            require(value == null || value instanceof String || value instanceof Boolean, "Unsupported JSON value");
        }
    }

    private static double number(Object value) {
        require(value instanceof Number, "Expected a number");
        double number = ((Number) value).doubleValue();
        require(Double.isFinite(number), "Expected a finite number");
        return number;
    }

    private static void probability(Object value) {
        double probability = number(value);
        require(probability >= 0 && probability <= 1, "Probability must be within [0,1]");
    }

    private static void tokens(Object value) {
        if (value == null) {
            return;
        }
        double tokens = number(value);
        require(tokens >= 0 && tokens == Math.rint(tokens), "Token usage must be a nonnegative integer");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
