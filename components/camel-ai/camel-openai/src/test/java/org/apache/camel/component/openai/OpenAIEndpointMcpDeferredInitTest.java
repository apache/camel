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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.openai.client.OpenAIClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that an MCP server that is unreachable when the endpoint starts does not fail the route: initialization is
 * deferred and retried on first use. This is the self-referential agent scenario, where an application consumes its own
 * MCP endpoint and the HTTP server only accepts connections after the application has started.
 */
class OpenAIEndpointMcpDeferredInitTest {

    private static final String SERVER = "orderDesk";
    private static final List<String> SERVER_TOOLS = List.of("say_hello", "list_orders");

    /**
     * Overrides the client-creation seam: throws while {@code serverUp} is false, simulating an MCP server that is not
     * yet accepting connections, and returns a mock client once the server is "up".
     */
    private static class TestEndpoint extends OpenAIEndpoint {
        volatile boolean serverUp;
        final AtomicInteger connectionAttempts = new AtomicInteger();

        TestEndpoint(OpenAIComponent component, OpenAIConfiguration config) {
            super("openai:chat-completion", component, config);
        }

        @Override
        McpSyncClient createMcpClient(String serverName, Map<String, String> props) {
            connectionAttempts.incrementAndGet();
            if (!serverUp) {
                throw new RuntimeException("Client failed to initialize by explicit API call");
            }
            McpSyncClient client = mock(McpSyncClient.class);
            List<McpSchema.Tool> tools = SERVER_TOOLS.stream()
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

    private TestEndpoint createEndpoint() {
        DefaultCamelContext ctx = new DefaultCamelContext();
        OpenAIComponent component = new OpenAIComponent();
        component.setCamelContext(ctx);

        Map<String, Object> flatConfig = new HashMap<>();
        flatConfig.put(SERVER + ".transportType", "streamableHttp");
        flatConfig.put(SERVER + ".url", "http://localhost:1/mcp");

        OpenAIConfiguration config = new OpenAIConfiguration();
        config.setMcpServer(flatConfig);

        TestEndpoint endpoint = new TestEndpoint(component, config);
        endpoint.setCamelContext(ctx);
        endpoint.setOperation(OpenAIOperations.chatCompletion);
        return endpoint;
    }

    private static Set<String> toolNames(McpToolState state) {
        return state.tools().stream().map(t -> t.function().name()).collect(Collectors.toSet());
    }

    @Test
    void unreachableServerDoesNotFailEndpointStart() throws Exception {
        TestEndpoint endpoint = createEndpoint();

        assertThatCode(endpoint::doStart).doesNotThrowAnyException();
        assertThat(endpoint.connectionAttempts).hasValue(1);

        endpoint.doStop();
    }

    @Test
    void toolsAppearOnFirstUseOnceServerIsReachable() throws Exception {
        TestEndpoint endpoint = createEndpoint();
        endpoint.doStart();

        // still down on first use: no tools, but no failure either
        assertThat(toolNames(endpoint.getMcpToolState())).isEmpty();

        endpoint.serverUp = true;
        McpToolState state = endpoint.getMcpToolState();
        assertThat(toolNames(state)).containsExactlyInAnyOrderElementsOf(SERVER_TOOLS);
        assertThat(state.toolToServerName()).containsValues(SERVER);

        // once initialized, further uses do not attempt new connections
        int attempts = endpoint.connectionAttempts.get();
        endpoint.getMcpToolState();
        assertThat(endpoint.connectionAttempts).hasValue(attempts);

        endpoint.doStop();
    }

    @Test
    void reachableServerInitializesEagerlyAtStart() throws Exception {
        TestEndpoint endpoint = createEndpoint();
        endpoint.serverUp = true;

        endpoint.doStart();
        assertThat(endpoint.connectionAttempts).hasValue(1);
        assertThat(toolNames(endpoint.getMcpToolState())).containsExactlyInAnyOrderElementsOf(SERVER_TOOLS);
        assertThat(endpoint.connectionAttempts).hasValue(1);

        endpoint.doStop();
    }

    @Test
    void stoppedEndpointDoesNotRetryPendingServers() throws Exception {
        TestEndpoint endpoint = createEndpoint();
        endpoint.doStart();
        endpoint.doStop();

        endpoint.serverUp = true;
        assertThat(toolNames(endpoint.getMcpToolState())).isEmpty();
        assertThat(endpoint.connectionAttempts).hasValue(1);
    }
}
