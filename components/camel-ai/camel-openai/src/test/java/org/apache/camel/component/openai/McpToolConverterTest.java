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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.openai.core.JsonValue;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolConverterTest {

    @Test
    void convertFullSchema() {
        McpSchema.Tool tool = McpSchema.Tool.builder("get_weather",
                Map.of("type", "object",
                        "properties", Map.of("city", Map.of("type", "string", "description", "City name"),
                                "required", List.of("city"))))
                .description("Get the weather for a location")
                .build();

        List<ChatCompletionFunctionTool> result = McpToolConverter.convert(List.of(tool));

        assertEquals(1, result.size());
        ChatCompletionFunctionTool converted = result.get(0);
        assertEquals("get_weather", converted.function().name());
        assertTrue(converted.function().description().isPresent());
        assertEquals("Get the weather for a location", converted.function().description().get());
        assertTrue(converted.function().parameters().isPresent());
    }

    @Test
    void convertWithoutInputSchema() {
        McpSchema.Tool tool = McpSchema.Tool.builder("no_params_tool", Collections.emptyMap())
                .description("A tool with no parameters")
                .build();

        List<ChatCompletionFunctionTool> result = McpToolConverter.convert(List.of(tool));

        assertEquals(1, result.size());
        assertEquals("no_params_tool", result.get(0).function().name());
        assertTrue(result.get(0).function().description().isPresent());
    }

    @Test
    void convertWithoutDescription() {
        McpSchema.Tool tool = McpSchema.Tool.builder("bare_tool", Map.of("type", "object"))
                .name("bare_tool")
                .build();

        List<ChatCompletionFunctionTool> result = McpToolConverter.convert(List.of(tool));

        assertEquals(1, result.size());
        assertEquals("bare_tool", result.get(0).function().name());
    }

    @Test
    void convertMultipleTools() {
        McpSchema.Tool tool1 = McpSchema.Tool.builder("tool_a", Collections.emptyMap())
                .description("First tool")
                .build();

        McpSchema.Tool tool2 = McpSchema.Tool
                .builder("tool_b", Map.of("type", "object", "properties", Map.of("x", Map.of("type", "number"))))
                .description("Second tool")
                .build();

        List<ChatCompletionFunctionTool> result = McpToolConverter.convert(List.of(tool1, tool2));

        assertEquals(2, result.size());
        assertEquals("tool_a", result.get(0).function().name());
        assertEquals("tool_b", result.get(1).function().name());
    }

    @Test
    void convertEmptyList() {
        List<ChatCompletionFunctionTool> result = McpToolConverter.convert(List.of());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void convertPreservesAllInputSchemaKeywords() {
        Map<String, Object> addressDef = Map.of("type", "object", "properties", Map.of("city", Map.of("type", "string")));
        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "description", "Search parameters",
                "additionalProperties", false,
                "$defs", Map.of("Address", addressDef),
                "properties", Map.of("query", Map.of("type", "string"), "address", Map.of("$ref", "#/$defs/Address")),
                "required", List.of("query"));

        McpSchema.Tool tool = McpSchema.Tool.builder("search", inputSchema)
                .description("Search tool")
                .build();

        FunctionParameters parameters = McpToolConverter.convert(List.of(tool)).get(0).function().parameters().get();
        Map<String, JsonValue> converted = parameters._additionalProperties();

        assertThat(converted).containsKeys("type", "description", "additionalProperties", "$defs", "properties", "required");
        assertThat(converted.get("type").asString()).contains("object");
        assertThat(converted.get("description").asString()).contains("Search parameters");
        assertThat(converted.get("additionalProperties").asBoolean()).contains(false);
        @SuppressWarnings("unchecked")
        Map<String, JsonValue> defs = (Map<String, JsonValue>) converted.get("$defs").asObject().orElse(Map.of());
        @SuppressWarnings("unchecked")
        Map<String, JsonValue> properties = (Map<String, JsonValue>) converted.get("properties").asObject().orElse(Map.of());
        assertThat(defs).containsKey("Address");
        assertThat(properties).containsKey("address");
        assertThat(converted.get("required").asArray()).isNotEmpty();
    }

    @Test
    void convertDefaultsTypeToObjectWhenMissing() {
        McpSchema.Tool tool = McpSchema.Tool.builder("bare_schema", Map.of("properties", Map.of("x", Map.of("type", "string"))))
                .build();

        FunctionParameters parameters = McpToolConverter.convert(List.of(tool)).get(0).function().parameters().get();

        assertThat(parameters._additionalProperties().get("type").asString()).contains("object");
        assertThat(parameters._additionalProperties()).containsKey("properties");
    }
}
