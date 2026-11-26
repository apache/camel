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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.ibm.watsonx.ai.chat.ChatRequest;
import com.ibm.watsonx.ai.chat.ChatResponse;
import com.ibm.watsonx.ai.chat.ChatService;
import com.ibm.watsonx.ai.chat.TextChatResponse;
import com.ibm.watsonx.ai.chat.model.AssistantMessage;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.ChatParameters;
import com.ibm.watsonx.ai.chat.model.PartialChatResponse;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
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
            streamConsumer = endpoint.getStreamEndpointConsumer(exchange);
        }
        if (streamConsumer == null) {
            throw new IllegalArgumentException(
                    "Stream consumer must be provided via header '" + WatsonxAiConstants.STREAM_CONSUMER
                                               + "' or the streamEndpoint option for streaming operations");
        }
        Consumer<String> chunks = streamConsumer;

        ChatRequest request = buildChatRequest(exchange, messages);
        StringBuilder text = new StringBuilder();
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        AtomicReference<CompletableFuture<ChatResponse>> streamRef = new AtomicReference<>();
        com.ibm.watsonx.ai.chat.ChatHandler handler = new com.ibm.watsonx.ai.chat.ChatHandler() {
            @Override
            public void onPartialResponse(String partialResponse, PartialChatResponse partialChatResponse) {
                // the chunks of the first answer only (numberOfChoices), and none after an error
                Integer index = partialChatResponse != null ? partialChatResponse.index() : null;
                if (firstError.get() == null && partialResponse != null && !partialResponse.isEmpty()
                        && (index == null || index == 0)) {
                    text.append(partialResponse);
                    chunks.accept(partialResponse);
                }
            }

            @Override
            public void onError(Throwable error) {
                // an error of the stream, or of the stream consumer (which the SDK reports here, and does not stop the
                // stream for): the exchange fails with the first one
                if (firstError.compareAndSet(null, error)) {
                    // the outcome is known: stop the stream rather than wait for its end
                    CompletableFuture<ChatResponse> stream = streamRef.get();
                    if (stream != null) {
                        stream.cancel(true);
                    }
                }
                LOG.debug("Error during streaming chat", error);
            }

            @Override
            public boolean failOnFirstError() {
                return true;
            }
        };

        ChatService service = endpoint.getChatService();
        CompletableFuture<ChatResponse> stream = service.chatStreaming(request, handler);
        streamRef.set(stream);
        if (firstError.get() != null) {
            stream.cancel(true);
        }
        // the complete answer, with its tool calls and reasoning, once the stream ends
        ChatResponse response;
        try {
            response = awaitStream(stream);
        } catch (Exception e) {
            throw firstError.get() instanceof Exception error ? error : e;
        }
        if (firstError.get() != null) {
            throw firstError.get() instanceof Exception error ? error : new RuntimeCamelException(firstError.get());
        }

        Map<String, Object> headers = chatResponseHeaders(response, messages);
        // the answer as its chunks came (without the reasoning that the thinking tags split off): the SDK trims the
        // content of its complete answer
        if (!text.isEmpty()) {
            headers.put(WatsonxAiConstants.GENERATED_TEXT, text.toString());
            if (headers.get(WatsonxAiConstants.ASSISTANT_MESSAGE) instanceof AssistantMessage answer) {
                headers.put(WatsonxAiConstants.ASSISTANT_MESSAGE, new AssistantMessage(
                        text.toString(), answer.thinking(), answer.name(), answer.refusal(), answer.toolCalls()));
            }
        }
        return WatsonxAiOperationResponse.create(headers.get(WatsonxAiConstants.GENERATED_TEXT), headers);
    }
}
