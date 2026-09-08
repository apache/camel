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

import java.util.concurrent.atomic.AtomicInteger;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.moderation.Moderation;
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
        Agent agent = new AgentWithoutMemory(moderatedConfiguration(countingChatModel(new AtomicInteger())));

        Result<String> result = agent.chat(new AiAgentBody<>("Hello support team"), null);

        assertThat(result.content()).isEqualTo("ok");
    }

    @Test
    void agentWithoutMemoryRejectsFlaggedInputWhenModerationConfigured() {
        Agent agent = new AgentWithoutMemory(moderatedConfiguration(countingChatModel(new AtomicInteger())));

        assertThatThrownBy(() -> agent.chat(new AiAgentBody<>("message with " + FLAGGED_TOKEN), null))
                .isInstanceOf(ModerationException.class)
                .satisfies(error -> {
                    ModerationException moderationException = (ModerationException) error;
                    assertThat(moderationException.moderation()).isNotNull();
                    assertThat(moderationException.moderation().flagged()).isTrue();
                });
    }

    @Test
    void agentWithoutMemoryDoesNotInvokeChatModelWhenInputIsFlagged() {
        AtomicInteger chatInvocations = new AtomicInteger();
        Agent agent = new AgentWithoutMemory(moderatedConfiguration(countingChatModel(chatInvocations)));

        assertThatThrownBy(() -> agent.chat(new AiAgentBody<>("message with " + FLAGGED_TOKEN), null))
                .isInstanceOf(ModerationException.class);

        assertThat(chatInvocations.get()).isZero();
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

        AgentConfiguration configuration = moderatedConfiguration(countingChatModel(new AtomicInteger()))
                .withChatMemoryProvider(memoryProvider);
        Agent agent = new AgentWithMemory(configuration);

        assertThatThrownBy(() -> agent.chat(new AiAgentBody<>("message with " + FLAGGED_TOKEN, null, "session-1"), null))
                .isInstanceOf(ModerationException.class);
    }

    @Test
    void agentWithMemoryDoesNotInvokeChatModelWhenInputIsFlagged() {
        AtomicInteger chatInvocations = new AtomicInteger();
        ChatMemoryProvider memoryProvider = memoryId -> dev.langchain4j.memory.chat.MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(10)
                .build();

        AgentConfiguration configuration = moderatedConfiguration(countingChatModel(chatInvocations))
                .withChatMemoryProvider(memoryProvider);
        Agent agent = new AgentWithMemory(configuration);

        assertThatThrownBy(() -> agent.chat(new AiAgentBody<>("message with " + FLAGGED_TOKEN, null, "session-1"), null))
                .isInstanceOf(ModerationException.class);

        assertThat(chatInvocations.get()).isZero();
    }

    @Test
    void agentWithMemoryAllowsCleanInputWhenModerationConfigured() {
        ChatMemoryProvider memoryProvider = memoryId -> dev.langchain4j.memory.chat.MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(10)
                .build();

        AgentConfiguration configuration = moderatedConfiguration(countingChatModel(new AtomicInteger()))
                .withChatMemoryProvider(memoryProvider);
        Agent agent = new AgentWithMemory(configuration);

        Result<String> result = agent.chat(new AiAgentBody<>("Hello", null, "session-2"), null);

        assertThat(result.content()).isEqualTo("ok");
    }

    @Test
    void moderationSupportFailsClosedWhenModelReturnsNoVerdict() {
        ModerationModel moderationModel = new ModerationModel() {
            @Override
            public dev.langchain4j.model.moderation.ModerationResponse doModerate(
                    dev.langchain4j.model.moderation.ModerationRequest request) {
                return null;
            }
        };

        assertThatThrownBy(() -> ModerationSupport.moderateUserMessage(moderationModel, "hello"))
                .isInstanceOf(ModerationException.class)
                .satisfies(error -> {
                    ModerationException moderationException = (ModerationException) error;
                    assertThat(moderationException.moderation()).isNotNull();
                    assertThat(moderationException.moderation().flagged()).isTrue();
                });
    }

    @Test
    void agentWithMemoryDoesNotAccessMemoryProviderWhenInputIsFlagged() {
        AtomicInteger memoryProviderInvocations = new AtomicInteger();
        ChatMemoryProvider memoryProvider = memoryId -> {
            memoryProviderInvocations.incrementAndGet();
            return dev.langchain4j.memory.chat.MessageWindowChatMemory.builder()
                    .id(memoryId)
                    .maxMessages(10)
                    .build();
        };

        AgentConfiguration configuration = moderatedConfiguration(countingChatModel(new AtomicInteger()))
                .withChatMemoryProvider(memoryProvider);
        Agent agent = new AgentWithMemory(configuration);

        assertThatThrownBy(() -> agent.chat(new AiAgentBody<>("message with " + FLAGGED_TOKEN, null, "session-1"), null))
                .isInstanceOf(ModerationException.class);

        assertThat(memoryProviderInvocations.get()).isZero();
    }

    @Test
    void moderationSupportSkipsEmptyUserMessage() {
        ModerationModel moderationModel = new FlaggingModerationModel(FLAGGED_TOKEN);

        ModerationSupport.moderateUserMessage(moderationModel, null);
        ModerationSupport.moderateUserMessage(moderationModel, "");
    }

    @Test
    void moderationSupportThrowsWhenModelFlagsInput() {
        ModerationModel moderationModel = new FlaggingModerationModel(FLAGGED_TOKEN);

        assertThatThrownBy(() -> ModerationSupport.moderateUserMessage(moderationModel, "contains " + FLAGGED_TOKEN))
                .isInstanceOf(ModerationException.class)
                .extracting(error -> ((ModerationException) error).moderation())
                .isNotNull()
                .extracting(Moderation::flagged)
                .isEqualTo(true);
    }

    private static AgentConfiguration moderatedConfiguration(ChatModel chatModel) {
        return new AgentConfiguration()
                .withChatModel(chatModel)
                .withModerationModel(flaggingModerationModel());
    }

    private static ChatModel countingChatModel(AtomicInteger counter) {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                counter.incrementAndGet();
                return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
            }
        };
    }

    private static ChatModel noopChatModel() {
        return countingChatModel(new AtomicInteger());
    }

    private static ModerationModel flaggingModerationModel() {
        return new FlaggingModerationModel(FLAGGED_TOKEN);
    }
}
