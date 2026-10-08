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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.util.StringHelper;

/** Fluent definition of an expert-owned evaluation. All DSLs build the same immutable declaration. */
public final class SemanticEvaluationBuilder {
    private final SemanticEvaluationsBuilder parent;
    private final Map<String, Object> parameters = new LinkedHashMap<>();
    private final Map<String, String> criteria = new LinkedHashMap<>();
    private final List<String> levels = new ArrayList<>();
    private String threshold;
    private String uncertainty;
    private boolean normalizeUncertaintyPolicy;
    private String operation;
    private String expert;
    private String state;

    /** Create a standalone declaration, completed with {@link #build(CamelContext)}. */
    public SemanticEvaluationBuilder() {
        this(null);
    }

    SemanticEvaluationBuilder(SemanticEvaluationsBuilder parent) {
        this.parent = parent;
    }

    public SemanticEvaluationBuilder operation(String operation) {
        this.operation = operation;
        return this;
    }

    /** Select an instruction-driven operation by type, such as boolean, choice or score. */
    public SemanticEvaluationBuilder type(String type) {
        return operation(type.toLowerCase(Locale.ROOT));
    }

    public SemanticEvaluationBuilder expert(String expert) {
        this.expert = expert;
        return this;
    }

    String getExpert() {
        return expert != null ? expert : parent != null ? parent.getExpert() : null;
    }

    public SemanticEvaluationBuilder state(String state) {
        this.state = state;
        return this;
    }

    public SemanticEvaluationBuilder parameter(String name, Object value) {
        if (parameters.containsKey(name)) {
            throw new IllegalArgumentException("Duplicate semantic parameter: " + name);
        }
        parameters.put(name, value);
        return this;
    }

    public SemanticEvaluationBuilder parameters(Map<String, ?> values) {
        values.forEach(this::parameter);
        return this;
    }

    public SemanticEvaluationBuilder instructions(String instructions) {
        return parameter("instructions", instructions);
    }

    public SemanticEvaluationBuilder criterion(String name, String description) {
        if (criteria.containsKey(name)) {
            throw new IllegalArgumentException("Duplicate semantic criterion: " + name);
        }
        criteria.put(name, description);
        return this;
    }

    public SemanticEvaluationBuilder level(String level) {
        levels.add(level);
        return this;
    }

    public SemanticEvaluationBuilder threshold(double threshold) {
        return parameter("threshold", threshold);
    }

    public SemanticEvaluationBuilder threshold(String threshold) {
        this.threshold = threshold;
        return this;
    }

    public SemanticEvaluationBuilder uncertainty(double uncertainty) {
        return parameter("uncertainty", uncertainty);
    }

    public SemanticEvaluationBuilder uncertainty(String uncertainty) {
        this.uncertainty = uncertainty;
        return this;
    }

    public SemanticEvaluationBuilder uncertaintyPolicy(String policy) {
        parameter("uncertaintyPolicy", policy);
        normalizeUncertaintyPolicy = true;
        return this;
    }

    public SemanticEvaluationsBuilder end() {
        if (parent == null) {
            throw new IllegalStateException("Complete a standalone declaration with build(context)");
        }
        return parent;
    }

    public void register() {
        end().register();
    }

    /** Build an immutable declaration, resolving placeholders in parameter values while retaining their types. */
    public SemanticEvaluation build(CamelContext context) {
        Map<String, Object> values = new LinkedHashMap<>(parameters);
        if (!criteria.isEmpty() || !levels.isEmpty()) {
            if (values.containsKey("criteria") || !criteria.isEmpty() && !levels.isEmpty()) {
                throw new IllegalArgumentException("Duplicate parameter 'criteria'");
            }
            values.put("criteria", !criteria.isEmpty() ? criteria : levels);
        }
        for (String numeric : List.of("threshold", "uncertainty")) {
            String text = numeric.equals("threshold") ? threshold : uncertainty;
            if (text != null) {
                if (values.containsKey(numeric)) {
                    throw new IllegalArgumentException("Duplicate parameter '" + numeric + "'");
                }
                try {
                    values.put(numeric, Double.valueOf(context.resolvePropertyPlaceholders(text)));
                } catch (NumberFormatException invalid) {
                    throw new IllegalArgumentException("Parameter '" + numeric + "' must be a valid number");
                }
            }
        }
        values.replaceAll((name, value) -> resolve(context, value));
        if (normalizeUncertaintyPolicy && values.get("uncertaintyPolicy") instanceof String policy) {
            values.put("uncertaintyPolicy",
                    StringHelper.asEnumConstantValue(policy).toLowerCase(Locale.ROOT).replace('_', '-'));
        }
        return new SemanticEvaluation(
                operation, getExpert(), state != null ? state : parent != null ? parent.getState() : null, values);
    }

    private static Object resolve(CamelContext context, Object value) {
        if (value instanceof String text) {
            return context.resolvePropertyPlaceholders(text);
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> resolved = new LinkedHashMap<>();
            map.forEach((key, item) -> resolved.put(key, resolve(context, item)));
            return resolved;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> resolve(context, item)).toList();
        }
        return value;
    }
}
