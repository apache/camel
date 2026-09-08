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

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.service.ModerationException;
import dev.langchain4j.service.Result;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentModerationTest {

    private static final String FLAGGED_TOKEN = "policy-violation";

    @Test
    void agentWithoutMemoryAllowsCleanInputWhenModerationConfigured() {
        Agent agent = new AgentWithoutMemory(moderatedConfiguration());

        Result<String> result = agent.chat(new AiAgentBody<>("Hello support team"), null);

        assertThat(result.content()).isEqualTo("ok");
    }

    @Test
    void agentWithoutMemoryRejectsFlaggedInputWhenModerationConfigured() {
        Agent agent = new AgentWithoutMemory(moderatedConfiguration());

        assertThatThrownBy(() -> agent.chat(new AiAgentBody<>("message with " + FLAGGED_TOKEN), null))
                .isInstanceOf(ModerationException.class)
                .satisfies(error -> {
                    ModerationException moderationException = (ModerationException) error;
                    assertThat(moderationException.moderation()).isNotNull();
                    assertThat(moderationException.moderation().flagged()).isTrue();
                    assertThat(moderationException.moderation().flaggedText())
                            .contains(FLAGGED_TOKEN);
                });
    }

    @Test
    void agentWithoutMemorySkipsModerationWhenModelNotConfigured() {
        AgentConfiguration configuration = new AgentConfiguration().withChatModel(noopChatModel());
        Agent agent = new AgentWithoutMemory(configuration);

        Result<String> result = agent.chat(new AiAgentBody<>("message with " + FLAGGED_TOKEN), null);

        assertThat(result.content()).isEqualTo("ok");
    }

    @Test
    void agentWithMemoryRejectsFlaggedInputWhenModerationConfigured() {
        ChatMemoryProvider memoryProvider = memoryId -> dev.langchain4j.memory.chat.MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(10)
                .build();

        AgentConfiguration configuration = moderatedConfiguration().withChatMemoryProvider(memoryProvider);
        Agent agent = new AgentWithMemory(configuration);

        assertThatThrownBy(() -> agent.chat(new AiAgentBody<>("message with " + FLAGGED_TOKEN, null, "session-1"), null))
                .isInstanceOf(ModerationException.class);
    }

    @Test
    void agentWithMemoryAllowsCleanInputWhenModerationConfigured() {
        ChatMemoryProvider memoryProvider = memoryId -> dev.langchain4j.memory.chat.MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(10)
                .build();

        AgentConfiguration configuration = moderatedConfiguration().withChatMemoryProvider(memoryProvider);
        Agent agent = new AgentWithMemory(configuration);

        Result<String> result = agent.chat(new AiAgentBody<>("Hello", null, "session-2"), null);

        assertThat(result.content()).isEqualTo("ok");
    }

    private static AgentConfiguration moderatedConfiguration() {
        return new AgentConfiguration()
                .withChatModel(noopChatModel())
                .withModerationModel(flaggingModerationModel());
    }

    private static ChatModel noopChatModel() {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
            }
        };
    }

    private static ModerationModel flaggingModerationModel() {
        return new FlaggingModerationModel(FLAGGED_TOKEN);
    }
}
