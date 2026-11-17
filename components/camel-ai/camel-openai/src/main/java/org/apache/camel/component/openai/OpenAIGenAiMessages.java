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

import java.util.List;
import java.util.stream.Collectors;

import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import org.apache.camel.component.ai.observability.GenAiMessages;

/**
 * Converts Chat Completions requests and responses into the messages of the GenAI semantic conventions, for
 * observability level {@code full}.
 */
final class OpenAIGenAiMessages {

    private OpenAIGenAiMessages() {
    }

    static GenAiMessages input(ChatCompletionCreateParams params) {
        GenAiMessages messages = new GenAiMessages();
        for (ChatCompletionMessageParam message : params.messages()) {
            if (message.isSystem()) {
                var content = message.asSystem().content();
                messages.text(GenAiMessages.SYSTEM,
                        content.isText() ? content.asText() : joinText(content.asArrayOfContentParts()));
            } else if (message.isDeveloper()) {
                var content = message.asDeveloper().content();
                messages.text(GenAiMessages.SYSTEM,
                        content.isText() ? content.asText() : joinText(content.asArrayOfContentParts()));
            } else if (message.isUser()) {
                var content = message.asUser().content();
                messages.text(GenAiMessages.USER,
                        content.isText() ? content.asText() : joinUserParts(content.asArrayOfContentParts()));
            } else if (message.isAssistant()) {
                addAssistant(messages, message.asAssistant());
            } else if (message.isTool()) {
                var tool = message.asTool();
                var content = tool.content();
                messages.toolResult(tool.toolCallId(),
                        content.isText() ? content.asText() : joinText(content.asArrayOfContentParts()));
            }
        }
        return messages;
    }

    static GenAiMessages output(ChatCompletion response, String finishReason) {
        GenAiMessages messages = new GenAiMessages();
        if (response.choices().isEmpty()) {
            return messages;
        }
        ChatCompletionMessage message = response.choices().get(0).message();
        messages.text(GenAiMessages.ASSISTANT, message.content().or(message::refusal).orElse(null));
        message.toolCalls().ifPresent(calls -> addToolCalls(messages, calls));
        return messages.finishReason(finishReason);
    }

    private static void addAssistant(GenAiMessages messages, ChatCompletionAssistantMessageParam assistant) {
        String text = assistant.content()
                .map(content -> content.isText()
                        ? content.asText()
                        : content.asArrayOfContentParts().stream()
                                .map(part -> part.isText() ? part.asText().text() : "[refusal]")
                                .collect(Collectors.joining("\n")))
                .orElse(null);
        messages.text(GenAiMessages.ASSISTANT, text);
        assistant.toolCalls().ifPresent(calls -> addToolCalls(messages, calls));
    }

    private static void addToolCalls(GenAiMessages messages, List<ChatCompletionMessageToolCall> calls) {
        for (ChatCompletionMessageToolCall call : calls) {
            if (call.isFunction()) {
                var function = call.asFunction();
                messages.toolCall(function.id(), function.function().name(), function.function().arguments());
            }
        }
    }

    private static String joinText(List<ChatCompletionContentPartText> parts) {
        return parts.stream().map(ChatCompletionContentPartText::text).collect(Collectors.joining("\n"));
    }

    private static String joinUserParts(List<ChatCompletionContentPart> parts) {
        return parts.stream()
                .map(part -> part.isText() ? part.asText().text() : "[" + kind(part) + "]")
                .collect(Collectors.joining("\n"));
    }

    private static String kind(ChatCompletionContentPart part) {
        if (part.isImageUrl()) {
            return "image";
        }
        if (part.isInputAudio()) {
            return "audio";
        }
        return part.isFile() ? "file" : "content";
    }
}
