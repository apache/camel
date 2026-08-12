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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a mock expectation for a moderation request. Contains the expected input text and the moderation verdict
 * to reply with.
 */
public class ModerationExpectation {
    private static final String DEFAULT_MODEL = "camel-moderation";

    private final String expectedInput;
    private final Map<String, Boolean> flaggedCategories = new LinkedHashMap<>();
    private final Map<String, Double> categoryScores = new LinkedHashMap<>();
    private boolean flagged;
    private boolean illicitCategoriesIncluded = true;
    private boolean resultOmitted;
    private String model = DEFAULT_MODEL;

    public ModerationExpectation(String expectedInput) {
        this.expectedInput = expectedInput;
    }

    public String getExpectedInput() {
        return expectedInput;
    }

    public boolean isFlagged() {
        return flagged;
    }

    public void setFlagged(boolean flagged) {
        this.flagged = flagged;
    }

    public Map<String, Boolean> getFlaggedCategories() {
        return flaggedCategories;
    }

    public Map<String, Double> getCategoryScores() {
        return categoryScores;
    }

    /**
     * Marks a category as violated, which also flags the whole result.
     *
     * @param category the OpenAI category name, for example {@code hate} or {@code self-harm/intent}
     * @param score    the confidence score reported for that category
     */
    public void flagCategory(String category, double score) {
        flaggedCategories.put(category, true);
        categoryScores.put(category, score);
        flagged = true;
    }

    /**
     * Reports a score for a category without marking it as violated.
     */
    public void scoreCategory(String category, double score) {
        categoryScores.put(category, score);
    }

    public boolean isResultOmitted() {
        return resultOmitted;
    }

    /**
     * Replies without a result for this input, reproducing a provider that returns fewer verdicts than inputs.
     */
    public void setResultOmitted(boolean resultOmitted) {
        this.resultOmitted = resultOmitted;
    }

    public boolean isIllicitCategoriesIncluded() {
        return illicitCategoriesIncluded;
    }

    /**
     * Controls whether the {@code illicit} and {@code illicit/violent} categories are reported. They are optional in
     * the API model, so this allows reproducing an OpenAI-compatible provider that does not return them.
     */
    public void setIllicitCategoriesIncluded(boolean illicitCategoriesIncluded) {
        this.illicitCategoriesIncluded = illicitCategoriesIncluded;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public boolean matches(String input) {
        return expectedInput.equals(input);
    }

    @Override
    public String toString() {
        return String.format("ModerationExpectation{input='%s', flagged=%b, categories=%s, model='%s'}",
                expectedInput, flagged, flaggedCategories.keySet(), model);
    }
}
