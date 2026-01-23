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
package org.apache.camel.component.ibm.watsonx.ai.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.ibm.watsonx.ai.chat.ChatResponse;
import com.ibm.watsonx.ai.chat.ChatService;
import com.ibm.watsonx.ai.chat.model.AssistantMessage;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.ChatParameters;
import com.ibm.watsonx.ai.chat.model.PartialChatResponse;
import com.ibm.watsonx.ai.chat.model.Tool;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConstants;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiEndpoint;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for chat operations.
 */
public class ChatHandler extends AbstractWatsonxAiHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ChatHandler.class);

    public ChatHandler(WatsonxAiEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public WatsonxAiOperationResponse handle(Exchange exchange, WatsonxAiOperations operation) throws Exception {
        switch (operation) {
            case chat:
                return processChat(exchange);
            case chatStreaming:
                return processChatStreaming(exchange);
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    @Override
    public WatsonxAiOperations[] getSupportedOperations() {
        return new WatsonxAiOperations[] {
                WatsonxAiOperations.chat,
                WatsonxAiOperations.chatStreaming
        };
    }

    @SuppressWarnings("unchecked")
    private WatsonxAiOperationResponse processChat(Exchange exchange) {
        Message in = exchange.getIn();

        // Get messages from header or body
        List<ChatMessage> messages = getMessages(exchange);

        // Get tools from header if present
        List<Tool> tools = in.getHeader(WatsonxAiConstants.TOOLS, List.class);

        // Build parameters from configuration and headers
        ChatParameters.Builder paramsBuilder = ChatParameters.builder();
        applyChatParameters(paramsBuilder, exchange);

        // Call the service with tools if present
        ChatService service = endpoint.getChatService();
        ChatResponse response = service.chat(messages, paramsBuilder.build(), tools);

        // Get assistant message
        AssistantMessage assistantMessage = response.toAssistantMessage();
        String content = assistantMessage.content();

        // Build response headers
        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.GENERATED_TEXT, content);
        headers.put(WatsonxAiConstants.ASSISTANT_MESSAGE, assistantMessage);
        headers.put(WatsonxAiConstants.HAS_TOOL_CALLS, assistantMessage.hasToolCalls());

        // Set tool calls if present
        if (assistantMessage.hasToolCalls()) {
            headers.put(WatsonxAiConstants.TOOL_CALLS, assistantMessage.toolCalls());
        }

        if (response.usage() != null) {
            headers.put(WatsonxAiConstants.INPUT_TOKEN_COUNT, response.usage().promptTokens());
            headers.put(WatsonxAiConstants.OUTPUT_TOKEN_COUNT, response.usage().completionTokens());
        }
        if (response.finishReason() != null) {
            headers.put(WatsonxAiConstants.STOP_REASON, response.finishReason().value());
        }

        return WatsonxAiOperationResponse.create(content, headers);
    }

    @SuppressWarnings("unchecked")
    private WatsonxAiOperationResponse processChatStreaming(Exchange exchange) throws Exception {
        Message in = exchange.getIn();

        // Get messages from header or body
        List<ChatMessage> messages = getMessages(exchange);

        // Get the stream consumer from header
        Consumer<String> streamConsumer = in.getHeader(WatsonxAiConstants.STREAM_CONSUMER, Consumer.class);
        if (streamConsumer == null) {
            throw new IllegalArgumentException(
                    "Stream consumer must be provided via header '" + WatsonxAiConstants.STREAM_CONSUMER
                                               + "' for streaming operations");
        }

        // Build parameters from configuration and headers
        ChatParameters.Builder paramsBuilder = ChatParameters.builder();
        applyChatParameters(paramsBuilder, exchange);

        // Call the streaming service
        ChatService service = endpoint.getChatService();
        StringBuilder fullResponse = new StringBuilder();
        final ChatResponse[] finalResponse = new ChatResponse[1];
        final Throwable[] errorHolder = new Throwable[1];

        com.ibm.watsonx.ai.chat.ChatHandler handler = new com.ibm.watsonx.ai.chat.ChatHandler() {
            @Override
            public void onPartialResponse(String partialResponse, PartialChatResponse partialChatResponse) {
                if (partialResponse != null && !partialResponse.isEmpty()) {
                    fullResponse.append(partialResponse);
                    streamConsumer.accept(partialResponse);
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                finalResponse[0] = completeResponse;
            }

            @Override
            public void onError(Throwable error) {
                errorHolder[0] = error;
                LOG.error("Error during streaming chat", error);
            }
        };

        CompletableFuture<ChatResponse> future = service.chatStreaming(messages, paramsBuilder.build(), handler);

        // Wait for completion
        future.get();

        // Check for errors
        if (errorHolder[0] != null) {
            throw new RuntimeException("Error during streaming chat", errorHolder[0]);
        }

        // Build response headers
        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.GENERATED_TEXT, fullResponse.toString());

        // Set additional headers from complete response if available
        if (finalResponse[0] != null) {
            if (finalResponse[0].usage() != null) {
                headers.put(WatsonxAiConstants.INPUT_TOKEN_COUNT, finalResponse[0].usage().promptTokens());
                headers.put(WatsonxAiConstants.OUTPUT_TOKEN_COUNT, finalResponse[0].usage().completionTokens());
            }
            if (finalResponse[0].finishReason() != null) {
                headers.put(WatsonxAiConstants.STOP_REASON, finalResponse[0].finishReason().value());
            }
        }

        return WatsonxAiOperationResponse.create(fullResponse.toString(), headers);
    }
}
