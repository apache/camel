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
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies sliding-window limits to exchange-scoped OpenAI conversation history. Trimming removes whole segments from
 * the oldest side so assistant tool-call blocks stay paired with their tool result messages.
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

        int originalSize = history.size();
        List<Segment> segments = buildSegments(history);
        int firstSegment = 0;

        if (maxMessages > 0) {
            firstSegment = findFirstSegmentForMessageLimit(segments, maxMessages);
        }

        if (maxTokens > 0) {
            firstSegment = Math.max(firstSegment,
                    findFirstSegmentForTokenLimit(history, segments, maxTokens));
        }

        List<ChatCompletionMessageParam> trimmed;
        if (firstSegment >= segments.size()) {
            trimmed = List.of();
        } else {
            Segment first = segments.get(firstSegment);
            Segment last = segments.get(segments.size() - 1);
            trimmed = new ArrayList<>(history.subList(first.start, last.end + 1));
            if (maxTokens > 0 && estimateTokens(trimmed) > maxTokens) {
                trimmed = List.of();
            }
        }

        int dropped = originalSize - trimmed.size();
        if (dropped > 0) {
            LOG.debug("Trimmed conversation history: dropped {} message(s), retained {}", dropped, trimmed.size());
        }

        return trimmed;
    }

    static int estimateTokens(List<ChatCompletionMessageParam> messages) {
        return estimateTokens(messages, 0, messages.size());
    }

    private static int estimateTokens(List<ChatCompletionMessageParam> messages, int from, int to) {
        int chars = 0;
        for (int i = from; i < to; i++) {
            chars += estimateMessageChars(messages.get(i));
        }
        return tokensFromChars(chars);
    }

    private static int tokensFromChars(int chars) {
        return (chars + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
    }

    private static int findFirstSegmentForMessageLimit(List<Segment> segments, int maxMessages) {
        int firstSegment = segments.size() - 1;
        int messageCount = segments.get(firstSegment).messageCount();
        while (firstSegment > 0) {
            Segment previous = segments.get(firstSegment - 1);
            int nextCount = messageCount + previous.messageCount();
            if (nextCount > maxMessages) {
                break;
            }
            firstSegment--;
            messageCount = nextCount;
        }
        return firstSegment;
    }

    private static int findFirstSegmentForTokenLimit(
            List<ChatCompletionMessageParam> history, List<Segment> segments, int maxTokens) {
        for (int firstSegment = 0; firstSegment < segments.size(); firstSegment++) {
            Segment first = segments.get(firstSegment);
            Segment last = segments.get(segments.size() - 1);
            if (estimateTokens(history, first.start, last.end + 1) <= maxTokens) {
                return firstSegment;
            }
        }
        return segments.size();
    }

    private static List<Segment> buildSegments(List<ChatCompletionMessageParam> history) {
        List<Segment> segments = new ArrayList<>();
        int index = 0;
        while (index < history.size()) {
            ChatCompletionMessageParam message = history.get(index);
            if (isAssistantWithToolCalls(message)) {
                int end = index;
                while (end + 1 < history.size() && history.get(end + 1).tool().isPresent()) {
                    end++;
                }
                segments.add(new Segment(index, end));
                index = end + 1;
            } else {
                segments.add(new Segment(index, index));
                index++;
            }
        }
        return segments;
    }

    private static boolean isAssistantWithToolCalls(ChatCompletionMessageParam message) {
        return message.assistant()
                .map(assistant -> !assistant.toolCalls().orElse(List.of()).isEmpty())
                .orElse(false);
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
                    .mapToInt(OpenAIConversationHistoryTrimmer::estimateContentPartChars)
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

    private static int estimateContentPartChars(ChatCompletionContentPart part) {
        int chars = 0;
        if (part.text().isPresent()) {
            chars += part.asText().text().length();
        }
        if (part.imageUrl().isPresent()) {
            chars += part.asImageUrl().imageUrl().url().length();
        }
        return chars;
    }

    private static final class Segment {
        private final int start;
        private final int end;

        private Segment(int start, int end) {
            this.start = start;
            this.end = end;
        }

        private int messageCount() {
            return end - start + 1;
        }
    }
}
