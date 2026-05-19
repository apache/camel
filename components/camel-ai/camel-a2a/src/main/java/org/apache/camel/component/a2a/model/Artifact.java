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
 * A2A protocol artifact.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Artifact(
        String artifactId,
        String name,
        String description,
        List<Part<?>> parts,
        Map<String, Object> metadata,
        List<String> extensions) {

    public Artifact {
        parts = parts != null ? List.copyOf(parts) : null;
        metadata = metadata != null ? Map.copyOf(metadata) : null;
        extensions = extensions != null ? List.copyOf(extensions) : null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(Artifact artifact) {
        return new Builder()
                .artifactId(artifact.artifactId)
                .name(artifact.name)
                .description(artifact.description)
                .parts(artifact.parts != null ? new ArrayList<>(artifact.parts) : null)
                .metadata(artifact.metadata != null ? new HashMap<>(artifact.metadata) : null)
                .extensions(artifact.extensions != null ? new ArrayList<>(artifact.extensions) : null);
    }

    public static class Builder {
        private String artifactId;
        private String name;
        private String description;
        private List<Part<?>> parts;
        private Map<String, Object> metadata;
        private List<String> extensions;

        public Builder artifactId(String artifactId) {
            this.artifactId = artifactId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder parts(List<Part<?>> parts) {
            this.parts = parts;
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

        public Artifact build() {
            return new Artifact(artifactId, name, description, parts, metadata, extensions);
        }
    }
}
