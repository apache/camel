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
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAIAgenticEventNotifierTest extends CamelTestSupport {

    private static final String ENDPOINT_URI = "openai:chat-completion?model=gpt-5&apiKey=dummy"
                                               + "&autoToolExecution=true&baseUrl=%s/v1";

    private final List<CamelEvent> events = new CopyOnWriteArrayList<>();

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("call one tool")
            .invokeTool("get_weather")
            .withParam("city", "London")
            .replyWith("The weather in London is sunny.")
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
            }
        };
    }

    @BeforeEach
    void registerEventNotifier() {
        events.clear();
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                if (event.getType() == CamelEvent.Type.Custom) {
                    events.add(event);
                }
            }

            @Override
            public boolean isEnabled(CamelEvent event) {
                return event.getType() == CamelEvent.Type.Custom;
            }
        });
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
        OpenAIEndpoint endpoint = context.getEndpoint(String.format(ENDPOINT_URI, openAIMock.getBaseUrl()),
                OpenAIEndpoint.class);
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
    void shouldEmitAgenticLifecycleEvents() {
        Map<String, McpSyncClient> toolClients = new HashMap<>();
        toolClients.put("get_weather", createMockMcpClient("Sunny, 22°C"));
        injectMcpTools(toolClients);

        template.sendBody("direct:mcp-chat", "call one tool");

        List<OpenAIAgenticLoopStartedEvent> started = new ArrayList<>();
        List<OpenAIAgenticToolCallExecutedEvent> toolEvents = new ArrayList<>();
        List<OpenAIAgenticLoopCompletedEvent> completed = new ArrayList<>();
        for (CamelEvent event : events) {
            if (event instanceof OpenAIAgenticLoopStartedEvent startedEvent) {
                started.add(startedEvent);
            } else if (event instanceof OpenAIAgenticToolCallExecutedEvent toolEvent) {
                toolEvents.add(toolEvent);
            } else if (event instanceof OpenAIAgenticLoopCompletedEvent completedEvent) {
                completed.add(completedEvent);
            }
        }

        assertThat(started).hasSize(1);
        assertThat(started.get(0).getMaxIterations()).isPositive();
        assertThat(toolEvents).hasSize(1);
        assertThat(toolEvents.get(0).getToolName()).isEqualTo("get_weather");
        assertThat(toolEvents.get(0).getIteration()).isEqualTo(1);
        assertThat(toolEvents.get(0).getDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(completed).hasSize(1);
        assertThat(completed.get(0).getIterationCount()).isEqualTo(1);
        assertThat(completed.get(0).getTotalTokens()).isPositive();
        assertThat(completed.get(0).getStopReason()).isNotBlank();
    }
}
