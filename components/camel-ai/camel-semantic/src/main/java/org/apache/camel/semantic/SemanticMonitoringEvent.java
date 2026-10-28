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
import java.util.Objects;

import org.apache.camel.ai.AiEvent;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/** Supplied producer output, distinct from assessment completion so collectors cannot count their own output. */
public final class SemanticMonitoringEvent implements AiEvent {
    private final SemanticMonitoringSnapshot snapshot;
    private long timestamp = System.currentTimeMillis();

    public SemanticMonitoringEvent(SemanticMonitoringSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot);
    }

    public SemanticMonitoringSnapshot getSnapshot() {
        return snapshot;
    }

    @Override
    public String getProvider() {
        return "semantic";
    }

    @Override
    public Object getSource() {
        return snapshot.getSource();
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
        return Map.of("type", getType().name(), "provider", getProvider(), "timestamp", timestamp,
                "snapshot", snapshot.toMap());
    }

    @Override
    public String toJSon(int indent) {
        String json = new JsonObject(asJSon()).toJson();
        return indent > 0 ? Jsoner.prettyPrint(json) : json;
    }

    @Override
    public String toString() {
        return "SemanticMonitoringEvent[id=" + snapshot.getId() + "]";
    }
}
