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

import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.service.ModerationException;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ai.observability.GenAiErrorCategory;
import org.apache.camel.component.ai.observability.GenAiErrorProperties;
import org.apache.camel.component.ai.observability.GenAiObservabilityProperties;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AiAgentBody;
import org.apache.camel.component.langchain4j.agent.api.Headers;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LangChain4jAgentModerationTest extends CamelTestSupport {

    private static final String FLAGGED_TOKEN = "policy-violation";

    private final AtomicReference<Exchange> moderatedExchange = new AtomicReference<>();
    private final AtomicInteger chatInvocations = new AtomicInteger();

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("moderatedAgentConfig", moderatedAgentConfiguration(chatInvocations));
        registry.bind("memoryModeratedAgentConfig", memoryModeratedAgentConfiguration(new AtomicInteger()));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                Properties properties = new Properties();
                properties.setProperty(GenAiObservabilityProperties.ENABLED, "false");
                context.getPropertiesComponent().setOverrideProperties(properties);

                onException(ModerationException.class)
                        .process(exchange -> moderatedExchange.set(exchange))
                        .setBody(constant("Sorry, your message violates our usage policy."))
                        .handled(true);

                from("direct:moderated")
                        .to("langchain4j-agent:support?agentConfiguration=#moderatedAgentConfig")
                        .to("mock:result");

                from("direct:clean")
                        .to("langchain4j-agent:support?agentConfiguration=#moderatedAgentConfig")
                        .to("mock:success");

                from("direct:memory-moderated")
                        .to("langchain4j-agent:support?agentConfiguration=#memoryModeratedAgentConfig")
                        .to("mock:memory-result");
            }
        };
    }

    @Test
    void shouldExposeModerationFlaggedHeaderWhenInputIsFlagged() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        template.sendBody("direct:moderated", new AiAgentBody<>("contains " + FLAGGED_TOKEN));

        mock.assertIsSatisfied(10, TimeUnit.SECONDS);

        Exchange exchange = moderatedExchange.get();
        assertThat(exchange).isNotNull();
        assertThat(exchange.getMessage().getBody(String.class))
                .isEqualTo("Sorry, your message violates our usage policy.");
        assertThat(exchange.getMessage().getHeader(Headers.MODERATION_FLAGGED, Boolean.class)).isTrue();
        assertThat(exchange.getProperty(GenAiErrorProperties.ERROR_CATEGORY, String.class))
                .isEqualTo(GenAiErrorCategory.VALIDATION.name());
    }

    @Test
    void shouldNotInvokeChatModelWhenInputIsFlagged() throws Exception {
        chatInvocations.set(0);
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        template.sendBody("direct:moderated", new AiAgentBody<>("contains " + FLAGGED_TOKEN));

        mock.assertIsSatisfied(10, TimeUnit.SECONDS);

        assertThat(chatInvocations.get()).isZero();
    }

    @Test
    void shouldReturnResponseWhenInputPassesModeration() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:success");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("ok");

        template.sendBody("direct:clean", new AiAgentBody<>("Hello support"));

        mock.assertIsSatisfied(10, TimeUnit.SECONDS);
    }

    @Test
    void shouldRejectFlaggedInputForMemoryAgentRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:memory-result");
        mock.expectedMessageCount(0);

        template.sendBody("direct:memory-moderated",
                new AiAgentBody<>("contains " + FLAGGED_TOKEN, null, "session-42"));

        mock.assertIsSatisfied(10, TimeUnit.SECONDS);

        Exchange exchange = moderatedExchange.get();
        assertThat(exchange).isNotNull();
        assertThat(exchange.getMessage().getHeader(Headers.MODERATION_FLAGGED, Boolean.class)).isTrue();
    }

    private static AgentConfiguration moderatedAgentConfiguration(AtomicInteger chatInvocations) {
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                chatInvocations.incrementAndGet();
                return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
            }
        };

        ModerationModel moderationModel = new FlaggingModerationModel(FLAGGED_TOKEN);

        return new AgentConfiguration()
                .withChatModel(chatModel)
                .withModerationModel(moderationModel);
    }

    private static AgentConfiguration memoryModeratedAgentConfiguration(AtomicInteger chatInvocations) {
        ChatMemoryProvider memoryProvider = memoryId -> dev.langchain4j.memory.chat.MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(10)
                .build();

        return moderatedAgentConfiguration(chatInvocations).withChatMemoryProvider(memoryProvider);
    }
}
