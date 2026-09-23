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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable question definition. Probabilities and thresholds are provider-specific evidence, not accuracy guarantees.
 */
public final class SemanticQuestion {
    public enum Type {
        BOOLEAN,
        CHOICE,
        SCORE
    }

    public enum UncertaintyPolicy {
        FAIL,
        NON_MATCH
    }

    private final Type type;
    private final String instructions;
    private final String state;
    private final Map<String, String> criteria;
    private final List<String> levels;
    private final double threshold;
    private final double uncertainty;
    private final UncertaintyPolicy uncertaintyPolicy;

    public SemanticQuestion(Type type, String instructions, String state, Map<String, String> criteria,
                            List<String> levels, double threshold, double uncertainty, UncertaintyPolicy uncertaintyPolicy) {
        this.type = Objects.requireNonNull(type, "Question type is required");
        if (instructions == null || instructions.isBlank()) {
            throw new IllegalArgumentException("Question instructions must not be blank");
        }
        if (state != null && state.isBlank()) {
            throw new IllegalArgumentException("Question state selector must not be blank");
        }
        this.instructions = instructions;
        this.state = state;
        this.criteria = criteria == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(criteria));
        this.levels = levels == null ? List.of() : List.copyOf(levels);
        this.criteria.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || value.isBlank()) {
                throw new IllegalArgumentException("Question criteria require nonblank keys and descriptions");
            }
        });
        if (this.levels.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Question score levels must not be blank");
        }
        if (type == Type.CHOICE && this.criteria.isEmpty() || type == Type.SCORE && this.levels.isEmpty()
                || type != Type.SCORE && !this.levels.isEmpty() || type == Type.SCORE && !this.criteria.isEmpty()) {
            throw new IllegalArgumentException(
                    "Choice needs criteria, score needs ordered levels, boolean accepts true/false criteria");
        }
        if (type == Type.BOOLEAN && this.criteria.keySet().stream().anyMatch(k -> !k.equals("true") && !k.equals("false"))) {
            throw new IllegalArgumentException("Boolean criteria keys must be true or false");
        }
        if (!Double.isFinite(threshold) || !Double.isFinite(uncertainty) || uncertainty < 0
                || threshold - uncertainty < 0 || threshold + uncertainty > 1) {
            throw new IllegalArgumentException("Boolean threshold and uncertainty band must be within [0,1]");
        }
        this.threshold = threshold;
        this.uncertainty = uncertainty;
        this.uncertaintyPolicy = Objects.requireNonNull(uncertaintyPolicy, "Uncertainty policy is required");
    }

    public Type getType() {
        return type;
    }

    public String getInstructions() {
        return instructions;
    }

    public String getState() {
        return state;
    }

    public Map<String, String> getCriteria() {
        return criteria;
    }

    public List<String> getLevels() {
        return levels;
    }

    public double getThreshold() {
        return threshold;
    }

    public double getUncertainty() {
        return uncertainty;
    }

    public UncertaintyPolicy getUncertaintyPolicy() {
        return uncertaintyPolicy;
    }
}
