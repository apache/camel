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
import java.util.Locale;
import java.util.stream.Collectors;

import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputContent;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.Tool;
import org.apache.camel.component.ai.observability.GenAiMessages;
import org.apache.camel.component.ai.observability.GenAiToolDefinitions;

/**
 * Converts Responses API requests and responses into the messages of the GenAI semantic conventions, for observability
 * level {@code full}.
 */
final class OpenAIResponsesGenAiMessages {

    private OpenAIResponsesGenAiMessages() {
    }

    static GenAiMessages input(ResponseCreateParams params) {
        GenAiMessages messages = new GenAiMessages();
        params.input().ifPresent(input -> {
            if (input.isText()) {
                messages.text(GenAiMessages.USER, input.asText());
            } else if (input.isResponse()) {
                for (ResponseInputItem item : input.asResponse()) {
                    addInputItem(messages, item);
                }
            }
        });
        return messages;
    }

    static GenAiMessages output(Response response, String finishReason) {
        GenAiMessages messages = new GenAiMessages();
        for (ResponseOutputItem item : response.output()) {
            if (item.isMessage()) {
                String text = item.asMessage().content().stream()
                        .filter(ResponseOutputMessage.Content::isOutputText)
                        .map(c -> c.asOutputText().text())
                        .collect(Collectors.joining("\n"));
                messages.text(GenAiMessages.ASSISTANT, text.isEmpty() ? null : text);
            } else if (item.isFunctionCall()) {
                ResponseFunctionToolCall call = item.asFunctionCall();
                messages.toolCall(call.callId(), call.name(), call.arguments());
            }
        }
        return messages.finishReason(finishReason);
    }

    static GenAiToolDefinitions toolDefinitions(ResponseCreateParams params) {
        GenAiToolDefinitions definitions = new GenAiToolDefinitions();
        for (Tool tool : params.tools().orElse(List.of())) {
            tool.function().ifPresent(function -> definitions.function(
                    function.name(),
                    function.description().orElse(null),
                    null));
        }
        return definitions;
    }

    private static void addInputItem(GenAiMessages messages, ResponseInputItem item) {
        if (item.isEasyInputMessage()) {
            EasyInputMessage msg = item.asEasyInputMessage();
            String role = roleOf(msg.role().asString());
            EasyInputMessage.Content content = msg.content();
            if (content.isTextInput()) {
                messages.text(role, content.asTextInput());
            } else if (content.isResponseInputMessageContentList()) {
                String text = content.asResponseInputMessageContentList().stream()
                        .filter(ResponseInputContent::isInputText)
                        .map(c -> c.asInputText().text())
                        .collect(Collectors.joining("\n"));
                messages.text(role, text.isEmpty() ? null : text);
            }
        } else if (item.isFunctionCallOutput()) {
            ResponseInputItem.FunctionCallOutput output = item.asFunctionCallOutput();
            String callId = output.callId().orElse(null);
            String content = output.output().string().orElse(null);
            messages.toolResult(callId, content);
        } else if (item.isFunctionCall()) {
            ResponseFunctionToolCall call = item.asFunctionCall();
            messages.toolCall(call.callId(), call.name(), call.arguments());
        }
    }

    private static String roleOf(String apiRole) {
        if (apiRole == null) {
            return GenAiMessages.USER;
        }
        return switch (apiRole.toLowerCase(Locale.ROOT)) {
            case "assistant" -> GenAiMessages.ASSISTANT;
            case "system", "developer" -> GenAiMessages.SYSTEM;
            default -> GenAiMessages.USER;
        };
    }
}
