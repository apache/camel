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

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
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
    void listExamplesGroupsTheLadderWithoutArguments() {
        ToolContext ctx = new ToolContext();
        String json = ToolRegistry.execute("list_examples", ctx, Map.of()).toString();
        assertTrue(json.contains("\"groups\""), "Should return the groups");
        assertTrue(json.contains("\"level\":\"quick-start\""), "Should start with the quick-start group");
        assertTrue(json.contains("timer-log"), "Should list the examples");
        assertTrue(json.contains("\"teaches\""), "Should tell what the examples teach");
        // more than the old cap of 20 examples
        assertTrue(json.indexOf("\"total\":") > 0);
        String total = json.replaceAll(".*\"total\":(\\d+).*", "$1");
        assertTrue(Integer.parseInt(total) > 20, "Should count all examples, got " + total);
    }

    @Test
    void listExamplesFiltersOneGroupAndLimits() {
        ToolContext ctx = new ToolContext();
        String json = ToolRegistry.execute("list_examples", ctx, Map.of("level", "run", "limit", "1")).toString();
        assertTrue(json.contains("\"count\":1"), "Should honour the limit: " + json);
        assertTrue(json.contains("\"level\":\"run\""));
        assertFalse(json.contains("\"level\":\"quick-start\""), "Should only return the run group");
        assertThrows(ToolExecutionException.class,
                () -> ToolRegistry.execute("list_examples", ctx, Map.of("limit", "many")));
        String zero = ToolRegistry.execute("list_examples", ctx, Map.of("level", "run", "limit", "0")).toString();
        assertFalse(zero.contains("\"count\":0"), "limit 0 falls back to the default: " + zero.substring(0, 60));
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

    @Test
    void historySummaryKeepsTheStepsAndTheBodyTypeAndSize() {
        // CAMEL-24844: the compact form of get_history a small model can read
        JsonObject body = new JsonObject();
        body.put("type", "java.util.LinkedHashMap");
        body.put("size", 3);
        body.put("value", "{orderId=ORD-1001}");
        JsonObject message = new JsonObject();
        message.put("body", body);
        message.put("headers", new JsonArray());
        JsonObject trace = new JsonObject();
        trace.put("routeId", "route1");
        trace.put("nodeId", "unmarshal1");
        trace.put("nodeShortName", "unmarshal");
        trace.put("elapsed", 2);
        trace.put("message", message);
        JsonArray traces = new JsonArray();
        traces.add(trace);
        JsonObject history = new JsonObject();
        history.put("name", "shop");
        history.put("traces", traces);

        JsonObject summary = ToolRegistry.historySummary(history);
        assertEquals("shop", summary.get("name"));
        JsonArray steps = summary.getCollection("steps");
        assertEquals(1, steps.size());
        JsonObject step = (JsonObject) steps.get(0);
        assertEquals("unmarshal1", step.get("nodeId"));
        assertEquals("java.util.LinkedHashMap", step.get("bodyType"));
        assertEquals(3, step.get("bodySize"));
        assertNull(step.get("message"), "no bodies, headers or properties in the summary");
    }
}
