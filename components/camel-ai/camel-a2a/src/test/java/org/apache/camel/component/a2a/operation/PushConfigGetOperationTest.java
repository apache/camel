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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.a2a.A2AConstants;
import org.apache.camel.component.a2a.model.TaskPushNotificationConfig;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PushConfigGetOperationTest {

    private final PushConfigGetOperation operation = new PushConfigGetOperation();
    private final CamelContext context = new DefaultCamelContext();

    @Test
    void buildRequestExtractsHeaders() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(A2AConstants.PUSH_CONFIG_ID, "cfg-1");
        exchange.getIn().setHeader(A2AConstants.TASK_ID, "task-1");

        Object result = operation.buildRequest(exchange);

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) result;
        assertThat(params).containsEntry("id", "cfg-1");
        assertThat(params).containsEntry("taskId", "task-1");
    }

    @Test
    void buildRequestWithMissingHeaders() {
        Exchange exchange = new DefaultExchange(context);

        Object result = operation.buildRequest(exchange);

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) result;
        assertThat(params.get("id")).isNull();
        assertThat(params.get("taskId")).isNull();
    }

    @Test
    void parseRequestSetsHeaders() {
        Exchange exchange = new DefaultExchange(context);
        Map<String, Object> params = Map.of("id", "cfg-2", "taskId", "task-2");

        operation.parseRequest(exchange, params);

        assertThat(exchange.getIn().getHeader(A2AConstants.PUSH_CONFIG_ID)).isEqualTo("cfg-2");
        assertThat(exchange.getIn().getHeader(A2AConstants.TASK_ID)).isEqualTo("task-2");
        assertThat(exchange.getIn().getHeader(A2AConstants.OPERATION)).isEqualTo("GetTaskPushNotificationConfig");
    }

    @Test
    void parseResponseSetsBodyAndConfigIdHeader() {
        Exchange exchange = new DefaultExchange(context);
        TaskPushNotificationConfig config = new TaskPushNotificationConfig();
        config.setId("cfg-3");
        config.setUrl("https://example.com/webhook");

        operation.parseResponse(exchange, config);

        assertThat(exchange.getIn().getBody()).isSameAs(config);
        assertThat(exchange.getIn().getHeader(A2AConstants.PUSH_CONFIG_ID)).isEqualTo("cfg-3");
    }

    @Test
    void parseResponseWithNullId() {
        Exchange exchange = new DefaultExchange(context);
        TaskPushNotificationConfig config = new TaskPushNotificationConfig();

        operation.parseResponse(exchange, config);

        assertThat(exchange.getIn().getBody()).isSameAs(config);
        assertThat(exchange.getIn().getHeader(A2AConstants.PUSH_CONFIG_ID)).isNull();
    }

    @Test
    void buildResponseReturnsBodyAsConfig() {
        Exchange exchange = new DefaultExchange(context);
        TaskPushNotificationConfig config = new TaskPushNotificationConfig();
        config.setId("cfg-4");
        exchange.getIn().setBody(config);

        Object result = operation.buildResponse(exchange);

        assertThat(result).isSameAs(config);
    }

    @Test
    void isInfrastructureOnly() {
        assertThat(operation.isInfrastructureOnly()).isTrue();
    }

    @Test
    void methodName() {
        assertThat(operation.methodName()).isEqualTo("GetTaskPushNotificationConfig");
    }

    @Test
    void operationType() {
        assertThat(operation.getOperationType()).isEqualTo(A2AOperations.PUSH_CONFIG_GET);
    }
}
