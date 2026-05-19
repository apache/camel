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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.a2a.A2AConstants;
import org.apache.camel.component.a2a.model.TaskPushNotificationConfig;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PushConfigListOperationTest {

    private final PushConfigListOperation operation = new PushConfigListOperation();
    private final CamelContext context = new DefaultCamelContext();

    @Test
    void buildRequestExtractsTaskIdHeader() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(A2AConstants.TASK_ID, "task-42");

        Object result = operation.buildRequest(exchange);

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) result;
        assertThat(params).containsEntry("taskId", "task-42");
    }

    @Test
    void buildRequestWithNoTaskId() {
        Exchange exchange = new DefaultExchange(context);

        Object result = operation.buildRequest(exchange);

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) result;
        assertThat(params.get("taskId")).isNull();
    }

    @Test
    void parseRequestSetsHeaders() {
        Exchange exchange = new DefaultExchange(context);
        Map<String, Object> params = Map.of("taskId", "task-55");

        operation.parseRequest(exchange, params);

        assertThat(exchange.getIn().getHeader(A2AConstants.TASK_ID)).isEqualTo("task-55");
        assertThat(exchange.getIn().getHeader(A2AConstants.OPERATION)).isEqualTo("ListTaskPushNotificationConfigs");
    }

    @Test
    void parseRequestWithNullTaskId() {
        Exchange exchange = new DefaultExchange(context);
        Map<String, Object> params = new HashMap<>();
        params.put("taskId", null);

        operation.parseRequest(exchange, params);

        assertThat(exchange.getIn().getHeader(A2AConstants.TASK_ID)).isNull();
        assertThat(exchange.getIn().getHeader(A2AConstants.OPERATION)).isEqualTo("ListTaskPushNotificationConfigs");
    }

    @Test
    void parseResponseSetsBodyAndResponseType() {
        Exchange exchange = new DefaultExchange(context);
        TaskPushNotificationConfig config1 = new TaskPushNotificationConfig();
        config1.setId("cfg-a");
        TaskPushNotificationConfig config2 = new TaskPushNotificationConfig();
        config2.setId("cfg-b");
        List<TaskPushNotificationConfig> configs = Arrays.asList(config1, config2);

        operation.parseResponse(exchange, configs);

        assertThat(exchange.getIn().getBody()).isSameAs(configs);
        assertThat(exchange.getIn().getHeader(A2AConstants.RESPONSE_TYPE)).isEqualTo("pushConfigList");
    }

    @Test
    void parseResponseWithEmptyList() {
        Exchange exchange = new DefaultExchange(context);
        List<TaskPushNotificationConfig> configs = List.of();

        operation.parseResponse(exchange, configs);

        assertThat(exchange.getIn().getBody()).isSameAs(configs);
        assertThat(exchange.getIn().getHeader(A2AConstants.RESPONSE_TYPE)).isEqualTo("pushConfigList");
    }

    @Test
    void buildResponseReturnsBodyAsList() {
        Exchange exchange = new DefaultExchange(context);
        TaskPushNotificationConfig config = new TaskPushNotificationConfig();
        config.setId("cfg-c");
        List<TaskPushNotificationConfig> configs = List.of(config);
        exchange.getIn().setBody(configs);

        Object result = operation.buildResponse(exchange);

        assertThat(result).isSameAs(configs);
    }

    @Test
    void isInfrastructureOnly() {
        assertThat(operation.isInfrastructureOnly()).isTrue();
    }

    @Test
    void methodName() {
        assertThat(operation.methodName()).isEqualTo("ListTaskPushNotificationConfigs");
    }

    @Test
    void operationType() {
        assertThat(operation.getOperationType()).isEqualTo(A2AOperations.PUSH_CONFIG_LIST);
    }
}
