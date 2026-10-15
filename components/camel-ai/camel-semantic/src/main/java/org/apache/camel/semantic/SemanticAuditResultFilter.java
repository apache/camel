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
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compiled predicates over captured semantics and values; never infer types from display text. */
final class SemanticAuditResultFilter {
    static final Set<String> FIELDS = Set.of("resultType", "resultValue", "resultLabel", "scoreMin", "scoreMax");
    private final String type;
    private final Object value;
    private final String label;
    private final BigDecimal minimum;
    private final BigDecimal maximum;

    SemanticAuditResultFilter(Map<String, String> filters) {
        type = filters.get("resultType");
        String selected = filters.get("resultValue");
        label = filters.get("resultLabel");
        boolean scoreBounds = filters.containsKey("scoreMin") || filters.containsKey("scoreMax");
        if (type == null && FIELDS.stream().anyMatch(filters::containsKey)
                || type != null && !Set.of("BOOLEAN", "CHOICE", "SCORE", "CLASSIFICATION").contains(type)
                || label != null && !"CLASSIFICATION".equals(type)
                || selected != null && "CLASSIFICATION".equals(type)
                || scoreBounds && !"SCORE".equals(type)) {
            throw new IllegalArgumentException("Result filters require a compatible explicit resultType");
        }
        minimum = decimal(filters.get("scoreMin"));
        maximum = decimal(filters.get("scoreMax"));
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("scoreMin must not exceed scoreMax");
        }
        if (selected != null && "BOOLEAN".equals(type)) {
            if (!Set.of("true", "false").contains(selected)) {
                throw new IllegalArgumentException("BOOLEAN resultValue must be true or false");
            }
            value = Boolean.valueOf(selected);
        } else {
            value = "SCORE".equals(type) ? decimal(selected) : selected;
        }
    }

    private static BigDecimal decimal(String text) {
        if (text == null) {
            return null;
        }
        BigDecimal number = new BigDecimal(text);
        if (!Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException("Score filters must be finite numbers");
        }
        return number;
    }

    boolean matches(SemanticAuditRecord record) {
        if (type == null) {
            return true;
        }
        if (!(record.toMap().get("semantics") instanceof Map<?, ?> semantics)
                || !type.equals(semantics.get("resultType"))) {
            return false;
        }
        if (value == null && label == null && minimum == null && maximum == null) {
            return true;
        }
        if (record.toMap().containsKey("resultOmitted") || !(record.toMap().get("result") instanceof Map<?, ?> result)) {
            return false;
        }
        Object actual = result.get("value");
        return switch (type) {
            case "BOOLEAN" -> actual instanceof Boolean && value.equals(actual);
            case "CHOICE" -> actual instanceof String && value.equals(actual);
            case "CLASSIFICATION" -> actual instanceof List<?> labels && labels.contains(label);
            case "SCORE" -> actual instanceof Number number && matchesScore(number);
            default -> false;
        };
    }

    private boolean matchesScore(Number number) {
        if (!Double.isFinite(number.doubleValue())) {
            return false;
        }
        BigDecimal actual = new BigDecimal(number.toString());
        return (value == null || actual.compareTo((BigDecimal) value) == 0)
                && (minimum == null || actual.compareTo(minimum) >= 0)
                && (maximum == null || actual.compareTo(maximum) <= 0);
    }
}
