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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAIAgenticTokenBudgetTest extends CamelTestSupport {

    @RegisterExtension
    public OpenAIMock tokenMock = new OpenAIMock().builder()
            .when("call one tool")
            .invokeTool("get_weather")
            .withParam("city", "London")
            .replyWith("The weather in London is sunny.")
            .end()
            .when("expensive tool call")
            .withUsage(70, 50)
            .invokeTool("get_weather")
            .withParam("city", "Paris")
            .replyWith("Should not reach this response")
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        String base = tokenMock.getBaseUrl() + "/v1";
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:agentic-tokens")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true&baseUrl=" + base);

                from("direct:agentic-token-budget")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true"
                            + "&maxAgenticTokens=100&maxToolIterations=5&baseUrl=" + base);
            }
        };
    }

    @Test
    void agenticLoopShouldExposeCumulativeTokenHeaders() {
        String endpointUri = "openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true&baseUrl="
                             + tokenMock.getBaseUrl() + "/v1";
        injectMcpTools(endpointUri, createWeatherToolClients());

        Exchange result = template.request("direct:agentic-tokens", e -> e.getIn().setBody("call one tool"));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getHeader(OpenAIConstants.AGENTIC_PROMPT_TOKENS, Long.class)).isEqualTo(20L);
        assertThat(result.getMessage().getHeader(OpenAIConstants.AGENTIC_COMPLETION_TOKENS, Long.class)).isEqualTo(10L);
        assertThat(result.getMessage().getHeader(OpenAIConstants.AGENTIC_TOTAL_TOKENS, Long.class)).isEqualTo(30L);
    }

    @Test
    void maxAgenticTokensShouldStopLoopWithDescriptiveException() {
        String endpointUri = "openai:chat-completion?model=gpt-5&apiKey=dummy&autoToolExecution=true"
                             + "&maxAgenticTokens=100&maxToolIterations=5&baseUrl="
                             + tokenMock.getBaseUrl() + "/v1";
        injectMcpTools(endpointUri, createWeatherToolClients());

        Exchange result = template.request("direct:agentic-token-budget", e -> e.getIn().setBody("expensive tool call"));

        assertThat(result.getException()).isNotNull();
        assertInstanceOf(IllegalStateException.class, result.getException());
        assertThat(result.getException().getMessage())
                .contains("Max agentic tokens (100) exceeded at iteration 0")
                .contains("prompt=70")
                .contains("completion=50")
                .contains("total=120");
        assertThat(result.getMessage().getHeader(OpenAIConstants.AGENTIC_TOTAL_TOKENS, Long.class)).isEqualTo(120L);
    }

    private Map<String, McpSyncClient> createWeatherToolClients() {
        Map<String, McpSyncClient> toolClients = new HashMap<>();
        toolClients.put("get_weather", createMockMcpClient("Sunny, 22°C"));
        return toolClients;
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
