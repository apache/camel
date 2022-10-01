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
package org.apache.camel.component.pulsar.integration;

import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.pulsar.PulsarComponent;
import org.apache.camel.component.pulsar.PulsarEndpoint;
import org.apache.camel.component.pulsar.PulsarMessageReceipt;
import org.apache.camel.component.pulsar.utils.AutoConfiguration;
import org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.test.infra.common.TestUtils;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.impl.ClientBuilderImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNull;

public class PulsarConsumerDeadLetterPolicyIT extends PulsarITSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(PulsarConsumerDeadLetterPolicyIT.class);
    private static final String TOPIC_URI = "persistent://public/default/camel-topic-";
    private static int topicId;

    @EndpointInject("mock:result")
    private MockEndpoint to;

    @EndpointInject("mock:deadLetter")
    private MockEndpoint deadLetter;

    private Producer<String> producer;

    private String topicUri;

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = new SimpleRegistry();

        registerPulsarBeans(registry);

        return registry;
    }

    private void registerPulsarBeans(final Registry registry) throws PulsarClientException {
        PulsarClient pulsarClient = givenPulsarClient();
        AutoConfiguration autoConfiguration = new AutoConfiguration(null, null);

        registry.bind("pulsarClient", pulsarClient);
        PulsarComponent comp = new PulsarComponent(context);
        comp.setAutoConfiguration(autoConfiguration);
        comp.setPulsarClient(pulsarClient);
        registry.bind("pulsar", comp);
    }

    @BeforeEach
    public void buildProducer() throws PulsarClientException {
        try {
            context.removeRoute("myRoute");
            context.removeRoute("myDeadLetterRoute");
        } catch (Exception ignored) {

        }
        String producerName = this.getClass().getSimpleName() + TestUtils.randomWithRange(1, 100);

        topicUri = PulsarConsumerDeadLetterPolicyIT.TOPIC_URI + ++topicId;
        producer = givenPulsarClient().newProducer(Schema.STRING).producerName(producerName).topic(topicUri).create();
    }

    @AfterEach
    public void tearDownProducer() {
        try {
            producer.close();
        } catch (PulsarClientException e) {
            LOGGER.warn("Failed to close client: {}", e.getMessage(), e);
        }
    }

    @Test
    public void givenNoMaxRedeliverCountAndDeadLetterTopicverifyValuesAreNull() throws Exception {
        PulsarComponent component = context.getComponent("pulsar", PulsarComponent.class);

        PulsarEndpoint endpoint = (PulsarEndpoint) component.createEndpoint("pulsar:" + topicUri);

        assertNull(endpoint.getPulsarConfiguration().getMaxRedeliverCount());
        assertNull(endpoint.getPulsarConfiguration().getDeadLetterTopic());
    }

    @Test
    public void givenMaxRedeliverCountVerifyMessageGetsSentToDefaultDeadLetterTopicAfterCountExceeded()
            throws Exception {
        PulsarComponent component = context.getComponent("pulsar", PulsarComponent.class);

        PulsarEndpoint from = (PulsarEndpoint) component
                .createEndpoint("pulsar:" + topicUri
                                + "?maxRedeliverCount=5&subscriptionType=Shared&allowManualAcknowledgement=true&ackTimeoutMillis=1000");
        PulsarEndpoint deadLetterFrom = (PulsarEndpoint) component.createEndpoint("pulsar:" + topicUri + "-subs-DLQ");

        to.expectedMessageCount(6);
        deadLetter.expectedMessageCount(1);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(from).routeId("myRoute").to(to);

                from(deadLetterFrom).routeId("myDeadLetterRoute").to(deadLetter);
            }
        });
        producer.send("Hello World!");

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
    }

    @Test
    public void givenMaxRedeliverCountAndDeadLetterTopicVerifyMessageGetsSentToSpecifiedDeadLetterTopicAfterCountExceeded()
            throws Exception {
        PulsarComponent component = context.getComponent("pulsar", PulsarComponent.class);

        PulsarEndpoint from = (PulsarEndpoint) component
                .createEndpoint("pulsar:" + topicUri
                                + "?maxRedeliverCount=5&deadLetterTopic=customTopic&subscriptionType=Shared&allowManualAcknowledgement=true&ackTimeoutMillis=1000");
        PulsarEndpoint deadLetterFrom
                = (PulsarEndpoint) component.createEndpoint("pulsar:persistent://public/default/customTopic");

        to.expectedMessageCount(6);
        deadLetter.expectedMessageCount(1);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(from).routeId("myRoute").to(to);

                from(deadLetterFrom).routeId("myDeadLetterRoute").to(deadLetter);
            }
        });

        producer.send("Hello World!");
        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
    }

    @Test
    public void givenOnlyDeadLetterTopicVerifyMessageDoesNotGetSentToSpecifiedTopic() throws Exception {
        PulsarComponent component = context.getComponent("pulsar", PulsarComponent.class);

        PulsarEndpoint from = (PulsarEndpoint) component
                .createEndpoint("pulsar:" + topicUri
                                + "?maxRedeliverCount=5&deadLetterTopic=customTopic&subscriptionType=Shared&allowManualAcknowledgement=true&ackTimeoutMillis=1000");
        PulsarEndpoint deadLetterFrom
                = (PulsarEndpoint) component.createEndpoint("pulsar:persistent://public/default/customTopic");

        to.expectedMessageCount(6);
        deadLetter.expectedMessageCount(0);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(from).routeId("myRoute").to(to).process(exchange -> {
                    Integer tries = exchange.getProperty("retryCount", 1, Integer.class);
                    if (tries >= 6) {
                        PulsarMessageReceipt receipt
                                = (PulsarMessageReceipt) exchange.getIn().getHeader(PulsarMessageHeaders.MESSAGE_RECEIPT);
                        receipt.acknowledge();
                    }
                    exchange.setProperty("retryCount", tries + 1);
                });

                from(deadLetterFrom).routeId("myDeadLetterRoute").to(deadLetter);
            }
        });

        producer.send("Hello World!");
        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
    }

    private PulsarClient givenPulsarClient() throws PulsarClientException {
        return new ClientBuilderImpl().serviceUrl(getPulsarBrokerUrl()).ioThreads(1).listenerThreads(1).build();
    }
}
