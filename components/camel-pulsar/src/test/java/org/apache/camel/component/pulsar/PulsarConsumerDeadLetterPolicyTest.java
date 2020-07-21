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
package org.apache.camel.component.pulsar;

import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.pulsar.utils.AutoConfiguration;
import org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.impl.ClientBuilderImpl;
import org.junit.Before;
import org.junit.Test;

public class PulsarConsumerDeadLetterPolicyTest extends PulsarTestSupport {

    private static final String TOPIC_URI = "persistent://public/default/camel-topic";
    private static final String PRODUCER = "camel-producer-1";

    @EndpointInject("mock:result")
    private MockEndpoint to;

    @EndpointInject("mock:deadLetter")
    private MockEndpoint deadLetter;

    private Producer<String> producer;

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

    @Before
    public void buildProducer() throws PulsarClientException {
        try {
            context.removeRoute("myRoute");
            context.removeRoute("myDeadLetterRoute");
        } catch (Exception ignored) {

        }
        producer = givenPulsarClient().newProducer(Schema.STRING).producerName(PRODUCER).topic(TOPIC_URI).create();

    }

    @Test
    public void givenNoMaxRedeliverCountAndDeadLetterTopicverifyValuesAreNull() throws Exception {
        PulsarComponent component = context.getComponent("pulsar", PulsarComponent.class);

        PulsarEndpoint endpoint = (PulsarEndpoint) component.createEndpoint("pulsar:" + TOPIC_URI);

        assertNull(endpoint.getPulsarConfiguration().getMaxRedeliverCount());
        assertNull(endpoint.getPulsarConfiguration().getDeadLetterTopic());
    }

    @Test
    public void givenMaxRedeliverCountverifyMessageGetsSentToDefaultDeadLetterTopicAfterCountExceeded()
            throws Exception {
        PulsarComponent component = context.getComponent("pulsar", PulsarComponent.class);

        PulsarEndpoint from = (PulsarEndpoint) component.createEndpoint("pulsar:" + TOPIC_URI + "?maxRedeliverCount=5&subscriptionType=Shared&allowManualAcknowledgement=true&ackTimeoutMillis=1000");
        PulsarEndpoint deadLetterFrom = (PulsarEndpoint) component.createEndpoint("pulsar:" + TOPIC_URI + "-subs-DLQ");

        to.expectedMessageCount(5);
        deadLetter.expectedMessageCount(1);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(from).routeId("myRoute").to(to);

                from(deadLetterFrom).routeId("myDeadLetterRoute").to(deadLetter);
            }
        });
        producer.send("Hello World!");

        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);
    }

    @Test
    public void givenMaxRedeliverCountAndDeadLetterTopicverifyMessageGetsSentToSpecifiedDeadLetterTopicAfterCountExceeded() throws Exception {
        PulsarComponent component = context.getComponent("pulsar", PulsarComponent.class);

        PulsarEndpoint from = (PulsarEndpoint) component.createEndpoint("pulsar:" + TOPIC_URI + "?maxRedeliverCount=5&deadLetterTopic=customTopic&subscriptionType=Shared&allowManualAcknowledgement=true&ackTimeoutMillis=1000");
        PulsarEndpoint deadLetterFrom = (PulsarEndpoint) component.createEndpoint("pulsar:persistent://public/default/customTopic");

        to.expectedMessageCount(5);
        deadLetter.expectedMessageCount(1);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(from).routeId("myRoute").to(to);

                from(deadLetterFrom).routeId("myDeadLetterRoute").to(deadLetter);
            }
        });

        producer.send("Hello World!");
        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);
    }

    @Test
    public void givenOnlyDeadLetterTopicverifyMessageDoesNotGetSentToSpecifiedTopic() throws Exception {
        PulsarComponent component = context.getComponent("pulsar", PulsarComponent.class);

        PulsarEndpoint from = (PulsarEndpoint) component.createEndpoint("pulsar:" + TOPIC_URI + "?maxRedeliverCount=5&deadLetterTopic=customTopic&subscriptionType=Shared&allowManualAcknowledgement=true&ackTimeoutMillis=1000");
        PulsarEndpoint deadLetterFrom = (PulsarEndpoint) component.createEndpoint("pulsar:persistent://public/default/customTopic");

        to.expectedMessageCount(6);
        deadLetter.expectedMessageCount(0);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(from).routeId("myRoute").to(to).process(exchange -> {
                    Integer tries = exchange.getProperty("retryCount", 1, Integer.class);
                    if (tries >= 6) {
                        PulsarMessageReceipt receipt = (PulsarMessageReceipt) exchange.getIn().getHeader(PulsarMessageHeaders.MESSAGE_RECEIPT);
                        receipt.acknowledge();
                    }
                    exchange.setProperty("retryCount", tries + 1);
                });

                from(deadLetterFrom).routeId("myDeadLetterRoute").to(deadLetter);
            }
        });

        producer.send("Hello World!");
        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);
    }

    private PulsarClient givenPulsarClient() throws PulsarClientException {
        return new ClientBuilderImpl().serviceUrl(getPulsarBrokerUrl()).ioThreads(1).listenerThreads(1).build();
    }
}
