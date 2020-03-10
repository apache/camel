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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.rabbitmq.RabbitMQConstants;
import org.apache.camel.support.ObjectHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RabbitMQProducerIntTest extends AbstractRabbitMQIntTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQProducerIntTest.class);

    private static final String EXCHANGE = "ex1";
    private static final String ROUTE = "route1";
    private static final String CUSTOM_HEADER = "CustomHeader";
    private static final String BASIC_URI_FORMAT = "rabbitmq:localhost:5672/%s?routingKey=%s&username=cameltest&password=cameltest&skipQueueDeclare=true";
    private static final String BASIC_URI = String.format(BASIC_URI_FORMAT, EXCHANGE, ROUTE);
    private static final String ALLOW_NULL_HEADERS = BASIC_URI + "&allowNullHeaders=true&allowCustomHeaders=false";
    private static final String ALLOW_CUSTOM_HEADERS = BASIC_URI + "&allowCustomHeaders=true";
    private static final String PUBLISHER_ACKNOWLEDGES_URI = BASIC_URI + "&mandatory=true&publisherAcknowledgements=true";
    private static final String PUBLISHER_ACKNOWLEDGES_BAD_ROUTE_URI = String.format(BASIC_URI_FORMAT, EXCHANGE, "route2") + "&publisherAcknowledgements=true";
    private static final String GUARANTEED_DELIVERY_URI = BASIC_URI + "&mandatory=true&guaranteedDeliveries=true";
    private static final String GUARANTEED_DELIVERY_BAD_ROUTE_NOT_MANDATORY_URI = String.format(BASIC_URI_FORMAT, EXCHANGE, "route2") + "&guaranteedDeliveries=true";
    private static final String GUARANTEED_DELIVERY_BAD_ROUTE_URI = String.format(BASIC_URI_FORMAT, EXCHANGE, "route2") + "&mandatory=true&guaranteedDeliveries=true";

    @Produce("direct:start")
    protected ProducerTemplate template;

    @Produce("direct:start-allow-null-headers")
    protected ProducerTemplate templateAllowNullHeaders;

    @Produce("direct:start-not-allow-custom-headers")
    protected ProducerTemplate templateNotAllowCustomHeaders;

    @Produce("direct:start-allow-custom-headers")
    protected ProducerTemplate templateAllowCustomHeaders;

    @Produce("direct:start-with-confirms")
    protected ProducerTemplate templateWithConfirms;

    @Produce("direct:start-with-confirms-bad-route")
    protected ProducerTemplate templateWithConfirmsAndBadRoute;

    @Produce("direct:start-with-guaranteed-delivery")
    protected ProducerTemplate templateWithGuranteedDelivery;

    @Produce("direct:start-with-guaranteed-delivery-bad-route")
    protected ProducerTemplate templateWithGuranteedDeliveryAndBadRoute;

    @Produce("direct:start-with-guaranteed-delivery-bad-route-but-not-mandatory")
    protected ProducerTemplate templateWithGuranteedDeliveryBadRouteButNotMandatory;

    private Connection connection;
    private Channel channel;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        context().setTracing(true);
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to(BASIC_URI);
                from("direct:start-allow-null-headers").to(ALLOW_NULL_HEADERS);
                from("direct:start-not-allow-custom-headers").to(ALLOW_NULL_HEADERS);
                from("direct:start-allow-custom-headers").to(ALLOW_CUSTOM_HEADERS);
                from("direct:start-with-confirms").to(PUBLISHER_ACKNOWLEDGES_URI);
                from("direct:start-with-confirms-bad-route").to(PUBLISHER_ACKNOWLEDGES_BAD_ROUTE_URI);
                from("direct:start-with-guaranteed-delivery").to(GUARANTEED_DELIVERY_URI);
                from("direct:start-with-guaranteed-delivery-bad-route").to(GUARANTEED_DELIVERY_BAD_ROUTE_URI);
                from("direct:start-with-guaranteed-delivery-bad-route-but-not-mandatory").to(GUARANTEED_DELIVERY_BAD_ROUTE_NOT_MANDATORY_URI);
            }
        };
    }

    @Before
    public void setUpRabbitMQ() throws Exception {
        connection = connection();
        channel = connection.createChannel();
        channel.queueDeclare("sammyq", true, false, false, null);
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

    @Test
    public void producedMessageWithNotNullHeaders() throws InterruptedException, IOException, TimeoutException {
        final List<String> received = new ArrayList<>();
        final Map<String, Object> receivedHeaders = new HashMap<>();
        Map<String, Object> headers = new HashMap<>();

        headers.put(RabbitMQConstants.EXCHANGE_NAME, EXCHANGE);
        headers.put(CUSTOM_HEADER, CUSTOM_HEADER.toLowerCase());

        channel.basicConsume("sammyq", true, new ArrayPopulatingConsumer(received, receivedHeaders));

        template.sendBodyAndHeaders("new message", headers);

        assertThatBodiesAndHeadersReceivedIn(receivedHeaders, headers, received, "new message");
    }

    @Test
    public void producedMessageAllowNullHeaders() throws InterruptedException, IOException, TimeoutException {
        final List<String> received = new ArrayList<>();
        final Map<String, Object> receivedHeaders = new HashMap<>();
        Map<String, Object> headers = new HashMap<>();

        headers.put(RabbitMQConstants.EXCHANGE_NAME, null);
        headers.put(CUSTOM_HEADER, null);

        channel.basicConsume("sammyq", true, new ArrayPopulatingConsumer(received, receivedHeaders));

        templateAllowNullHeaders.sendBodyAndHeaders("new message", headers);

        assertThatBodiesAndHeadersReceivedIn(receivedHeaders, headers, received, "new message");
    }

    @Test
    public void producedMessageNotAllowCustomHeaders() throws InterruptedException, IOException, TimeoutException {
        final List<String> received = new ArrayList<>();
        final Map<String, Object> receivedHeaders = new HashMap<>();
        Map<String, Object> headers = new HashMap<>();

        headers.put(RabbitMQConstants.EXCHANGE_NAME, "testa");
        headers.put(CUSTOM_HEADER, "exchange");

        channel.basicConsume("sammyq", true, new ArrayPopulatingConsumer(received, receivedHeaders));

        templateNotAllowCustomHeaders.sendBodyAndHeaders("new message", headers);

        Thread.sleep(500);
        assertEquals(received.get(0), "new message");
        assertTrue(receivedHeaders.containsKey(RabbitMQConstants.EXCHANGE_NAME));
        assertFalse(receivedHeaders.containsKey(CUSTOM_HEADER));
    }

    @Test
    public void producedMessageAllowCustomHeaders() throws InterruptedException, IOException, TimeoutException {
        final List<String> received = new ArrayList<>();
        final Map<String, Object> receivedHeaders = new HashMap<>();
        Map<String, Object> headers = new HashMap<>();

        headers.put(RabbitMQConstants.EXCHANGE_NAME, "testa");
        headers.put(CUSTOM_HEADER, "exchange");

        channel.basicConsume("sammyq", true, new ArrayPopulatingConsumer(received, receivedHeaders));

        templateAllowCustomHeaders.sendBodyAndHeaders("new message", headers);

        Thread.sleep(500);
        assertEquals(received.get(0), "new message");
        assertTrue(receivedHeaders.containsKey(RabbitMQConstants.EXCHANGE_NAME));
        assertTrue(receivedHeaders.containsKey(CUSTOM_HEADER));
    }

    private void assertThatBodiesReceivedIn(final List<String> received, final String... expected) throws InterruptedException {
        Thread.sleep(500);

        assertListSize(received, expected.length);
        for (String body : expected) {
            assertEquals(body, received.get(0));
        }
    }

    private void assertThatBodiesAndHeadersReceivedIn(Map<String, Object> receivedHeaders, Map<String, Object> expectedHeaders, final List<String> received,
                                                      final String... expected)
        throws InterruptedException {
        Thread.sleep(500);

        assertListSize(received, expected.length);
        for (String body : expected) {
            assertEquals(body, received.get(0));
        }

        for (Map.Entry<String, Object> headers : expectedHeaders.entrySet()) {
            Object receivedValue = receivedHeaders.get(headers.getKey());
            Object expectedValue = headers.getValue();
            assertTrue("Header key " + headers.getKey() + " not found", receivedHeaders.containsKey(headers.getKey()));
            assertEquals(0, ObjectHelper.compare(receivedValue == null ? "" : receivedValue.toString(), expectedValue == null ? "" : expectedValue.toString()));
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

    @Test
    public void shouldSuccessfullyProduceMessageWhenGuaranteedDeliveryIsActivatedAndMessageIsMarkedAsMandatory() throws InterruptedException, IOException, TimeoutException {
        final List<String> received = new ArrayList<>();
        channel.basicConsume("sammyq", true, new ArrayPopulatingConsumer(received));

        templateWithGuranteedDelivery.sendBodyAndHeader("publisher ack message", RabbitMQConstants.EXCHANGE_NAME, "ex1");

        assertThatBodiesReceivedIn(received, "publisher ack message");
    }

    @Test(expected = RuntimeCamelException.class)
    public void shouldFailIfMessageIsMarkedAsMandatoryAndGuaranteedDeliveryIsActiveButNoQueueIsBound() {
        templateWithGuranteedDeliveryAndBadRoute.sendBody("publish with ack and return message");
    }

    @Test
    public void shouldSuccessfullyProduceMessageWhenGuaranteedDeliveryIsActivatedOnABadRouteButMessageIsNotMandatory() throws InterruptedException, IOException, TimeoutException {
        final List<String> received = new ArrayList<>();
        channel.basicConsume("sammyq", true, new ArrayPopulatingConsumer(received));

        templateWithGuranteedDeliveryBadRouteButNotMandatory.sendBodyAndHeader("publisher ack message", RabbitMQConstants.EXCHANGE_NAME, "ex1");

        assertThatBodiesReceivedIn(received);
    }

    private class ArrayPopulatingConsumer extends DefaultConsumer {
        private final List<String> received;
        private final Map<String, Object> receivedHeaders;

        ArrayPopulatingConsumer(final List<String> received) {
            super(RabbitMQProducerIntTest.this.channel);
            this.received = received;
            receivedHeaders = new HashMap<>();
        }

        ArrayPopulatingConsumer(final List<String> received, Map<String, Object> receivedHeaders) {
            super(RabbitMQProducerIntTest.this.channel);
            this.received = received;
            this.receivedHeaders = receivedHeaders;
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            LOGGER.info("AMQP.BasicProperties: {}", properties);

            receivedHeaders.putAll(properties.getHeaders());
            received.add(new String(body));
        }
    }
}
