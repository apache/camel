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
import org.apache.camel.component.a2a.A2ADataFormat;
import org.apache.camel.component.a2a.model.Message;
import org.apache.camel.component.a2a.model.SendMessageRequest;
import org.apache.camel.component.a2a.model.SendMessageResponse;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TaskStatus;
import org.apache.camel.component.a2a.model.TextPart;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageSendOperationTest {

    private final MessageSendOperation operation = new MessageSendOperation();
    private final CamelContext context = new DefaultCamelContext();

    @Test
    void buildRequestFromStringBody() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Hello agent");

        Object result = operation.buildRequest(exchange);

        assertThat(result).isInstanceOf(SendMessageRequest.class);
        SendMessageRequest request = (SendMessageRequest) result;
        assertThat(request.getMessage()).isNotNull();
        assertThat(request.getMessage().role()).isEqualTo(Message.Role.ROLE_USER);
        assertThat(request.getMessage().parts()).hasSize(1);
        assertThat(request.getMessage().parts().get(0)).isInstanceOf(TextPart.class);
        TextPart textPart = (TextPart) request.getMessage().parts().get(0);
        assertThat(textPart.text()).isEqualTo("Hello agent");
        assertThat(request.getMessage().messageId()).isNotEmpty();
    }

    @Test
    void buildRequestFromMessageBody() {
        Exchange exchange = new DefaultExchange(context);
        String messageId = UUID.randomUUID().toString();
        Message message = Message.builder()
                .messageId(messageId)
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("Custom message")))
                .build();
        exchange.getIn().setBody(message);

        Object result = operation.buildRequest(exchange);

        assertThat(result).isInstanceOf(SendMessageRequest.class);
        SendMessageRequest request = (SendMessageRequest) result;
        assertThat(request.getMessage().messageId()).isEqualTo(messageId);
    }

    @Test
    void buildRequestIncludesContextId() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Test message");
        String contextId = UUID.randomUUID().toString();
        exchange.getIn().setHeader(A2AConstants.CONTEXT_ID, contextId);

        Object result = operation.buildRequest(exchange);

        assertThat(result).isInstanceOf(SendMessageRequest.class);
        SendMessageRequest request = (SendMessageRequest) result;
        assertThat(request.getMessage().contextId()).isEqualTo(contextId);
    }

    @Test
    void methodName() {
        assertThat(operation.methodName()).isEqualTo("SendMessage");
    }

    @Test
    void parseResponseWithTaskSetsTaskAsBody() {
        Exchange exchange = new DefaultExchange(context);
        SendMessageResponse response = new SendMessageResponse();
        Message agentMsg = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart("agent response text")))
                .build();
        Task task = Task.builder()
                .id("task-123")
                .contextId("context-456")
                .status(new TaskStatus(TaskState.WORKING))
                .history(List.of(agentMsg))
                .build();
        response.setTask(task);

        operation.parseResponse(exchange, response);

        // parseResponse now sets body = Task, not extracted text
        assertThat(exchange.getIn().getBody()).isInstanceOf(Task.class);
        Task bodyTask = exchange.getIn().getBody(Task.class);
        assertThat(bodyTask.id()).isEqualTo("task-123");
        assertThat(exchange.getProperty(A2AConstants.RESPONSE_TASK)).isSameAs(task);
        assertThat(exchange.getIn().getHeader(A2AConstants.TASK_ID)).isEqualTo("task-123");
        assertThat(exchange.getIn().getHeader(A2AConstants.CONTEXT_ID)).isEqualTo("context-456");
        assertThat(exchange.getIn().getHeader(A2AConstants.TASK_STATE)).isEqualTo("TASK_STATE_WORKING");
        assertThat(exchange.getIn().getHeader(A2AConstants.RESPONSE_TYPE)).isEqualTo("task");
    }

    @Test
    void parseResponseWithTaskEmptyHistory() {
        Exchange exchange = new DefaultExchange(context);
        SendMessageResponse response = new SendMessageResponse();
        Task task = Task.builder()
                .id("task-empty")
                .status(new TaskStatus(TaskState.SUBMITTED))
                .build();
        response.setTask(task);

        operation.parseResponse(exchange, response);

        // Body is the Task object itself
        assertThat(exchange.getIn().getBody()).isInstanceOf(Task.class);
        assertThat(exchange.getProperty(A2AConstants.RESPONSE_TASK)).isSameAs(task);
        assertThat(exchange.getIn().getHeader(A2AConstants.TASK_ID)).isEqualTo("task-empty");
    }

    @Test
    void parseResponseWithMessageSetsMessageAsBody() {
        Exchange exchange = new DefaultExchange(context);
        SendMessageResponse response = new SendMessageResponse();
        Message message = Message.builder()
                .messageId("msg-789")
                .parts(List.of(new TextPart("message content")))
                .build();
        response.setMessage(message);

        operation.parseResponse(exchange, response);

        // parseResponse now sets body = Message, not extracted text
        assertThat(exchange.getIn().getBody()).isInstanceOf(Message.class);
        Message bodyMessage = exchange.getIn().getBody(Message.class);
        assertThat(bodyMessage.messageId()).isEqualTo("msg-789");
        assertThat(exchange.getIn().getHeader(A2AConstants.MESSAGE_ID)).isEqualTo("msg-789");
        assertThat(exchange.getIn().getHeader(A2AConstants.RESPONSE_TYPE)).isEqualTo("message");
    }

    @Test
    void parseRequest() {
        Exchange exchange = new DefaultExchange(context);
        SendMessageRequest request = new SendMessageRequest();
        Message message = Message.builder()
                .messageId("msg-001")
                .contextId("ctx-002")
                .parts(List.of(new TextPart("Incoming message text")))
                .build();
        request.setMessage(message);

        operation.parseRequest(exchange, request);

        assertThat(exchange.getIn().getBody(String.class)).isEqualTo("Incoming message text");
        assertThat(exchange.getIn().getHeader(A2AConstants.MESSAGE_ID)).isEqualTo("msg-001");
        assertThat(exchange.getIn().getHeader(A2AConstants.CONTEXT_ID)).isEqualTo("ctx-002");
        assertThat(exchange.getIn().getHeader(A2AConstants.OPERATION)).isEqualTo("SendMessage");
    }

    @Test
    void parseRequestPojoMode() {
        Exchange exchange = new DefaultExchange(context);
        SendMessageRequest request = new SendMessageRequest();
        Message message = Message.builder()
                .messageId("msg-pojo")
                .contextId("ctx-pojo")
                .parts(List.of(new TextPart("POJO message text")))
                .build();
        request.setMessage(message);

        operation.parseRequest(exchange, request, A2ADataFormat.POJO);

        assertThat(exchange.getIn().getBody()).isInstanceOf(Message.class);
        Message body = (Message) exchange.getIn().getBody();
        assertThat(body.messageId()).isEqualTo("msg-pojo");
        assertThat(body.parts()).hasSize(1);
        assertThat(exchange.getIn().getHeader(A2AConstants.MESSAGE_ID)).isEqualTo("msg-pojo");
        assertThat(exchange.getIn().getHeader(A2AConstants.CONTEXT_ID)).isEqualTo("ctx-pojo");
        assertThat(exchange.getIn().getHeader(A2AConstants.OPERATION)).isEqualTo("SendMessage");
    }

    @Test
    void parseRequestRawMode() {
        Exchange exchange = new DefaultExchange(context);
        SendMessageRequest request = new SendMessageRequest();
        Message message = Message.builder()
                .messageId("msg-raw")
                .contextId("ctx-raw")
                .parts(List.of(new TextPart("RAW message text")))
                .build();
        request.setMessage(message);

        operation.parseRequest(exchange, request, A2ADataFormat.RAW);

        assertThat(exchange.getIn().getBody()).isInstanceOf(String.class);
        String jsonBody = (String) exchange.getIn().getBody();
        assertThat(jsonBody).contains("msg-raw");
        assertThat(jsonBody).contains("RAW message text");
        assertThat(exchange.getIn().getHeader(A2AConstants.MESSAGE_ID)).isEqualTo("msg-raw");
        assertThat(exchange.getIn().getHeader(A2AConstants.CONTEXT_ID)).isEqualTo("ctx-raw");
        assertThat(exchange.getIn().getHeader(A2AConstants.OPERATION)).isEqualTo("SendMessage");
    }

    @Test
    void parseRequestPayloadModeExplicit() {
        Exchange exchange = new DefaultExchange(context);
        SendMessageRequest request = new SendMessageRequest();
        Message message = Message.builder()
                .messageId("msg-payload")
                .contextId("ctx-payload")
                .parts(List.of(new TextPart("Payload message text")))
                .build();
        request.setMessage(message);

        operation.parseRequest(exchange, request, A2ADataFormat.PAYLOAD);

        assertThat(exchange.getIn().getBody(String.class)).isEqualTo("Payload message text");
        assertThat(exchange.getIn().getHeader(A2AConstants.MESSAGE_ID)).isEqualTo("msg-payload");
        assertThat(exchange.getIn().getHeader(A2AConstants.OPERATION)).isEqualTo("SendMessage");
    }

    @Test
    void buildResponseWithMessageBody() {
        Exchange exchange = new DefaultExchange(context);
        Message agentMessage = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart("Agent response")))
                .messageId("agent-msg-1")
                .build();
        exchange.getIn().setBody(agentMessage);
        exchange.getIn().setHeader(A2AConstants.RESPONSE_TYPE, "message");

        Object result = operation.buildResponse(exchange);

        assertThat(result).isInstanceOf(SendMessageResponse.class);
        SendMessageResponse response = (SendMessageResponse) result;
        assertThat(response.isMessageResponse()).isTrue();
        assertThat(response.getMessage()).isSameAs(agentMessage);
        assertThat(response.getMessage().messageId()).isEqualTo("agent-msg-1");
    }

    @Test
    void buildResponseWithMessageBodyAsTask() {
        Exchange exchange = new DefaultExchange(context);
        Message agentMessage = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart("Task result via POJO")))
                .messageId("agent-msg-2")
                .build();
        exchange.getIn().setBody(agentMessage);
        exchange.getIn().setHeader(A2AConstants.TASK_ID, "task-pojo");
        exchange.getIn().setHeader(A2AConstants.CONTEXT_ID, "ctx-pojo");

        Object result = operation.buildResponse(exchange);

        assertThat(result).isInstanceOf(SendMessageResponse.class);
        SendMessageResponse response = (SendMessageResponse) result;
        assertThat(response.isTaskResponse()).isTrue();
        assertThat(response.getTask().id()).isEqualTo("task-pojo");
        assertThat(response.getTask().status().message()).isSameAs(agentMessage);
    }

    @Test
    void buildResponseAsMessage() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Response text");
        exchange.getIn().setHeader(A2AConstants.RESPONSE_TYPE, "message");

        Object result = operation.buildResponse(exchange);

        assertThat(result).isInstanceOf(SendMessageResponse.class);
        SendMessageResponse response = (SendMessageResponse) result;
        assertThat(response.isMessageResponse()).isTrue();
        assertThat(response.isTaskResponse()).isFalse();
        assertThat(response.getMessage()).isNotNull();
        assertThat(response.getMessage().role()).isEqualTo(Message.Role.ROLE_AGENT);
        assertThat(response.getMessage().parts()).hasSize(1);
        TextPart textPart = (TextPart) response.getMessage().parts().get(0);
        assertThat(textPart.text()).isEqualTo("Response text");
    }

    @Test
    void buildResponseAsTask() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Task result");
        exchange.getIn().setHeader(A2AConstants.TASK_ID, "task-999");
        exchange.getIn().setHeader(A2AConstants.CONTEXT_ID, "ctx-888");
        exchange.getIn().setHeader(A2AConstants.TASK_STATE, "TASK_STATE_COMPLETED");

        Object result = operation.buildResponse(exchange);

        assertThat(result).isInstanceOf(SendMessageResponse.class);
        SendMessageResponse response = (SendMessageResponse) result;
        assertThat(response.isTaskResponse()).isTrue();
        assertThat(response.isMessageResponse()).isFalse();
        assertThat(response.getTask()).isNotNull();
        assertThat(response.getTask().id()).isEqualTo("task-999");
        assertThat(response.getTask().contextId()).isEqualTo("ctx-888");
        assertThat(response.getTask().status().state()).isEqualTo(TaskState.COMPLETED);
        assertThat(response.getTask().status().message()).isNotNull();
        assertThat(response.getTask().status().message().role()).isEqualTo(Message.Role.ROLE_AGENT);
        assertThat(response.getTask().status().message().parts().get(0)).isInstanceOf(TextPart.class);
        assertThat(response.getTask().history()).hasSize(1);
        assertThat(response.getTask().history().get(0).role()).isEqualTo(Message.Role.ROLE_AGENT);
    }

    @Test
    void buildResponseAsTaskWithDefaults() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Task result");

        Object result = operation.buildResponse(exchange);

        assertThat(result).isInstanceOf(SendMessageResponse.class);
        SendMessageResponse response = (SendMessageResponse) result;
        assertThat(response.isTaskResponse()).isTrue();
        assertThat(response.getTask()).isNotNull();
        assertThat(response.getTask().id()).isNotEmpty(); // UUID generated
        assertThat(response.getTask().contextId()).isNotEmpty(); // UUID generated
        assertThat(response.getTask().status().state()).isEqualTo(TaskState.COMPLETED); // Default
    }
}
