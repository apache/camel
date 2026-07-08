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
package org.apache.camel.component.a2a.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A2A protocol task.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Task(
        String id,
        String contextId,
        TaskStatus status,
        List<Artifact> artifacts,
        List<Message> history,
        Map<String, Object> metadata) {

    public Task {
        artifacts = artifacts != null ? List.copyOf(artifacts) : null;
        history = history != null ? List.copyOf(history) : null;
        metadata = metadata != null ? Map.copyOf(metadata) : null;
    }

    /**
     * Convenience accessor for Camel OGNL: ${body.latest.parts[0].text}
     */
    public Message latest() {
        return (history != null && !history.isEmpty()) ? history.get(history.size() - 1) : null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(Task task) {
        return new Builder()
                .id(task.id)
                .contextId(task.contextId)
                .status(task.status)
                .artifacts(task.artifacts != null ? new ArrayList<>(task.artifacts) : null)
                .history(task.history != null ? new ArrayList<>(task.history) : null)
                .metadata(task.metadata != null ? new HashMap<>(task.metadata) : null);
    }

    public static class Builder {
        private String id;
        private String contextId;
        private TaskStatus status;
        private List<Artifact> artifacts;
        private List<Message> history;
        private Map<String, Object> metadata;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder contextId(String contextId) {
            this.contextId = contextId;
            return this;
        }

        public Builder status(TaskStatus status) {
            this.status = status;
            return this;
        }

        public Builder artifacts(List<Artifact> artifacts) {
            this.artifacts = artifacts;
            return this;
        }

        public Builder history(List<Message> history) {
            this.history = history;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Task build() {
            return new Task(id, contextId, status, artifacts, history, metadata);
        }
    }
}
