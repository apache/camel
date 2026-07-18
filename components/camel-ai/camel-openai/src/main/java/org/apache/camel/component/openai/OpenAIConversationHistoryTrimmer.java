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
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies sliding-window limits to exchange-scoped OpenAI conversation history.
 */
final class OpenAIConversationHistoryTrimmer {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAIConversationHistoryTrimmer.class);
    private static final int CHARS_PER_TOKEN = 4;

    private OpenAIConversationHistoryTrimmer() {
    }

    static List<ChatCompletionMessageParam> trim(
            List<ChatCompletionMessageParam> history, OpenAIConfiguration config) {
        if (history == null || history.isEmpty()) {
            return history;
        }

        int maxMessages = config.getMaxHistoryMessages();
        int maxTokens = config.getMaxHistoryTokens();
        if (maxMessages <= 0 && maxTokens <= 0) {
            return history;
        }

        List<ChatCompletionMessageParam> trimmed = new ArrayList<>(history);
        int originalSize = trimmed.size();

        if (maxMessages > 0 && trimmed.size() > maxMessages) {
            trimmed = new ArrayList<>(trimmed.subList(trimmed.size() - maxMessages, trimmed.size()));
        }

        if (maxTokens > 0) {
            while (trimmed.size() > 1 && estimateTokens(trimmed) > maxTokens) {
                trimmed.remove(0);
            }
            if (trimmed.size() == 1 && estimateTokens(trimmed) > maxTokens) {
                trimmed.clear();
            }
        }

        int dropped = originalSize - trimmed.size();
        if (dropped > 0) {
            LOG.debug("Trimmed conversation history: dropped {} message(s), retained {}", dropped, trimmed.size());
        }

        return trimmed;
    }

    static int estimateTokens(List<ChatCompletionMessageParam> messages) {
        int chars = 0;
        for (ChatCompletionMessageParam message : messages) {
            chars += estimateMessageChars(message);
        }
        return (chars + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
    }

    private static int estimateMessageChars(ChatCompletionMessageParam message) {
        return message.user()
                .map(OpenAIConversationHistoryTrimmer::estimateUserMessageChars)
                .orElseGet(() -> message.assistant()
                        .map(OpenAIConversationHistoryTrimmer::estimateAssistantMessageChars)
                        .orElseGet(() -> message.tool()
                                .map(OpenAIConversationHistoryTrimmer::estimateToolMessageChars)
                                .orElse(0)));
    }

    private static int estimateUserMessageChars(ChatCompletionUserMessageParam user) {
        ChatCompletionUserMessageParam.Content content = user.content();
        if (content.isText()) {
            return content.asText().length();
        }
        if (content.isArrayOfContentParts()) {
            return content.asArrayOfContentParts().stream()
                    .mapToInt(part -> part.text().map(text -> text.text().length()).orElse(0))
                    .sum();
        }
        return 0;
    }

    private static int estimateAssistantMessageChars(ChatCompletionAssistantMessageParam assistant) {
        int chars = assistant.content()
                .filter(ChatCompletionAssistantMessageParam.Content::isText)
                .map(content -> content.asText().length())
                .orElse(0);

        for (ChatCompletionMessageToolCall toolCall : assistant.toolCalls().orElse(List.of())) {
            chars += toolCall.asFunction().function().name().length();
            chars += toolCall.asFunction().function().arguments().length();
        }

        return chars;
    }

    private static int estimateToolMessageChars(ChatCompletionToolMessageParam tool) {
        ChatCompletionToolMessageParam.Content content = tool.content();
        if (content.isText()) {
            return content.asText().length();
        }
        if (content.isArrayOfContentParts()) {
            return content.asArrayOfContentParts().stream()
                    .mapToInt(part -> part.text().length())
                    .sum();
        }
        return 0;
    }
}
