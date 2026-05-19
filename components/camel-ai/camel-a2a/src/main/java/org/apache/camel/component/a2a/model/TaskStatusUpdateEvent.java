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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A2A SSE task status update event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskStatusUpdateEvent(
        String taskId,
        String contextId,
        TaskStatus status,
        Map<String, Object> metadata) {

    @JsonIgnore
    public boolean isFinal() {
        return status != null && status.state() != null && status.state().isTerminal();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(TaskStatusUpdateEvent event) {
        return new Builder()
                .taskId(event.taskId)
                .contextId(event.contextId)
                .status(event.status)
                .metadata(event.metadata != null ? new HashMap<>(event.metadata) : null);
    }

    public static class Builder {
        private String taskId;
        private String contextId;
        private TaskStatus status;
        private Map<String, Object> metadata;

        public Builder taskId(String taskId) {
            this.taskId = taskId;
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

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public TaskStatusUpdateEvent build() {
            return new TaskStatusUpdateEvent(taskId, contextId, status, metadata);
        }
    }
}
