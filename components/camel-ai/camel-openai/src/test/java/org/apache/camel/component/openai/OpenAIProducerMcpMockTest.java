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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OpenAIProducerMcpMockTest extends CamelTestSupport {

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            // Single tool call: model calls tool once, then returns text
            .when("call one tool")
            .invokeTool("get_weather")
            .withParam("city", "London")
            .replyWith("The weather in London is sunny.")
            .end()
            // Multi-step: model calls tool A, then tool B, then text
            .when("call two tools")
            .invokeTool("find_location")
            .withParam("name", "Paris")
            .andThenInvokeTool("get_weather")
            .withParam("latitude", "48.8566")
            .replyWith("The weather in Paris is cloudy.")
            .end()
            // No tools needed: just text response (backward compat)
            .when("no tools needed")
            .replyWith("Just a text response")
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:mcp-chat")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");

                from("direct:mcp-chat-no-auto")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=false&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");

                from("direct:mcp-chat-memory")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true&conversationMemory=true&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");

                from("direct:mcp-chat-max1")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true&maxToolIterations=1&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");
            }
        };
    }

    private McpSyncClient createMockMcpClient(String toolName, String resultText) {
        McpSyncClient client = mock(McpSyncClient.class);
        McpSchema.CallToolResult result = McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(resultText)))
                .isError(false)
                .build();
        when(client.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(result);
        return client;
    }

    private McpSyncClient createErrorMcpClient(String toolName) {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.callTool(any(McpSchema.CallToolRequest.class)))
                .thenThrow(new RuntimeException("MCP connection failed"));
        return client;
    }

    private void injectMcpTools(String endpointKey, Map<String, McpSyncClient> toolClients) {
        injectMcpTools(endpointKey, toolClients, Set.of());
    }

    private void injectMcpTools(
            String endpointKey, Map<String, McpSyncClient> toolClients,
            Set<String> returnDirectToolNames) {
        OpenAIEndpoint endpoint = context.getEndpoint(endpointKey, OpenAIEndpoint.class);

        // Convert tool names to OpenAI function tools via McpToolConverter
        List<McpSchema.Tool> mcpTools = toolClients.keySet().stream()
                .map(name -> McpSchema.Tool.builder()
                        .name(name)
                        .description("Mock tool: " + name)
                        .inputSchema(new McpSchema.JsonSchema("object", null, null, null, null, null))
                        .build())
                .toList();

        endpoint.setMcpTools(McpToolConverter.convert(mcpTools));
        endpoint.setToolClientMap(toolClients);
        endpoint.setReturnDirectTools(returnDirectToolNames);
    }

    @Test
    void singleToolCallLoop() {
        String endpointUri = "openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true&baseUrl="
                             + openAIMock.getBaseUrl() + "/v1";

        Map<String, McpSyncClient> toolClients = new HashMap<>();
        toolClients.put("get_weather", createMockMcpClient("get_weather", "Sunny, 22°C"));
        injectMcpTools(endpointUri, toolClients);

        Exchange result = template.request("direct:mcp-chat", e -> e.getIn().setBody("call one tool"));

        assertNotNull(result.getMessage().getBody(String.class));
        assertEquals("The weather in London is sunny.", result.getMessage().getBody(String.class));
        assertEquals(1, result.getMessage().getHeader(OpenAIConstants.TOOL_ITERATIONS, Integer.class));
        assertFalse(result.getMessage().getHeader(OpenAIConstants.MCP_RETURN_DIRECT, Boolean.class));

        @SuppressWarnings("unchecked")
        List<String> toolCalls = result.getMessage().getHeader(OpenAIConstants.MCP_TOOL_CALLS, List.class);
        assertNotNull(toolCalls);
        assertEquals(1, toolCalls.size());
        assertEquals("get_weather", toolCalls.get(0));
    }

    @Test
    void multiStepToolCalls() {
        String endpointUri = "openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true&baseUrl="
                             + openAIMock.getBaseUrl() + "/v1";

        Map<String, McpSyncClient> toolClients = new HashMap<>();
        toolClients.put("find_location", createMockMcpClient("find_location", "48.8566, 2.3522"));
        toolClients.put("get_weather", createMockMcpClient("get_weather", "Cloudy, 15°C"));
        injectMcpTools(endpointUri, toolClients);

        Exchange result = template.request("direct:mcp-chat", e -> e.getIn().setBody("call two tools"));

        assertEquals("The weather in Paris is cloudy.", result.getMessage().getBody(String.class));
        assertEquals(2, result.getMessage().getHeader(OpenAIConstants.TOOL_ITERATIONS, Integer.class));
        assertFalse(result.getMessage().getHeader(OpenAIConstants.MCP_RETURN_DIRECT, Boolean.class));

        @SuppressWarnings("unchecked")
        List<String> toolCalls = result.getMessage().getHeader(OpenAIConstants.MCP_TOOL_CALLS, List.class);
        assertEquals(2, toolCalls.size());
        assertEquals("find_location", toolCalls.get(0));
        assertEquals("get_weather", toolCalls.get(1));
    }

    @Test
    void autoToolExecutionDisabledReturnsRawToolCalls() {
        String endpointUri = "openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=false&baseUrl="
                             + openAIMock.getBaseUrl() + "/v1";

        Map<String, McpSyncClient> toolClients = new HashMap<>();
        toolClients.put("get_weather", createMockMcpClient("get_weather", "result"));
        injectMcpTools(endpointUri, toolClients);

        Exchange result = template.request("direct:mcp-chat-no-auto", e -> e.getIn().setBody("call one tool"));

        // With autoToolExecution=false, the body is the raw toolCalls() Optional from the SDK
        Object body = result.getMessage().getBody();
        assertNotNull(body);
        assertInstanceOf(Optional.class, body);
    }

    @Test
    void toolExecutionErrorSentToModel() {
        String endpointUri = "openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true&baseUrl="
                             + openAIMock.getBaseUrl() + "/v1";

        Map<String, McpSyncClient> toolClients = new HashMap<>();
        toolClients.put("get_weather", createErrorMcpClient("get_weather"));
        injectMcpTools(endpointUri, toolClients);

        // The error is caught and sent back to the model; the mock responds with final text
        Exchange result = template.request("direct:mcp-chat", e -> e.getIn().setBody("call one tool"));

        // The model should still produce a final response (the mock handles this)
        assertNotNull(result.getMessage().getBody(String.class));
        assertFalse(result.getMessage().getHeader(OpenAIConstants.MCP_RETURN_DIRECT, Boolean.class));
    }

    @Test
    void noToolCallsReturnNormalResponse() {
        String endpointUri = "openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true&baseUrl="
                             + openAIMock.getBaseUrl() + "/v1";

        Map<String, McpSyncClient> toolClients = new HashMap<>();
        toolClients.put("get_weather", createMockMcpClient("get_weather", "result"));
        injectMcpTools(endpointUri, toolClients);

        Exchange result = template.request("direct:mcp-chat", e -> e.getIn().setBody("no tools needed"));

        assertEquals("Just a text response", result.getMessage().getBody(String.class));
        assertEquals(0, result.getMessage().getHeader(OpenAIConstants.TOOL_ITERATIONS, Integer.class));
        assertFalse(result.getMessage().getHeader(OpenAIConstants.MCP_RETURN_DIRECT, Boolean.class));
    }

    @Test
    void conversationMemoryIncludesToolCallChain() {
        String endpointUri
                = "openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true&conversationMemory=true&baseUrl="
                  + openAIMock.getBaseUrl() + "/v1";

        Map<String, McpSyncClient> toolClients = new HashMap<>();
        toolClients.put("get_weather", createMockMcpClient("get_weather", "Sunny, 22°C"));
        injectMcpTools(endpointUri, toolClients);

        Exchange result = template.request("direct:mcp-chat-memory", e -> e.getIn().setBody("call one tool"));

        // Conversation history should contain the tool call chain
        @SuppressWarnings("unchecked")
        List<?> history = result.getProperty("CamelOpenAIConversationHistory", List.class);
        assertNotNull(history);
        // Should contain: assistant+toolCalls, tool result, final assistant
        assertTrue(history.size() >= 3, "Expected at least 3 entries in history, got " + history.size());
    }

    @Test
    void returnDirectSingleTool() {
        String endpointUri = "openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true&baseUrl="
                             + openAIMock.getBaseUrl() + "/v1";

        Map<String, McpSyncClient> toolClients = new HashMap<>();
        toolClients.put("get_weather", createMockMcpClient("get_weather", "Direct result: Sunny!"));
        Set<String> returnDirect = new HashSet<>();
        returnDirect.add("get_weather");
        injectMcpTools(endpointUri, toolClients, returnDirect);

        Exchange result = template.request("direct:mcp-chat", e -> e.getIn().setBody("call one tool"));

        // returnDirect should short-circuit: body is the tool result, not LLM text
        assertEquals("Direct result: Sunny!", result.getMessage().getBody(String.class));
        assertTrue(result.getMessage().getHeader(OpenAIConstants.MCP_RETURN_DIRECT, Boolean.class));
        assertEquals(1, result.getMessage().getHeader(OpenAIConstants.TOOL_ITERATIONS, Integer.class));
    }

    @Test
    void returnDirectWithErrorFallsBackToModel() {
        String endpointUri = "openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true&baseUrl="
                             + openAIMock.getBaseUrl() + "/v1";

        Map<String, McpSyncClient> toolClients = new HashMap<>();
        toolClients.put("get_weather", createErrorMcpClient("get_weather"));
        Set<String> returnDirect = new HashSet<>();
        returnDirect.add("get_weather");
        injectMcpTools(endpointUri, toolClients, returnDirect);

        // Error should override returnDirect — results go back to model
        Exchange result = template.request("direct:mcp-chat", e -> e.getIn().setBody("call one tool"));

        assertNotNull(result.getMessage().getBody(String.class));
        assertFalse(result.getMessage().getHeader(OpenAIConstants.MCP_RETURN_DIRECT, Boolean.class));
    }

    @Test
    void returnDirectAnnotationNullTreatedAsNonDirect() {
        String endpointUri = "openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true&baseUrl="
                             + openAIMock.getBaseUrl() + "/v1";

        Map<String, McpSyncClient> toolClients = new HashMap<>();
        toolClients.put("get_weather", createMockMcpClient("get_weather", "result"));
        // Empty returnDirect set means no tools have returnDirect
        injectMcpTools(endpointUri, toolClients, Set.of());

        Exchange result = template.request("direct:mcp-chat", e -> e.getIn().setBody("call one tool"));

        // Should NOT short-circuit — results go back to model
        assertFalse(result.getMessage().getHeader(OpenAIConstants.MCP_RETURN_DIRECT, Boolean.class));
        assertEquals("The weather in London is sunny.", result.getMessage().getBody(String.class));
    }
}
