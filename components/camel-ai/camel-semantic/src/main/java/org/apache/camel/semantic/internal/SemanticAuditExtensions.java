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
package org.apache.camel.semantic.internal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.semantic.SemanticCapabilities;
import org.apache.camel.semantic.SemanticEvaluation;

/** Full declared event interpretation and bounded projections for audit retention. */
public final class SemanticAuditExtensions {
    public static final int MAX_CHARS = 2048;
    private static final int MAX_ENTRIES = 32;
    private static final Set<String> INTERPRETATION_FIELDS = Set.of(
            "source", "meaning", "probabilityMeaning", "confidenceMeaning", "scoreLevels", "choices", "booleanLabels",
            "noMatchDeclared", "noMatchLabel",
            "probabilitiesComplete", "scoreProbabilityKeys");

    private SemanticAuditExtensions() {
    }

    public static Map<String, Object> capture(SemanticEvaluation evaluation, SemanticCapabilities.Operation operation) {
        if (evaluation == null || operation == null) {
            return Map.of();
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("meaning", operation.getResultMeaning());
        if (!operation.getProbabilityMeaning().isEmpty()) {
            fields.put("probabilityMeaning", operation.getProbabilityMeaning());
        }
        if (!operation.getConfidenceMeaning().isEmpty()) {
            fields.put("confidenceMeaning", operation.getConfidenceMeaning());
        }
        Map<String, Object> parameters = evaluation.getParameters();
        copyParameter(fields, "scoreLevels", parameters, operation.getScoreLevelsParameter());
        copyParameter(fields, "choices", parameters, operation.getChoiceLabelsParameter());
        copyParameter(fields, "booleanLabels", parameters, operation.getBooleanLabelsParameter());
        if (!fields.containsKey("choices") && !operation.getLabels().isEmpty()) {
            Map<String, String> labels = new LinkedHashMap<>();
            operation.getLabels().forEach(label -> labels.put(label, ""));
            fields.put("choices", labels);
        }
        if (fields.containsKey("scoreLevels") && operation.isProbabilities()) {
            fields.put("scoreProbabilityKeys", "zero_based_levels");
        }
        if (operation.isNoMatchDeclared()) {
            fields.put("noMatchDeclared", true);
            if (!operation.getNoMatchLabel().isEmpty()) {
                fields.put("noMatchLabel", operation.getNoMatchLabel());
            }
        }
        if (operation.isCompleteProbabilities()) {
            fields.put("probabilitiesComplete", true);
        }
        if (!fields.isEmpty()) {
            fields.put("source", "expert_contract");
        }
        return freezeInterpretation(fields);
    }

    private static void copyParameter(Map<String, Object> target, String field, Map<String, Object> parameters, String name) {
        if (!name.isEmpty() && parameters.get(name) != null) {
            target.put(field, parameters.get(name));
        }
    }

    /** Validate and freeze the declared interpretation without imposing a consumer's retention budget. */
    public static Map<String, Object> freezeInterpretation(Object value) {
        if (!(value instanceof Map<?, ?> fields) || !INTERPRETATION_FIELDS.containsAll(fields.keySet())) {
            throw new IllegalArgumentException("Invalid interpretation fields");
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        if (fields.containsKey("scoreLevels")) {
            if (!(fields.get("scoreLevels") instanceof List<?> levels) || levels.isEmpty()
                    || levels.stream().anyMatch(label -> !(label instanceof String))) {
                throw new IllegalArgumentException("Invalid score levels");
            }
            copy.put("scoreLevels", List.copyOf(levels));
        }
        for (String name : List.of("choices", "booleanLabels")) {
            if (fields.containsKey(name)) {
                if (!(fields.get(name) instanceof Map<?, ?> labels)) {
                    throw new IllegalArgumentException("Invalid interpretation labels");
                }
                if ("booleanLabels".equals(name) && !Set.of("true", "false").containsAll(labels.keySet())) {
                    throw new IllegalArgumentException("Boolean labels require true and false keys");
                }
                Map<String, String> captured = new LinkedHashMap<>();
                labels.forEach((key, label) -> {
                    if (!(key instanceof String text) || text.isBlank() || !(label instanceof String meaning)) {
                        throw new IllegalArgumentException("Interpretation labels require nonblank keys and text meanings");
                    }
                    captured.put(text, meaning);
                });
                copy.put(name, Collections.unmodifiableMap(captured));
            }
        }
        for (String name : List.of("noMatchDeclared", "probabilitiesComplete")) {
            if (fields.containsKey(name)) {
                if (!(fields.get(name) instanceof Boolean flag)) {
                    throw new IllegalArgumentException("Invalid interpretation declaration");
                }
                copy.put(name, flag);
            }
        }
        for (String name : List.of("source", "meaning", "probabilityMeaning", "confidenceMeaning", "noMatchLabel",
                "scoreProbabilityKeys")) {
            if (fields.containsKey(name)) {
                if (!(fields.get(name) instanceof String text)) {
                    throw new IllegalArgumentException("Invalid interpretation text");
                }
                copy.put(name, text);
            }
        }
        if (fields.containsKey("noMatchLabel") && (!Boolean.TRUE.equals(fields.get("noMatchDeclared"))
                || !(fields.get("choices") instanceof Map<?, ?> choices) || !choices.containsKey(fields.get("noMatchLabel")))) {
            throw new IllegalArgumentException("No-match label must belong to the declared choice domain");
        }
        if (fields.containsKey("scoreProbabilityKeys") && (!"zero_based_levels".equals(fields.get("scoreProbabilityKeys"))
                || !fields.containsKey("scoreLevels"))) {
            throw new IllegalArgumentException("Invalid score probability relationship");
        }
        return Collections.unmodifiableMap(copy);
    }

    /** Apply the audit record budget. An event may contain a larger, complete interpretation. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> interpretation(Object value) {
        Map<String, Object> fields = freezeInterpretation(value);
        for (Object field : fields.values()) {
            if (field instanceof List<?> list) {
                if (list.size() > MAX_ENTRIES) {
                    throw new IllegalArgumentException("Audit interpretation entry limit");
                }
                list.forEach(SemanticAuditExtensions::text);
            } else if (field instanceof Map<?, ?> map) {
                if (map.size() > MAX_ENTRIES) {
                    throw new IllegalArgumentException("Audit interpretation entry limit");
                }
                map.forEach((key, label) -> {
                    text(key);
                    text(label);
                });
            } else if (field instanceof String) {
                text(field);
            }
        }
        return (Map<String, Object>) SemanticAuditInputSnapshot.copy(fields, MAX_CHARS);
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> findings(Object value) {
        if (!(value instanceof List<?> findings) || findings.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid findings");
        }
        for (Object item : findings) {
            if (!(item instanceof Map<?, ?> finding)
                    || !Set.of("code", "meaning", "source", "severity").containsAll(finding.keySet())) {
                throw new IllegalArgumentException("Invalid finding fields");
            }
            for (String name : List.of("code", "meaning", "source")) {
                text(finding.get(name));
            }
            if (finding.containsKey("severity")) {
                if (!(finding.get("severity") instanceof Map<?, ?> severity)
                        || !Set.of("value", "scale", "source").equals(severity.keySet())) {
                    throw new IllegalArgumentException("Invalid severity provenance");
                }
                severity.values().forEach(SemanticAuditExtensions::text);
            }
        }
        return (List<Map<String, Object>>) SemanticAuditInputSnapshot.copy(findings, MAX_CHARS);
    }

    private static void text(Object value) {
        if (!(value instanceof String text) || text.codePointCount(0, text.length()) > 256
                || text.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid or oversized interpretation text");
        }
    }
}
