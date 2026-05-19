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
package org.apache.camel.component.a2a.operation;

import java.util.List;
import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.component.a2a.A2AConstants;
import org.apache.camel.component.a2a.model.Message;
import org.apache.camel.component.a2a.model.SendMessageRequest;
import org.apache.camel.component.a2a.model.TextPart;
import org.apache.camel.component.a2a.streaming.A2AStreamEmitter;

/**
 * Streaming variant of SendMessage. The consumer creates an {@link A2AStreamEmitter} and places it on the exchange
 * header {@link A2AConstants#STREAM_EMITTER}. The user's route processor emits SSE events via the emitter during
 * processing. The consumer collects the events and returns them as a {@code text/event-stream} response.
 */
public class MessageStreamOperation implements A2AOperation {

    @Override
    public A2AOperations getOperationType() {
        return A2AOperations.MESSAGE_STREAM;
    }

    @Override
    public boolean isStreaming() {
        return true;
    }

    @Override
    public Object buildRequest(Exchange exchange) {
        SendMessageRequest existing = exchange.getMessage().getBody(SendMessageRequest.class);
        if (existing != null) {
            return existing;
        }

        Message message;
        Object body = exchange.getMessage().getBody();
        if (body instanceof Message msg) {
            message = msg;
        } else {
            String text = exchange.getMessage().getBody(String.class);
            message = Message.builder()
                    .role(Message.Role.ROLE_USER)
                    .parts(List.of(new TextPart(text)))
                    .build();
        }
        Message.Builder msgBuilder = Message.builder(message);
        if (message.messageId() == null || message.messageId().isEmpty()) {
            msgBuilder.messageId(UUID.randomUUID().toString());
        }
        String contextId = exchange.getMessage().getHeader(A2AConstants.CONTEXT_ID, String.class);
        if (contextId != null) {
            msgBuilder.contextId(contextId);
        }
        message = msgBuilder.build();

        SendMessageRequest request = new SendMessageRequest();
        request.setMessage(message);
        return request;
    }

    @Override
    public void parseRequest(Exchange exchange, Object request) {
        SendMessageRequest sendMessageRequest = (SendMessageRequest) request;
        if (sendMessageRequest.getMessage() != null) {
            exchange.getMessage().setBody(sendMessageRequest.getMessage());
            exchange.getMessage().setHeader(A2AConstants.MESSAGE_ID, sendMessageRequest.getMessage().messageId());
            exchange.getMessage().setHeader(A2AConstants.CONTEXT_ID, sendMessageRequest.getMessage().contextId());
        }
        exchange.getMessage().setHeader(A2AConstants.OPERATION, methodName());
    }

    @Override
    public void parseResponse(Exchange exchange, Object response) {
        exchange.getMessage().setBody(response);
        exchange.getMessage().setHeader(A2AConstants.RESPONSE_TYPE, "stream");
    }

    @Override
    public Object buildResponse(Exchange exchange) {
        return exchange.getMessage().getHeader(A2AConstants.STREAM_EMITTER, A2AStreamEmitter.class);
    }
}
