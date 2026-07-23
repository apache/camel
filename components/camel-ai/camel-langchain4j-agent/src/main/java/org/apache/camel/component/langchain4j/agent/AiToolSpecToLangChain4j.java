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
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import org.apache.camel.component.ai.tool.AiToolParameterHelper;
import org.apache.camel.component.ai.tool.AiToolSpec;

final class AiToolSpecToLangChain4j {

    private AiToolSpecToLangChain4j() {
    }

    static ToolSpecification toToolSpecification(AiToolSpec spec) {
        ToolSpecification.Builder builder = ToolSpecification.builder()
                .name(spec.getName())
                .description(spec.getDescription());

        if (spec.getParameterDefs() != null && !spec.getParameterDefs().isEmpty()) {
            builder.parameters(buildSchema(spec.getParameterDefs()));
        }

        return builder.build();
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
