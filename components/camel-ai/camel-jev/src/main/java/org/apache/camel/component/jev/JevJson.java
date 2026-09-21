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
package org.apache.camel.component.jev;

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
final class JevJson {
    // Allow small rounding differences in the service's probability distributions and weighted scores.
    private static final double ROUNDING_TOLERANCE = 0.001;

    private JevJson() {
    }

    static JsonObject request(Map<String, Object> input, String model) throws IOException {
        Map<String, Object> request = new LinkedHashMap<>(input);
        request.putIfAbsent("model", model);
        text(request.get("model"));
        content(request.get("state"));
        Map<?, ?> questions = object(request.get("questions"));
        require(!questions.isEmpty(), "questions must not be empty");
        questions.forEach((key, value) -> {
            text(key);
            question(object(value));
        });
        jsonValue(request);
        // Detach nested criteria so later caller mutations cannot change response validation.
        return parse(Jsoner.serialize(request));
    }

    static String noulQuestion(Map<String, Object> question) {
        question(question);
        require("noul".equals(question.get("type")), "A Jev predicate requires a Noul question");
        jsonValue(question);
        return Jsoner.serialize(question);
    }

    private static void question(Map<?, ?> question) {
        content(question.get("instructions"));
        Object type = question.get("type");
        if ("noul".equals(type)) {
            if (question.containsKey("criteria")) {
                object(question.get("criteria")).forEach((key, value) -> {
                    require("true".equals(key) || "false".equals(key), "Noul criteria keys must be true or false");
                    content(value);
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
            require(criteria.size() >= 2 && criteria.size() <= 10, "Score requires 2 to 10 levels");
            criteria.forEach(JevJson::content);
        } else {
            throw new IllegalArgumentException("Question type must be noul, choice or score");
        }
    }

    static JsonObject response(String body, JsonObject request) throws IOException {
        JsonObject response = parse(body);
        try {
            text(response.get("model"));
            Map<?, ?> usage = object(response.get("usage"));
            tokens(usage.get("input_tokens"));
            tokens(usage.get("output_tokens"));
            Map<?, ?> answers = object(response.get("answers"));
            Map<?, ?> questions = object(request.get("questions"));
            require(answers.keySet().equals(questions.keySet()), "Answer names must match question names");
            questions.forEach((key, value) -> answer(object(answers.get(key)), object(value)));
            return response;
        } catch (IllegalArgumentException e) {
            // No response fragments in exceptions: the service may echo private submitted state.
            throw new IOException("Invalid Jev response: " + e.getMessage());
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
        probabilities.values().forEach(JevJson::probability);
        double sum = probabilities.values().stream().mapToDouble(value -> ((Number) value).doubleValue()).sum();
        require(Math.abs(sum - 1) <= ROUNDING_TOLERANCE, "Probabilities must sum to one");
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
            double weighted = IntStream.range(0, criteria.size())
                    .mapToDouble(index -> index * number(probabilities.get(Integer.toString(index)))).sum();
            require(Math.abs(score - weighted) <= ROUNDING_TOLERANCE, "Score must match its probability-weighted levels");
            Map<?, ?> legend = object(answer.get("legend"));
            require(legend.keySet().equals(levels), "Score legend must cover the requested levels");
            legend.values().forEach(value -> require(value instanceof String, "Score legend descriptions must be strings"));
        }
    }

    static JsonObject parse(String json) throws IOException {
        try {
            Object parsed = Jsoner.deserialize(json);
            if (parsed instanceof JsonObject object) {
                return object;
            }
        } catch (DeserializationException e) {
            // Do not expose parser diagnostics containing response data.
        }
        throw new IOException("Invalid Jev response: expected a JSON object");
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
            list.forEach(JevJson::jsonValue);
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
        double tokens = number(value);
        require(tokens >= 0 && tokens == Math.rint(tokens), "Token usage must be a nonnegative integer");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
