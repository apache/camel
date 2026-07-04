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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.a2a.A2AConstants;
import org.apache.camel.component.a2a.model.TaskSubscribeRequest;
import org.apache.camel.component.a2a.streaming.DefaultStreamEmitter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskSubscribeOperationTest {

    private final TaskSubscribeOperation operation = new TaskSubscribeOperation();
    private final CamelContext context = new DefaultCamelContext();

    @Test
    void isStreaming() {
        assertThat(operation.isStreaming()).isTrue();
    }

    @Test
    void isInfrastructureOnly() {
        assertThat(operation.isInfrastructureOnly()).isTrue();
    }

    @Test
    void methodName() {
        assertThat(operation.methodName()).isEqualTo("SubscribeToTask");
    }

    @Test
    void operationType() {
        assertThat(operation.getOperationType()).isEqualTo(A2AOperations.TASK_SUBSCRIBE);
    }

    @Test
    void buildRequestFromTaskIdHeader() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(A2AConstants.TASK_ID, "task-sub-1");

        Object result = operation.buildRequest(exchange);

        assertThat(result).isInstanceOf(TaskSubscribeRequest.class);
        TaskSubscribeRequest request = (TaskSubscribeRequest) result;
        assertThat(request.getId()).isEqualTo("task-sub-1");
        assertThat(request.getHistoryLength()).isNull();
    }

    @Test
    void buildRequestIncludesHistoryLength() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(A2AConstants.TASK_ID, "task-sub-2");
        exchange.getIn().setHeader(A2AConstants.HISTORY_LENGTH, 10);

        Object result = operation.buildRequest(exchange);

        assertThat(result).isInstanceOf(TaskSubscribeRequest.class);
        TaskSubscribeRequest request = (TaskSubscribeRequest) result;
        assertThat(request.getId()).isEqualTo("task-sub-2");
        assertThat(request.getHistoryLength()).isEqualTo(10);
    }

    @Test
    void buildRequestWithNoHeaders() {
        Exchange exchange = new DefaultExchange(context);

        Object result = operation.buildRequest(exchange);

        assertThat(result).isInstanceOf(TaskSubscribeRequest.class);
        TaskSubscribeRequest request = (TaskSubscribeRequest) result;
        assertThat(request.getId()).isNull();
        assertThat(request.getHistoryLength()).isNull();
    }

    @Test
    void parseRequestSetsTaskIdAndOperationHeaders() {
        Exchange exchange = new DefaultExchange(context);
        TaskSubscribeRequest request = new TaskSubscribeRequest();
        request.setId("task-sub-3");

        operation.parseRequest(exchange, request);

        assertThat(exchange.getIn().getHeader(A2AConstants.TASK_ID)).isEqualTo("task-sub-3");
        assertThat(exchange.getIn().getHeader(A2AConstants.OPERATION)).isEqualTo("SubscribeToTask");
        assertThat(exchange.getIn().getHeader(A2AConstants.HISTORY_LENGTH)).isNull();
    }

    @Test
    void parseRequestSetsHistoryLengthWhenPresent() {
        Exchange exchange = new DefaultExchange(context);
        TaskSubscribeRequest request = new TaskSubscribeRequest();
        request.setId("task-sub-4");
        request.setHistoryLength(20);

        operation.parseRequest(exchange, request);

        assertThat(exchange.getIn().getHeader(A2AConstants.TASK_ID)).isEqualTo("task-sub-4");
        assertThat(exchange.getIn().getHeader(A2AConstants.OPERATION)).isEqualTo("SubscribeToTask");
        assertThat(exchange.getIn().getHeader(A2AConstants.HISTORY_LENGTH)).isEqualTo(20);
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
        DefaultStreamEmitter emitter = new DefaultStreamEmitter("task-sub-5", "ctx-sub-5");
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
