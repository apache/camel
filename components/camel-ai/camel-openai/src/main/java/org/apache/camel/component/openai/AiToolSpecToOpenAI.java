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

import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import org.apache.camel.component.ai.tool.AiToolParameterHelper;
import org.apache.camel.component.ai.tool.AiToolSpec;
import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Converts route-based {@link AiToolSpec} instances to OpenAI chat completion tool format.
 */
final class AiToolSpecToOpenAI {

    private AiToolSpecToOpenAI() {
    }

    static ChatCompletionFunctionTool convert(AiToolSpec spec) {
        FunctionDefinition.Builder funcBuilder = FunctionDefinition.builder()
                .name(spec.getName())
                .description(spec.getDescription());

        String jsonSchema = spec.getParametersJsonSchema();
        if (jsonSchema == null && spec.getParameterDefs() != null && !spec.getParameterDefs().isEmpty()) {
            jsonSchema = AiToolParameterHelper.buildJsonSchemaFromDefs(spec.getParameterDefs());
        }
        if (jsonSchema != null && !jsonSchema.isBlank()) {
            funcBuilder.parameters(buildParameters(jsonSchema));
        }

        return ChatCompletionFunctionTool.builder()
                .function(funcBuilder.build())
                .build();
    }

    private static FunctionParameters buildParameters(String jsonSchema) {
        FunctionParameters.Builder paramsBuilder = FunctionParameters.builder();
        try {
            Object parsed = Jsoner.deserialize(jsonSchema);
            if (parsed instanceof JsonObject root) {
                if (!root.containsKey("type")) {
                    paramsBuilder.putAdditionalProperty("type", JsonValue.from("object"));
                }
                for (Map.Entry<String, Object> entry : root.entrySet()) {
                    paramsBuilder.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
                }
                return paramsBuilder.build();
            }
        } catch (DeserializationException e) {
            throw new IllegalArgumentException("Tool JSON Schema is not valid JSON for tool parameters", e);
        }
        throw new IllegalArgumentException("Tool JSON Schema must be a JSON object");
    }
}
