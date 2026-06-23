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
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.component.a2a.A2AConstants;
import org.apache.camel.component.a2a.model.ListTasksResponse;
import org.apache.camel.component.a2a.model.TaskListRequest;

/**
 * Implementation of the ListTasks A2A operation. Infrastructure-only operation for querying the task store with
 * filtering and pagination support.
 */
public class TaskListOperation implements A2AOperation {

    @Override
    public A2AOperations getOperationType() {
        return A2AOperations.TASK_LIST;
    }

    @Override
    public boolean isInfrastructureOnly() {
        return true;
    }

    @Override
    public Object buildRequest(Exchange exchange) {
        TaskListRequest request = new TaskListRequest();

        // Extract filter/pagination params from headers
        String contextId = exchange.getMessage().getHeader(A2AConstants.LIST_CONTEXT_ID, String.class);
        if (contextId != null) {
            request.setContextId(contextId);
        }

        Integer pageSize = exchange.getMessage().getHeader(A2AConstants.LIST_PAGE_SIZE, Integer.class);
        if (pageSize != null) {
            request.setPageSize(pageSize);
        }

        String pageToken = exchange.getMessage().getHeader(A2AConstants.LIST_PAGE_TOKEN, String.class);
        if (pageToken != null) {
            request.setPageToken(pageToken);
        }

        Boolean includeArtifacts = exchange.getMessage().getHeader(A2AConstants.LIST_INCLUDE_ARTIFACTS, Boolean.class);
        if (includeArtifacts != null) {
            request.setIncludeArtifacts(includeArtifacts);
        }

        Integer historyLength = exchange.getMessage().getHeader(A2AConstants.LIST_HISTORY_LENGTH, Integer.class);
        if (historyLength != null) {
            request.setHistoryLength(historyLength);
        }

        String statusTimestampAfter
                = exchange.getMessage().getHeader(A2AConstants.LIST_STATUS_TIMESTAMP_AFTER, String.class);
        if (statusTimestampAfter != null) {
            request.setStatusTimestampAfter(statusTimestampAfter);
        }

        // Parse comma-separated status list
        String statusStr = exchange.getMessage().getHeader(A2AConstants.LIST_STATUS, String.class);
        if (statusStr != null && !statusStr.trim().isEmpty()) {
            List<String> statusList = Arrays.stream(statusStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            if (!statusList.isEmpty()) {
                request.setStatus(statusList);
            }
        }

        return request;
    }

    @Override
    public void parseResponse(Exchange exchange, Object response) {
        if (response instanceof ListTasksResponse listResponse) {
            exchange.getMessage().setBody(listResponse.tasks());
        } else {
            exchange.getMessage().setBody(response);
        }
        exchange.getMessage().setHeader(A2AConstants.RESPONSE_TYPE, "taskList");
    }

    @Override
    public void parseRequest(Exchange exchange, Object request) {
        TaskListRequest taskListRequest = (TaskListRequest) request;

        // Set headers from request fields
        if (taskListRequest.getContextId() != null) {
            exchange.getMessage().setHeader(A2AConstants.CONTEXT_ID, taskListRequest.getContextId());
        }

        exchange.getMessage().setHeader(A2AConstants.OPERATION, methodName());
    }

    @Override
    public Object buildResponse(Exchange exchange) {
        // Return the task list from the exchange body
        return exchange.getMessage().getBody();
    }
}
