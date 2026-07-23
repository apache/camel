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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.a2a.A2AConstants;
import org.apache.camel.component.a2a.model.Message;
import org.apache.camel.component.a2a.model.SendMessageRequest;
import org.apache.camel.component.a2a.model.TextPart;
import org.apache.camel.component.a2a.streaming.DefaultStreamEmitter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageStreamOperationTest {

    private final MessageStreamOperation operation = new MessageStreamOperation();
    private final CamelContext context = new DefaultCamelContext();

    @Test
    void isStreaming() {
        assertThat(operation.isStreaming()).isTrue();
    }

    @Test
    void isNotInfrastructureOnly() {
        assertThat(operation.isInfrastructureOnly()).isFalse();
    }

    @Test
    void methodName() {
        assertThat(operation.methodName()).isEqualTo("SendStreamingMessage");
    }

    @Test
    void operationType() {
        assertThat(operation.getOperationType()).isEqualTo(A2AOperations.MESSAGE_STREAM);
    }

    @Test
    void buildRequestFromStringBody() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Stream this message");

        Object result = operation.buildRequest(exchange);

        assertThat(result).isInstanceOf(SendMessageRequest.class);
        SendMessageRequest request = (SendMessageRequest) result;
        assertThat(request.getMessage()).isNotNull();
        assertThat(request.getMessage().role()).isEqualTo(Message.Role.ROLE_USER);
        assertThat(request.getMessage().parts()).hasSize(1);
        assertThat(request.getMessage().parts().get(0)).isInstanceOf(TextPart.class);
        TextPart textPart = (TextPart) request.getMessage().parts().get(0);
        assertThat(textPart.text()).isEqualTo("Stream this message");
        assertThat(request.getMessage().messageId()).isNotEmpty();
    }

    @Test
    void buildRequestFromMessageBody() {
        Exchange exchange = new DefaultExchange(context);
        String messageId = UUID.randomUUID().toString();
        Message message = Message.builder()
                .messageId(messageId)
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("Custom stream message")))
                .build();
        exchange.getIn().setBody(message);

        Object result = operation.buildRequest(exchange);

        assertThat(result).isInstanceOf(SendMessageRequest.class);
        SendMessageRequest request = (SendMessageRequest) result;
        assertThat(request.getMessage().messageId()).isEqualTo(messageId);
    }

    @Test
    void buildRequestFromExistingSendMessageRequest() {
        Exchange exchange = new DefaultExchange(context);
        SendMessageRequest existing = new SendMessageRequest();
        Message message = Message.builder()
                .messageId("existing-msg")
                .build();
        existing.setMessage(message);
        exchange.getIn().setBody(existing);

        Object result = operation.buildRequest(exchange);

        assertThat(result).isSameAs(existing);
    }

    @Test
    void buildRequestIncludesContextId() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Test stream");
        String contextId = UUID.randomUUID().toString();
        exchange.getIn().setHeader(A2AConstants.CONTEXT_ID, contextId);

        Object result = operation.buildRequest(exchange);

        assertThat(result).isInstanceOf(SendMessageRequest.class);
        SendMessageRequest request = (SendMessageRequest) result;
        assertThat(request.getMessage().contextId()).isEqualTo(contextId);
    }

    @Test
    void buildRequestGeneratesMessageIdWhenMissing() {
        Exchange exchange = new DefaultExchange(context);
        Message message = Message.builder()
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("No message id")))
                .build();
        exchange.getIn().setBody(message);

        Object result = operation.buildRequest(exchange);

        SendMessageRequest request = (SendMessageRequest) result;
        assertThat(request.getMessage().messageId()).isNotNull().isNotEmpty();
    }

    @Test
    void buildRequestPreservesExistingMessageId() {
        Exchange exchange = new DefaultExchange(context);
        Message message = Message.builder()
                .messageId("keep-this-id")
                .role(Message.Role.ROLE_USER)
                .build();
        exchange.getIn().setBody(message);

        Object result = operation.buildRequest(exchange);

        SendMessageRequest request = (SendMessageRequest) result;
        assertThat(request.getMessage().messageId()).isEqualTo("keep-this-id");
    }

    @Test
    void parseRequestSetsBodyAndHeaders() {
        Exchange exchange = new DefaultExchange(context);
        SendMessageRequest request = new SendMessageRequest();
        Message message = Message.builder()
                .messageId("msg-stream-1")
                .contextId("ctx-stream-1")
                .parts(List.of(new TextPart("Streamed text")))
                .build();
        request.setMessage(message);

        operation.parseRequest(exchange, request);

        assertThat(exchange.getIn().getBody()).isSameAs(message);
        assertThat(exchange.getIn().getHeader(A2AConstants.MESSAGE_ID)).isEqualTo("msg-stream-1");
        assertThat(exchange.getIn().getHeader(A2AConstants.CONTEXT_ID)).isEqualTo("ctx-stream-1");
        assertThat(exchange.getIn().getHeader(A2AConstants.OPERATION)).isEqualTo("SendStreamingMessage");
    }

    @Test
    void parseRequestWithNullMessage() {
        Exchange exchange = new DefaultExchange(context);
        SendMessageRequest request = new SendMessageRequest();

        operation.parseRequest(exchange, request);

        assertThat(exchange.getIn().getHeader(A2AConstants.OPERATION)).isEqualTo("SendStreamingMessage");
        assertThat(exchange.getIn().getHeader(A2AConstants.MESSAGE_ID)).isNull();
    }

    @Test
    void parseResponseSetsBodyAndStreamResponseType() {
        Exchange exchange = new DefaultExchange(context);
        String sseResponse = "data: {\"event\":\"status\"}\n\n";

        operation.parseResponse(exchange, sseResponse);

        assertThat(exchange.getIn().getBody()).isEqualTo(sseResponse);
        assertThat(exchange.getIn().getHeader(A2AConstants.RESPONSE_TYPE)).isEqualTo("stream");
    }

    @Test
    void buildResponseReturnsStreamEmitterFromHeader() {
        Exchange exchange = new DefaultExchange(context);
        DefaultStreamEmitter emitter = new DefaultStreamEmitter("task-1", "ctx-1");
        exchange.getIn().setHeader(A2AConstants.STREAM_EMITTER, emitter);

        Object result = operation.buildResponse(exchange);

        assertThat(result).isSameAs(emitter);
    }

    @Test
    void buildResponseReturnsNullWhenNoEmitter() {
        Exchange exchange = new DefaultExchange(context);

        Object result = operation.buildResponse(exchange);

        assertThat(result).isNull();
    }
}
