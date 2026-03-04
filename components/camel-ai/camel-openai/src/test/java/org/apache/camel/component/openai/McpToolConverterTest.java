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

import java.util.List;
import java.util.Map;

import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolConverterTest {

    @Test
    void convertFullSchema() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("get_weather")
                .description("Get the weather for a location")
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        Map.of("city", Map.of("type", "string", "description", "City name")),
                        List.of("city"),
                        null, null, null))
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
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("no_params_tool")
                .description("A tool with no parameters")
                .build();

        List<ChatCompletionFunctionTool> result = McpToolConverter.convert(List.of(tool));

        assertEquals(1, result.size());
        assertEquals("no_params_tool", result.get(0).function().name());
        assertTrue(result.get(0).function().description().isPresent());
    }

    @Test
    void convertWithoutDescription() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("bare_tool")
                .inputSchema(new McpSchema.JsonSchema("object", null, null, null, null, null))
                .build();

        List<ChatCompletionFunctionTool> result = McpToolConverter.convert(List.of(tool));

        assertEquals(1, result.size());
        assertEquals("bare_tool", result.get(0).function().name());
    }

    @Test
    void convertMultipleTools() {
        McpSchema.Tool tool1 = McpSchema.Tool.builder()
                .name("tool_a")
                .description("First tool")
                .build();

        McpSchema.Tool tool2 = McpSchema.Tool.builder()
                .name("tool_b")
                .description("Second tool")
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        Map.of("x", Map.of("type", "number")),
                        null, null, null, null))
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
}
