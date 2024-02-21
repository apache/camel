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

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.pulsar.PulsarComponent;
import org.apache.camel.component.pulsar.PulsarMessageReceipt;
import org.apache.camel.component.pulsar.PulsarMessageReceiptFactory;
import org.apache.camel.component.pulsar.utils.AutoConfiguration;
import org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.impl.ClientBuilderImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class PulsarCustomMessageReceiptIT extends PulsarITSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(PulsarCustomMessageReceiptIT.class);

    private static final String TOPIC_URI = "persistent://public/default/camel-topic/PulsarCustomMessageReceiptIT";
    private static final String PRODUCER = "camel-producer-1";

    public PulsarMessageReceiptFactory mockPulsarMessageReceiptFactory = mock(PulsarMessageReceiptFactory.class);

    public PulsarMessageReceipt mockPulsarMessageReceipt = mock(PulsarMessageReceipt.class);

    @EndpointInject("pulsar:" + TOPIC_URI + "?numberOfConsumers=1&subscriptionType=Exclusive"
                    + "&subscriptionName=camel-subscription&consumerQueueSize=1&consumerName=camel-consumer"
                    + "&allowManualAcknowledgement=true" + "&ackTimeoutMillis=1000")
    private Endpoint from;

    @EndpointInject("mock:result")
    private MockEndpoint to;

    private Producer<String> producer;

    @BeforeEach
    public void setup() throws Exception {
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
        // Test adding a custom PulsarMessageReceiptFactory
        comp.setPulsarMessageReceiptFactory(mockPulsarMessageReceiptFactory);
        registry.bind("pulsar", comp);
    }

    private PulsarClient givenPulsarClient() throws PulsarClientException {
        return new ClientBuilderImpl().serviceUrl(getPulsarBrokerUrl()).ioThreads(1).listenerThreads(1).build();
    }

    @Test
    public void testAcknowledgeWithCustomMessageReceipt() throws Exception {
        to.expectedMinimumMessageCount(2);

        when(mockPulsarMessageReceiptFactory.newInstance(any(), any(), any())).thenReturn(mockPulsarMessageReceipt);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(from).routeId("myRoute").to(to).process(exchange -> {
                    LOGGER.info("Processing message {}", exchange.getIn().getBody());

                    PulsarMessageReceipt receipt
                            = (PulsarMessageReceipt) exchange.getIn().getHeader(PulsarMessageHeaders.MESSAGE_RECEIPT);
                    receipt.acknowledge();
                });
            }
        });

        producer.send("Hello World!");

        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, to);

        // The mock does not actually acknowledge using the pulsar consumer, so
        // the message will be re-consumed
        // after the ackTimeout.
        verify(mockPulsarMessageReceipt, atLeast(2)).acknowledge();
        verifyNoMoreInteractions(mockPulsarMessageReceipt);
    }

}
