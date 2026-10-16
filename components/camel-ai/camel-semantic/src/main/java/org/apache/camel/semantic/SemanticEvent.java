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
import java.util.Map;
import java.util.Objects;

import org.apache.camel.ai.AiEvent;
import org.apache.camel.semantic.internal.SemanticAuditExtensions;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * A frozen Semantic occurrence shared by audit and event consumers, independently of retention. Interpretation is
 * complete: consumers that retain occurrences must enforce their own storage and queue budgets. Raw input is never
 * included in the frozen data. Route-bound notifications also expose the live Exchange through
 * {@link SemanticExchangeEvent}; retaining consumers must copy the frozen data instead of retaining that notification.
 * Starts and request outcomes are not completed assessments; consumers must select the appropriate kind.
 */
public sealed class SemanticEvent implements AiEvent permits SemanticExchangeEvent {
    public enum Kind {
        EVALUATION_STARTED,
        EVALUATION_COMPLETED,
        REQUEST_OUTCOME
    }

    private final Kind kind;
    private final Map<String, Object> data;
    private long timestamp;

    public SemanticEvent(Kind kind, Map<String, ?> data) {
        this.kind = Objects.requireNonNull(kind);
        String category = kind == Kind.REQUEST_OUTCOME ? "request" : "evaluation";
        if (!category.equals(data.get("category")) || data.containsKey("input")
                || data.containsKey("inputOmitted") || data.containsKey("inputRedacted")
                || data.containsKey("interpretation") && data.containsKey("interpretationOmitted")) {
            throw new IllegalArgumentException("Semantic events require an input-free occurrence of the matching category");
        }
        Map<String, Object> base = new LinkedHashMap<>(data);
        Object interpretation = base.remove("interpretation");
        // Validate the existing occurrence fields independently of the audit interpretation budget.
        Map<String, Object> copy = new LinkedHashMap<>(SemanticAuditRecord.fromMap(base).toMap());
        if (interpretation != null) {
            copy.put("interpretation", SemanticAuditExtensions.freezeInterpretation(interpretation));
        }
        this.data = Collections.unmodifiableMap(copy);
        timestamp = Instant.parse((String) copy.get("timestamp")).toEpochMilli();
    }

    public Kind getKind() {
        return kind;
    }

    /** Immutable occurrence data; retaining consumers must apply their budgets before enqueueing or storing it. */
    public Map<String, Object> getData() {
        return data;
    }

    public String getEventId() {
        return (String) data.get("eventId");
    }

    @Override
    public String getProvider() {
        return "semantic";
    }

    @Override
    public Object getSource() {
        return data.getOrDefault("contextId", "semantic");
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public Map<String, Object> asJSon() {
        return Map.of("type", getType().name(), "provider", getProvider(), "kind", kind.name(),
                "timestamp", timestamp, "data", data);
    }

    @Override
    public String toJSon(int indent) {
        String json = new JsonObject(asJSon()).toJson();
        return indent > 0 ? Jsoner.prettyPrint(json) : json;
    }

    @Override
    public String toString() {
        // The dispatcher logs events on listener failures; payload text belongs only in explicit structured export.
        return "SemanticEvent[" + kind + ", eventId=" + getEventId() + "]";
    }
}
