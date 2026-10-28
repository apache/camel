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

package org.apache.camel.dsl.jbang.core.common;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exact field=value filters, including quoted values and escaped whitespace/quotes. */
public final class SemanticAuditFilters {
    public static final Set<String> QUERY_FIELDS
            = Set.of("category", "expert", "routeId", "executingRouteId", "exchangeId", "breadcrumbId", "since", "until",
                    "definition", "operation", "resultType", "resultValue", "resultLabel", "scoreMin", "scoreMax");

    private SemanticAuditFilters() {
    }

    public static Map<String, String> parse(String input, Set<String> fields) {
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        for (char ch : input.toCharArray()) {
            if (escaped) {
                token.append(ch);
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                } else {
                    token.append(ch);
                }
            } else if (ch == '\'' || ch == '"') {
                quote = ch;
            } else if (Character.isWhitespace(ch)) {
                if (!token.isEmpty()) {
                    tokens.add(token.toString());
                    token.setLength(0);
                }
            } else {
                token.append(ch);
            }
        }
        if (escaped || quote != 0) {
            throw new IllegalArgumentException("Close the quoted filter or complete the escape");
        }
        if (!token.isEmpty()) {
            tokens.add(token.toString());
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String value : tokens) {
            int separator = value.indexOf('=');
            if (separator < 1 || !fields.contains(value.substring(0, separator))) {
                throw new IllegalArgumentException("Use supported field=value filters");
            }
            String name = value.substring(0, separator);
            String selected = value.substring(separator + 1);
            if (selected.isBlank() || selected.length() > 256 || result.putIfAbsent(name, selected) != null) {
                throw new IllegalArgumentException(
                        "Filter values must be nonblank, at most 256 characters, and specified once");
            }
        }
        return Map.copyOf(result);
    }

    public static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** Validate query syntax before sending to the runtime, which also enforces reader capabilities and semantics. */
    public static void validateQuery(Map<String, ?> filters) {
        Instant since = instant(filters.get("since"));
        Instant until = instant(filters.get("until"));
        if (since != null && until != null && !since.isBefore(until)) {
            throw new IllegalArgumentException("since must be before until");
        }
        String type = (String) filters.get("resultType");
        String value = (String) filters.get("resultValue");
        if (type != null && !Set.of("BOOLEAN", "CHOICE", "SCORE", "CLASSIFICATION").contains(type)
                || type == null && Set.of("resultValue", "resultLabel", "scoreMin", "scoreMax").stream()
                        .anyMatch(filters::containsKey)
                || filters.containsKey("resultLabel") && !"CLASSIFICATION".equals(type)
                || value != null && "CLASSIFICATION".equals(type)
                || (filters.containsKey("scoreMin") || filters.containsKey("scoreMax")) && !"SCORE".equals(type)) {
            throw new IllegalArgumentException("Result filters require a compatible explicit resultType");
        }
        if (value != null && "BOOLEAN".equals(type) && !Set.of("true", "false").contains(value)) {
            throw new IllegalArgumentException("BOOLEAN resultValue must be true or false");
        }
        if ("SCORE".equals(type)) {
            decimal(value);
            BigDecimal min = decimal((String) filters.get("scoreMin"));
            BigDecimal max = decimal((String) filters.get("scoreMax"));
            if (min != null && max != null && min.compareTo(max) > 0) {
                throw new IllegalArgumentException("scoreMin must not exceed scoreMax");
            }
        }
    }

    private static Instant instant(Object value) {
        return value == null ? null : Instant.parse(value.toString());
    }

    private static BigDecimal decimal(String value) {
        if (value == null) {
            return null;
        }
        try {
            BigDecimal number = new BigDecimal(value);
            if (Double.isFinite(number.doubleValue())) {
                return number;
            }
        } catch (NumberFormatException invalid) {
            // Do not expose parser diagnostics or user-supplied values in the usage error.
        }
        throw new IllegalArgumentException("Score filters must be finite numbers");
    }
}
