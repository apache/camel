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
import org.apache.camel.component.a2a.model.TaskPushNotificationConfig;

/**
 * Implementation of the CreateTaskPushNotificationConfig A2A operation. Infrastructure-only operation for registering
 * push notification endpoints.
 */
public class PushConfigCreateOperation implements A2AOperation {

    @Override
    public A2AOperations getOperationType() {
        return A2AOperations.PUSH_CONFIG_CREATE;
    }

    @Override
    public boolean isInfrastructureOnly() {
        return true;
    }

    @Override
    public Object buildRequest(Exchange exchange) {
        TaskPushNotificationConfig config = exchange.getMessage().getBody(TaskPushNotificationConfig.class);
        if (config == null) {
            throw new IllegalArgumentException(
                    "Exchange body must be a TaskPushNotificationConfig for " + methodName() + " operation");
        }
        config.setTaskId(exchange.getMessage().getHeader(A2AConstants.TASK_ID, String.class));
        return config;
    }

    @Override
    public void parseRequest(Exchange exchange, Object request) {
        TaskPushNotificationConfig config = (TaskPushNotificationConfig) request;
        exchange.getMessage().setBody(config);
        exchange.getMessage().setHeader(A2AConstants.TASK_ID, config.getTaskId());
        exchange.getMessage().setHeader(A2AConstants.OPERATION, methodName());
    }

    @Override
    public void parseResponse(Exchange exchange, Object response) {
        TaskPushNotificationConfig config = (TaskPushNotificationConfig) response;
        exchange.getMessage().setBody(config);
        exchange.getMessage().setHeader(A2AConstants.PUSH_CONFIG_ID, config.getId());
    }

    @Override
    public Object buildResponse(Exchange exchange) {
        return exchange.getMessage().getBody(TaskPushNotificationConfig.class);
    }
}
