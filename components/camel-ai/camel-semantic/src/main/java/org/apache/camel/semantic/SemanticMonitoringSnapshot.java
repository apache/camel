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
import java.util.Set;

import org.apache.camel.semantic.internal.SemanticAuditInputSnapshot;

/**
 * Immutable current measurements for runtime inspection, not a persistent monitoring report. No captured message input.
 */
public final class SemanticMonitoringSnapshot {
    private static final Set<String> FIELDS = Set.of("id", "source", "name", "meaning", "kind", "unit", "values",
            "dimensions", "epoch", "from", "to", "availability", "denominator", "exclusions", "loss", "revision",
            "taskId", "taskVersion");
    private final Map<String, Object> data;

    @SuppressWarnings("unchecked")
    public SemanticMonitoringSnapshot(Map<String, ?> data) {
        if (!FIELDS.containsAll(data.keySet())) {
            throw new IllegalArgumentException("Unknown monitoring snapshot field");
        }
        for (String field : Set.of("id", "source", "name", "meaning", "kind", "unit")) {
            if (!(data.get(field) instanceof String text) || text.isBlank() || text.length() > 256
                    || text.codePoints().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Monitoring snapshots require bounded descriptions and identity");
            }
        }
        if (data.containsKey("dimensions") && !(data.get("dimensions") instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Dimensions must be a map");
        }
        if (data.get("dimensions") instanceof Map<?, ?> dimensions && (dimensions.size() > 16
                || dimensions.entrySet().stream()
                        .anyMatch(entry -> !(entry.getKey() instanceof String key) || key.length() > 128
                                || !(entry.getValue() instanceof String value) || value.length() > 256))) {
            throw new IllegalArgumentException("Invalid metric dimensions");
        }
        this.data = (Map<String, Object>) SemanticAuditInputSnapshot.copy(data, 32768);
    }

    public String getId() {
        return (String) data.get("id");
    }

    public String getSource() {
        return (String) data.get("source");
    }

    public Map<String, Object> toMap() {
        return data;
    }
}
