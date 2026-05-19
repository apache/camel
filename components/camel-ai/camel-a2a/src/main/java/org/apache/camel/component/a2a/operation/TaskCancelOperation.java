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

import org.apache.camel.Exchange;
import org.apache.camel.component.a2a.A2AConstants;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskCancelRequest;

/**
 * Implementation of the CancelTask A2A operation. Infrastructure-only operation for canceling tasks in the task store
 * without dispatching to the user's route.
 */
public class TaskCancelOperation implements A2AOperation {

    @Override
    public A2AOperations getOperationType() {
        return A2AOperations.TASK_CANCEL;
    }

    @Override
    public boolean isInfrastructureOnly() {
        return true;
    }

    @Override
    public Object buildRequest(Exchange exchange) {
        String taskId = exchange.getMessage().getHeader(A2AConstants.TASK_ID, String.class);
        return new TaskCancelRequest(taskId);
    }

    @Override
    public void parseResponse(Exchange exchange, Object response) {
        applyTaskHeaders(exchange, (Task) response);
    }

    @Override
    public void parseRequest(Exchange exchange, Object request) {
        TaskCancelRequest taskCancelRequest = (TaskCancelRequest) request;

        exchange.getMessage().setHeader(A2AConstants.TASK_ID, taskCancelRequest.getId());
        exchange.getMessage().setHeader(A2AConstants.OPERATION, methodName());
    }

    @Override
    public Object buildResponse(Exchange exchange) {
        // Return the canceled task from the exchange body
        return exchange.getMessage().getBody(Task.class);
    }
}
