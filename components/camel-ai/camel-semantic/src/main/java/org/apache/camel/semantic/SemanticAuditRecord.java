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

import org.apache.camel.semantic.internal.SemanticAuditInputSnapshot;

/**
 * Immutable, size-bounded snapshot. It never contains the exchange or provider exception. Input is opt-in. Schema
 * version 1 uses UTC timestamps and keeps route actions separate from expert results.
 */
public final class SemanticAuditRecord {
    private final Map<String, Object> fields;
    private final Instant timestamp;

    SemanticAuditRecord(Map<String, Object> fields) {
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        this.timestamp = Instant.parse((String) fields.get("timestamp"));
    }

    /** Create or restore a version-1 record; rejects unknown fields and defensively copies bounded nested data. */
    public static SemanticAuditRecord fromMap(Map<String, ?> data) {
        Set<String> keys = Set.of("schemaVersion", "eventId", "invocationId", "timestamp", "category", "origin", "requestId",
                "batchId", "definition", "target", "expert", "operation", "contextId", "exchangeId", "routeId", "semantics",
                "status", "reasonCode", "durationNanos", "resultOmitted", "result", "action", "namespace", "correlationId",
                "policyId", "policyVersion", "rule", "evidence", "startedAt", "provider", "model", "revision", "input",
                "inputOmitted", "inputRedacted", "breadcrumbId");
        if (!keys.containsAll(data.keySet()) || !(data.get("schemaVersion") instanceof Number version)
                || version.doubleValue() != 1
                || !(data.get("eventId") instanceof String id) || id.isBlank() || id.length() > 256
                || !(data.get("category") instanceof String category)
                || !Set.of("evaluation", "decision", "request").contains(category)
                || !(data.get("timestamp") instanceof String timestamp)) {
            throw new IllegalArgumentException("Invalid semantic audit record");
        }
        Set<String> structured
                = Set.of("schemaVersion", "durationNanos", "semantics", "result", "evidence", "input", "inputRedacted");
        data.forEach((key, value) -> {
            if (!structured.contains(key) && !(value instanceof String)) {
                throw new IllegalArgumentException("Invalid audit text field: " + key);
            }
        });
        if (data.containsKey("durationNanos") && (!(data.get("durationNanos") instanceof Number duration)
                || duration.longValue() < 0 || duration.doubleValue() != duration.longValue())) {
            throw new IllegalArgumentException("Invalid audit duration");
        }
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
        if (data.get("semantics") instanceof Map<?, ?> semantics
                && semantics.values().stream().anyMatch(value -> !(value instanceof String))) {
            throw new IllegalArgumentException("Invalid audit semantics");
        }
        if (data.get("result") instanceof Map<?, ?> result) {
            Object value = result.get("value");
            if (value != null && !(value instanceof Boolean || value instanceof Number || value instanceof String
                    || value instanceof List<?> labels && labels.stream().allMatch(String.class::isInstance))) {
                throw new IllegalArgumentException("Invalid audit result value");
            }
            for (String key : List.of("probability", "confidence")) {
                if (result.containsKey(key)) {
                    validateProbability(result.get(key));
                }
            }
            if (result.containsKey("probabilities")) {
                if (!(result.get("probabilities") instanceof Map<?, ?> probabilities)) {
                    throw new IllegalArgumentException("Invalid audit probabilities");
                }
                probabilities.values().forEach(SemanticAuditRecord::validateProbability);
            }
        }
        if (data.containsKey("input") && data.containsKey("inputOmitted")
                || data.containsKey("inputRedacted") && (!(data.get("inputRedacted") instanceof Boolean)
                        || !data.containsKey("input"))
                || !"evaluation".equals(category) && (data.containsKey("input") || data.containsKey("inputOmitted"))) {
            throw new IllegalArgumentException("Invalid audit input fields");
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        data.forEach((key, value) -> copy.put(key, "input".equals(key)
                ? SemanticAuditInputSnapshot.copy(value, SemanticAuditInputConfiguration.MAX_CHARS) : freeze(value, 0)));
        return new SemanticAuditRecord(copy);
    }

    private static void validateProbability(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() < 0 || number.doubleValue() > 1) {
            throw new IllegalArgumentException("Invalid audit probability or confidence");
        }
    }

    /** Completion/event occurrence time, parsed once when this immutable record is created. */
    public Instant getTimestamp() {
        return timestamp;
    }

    public Instant getStartedAt() {
        String started = text("startedAt");
        return started == null ? null : Instant.parse(started);
    }

    public Long getDurationNanos() {
        return fields.get("durationNanos") instanceof Number duration ? duration.longValue() : null;
    }

    public List<String> getEvidence() {
        return fields.get("evidence") instanceof List<?> ids ? ids.stream().map(String.class::cast).toList() : List.of();
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
