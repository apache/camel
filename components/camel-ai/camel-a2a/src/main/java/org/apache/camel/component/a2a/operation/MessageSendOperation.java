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
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.a2a.A2AConstants;
import org.apache.camel.component.a2a.A2ADataFormat;
import org.apache.camel.component.a2a.A2ATypeConverters;
import org.apache.camel.component.a2a.model.Message;
import org.apache.camel.component.a2a.model.SendMessageRequest;
import org.apache.camel.component.a2a.model.SendMessageResponse;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TaskStatus;
import org.apache.camel.component.a2a.model.TextPart;
import org.apache.camel.component.a2a.util.A2AJsonMapper;

/**
 * Implementation of the SendMessage A2A operation. Handles message-to-message and message-to-task interactions.
 */
public class MessageSendOperation implements A2AOperation {

    @Override
    public A2AOperations getOperationType() {
        return A2AOperations.MESSAGE_SEND;
    }

    @Override
    public Object buildRequest(Exchange exchange) {
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

        // Generate messageId if not set
        Message.Builder msgBuilder = Message.builder(message);
        if (message.messageId() == null || message.messageId().isEmpty()) {
            msgBuilder.messageId(UUID.randomUUID().toString());
        }

        // Set contextId from header if present
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
    public void parseResponse(Exchange exchange, Object response) {
        SendMessageResponse sendMessageResponse = (SendMessageResponse) response;
        sendMessageResponse.validate();

        if (sendMessageResponse.isTaskResponse()) {
            Task task = sendMessageResponse.getTask();
            exchange.getMessage().setBody(task);
            exchange.setProperty(A2AConstants.RESPONSE_TASK, task);
            exchange.getMessage().setHeader(A2AConstants.TASK_ID, task.id());
            if (task.contextId() != null) {
                exchange.getMessage().setHeader(A2AConstants.CONTEXT_ID, task.contextId());
            }
            if (task.status() != null && task.status().state() != null) {
                exchange.getMessage().setHeader(A2AConstants.TASK_STATE,
                        task.status().state().getProtoName());
            }
            exchange.getMessage().setHeader(A2AConstants.RESPONSE_TYPE, "task");
        } else if (sendMessageResponse.isMessageResponse()) {
            Message message = sendMessageResponse.getMessage();
            exchange.getMessage().setBody(message);
            exchange.getMessage().setHeader(A2AConstants.MESSAGE_ID, message.messageId());
            exchange.getMessage().setHeader(A2AConstants.RESPONSE_TYPE, "message");
        }
    }

    @Override
    public void parseRequest(Exchange exchange, Object request) {
        SendMessageRequest sendMessageRequest = (SendMessageRequest) request;
        Message message = sendMessageRequest.getMessage();

        String textContent = A2ATypeConverters.messageToString(message);
        exchange.getMessage().setBody(textContent);

        exchange.getMessage().setHeader(A2AConstants.MESSAGE_ID, message.messageId());
        exchange.getMessage().setHeader(A2AConstants.CONTEXT_ID, message.contextId());
        exchange.getMessage().setHeader(A2AConstants.OPERATION, methodName());
    }

    @Override
    public void parseRequest(Exchange exchange, Object request, A2ADataFormat dataFormat) {
        SendMessageRequest sendMessageRequest = (SendMessageRequest) request;
        Message message = sendMessageRequest.getMessage();

        if (dataFormat == A2ADataFormat.POJO) {
            exchange.getMessage().setBody(message);
        } else if (dataFormat == A2ADataFormat.RAW) {
            try {
                exchange.getMessage().setBody(A2AJsonMapper.instance().writeValueAsString(message));
            } catch (Exception e) {
                throw new RuntimeCamelException("Failed to serialize Message to JSON", e);
            }
        } else {
            // PAYLOAD (default): extract text content
            String textContent = A2ATypeConverters.messageToString(message);
            exchange.getMessage().setBody(textContent);
        }

        exchange.getMessage().setHeader(A2AConstants.MESSAGE_ID, message.messageId());
        exchange.getMessage().setHeader(A2AConstants.CONTEXT_ID, message.contextId());
        exchange.getMessage().setHeader(A2AConstants.OPERATION, methodName());
    }

    @Override
    public Object buildResponse(Exchange exchange) {
        Object body = exchange.getMessage().getBody();

        String taskId = exchange.getMessage().getHeader(A2AConstants.TASK_ID, String.class);
        String contextId = exchange.getMessage().getHeader(A2AConstants.CONTEXT_ID, String.class);
        String taskStateStr = exchange.getMessage().getHeader(A2AConstants.TASK_STATE, String.class);
        String responseType = exchange.getMessage().getHeader(A2AConstants.RESPONSE_TYPE, String.class);

        // If route set a Message directly (POJO mode), use it as-is
        Message agentMessage;
        if (body instanceof Message msg) {
            agentMessage = msg;
        } else {
            String responseText = exchange.getMessage().getBody(String.class);
            agentMessage = Message.builder()
                    .role(Message.Role.ROLE_AGENT)
                    .parts(List.of(new TextPart(responseText)))
                    .messageId(UUID.randomUUID().toString())
                    .build();
        }

        SendMessageResponse response = new SendMessageResponse();

        if ("message".equals(responseType)) {
            // Message-only response
            response.setMessage(agentMessage);
        } else {
            // Task response
            TaskState state = taskStateStr != null ? TaskState.fromProtoName(taskStateStr) : TaskState.COMPLETED;
            Task task = Task.builder()
                    .id(taskId != null ? taskId : UUID.randomUUID().toString())
                    .contextId(contextId != null ? contextId : UUID.randomUUID().toString())
                    .status(new TaskStatus(state, agentMessage))
                    .history(List.of(agentMessage))
                    .build();

            response.setTask(task);
        }

        return response;
    }

}
