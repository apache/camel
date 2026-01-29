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
package org.apache.camel.component.langchain4j.tools.spec;

import java.util.Set;

import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for CamelToolExecutorCache
 */
public class CamelToolExecutorCacheTest {

    private CamelToolExecutorCache cache;

    @BeforeEach
    public void setUp() {
        cache = CamelToolExecutorCache.getInstance();
        // Clear any existing tools
        cache.getTools().clear();
        cache.getSearchableTools().clear();
    }

    @AfterEach
    public void tearDown() {
        cache.getTools().clear();
        cache.getSearchableTools().clear();
    }

    @Test
    public void testPutAndGetExposedTool() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("testTool")
                .description("Test tool")
                .build();
        CamelToolSpecification camelSpec = new CamelToolSpecification(spec, null, true);

        cache.put("users", camelSpec);

        Set<CamelToolSpecification> tools = cache.getTools().get("users");
        assertNotNull(tools);
        assertEquals(1, tools.size());
        assertTrue(tools.contains(camelSpec));
    }

    @Test
    public void testPutAndGetSearchableTool() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("searchableTool")
                .description("Searchable tool")
                .build();
        CamelToolSpecification camelSpec = new CamelToolSpecification(spec, null, false);

        cache.putSearchable("products", camelSpec);

        Set<CamelToolSpecification> tools = cache.getSearchableTools().get("products");
        assertNotNull(tools);
        assertEquals(1, tools.size());
        assertTrue(tools.contains(camelSpec));
    }

    @Test
    public void testRemoveExposedTool() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("testTool")
                .description("Test tool")
                .build();
        CamelToolSpecification camelSpec = new CamelToolSpecification(spec, null, true);

        cache.put("users", camelSpec);
        assertEquals(1, cache.getTools().get("users").size());

        cache.remove("users", camelSpec);
        assertNull(cache.getTools().get("users"));
    }

    @Test
    public void testRemoveSearchableTool() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("searchableTool")
                .description("Searchable tool")
                .build();
        CamelToolSpecification camelSpec = new CamelToolSpecification(spec, null, false);

        cache.putSearchable("products", camelSpec);
        assertEquals(1, cache.getSearchableTools().get("products").size());

        cache.removeSearchable("products", camelSpec);
        assertNull(cache.getSearchableTools().get("products"));
    }

    @Test
    public void testMultipleToolsWithSameTag() {
        ToolSpecification spec1 = ToolSpecification.builder().name("tool1").description("Tool 1").build();
        ToolSpecification spec2 = ToolSpecification.builder().name("tool2").description("Tool 2").build();

        CamelToolSpecification camelSpec1 = new CamelToolSpecification(spec1, null, true);
        CamelToolSpecification camelSpec2 = new CamelToolSpecification(spec2, null, true);

        cache.put("users", camelSpec1);
        cache.put("users", camelSpec2);

        Set<CamelToolSpecification> tools = cache.getTools().get("users");
        assertNotNull(tools);
        assertEquals(2, tools.size());
    }

    @Test
    public void testRemoveOneOfMultipleTools() {
        ToolSpecification spec1 = ToolSpecification.builder().name("tool1").description("Tool 1").build();
        ToolSpecification spec2 = ToolSpecification.builder().name("tool2").description("Tool 2").build();

        CamelToolSpecification camelSpec1 = new CamelToolSpecification(spec1, null, true);
        CamelToolSpecification camelSpec2 = new CamelToolSpecification(spec2, null, true);

        cache.put("users", camelSpec1);
        cache.put("users", camelSpec2);

        cache.remove("users", camelSpec1);

        Set<CamelToolSpecification> tools = cache.getTools().get("users");
        assertNotNull(tools);
        assertEquals(1, tools.size());
        assertTrue(tools.contains(camelSpec2));
        assertFalse(tools.contains(camelSpec1));
    }

    @Test
    public void testHasSearchableTools() {
        assertFalse(cache.hasSearchableTools());

        ToolSpecification spec = ToolSpecification.builder().name("tool").description("Tool").build();
        cache.putSearchable("users", new CamelToolSpecification(spec, null, false));

        assertTrue(cache.hasSearchableTools());
    }
}
