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
package org.apache.camel.maven.dsl.yaml.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.squareup.javapoet.AnnotationSpec;
import org.apache.camel.tooling.util.Strings;

import static org.apache.camel.maven.dsl.yaml.GenerateYamlSupportMojo.CN_YAML_PROPERTY;

public final class YamlProperties {
    private YamlProperties() {
    }

    public static AnnotationBuilder annotation(String name, String type) {
        return new AnnotationBuilder().withName(name).withType(type);
    }

    public static class AnnotationBuilder {
        private String name;
        private String displayName;
        private String description;
        private String type;
        private String subType;
        private String format;
        private String defaultValue;
        private boolean required;
        private boolean deprecated;
        private boolean secret;
        private String oneOf;

        public AnnotationBuilder withName(String name) {
            this.name = name;
            return this;
        }

        public AnnotationBuilder withDisplayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public AnnotationBuilder withDisplayName(JsonNode node) {
            if (node == null) {
                return this;
            }
            if (node.isMissingNode()) {
                return this;
            }
            if (!node.isTextual()) {
                return this;
            }

            return withDisplayName(node.asText());
        }

        public AnnotationBuilder withDescription(String description) {
            this.description = description;
            return this;
        }

        public AnnotationBuilder withDescription(JsonNode node) {
            if (node == null) {
                return this;
            }
            if (node.isMissingNode()) {
                return this;
            }
            if (!node.isTextual()) {
                return this;
            }

            return withDescription(node.asText());
        }

        public AnnotationBuilder withType(String type) {
            this.type = type;
            return this;
        }

        public AnnotationBuilder withSubType(String subType) {
            this.subType = subType;
            return this;
        }

        public AnnotationBuilder withFormat(String format) {
            this.format = format;
            return this;
        }

        public AnnotationBuilder withRequired(boolean required) {
            this.required = required;
            return this;
        }

        public AnnotationBuilder withDeprecated(boolean deprecated) {
            this.deprecated = deprecated;
            return this;
        }

        public AnnotationBuilder withDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public AnnotationBuilder withDefaultValue(JsonNode node) {
            if (node == null) {
                return this;
            }
            if (node.isMissingNode()) {
                return this;
            }
            if (!node.isTextual()) {
                return this;
            }

            return withDefaultValue(node.asText());
        }

        public AnnotationBuilder withIsSecret(boolean secret) {
            this.secret = secret;
            return this;
        }

        public AnnotationBuilder withIsSecret(JsonNode node) {
            if (node == null) {
                return this;
            }
            if (node.isMissingNode()) {
                return this;
            }
            if (!node.isTextual()) {
                return this;
            }

            return withIsSecret(node.asBoolean());
        }

        public AnnotationBuilder withOneOf(String oneOf) {
            this.oneOf = oneOf;
            return this;
        }

        public AnnotationSpec build() {
            AnnotationSpec.Builder builder = AnnotationSpec.builder(CN_YAML_PROPERTY);
            builder.addMember("name", "$S", name);

            if (subType == null) {
                builder.addMember("type", "$S", type);
            } else {
                builder.addMember("type", "$S", type + ":" + subType);
            }

            if (required) {
                builder.addMember("required", "$L", required);
            }
            if (deprecated) {
                builder.addMember("deprecated", "$L", deprecated);
            }

            if (!Strings.isNullOrEmpty(format)) {
                builder.addMember("format", "$S", format);
            } else if (secret) {
                builder.addMember("format", "$S", "password");
            }

            if (!Strings.isNullOrEmpty(defaultValue)) {
                builder.addMember("defaultValue", "$S", defaultValue);
            }

            if (!Strings.isNullOrEmpty(description)) {
                builder.addMember("description", "$S", description);
            }
            if (!Strings.isNullOrEmpty(displayName)) {
                builder.addMember("displayName", "$S", displayName);
            }
            if (!Strings.isNullOrEmpty(oneOf)) {
                builder.addMember("oneOf", "$S", oneOf);
            }

            return builder.build();
        }
    }
}
