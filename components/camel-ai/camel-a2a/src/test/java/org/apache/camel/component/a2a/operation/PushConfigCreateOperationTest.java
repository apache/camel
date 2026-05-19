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
import org.apache.camel.component.a2a.model.TaskPushNotificationConfig;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PushConfigCreateOperationTest {

    private final PushConfigCreateOperation operation = new PushConfigCreateOperation();
    private final CamelContext context = new DefaultCamelContext();

    @Test
    void buildRequestFromConfigBody() {
        Exchange exchange = new DefaultExchange(context);
        TaskPushNotificationConfig config = new TaskPushNotificationConfig();
        config.setId("config-1");
        config.setUrl("https://example.com/webhook");
        exchange.getIn().setBody(config);
        exchange.getIn().setHeader(A2AConstants.TASK_ID, "task-100");

        Object result = operation.buildRequest(exchange);

        assertThat(result).isInstanceOf(TaskPushNotificationConfig.class);
        TaskPushNotificationConfig resultConfig = (TaskPushNotificationConfig) result;
        assertThat(resultConfig).isSameAs(config);
        assertThat(resultConfig.getTaskId()).isEqualTo("task-100");
        assertThat(resultConfig.getUrl()).isEqualTo("https://example.com/webhook");
    }

    @Test
    void buildRequestSetsTaskIdFromHeader() {
        Exchange exchange = new DefaultExchange(context);
        TaskPushNotificationConfig config = new TaskPushNotificationConfig();
        config.setTaskId("original-task");
        exchange.getIn().setBody(config);
        exchange.getIn().setHeader(A2AConstants.TASK_ID, "override-task");

        Object result = operation.buildRequest(exchange);

        TaskPushNotificationConfig resultConfig = (TaskPushNotificationConfig) result;
        assertThat(resultConfig.getTaskId()).isEqualTo("override-task");
    }

    @Test
    void buildRequestThrowsWhenBodyIsNull() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(null);

        assertThatThrownBy(() -> operation.buildRequest(exchange))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TaskPushNotificationConfig");
    }

    @Test
    void buildRequestThrowsWhenBodyIsWrongType() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("not a config object");

        assertThatThrownBy(() -> operation.buildRequest(exchange))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CreateTaskPushNotificationConfig");
    }

    @Test
    void parseRequestSetsBodyAndHeaders() {
        Exchange exchange = new DefaultExchange(context);
        TaskPushNotificationConfig config = new TaskPushNotificationConfig();
        config.setId("config-2");
        config.setTaskId("task-200");
        config.setUrl("https://example.com/hook");

        operation.parseRequest(exchange, config);

        assertThat(exchange.getIn().getBody()).isSameAs(config);
        assertThat(exchange.getIn().getHeader(A2AConstants.TASK_ID)).isEqualTo("task-200");
        assertThat(exchange.getIn().getHeader(A2AConstants.OPERATION)).isEqualTo("CreateTaskPushNotificationConfig");
    }

    @Test
    void parseResponseSetsBodyAndConfigIdHeader() {
        Exchange exchange = new DefaultExchange(context);
        TaskPushNotificationConfig config = new TaskPushNotificationConfig();
        config.setId("config-3");
        config.setTaskId("task-300");

        operation.parseResponse(exchange, config);

        assertThat(exchange.getIn().getBody()).isSameAs(config);
        assertThat(exchange.getIn().getHeader(A2AConstants.PUSH_CONFIG_ID)).isEqualTo("config-3");
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
        config.setId("config-4");
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
        assertThat(operation.methodName()).isEqualTo("CreateTaskPushNotificationConfig");
    }

    @Test
    void operationType() {
        assertThat(operation.getOperationType()).isEqualTo(A2AOperations.PUSH_CONFIG_CREATE);
    }
}
