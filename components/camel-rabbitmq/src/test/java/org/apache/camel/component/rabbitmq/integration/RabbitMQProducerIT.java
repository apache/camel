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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
import org.apache.camel.test.infra.rabbitmq.services.ConnectionProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.assertListSize;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RabbitMQProducerIT extends AbstractRabbitMQIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQProducerIT.class);

    private static final String EXCHANGE = "ex1";
    private static final String ROUTE = "route1";
    private static final String CUSTOM_HEADER = "CustomHeader";

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

    private String getBasicURI(String route) {
        ConnectionProperties connectionProperties = service.connectionProperties();

        return String.format("rabbitmq:%s:%d/%s?routingKey=%s&username=%s&password=%s&skipQueueDeclare=true&skipQueueBind=true",
                connectionProperties.hostname(), connectionProperties.port(),
                EXCHANGE, route, connectionProperties.username(), connectionProperties.password());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        String basicURI = getBasicURI(ROUTE);
        String allowNullHeaders = basicURI + "&allowNullHeaders=true&allowCustomHeaders=false";
        String allowCustomHeaders = basicURI + "&allowCustomHeaders=true";
        String publisherAcknowledgesUri = basicURI + "&mandatory=true&publisherAcknowledgements=true";
        String publisherAcknowledgesBadRouteUri = getBasicURI("route2") + "&publisherAcknowledgements=true";
        String guaranteedDeliveryUri = basicURI + "&mandatory=true&guaranteedDeliveries=true";
        String guaranteedDeliveryBadRouteNotMandatoryUri = getBasicURI("route2") + "&guaranteedDeliveries=true";
        String guaranteedDeliveryBadRouteUri = getBasicURI("route2") + "&mandatory=true&guaranteedDeliveries=true";

        context().setTracing(true);
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to(basicURI);
                from("direct:start-allow-null-headers").to(allowNullHeaders);
                from("direct:start-not-allow-custom-headers").to(allowNullHeaders);
                from("direct:start-allow-custom-headers").to(allowCustomHeaders);
                from("direct:start-with-confirms").to(publisherAcknowledgesUri);
                from("direct:start-with-confirms-bad-route").to(publisherAcknowledgesBadRouteUri);
                from("direct:start-with-guaranteed-delivery").to(guaranteedDeliveryUri);
                from("direct:start-with-guaranteed-delivery-bad-route").to(guaranteedDeliveryBadRouteUri);
                from("direct:start-with-guaranteed-delivery-bad-route-but-not-mandatory")
                        .to(guaranteedDeliveryBadRouteNotMandatoryUri);
            }
        };
    }

    @BeforeEach
    public void setUpRabbitMQ() throws Exception {
        connection = connection();
        channel = connection.createChannel();
        channel.queueDeclare("sammyq", true, false, false, null);
        channel.queueBind("sammyq", EXCHANGE, ROUTE);
    }

    @AfterEach
    public void tearDownRabbitMQ() throws Exception {
        channel.abort();
        connection.abort();
    }

    @Test
    public void producedMessageIsReceived() throws IOException {
        final List<String> received = new ArrayList<>();
        channel.basicConsume("sammyq", true, new ArrayPopulatingConsumer(received));

        template.sendBodyAndHeader("new message", RabbitMQConstants.EXCHANGE_NAME, "ex1");

        assertThatBodiesReceivedIn(received, "new message");
    }

    @Test
    public void producedMessageWithNotNullHeaders() throws InterruptedException, IOException {
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
    public void producedMessageAllowNullHeaders() throws InterruptedException, IOException {
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
    public void producedMessageNotAllowCustomHeaders() throws IOException {
        final List<String> received = new ArrayList<>();
        final Map<String, Object> receivedHeaders = new HashMap<>();
        Map<String, Object> headers = new HashMap<>();

        headers.put(RabbitMQConstants.EXCHANGE_NAME, "testa");
        headers.put(CUSTOM_HEADER, "exchange");

        channel.basicConsume("sammyq", true, new ArrayPopulatingConsumer(received, receivedHeaders));

        templateNotAllowCustomHeaders.sendBodyAndHeaders("new message", headers);

        await().atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertEquals("new message", received.get(0)));
        assertTrue(receivedHeaders.containsKey(RabbitMQConstants.EXCHANGE_NAME));
        assertFalse(receivedHeaders.containsKey(CUSTOM_HEADER));
    }

    @Test
    public void producedMessageAllowCustomHeaders() throws InterruptedException, IOException {
        final List<String> received = new ArrayList<>();
        final Map<String, Object> receivedHeaders = new HashMap<>();
        Map<String, Object> headers = new HashMap<>();

        headers.put(RabbitMQConstants.EXCHANGE_NAME, "testa");
        headers.put(CUSTOM_HEADER, "exchange");

        channel.basicConsume("sammyq", true, new ArrayPopulatingConsumer(received, receivedHeaders));

        templateAllowCustomHeaders.sendBodyAndHeaders("new message", headers);

        await().atMost(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertEquals("new message", received.get(0)));

        assertTrue(receivedHeaders.containsKey(RabbitMQConstants.EXCHANGE_NAME));
        assertTrue(receivedHeaders.containsKey(CUSTOM_HEADER));
    }

    private void assertThatBodiesReceivedIn(final List<String> received, final String... expected) {
        await().atMost(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertListSize(received, expected.length));
        for (String body : expected) {
            assertEquals(body, received.get(0));
        }
    }

    private void assertThatBodiesAndHeadersReceivedIn(
            Map<String, Object> receivedHeaders, Map<String, Object> expectedHeaders, final List<String> received,
            final String... expected) {
        await().atMost(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertListSize(received, expected.length));
        for (String body : expected) {
            assertEquals(body, received.get(0));
        }

        for (Map.Entry<String, Object> headers : expectedHeaders.entrySet()) {
            Object receivedValue = receivedHeaders.get(headers.getKey());
            Object expectedValue = headers.getValue();
            assertTrue(receivedHeaders.containsKey(headers.getKey()), "Header key " + headers.getKey() + " not found");
            assertEquals(0, ObjectHelper.compare(receivedValue == null ? "" : receivedValue.toString(),
                    expectedValue == null ? "" : expectedValue.toString()));
        }

    }

    @Test
    public void producedMessageIsReceivedWhenPublisherAcknowledgementsAreEnabled()
            throws IOException {
        final List<String> received = new ArrayList<>();
        channel.basicConsume("sammyq", true, new ArrayPopulatingConsumer(received));

        templateWithConfirms.sendBodyAndHeader("publisher ack message", RabbitMQConstants.EXCHANGE_NAME, "ex1");

        assertThatBodiesReceivedIn(received, "publisher ack message");
    }

    @Test
    public void producedMessageIsReceivedWhenPublisherAcknowledgementsAreEnabledAndBadRoutingKeyIsUsed()
            throws IOException {
        final List<String> received = new ArrayList<>();
        channel.basicConsume("sammyq", true, new ArrayPopulatingConsumer(received));

        templateWithConfirmsAndBadRoute.sendBody("publisher ack message");

        assertThatBodiesReceivedIn(received);
    }

    @Test
    public void shouldSuccessfullyProduceMessageWhenGuaranteedDeliveryIsActivatedAndMessageIsMarkedAsMandatory()
            throws IOException {
        final List<String> received = new ArrayList<>();
        channel.basicConsume("sammyq", true, new ArrayPopulatingConsumer(received));

        templateWithGuranteedDelivery.sendBodyAndHeader("publisher ack message", RabbitMQConstants.EXCHANGE_NAME, "ex1");

        assertThatBodiesReceivedIn(received, "publisher ack message");
    }

    @Test
    public void shouldFailIfMessageIsMarkedAsMandatoryAndGuaranteedDeliveryIsActiveButNoQueueIsBound() {
        assertThrows(RuntimeCamelException.class,
                () -> templateWithGuranteedDeliveryAndBadRoute.sendBody("publish with ack and return message"));
    }

    @Test
    public void shouldSuccessfullyProduceMessageWhenGuaranteedDeliveryIsActivatedOnABadRouteButMessageIsNotMandatory()
            throws IOException {
        final List<String> received = new ArrayList<>();
        channel.basicConsume("sammyq", true, new ArrayPopulatingConsumer(received));

        templateWithGuranteedDeliveryBadRouteButNotMandatory.sendBodyAndHeader("publisher ack message",
                RabbitMQConstants.EXCHANGE_NAME, "ex1");

        assertThatBodiesReceivedIn(received);
    }

    private class ArrayPopulatingConsumer extends DefaultConsumer {
        private final List<String> received;
        private final Map<String, Object> receivedHeaders;

        ArrayPopulatingConsumer(final List<String> received) {
            super(RabbitMQProducerIT.this.channel);
            this.received = received;
            receivedHeaders = new HashMap<>();
        }

        ArrayPopulatingConsumer(final List<String> received, Map<String, Object> receivedHeaders) {
            super(RabbitMQProducerIT.this.channel);
            this.received = received;
            this.receivedHeaders = receivedHeaders;
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
            LOGGER.info("AMQP.BasicProperties: {}", properties);

            receivedHeaders.putAll(properties.getHeaders());
            received.add(new String(body));
        }
    }
}
