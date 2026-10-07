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
import java.util.Map;
import java.util.Set;

/** Immutable evaluation declaration. The expert defines the operation, parameter vocabulary and result semantics. */
public final class SemanticEvaluation {
    private static final Set<Class<?>> NUMBER_TYPES = Set.of(Byte.class, Short.class, Integer.class, Long.class,
            Float.class, Double.class, BigInteger.class, BigDecimal.class);

    private final String operation;
    private final String expert;
    private final String state;
    private final Map<String, Object> parameters;

    public SemanticEvaluation(String operation, String expert, String state, Map<String, ?> parameters) {
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

}
