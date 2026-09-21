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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.List;

import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeToolsTest {

    private RuntimeTools createTools() {
        RuntimeTools tools = new RuntimeTools();
        tools.runtimeService = new RuntimeService();
        return tools;
    }

    @Test
    void processesReturnsListWithoutThrowing() {
        RuntimeTools tools = createTools();
        List<RuntimeService.ProcessInfo> result = tools.camel_runtime_processes();
        assertThat(result).isNotNull();
    }

    @Test
    void contextThrowsWhenNoProcessRunning() {
        RuntimeTools tools = createTools();
        List<RuntimeService.ProcessInfo> processes = tools.camel_runtime_processes();
        if (processes.isEmpty()) {
            assertThatThrownBy(() -> tools.camel_runtime_context(null))
                    .isInstanceOf(ToolCallException.class)
                    .hasMessageContaining("No running Camel processes");
        }
    }

    @Test
    void routeControlRequiresRouteId() {
        RuntimeTools tools = createTools();
        assertThatThrownBy(() -> tools.camel_runtime_route_control(null, null, "start"))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("routeId is required");
    }

    @Test
    void routeControlRequiresCommand() {
        RuntimeTools tools = createTools();
        assertThatThrownBy(() -> tools.camel_runtime_route_control(null, "myRoute", null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("command is required");
    }

    @Test
    void sqlRequiresQuery() {
        RuntimeTools tools = createTools();
        assertThatThrownBy(() -> tools.camel_runtime_sql(null, null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("query is required");
    }

    @Test
    void theNewWrappersDelegateToRegistryTools() {
        // CAMEL-24867: every wrapper names a tool the shared registry has, so a typo cannot hide until runtime
        for (String name : List.of("execute_sql", "get_datasources", "get_sql_trace", "get_circuit_breakers", "get_metrics",
                "get_eip_stats", "get_spans", "get_startup_steps", "get_route_analysis", "detect_config_drift")) {
            assertThat(ToolRegistry.findTool(name)).as(name).isNotNull();
        }
    }

    @Test
    void sendRequiresEndpoint() {
        RuntimeTools tools = createTools();
        assertThatThrownBy(() -> tools.camel_runtime_send(null, null, "body", null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("endpoint is required");
    }

    @Test
    void traceRequiresAction() {
        RuntimeTools tools = createTools();
        assertThatThrownBy(() -> tools.camel_runtime_trace(null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("action is required");
    }

    @Test
    void browseRequiresEndpoint() {
        RuntimeTools tools = createTools();
        assertThatThrownBy(() -> tools.camel_runtime_browse(null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("endpoint is required");
    }
}
