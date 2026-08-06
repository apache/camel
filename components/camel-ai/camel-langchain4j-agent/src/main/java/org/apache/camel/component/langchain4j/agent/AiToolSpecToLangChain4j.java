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
package org.apache.camel.component.langchain4j.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.internal.JsonSchemaElementJsonUtils;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import org.apache.camel.component.ai.tool.AiToolParameterHelper;
import org.apache.camel.component.ai.tool.AiToolSpec;
import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Converts {@link AiToolSpec} instances to langchain4j {@link ToolSpecification} objects, mapping Camel parameter
 * definitions to the corresponding JSON Schema types.
 */
public final class AiToolSpecToLangChain4j {

    private AiToolSpecToLangChain4j() {
    }

    public static ToolSpecification toToolSpecification(AiToolSpec spec) {
        ToolSpecification.Builder builder = ToolSpecification.builder()
                .name(spec.getName())
                .description(spec.getDescription());

        if (spec.getParameterDefs() != null && !spec.getParameterDefs().isEmpty()) {
            builder.parameters(buildSchema(spec.getParameterDefs()));
        } else if (spec.getParametersJsonSchema() != null && !spec.getParametersJsonSchema().isBlank()) {
            builder.parameters(buildSchemaFromJson(spec.getParametersJsonSchema()));
        }

        return builder.build();
    }

    private static JsonObjectSchema buildSchemaFromJson(String jsonSchema) {
        try {
            Object parsed = Jsoner.deserialize(jsonSchema);
            if (parsed == null) {
                throw new IllegalArgumentException("Tool JSON Schema must be a JSON object, but was: null");
            }
            if (!(parsed instanceof JsonObject root)) {
                throw new IllegalArgumentException(
                        "Tool JSON Schema must be a JSON object, but was: " + parsed.getClass().getSimpleName());
            }
            JsonSchemaElement element = JsonSchemaElementJsonUtils.fromMap(root);
            if (element instanceof JsonObjectSchema objectSchema) {
                return objectSchema;
            }
            JsonObject stripped = stripSchemaMetadata(root);
            if (!stripped.equals(root)) {
                element = JsonSchemaElementJsonUtils.fromMap(stripped);
                if (element instanceof JsonObjectSchema objectSchema) {
                    return objectSchema;
                }
            }
            if (element instanceof JsonRawSchema rawSchema) {
                throw new IllegalArgumentException(
                        "Tool JSON Schema root could not be converted to JsonObjectSchema. "
                                                   + "Remove unsupported root keywords such as $schema or schema-valued "
                                                   + "additionalProperties, or simplify the root schema.");
            }
            throw new IllegalArgumentException(
                    "Tool JSON Schema root must deserialize to JsonObjectSchema, but was: "
                                               + element.getClass().getSimpleName());
        } catch (DeserializationException e) {
            throw new IllegalArgumentException("Tool JSON Schema is not valid JSON", e);
        }
    }

    private static JsonObject stripSchemaMetadata(JsonObject root) {
        JsonObject copy = new JsonObject();
        for (Map.Entry<String, Object> entry : root.entrySet()) {
            String key = entry.getKey();
            if ("$schema".equals(key) || "$id".equals(key) || "$defs".equals(key) || "definitions".equals(key)) {
                continue;
            }
            copy.put(key, entry.getValue());
        }
        return copy;
    }

    private static JsonObjectSchema buildSchema(Map<String, AiToolParameterHelper.ParameterDef> defs) {
        JsonObjectSchema.Builder schemaBuilder = JsonObjectSchema.builder();
        List<String> required = new ArrayList<>();

        for (Map.Entry<String, AiToolParameterHelper.ParameterDef> entry : defs.entrySet()) {
            String paramName = entry.getKey();
            AiToolParameterHelper.ParameterDef def = entry.getValue();

            JsonSchemaElement schema;
            if (def.getEnumValues() != null && !def.getEnumValues().isEmpty()) {
                schema = JsonEnumSchema.builder()
                        .enumValues(def.getEnumValues())
                        .description(def.getDescription())
                        .build();
            } else {
                schema = switch (def.getType().toLowerCase(Locale.ROOT)) {
                    case "integer", "int", "long" -> JsonIntegerSchema.builder().description(def.getDescription()).build();
                    case "number", "double", "float" -> JsonNumberSchema.builder().description(def.getDescription()).build();
                    case "boolean", "bool" -> JsonBooleanSchema.builder().description(def.getDescription()).build();
                    default -> JsonStringSchema.builder().description(def.getDescription()).build();
                };
            }

            schemaBuilder.addProperty(paramName, schema);
            if (def.isRequired()) {
                required.add(paramName);
            }
        }

        if (!required.isEmpty()) {
            schemaBuilder.required(required);
        }

        return schemaBuilder.build();
    }
}
