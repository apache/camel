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
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskGetRequest;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TaskStatus;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskGetOperationTest {

    private final TaskGetOperation operation = new TaskGetOperation();
    private final CamelContext context = new DefaultCamelContext();

    @Test
    void buildRequestFromTaskIdHeader() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(A2AConstants.TASK_ID, "task-123");

        Object result = operation.buildRequest(exchange);

        assertThat(result).isInstanceOf(TaskGetRequest.class);
        TaskGetRequest request = (TaskGetRequest) result;
        assertThat(request.getId()).isEqualTo("task-123");
        assertThat(request.getHistoryLength()).isNull();
    }

    @Test
    void buildRequestIncludesHistoryLength() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(A2AConstants.TASK_ID, "task-456");
        exchange.getIn().setHeader(A2AConstants.HISTORY_LENGTH, 5);

        Object result = operation.buildRequest(exchange);

        assertThat(result).isInstanceOf(TaskGetRequest.class);
        TaskGetRequest request = (TaskGetRequest) result;
        assertThat(request.getId()).isEqualTo("task-456");
        assertThat(request.getHistoryLength()).isEqualTo(5);
    }

    @Test
    void parseResponseSetsTaskBody() {
        Exchange exchange = new DefaultExchange(context);
        Task task = Task.builder()
                .id("task-123")
                .contextId("context-456")
                .status(new TaskStatus(TaskState.COMPLETED))
                .build();

        operation.parseResponse(exchange, task);

        assertThat(exchange.getIn().getBody()).isSameAs(task);
        assertThat(exchange.getIn().getHeader(A2AConstants.TASK_ID)).isEqualTo("task-123");
        assertThat(exchange.getIn().getHeader(A2AConstants.CONTEXT_ID)).isEqualTo("context-456");
        assertThat(exchange.getIn().getHeader(A2AConstants.TASK_STATE)).isEqualTo("TASK_STATE_COMPLETED");
        assertThat(exchange.getIn().getHeader(A2AConstants.RESPONSE_TYPE)).isEqualTo("task");
    }

    @Test
    void parseRequest() {
        Exchange exchange = new DefaultExchange(context);
        TaskGetRequest request = new TaskGetRequest();
        request.setId("task-789");
        request.setHistoryLength(10);

        operation.parseRequest(exchange, request);

        assertThat(exchange.getIn().getHeader(A2AConstants.TASK_ID)).isEqualTo("task-789");
        assertThat(exchange.getIn().getHeader(A2AConstants.OPERATION)).isEqualTo("GetTask");
        assertThat(exchange.getIn().getHeader(A2AConstants.HISTORY_LENGTH)).isEqualTo(10);
    }

    @Test
    void parseRequestWithoutHistoryLength() {
        Exchange exchange = new DefaultExchange(context);
        TaskGetRequest request = new TaskGetRequest();
        request.setId("task-999");

        operation.parseRequest(exchange, request);

        assertThat(exchange.getIn().getHeader(A2AConstants.TASK_ID)).isEqualTo("task-999");
        assertThat(exchange.getIn().getHeader(A2AConstants.OPERATION)).isEqualTo("GetTask");
        assertThat(exchange.getIn().getHeader(A2AConstants.HISTORY_LENGTH)).isNull();
    }

    @Test
    void buildResponse() {
        Exchange exchange = new DefaultExchange(context);
        Task task = Task.builder()
                .id("task-555")
                .contextId("context-666")
                .build();
        exchange.getIn().setBody(task);

        Object result = operation.buildResponse(exchange);

        assertThat(result).isSameAs(task);
    }

    @Test
    void isInfrastructureOnly() {
        assertThat(operation.isInfrastructureOnly()).isTrue();
    }

    @Test
    void methodName() {
        assertThat(operation.methodName()).isEqualTo("GetTask");
    }
}
