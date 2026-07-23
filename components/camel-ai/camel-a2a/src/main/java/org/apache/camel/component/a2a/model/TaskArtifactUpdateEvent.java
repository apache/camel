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

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A2A SSE task artifact update event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskArtifactUpdateEvent(
        String taskId,
        String contextId,
        Artifact artifact,
        Boolean append,
        Boolean lastChunk,
        Map<String, Object> metadata) {

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(TaskArtifactUpdateEvent event) {
        return new Builder()
                .taskId(event.taskId)
                .contextId(event.contextId)
                .artifact(event.artifact)
                .append(event.append)
                .lastChunk(event.lastChunk)
                .metadata(event.metadata != null ? new HashMap<>(event.metadata) : null);
    }

    public static class Builder {
        private String taskId;
        private String contextId;
        private Artifact artifact;
        private Boolean append;
        private Boolean lastChunk;
        private Map<String, Object> metadata;

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder contextId(String contextId) {
            this.contextId = contextId;
            return this;
        }

        public Builder artifact(Artifact artifact) {
            this.artifact = artifact;
            return this;
        }

        public Builder append(Boolean append) {
            this.append = append;
            return this;
        }

        public Builder lastChunk(Boolean lastChunk) {
            this.lastChunk = lastChunk;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public TaskArtifactUpdateEvent build() {
            return new TaskArtifactUpdateEvent(taskId, contextId, artifact, append, lastChunk, metadata);
        }
    }
}
