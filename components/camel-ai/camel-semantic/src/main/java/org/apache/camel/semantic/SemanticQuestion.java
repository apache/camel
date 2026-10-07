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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Immutable evaluation declaration. The expert defines the operation, parameter vocabulary and result semantics. */
public final class SemanticQuestion {
    private static final Set<Class<?>> NUMBER_TYPES = Set.of(Byte.class, Short.class, Integer.class, Long.class,
            Float.class, Double.class, BigInteger.class, BigDecimal.class);

    public enum Type {
        BOOLEAN,
        CHOICE,
        SCORE,
        CLASSIFICATION
    }

    public enum UncertaintyPolicy {
        FAIL,
        NON_MATCH
    }

    private final String operation;
    private final String expert;
    private final String state;
    private final Map<String, Object> parameters;

    public SemanticQuestion(String operation, String expert, String state, Map<String, ?> parameters) {
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("Evaluation operation is required");
        }
        if (expert != null && expert.isBlank()) {
            throw new IllegalArgumentException("Evaluation expert must not be blank");
        }
        if (state != null && state.isBlank()) {
            throw new IllegalArgumentException("Evaluation state selector must not be blank");
        }
        this.operation = operation;
        this.expert = expert;
        this.state = state;
        this.parameters = immutableMap(parameters == null ? Map.of() : parameters);
    }

    /** Convenience declaration for instruction-driven operations named boolean, choice or score. */
    public SemanticQuestion(Type type, String instructions, String state, Map<String, String> criteria,
                            List<String> levels, double threshold, double uncertainty, UncertaintyPolicy policy) {
        this(type, instructions, state, criteria, levels, threshold, uncertainty, policy, null);
    }

    public SemanticQuestion(Type type, String instructions, String state, Map<String, String> criteria,
                            List<String> levels, double threshold, double uncertainty, UncertaintyPolicy policy,
                            String expert) {
        this(type.name().toLowerCase(Locale.ROOT), expert, state,
             instructionParameters(type, instructions, criteria, levels, threshold, uncertainty, policy));
    }

    private static Map<String, Object> instructionParameters(
            Type type, String instructions, Map<String, String> criteria,
            List<String> levels, double threshold, double uncertainty,
            UncertaintyPolicy policy) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (instructions != null) {
            values.put("instructions", instructions);
        }
        if (criteria != null && !criteria.isEmpty()) {
            values.put("criteria", criteria);
        }
        if (levels != null && !levels.isEmpty()) {
            values.put("criteria", levels);
        }
        if (type == Type.BOOLEAN) {
            values.put("threshold", threshold);
            values.put("uncertainty", uncertainty);
            values.put("uncertaintyPolicy", policy == UncertaintyPolicy.NON_MATCH ? "non-match" : "fail");
        }
        return values;
    }

    static Map<String, Object> immutableMap(Map<String, ?> values) {
        Map<String, Object> copy = new LinkedHashMap<>();
        values.forEach((name, value) -> {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Parameter names must not be blank");
            }
            copy.put(name, immutableValue(value));
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, entry) -> {
                if (!(key instanceof String name)) {
                    throw new IllegalArgumentException("Parameter maps require string keys");
                }
                copy.put(name, entry);
            });
            return immutableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            list.forEach(entry -> copy.add(immutableValue(entry)));
            return Collections.unmodifiableList(copy);
        }
        if (value == null || value instanceof String || value instanceof Boolean
                || NUMBER_TYPES.contains(value.getClass())) {
            return value;
        }
        throw new IllegalArgumentException("Parameters require immutable scalar, map or list values");
    }

    public String getOperation() {
        return operation;
    }

    public String getExpert() {
        return expert;
    }

    public String getState() {
        return state;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * Instruction-driven operation kind; custom operations have their result type in the expert contract.
     *
     * @throws IllegalArgumentException if the operation is not a built-in instruction-driven kind
     */
    public Type getType() {
        return Type.valueOf(operation.toUpperCase(Locale.ROOT));
    }

    public String getInstructions() {
        return (String) parameters.get("instructions");
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getCriteria() {
        return parameters.get("criteria") instanceof Map<?, ?> map ? (Map<String, String>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    public List<String> getLevels() {
        return parameters.get("criteria") instanceof List<?> list ? (List<String>) list : List.of();
    }

    /** Requested threshold, or null when omitted. Defaults belong to the expert. */
    public Double getThreshold() {
        return parameters.get("threshold") instanceof Number number ? number.doubleValue() : null;
    }

    /** Requested uncertainty band, or null when omitted. Defaults belong to the expert. */
    public Double getUncertainty() {
        return parameters.get("uncertainty") instanceof Number number ? number.doubleValue() : null;
    }

    /** Requested instruction-driven policy, or null when omitted. Custom policies remain available in parameters. */
    public UncertaintyPolicy getUncertaintyPolicy() {
        Object policy = parameters.get("uncertaintyPolicy");
        if (policy == null) {
            return null;
        }
        return UncertaintyPolicy.valueOf(policy.toString().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
