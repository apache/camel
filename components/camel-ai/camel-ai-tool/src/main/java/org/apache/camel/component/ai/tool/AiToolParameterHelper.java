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
package org.apache.camel.component.ai.tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared utilities for parsing tool parameter metadata and building JSON Schema. Replaces the duplicated
 * {@code TagsHelper} and {@code parseParameterMetadata()} logic from {@code camel-langchain4j-tools} and
 * {@code camel-spring-ai-tools}.
 *
 * @since 4.22
 */
public final class AiToolParameterHelper {

    private static final Logger LOG = LoggerFactory.getLogger(AiToolParameterHelper.class);

    private AiToolParameterHelper() {
    }

    /**
     * Splits a comma-separated tag list into individual tags.
     */
    public static String[] splitTags(String tagList) {
        if (tagList == null || tagList.isBlank()) {
            return new String[0];
        }
        return Arrays.stream(tagList.trim().split("\\s*,\\s*"))
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    /**
     * Parses a flat parameter map (as received from URI or endpoint config) into structured {@link ParameterDef}
     * objects.
     * <p>
     * Handles entries like:
     * <ul>
     * <li>{@code city=string} — defines parameter type</li>
     * <li>{@code city.description=The city name} — adds description</li>
     * <li>{@code city.required=true} — marks as required</li>
     * <li>{@code unit.enum=celsius,fahrenheit} — defines allowed values</li>
     * </ul>
     */
    public static Map<String, ParameterDef> parseParameterMetadata(Map<String, String> parameters) {
        Map<String, ParameterDef> metadata = new HashMap<>();

        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key.contains(".")) {
                String[] parts = key.split("\\.", 2);
                String paramName = parts[0];
                String propertyName = parts[1];
                ParameterDef def = metadata.computeIfAbsent(paramName, k -> new ParameterDef());

                switch (propertyName) {
                    case "description" -> def.setDescription(value);
                    case "required" -> def.setRequired(Boolean.parseBoolean(value));
                    case "enum" -> def.setEnumValues(
                            Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
                    default -> LOG.warn("Unknown parameter property '{}' for parameter '{}' -- "
                                        + "supported properties are: description, required, enum",
                            propertyName, paramName);
                }
            } else {
                metadata.computeIfAbsent(key, k -> new ParameterDef()).setType(value);
            }
        }

        return metadata;
    }

    /**
     * Builds a JSON Schema object string from the flat parameter map. The result conforms to JSON Schema and is
     * understood by Spring AI ({@code inputSchema}) and OpenAI ({@code function.parameters}).
     */
    public static String buildJsonSchema(Map<String, String> parameters) {
        return buildJsonSchemaFromDefs(parseParameterMetadata(parameters));
    }

    /**
     * Builds a JSON Schema object string from pre-parsed parameter definitions. Use this method when
     * {@link #parseParameterMetadata(Map)} has already been called to avoid re-parsing.
     */
    public static String buildJsonSchemaFromDefs(Map<String, ParameterDef> defs) {
        JsonObject schema = new JsonObject();
        schema.put("type", "object");

        JsonObject properties = new JsonObject();
        List<String> required = new ArrayList<>();

        for (Map.Entry<String, ParameterDef> entry : defs.entrySet()) {
            String name = entry.getKey();
            ParameterDef def = entry.getValue();

            JsonObject prop = new JsonObject();
            prop.put("type", mapType(def.getType()));

            if (def.getDescription() != null) {
                prop.put("description", def.getDescription());
            }
            if (def.getEnumValues() != null && !def.getEnumValues().isEmpty()) {
                JsonArray enumArray = new JsonArray();
                enumArray.addAll(def.getEnumValues());
                prop.put("enum", enumArray);
            }
            if (def.isRequired()) {
                required.add(name);
            }
            properties.put(name, prop);
        }

        schema.put("properties", properties);

        if (!required.isEmpty()) {
            JsonArray requiredArray = new JsonArray();
            requiredArray.addAll(required);
            schema.put("required", requiredArray);
        }

        schema.put("additionalProperties", false);

        return schema.toJson();
    }

    private static String mapType(String type) {
        if (type == null) {
            return "string";
        }
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "integer", "int", "long" -> "integer";
            case "number", "double", "float" -> "number";
            case "boolean", "bool" -> "boolean";
            default -> "string";
        };
    }

    /**
     * Holds structured metadata for a single tool parameter.
     */
    public static class ParameterDef {
        private String type = "string";
        private String description;
        private boolean required;
        private List<String> enumValues;

        public String getType() {
            return type;
        }

        private void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        private void setDescription(String description) {
            this.description = description;
        }

        public boolean isRequired() {
            return required;
        }

        private void setRequired(boolean required) {
            this.required = required;
        }

        public List<String> getEnumValues() {
            return enumValues;
        }

        private void setEnumValues(List<String> enumValues) {
            this.enumValues = enumValues != null ? List.copyOf(enumValues) : null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ParameterDef that = (ParameterDef) o;
            return required == that.required
                    && Objects.equals(type, that.type)
                    && Objects.equals(description, that.description)
                    && Objects.equals(enumValues, that.enumValues);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, description, required, enumValues);
        }

        @Override
        public String toString() {
            return "ParameterDef{type=" + type + ", description=" + description
                   + ", required=" + required + ", enumValues=" + enumValues + '}';
        }
    }
}
