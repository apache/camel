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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.openai.client.OpenAIClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CAMEL-23964: per-server MCP tool filtering via the {@code toolNames} include list.
 */
class OpenAIEndpointMcpToolFilteringTest {

    private static final String SERVER_A = "serverA";
    private static final String SERVER_B = "serverB";
    private static final List<String> ALL_TOOLS = List.of("read_file", "write_file", "list_directory", "delete_file");

    /**
     * Overrides the client-creation seam so that tests return mock MCP clients instead of opening real transports, and
     * stubs createClient to avoid needing real API keys.
     */
    private static class TestEndpoint extends OpenAIEndpoint {
        private final Map<String, List<String>> serverToolNames;

        TestEndpoint(OpenAIComponent component, OpenAIConfiguration config,
                     Map<String, List<String>> serverToolNames) {
            super("openai:chat-completion", component, config);
            this.serverToolNames = serverToolNames;
        }

        @Override
        McpSyncClient createMcpClient(String serverName, Map<String, String> props) {
            List<String> toolNames = serverToolNames.getOrDefault(serverName, List.of());
            McpSyncClient client = mock(McpSyncClient.class);
            List<McpSchema.Tool> tools = toolNames.stream()
                    .map(n -> McpSchema.Tool.builder(n, Map.of("type", "object")).description("mock " + n).build())
                    .toList();
            when(client.listTools()).thenReturn(McpSchema.ListToolsResult.builder(tools).build());
            return client;
        }

        @Override
        protected OpenAIClient createClient() {
            return mock(OpenAIClient.class);
        }
    }

    private TestEndpoint createAndStartEndpoint(
            Map<String, Map<String, String>> serverConfigs,
            Map<String, List<String>> serverToolNames)
            throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        OpenAIComponent component = new OpenAIComponent();
        component.setCamelContext(ctx);

        // Build the flat mcpServer config map that OpenAIConfiguration expects
        Map<String, Object> flatConfig = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : serverConfigs.entrySet()) {
            String name = entry.getKey();
            for (Map.Entry<String, String> prop : entry.getValue().entrySet()) {
                flatConfig.put(name + "." + prop.getKey(), prop.getValue());
            }
        }

        OpenAIConfiguration config = new OpenAIConfiguration();
        config.setMcpServer(flatConfig);

        TestEndpoint endpoint = new TestEndpoint(component, config, serverToolNames);
        endpoint.setCamelContext(ctx);
        endpoint.setOperation(OpenAIOperations.chatCompletion);
        endpoint.doStart();
        return endpoint;
    }

    @Test
    void toolNamesFilterKeepsOnlySpecifiedTools() throws Exception {
        Map<String, Map<String, String>> serverConfigs = Map.of(
                SERVER_A, Map.of("transportType", "stdio", "command", "echo", "toolNames", "read_file,list_directory"));

        Map<String, List<String>> serverToolNames = Map.of(SERVER_A, ALL_TOOLS);

        TestEndpoint endpoint = createAndStartEndpoint(serverConfigs, serverToolNames);

        McpToolState state = endpoint.getMcpToolState();
        Set<String> registeredNames = state.tools().stream()
                .map(t -> t.function().name())
                .collect(Collectors.toSet());

        assertThat(registeredNames).containsExactlyInAnyOrder("read_file", "list_directory");
        assertThat(registeredNames).doesNotContain("write_file", "delete_file");

        // Also verify tool-to-client and tool-to-server maps are consistent
        assertThat(state.toolClientMap()).containsOnlyKeys("read_file", "list_directory");
        assertThat(state.toolToServerName()).containsOnlyKeys("read_file", "list_directory");

        endpoint.doStop();
    }

    @Test
    void noToolNamesPropertyRegistersAllTools() throws Exception {
        Map<String, Map<String, String>> serverConfigs = Map.of(
                SERVER_A, Map.of("transportType", "stdio", "command", "echo"));

        Map<String, List<String>> serverToolNames = Map.of(SERVER_A, ALL_TOOLS);

        TestEndpoint endpoint = createAndStartEndpoint(serverConfigs, serverToolNames);

        McpToolState state = endpoint.getMcpToolState();
        Set<String> registeredNames = state.tools().stream()
                .map(t -> t.function().name())
                .collect(Collectors.toSet());

        assertThat(registeredNames).containsExactlyInAnyOrder("read_file", "write_file", "list_directory", "delete_file");

        endpoint.doStop();
    }

    @Test
    void emptyToolNamesPropertyRegistersAllTools() throws Exception {
        Map<String, Map<String, String>> serverConfigs = Map.of(
                SERVER_A, Map.of("transportType", "stdio", "command", "echo", "toolNames", ""));

        Map<String, List<String>> serverToolNames = Map.of(SERVER_A, ALL_TOOLS);

        TestEndpoint endpoint = createAndStartEndpoint(serverConfigs, serverToolNames);

        McpToolState state = endpoint.getMcpToolState();
        Set<String> registeredNames = state.tools().stream()
                .map(t -> t.function().name())
                .collect(Collectors.toSet());

        assertThat(registeredNames).containsExactlyInAnyOrder("read_file", "write_file", "list_directory", "delete_file");

        endpoint.doStop();
    }

    @Test
    void toolNamesWithSpacesAreTrimmed() throws Exception {
        Map<String, Map<String, String>> serverConfigs = Map.of(
                SERVER_A, Map.of("transportType", "stdio", "command", "echo", "toolNames", " read_file , list_directory "));

        Map<String, List<String>> serverToolNames = Map.of(SERVER_A, ALL_TOOLS);

        TestEndpoint endpoint = createAndStartEndpoint(serverConfigs, serverToolNames);

        McpToolState state = endpoint.getMcpToolState();
        Set<String> registeredNames = state.tools().stream()
                .map(t -> t.function().name())
                .collect(Collectors.toSet());

        assertThat(registeredNames).containsExactlyInAnyOrder("read_file", "list_directory");

        endpoint.doStop();
    }

    @Test
    void multipleServersWithDifferentFilters() throws Exception {
        Map<String, Map<String, String>> serverConfigs = new HashMap<>();
        serverConfigs.put(SERVER_A, Map.of("transportType", "stdio", "command", "echo", "toolNames", "read_file"));
        serverConfigs.put(SERVER_B, Map.of("transportType", "stdio", "command", "echo", "toolNames", "search"));

        Map<String, List<String>> serverToolNames = new HashMap<>();
        serverToolNames.put(SERVER_A, ALL_TOOLS);
        serverToolNames.put(SERVER_B, List.of("search", "index", "delete"));

        TestEndpoint endpoint = createAndStartEndpoint(serverConfigs, serverToolNames);

        McpToolState state = endpoint.getMcpToolState();
        Set<String> registeredNames = state.tools().stream()
                .map(t -> t.function().name())
                .collect(Collectors.toSet());

        assertThat(registeredNames).containsExactlyInAnyOrder("read_file", "search");

        // Verify correct server mapping
        assertThat(state.toolToServerName().get("read_file")).isEqualTo(SERVER_A);
        assertThat(state.toolToServerName().get("search")).isEqualTo(SERVER_B);

        endpoint.doStop();
    }

    @Test
    void oneServerFilteredOtherUnfiltered() throws Exception {
        Map<String, Map<String, String>> serverConfigs = new HashMap<>();
        serverConfigs.put(SERVER_A, Map.of("transportType", "stdio", "command", "echo", "toolNames", "read_file"));
        serverConfigs.put(SERVER_B, Map.of("transportType", "stdio", "command", "echo"));

        Map<String, List<String>> serverToolNames = new HashMap<>();
        serverToolNames.put(SERVER_A, ALL_TOOLS);
        serverToolNames.put(SERVER_B, List.of("search", "index"));

        TestEndpoint endpoint = createAndStartEndpoint(serverConfigs, serverToolNames);

        McpToolState state = endpoint.getMcpToolState();
        Set<String> registeredNames = state.tools().stream()
                .map(t -> t.function().name())
                .collect(Collectors.toSet());

        // Server A: only read_file (filtered); Server B: search and index (unfiltered)
        assertThat(registeredNames).containsExactlyInAnyOrder("read_file", "search", "index");

        endpoint.doStop();
    }

    @Test
    void toolNamesFilterAppliedOnReconnect() throws Exception {
        // Set up a server with toolNames filter
        Map<String, Map<String, String>> serverConfigs = new HashMap<>();
        serverConfigs.put(SERVER_A,
                new HashMap<>(Map.of("transportType", "stdio", "command", "echo", "toolNames", "read_file,list_directory")));

        Map<String, List<String>> serverToolNames = new HashMap<>();
        serverToolNames.put(SERVER_A, ALL_TOOLS);

        TestEndpoint endpoint = createAndStartEndpoint(serverConfigs, serverToolNames);

        // Verify initial state is filtered
        McpToolState initialState = endpoint.getMcpToolState();
        assertThat(initialState.tools()).hasSize(2);

        // Now trigger reconnect — the filter should still apply
        McpSyncClient oldClient = initialState.toolClientMap().get("read_file");
        McpSyncClient newClient = endpoint.reconnectMcpServer(oldClient, "read_file");
        assertThat(newClient).isNotNull();

        // Verify the reconnected state still has only the filtered tools
        McpToolState reconnectedState = endpoint.getMcpToolState();
        Set<String> reconnectedNames = reconnectedState.tools().stream()
                .map(t -> t.function().name())
                .collect(Collectors.toSet());

        assertThat(reconnectedNames).containsExactlyInAnyOrder("read_file", "list_directory");
        assertThat(reconnectedNames).doesNotContain("write_file", "delete_file");

        endpoint.doStop();
    }

    @Test
    void singleToolNameFilter() throws Exception {
        Map<String, Map<String, String>> serverConfigs = Map.of(
                SERVER_A, Map.of("transportType", "stdio", "command", "echo", "toolNames", "delete_file"));

        Map<String, List<String>> serverToolNames = Map.of(SERVER_A, ALL_TOOLS);

        TestEndpoint endpoint = createAndStartEndpoint(serverConfigs, serverToolNames);

        McpToolState state = endpoint.getMcpToolState();
        Set<String> registeredNames = state.tools().stream()
                .map(t -> t.function().name())
                .collect(Collectors.toSet());

        assertThat(registeredNames).containsExactly("delete_file");

        endpoint.doStop();
    }

    @Test
    void returnDirectToolsRespectedWithFilter() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        OpenAIComponent component = new OpenAIComponent();
        component.setCamelContext(ctx);

        Map<String, Object> flatConfig = new HashMap<>();
        flatConfig.put(SERVER_A + ".transportType", "stdio");
        flatConfig.put(SERVER_A + ".command", "echo");
        flatConfig.put(SERVER_A + ".toolNames", "read_file,direct_tool");

        OpenAIConfiguration config = new OpenAIConfiguration();
        config.setMcpServer(flatConfig);

        // Create endpoint with a tool that has returnDirect=true annotation
        McpSchema.ToolAnnotations returnDirectAnnotations = McpSchema.ToolAnnotations.builder()
                .returnDirect(true)
                .build();

        TestEndpoint endpoint = new TestEndpoint(
                component, config,
                Map.of(SERVER_A, List.of("read_file", "direct_tool", "other_tool"))) {
            @Override
            McpSyncClient createMcpClient(String serverName, Map<String, String> props) {
                McpSyncClient client = mock(McpSyncClient.class);
                List<McpSchema.Tool> tools = List.of(
                        McpSchema.Tool.builder("read_file", Map.of("type", "object")).description("read").build(),
                        McpSchema.Tool.builder("direct_tool", Map.of("type", "object"))
                                .description("direct")
                                .annotations(returnDirectAnnotations)
                                .build(),
                        McpSchema.Tool.builder("other_tool", Map.of("type", "object")).description("other").build());
                when(client.listTools()).thenReturn(McpSchema.ListToolsResult.builder(tools).build());
                return client;
            }
        };
        endpoint.setCamelContext(ctx);
        endpoint.setOperation(OpenAIOperations.chatCompletion);
        endpoint.doStart();

        McpToolState state = endpoint.getMcpToolState();

        // Only read_file and direct_tool should be registered
        assertThat(state.tools()).hasSize(2);
        assertThat(state.returnDirectTools()).containsExactly("direct_tool");
        assertThat(state.returnDirectTools()).doesNotContain("other_tool");

        endpoint.doStop();
    }
}
