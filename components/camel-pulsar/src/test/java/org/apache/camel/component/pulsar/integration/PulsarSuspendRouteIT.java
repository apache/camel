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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.pulsar.PulsarComponent;
import org.apache.camel.component.pulsar.PulsarMessageReceipt;
import org.apache.camel.component.pulsar.utils.AutoConfiguration;
import org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.impl.ClientBuilderImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PulsarSuspendRouteIT extends PulsarITSupport {

    private static final String TOPIC_URI = "persistent://public/default/camel-topic";
    private static final String PRODUCER_NAME = "test-producer";
    private static final String ROUTE_ID = "a-route";
    private static final Logger LOGGER = LoggerFactory.getLogger(PulsarSuspendRouteIT.class);

    private Endpoint from;

    @EndpointInject("mock:result")
    private MockEndpoint to;

    private Producer<String> producer;
    private String topicName;

    @BeforeEach
    public void setup() throws Exception {
        topicName = randomizeTopicUri(PulsarSuspendRouteIT.TOPIC_URI);
        producer = setUpPulsarClient().newProducer(Schema.STRING).producerName(PRODUCER_NAME).topic(topicName).create();
    }

    @AfterEach
    public void tearDownProducer() {
        try {
            producer.close();
        } catch (PulsarClientException e) {
            LOGGER.warn("Failed to close client: {}", e.getMessage(), e);
        }
        context.stop();
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        registerPulsarBeans(registry);
        return registry;
    }

    private void registerPulsarBeans(SimpleRegistry registry) throws PulsarClientException {
        PulsarClient pulsarClient = setUpPulsarClient();
        AutoConfiguration autoConfiguration = new AutoConfiguration(null, null);

        registry.bind("pulsarClient", pulsarClient);
        PulsarComponent comp = new PulsarComponent(context);
        comp.setAutoConfiguration(autoConfiguration);
        comp.setPulsarClient(pulsarClient);
        registry.bind("pulsar", comp);
    }

    private PulsarClient setUpPulsarClient() throws PulsarClientException {
        return new ClientBuilderImpl().serviceUrl(getPulsarBrokerUrl()).ioThreads(1).listenerThreads(1).build();
    }

    @Test
    public void suspendRouteWithManualAck() throws Exception {
        int consumerQueueSize = 1;
        from = context.getEndpoint("pulsar:" + topicName + "?numberOfConsumers=1&subscriptionType=Exclusive"
                                   + "&subscriptionName=camel-subscription&consumerQueueSize=" + consumerQueueSize
                                   + "&consumerName=camel-consumer&allowManualAcknowledgement=true");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(from).routeId(ROUTE_ID).to(to);
            }
        });

        to.setExpectedMessageCount(1);
        to.setAssertPeriod(2000);

        producer.send("a message");

        MockEndpoint.assertIsSatisfied(context);

        context.getRouteController().suspendRoute(ROUTE_ID);

        // manually acknowledge messages after route is suspended
        to.getReceivedExchanges().forEach(e -> {
            PulsarMessageReceipt receipt
                    = (PulsarMessageReceipt) e.getIn().getHeader(PulsarMessageHeaders.MESSAGE_RECEIPT);
            try {
                receipt.acknowledge();
            } catch (PulsarClientException pulsarClientException) {
                Assertions.fail("Pulsar consumer should be able to acknowledge message when route is suspended");
            }
        });
    }

    @Test
    public void suspendRouteWithBuiltInAck() throws Exception {
        // Given a route that automatically acknowledges Pulsar messages,
        // suspending the route while an exchange is in-flight should raise no exceptions
        int consumerQueueSize = 1;
        from = context.getEndpoint("pulsar:" + topicName + "?numberOfConsumers=1&subscriptionType=Exclusive"
                                   + "&subscriptionName=camel-subscription&consumerQueueSize=" + consumerQueueSize
                                   + "&consumerName=camel-consumer&allowManualAcknowledgement=false");

        CountDownLatch waitForRouteSuspension = new CountDownLatch(1);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(from).routeId(ROUTE_ID).process(e -> assertTrue(waitForRouteSuspension.await(2, TimeUnit.SECONDS)))
                        .to(to);
            }
        });

        to.setExpectedMessageCount(1);
        to.setAssertPeriod(2000);

        producer.send("test");

        context.getRouteController().suspendRoute(ROUTE_ID);
        waitForRouteSuspension.countDown();

        MockEndpoint.assertIsSatisfied(context);

        // Confirm that acknowledging the exchange did not raise an exception
        Exception e = to.getReceivedExchanges().get(0).getException();
        assertNull(e);
    }

    @Test
    public void suspendAndResumeRoute() throws Exception {
        int consumerQueueSize = 1;
        from = context.getEndpoint("pulsar:" + topicName + "?numberOfConsumers=1&subscriptionType=Exclusive"
                                   + "&subscriptionName=camel-subscription&consumerQueueSize=" + consumerQueueSize
                                   + "&consumerName=camel-consumer&allowManualAcknowledgement=false");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(from).routeId(ROUTE_ID).to(to);
            }
        });

        List<MessageId> sentMessageIds = new ArrayList<>();

        to.setExpectedMessageCount(1);
        sentMessageIds.add(producer.send("message 1"));
        MockEndpoint.assertIsSatisfied(context);

        // After suspension, the consumer will process exactly 1 more message (configured by `consumerQueueSize`)
        context.getRouteController().suspendRoute(ROUTE_ID);
        to.setExpectedMessageCount(2);
        to.setAssertPeriod(2000);

        sentMessageIds.add(producer.send("message 2"));
        sentMessageIds.add(producer.send("message 3"));
        sentMessageIds.add(producer.send("message 4"));

        MockEndpoint.assertIsSatisfied(context);

        // Once route is resumed, previously sent messages will also be consumed
        to.setExpectedMessageCount(5);
        to.setAssertPeriod(3000); // Wait 3s because resuming the consumer takes a while

        context.getRouteController().resumeRoute(ROUTE_ID);
        sentMessageIds.add(producer.send("message 5"));

        MockEndpoint.assertIsSatisfied(context);

        List<MessageId> receivedMessageIds = to.getReceivedExchanges().stream()
                .map(e -> e.getIn().getHeader(PulsarMessageHeaders.MESSAGE_ID, MessageId.class))
                .toList();
        assertEquals(sentMessageIds, receivedMessageIds);
    }

    @Test
    public void routeWithNoConsumerQueueReceivesNoMessagesAfterSuspension() throws Exception {
        int consumerQueueSize = 0;
        from = context.getEndpoint("pulsar:" + topicName + "?numberOfConsumers=1&subscriptionType=Exclusive"
                                   + "&subscriptionName=camel-subscription&consumerQueueSize=" + consumerQueueSize
                                   + "&consumerName=camel-consumer&allowManualAcknowledgement=false");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(from).routeId(ROUTE_ID).to(to);
            }
        });

        to.setExpectedMessageCount(1);
        to.setAssertPeriod(2000);

        context.getRouteController().suspendRoute(ROUTE_ID);

        producer.send("a message");
        producer.send("another message");

        MockEndpoint.assertIsSatisfied(context);
    }

    // to prevent leaking test state
    private String randomizeTopicUri(String topicUri) {
        return topicUri + UUID.randomUUID();
    }
}
