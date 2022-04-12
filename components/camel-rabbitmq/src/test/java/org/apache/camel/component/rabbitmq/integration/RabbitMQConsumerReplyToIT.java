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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.rabbitmq.services.ConnectionProperties;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertListSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test to check if requested direct reply messages are received
 */
public class RabbitMQConsumerReplyToIT extends AbstractRabbitMQIT {
    protected static final String QUEUE = "amq.rabbitmq.reply-to";

    private static final String EXCHANGE = "ex_reply";
    private static final String ROUTING_KEY = "testreply";
    private static final String REQUEST = "Knock! Knock!";
    private static final String REPLY = "Hello world";

    protected Channel channel;

    private Connection connection;

    @BeforeEach
    public void setUpRabbitMQ() throws Exception {
        connection = connection();
        channel = connection.createChannel();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        ConnectionProperties connectionProperties = service.connectionProperties();

        context().setTracing(true);
        return new RouteBuilder() {

            @Override
            public void configure() {
                log.info("Building routes...");

                fromF("rabbitmq:localhost:%d/%s?username=%s&password=%s&routingKey=%s", connectionProperties.port(),
                        EXCHANGE, connectionProperties.username(), connectionProperties.password(), ROUTING_KEY)
                                .log(body().toString()).setBody(simple(REPLY));
            }
        };
    }

    @Test
    public void replyMessageIsReceived() throws IOException, InterruptedException {
        final List<String> received = new ArrayList<>();

        AMQP.BasicProperties.Builder prop = new AMQP.BasicProperties.Builder();
        prop.replyTo(QUEUE);

        channel.basicConsume(QUEUE, true, new ArrayPopulatingConsumer(received));
        channel.basicPublish(EXCHANGE, ROUTING_KEY, prop.build(), REQUEST.getBytes());

        assertThatBodiesReceivedIn(received, REPLY);
    }

    private void assertThatBodiesReceivedIn(final List<String> received, final String... expected) {
        Awaitility.await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertListSize(received, expected.length));

        for (String body : expected) {
            assertEquals(body, received.get(0));
        }
    }

    private class ArrayPopulatingConsumer extends DefaultConsumer {
        private final List<String> received;

        ArrayPopulatingConsumer(final List<String> received) {
            super(RabbitMQConsumerReplyToIT.this.channel);
            this.received = received;
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
            received.add(new String(body));
        }
    }
}
