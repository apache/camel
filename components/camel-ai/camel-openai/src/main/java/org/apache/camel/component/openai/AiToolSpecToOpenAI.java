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
package org.apache.camel.component.openai;

import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import org.apache.camel.component.ai.tool.AiToolSpec;

/**
 * Converts {@link AiToolSpec} instances to OpenAI {@link ChatCompletionFunctionTool} objects.
 * <p>
 * Uses the pre-built JSON Schema string from {@link AiToolSpec#getParametersJsonSchema()} and parses it into the OpenAI
 * SDK's {@link FunctionParameters} format via {@link JsonValue#from(Object)}.
 */
final class AiToolSpecToOpenAI {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private AiToolSpecToOpenAI() {
    }

    /**
     * Converts an {@link AiToolSpec} to an OpenAI {@link ChatCompletionFunctionTool}.
     *
     * @param  spec the tool specification to convert
     * @return      the OpenAI function tool definition
     */
    static ChatCompletionFunctionTool toFunctionTool(AiToolSpec spec) {
        FunctionDefinition.Builder funcBuilder = FunctionDefinition.builder()
                .name(spec.getName());

        if (spec.getDescription() != null) {
            funcBuilder.description(spec.getDescription());
        }

        String jsonSchema = spec.getParametersJsonSchema();
        if (jsonSchema != null && !jsonSchema.isEmpty()) {
            try {
                Map<String, Object> schemaMap = OBJECT_MAPPER.readValue(jsonSchema, MAP_TYPE);
                FunctionParameters.Builder paramsBuilder = FunctionParameters.builder();

                if (!schemaMap.containsKey("type")) {
                    paramsBuilder.putAdditionalProperty("type", JsonValue.from("object"));
                }
                for (Map.Entry<String, Object> entry : schemaMap.entrySet()) {
                    paramsBuilder.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
                }

                funcBuilder.parameters(paramsBuilder.build());
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Failed to parse JSON Schema for tool '" + spec.getName() + "': " + e.getMessage(), e);
            }
        }

        return ChatCompletionFunctionTool.builder()
                .function(funcBuilder.build())
                .build();
    }
}
