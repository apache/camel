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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.openai.core.ObjectMappers;
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
                addInputItems(messages, input.asResponse());
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

    @SuppressWarnings("unchecked")
    static GenAiToolDefinitions toolDefinitions(ResponseCreateParams params) {
        GenAiToolDefinitions definitions = new GenAiToolDefinitions();
        for (Tool tool : params.tools().orElse(List.of())) {
            tool.function().ifPresent(function -> definitions.function(
                    function.name(),
                    function.description().orElse(null),
                    function.parameters()
                            .map(p -> ObjectMappers.jsonMapper().convertValue(p, Map.class))
                            .orElse(null)));
        }
        return definitions;
    }

    private static void addInputItems(GenAiMessages messages, List<ResponseInputItem> items) {
        boolean lastIsAssistant = false;
        for (ResponseInputItem item : items) {
            if (item.isEasyInputMessage()) {
                EasyInputMessage msg = item.asEasyInputMessage();
                String role = roleOf(msg.role().asString());
                lastIsAssistant = GenAiMessages.ASSISTANT.equals(role);
                addEasyInputMessage(messages, role, msg.content());
            } else if (item.isMessage()) {
                // ResponseInputItem.Message - image/PDF prompt items from OpenAIResponsesInputBuilder
                ResponseInputItem.Message msg = item.asMessage();
                String role = roleOf(msg.role().asString());
                lastIsAssistant = GenAiMessages.ASSISTANT.equals(role);
                String text = msg.content().stream()
                        .filter(ResponseInputContent::isInputText)
                        .map(c -> c.asInputText().text())
                        .collect(Collectors.joining("\n"));
                if (text.isEmpty()) {
                    // binary content (image/PDF) - record placeholder
                    messages.text(role, null);
                } else {
                    messages.text(role, text);
                }
            } else if (item.isResponseOutputMessage()) {
                // assistant preamble from OpenAIResponsesSupport.toInputItems()
                ResponseOutputMessage msg = item.asResponseOutputMessage();
                String text = msg.content().stream()
                        .filter(ResponseOutputMessage.Content::isOutputText)
                        .map(c -> c.asOutputText().text())
                        .collect(Collectors.joining("\n"));
                lastIsAssistant = true;
                messages.text(GenAiMessages.ASSISTANT, text.isEmpty() ? null : text);
            } else if (item.isFunctionCallOutput()) {
                lastIsAssistant = false;
                ResponseInputItem.FunctionCallOutput output = item.asFunctionCallOutput();
                String callId = output.callId().orElse(null);
                String content = output.output().string().orElse(null);
                messages.toolResult(callId, content);
            } else if (item.isFunctionCall()) {
                // Ensure tool_call lands in an assistant message per semconv
                if (!lastIsAssistant) {
                    messages.text(GenAiMessages.ASSISTANT, null);
                }
                lastIsAssistant = true;
                ResponseFunctionToolCall call = item.asFunctionCall();
                messages.toolCall(call.callId(), call.name(), call.arguments());
            }
        }
    }

    private static void addEasyInputMessage(GenAiMessages messages, String role, EasyInputMessage.Content content) {
        if (content.isTextInput()) {
            messages.text(role, content.asTextInput());
        } else if (content.isResponseInputMessageContentList()) {
            String text = content.asResponseInputMessageContentList().stream()
                    .map(c -> {
                        if (c.isInputText()) {
                            return c.asInputText().text();
                        }
                        if (c.isInputImage()) {
                            return "[image]";
                        }
                        if (c.isInputFile()) {
                            return "[file]";
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("\n"));
            messages.text(role, text.isEmpty() ? null : text);
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
