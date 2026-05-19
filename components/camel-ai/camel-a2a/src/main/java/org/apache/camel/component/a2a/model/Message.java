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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A2A protocol message.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Message(
        Role role,
        List<Part<?>> parts,
        String messageId,
        String contextId,
        String taskId,
        List<String> referenceTaskIds,
        Map<String, Object> metadata,
        List<String> extensions) {

    public Message {
        parts = parts != null ? List.copyOf(parts) : null;
        referenceTaskIds = referenceTaskIds != null ? List.copyOf(referenceTaskIds) : null;
        metadata = metadata != null ? Map.copyOf(metadata) : null;
        extensions = extensions != null ? List.copyOf(extensions) : null;
    }

    public enum Role {
        ROLE_USER("ROLE_USER"),
        ROLE_AGENT("ROLE_AGENT"),
        ROLE_UNSPECIFIED("ROLE_UNSPECIFIED");

        private final String value;

        Role(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        @JsonCreator
        public static Role fromValue(String value) {
            if (value == null) {
                return ROLE_UNSPECIFIED;
            }
            for (Role r : values()) {
                if (r.value.equalsIgnoreCase(value)) {
                    return r;
                }
            }
            // v0.x compat: accept bare "user"/"agent" from older clients
            if ("user".equalsIgnoreCase(value)) {
                return ROLE_USER;
            }
            if ("agent".equalsIgnoreCase(value)) {
                return ROLE_AGENT;
            }
            return ROLE_UNSPECIFIED;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(Message message) {
        return new Builder()
                .role(message.role)
                .parts(message.parts != null ? new ArrayList<>(message.parts) : null)
                .messageId(message.messageId)
                .contextId(message.contextId)
                .taskId(message.taskId)
                .referenceTaskIds(message.referenceTaskIds != null ? new ArrayList<>(message.referenceTaskIds) : null)
                .metadata(message.metadata != null ? new HashMap<>(message.metadata) : null)
                .extensions(message.extensions != null ? new ArrayList<>(message.extensions) : null);
    }

    public static class Builder {
        private Role role;
        private List<Part<?>> parts;
        private String messageId;
        private String contextId;
        private String taskId;
        private List<String> referenceTaskIds;
        private Map<String, Object> metadata;
        private List<String> extensions;

        public Builder role(Role role) {
            this.role = role;
            return this;
        }

        public Builder parts(List<Part<?>> parts) {
            this.parts = parts;
            return this;
        }

        public Builder messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder contextId(String contextId) {
            this.contextId = contextId;
            return this;
        }

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder referenceTaskIds(List<String> referenceTaskIds) {
            this.referenceTaskIds = referenceTaskIds;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder extensions(List<String> extensions) {
            this.extensions = extensions;
            return this;
        }

        public Message build() {
            return new Message(role, parts, messageId, contextId, taskId, referenceTaskIds, metadata, extensions);
        }
    }
}
