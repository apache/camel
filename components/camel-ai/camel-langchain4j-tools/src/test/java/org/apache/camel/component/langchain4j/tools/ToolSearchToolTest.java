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

import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import org.apache.camel.component.langchain4j.tools.spec.CamelToolExecutorCache;
import org.apache.camel.component.langchain4j.tools.spec.CamelToolSpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ToolSearchTool
 */
public class ToolSearchToolTest {

    private CamelToolExecutorCache toolCache;
    private ToolSearchTool toolSearchTool;

    @BeforeEach
    public void setUp() {
        toolCache = CamelToolExecutorCache.getInstance();
        // Clear any existing tools
        toolCache.getSearchableTools().clear();
        toolCache.getTools().clear();

        // Create tool search tool with producer tags
        String[] producerTags = new String[] { "users", "products" };
        toolSearchTool = new ToolSearchTool(toolCache, producerTags);
    }

    @AfterEach
    public void tearDown() {
        toolCache.getSearchableTools().clear();
        toolCache.getTools().clear();
    }

    @Test
    public void testSearchToolsWithMatchingTag() {
        // Add a searchable tool
        ToolSpecification spec = createToolSpec("queryUser", "Query user by ID");
        CamelToolSpecification camelSpec = new CamelToolSpecification(spec, null, false);
        toolCache.putSearchable("users", camelSpec);

        // Search for the tool
        List<CamelToolSpecification> results = toolSearchTool.searchTools("users");

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("queryUser", results.get(0).getToolSpecification().name());
    }

    @Test
    public void testSearchToolsWithMultipleTags() {
        // Add tools with different tags
        ToolSpecification spec1 = createToolSpec("queryUser", "Query user");
        ToolSpecification spec2 = createToolSpec("sendEmail", "Send email");
        ToolSpecification spec3 = createToolSpec("queryProduct", "Query product");

        toolCache.putSearchable("users", new CamelToolSpecification(spec1, null, false));
        toolCache.putSearchable("email", new CamelToolSpecification(spec2, null, false));
        toolCache.putSearchable("products", new CamelToolSpecification(spec3, null, false));

        // Search for multiple tags
        List<CamelToolSpecification> results = toolSearchTool.searchTools("users,email");

        assertNotNull(results);
        assertEquals(2, results.size());
    }

    @Test
    public void testSearchToolsNoDuplicates() {
        // Add a tool with multiple tags
        ToolSpecification spec = createToolSpec("sendEmail", "Send email to user");
        CamelToolSpecification camelSpec = new CamelToolSpecification(spec, null, false);

        toolCache.putSearchable("users", camelSpec);
        toolCache.putSearchable("email", camelSpec);

        // Search for both tags - should return only one result
        List<CamelToolSpecification> results = toolSearchTool.searchTools("users,email");

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("sendEmail", results.get(0).getToolSpecification().name());
    }

    @Test
    public void testSearchToolsWithEmptyTags() {
        // Add some tools
        toolCache.putSearchable("users", new CamelToolSpecification(createToolSpec("tool1", "Tool 1"), null, false));
        toolCache.putSearchable("products", new CamelToolSpecification(createToolSpec("tool2", "Tool 2"), null, false));

        // Search with empty tags should return all searchable tools for producer tags
        List<CamelToolSpecification> results = toolSearchTool.searchTools("");

        assertNotNull(results);
        assertEquals(2, results.size());
    }

    @Test
    public void testSearchToolsWithNullTags() {
        // Add some tools
        toolCache.putSearchable("users", new CamelToolSpecification(createToolSpec("tool1", "Tool 1"), null, false));

        // Search with null tags should return all searchable tools
        List<CamelToolSpecification> results = toolSearchTool.searchTools(null);

        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    public void testSearchToolsNoMatches() {
        // Add a tool with a different tag
        toolCache.putSearchable("admin", new CamelToolSpecification(createToolSpec("adminTool", "Admin tool"), null, false));

        // Search for non-existent tag
        List<CamelToolSpecification> results = toolSearchTool.searchTools("users");

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    private ToolSpecification createToolSpec(String name, String description) {
        return ToolSpecification.builder()
                .name(name)
                .description(description)
                .parameters(JsonObjectSchema.builder()
                        .addProperty("param1", JsonStringSchema.builder().description("Parameter 1").build())
                        .build())
                .build();
    }
}
