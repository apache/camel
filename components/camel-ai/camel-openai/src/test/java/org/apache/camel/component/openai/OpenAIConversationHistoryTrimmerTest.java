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
import java.util.List;

import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartImage;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAIConversationHistoryTrimmerTest {

    @Test
    void trimShouldReturnSameListWhenNoLimitsConfigured() {
        List<ChatCompletionMessageParam> history = List.of(
                userMessage("one"),
                assistantMessage("two"));

        List<ChatCompletionMessageParam> trimmed = OpenAIConversationHistoryTrimmer.trim(history, config(0, 0));

        assertThat(trimmed).isSameAs(history);
    }

    @Test
    void trimShouldRetainMostRecentMessagesWhenMaxHistoryMessagesIsSet() {
        List<ChatCompletionMessageParam> history = new ArrayList<>();
        history.add(userMessage("msg-1"));
        history.add(assistantMessage("reply-1"));
        history.add(userMessage("msg-2"));
        history.add(assistantMessage("reply-2"));
        history.add(userMessage("msg-3"));
        history.add(assistantMessage("reply-3"));

        List<ChatCompletionMessageParam> trimmed = OpenAIConversationHistoryTrimmer.trim(history, config(2, 0));

        assertThat(trimmed).hasSize(2);
        assertThat(extractText(trimmed.get(0))).isEqualTo("msg-3");
        assertThat(extractText(trimmed.get(1))).isEqualTo("reply-3");
    }

    @Test
    void trimShouldDropOldestMessagesWhenMaxHistoryTokensIsExceeded() {
        List<ChatCompletionMessageParam> history = List.of(
                userMessage(repeat('a', 40)),
                assistantMessage(repeat('b', 40)),
                userMessage(repeat('c', 40)));

        List<ChatCompletionMessageParam> trimmed = OpenAIConversationHistoryTrimmer.trim(history, config(0, 15));

        assertThat(OpenAIConversationHistoryTrimmer.estimateTokens(trimmed)).isLessThanOrEqualTo(15);
        assertThat(trimmed).hasSize(1);
        assertThat(extractText(trimmed.get(0))).isEqualTo(repeat('c', 40));
    }

    @Test
    void trimShouldApplyBothMessageAndTokenLimits() {
        List<ChatCompletionMessageParam> history = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            history.add(userMessage("user-" + i + repeat('x', 36)));
            history.add(assistantMessage("assistant-" + i));
        }

        List<ChatCompletionMessageParam> trimmed = OpenAIConversationHistoryTrimmer.trim(history, config(4, 20));

        assertThat(trimmed.size()).isLessThanOrEqualTo(4);
        assertThat(OpenAIConversationHistoryTrimmer.estimateTokens(trimmed)).isLessThanOrEqualTo(20);
        assertThat(trimmed.stream().map(OpenAIConversationHistoryTrimmerTest::extractText))
                .noneMatch(text -> text.contains("user-1") || text.contains("assistant-1"));
        assertThat(trimmed.stream().map(OpenAIConversationHistoryTrimmerTest::extractText))
                .anyMatch(text -> text.contains("user-6"));
    }

    @Test
    void trimShouldCountToolCallArgumentsTowardTokenEstimate() {
        ChatCompletionMessageParam assistantWithToolCall = ChatCompletionMessageParam.ofAssistant(
                ChatCompletionAssistantMessageParam.builder()
                        .toolCalls(List.of(
                                ChatCompletionMessageToolCall.ofFunction(
                                        ChatCompletionMessageFunctionToolCall.builder()
                                                .id("call-1")
                                                .function(ChatCompletionMessageFunctionToolCall.Function.builder()
                                                        .name("lookup")
                                                        .arguments("{\"query\":\"" + repeat('z', 80) + "\"}")
                                                        .build())
                                                .build())))
                        .build());

        List<ChatCompletionMessageParam> history = List.of(
                userMessage("short"),
                assistantWithToolCall,
                toolMessage("tool-result"));

        List<ChatCompletionMessageParam> trimmed = OpenAIConversationHistoryTrimmer.trim(history, config(0, 10));

        assertThat(trimmed).isEmpty();
    }

    @Test
    void trimShouldKeepAssistantToolCallBlockIntactWhenMessageLimitWouldSplitIt() {
        List<ChatCompletionMessageParam> history = List.of(
                userMessage("older"),
                assistantMessage("older-reply"),
                userMessage("latest"),
                assistantWithToolCall("call-1"),
                toolMessage("tool-result"),
                assistantMessage("final-reply"));

        List<ChatCompletionMessageParam> trimmed = OpenAIConversationHistoryTrimmer.trim(history, config(2, 0));

        assertThat(trimmed).hasSize(1);
        assertThat(trimmed.get(0).assistant()).isPresent();
        assertThat(extractText(trimmed.get(0))).isEqualTo("final-reply");
        assertThat(trimmed.stream().noneMatch(message -> message.tool().isPresent())).isTrue();
    }

    @Test
    void trimShouldRetainAssistantAndToolResultsAsOneSegment() {
        List<ChatCompletionMessageParam> history = List.of(
                assistantWithToolCall("call-1"),
                toolMessage("tool-result"));

        List<ChatCompletionMessageParam> trimmed = OpenAIConversationHistoryTrimmer.trim(history, config(2, 0));

        assertThat(trimmed).hasSize(2);
        assertThat(trimmed.get(0).assistant()).isPresent();
        assertThat(trimmed.get(1).tool()).isPresent();
    }

    @Test
    void estimateTokensShouldIncludeImagePayloadSize() {
        String imageUrl = "data:image/png;base64," + repeat('x', 400);
        ChatCompletionMessageParam imageMessage = ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder()
                        .content(ChatCompletionUserMessageParam.Content.ofArrayOfContentParts(List.of(
                                ChatCompletionContentPart.ofText(
                                        ChatCompletionContentPartText.builder().text("describe").build()),
                                ChatCompletionContentPart.ofImageUrl(
                                        ChatCompletionContentPartImage.builder()
                                                .imageUrl(ChatCompletionContentPartImage.ImageUrl.builder()
                                                        .url(imageUrl)
                                                        .build())
                                                .build()))))
                        .build());

        int tokens = OpenAIConversationHistoryTrimmer.estimateTokens(List.of(imageMessage));

        assertThat(tokens).isGreaterThan(100);
    }

    private static OpenAIConfiguration config(int maxHistoryMessages, int maxHistoryTokens) {
        OpenAIConfiguration configuration = new OpenAIConfiguration();
        configuration.setMaxHistoryMessages(maxHistoryMessages);
        configuration.setMaxHistoryTokens(maxHistoryTokens);
        return configuration;
    }

    private static ChatCompletionMessageParam userMessage(String text) {
        return ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder()
                        .content(ChatCompletionUserMessageParam.Content.ofText(text))
                        .build());
    }

    private static ChatCompletionMessageParam assistantMessage(String text) {
        return ChatCompletionMessageParam.ofAssistant(
                ChatCompletionAssistantMessageParam.builder()
                        .content(ChatCompletionAssistantMessageParam.Content.ofText(text))
                        .build());
    }

    private static ChatCompletionMessageParam assistantWithToolCall(String toolCallId) {
        return ChatCompletionMessageParam.ofAssistant(
                ChatCompletionAssistantMessageParam.builder()
                        .toolCalls(List.of(
                                ChatCompletionMessageToolCall.ofFunction(
                                        ChatCompletionMessageFunctionToolCall.builder()
                                                .id(toolCallId)
                                                .function(ChatCompletionMessageFunctionToolCall.Function.builder()
                                                        .name("lookup")
                                                        .arguments("{}")
                                                        .build())
                                                .build())))
                        .build());
    }

    private static ChatCompletionMessageParam toolMessage(String text) {
        return ChatCompletionMessageParam.ofTool(
                ChatCompletionToolMessageParam.builder()
                        .toolCallId("call-1")
                        .content(text)
                        .build());
    }

    private static String extractText(ChatCompletionMessageParam message) {
        return message.user()
                .map(user -> user.content().isText() ? user.content().asText() : "")
                .orElseGet(() -> message.assistant()
                        .flatMap(assistant -> assistant.content()
                                .filter(ChatCompletionAssistantMessageParam.Content::isText)
                                .map(ChatCompletionAssistantMessageParam.Content::asText))
                        .orElseGet(() -> message.tool()
                                .map(tool -> tool.content().isText() ? tool.content().asText() : "")
                                .orElse("")));
    }

    private static String repeat(char value, int count) {
        return String.valueOf(value).repeat(count);
    }
}
