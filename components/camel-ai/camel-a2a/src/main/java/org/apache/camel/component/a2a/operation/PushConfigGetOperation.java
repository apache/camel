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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.component.a2a.A2AConstants;
import org.apache.camel.component.a2a.model.TaskPushNotificationConfig;

/**
 * Implementation of the GetTaskPushNotificationConfig A2A operation. Infrastructure-only operation for retrieving a
 * specific push notification configuration.
 */
public class PushConfigGetOperation implements A2AOperation {

    @Override
    public A2AOperations getOperationType() {
        return A2AOperations.PUSH_CONFIG_GET;
    }

    @Override
    public boolean isInfrastructureOnly() {
        return true;
    }

    @Override
    public Object buildRequest(Exchange exchange) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", exchange.getMessage().getHeader(A2AConstants.PUSH_CONFIG_ID, String.class));
        params.put("taskId", exchange.getMessage().getHeader(A2AConstants.TASK_ID, String.class));
        return params;
    }

    @Override
    public void parseRequest(Exchange exchange, Object request) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request;
        exchange.getMessage().setHeader(A2AConstants.PUSH_CONFIG_ID, params.get("id"));
        exchange.getMessage().setHeader(A2AConstants.TASK_ID, params.get("taskId"));
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
