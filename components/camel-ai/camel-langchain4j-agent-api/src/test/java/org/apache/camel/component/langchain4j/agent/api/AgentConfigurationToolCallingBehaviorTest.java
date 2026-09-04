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
package org.apache.camel.component.langchain4j.agent.api;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Behavioral tests for CAMEL-23928: tool-calling options and the AiServices customizer hook wired through
 * {@link AbstractAgent#configureBuilder(dev.langchain4j.service.AiServices, dev.langchain4j.service.tool.ToolProvider)}.
 */
class AgentConfigurationToolCallingBehaviorTest {

    private final AtomicInteger chatRound = new AtomicInteger();
    private final AtomicBoolean beforeToolExecutionInvoked = new AtomicBoolean();
    private final AtomicReference<String> hallucinatedToolName = new AtomicReference<>();
    private final AtomicBoolean toolExecutionErrorHandled = new AtomicBoolean();

    @BeforeEach
    void resetState() {
        chatRound.set(0);
        beforeToolExecutionInvoked.set(false);
        hallucinatedToolName.set(null);
        toolExecutionErrorHandled.set(false);
    }

    @Test
    void hallucinatedToolNameStrategyAllowsAgentToRecover() {
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                if (chatRound.getAndIncrement() == 0) {
                    ToolExecutionRequest hallucinated = ToolExecutionRequest.builder()
                            .id("h1")
                            .name("task_complete")
                            .arguments("{}")
                            .build();
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.builder().toolExecutionRequests(List.of(hallucinated)).build())
                            .build();
                }
                return ChatResponse.builder().aiMessage(AiMessage.from("recovered")).build();
            }
        };

        AgentConfiguration configuration = new AgentConfiguration()
                .withChatModel(chatModel)
                .withMaxToolCallingRoundTrips(3)
                .withHallucinatedToolNameStrategy(request -> {
                    hallucinatedToolName.set(request.name());
                    return ToolExecutionResultMessage.from(request, "Tool not found: " + request.name());
                });

        Agent agent = new AgentWithoutMemory(configuration);
        Result<String> result = agent.chat(new AiAgentBody<>("complete the task"), null);

        assertThat(result.content()).isEqualTo("recovered");
        assertThat(hallucinatedToolName.get()).isEqualTo("task_complete");
        assertThat(chatRound.get()).isEqualTo(2);
    }

    @Test
    void maxToolCallingRoundTripsIsEnforced() {
        ChatModel alwaysRequestsTool = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                        .id("loop")
                        .name("countItems")
                        .arguments("{}")
                        .build();
                return ChatResponse.builder()
                        .aiMessage(AiMessage.builder().toolExecutionRequests(List.of(toolRequest)).build())
                        .build();
            }
        };

        AgentConfiguration configuration = new AgentConfiguration()
                .withChatModel(alwaysRequestsTool)
                .withCustomTools(List.of(new CountItemsTool()))
                .withMaxToolCallingRoundTrips(1);

        Agent agent = new AgentWithoutMemory(configuration);

        assertThatThrownBy(() -> agent.chat(new AiAgentBody<>("count"), null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("tool calling round trips");
    }

    @Test
    void toolExecutionErrorHandlerAndCompensationAllowRecovery() {
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                if (chatRound.getAndIncrement() == 0) {
                    ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                            .id("f1")
                            .name("failOperation")
                            .arguments("{}")
                            .build();
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.builder().toolExecutionRequests(List.of(toolRequest)).build())
                            .build();
                }
                return ChatResponse.builder().aiMessage(AiMessage.from("handled")).build();
            }
        };

        ToolExecutionErrorHandler errorHandler = (error, context) -> {
            toolExecutionErrorHandled.set(true);
            return ToolErrorHandlerResult.text("tool failed safely");
        };

        AgentConfiguration configuration = new AgentConfiguration()
                .withChatModel(chatModel)
                .withCustomTools(List.of(new FailingTool()))
                .withMaxToolCallingRoundTrips(3)
                .withCompensateOnToolErrors(true)
                .withToolExecutionErrorHandler(errorHandler);

        Agent agent = new AgentWithoutMemory(configuration);
        Result<String> result = agent.chat(new AiAgentBody<>("run failing tool"), null);

        assertThat(result.content()).isEqualTo("handled");
        assertThat(toolExecutionErrorHandled).isTrue();
    }

    @Test
    void aiServicesCustomizerCanConfigureBeforeToolExecution() {
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                if (chatRound.getAndIncrement() == 0) {
                    ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                            .id("c1")
                            .name("countItems")
                            .arguments("{}")
                            .build();
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.builder().toolExecutionRequests(List.of(toolRequest)).build())
                            .build();
                }
                return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
            }
        };

        AgentConfiguration configuration = new AgentConfiguration()
                .withChatModel(chatModel)
                .withCustomTools(List.of(new CountItemsTool()))
                .withMaxToolCallingRoundTrips(3)
                .withAiServicesCustomizer(builder -> builder.beforeToolExecution(
                        before -> beforeToolExecutionInvoked.set(true)));

        Agent agent = new AgentWithoutMemory(configuration);
        Result<String> result = agent.chat(new AiAgentBody<>("count items"), null);

        assertThat(result.content()).isEqualTo("done");
        assertThat(beforeToolExecutionInvoked).isTrue();
    }

    static class CountItemsTool {

        @Tool(name = "countItems", value = "Returns a fixed count")
        int countItems() {
            return 42;
        }
    }

    static class FailingTool {

        @Tool(name = "failOperation", value = "Always fails")
        String failOperation() {
            throw new RuntimeException("Simulated tool failure");
        }
    }
}
