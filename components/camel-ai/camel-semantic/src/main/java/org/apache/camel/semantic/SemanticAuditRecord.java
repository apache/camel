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

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable, size-bounded snapshot. It never contains the exchange, input or provider exception. Schema version 1 uses
 * UTC timestamps and keeps route actions separate from expert results.
 */
public final class SemanticAuditRecord {
    private final Map<String, Object> fields;

    SemanticAuditRecord(Map<String, Object> fields) {
        this.fields = Map.copyOf(fields);
    }

    /** Reconstruct a persisted version-1 record; rejects unknown fields and bounds nested data. */
    public static SemanticAuditRecord fromMap(Map<String, ?> data) {
        Set<String> keys = Set.of("schemaVersion", "eventId", "invocationId", "timestamp", "category", "origin", "requestId",
                "batchId", "definition", "target", "expert", "operation", "contextId", "exchangeId", "routeId", "semantics",
                "status", "reasonCode", "durationNanos", "resultOmitted", "result", "action", "namespace", "correlationId",
                "policyId", "policyVersion", "rule", "evidence", "startedAt", "provider", "model", "revision");
        if (!keys.containsAll(data.keySet()) || !(data.get("schemaVersion") instanceof Number version)
                || version.doubleValue() != 1
                || !(data.get("eventId") instanceof String id) || id.isBlank() || id.length() > 256
                || !(data.get("category") instanceof String category)
                || !Set.of("evaluation", "decision", "request").contains(category)
                || !(data.get("timestamp") instanceof String timestamp)) {
            throw new IllegalArgumentException("Invalid semantic audit record");
        }
        Instant.parse(timestamp);
        if (data.containsKey("startedAt")) {
            if (!(data.get("startedAt") instanceof String startedAt)) {
                throw new IllegalArgumentException("Invalid audit start timestamp");
            }
            Instant.parse(startedAt);
        }
        for (String key : List.of("provider", "model", "revision")) {
            if (data.containsKey(key) && (!(data.get(key) instanceof String text) || text.isBlank())) {
                throw new IllegalArgumentException("Invalid audit producer identity");
            }
        }
        if (data.containsKey("evidence") && (!(data.get("evidence") instanceof List<?> ids)
                || ids.size() > 100 || ids.stream().anyMatch(reference -> !(reference instanceof String text)
                        || text.isBlank() || text.length() > 256))) {
            throw new IllegalArgumentException("Invalid audit evidence references");
        }
        validateKeys(data.get("semantics"), Set.of("resultType", "meaning", "probabilityMeaning", "confidenceMeaning"));
        validateKeys(data.get("result"), Set.of("value", "probability", "confidence", "probabilities"));
        Map<String, Object> copy = new LinkedHashMap<>();
        data.forEach((key, value) -> copy.put(key, freeze(value, 0)));
        return new SemanticAuditRecord(copy);
    }

    private static void validateKeys(Object value, Set<String> allowed) {
        if (value != null && (!(value instanceof Map<?, ?> map) || !allowed.containsAll(map.keySet()))) {
            throw new IllegalArgumentException("Invalid audit snapshot fields");
        }
    }

    private static Object freeze(Object value, int depth) {
        if (value instanceof String text) {
            if (text.codePointCount(0, text.length()) > 256 || text.codePoints().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Invalid audit text");
            }
            return text;
        }
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            return value;
        }
        if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
            return number.doubleValue();
        }
        if (depth < 2 && value instanceof List<?> list && list.size() <= 100) {
            return list.stream().map(item -> freeze(item, depth + 1)).toList();
        }
        if (depth < 2 && value instanceof Map<?, ?> map && map.size() <= 100) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                if (!(key instanceof String text)) {
                    throw new IllegalArgumentException("Invalid audit key");
                }
                copy.put((String) freeze(text, depth + 1), freeze(item, depth + 1));
            });
            return Collections.unmodifiableMap(copy);
        }
        throw new IllegalArgumentException("Invalid audit value");
    }

    public String getEventId() {
        return text("eventId");
    }

    public String getInvocationId() {
        return text("invocationId");
    }

    public String getCategory() {
        return text("category");
    }

    public String getExpert() {
        return text("expert");
    }

    public String getStatus() {
        return text("status");
    }

    public String text(String name) {
        return fields.get(name) instanceof String value ? value : null;
    }

    public Map<String, Object> toMap() {
        return fields;
    }
}
