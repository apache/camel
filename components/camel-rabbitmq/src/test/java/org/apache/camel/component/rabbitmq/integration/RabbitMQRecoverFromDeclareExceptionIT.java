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
package org.apache.camel.component.rabbitmq.integration;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.rabbitmq.RabbitMQComponent;
import org.apache.camel.test.infra.rabbitmq.services.ConnectionProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_METHOD;

/**
 * Integration test to check whether an exception during declaring exchanges/queues during startup of camel context
 * behaves according to the value of {@link RabbitMQComponent#isRecoverFromDeclareException()}.
 */
@TestInstance(PER_METHOD)
public class RabbitMQRecoverFromDeclareExceptionIT extends AbstractRabbitMQIT {

    private static final String EXCHANGE = "testExchange";

    private com.rabbitmq.client.Connection connection;
    private com.rabbitmq.client.Channel channel;

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        connection = connection();
        channel = connection.createChannel();
        channel.exchangeDeclare(EXCHANGE, "direct");

        super.setUp();

    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();

        channel.abort();
        connection.abort();
    }

    @Test
    void testWrongExchangeTypeInterruptsStartupWhenRecoveryOff() throws Exception {
        context.addRoutes(createRouteWithWrongExchangeType(false));
        assertThatExceptionOfType(RuntimeCamelException.class)
                .isThrownBy(() -> context.start())
                .withMessageStartingWith("Unrecoverable error when attempting to declare exchange or queue");
    }

    @Test
    void testWrongExchangeTypeDoesNotInterruptStartup() throws Exception {
        context.addRoutes(createRouteWithWrongExchangeType(true));
        assertThatNoException().isThrownBy(() -> context.start());
    }

    private RoutesBuilder createRouteWithWrongExchangeType(boolean recoverFromDeclareException) {
        ConnectionProperties connectionProperties = service.connectionProperties();
        String rabbitMQEndpoint = String.format("rabbitmq:localhost:%d/%s?exchangeType=fanout" +
                                                "&username=%s&password=%s" +
                                                "&queue=q1" +
                                                "&routingKey=rk1" +
                                                "&recoverFromDeclareException=%b",
                connectionProperties.port(), EXCHANGE, connectionProperties.username(), connectionProperties.password(),
                recoverFromDeclareException);

        return new RouteBuilder(context) {
            @Override
            public void configure() {
                from(rabbitMQEndpoint).id("consumingRoute").log("Receiving message");
            }
        };
    }

}
