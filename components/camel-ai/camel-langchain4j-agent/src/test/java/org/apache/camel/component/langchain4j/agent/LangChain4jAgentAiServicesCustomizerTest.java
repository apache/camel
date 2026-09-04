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
package org.apache.camel.component.langchain4j.agent;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AiAgentBody;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for CAMEL-23928: {@link AgentConfiguration} tool-calling options and AiServices customizer when
 * used through the langchain4j-agent component.
 */
class LangChain4jAgentAiServicesCustomizerTest extends CamelTestSupport {

    private static final String TAG = "aiservices-customizer";

    private final AtomicInteger chatRound = new AtomicInteger();
    private final AtomicBoolean customizerInvoked = new AtomicBoolean();
    private final AtomicReference<String> hallucinatedToolName = new AtomicReference<>();

    @BeforeEach
    void resetState() {
        chatRound.set(0);
        customizerInvoked.set(false);
        hallucinatedToolName.set(null);
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("hallucinationConfig", new AgentConfiguration()
                .withChatModel(createHallucinationRecoveryModel())
                .withMaxToolCallingRoundTrips(3)
                .withHallucinatedToolNameStrategy(request -> {
                    hallucinatedToolName.set(request.name());
                    return ToolExecutionResultMessage.from(request, "unknown tool: " + request.name());
                }));

        registry.bind("customizerConfig", new AgentConfiguration()
                .withChatModel(createSingleToolModel())
                .withMaxToolCallingRoundTrips(3)
                .withAiServicesCustomizer(builder -> {
                    customizerInvoked.set(true);
                    builder.beforeToolExecution(before -> {
                    });
                }));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:hallucination")
                        .to("langchain4j-agent:test?agentConfiguration=#hallucinationConfig&tags=" + TAG);

                from("direct:customizer")
                        .to("langchain4j-agent:test?agentConfiguration=#customizerConfig&tags=" + TAG);

                from("ai-tool:routeCounter?tags=" + TAG + "&description=Route-backed counter")
                        .setBody(constant("counted"));
            }
        };
    }

    @Test
    void hallucinatedToolNameStrategyWorksThroughLangchain4jAgentEndpoint() {
        String response = template.requestBody("direct:hallucination", new AiAgentBody<>("finish task"), String.class);

        assertThat(response).isEqualTo("recovered via route");
        assertThat(hallucinatedToolName.get()).isEqualTo("task_complete");
    }

    @Test
    void aiServicesCustomizerIsAppliedWhenUsingAgentConfigurationBean() {
        String response = template.requestBody("direct:customizer", new AiAgentBody<>("count"), String.class);

        assertThat(response).isEqualTo("done");
        assertThat(customizerInvoked).isTrue();
    }

    private ChatModel createHallucinationRecoveryModel() {
        return new ChatModel() {
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
                return ChatResponse.builder().aiMessage(AiMessage.from("recovered via route")).build();
            }
        };
    }

    private ChatModel createSingleToolModel() {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                if (chatRound.getAndIncrement() == 0) {
                    ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                            .id("r1")
                            .name("routeCounter")
                            .arguments("{}")
                            .build();
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.builder().toolExecutionRequests(List.of(toolRequest)).build())
                            .build();
                }
                return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
            }
        };
    }
}
