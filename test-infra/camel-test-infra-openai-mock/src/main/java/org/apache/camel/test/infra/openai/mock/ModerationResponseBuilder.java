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
package org.apache.camel.test.infra.openai.mock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Builder class for creating OpenAI moderation API mock responses.
 */
public class ModerationResponseBuilder {

    /**
     * The categories reported by the OpenAI moderation API. Every category is always present in a real response, so the
     * mock emits all of them and lets the expectation decide which ones are violated.
     */
    private static final List<String> CATEGORIES = List.of(
            "harassment", "harassment/threatening",
            "hate", "hate/threatening",
            "illicit", "illicit/violent",
            "self-harm", "self-harm/instructions", "self-harm/intent",
            "sexual", "sexual/minors",
            "violence", "violence/graphic");

    /**
     * The categories that only the {@code omni-moderation-*} models report.
     */
    private static final List<String> ILLICIT_CATEGORIES = List.of("illicit", "illicit/violent");

    private final ObjectMapper objectMapper;

    public ModerationResponseBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String createModerationResponse(List<ModerationExpectation> expectations, String requestedModel)
            throws Exception {
        String model = requestedModel;
        if (model == null) {
            model = expectations.isEmpty() ? "camel-moderation" : expectations.get(0).getModel();
        }

        List<ModerationResult> results = new ArrayList<>(expectations.size());
        for (ModerationExpectation expectation : expectations) {
            if (expectation.isResultOmitted()) {
                continue;
            }
            results.add(new ModerationResult(
                    expectation.isFlagged(),
                    categories(expectation),
                    categoryScores(expectation),
                    categoryAppliedInputTypes(expectation)));
        }

        return objectMapper.writeValueAsString(new ModerationResponse("modr-camel", model, results));
    }

    private Map<String, Boolean> categories(ModerationExpectation expectation) {
        Map<String, Boolean> categories = new LinkedHashMap<>();
        for (String category : CATEGORIES) {
            if (!expectation.isIllicitCategoriesIncluded() && ILLICIT_CATEGORIES.contains(category)) {
                continue;
            }
            categories.put(category, expectation.getFlaggedCategories().getOrDefault(category, false));
        }
        return categories;
    }

    private Map<String, Double> categoryScores(ModerationExpectation expectation) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (String category : CATEGORIES) {
            scores.put(category, expectation.getCategoryScores().getOrDefault(category, 0.0));
        }
        return scores;
    }

    /**
     * The real API reports which input modality triggered each category. The mock only ever moderates text.
     */
    private Map<String, List<String>> categoryAppliedInputTypes(ModerationExpectation expectation) {
        Map<String, List<String>> appliedInputTypes = new LinkedHashMap<>();
        for (String category : categories(expectation).keySet()) {
            appliedInputTypes.put(category, List.of("text"));
        }
        return appliedInputTypes;
    }

    private record ModerationResponse(
            @JsonProperty("id") String id,
            @JsonProperty("model") String model,
            @JsonProperty("results") List<ModerationResult> results) {
    }

    private record ModerationResult(
            @JsonProperty("flagged") boolean flagged,
            @JsonProperty("categories") Map<String, Boolean> categories,
            @JsonProperty("category_scores") Map<String, Double> categoryScores,
            @JsonProperty("category_applied_input_types") Map<String, List<String>> categoryAppliedInputTypes) {
    }
}
