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
import org.apache.camel.component.pulsar.utils.AutoConfiguration;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.infra.common.TestUtils;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.impl.ClientBuilderImpl;
import org.apache.pulsar.client.impl.MultiplierRedeliveryBackoff;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PulsarConsumerNoAcknowledgementIT extends PulsarITSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(PulsarConsumerNoAcknowledgementIT.class);
    private static final String TOPIC_URI_PREFIX = "persistent://public/default/camel-no-ack-topic-";
    private static int topicId;

    private Endpoint from;

    @EndpointInject("mock:result")
    private MockEndpoint to;

    private Producer<String> producer;

    @BeforeEach
    public void setup() throws Exception {
        context.removeRoute("myRoute");
        String producerName = this.getClass().getSimpleName() + TestUtils.randomWithRange(1, 100);

        String topicUri = TOPIC_URI_PREFIX + ++topicId;
        producer = givenPulsarClient().newProducer(Schema.STRING).producerName(producerName).topic(topicUri).create();

        from = context.getEndpoint("pulsar:" + topicUri + "?numberOfConsumers=1&subscriptionType=Exclusive"
                                   + "&subscriptionName=camel-subscription&consumerQueueSize=1&consumerName=camel-consumer");
        to = context.getEndpoint("mock:result", MockEndpoint.class);
    }

    @AfterEach
    public void tearDown() {
        try {
            if (from != null) {
                from.close();
            }
            if (producer != null) {
                producer.close();
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to close resources: {}", e.getMessage(), e);
        }
        try {
            context.removeRoute("myRoute");
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        registerPulsarBeans(registry);
    }

    private void registerPulsarBeans(Registry registry) throws PulsarClientException {
        PulsarClient pulsarClient = givenPulsarClient();
        AutoConfiguration autoConfiguration = new AutoConfiguration(null, null);

        registry.bind("pulsarClient", pulsarClient);
        PulsarComponent comp = new PulsarComponent(context);
        comp.setAutoConfiguration(autoConfiguration);
        comp.setPulsarClient(pulsarClient);
        comp.getConfiguration()
                .setAllowManualAcknowledgement(true); // Set to true here instead of the endpoint query parameter.
        // Given relevant millis=1000 redeliveries will occur at 1s + 0.01s, 1s + 1s, 1s + 100s, 1s + 100s, 1s + 100s...
        comp.getConfiguration().setAckTimeoutMillis(1_000L);
        comp.getConfiguration().setAckTimeoutRedeliveryBackoff(MultiplierRedeliveryBackoff.builder()
                .minDelayMs(10L)
                .maxDelayMs(100_000L)
                .multiplier(100.0)
                .build());
        registry.bind("pulsar", comp);
    }

    private PulsarClient givenPulsarClient() throws PulsarClientException {
        return new ClientBuilderImpl().serviceUrl(getPulsarBrokerUrl()).ioThreads(1).listenerThreads(1).build();
    }

    @Test
    public void testAMessageIsConsumedMultipleTimes() throws Exception {
        to.expectedMinimumMessageCount(2);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // Nothing in the route will ack the message.
                from(from).routeId("myRoute").to(to);
            }
        });

        producer.send("Hello World!");

        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, to);
    }

    @Test
    public void testAMessageIsConsumedMultipleTimesWithAckTimeoutBackoff() throws Exception {
        to.expectedMinimumMessageCount(3);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // Nothing in the route will ack the message.
                from(from).routeId("myRoute").to(to);
            }
        });

        producer.send("Hello World!");

        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, to);
    }
}
