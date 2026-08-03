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
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import org.apache.camel.component.ai.tool.AiToolParameterHelper;
import org.apache.camel.component.ai.tool.AiToolSpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiToolSpecToOpenAITest {

    @Test
    void convertFullSpec() {
        Map<String, String> params = Map.of(
                "city", "string",
                "city.description", "The city name",
                "city.required", "true",
                "unit", "string",
                "unit.enum", "celsius,fahrenheit",
                "unit.description", "Temperature unit");

        Map<String, AiToolParameterHelper.ParameterDef> defs = AiToolParameterHelper.parseParameterMetadata(params);
        String jsonSchema = AiToolParameterHelper.buildJsonSchema(params);

        AiToolSpec spec = new AiToolSpec("getWeather", "Get current weather", defs, jsonSchema, null);

        ChatCompletionFunctionTool result = AiToolSpecToOpenAI.toFunctionTool(spec);

        assertThat(result.function().name()).isEqualTo("getWeather");
        assertThat(result.function().description()).hasValue("Get current weather");
        assertThat(result.function().parameters()).isPresent();

        FunctionParameters parameters = result.function().parameters().get();
        Map<String, JsonValue> props = parameters._additionalProperties();

        assertThat(props.get("type").asString()).contains("object");
        assertThat(props).containsKey("properties");
    }

    @Test
    void convertSpecWithoutParameters() {
        AiToolSpec spec = new AiToolSpec("noParams", "A tool with no parameters", Map.of(), null, null);

        ChatCompletionFunctionTool result = AiToolSpecToOpenAI.toFunctionTool(spec);

        assertThat(result.function().name()).isEqualTo("noParams");
        assertThat(result.function().description()).hasValue("A tool with no parameters");
        assertThat(result.function().parameters()).isEmpty();
    }

    @Test
    void convertSpecWithoutDescription() {
        AiToolSpec spec = new AiToolSpec("bareTool", null, Map.of(), null, null);

        ChatCompletionFunctionTool result = AiToolSpecToOpenAI.toFunctionTool(spec);

        assertThat(result.function().name()).isEqualTo("bareTool");
        assertThat(result.function().description()).isEmpty();
    }

    @Test
    void convertSpecDefaultsTypeToObject() {
        // JSON Schema without "type" key should get "object" defaulted
        String jsonSchema = "{\"properties\":{\"x\":{\"type\":\"string\"}}}";
        AiToolSpec spec = new AiToolSpec("testTool", "Test", Map.of(), jsonSchema, null);

        ChatCompletionFunctionTool result = AiToolSpecToOpenAI.toFunctionTool(spec);

        assertThat(result.function().parameters()).isPresent();
        FunctionParameters parameters = result.function().parameters().get();
        assertThat(parameters._additionalProperties().get("type").asString()).contains("object");
    }

    @Test
    void convertSpecPreservesRequiredArray() {
        String jsonSchema = "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}},\"required\":[\"city\"]}";
        AiToolSpec spec = new AiToolSpec("withRequired", "Has required", Map.of(), jsonSchema, null);

        ChatCompletionFunctionTool result = AiToolSpecToOpenAI.toFunctionTool(spec);

        FunctionParameters parameters = result.function().parameters().get();
        assertThat(parameters._additionalProperties()).containsKey("required");
        assertThat(parameters._additionalProperties().get("required").asArray()).isNotEmpty();
    }

    @Test
    void convertSpecWithEmptyJsonSchema() {
        AiToolSpec spec = new AiToolSpec("emptySchema", "Empty", Map.of(), "", null);

        ChatCompletionFunctionTool result = AiToolSpecToOpenAI.toFunctionTool(spec);

        assertThat(result.function().name()).isEqualTo("emptySchema");
        assertThat(result.function().parameters()).isEmpty();
    }

    @Test
    void convertSpecWithInvalidJsonSchemaThrows() {
        AiToolSpec spec = new AiToolSpec("badSchema", "Bad", Map.of(), "not valid json", null);

        assertThatThrownBy(() -> AiToolSpecToOpenAI.toFunctionTool(spec))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to parse JSON Schema for tool 'badSchema'");
    }

    @Test
    void convertSpecPreservesAdditionalPropertiesFalse() {
        String jsonSchema = "{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\"}},\"additionalProperties\":false}";
        AiToolSpec spec = new AiToolSpec("strict", "Strict tool", Map.of(), jsonSchema, null);

        ChatCompletionFunctionTool result = AiToolSpecToOpenAI.toFunctionTool(spec);

        FunctionParameters parameters = result.function().parameters().get();
        assertThat(parameters._additionalProperties()).containsKey("additionalProperties");
        assertThat(parameters._additionalProperties().get("additionalProperties").asBoolean()).contains(false);
    }
}
