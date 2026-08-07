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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
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

    /**
     * Validates that flat {@code parameter.*} metadata and {@code argSchema} are not both configured.
     */
    public static void validateParameterSourceExclusive(Map<String, String> parameters, String argSchema) {
        boolean hasParameters = parameters != null && !parameters.isEmpty();
        boolean hasArgSchema = argSchema != null && !argSchema.isBlank();
        if (hasParameters && hasArgSchema) {
            throw new IllegalArgumentException(
                    "argSchema and parameter.* are mutually exclusive on ai-tool endpoints");
        }
    }

    /**
     * Validates that flat {@code outputParameter.*} metadata and {@code outputSchema} are not both configured.
     */
    public static void validateOutputSourceExclusive(Map<String, String> outputParameters, String outputSchema) {
        boolean hasOutputParameters = outputParameters != null && !outputParameters.isEmpty();
        boolean hasOutputSchema = outputSchema != null && !outputSchema.isBlank();
        if (hasOutputParameters && hasOutputSchema) {
            throw new IllegalArgumentException(
                    "outputSchema and outputParameter.* are mutually exclusive on ai-tool endpoints");
        }
    }

    /**
     * Resolves, validates, and normalizes a raw JSON Schema for tool input.
     */
    public static String resolveArgSchema(CamelContext camelContext, String argSchema) {
        if (argSchema == null || argSchema.isBlank()) {
            throw new IllegalArgumentException("argSchema must not be blank");
        }

        String resolved = camelContext.resolvePropertyPlaceholders(argSchema);
        String content = resolveResourceContent(camelContext, resolved);
        if (content != null) {
            resolved = content;
        }

        JsonObject root = parseJsonObject(resolved, argSchema);
        validateRootSchemaObject(root);
        return root.toJson();
    }

    /**
     * Resolves and normalizes a raw JSON Schema describing tool output. Unlike
     * {@link #resolveArgSchema(CamelContext, String)}, the schema may describe any JSON type (object, array, string,
     * etc.).
     */
    public static String resolveOutputSchema(CamelContext camelContext, String outputSchema) {
        if (outputSchema == null || outputSchema.isBlank()) {
            throw new IllegalArgumentException("outputSchema must not be blank");
        }

        String resolved = camelContext.resolvePropertyPlaceholders(outputSchema);
        String content = resolveResourceContent(camelContext, resolved, "outputSchema");
        if (content != null) {
            resolved = content;
        }

        JsonObject root = parseJsonObject(resolved, outputSchema, "outputSchema");
        return root.toJson();
    }

    /**
     * Parses a route body into structured JSON content when an output schema is declared.
     */
    public static Object parseStructuredOutput(Object body) {
        if (body == null) {
            throw new IllegalArgumentException(
                    "Route body must not be null when an output schema is declared");
        }
        if (body instanceof Map<?, ?> || body instanceof List<?> || body instanceof Number || body instanceof Boolean) {
            return body;
        }
        if (body instanceof String text) {
            if (text.isBlank()) {
                throw new IllegalArgumentException(
                        "Route body must not be blank when an output schema is declared");
            }
            try {
                return Jsoner.deserialize(text);
            } catch (DeserializationException e) {
                throw new IllegalArgumentException(
                        "Route body must be valid JSON when an output schema is declared", e);
            }
        }
        throw new IllegalArgumentException(
                "Route body must be JSON (String, Map, or List) when an output schema is declared, but was: "
                                           + body.getClass().getSimpleName());
    }

    /**
     * Serializes structured JSON content to a text representation for LLM adapters.
     */
    public static String structuredContentToText(Object structuredContent, Object originalBody) {
        if (structuredContent == null) {
            return "No result";
        }
        if (originalBody instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        if (structuredContent instanceof JsonObject jsonObject) {
            return jsonObject.toJson();
        }
        if (structuredContent instanceof JsonArray jsonArray) {
            return jsonArray.toJson();
        }
        return Jsoner.serialize(structuredContent);
    }

    /**
     * Returns top-level property names declared in a JSON Schema object.
     */
    public static Set<String> extractTopLevelPropertyNames(String jsonSchema) {
        if (jsonSchema == null || jsonSchema.isBlank()) {
            return Set.of();
        }
        JsonObject root = parseJsonObject(jsonSchema, jsonSchema);
        Map<String, Object> properties = requirePropertiesMap(root, jsonSchema);
        if (properties.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(properties.keySet());
    }

    /**
     * Returns top-level required property names declared in a JSON Schema object.
     */
    public static Set<String> extractRequiredPropertyNames(String jsonSchema) {
        if (jsonSchema == null || jsonSchema.isBlank()) {
            return Set.of();
        }
        JsonObject root = parseJsonObject(jsonSchema, jsonSchema);
        Collection<?> required = readRequiredArray(root, jsonSchema);
        if (required.isEmpty()) {
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (Object value : required) {
            if (value != null) {
                names.add(value.toString());
            }
        }
        return Set.copyOf(names);
    }

    private static JsonObject parseJsonObject(String json, String originalValue) {
        return parseJsonObject(json, originalValue, "argSchema");
    }

    private static JsonObject parseJsonObject(String json, String originalValue, String context) {
        try {
            Object parsed = Jsoner.deserialize(json);
            if (parsed == null) {
                throw new IllegalArgumentException(context + " must be a JSON object, but was: null");
            }
            if (!(parsed instanceof JsonObject root)) {
                throw new IllegalArgumentException(
                        context + " must be a JSON object, but was: " + parsed.getClass().getSimpleName());
            }
            return root;
        } catch (DeserializationException e) {
            throw new IllegalArgumentException(
                    context + " does not contain valid JSON. Provided value: " + originalValue, e);
        }
    }

    private static void validateRootSchemaObject(JsonObject root) {
        Object type = root.get("type");
        if (type != null && !"object".equals(type)) {
            throw new IllegalArgumentException(
                    "argSchema root type must be 'object' when specified, but was: " + type);
        }
        Map<String, Object> properties = requirePropertiesMap(root, "argSchema");
        if (properties.isEmpty()) {
            throw new IllegalArgumentException("argSchema must declare at least one top-level property");
        }
        Collection<?> required = readRequiredArray(root, "argSchema");
        for (Object requiredName : required) {
            if (requiredName == null || !properties.containsKey(requiredName.toString())) {
                throw new IllegalArgumentException(
                        "argSchema required entry '" + requiredName + "' is not declared in properties");
            }
        }
    }

    private static Map<String, Object> requirePropertiesMap(JsonObject root, String context) {
        Object properties = root.get("properties");
        if (properties == null) {
            throw new IllegalArgumentException(context + " must be a JSON Schema object with a properties map");
        }
        if (!(properties instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(
                    context + " properties must be a JSON object, but was: "
                                               + properties.getClass().getSimpleName());
        }
        Map<String, Object> typed = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                typed.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return typed;
    }

    private static Collection<?> readRequiredArray(JsonObject root, String context) {
        Object required = root.get("required");
        if (required == null) {
            return List.of();
        }
        if (!(required instanceof Collection<?> collection)) {
            throw new IllegalArgumentException(
                    context + " required must be a JSON array, but was: "
                                               + required.getClass().getSimpleName());
        }
        return collection;
    }

    private static String resolveResourceContent(CamelContext camelContext, String property) {
        return resolveResourceContent(camelContext, property, "argSchema");
    }

    private static String resolveResourceContent(CamelContext camelContext, String property, String context) {
        try {
            if (ResourceHelper.hasScheme(property)) {
                try (InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, property)) {
                    return camelContext.getTypeConverter().convertTo(String.class, is);
                }
            }
            try (InputStream is = ResourceHelper.resolveResourceAsInputStream(camelContext, property)) {
                if (is != null) {
                    return camelContext.getTypeConverter().convertTo(String.class, is);
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to load " + context + " resource: " + property, e);
        } catch (Exception e) {
            // not a resolvable resource URI — fall through and treat as inline JSON content
        }
        return null;
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
