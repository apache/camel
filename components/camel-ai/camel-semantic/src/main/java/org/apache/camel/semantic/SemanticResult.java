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
import java.util.Objects;
import java.util.Set;

/**
 * An expert answer after applying its policy. Value is Boolean, a category String, a numeric score, or a set of
 * classification labels. Missing probabilities/confidence remain absent. Metadata may contain provider/model identity,
 * revision and usage. It must not contain credentials or input state.
 */
public final class SemanticResult {
    private final Object value;
    private final Double probability;
    private final Map<String, Double> probabilities;
    private final Double confidence;
    private final Map<String, Object> metadata;

    public SemanticResult(Object value, Double probability, Map<String, Double> probabilities,
                          Double confidence, Map<String, Object> metadata) {
        if (value instanceof Set<?> labels && labels.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Semantic classification labels must not be null");
        }
        this.value = value instanceof Set<?> labels ? Set.copyOf(labels) : value;
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
