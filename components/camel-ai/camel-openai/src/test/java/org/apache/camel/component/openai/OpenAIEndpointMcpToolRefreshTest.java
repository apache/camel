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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for CAMEL-23078: handling of the MCP {@code tools/list_changed} notification, which refreshes the advertised
 * tool list at runtime instead of keeping whatever was listed when the endpoint started.
 */
class OpenAIEndpointMcpToolRefreshTest {

    private static final String SERVER = "test-server";
    private static final String OTHER_SERVER = "other-server";

    @Test
    void refreshRegistersToolsAddedByTheServer() {
        McpSyncClient client = mock(McpSyncClient.class);
        OpenAIEndpoint endpoint = newEndpoint(client, List.of("get_weather"));

        endpoint.onToolsChanged(SERVER, client, tools("get_weather", "get_traffic"));

        assertThat(endpoint.getMcpToolState().toolClientMap()).containsOnlyKeys("get_weather", "get_traffic");
        assertThat(toolNames(endpoint)).containsExactlyInAnyOrder("get_weather", "get_traffic");
        assertThat(endpoint.getMcpToolState().toolToServerName())
                .containsEntry("get_traffic", SERVER);
    }

    @Test
    void refreshDropsToolsRemovedByTheServer() {
        McpSyncClient client = mock(McpSyncClient.class);
        OpenAIEndpoint endpoint = newEndpoint(client, List.of("get_weather", "get_traffic"));

        endpoint.onToolsChanged(SERVER, client, tools("get_weather"));

        assertThat(endpoint.getMcpToolState().toolClientMap()).containsOnlyKeys("get_weather");
        assertThat(toolNames(endpoint)).containsExactly("get_weather");
        assertThat(endpoint.getMcpToolState().toolToServerName()).doesNotContainKey("get_traffic");
    }

    @Test
    void refreshLeavesOtherServersUntouched() {
        McpSyncClient client = mock(McpSyncClient.class);
        McpSyncClient otherClient = mock(McpSyncClient.class);
        OpenAIEndpoint endpoint = newEndpoint(client, List.of("get_weather"));
        registerServer(endpoint, OTHER_SERVER, otherClient, List.of("send_email"));

        endpoint.onToolsChanged(SERVER, client, tools("get_traffic"));

        assertThat(endpoint.getMcpToolState().toolClientMap())
                .containsEntry("send_email", otherClient)
                .containsEntry("get_traffic", client)
                .doesNotContainKey("get_weather");
    }

    @Test
    void refreshReappliesThePerServerToolNamesFilter() {
        // the SDK hands the consumer the unfiltered tool list, so the include list must be applied again
        McpSyncClient client = mock(McpSyncClient.class);
        OpenAIEndpoint endpoint = newEndpoint(client, List.of("get_weather"));
        endpoint.setServerConfigs(new ConcurrentHashMap<>(
                Map.of(
                        SERVER, Map.of("transportType", "stdio", "toolNames", "get_weather,get_traffic"))));

        endpoint.onToolsChanged(SERVER, client, tools("get_weather", "get_traffic", "delete_everything"));

        assertThat(endpoint.getMcpToolState().toolClientMap())
                .containsOnlyKeys("get_weather", "get_traffic");
    }

    @Test
    void refreshSkipsToolNamesAlreadyRegisteredByAnotherServer() {
        McpSyncClient client = mock(McpSyncClient.class);
        McpSyncClient otherClient = mock(McpSyncClient.class);
        OpenAIEndpoint endpoint = newEndpoint(client, List.of("get_weather"));
        registerServer(endpoint, OTHER_SERVER, otherClient, List.of("get_traffic"));

        endpoint.onToolsChanged(SERVER, client, tools("get_weather", "get_traffic"));

        // the duplicate stays with the server that registered it first
        assertThat(endpoint.getMcpToolState().toolClientMap()).containsEntry("get_traffic", otherClient);
    }

    @Test
    void refreshTracksReturnDirectAnnotations() {
        McpSyncClient client = mock(McpSyncClient.class);
        OpenAIEndpoint endpoint = newEndpoint(client, List.of("get_weather"));

        endpoint.onToolsChanged(SERVER, client, List.of(
                tool("get_weather"), returnDirectTool("fetch_report")));

        assertThat(endpoint.getMcpToolState().returnDirectTools()).containsExactly("fetch_report");
    }

    @Test
    void refreshPreservesManualReturnDirectOverrides() {
        McpSyncClient client = mock(McpSyncClient.class);
        OpenAIEndpoint endpoint = newEndpoint(client, List.of("get_weather"));
        endpoint.addReturnDirectTool("get_weather");

        endpoint.onToolsChanged(SERVER, client, tools("get_weather", "get_traffic"));

        assertThat(endpoint.getMcpToolState().returnDirectTools()).contains("get_weather");
    }

    @Test
    void refreshPreservesManualReturnDirectRemovals() {
        McpSyncClient client = mock(McpSyncClient.class);
        OpenAIEndpoint endpoint = newEndpoint(client, List.of("fetch_report"));
        endpoint.removeReturnDirectTool("fetch_report");

        endpoint.onToolsChanged(SERVER, client, List.of(returnDirectTool("fetch_report")));

        assertThat(endpoint.getMcpToolState().returnDirectTools()).doesNotContain("fetch_report");
    }

    @Test
    void refreshDoesNotReinstateAnOverrideForAToolThatVanished() {
        McpSyncClient client = mock(McpSyncClient.class);
        OpenAIEndpoint endpoint = newEndpoint(client, List.of("get_weather", "get_traffic"));
        endpoint.addReturnDirectTool("get_traffic");

        endpoint.onToolsChanged(SERVER, client, tools("get_weather"));

        assertThat(endpoint.getMcpToolState().returnDirectTools()).doesNotContain("get_traffic");
    }

    @Test
    void refreshIsIgnoredWhenDisabled() {
        McpSyncClient client = mock(McpSyncClient.class);
        OpenAIEndpoint endpoint = newEndpoint(client, List.of("get_weather"));
        endpoint.getConfiguration().setMcpToolRefresh(false);

        endpoint.onToolsChanged(SERVER, client, tools("get_weather", "get_traffic"));

        assertThat(endpoint.getMcpToolState().toolClientMap()).containsOnlyKeys("get_weather");
    }

    @Test
    void refreshFromASupersededClientIsIgnored() {
        // a reconnect already replaced the client, so a notification still in flight from the old one is stale
        McpSyncClient reconnected = mock(McpSyncClient.class);
        McpSyncClient superseded = mock(McpSyncClient.class);
        OpenAIEndpoint endpoint = newEndpoint(reconnected, List.of("get_weather"));

        endpoint.onToolsChanged(SERVER, superseded, tools("get_weather", "get_traffic"));

        assertThat(endpoint.getMcpToolState().toolClientMap())
                .containsOnlyKeys("get_weather")
                .containsEntry("get_weather", reconnected);
    }

    @Test
    void refreshForAnUnknownServerIsIgnored() {
        McpSyncClient client = mock(McpSyncClient.class);
        OpenAIEndpoint endpoint = newEndpoint(client, List.of("get_weather"));

        endpoint.onToolsChanged("never-configured", client, tools("get_traffic"));

        assertThat(endpoint.getMcpToolState().toolClientMap()).containsOnlyKeys("get_weather");
    }

    @Test
    void refreshBeforeTheClientIsReadyIsIgnored() {
        McpSyncClient client = mock(McpSyncClient.class);
        OpenAIEndpoint endpoint = newEndpoint(client, List.of("get_weather"));

        endpoint.onToolsChanged(SERVER, null, tools("get_traffic"));

        assertThat(endpoint.getMcpToolState().toolClientMap()).containsOnlyKeys("get_weather");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private OpenAIEndpoint newEndpoint(McpSyncClient client, List<String> initialTools) {
        DefaultCamelContext ctx = new DefaultCamelContext();
        OpenAIComponent component = new OpenAIComponent();
        component.setCamelContext(ctx);

        OpenAIEndpoint endpoint = new OpenAIEndpoint("openai:chat-completion", component, new OpenAIConfiguration());
        endpoint.setCamelContext(ctx);

        endpoint.setMcpToolState(McpToolState.empty());
        endpoint.setServerConfigs(new ConcurrentHashMap<>(Map.of(SERVER, Map.of("transportType", "stdio"))));
        endpoint.setMcpClientLocks(new ConcurrentHashMap<>(Map.of(SERVER, new ReentrantLock())));

        registerServer(endpoint, SERVER, client, initialTools);
        return endpoint;
    }

    /**
     * Seeds the shared tool state with the tools of one server, mirroring what endpoint startup would have published.
     */
    private void registerServer(
            OpenAIEndpoint endpoint, String serverName, McpSyncClient client, List<String> toolNames) {
        McpToolState current = endpoint.getMcpToolState();

        List<ChatCompletionFunctionTool> allTools = new ArrayList<>(current.tools());
        Map<String, McpSyncClient> clientMap = new HashMap<>(current.toolClientMap());
        Map<String, String> toolToServer = new HashMap<>(current.toolToServerName());

        List<McpSchema.Tool> mcpTools = toolNames.stream().map(OpenAIEndpointMcpToolRefreshTest::tool).toList();
        allTools.addAll(McpToolConverter.convert(mcpTools));
        toolNames.forEach(name -> {
            clientMap.put(name, client);
            toolToServer.put(name, serverName);
        });

        endpoint.setMcpToolState(
                new McpToolState(allTools, clientMap, toolToServer, current.returnDirectTools(), Map.of()));
    }

    private static List<String> toolNames(OpenAIEndpoint endpoint) {
        return endpoint.getMcpToolState().tools().stream()
                .map(t -> t.function().name())
                .toList();
    }

    private static List<McpSchema.Tool> tools(String... names) {
        return List.of(names).stream().map(OpenAIEndpointMcpToolRefreshTest::tool).toList();
    }

    private static McpSchema.Tool tool(String name) {
        return McpSchema.Tool.builder(name, Map.of("type", "object")).description("mock " + name).build();
    }

    private static McpSchema.Tool returnDirectTool(String name) {
        return McpSchema.Tool.builder(name, Map.of("type", "object"))
                .description("mock " + name)
                .annotations(McpSchema.ToolAnnotations.builder().returnDirect(true).build())
                .build();
    }
}
