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
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test to confirm REQUEUE header causes message to be re-queued instead of sent to DLQ.
 */
public class RabbitMQRequeueIT extends AbstractRabbitMQIT {
    public static final String ROUTING_KEY = "rk4";
    public static final String DEAD_LETTER_QUEUE_NAME = "dlq";

    @Produce("direct:rabbitMQ")
    protected ProducerTemplate directProducer;

    @EndpointInject("mock:producing")
    private MockEndpoint producingMockEndpoint;

    @EndpointInject("mock:consuming")
    private MockEndpoint consumingMockEndpoint;
    private com.rabbitmq.client.Connection connection;
    private com.rabbitmq.client.Channel channel;
    private com.rabbitmq.client.Channel deadLetterChannel;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        connection = connection();
        channel = connection.createChannel();
        deadLetterChannel = connection.createChannel();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();

        channel.abort();
        deadLetterChannel.abort();
        connection.abort();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        ConnectionProperties connectionProperties = service.connectionProperties();
        String rabbitMQEndpoint = String.format("rabbitmq:localhost:%d/ex4?username=%s&password=%s"
                                                + "&autoAck=false&autoDelete=false&durable=true&queue=q4&deadLetterExchange=dlx&deadLetterExchangeType=fanout"
                                                + "&deadLetterQueue=%s&routingKey=%s",
                connectionProperties.port(), connectionProperties.username(),
                connectionProperties.password(), DEAD_LETTER_QUEUE_NAME, ROUTING_KEY);

        return new RouteBuilder() {

            @Override
            public void configure() {
                from("direct:rabbitMQ").id("producingRoute").log("Sending message").to(ExchangePattern.InOnly, rabbitMQEndpoint)
                        .to(producingMockEndpoint);

                from(rabbitMQEndpoint).id("consumingRoute").log("Receiving message")
                        .to(ExchangePattern.InOnly, consumingMockEndpoint)
                        .throwException(new Exception("Simulated exception"));
            }
        };
    }

    @Test
    public void testNoRequeueHeaderCausesReject() throws Exception {
        final java.util.List<String> received = new java.util.ArrayList<>();

        producingMockEndpoint.expectedMessageCount(1);
        consumingMockEndpoint.expectedMessageCount(1);

        directProducer.sendBody("Hello, World!");
        deadLetterChannel.basicConsume(DEAD_LETTER_QUEUE_NAME, true, new DeadLetterConsumer(received));

        // If message was rejected and not requeued, it will be published in
        // dead letter queue
        await().atMost(5, SECONDS).until(() -> received.size() == 1);

        producingMockEndpoint.assertIsSatisfied();
        consumingMockEndpoint.assertIsSatisfied();
    }

    @Test
    public void testNonBooleanRequeueHeaderCausesReject() throws Exception {
        final java.util.List<String> received = new java.util.ArrayList<>();

        producingMockEndpoint.expectedMessageCount(1);
        consumingMockEndpoint.expectedMessageCount(1);

        directProducer.sendBodyAndHeader("Hello, World!", RabbitMQConstants.REQUEUE, 4L);
        deadLetterChannel.basicConsume(DEAD_LETTER_QUEUE_NAME, true, new DeadLetterConsumer(received));

        // If message was rejected and not requeued, it will be published in
        // dead letter queue
        await().atMost(5, SECONDS).until(() -> received.size() == 1);

        producingMockEndpoint.assertIsSatisfied();
        consumingMockEndpoint.assertIsSatisfied();
    }

    @Test
    public void testFalseRequeueHeaderCausesReject() throws Exception {
        final java.util.List<String> received = new java.util.ArrayList<>();

        producingMockEndpoint.expectedMessageCount(1);
        consumingMockEndpoint.expectedMessageCount(1);

        directProducer.sendBodyAndHeader("Hello, World!", RabbitMQConstants.REQUEUE, false);
        deadLetterChannel.basicConsume(DEAD_LETTER_QUEUE_NAME, true, new DeadLetterConsumer(received));

        // If message was rejected and not requeued, it will be published in
        // dead letter queue
        await().atMost(5, SECONDS).until(() -> received.size() == 1);

        producingMockEndpoint.assertIsSatisfied();
        consumingMockEndpoint.assertIsSatisfied();
    }

    @Test
    public void testTrueRequeueHeaderCausesRequeue() throws Exception {
        final java.util.List<String> received = new java.util.ArrayList<>();

        producingMockEndpoint.expectedMessageCount(1);
        consumingMockEndpoint.setMinimumExpectedMessageCount(2);

        directProducer.sendBodyAndHeader("Hello, World!", RabbitMQConstants.REQUEUE, true);
        deadLetterChannel.basicConsume(DEAD_LETTER_QUEUE_NAME, true, new DeadLetterConsumer(received));

        Awaitility.await().during(1, SECONDS).atMost(2, SECONDS).until(() -> received.size() >= 0);

        // If message was rejected and requeued it will not be published in dead
        // letter queue
        assertEquals(0, received.size());
        producingMockEndpoint.assertIsSatisfied();
        consumingMockEndpoint.assertIsSatisfied();
    }

    private class DeadLetterConsumer extends com.rabbitmq.client.DefaultConsumer {
        private final java.util.List<String> received;

        DeadLetterConsumer(java.util.List<String> received) {
            super(deadLetterChannel);
            this.received = received;
        }

        @Override
        public void handleDelivery(
                String consumerTag, com.rabbitmq.client.Envelope envelope, com.rabbitmq.client.AMQP.BasicProperties properties,
                byte[] body) {
            received.add(new String(body));
        }
    }
}
