/**
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
package org.apache.camel.component.rabbitmq;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RabbitMQProducerIntTest extends CamelTestSupport {
    private static final String EXCHANGE = "ex1";
    private static final String ROUTE = "route1";
    private static final String BASIC_URI_FORMAT = "rabbitmq:localhost:5672/%s?routingKey=%s&username=cameltest&password=cameltest&skipQueueDeclare=true";
    private static final String BASIC_URI = String.format(BASIC_URI_FORMAT, EXCHANGE, ROUTE);
    private static final String PUBLISHER_ACKNOWLEDGES_URI = BASIC_URI + "&mandatory=true&publisherAcknowledgements=true";
    private static final String PUBLISHER_ACKNOWLEDGES_BAD_ROUTE_URI = String.format(BASIC_URI_FORMAT, EXCHANGE, "route2") + "&publisherAcknowledgements=true";

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

    @Produce(uri = "direct:start-with-confirms")
    protected ProducerTemplate templateWithConfirms;

    @Produce(uri = "direct:start-with-confirms-bad-route")
    protected ProducerTemplate templateWithConfirmsAndBadRoute;

    private Connection connection;
    private Channel channel;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        context().setTracing(true);
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to(BASIC_URI);
                from("direct:start-with-confirms").to(PUBLISHER_ACKNOWLEDGES_URI);
                from("direct:start-with-confirms-bad-route").to(PUBLISHER_ACKNOWLEDGES_BAD_ROUTE_URI);
            }
        };
    }

    @Before
    public void setUpRabbitMQ() throws Exception {
        connection = createTestConnection();
        channel = connection.createChannel();
        channel.queueDeclare("sammyq", false, false, true, null);
        channel.queueBind("sammyq", EXCHANGE, ROUTE);
    }

    @After
    public void tearDownRabbitMQ() throws Exception {
        channel.abort();
        connection.abort();
    }

    @Test
    public void producedMessageIsReceived() throws InterruptedException, IOException, TimeoutException {
        final List<String> received = new ArrayList<>();
        channel.basicConsume("sammyq", true, new ArrayPopulatingConsumer(received));

        template.sendBodyAndHeader("new message", RabbitMQConstants.EXCHANGE_NAME, "ex1");

        assertThatBodiesReceivedIn(received, "new message");
    }

    private void assertThatBodiesReceivedIn(final List<String> received, final String... expected) throws InterruptedException {
        Thread.sleep(500);

        assertListSize(received, expected.length);
        for (String body : expected) {
            assertEquals(body, received.get(0));
        }
    }

    @Test
    public void producedMessageIsReceivedWhenPublisherAcknowledgementsAreEnabled() throws InterruptedException, IOException, TimeoutException {
        final List<String> received = new ArrayList<>();
        channel.basicConsume("sammyq", true, new ArrayPopulatingConsumer(received));

        templateWithConfirms.sendBodyAndHeader("publisher ack message", RabbitMQConstants.EXCHANGE_NAME, "ex1");

        assertThatBodiesReceivedIn(received, "publisher ack message");
    }

    @Test
    public void producedMessageIsReceivedWhenPublisherAcknowledgementsAreEnabledAndBadRoutingKeyIsUsed() throws InterruptedException, IOException, TimeoutException {
        final List<String> received = new ArrayList<>();
        channel.basicConsume("sammyq", true, new ArrayPopulatingConsumer(received));

        templateWithConfirmsAndBadRoute.sendBody("publisher ack message");

        assertThatBodiesReceivedIn(received);
    }

    private Connection createTestConnection() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5672);
        factory.setUsername("cameltest");
        factory.setPassword("cameltest");
        factory.setVirtualHost("/");
        return factory.newConnection();
    }

    private class ArrayPopulatingConsumer extends DefaultConsumer {
        private final List<String> received;

        public ArrayPopulatingConsumer(final List<String> received) {
            super(RabbitMQProducerIntTest.this.channel);
            this.received = received;
        }

        @Override
        public void handleDelivery(String consumerTag,
                                   Envelope envelope,
                                   AMQP.BasicProperties properties,
                                   byte[] body) throws IOException {
            received.add(new String(body));
        }
    }
}

