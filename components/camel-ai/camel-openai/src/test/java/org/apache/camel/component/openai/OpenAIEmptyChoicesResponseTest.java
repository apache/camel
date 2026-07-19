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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.CamelExchangeException;
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
 * Verifies that a chat completion response with an empty {@code choices} array fails the exchange with a meaningful
 * {@link CamelExchangeException} in both the simple and agentic non-streaming paths.
 */
public class OpenAIEmptyChoicesResponseTest extends CamelTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("hello")
            .thenRespondWith((exchange, input) -> emptyChoicesCompletionJson())
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:chat")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");

                from("direct:mcp-chat")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");
            }
        };
    }

    @Test
    void emptyChoicesMustFailWithMeaningfulException() {
        Exchange result = template.request("direct:chat", e -> e.getIn().setBody("hello"));

        assertThat(result.getException())
                .as("An empty choices array cannot produce a response body")
                .isInstanceOf(CamelExchangeException.class)
                .hasMessageContaining("no choices");
    }

    @Test
    void emptyChoicesInAgenticPathMustFailWithMeaningfulException() {
        String endpointUri = "openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true&baseUrl="
                             + openAIMock.getBaseUrl() + "/v1";

        Map<String, McpSyncClient> toolClients = new HashMap<>();
        toolClients.put("get_weather", createMockMcpClient("Sunny, 22°C"));
        injectMcpTools(endpointUri, toolClients);

        Exchange result = template.request("direct:mcp-chat", e -> e.getIn().setBody("hello"));

        assertThat(result.getException())
                .as("An empty choices array cannot produce a response body in the agentic path")
                .isInstanceOf(CamelExchangeException.class)
                .hasMessageContaining("no choices");
    }

    private static String emptyChoicesCompletionJson() {
        try {
            Map<String, Object> completion = new HashMap<>();
            completion.put("id", UUID.randomUUID().toString());
            completion.put("choices", List.of());
            completion.put("created", System.currentTimeMillis() / 1000L);
            completion.put("model", "openai-mock");
            completion.put("object", "chat.completion");
            return MAPPER.writeValueAsString(completion);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    private void injectMcpTools(String endpointKey, Map<String, McpSyncClient> toolClients) {
        OpenAIEndpoint endpoint = context.getEndpoint(endpointKey, OpenAIEndpoint.class);

        List<McpSchema.Tool> mcpTools = toolClients.keySet().stream()
                .map(name -> McpSchema.Tool.builder(name, Map.of("type", "object"))
                        .description("Mock tool: " + name)
                        .build())
                .toList();

        endpoint.setMcpToolState(new McpToolState(
                McpToolConverter.convert(mcpTools),
                toolClients,
                Map.of(),
                Set.of()));
    }
}
