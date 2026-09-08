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

class OpenAIAgenticTraceTest extends CamelTestSupport {

    private static final String ENDPOINT_URI = "openai:chat-completion?model=gpt-5&apiKey=dummy"
                                               + "&autoToolExecution=true&baseUrl=%s/v1";

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("call one tool")
            .withUsage(10, 5)
            .invokeTool("get_weather")
            .withParam("city", "London")
            .replyWith("The weather in London is sunny.")
            .end()
            .when("call two tools")
            .withUsage(12, 6)
            .invokeTool("find_location")
            .withParam("name", "Paris")
            .andThenInvokeTool("get_weather")
            .withParam("latitude", "48.8566")
            .withUsage(8, 4)
            .replyWith("The weather in Paris is cloudy.")
            .end()
            .when("no tools needed")
            .withUsage(3, 2)
            .replyWith("Just a text response")
            .end()
            .when("expensive tool call")
            .withUsage(70, 50)
            .invokeTool("get_weather")
            .withParam("city", "Paris")
            .replyWith("Should not reach this response")
            .end()
            .when("keep calling tools")
            .invokeTool("get_weather")
            .withParam("city", "A")
            .andThenInvokeTool("get_weather")
            .withParam("city", "B")
            .replyWith("Should not reach this response")
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:mcp-chat")
                        .toF("openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true&baseUrl=%s/v1",
                                openAIMock.getBaseUrl());

                from("direct:token-budget-fail")
                        .toF("openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true"
                             + "&maxAgenticTokens=100&maxToolIterations=5&baseUrl=%s/v1",
                                openAIMock.getBaseUrl());

                from("direct:max-iterations-fail")
                        .toF("openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true"
                             + "&maxToolIterations=1&baseUrl=%s/v1",
                                openAIMock.getBaseUrl());
            }
        };
    }

    private McpSyncClient createMockMcpClient(String resultText) {
        McpSyncClient client = mock(McpSyncClient.class);
        McpSchema.CallToolResult result = McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(null, resultText, null)))
                .isError(false)
                .build();
        when(client.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(result);
        return client;
    }

    private void injectMcpTools(Map<String, McpSyncClient> toolClients) {
        injectMcpTools(String.format(ENDPOINT_URI, openAIMock.getBaseUrl()), toolClients);
    }

    private void injectMcpTools(String endpointUri, Map<String, McpSyncClient> toolClients) {
        OpenAIEndpoint endpoint = context.getEndpoint(endpointUri, OpenAIEndpoint.class);
        List<McpSchema.Tool> mcpTools = toolClients.keySet().stream()
                .map(name -> McpSchema.Tool.builder(name, Map.of("type", "object"))
                        .description("Mock tool: " + name)
                        .build())
                .toList();
        endpoint.setMcpToolState(new McpToolState(
                McpToolConverter.convert(mcpTools),
                toolClients,
                Map.of(),
                Set.of(),
                Map.of()));
    }

    @Test
    void shouldExposePerIterationTraceForSingleToolBatch() {
        Map<String, McpSyncClient> toolClients = new HashMap<>();
        toolClients.put("get_weather", createMockMcpClient("Sunny, 22°C"));
        injectMcpTools(toolClients);

        Exchange exchange = template.request("direct:mcp-chat", e -> e.getIn().setBody("call one tool"));

        @SuppressWarnings("unchecked")
        List<AgenticIterationTrace> trace
                = exchange.getProperty(OpenAIConstants.AGENTIC_TRACE, List.class);

        assertThat(trace).hasSize(2);
        assertThat(trace.get(0).iteration()).isEqualTo(1);
        assertThat(trace.get(0).toolCalls()).hasSize(1);
        assertThat(trace.get(0).toolCalls().get(0).toolName()).isEqualTo("get_weather");
        assertThat(trace.get(0).toolCalls().get(0).argumentsSummary()).contains("London");
        assertThat(trace.get(0).toolCalls().get(0).resultSummary()).contains("Sunny");
        assertThat(trace.get(0).toolCalls().get(0).success()).isTrue();
        assertThat(trace.get(0).promptTokens()).isEqualTo(10);
        assertThat(trace.get(0).completionTokens()).isEqualTo(5);
        assertThat(trace.get(1).iteration()).isEqualTo(2);
        assertThat(trace.get(1).toolCalls()).isEmpty();
        assertThat(trace.get(1).promptTokens()).isGreaterThan(0);
    }

    @Test
    void shouldExposeTraceForMultiStepAgenticLoop() {
        Map<String, McpSyncClient> toolClients = new HashMap<>();
        toolClients.put("find_location", createMockMcpClient("48.8566, 2.3522"));
        toolClients.put("get_weather", createMockMcpClient("Cloudy, 15°C"));
        injectMcpTools(toolClients);

        Exchange exchange = template.request("direct:mcp-chat", e -> e.getIn().setBody("call two tools"));

        @SuppressWarnings("unchecked")
        List<AgenticIterationTrace> trace
                = exchange.getProperty(OpenAIConstants.AGENTIC_TRACE, List.class);

        assertThat(trace).hasSizeGreaterThanOrEqualTo(3);
        assertThat(trace.get(0).toolCalls()).extracting(AgenticToolCallTrace::toolName)
                .containsExactly("find_location");
        assertThat(trace.get(1).toolCalls()).extracting(AgenticToolCallTrace::toolName)
                .containsExactly("get_weather");
        assertThat(trace.get(trace.size() - 1).toolCalls()).isEmpty();
    }

    @Test
    void shouldExposeTraceWhenModelReturnsDirectAnswer() {
        Map<String, McpSyncClient> toolClients = new HashMap<>();
        toolClients.put("get_weather", createMockMcpClient("unused"));
        injectMcpTools(toolClients);

        Exchange exchange = template.request("direct:mcp-chat", e -> e.getIn().setBody("no tools needed"));

        @SuppressWarnings("unchecked")
        List<AgenticIterationTrace> trace
                = exchange.getProperty(OpenAIConstants.AGENTIC_TRACE, List.class);

        assertThat(trace).hasSize(1);
        assertThat(trace.get(0).iteration()).isEqualTo(1);
        assertThat(trace.get(0).toolCalls()).isEmpty();
        assertThat(trace.get(0).promptTokens()).isEqualTo(3);
        assertThat(trace.get(0).completionTokens()).isEqualTo(2);
    }

    @Test
    void shouldSetCumulativeTokenHeadersAlongsideTrace() {
        Map<String, McpSyncClient> toolClients = new HashMap<>();
        toolClients.put("get_weather", createMockMcpClient("Sunny, 22°C"));
        injectMcpTools(toolClients);

        Exchange exchange = template.request("direct:mcp-chat", e -> e.getIn().setBody("call one tool"));

        assertThat(exchange.getMessage().getHeader(OpenAIConstants.AGENTIC_PROMPT_TOKENS, Long.class)).isPositive();
        assertThat(exchange.getMessage().getHeader(OpenAIConstants.AGENTIC_TOTAL_TOKENS, Long.class)).isPositive();
        assertThat(exchange.getProperty(OpenAIConstants.AGENTIC_TRACE)).isNotNull();
    }

    @Test
    void shouldPublishTraceWhenTokenBudgetExceeded() {
        Map<String, McpSyncClient> toolClients = new HashMap<>();
        toolClients.put("get_weather", createMockMcpClient("Sunny, 22°C"));
        String endpointUri = String.format(
                "openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true"
                                           + "&maxAgenticTokens=100&maxToolIterations=5&baseUrl=%s/v1",
                openAIMock.getBaseUrl());
        injectMcpTools(endpointUri, toolClients);

        Exchange exchange = template.request("direct:token-budget-fail", e -> e.getIn().setBody("expensive tool call"));

        assertThat(exchange.getException()).isInstanceOf(IllegalStateException.class);
        @SuppressWarnings("unchecked")
        List<AgenticIterationTrace> trace
                = exchange.getProperty(OpenAIConstants.AGENTIC_TRACE, List.class);
        assertThat(trace).isNotNull().hasSize(1);
        assertThat(trace.get(0).iteration()).isEqualTo(1);
        assertThat(trace.get(0).toolCalls()).isEmpty();
        assertThat(trace.get(0).promptTokens()).isEqualTo(70);
        assertThat(trace.get(0).completionTokens()).isEqualTo(50);
    }

    @Test
    void shouldPublishTraceWhenMaxIterationsExceeded() {
        Map<String, McpSyncClient> toolClients = new HashMap<>();
        toolClients.put("get_weather", createMockMcpClient("Sunny, 22°C"));
        String endpointUri = String.format(
                "openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true"
                                           + "&maxToolIterations=1&baseUrl=%s/v1",
                openAIMock.getBaseUrl());
        injectMcpTools(endpointUri, toolClients);

        Exchange exchange = template.request("direct:max-iterations-fail", e -> e.getIn().setBody("keep calling tools"));

        assertThat(exchange.getException()).isInstanceOf(IllegalStateException.class);
        @SuppressWarnings("unchecked")
        List<AgenticIterationTrace> trace
                = exchange.getProperty(OpenAIConstants.AGENTIC_TRACE, List.class);
        assertThat(trace).isNotNull().isNotEmpty();
        assertThat(trace.get(0).toolCalls()).isNotEmpty();
    }
}
