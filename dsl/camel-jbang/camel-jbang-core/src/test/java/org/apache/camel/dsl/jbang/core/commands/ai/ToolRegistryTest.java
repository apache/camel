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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRegistryTest {

    @Test
    void allToolsHaveUniqueNames() {
        List<String> names = ToolRegistry.allTools().stream()
                .map(ToolDescriptor::name).toList();
        Set<String> unique = new HashSet<>(names);
        assertEquals(unique.size(), names.size(), "Duplicate tool names found");
    }

    @Test
    void allToolsHaveDescriptions() {
        for (ToolDescriptor t : ToolRegistry.allTools()) {
            assertNotNull(t.description(), t.name() + " has no description");
            assertFalse(t.description().isBlank(), t.name() + " has blank description");
        }
    }

    @Test
    void allToolsHaveExecutors() {
        for (ToolDescriptor t : ToolRegistry.allTools()) {
            assertNotNull(t.executor(), t.name() + " has no executor");
        }
    }

    @Test
    void findToolReturnsRegisteredTool() {
        ToolDescriptor tool = ToolRegistry.findTool("catalog_components");
        assertNotNull(tool);
        assertEquals("catalog_components", tool.name());
    }

    @Test
    void findToolReturnsNullForUnknown() {
        assertNull(ToolRegistry.findTool("nonexistent_tool"));
    }

    @Test
    void catalogToolsWorkWithoutProcess() {
        // Catalog tools should not require a running process
        ToolContext ctx = new ToolContext();
        Object result = ToolRegistry.execute("catalog_components",
                ctx, Map.of("filter", "timer"));
        assertNotNull(result);
        String json = result.toString();
        assertTrue(json.contains("timer"), "Should find timer component");
    }

    @Test
    void catalogEipToolsWorkWithoutProcess() {
        ToolContext ctx = new ToolContext();
        Object result = ToolRegistry.execute("catalog_eips",
                ctx, Map.of("filter", "split"));
        assertNotNull(result);
        String json = result.toString();
        assertTrue(json.contains("split"), "Should find split EIP");
    }

    @Test
    void runtimeToolThrowsWithoutProcess() {
        ToolContext ctx = new ToolContext();
        assertThrows(ToolExecutionException.class,
                () -> ToolRegistry.execute("get_context", ctx, Map.of()));
    }

    @Test
    void registryContainsExpectedToolCount() {
        // Should have at least 50 tools (process + status + actions + DevConsole + analysis + catalog + examples)
        assertTrue(ToolRegistry.allTools().size() >= 50,
                "Expected at least 50 tools, got " + ToolRegistry.allTools().size());
    }

    @Test
    void newCatalogToolsPresent() {
        // Verify the new catalog tools that were MCP-only are now registered
        assertNotNull(ToolRegistry.findTool("catalog_dataformats"), "catalog_dataformats should be registered");
        assertNotNull(ToolRegistry.findTool("catalog_dataformat_doc"), "catalog_dataformat_doc should be registered");
        assertNotNull(ToolRegistry.findTool("catalog_languages"), "catalog_languages should be registered");
        assertNotNull(ToolRegistry.findTool("catalog_language_doc"), "catalog_language_doc should be registered");
        assertNotNull(ToolRegistry.findTool("catalog_eip_doc"), "catalog_eip_doc should be registered");
    }

    @Test
    void devConsoleToolsPresent() {
        // Verify DevConsole tools are registered
        assertNotNull(ToolRegistry.findTool("get_circuit_breakers"), "get_circuit_breakers should be registered");
        assertNotNull(ToolRegistry.findTool("get_startup_steps"), "get_startup_steps should be registered");
        assertNotNull(ToolRegistry.findTool("get_datasources"), "get_datasources should be registered");
        assertNotNull(ToolRegistry.findTool("execute_sql"), "execute_sql should be registered");
        assertNotNull(ToolRegistry.findTool("get_spans"), "get_spans should be registered");
        assertNotNull(ToolRegistry.findTool("get_metrics"), "get_metrics should be registered");
    }

    @Test
    void executeSqlToolRequiresProcess() {
        ToolContext ctx = new ToolContext();
        assertThrows(ToolExecutionException.class,
                () -> ToolRegistry.execute("execute_sql", ctx, Map.of("query", "SELECT 1")));
    }

    @Test
    void executeSqlToolRequiresQueryParam() {
        ToolContext ctx = new ToolContext();
        ctx.selectProcess(99999);
        // execute_sql should throw for empty query parameter
        assertThrows(ToolExecutionException.class,
                () -> ToolRegistry.execute("execute_sql", ctx, Map.of()));
    }

    @Test
    void circuitBreakerToolRequiresProcess() {
        ToolContext ctx = new ToolContext();
        assertThrows(ToolExecutionException.class,
                () -> ToolRegistry.execute("get_circuit_breakers", ctx, Map.of()));
    }

    @Test
    void analysisToolsPresent() {
        assertNotNull(ToolRegistry.findTool("get_route_analysis"), "get_route_analysis should be registered");
        assertNotNull(ToolRegistry.findTool("get_eip_stats"), "get_eip_stats should be registered");
        assertNotNull(ToolRegistry.findTool("detect_config_drift"), "detect_config_drift should be registered");
    }

    @Test
    void analysisToolsRequireProcess() {
        ToolContext ctx = new ToolContext();
        assertThrows(ToolExecutionException.class,
                () -> ToolRegistry.execute("get_route_analysis", ctx, Map.of()));
        assertThrows(ToolExecutionException.class,
                () -> ToolRegistry.execute("get_eip_stats", ctx, Map.of()));
        assertThrows(ToolExecutionException.class,
                () -> ToolRegistry.execute("detect_config_drift", ctx, Map.of()));
    }
}
