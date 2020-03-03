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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PulsarConsumerAcknowledgementTest extends PulsarTestSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(PulsarConsumerAcknowledgementTest.class);

    private static final String TOPIC_URI = "persistent://public/default/camel-topic";
    private static final String PRODUCER = "camel-producer-1";

    @EndpointInject("pulsar:" + TOPIC_URI + "?numberOfConsumers=1&subscriptionType=Exclusive"
                          + "&subscriptionName=camel-subscription&consumerQueueSize=1&consumerName=camel-consumer" + "&allowManualAcknowledgement=true" + "&ackTimeoutMillis=1000" + "&negativeAckRedeliveryDelayMicros=100000")
    private Endpoint from;

    @EndpointInject("mock:result")
    private MockEndpoint to;

    private Producer<String> producer;

    @Before
    public void setup() throws Exception {
        context.removeRoute("myRoute");
        producer = givenPulsarClient().newProducer(Schema.STRING).producerName(PRODUCER).topic(TOPIC_URI).create();
    }

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

    private PulsarClient givenPulsarClient() throws PulsarClientException {
        return new ClientBuilderImpl().serviceUrl(getPulsarBrokerUrl()).ioThreads(1).listenerThreads(1).build();
    }

    @Test
    public void testAcknowledge() throws Exception {
        to.expectsNoDuplicates(body());

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(from).routeId("myRoute").to(to).process(exchange -> {
                    LOGGER.info("Processing message {}", exchange.getIn().getBody());

                    PulsarMessageReceipt receipt = (PulsarMessageReceipt)exchange.getIn().getHeader(PulsarMessageHeaders.MESSAGE_RECEIPT);
                    receipt.acknowledge();
                });
            }
        });

        producer.send("Hello World!");

        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, to);
    }

    @Test
    public void testAcknowledgeAsync() throws Exception {
        to.expectsNoDuplicates(body());

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(from).routeId("myRoute").to(to).process(exchange -> {
                    LOGGER.info("Processing message {}", exchange.getIn().getBody());

                    PulsarMessageReceipt receipt = (PulsarMessageReceipt)exchange.getIn().getHeader(PulsarMessageHeaders.MESSAGE_RECEIPT);
                    try {
                        CompletableFuture<Void> f = receipt.acknowledgeAsync();
                        f.get();
                    } catch (Exception e) {
                        LOGGER.error(e.getMessage());
                    }
                });
            }
        });

        producer.send("Hello World!");

        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, to);
    }

    @Test
    public void testAcknowledgeCumulative() throws Exception {
        to.expectsNoDuplicates(body());

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(from).routeId("myRoute").to(to).process(exchange -> {
                    LOGGER.info("Processing message {}", exchange.getIn().getBody());

                    PulsarMessageReceipt receipt = (PulsarMessageReceipt)exchange.getIn().getHeader(PulsarMessageHeaders.MESSAGE_RECEIPT);
                    // Ack the second message. The first will also be acked.
                    if (exchange.getIn().getBody().equals("Hello World Again!")) {
                        receipt.acknowledgeCumulative();
                    }
                });
            }
        });

        producer.send("Hello World!");
        producer.send("Hello World Again!");

        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, to);
    }

    @Test
    public void testAcknowledgeCumulativeAsync() throws Exception {
        to.expectsNoDuplicates(body());

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(from).routeId("myRoute").to(to).process(exchange -> {
                    LOGGER.info("Processing message {}", exchange.getIn().getBody());

                    PulsarMessageReceipt receipt = (PulsarMessageReceipt)exchange.getIn().getHeader(PulsarMessageHeaders.MESSAGE_RECEIPT);
                    // Ack the second message. The first will also be acked.
                    if (exchange.getIn().getBody().equals("Hello World Again!")) {
                        try {
                            CompletableFuture<Void> f = receipt.acknowledgeCumulativeAsync();
                            f.get();
                        } catch (Exception e) {
                            LOGGER.error(e.getMessage());
                        }
                    }
                });
            }
        });

        producer.send("Hello World!");
        producer.send("Hello World Again!");

        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, to);
    }

    @Test
    public void testNegativeAcknowledge() throws Exception {
        to.expectedMessageCount(2);
        to.expectedBodiesReceived("Hello World!", "Hello World!");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(from).routeId("myRoute").to(to).process(exchange -> {
                    LOGGER.info("Processing message {}", exchange.getIn().getBody());

                    if (!Boolean.parseBoolean(exchange.getProperty("processedOnce", String.class))) {
                        exchange.setProperty("processedOnce", "true");
                        PulsarMessageReceipt receipt = (PulsarMessageReceipt) exchange.getIn()
                                .getHeader(PulsarMessageHeaders.MESSAGE_RECEIPT);
                        receipt.negativeAcknowledge();
                    } else {
                        PulsarMessageReceipt receipt = (PulsarMessageReceipt) exchange.getIn()
                                .getHeader(PulsarMessageHeaders.MESSAGE_RECEIPT);
                        receipt.acknowledge();
                    }
                });
            }
        });

        producer.newMessage().value("Hello World!").property("processedOnce", "false").send();

        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, to);
    }
}
