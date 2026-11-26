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

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.ibm.watsonx.ai.chat.ChatRequest;
import com.ibm.watsonx.ai.chat.ChatResponse;
import com.ibm.watsonx.ai.chat.ChatService;
import com.ibm.watsonx.ai.chat.TextChatResponse;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.ChatParameters;
import com.ibm.watsonx.ai.chat.model.PartialChatResponse;
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
        List<ChatMessage> messages = getMessages(exchange);
        ChatRequest request = buildChatRequest(exchange, messages);

        ChatService service = endpoint.getChatService();
        TextChatResponse response = service.chat(request);

        Map<String, Object> headers = chatResponseHeaders(response, messages);
        return WatsonxAiOperationResponse.create(headers.get(WatsonxAiConstants.GENERATED_TEXT), headers);
    }

    /**
     * The chat request of the messages, with the tools of the tools header and the parameters, reasoning settings and
     * guardrails of the options and headers.
     */
    @SuppressWarnings("unchecked")
    private ChatRequest buildChatRequest(Exchange exchange, List<ChatMessage> messages) {
        Message in = exchange.getIn();
        ChatParameters.Builder parameters = ChatParameters.builder();
        applyChatParameters(parameters, exchange);
        String modelId = in.getHeader(WatsonxAiConstants.MODEL_ID, String.class);
        if (modelId != null && !modelId.isBlank()) {
            parameters.modelId(modelId);
        }
        return ChatRequest.builder()
                .messages(messages)
                .tools(in.getHeader(WatsonxAiConstants.TOOLS, List.class))
                .parameters(parameters.build())
                .thinking(buildThinking(exchange))
                .moderations(buildModerations())
                .build();
    }

    @SuppressWarnings("unchecked")
    private WatsonxAiOperationResponse processChatStreaming(Exchange exchange) throws Exception {
        Message in = exchange.getIn();
        List<ChatMessage> messages = getMessages(exchange);

        // The chunks go to the stream consumer of the header, or else to the stream endpoint
        Consumer<String> streamConsumer = in.getHeader(WatsonxAiConstants.STREAM_CONSUMER, Consumer.class);
        if (streamConsumer == null) {
            streamConsumer = endpoint.getStreamEndpointConsumer();
        }
        if (streamConsumer == null) {
            throw new IllegalArgumentException(
                    "Stream consumer must be provided via header '" + WatsonxAiConstants.STREAM_CONSUMER
                                               + "' or the streamEndpoint option for streaming operations");
        }
        Consumer<String> chunks = streamConsumer;

        ChatRequest request = buildChatRequest(exchange, messages);
        com.ibm.watsonx.ai.chat.ChatHandler handler = new com.ibm.watsonx.ai.chat.ChatHandler() {
            @Override
            public void onPartialResponse(String partialResponse, PartialChatResponse partialChatResponse) {
                if (partialResponse != null && !partialResponse.isEmpty()) {
                    chunks.accept(partialResponse);
                }
            }

            @Override
            public void onError(Throwable error) {
                // the exchange fails with this error when the stream ends
                LOG.debug("Error during streaming chat", error);
            }

            @Override
            public boolean failOnFirstError() {
                return true;
            }
        };

        ChatService service = endpoint.getChatService();
        // the complete answer, with its tool calls and reasoning, once the stream ends
        ChatResponse response = awaitStream(service.chatStreaming(request, handler));

        Map<String, Object> headers = chatResponseHeaders(response, messages);
        return WatsonxAiOperationResponse.create(headers.get(WatsonxAiConstants.GENERATED_TEXT), headers);
    }
}
