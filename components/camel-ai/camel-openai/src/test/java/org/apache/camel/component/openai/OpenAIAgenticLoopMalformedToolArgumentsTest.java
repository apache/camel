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
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reproducer for malformed tool-call arguments in the agentic loop.
 *
 * <p>
 * Models occasionally emit syntactically invalid JSON in {@code tool_calls[].function.arguments} (a hallucination class
 * every agentic runtime has to deal with). The documented error-handling contract of the agentic loop (openai-mcp.adoc,
 * "Error Handling in the Agentic Loop") is that tool execution problems are caught and sent back to the model as tool
 * result text so the model can recover gracefully.
 *
 * <p>
 * However, {@code OpenAIProducer.processNonStreamingAgentic} parses the arguments with
 * {@code OBJECT_MAPPER.readValue(argsJson, Map.class)} <b>outside</b> the try/catch that implements that contract, so a
 * malformed arguments string crashes the whole exchange with a raw Jackson {@code JsonParseException} instead of giving
 * the model a chance to correct itself.
 */
public class OpenAIAgenticLoopMalformedToolArgumentsTest extends CamelTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AtomicInteger callCounter = new AtomicInteger();

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("call bad tool")
            .thenRespondWith((exchange, input) -> {
                try {
                    if (callCounter.getAndIncrement() == 0) {
                        // first round: the model requests a tool call with malformed JSON arguments
                        return toolCallResponseWithMalformedArguments();
                    }
                    // subsequent round: the model recovers and produces the final answer
                    return simpleTextResponse("Recovered from bad arguments");
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
                from("direct:mcp-chat")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");
            }
        };
    }

    @Test
    void malformedToolArgumentsMustBeReportedToTheModelNotCrashTheExchange() {
        McpSyncClient client = mock(McpSyncClient.class);
        McpSchema.CallToolResult toolResult = McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(null, "Sunny", null)))
                .isError(false)
                .build();
        when(client.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(toolResult);

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
                Set.of()));

        Exchange result = template.request("direct:mcp-chat", e -> e.getIn().setBody("call bad tool"));

        assertNull(result.getException(),
                "Malformed tool arguments must be handled like any other tool execution error "
                                          + "(fed back to the model as an error tool result), not crash the exchange. Got: "
                                          + result.getException());
        assertEquals("Recovered from bad arguments", result.getMessage().getBody(String.class));
    }

    private static String toolCallResponseWithMalformedArguments() throws Exception {
        Map<String, Object> function = new HashMap<>();
        function.put("name", "get_weather");
        function.put("arguments", "{\"city\": \"London\""); // truncated JSON - not parseable

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
