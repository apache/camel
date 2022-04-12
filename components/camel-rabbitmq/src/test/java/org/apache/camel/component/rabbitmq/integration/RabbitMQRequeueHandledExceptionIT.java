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

import org.apache.camel.EndpointInject;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.rabbitmq.RabbitMQConstants;
import org.apache.camel.test.infra.rabbitmq.services.ConnectionProperties;
import org.junit.jupiter.api.Test;

/**
 * Integration test to confirm REQUEUE header causes message not to be re-queued when an handled exception occurs.
 */
public class RabbitMQRequeueHandledExceptionIT extends AbstractRabbitMQIT {
    public static final String ROUTING_KEY = "rk4";

    @Produce("direct:rabbitMQ")
    protected ProducerTemplate directProducer;

    @EndpointInject("mock:producing")
    private MockEndpoint producingMockEndpoint;

    @EndpointInject("mock:consuming")
    private MockEndpoint consumingMockEndpoint;

    @Override
    protected RouteBuilder createRouteBuilder() {
        ConnectionProperties connectionProperties = service.connectionProperties();

        String rabbitMQEndpoint
                = String.format("rabbitmq:localhost:%d/ex4?username=%s&password=%s&autoAck=false&queue=q4&routingKey=%s",
                        connectionProperties.port(), connectionProperties.username(), connectionProperties.password(),
                        ROUTING_KEY);

        return new RouteBuilder() {

            @Override
            public void configure() {
                from("direct:rabbitMQ").id("producingRoute").log("Sending message").to(ExchangePattern.InOnly, rabbitMQEndpoint)
                        .to(producingMockEndpoint);

                from(rabbitMQEndpoint).onException(Exception.class).handled(true).end().id("consumingRoute")
                        .log("Receiving message").to(ExchangePattern.InOnly, consumingMockEndpoint)
                        .throwException(new Exception("Simulated handled exception"));
            }
        };
    }

    @Test
    public void testTrueRequeueHeaderWithHandleExceptionNotCausesRequeue() throws Exception {
        producingMockEndpoint.expectedMessageCount(1);
        consumingMockEndpoint.setMinimumExpectedMessageCount(1);

        directProducer.sendBodyAndHeader("Hello, World!", RabbitMQConstants.REQUEUE, true);

        producingMockEndpoint.assertIsSatisfied();
        consumingMockEndpoint.assertIsSatisfied();
    }
}
