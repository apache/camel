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
package org.apache.camel.semantic;

import java.util.Map;

/**
 * A provider answer. Value is Boolean, a category String, or a numeric score. A boolean provider may supply only
 * probability; the question then defines the decision policy. Missing probabilities/confidence remain absent. Metadata
 * may contain provider/model identity, revision and usage. It must not contain credentials or input state.
 */
public final class SemanticResult {
    private final Object value;
    private final Double probability;
    private final Map<String, Double> probabilities;
    private final Double confidence;
    private final Map<String, Object> metadata;

    public SemanticResult(Object value, Double probability, Map<String, Double> probabilities,
                          Double confidence, Map<String, Object> metadata) {
        this.value = value;
        this.probability = probability;
        this.probabilities = probabilities == null ? Map.of() : Map.copyOf(probabilities);
        this.confidence = confidence;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        checkProbability(probability);
        checkProbability(confidence);
        this.probabilities.values().forEach(SemanticResult::checkProbability);
    }

    private static void checkProbability(Double value) {
        if (value != null && (!Double.isFinite(value) || value < 0 || value > 1)) {
            throw new IllegalArgumentException("Semantic probabilities and confidence must be within [0,1]");
        }
    }

    public Object decision(SemanticQuestion question) {
        switch (question.getType()) {
            case BOOLEAN:
                if (probability != null) {
                    double threshold = question.getThreshold();
                    double uncertainty = question.getUncertainty();
                    if (uncertainty > 0 && probability >= threshold - uncertainty && probability <= threshold + uncertainty) {
                        if (question.getUncertaintyPolicy() == SemanticQuestion.UncertaintyPolicy.FAIL) {
                            throw new IllegalStateException("Semantic boolean decision is uncertain");
                        }
                        return false;
                    }
                    return probability >= threshold;
                }
                if (value instanceof Boolean && question.getUncertainty() == 0 && question.getThreshold() == 0.5) {
                    return value;
                }
                break;
            case CHOICE:
                if (value instanceof String && question.getCriteria().containsKey(value)) {
                    if (!probabilities.isEmpty() && !probabilities.keySet().equals(question.getCriteria().keySet())) {
                        throw new IllegalArgumentException("Semantic choice probabilities must cover every criterion");
                    }
                    return value;
                }
                break;
            case SCORE:
                if (value instanceof Number number && Double.isFinite(number.doubleValue()) && number.doubleValue() >= 0
                        && number.doubleValue() <= question.getLevels().size() - 1) {
                    return number.doubleValue();
                }
                break;
            default:
                break;
        }
        throw new IllegalArgumentException("Semantic result does not support the question and its decision policy");
    }

    public Object getValue() {
        return value;
    }

    public Double getProbability() {
        return probability;
    }

    public Map<String, Double> getProbabilities() {
        return probabilities;
    }

    public Double getConfidence() {
        return confidence;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
