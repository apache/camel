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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the configurable tool error strategies introduced by CAMEL-23963.
 *
 * <p>
 * Two endpoint options are tested:
 * <ul>
 * <li>{@code hallucinatedToolNameStrategy} -- controls what happens when the model requests a tool that does not exist
 * in any configured MCP server.</li>
 * <li>{@code toolExecutionErrorStrategy} -- controls what happens when a tool execution throws an exception.</li>
 * </ul>
 */
class OpenAIToolErrorStrategyTest extends CamelTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AtomicInteger hallucinatedCallCounter = new AtomicInteger();
    private final AtomicInteger toolErrorCallCounter = new AtomicInteger();
    private final AtomicInteger repromptCallCounter = new AtomicInteger();

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            // hallucinated tool: first call returns a tool call with a non-existent tool name,
            // second call (after corrective feedback) returns the final answer
            .when("use hallucinated tool")
            .thenRespondWith((exchange, input) -> {
                try {
                    if (hallucinatedCallCounter.getAndIncrement() == 0) {
                        return toolCallResponse("nonexistent_tool", "{\"arg\": \"value\"}");
                    }
                    return simpleTextResponse("Recovered after hallucinated tool correction");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .end()
            // tool execution error: first call returns a tool call that will throw
            .when("trigger tool error")
            .thenRespondWith((exchange, input) -> {
                try {
                    if (toolErrorCallCounter.getAndIncrement() == 0) {
                        return toolCallResponse("get_weather", "{\"city\": \"London\"}");
                    }
                    return simpleTextResponse("Should not reach here");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .end()
            // tool execution error with repromptModel: first call triggers error,
            // second call (after error feedback) returns the final answer
            .when("trigger tool error reprompt")
            .thenRespondWith((exchange, input) -> {
                try {
                    if (repromptCallCounter.getAndIncrement() == 0) {
                        return toolCallResponse("get_weather", "{\"city\": \"London\"}");
                    }
                    return simpleTextResponse("Recovered after tool error feedback");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Route with hallucinatedToolNameStrategy=repromptModel
                from("direct:hallucinated-reprompt")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true"
                            + "&hallucinatedToolNameStrategy=repromptModel&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");

                // Route with default toolExecutionErrorStrategy (failExchange)
                from("direct:error-fail-exchange")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");

                // Route with toolExecutionErrorStrategy=repromptModel
                from("direct:error-reprompt")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true"
                            + "&toolExecutionErrorStrategy=repromptModel&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");
            }
        };
    }

    @Test
    void hallucinatedToolNameRepromptModelSendsCorrectiveResultToModel() {
        // Set up a real tool so the model can self-correct
        McpSyncClient client = mock(McpSyncClient.class);
        McpSchema.CallToolResult toolResult = McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(null, "Sunny", null)))
                .isError(false)
                .build();
        when(client.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(toolResult);

        String endpointUri = "openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true"
                             + "&hallucinatedToolNameStrategy=repromptModel&baseUrl="
                             + openAIMock.getBaseUrl() + "/v1";
        OpenAIEndpoint endpoint = context.getEndpoint(endpointUri, OpenAIEndpoint.class);
        List<McpSchema.Tool> mcpTools = List.of(
                McpSchema.Tool.builder("get_weather", Map.of("type", "object"))
                        .description("Mock tool: get_weather")
                        .build());
        endpoint.setMcpToolState(new McpToolState(
                McpToolConverter.convert(mcpTools),
                Map.of("get_weather", client),
                Map.of(),
                Set.of(), Map.of()));

        Exchange result = template.request("direct:hallucinated-reprompt",
                e -> e.getIn().setBody("use hallucinated tool"));

        assertThat(result.getException())
                .as("With repromptModel, hallucinated tool names should not crash the exchange")
                .isNull();
        assertThat(result.getMessage().getBody(String.class))
                .isEqualTo("Recovered after hallucinated tool correction");
    }

    @Test
    void hallucinatedToolNameDefaultFailExchangeThrowsException() {
        // Use the default strategy (failExchange) via a separate endpoint
        McpSyncClient client = mock(McpSyncClient.class);
        String defaultUri = "openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1";
        OpenAIEndpoint endpoint = context.getEndpoint(defaultUri, OpenAIEndpoint.class);
        List<McpSchema.Tool> mcpTools = List.of(
                McpSchema.Tool.builder("get_weather", Map.of("type", "object"))
                        .description("Mock tool: get_weather")
                        .build());
        endpoint.setMcpToolState(new McpToolState(
                McpToolConverter.convert(mcpTools),
                Map.of("get_weather", client),
                Map.of(),
                Set.of(), Map.of()));

        // The default hallucinatedToolNameStrategy is failExchange, so this should throw
        assertThatThrownBy(() -> template.requestBody(defaultUri, "use hallucinated tool"))
                .isInstanceOf(CamelExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .cause()
                .hasMessageContaining("not found in any configured MCP server");
    }

    @Test
    void toolExecutionErrorDefaultFailExchangePropagatesToExchange() {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.callTool(any(McpSchema.CallToolRequest.class)))
                .thenThrow(new RuntimeException("Connection refused to weather service"));

        String endpointUri = "openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true&baseUrl="
                             + openAIMock.getBaseUrl() + "/v1";
        OpenAIEndpoint endpoint = context.getEndpoint(endpointUri, OpenAIEndpoint.class);
        List<McpSchema.Tool> mcpTools = List.of(
                McpSchema.Tool.builder("get_weather", Map.of("type", "object"))
                        .description("Mock tool: get_weather")
                        .build());
        endpoint.setMcpToolState(new McpToolState(
                McpToolConverter.convert(mcpTools),
                Map.of("get_weather", client),
                Map.of(),
                Set.of(), Map.of()));

        // The default toolExecutionErrorStrategy is failExchange, so this should propagate
        assertThatThrownBy(() -> template.requestBody("direct:error-fail-exchange", "trigger tool error"))
                .isInstanceOf(CamelExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .cause()
                .hasMessageContaining("Connection refused to weather service");
    }

    @Test
    void toolExecutionErrorRepromptModelSendsErrorBackToModel() {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.callTool(any(McpSchema.CallToolRequest.class)))
                .thenThrow(new RuntimeException("Connection refused to weather service"));

        String endpointUri = "openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true"
                             + "&toolExecutionErrorStrategy=repromptModel&baseUrl="
                             + openAIMock.getBaseUrl() + "/v1";
        OpenAIEndpoint endpoint = context.getEndpoint(endpointUri, OpenAIEndpoint.class);
        List<McpSchema.Tool> mcpTools = List.of(
                McpSchema.Tool.builder("get_weather", Map.of("type", "object"))
                        .description("Mock tool: get_weather")
                        .build());
        endpoint.setMcpToolState(new McpToolState(
                McpToolConverter.convert(mcpTools),
                Map.of("get_weather", client),
                Map.of(),
                Set.of(), Map.of()));

        Exchange result = template.request("direct:error-reprompt",
                e -> e.getIn().setBody("trigger tool error reprompt"));

        assertThat(result.getException())
                .as("With repromptModel, tool execution errors should not crash the exchange")
                .isNull();
        assertThat(result.getMessage().getBody(String.class))
                .isEqualTo("Recovered after tool error feedback");
    }

    // ---------- helpers ----------

    private static String toolCallResponse(String toolName, String arguments) throws Exception {
        Map<String, Object> function = new HashMap<>();
        function.put("name", toolName);
        function.put("arguments", arguments);

        Map<String, Object> toolCall = new HashMap<>();
        toolCall.put("id", UUID.randomUUID().toString());
        toolCall.put("type", "function");
        toolCall.put("function", function);

        Map<String, Object> message = new HashMap<>();
        message.put("role", "assistant");
        message.put("content", null);
        message.put("tool_calls", List.of(toolCall));

        return chatCompletion(message, "tool_calls");
    }

    private static String simpleTextResponse(String content) throws Exception {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "assistant");
        message.put("content", content);
        return chatCompletion(message, "stop");
    }

    private static String chatCompletion(Map<String, Object> message, String finishReason) throws Exception {
        Map<String, Object> choice = new HashMap<>();
        choice.put("finish_reason", finishReason);
        choice.put("index", 0);
        choice.put("message", message);

        Map<String, Object> completion = new HashMap<>();
        completion.put("id", UUID.randomUUID().toString());
        completion.put("choices", List.of(choice));
        completion.put("created", System.currentTimeMillis() / 1000L);
        completion.put("model", "openai-mock");
        completion.put("object", "chat.completion");
        return MAPPER.writeValueAsString(completion);
    }
}
