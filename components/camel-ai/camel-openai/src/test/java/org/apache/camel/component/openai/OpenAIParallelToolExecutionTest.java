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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end tests for CAMEL-23078: {@code parallelToolExecution=true} in the agentic loop.
 */
class OpenAIParallelToolExecutionTest extends CamelTestSupport {

    private static final long AWAIT_SECONDS = 10;

    /**
     * Every tool blocks until both have started, so the agentic loop can only make progress when the batch is
     * dispatched concurrently.
     */
    private final CountDownLatch rendezvous = new CountDownLatch(2);

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("call both tools")
            .invokeTool("get_weather")
            .withParam("city", "London")
            .andInvokeTool("get_traffic")
            .withParam("city", "London")
            .replyWith("London is sunny with light traffic.")
            .end()
            .build();

    private String endpointUri(boolean parallel) {
        return "openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true"
               + "&parallelToolExecution=" + parallel
               + "&baseUrl=" + openAIMock.getBaseUrl() + "/v1";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:parallel").to(endpointUri(true));
                from("direct:sequential").to(endpointUri(false));
            }
        };
    }

    @Test
    void parallelBatchIsDispatchedConcurrently() {
        injectMcpTools(endpointUri(true), Set.of());

        Exchange result = template.request("direct:parallel", e -> e.getIn().setBody("call both tools"));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo("London is sunny with light traffic.");
        assertThat(result.getMessage().getHeader(OpenAIConstants.TOOL_ITERATIONS, Integer.class)).isEqualTo(1);
        assertThat(result.getMessage().getHeader(OpenAIConstants.MCP_TOOL_CALLS, List.class))
                .containsExactly("get_weather", "get_traffic");
        assertThat(rendezvous.getCount()).isZero();
    }

    @Test
    void parallelBatchOfReturnDirectToolsShortCircuitsInOrder() {
        injectMcpTools(endpointUri(true), Set.of("get_weather", "get_traffic"));

        Exchange result = template.request("direct:parallel", e -> e.getIn().setBody("call both tools"));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getHeader(OpenAIConstants.MCP_RETURN_DIRECT, Boolean.class)).isTrue();
        // results are joined in the order the model requested the tools, not in completion order
        assertThat(result.getMessage().getBody(String.class)).isEqualTo("Sunny, 22C\nLight traffic");
    }

    @Test
    void sequentialExecutionRemainsTheDefaultBehavior() {
        // no rendezvous: the sequential path would deadlock on it, so use independent clients
        Map<String, McpSyncClient> toolClients = new LinkedHashMap<>();
        toolClients.put("get_weather", staticClient("Sunny, 22C"));
        toolClients.put("get_traffic", staticClient("Light traffic"));
        injectMcpTools(endpointUri(false), toolClients, Set.of());

        Exchange result = template.request("direct:sequential", e -> e.getIn().setBody("call both tools"));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo("London is sunny with light traffic.");
        assertThat(result.getMessage().getHeader(OpenAIConstants.MCP_TOOL_CALLS, List.class))
                .containsExactly("get_weather", "get_traffic");
    }

    private void injectMcpTools(String endpointKey, Set<String> returnDirectToolNames) {
        Map<String, McpSyncClient> toolClients = new LinkedHashMap<>();
        toolClients.put("get_weather", rendezvousClient("Sunny, 22C"));
        toolClients.put("get_traffic", rendezvousClient("Light traffic"));
        injectMcpTools(endpointKey, toolClients, returnDirectToolNames);
    }

    private void injectMcpTools(
            String endpointKey, Map<String, McpSyncClient> toolClients, Set<String> returnDirectToolNames) {
        OpenAIEndpoint endpoint = context.getEndpoint(endpointKey, OpenAIEndpoint.class);

        List<McpSchema.Tool> mcpTools = toolClients.keySet().stream()
                .map(name -> McpSchema.Tool.builder(name, Map.of("type", "object"))
                        .description("Mock tool: " + name)
                        .build())
                .toList();

        endpoint.setMcpToolState(new McpToolState(
                McpToolConverter.convert(mcpTools), toolClients, Map.of(), returnDirectToolNames, Map.of()));
    }

    private McpSyncClient rendezvousClient(String resultText) {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.callTool(any(McpSchema.CallToolRequest.class))).thenAnswer(invocation -> {
            rendezvous.countDown();
            if (!rendezvous.await(AWAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("tool calls were not dispatched concurrently");
            }
            return textResult(resultText);
        });
        return client;
    }

    private static McpSyncClient staticClient(String resultText) {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(textResult(resultText));
        return client;
    }

    private static McpSchema.CallToolResult textResult(String text) {
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(null, text, null)))
                .isError(false)
                .build();
    }
}
