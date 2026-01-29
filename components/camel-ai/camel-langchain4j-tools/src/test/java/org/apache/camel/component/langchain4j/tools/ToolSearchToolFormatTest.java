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
package org.apache.camel.component.langchain4j.tools;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import org.apache.camel.component.langchain4j.tools.spec.CamelToolSpecification;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ToolSearchTool formatting functionality
 */
public class ToolSearchToolFormatTest {

    @Test
    public void testFormatEmptyToolList() {
        List<CamelToolSpecification> tools = new ArrayList<>();
        String result = ToolSearchTool.formatToolsForLLM(tools);

        assertEquals("No tools found matching the search criteria.", result);
    }

    @Test
    public void testFormatSingleTool() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("queryUser")
                .description("Query user database by ID")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("userId", JsonStringSchema.builder().description("User ID").build())
                        .build())
                .build();

        List<CamelToolSpecification> tools = new ArrayList<>();
        tools.add(new CamelToolSpecification(spec, null, false));

        String result = ToolSearchTool.formatToolsForLLM(tools);

        assertTrue(result.contains("Found 1 tool(s)"));
        assertTrue(result.contains("1. queryUser"));
        assertTrue(result.contains("Description: Query user database by ID"));
        assertTrue(result.contains("Parameters: userId"));
    }

    @Test
    public void testFormatMultipleTools() {
        ToolSpecification spec1 = ToolSpecification.builder()
                .name("queryUser")
                .description("Query user by ID")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("userId", JsonStringSchema.builder().description("User ID").build())
                        .build())
                .build();

        ToolSpecification spec2 = ToolSpecification.builder()
                .name("sendEmail")
                .description("Send email to user")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("email", JsonStringSchema.builder().description("Email address").build())
                        .addProperty("message", JsonStringSchema.builder().description("Message content").build())
                        .build())
                .build();

        List<CamelToolSpecification> tools = new ArrayList<>();
        tools.add(new CamelToolSpecification(spec1, null, false));
        tools.add(new CamelToolSpecification(spec2, null, false));

        String result = ToolSearchTool.formatToolsForLLM(tools);

        assertTrue(result.contains("Found 2 tool(s)"));
        assertTrue(result.contains("1. queryUser"));
        assertTrue(result.contains("2. sendEmail"));
        assertTrue(result.contains("Description: Query user by ID"));
        assertTrue(result.contains("Description: Send email to user"));
        assertTrue(result.contains("Parameters: userId"));
        assertTrue(result.contains("Parameters: email, message") || result.contains("Parameters: message, email"));
    }

    @Test
    public void testFormatToolWithoutParameters() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("healthCheck")
                .description("Check system health")
                .build();

        List<CamelToolSpecification> tools = new ArrayList<>();
        tools.add(new CamelToolSpecification(spec, null, false));

        String result = ToolSearchTool.formatToolsForLLM(tools);

        assertTrue(result.contains("Found 1 tool(s)"));
        assertTrue(result.contains("1. healthCheck"));
        assertTrue(result.contains("Description: Check system health"));
        // Should not contain "Parameters:" line
        assertFalse(result.contains("Parameters:"));
    }

    private void assertFalse(boolean condition) {
        assertTrue(!condition);
    }
}
