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
import java.util.concurrent.atomic.AtomicReference;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.service.ModerationException;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
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

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("moderatedAgentConfig", moderatedAgentConfiguration());
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
            }
        };
    }

    @Test
    void shouldExposeModerationHeadersWhenInputIsFlagged() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        template.sendBody("direct:moderated", new AiAgentBody<>("contains " + FLAGGED_TOKEN));

        mock.assertIsSatisfied(10, TimeUnit.SECONDS);

        Exchange exchange = moderatedExchange.get();
        assertThat(exchange).isNotNull();
        assertThat(exchange.getMessage().getBody(String.class))
                .isEqualTo("Sorry, your message violates our usage policy.");
        assertThat(exchange.getMessage().getHeader(Headers.MODERATION, Moderation.class))
                .isNotNull()
                .extracting(Moderation::flagged)
                .isEqualTo(true);
        assertThat(exchange.getMessage().getHeader(Headers.MODERATION_FLAGGED_TEXT, String.class))
                .contains(FLAGGED_TOKEN);
    }

    @Test
    void shouldReturnResponseWhenInputPassesModeration() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:success");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("ok");

        template.sendBody("direct:clean", new AiAgentBody<>("Hello support"));

        mock.assertIsSatisfied(10, TimeUnit.SECONDS);
    }

    private static AgentConfiguration moderatedAgentConfiguration() {
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
            }
        };

        ModerationModel moderationModel = new FlaggingModerationModel(FLAGGED_TOKEN);

        return new AgentConfiguration()
                .withChatModel(chatModel)
                .withModerationModel(moderationModel);
    }
}
