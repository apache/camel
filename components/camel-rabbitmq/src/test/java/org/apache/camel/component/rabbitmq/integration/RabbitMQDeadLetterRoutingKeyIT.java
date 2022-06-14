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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.rabbitmq.services.ConnectionProperties;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.assertListSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RabbitMQDeadLetterRoutingKeyIT extends AbstractRabbitMQIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQDeadLetterRoutingKeyIT.class);

    private Connection connection;
    private Channel channel;
    private Channel deadLetterChannel;

    @EndpointInject(uri = "mock:received")
    private MockEndpoint receivedEndpoint;

    @Produce(uri = "direct:start")
    private ProducerTemplate template;

    @Override
    protected RouteBuilder createRouteBuilder() {
        ConnectionProperties connectionProperties = service.connectionProperties();

        String consumer = String.format("rabbitmq:ex9?hostname=%s&portNumber=%d&username=%s&password=%s"
                                        + "&skipExchangeDeclare=false&skipQueueDeclare=false&autoDelete=false&durable=true&autoAck=false"
                                        + "&queue=q9&routingKey=rk1&deadLetterExchange=dlx&deadLetterQueue=dlq&deadLetterExchangeType=fanout",
                connectionProperties.hostname(), connectionProperties.port(), connectionProperties.username(),
                connectionProperties.password());

        String consumerWithDlqRoutingKey = String.format("rabbitmq:ex10?hostname=%s&portNumber=%d&username=%s&password=%s"
                                                         + "&skipExchangeDeclare=false&skipQueueDeclare=false&autoDelete=false&durable=true"
                                                         + "&autoAck=false&queue=q10&routingKey=rk1&deadLetterExchange=dlx&deadLetterQueue=dlq"
                                                         + "&deadLetterExchangeType=fanout&deadLetterRoutingKey=rk2",
                connectionProperties.hostname(), connectionProperties.port(), connectionProperties.username(),
                connectionProperties.password());

        return new RouteBuilder() {

            @Override
            public void configure() {
                from(consumer).to(receivedEndpoint);
                from(consumerWithDlqRoutingKey).to(receivedEndpoint);
            }
        };
    }

    @BeforeEach
    public void setUpRabbitMQ() throws Exception {
        connection = connection();
        channel = connection.createChannel();
        deadLetterChannel = connection.createChannel();
    }

    @AfterEach
    public void tearDownRabbitMQ() throws Exception {
        channel.abort();
        deadLetterChannel.abort();
        connection.abort();
    }

    @Test
    public void originalRoutingKeyIsReceived() throws IOException {
        final List<String> received = new ArrayList<>();
        final StringBuilder routingKey = new StringBuilder();

        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().contentType("text/plain")
                .contentEncoding(StandardCharsets.UTF_8.toString()).build();

        receivedEndpoint.whenAnyExchangeReceived(exchange -> {
            throw new Exception("Simulated exception");
        });

        channel.basicPublish("ex9", "rk1", properties, "new message".getBytes(StandardCharsets.UTF_8));

        deadLetterChannel.basicConsume("dlq", true, new DeadLetterRoutingKeyConsumer(received, routingKey));

        Awaitility.await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertListSize(received, 1));
        assertEquals("rk1", routingKey.toString());
    }

    @Test
    public void deadLetterRoutingKeyIsReceived() throws IOException, InterruptedException {
        final List<String> received = new ArrayList<>();
        StringBuilder routingKey = new StringBuilder();

        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().contentType("text/plain")
                .contentEncoding(StandardCharsets.UTF_8.toString()).build();

        receivedEndpoint.whenAnyExchangeReceived(exchange -> {
            throw new Exception("Simulated exception");
        });

        channel.basicPublish("ex10", "rk1", properties, "new message".getBytes(StandardCharsets.UTF_8));

        deadLetterChannel.basicConsume("dlq", true, new DeadLetterRoutingKeyConsumer(received, routingKey));

        Awaitility.await().atMost(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertListSize(received, 1));

        assertEquals("rk2", routingKey.toString());
    }

    private class DeadLetterRoutingKeyConsumer extends DefaultConsumer {
        private final StringBuilder routingKey;
        private final List<String> received;

        DeadLetterRoutingKeyConsumer(final List<String> received, final StringBuilder routingKey) {
            super(RabbitMQDeadLetterRoutingKeyIT.this.deadLetterChannel);
            this.received = received;
            this.routingKey = routingKey;
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
            LOGGER.info("AMQP.BasicProperties: {}", properties);

            received.add(new String(body));
            routingKey.append(envelope.getRoutingKey());
        }
    }
}
