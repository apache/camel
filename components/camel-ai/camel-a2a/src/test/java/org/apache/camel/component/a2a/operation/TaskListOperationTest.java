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

import java.util.Arrays;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.a2a.A2AConstants;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskListRequest;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TaskStatus;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskListOperationTest {

    private final TaskListOperation operation = new TaskListOperation();
    private final CamelContext context = new DefaultCamelContext();

    @Test
    void buildRequestFromHeaders() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("CamelA2AListContextId", "ctx-1");
        exchange.getIn().setHeader("CamelA2AListPageSize", 10);
        exchange.getIn().setHeader("CamelA2AListStatus", "WORKING,FAILED");

        Object result = operation.buildRequest(exchange);

        assertThat(result).isInstanceOf(TaskListRequest.class);
        TaskListRequest request = (TaskListRequest) result;
        assertThat(request.getContextId()).isEqualTo("ctx-1");
        assertThat(request.getPageSize()).isEqualTo(10);
        assertThat(request.getStatus()).containsExactly("WORKING", "FAILED");
    }

    @Test
    void buildRequestWithAllHeaders() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("CamelA2AListContextId", "ctx-123");
        exchange.getIn().setHeader("CamelA2AListPageSize", 25);
        exchange.getIn().setHeader("CamelA2AListPageToken", "token-abc");
        exchange.getIn().setHeader("CamelA2AListIncludeArtifacts", true);
        exchange.getIn().setHeader("CamelA2AListHistoryLength", 5);
        exchange.getIn().setHeader("CamelA2AListStatusTimestampAfter", "2026-05-19T12:00:00Z");
        exchange.getIn().setHeader("CamelA2AListStatus", "COMPLETED,WORKING");

        Object result = operation.buildRequest(exchange);

        assertThat(result).isInstanceOf(TaskListRequest.class);
        TaskListRequest request = (TaskListRequest) result;
        assertThat(request.getContextId()).isEqualTo("ctx-123");
        assertThat(request.getPageSize()).isEqualTo(25);
        assertThat(request.getPageToken()).isEqualTo("token-abc");
        assertThat(request.getIncludeArtifacts()).isTrue();
        assertThat(request.getHistoryLength()).isEqualTo(5);
        assertThat(request.getStatusTimestampAfter()).isEqualTo("2026-05-19T12:00:00Z");
        assertThat(request.getStatus()).containsExactly("COMPLETED", "WORKING");
    }

    @Test
    void buildRequestWithNoHeaders() {
        Exchange exchange = new DefaultExchange(context);

        Object result = operation.buildRequest(exchange);

        assertThat(result).isInstanceOf(TaskListRequest.class);
        TaskListRequest request = (TaskListRequest) result;
        assertThat(request.getContextId()).isNull();
        assertThat(request.getPageSize()).isNull();
        assertThat(request.getPageToken()).isNull();
        assertThat(request.getIncludeArtifacts()).isNull();
        assertThat(request.getHistoryLength()).isNull();
        assertThat(request.getStatusTimestampAfter()).isNull();
        assertThat(request.getStatus()).isNull();
    }

    @Test
    void buildRequestHandlesEmptyStatusString() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("CamelA2AListStatus", "");

        Object result = operation.buildRequest(exchange);

        assertThat(result).isInstanceOf(TaskListRequest.class);
        TaskListRequest request = (TaskListRequest) result;
        assertThat(request.getStatus()).isNull();
    }

    @Test
    void buildRequestHandlesWhitespaceInStatus() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("CamelA2AListStatus", " WORKING , FAILED , COMPLETED ");

        Object result = operation.buildRequest(exchange);

        assertThat(result).isInstanceOf(TaskListRequest.class);
        TaskListRequest request = (TaskListRequest) result;
        assertThat(request.getStatus()).containsExactly("WORKING", "FAILED", "COMPLETED");
    }

    @Test
    void parseResponseSetsTaskListBody() {
        Exchange exchange = new DefaultExchange(context);
        Task task1 = Task.builder()
                .id("task-1")
                .status(new TaskStatus(TaskState.COMPLETED))
                .build();

        Task task2 = Task.builder()
                .id("task-2")
                .status(new TaskStatus(TaskState.WORKING))
                .build();

        List<Task> tasks = Arrays.asList(task1, task2);

        operation.parseResponse(exchange, tasks);

        assertThat(exchange.getIn().getBody()).isSameAs(tasks);
        assertThat(exchange.getIn().getHeader(A2AConstants.RESPONSE_TYPE)).isEqualTo("taskList");
    }

    @Test
    void parseRequest() {
        Exchange exchange = new DefaultExchange(context);
        TaskListRequest request = new TaskListRequest();
        request.setContextId("ctx-999");
        request.setPageSize(50);

        operation.parseRequest(exchange, request);

        assertThat(exchange.getIn().getHeader(A2AConstants.CONTEXT_ID)).isEqualTo("ctx-999");
        assertThat(exchange.getIn().getHeader(A2AConstants.OPERATION)).isEqualTo("ListTasks");
    }

    @Test
    void parseRequestWithNoContextId() {
        Exchange exchange = new DefaultExchange(context);
        TaskListRequest request = new TaskListRequest();

        operation.parseRequest(exchange, request);

        assertThat(exchange.getIn().getHeader(A2AConstants.CONTEXT_ID)).isNull();
        assertThat(exchange.getIn().getHeader(A2AConstants.OPERATION)).isEqualTo("ListTasks");
    }

    @Test
    void buildResponse() {
        Exchange exchange = new DefaultExchange(context);
        List<Task> tasks = Arrays.asList(
                Task.builder().build(),
                Task.builder().build());
        exchange.getIn().setBody(tasks);

        Object result = operation.buildResponse(exchange);

        assertThat(result).isSameAs(tasks);
    }

    @Test
    void isInfrastructureOnly() {
        assertThat(operation.isInfrastructureOnly()).isTrue();
    }

    @Test
    void methodName() {
        assertThat(operation.methodName()).isEqualTo("ListTasks");
    }
}
