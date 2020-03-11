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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
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
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PulsarConsumerInAsynchronousTest extends PulsarTestSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(PulsarConsumerInAsynchronousTest.class);

    private static final String TOPIC_URI_SYNCHRONOUS_FALSE = "persistent://public/default/synchronousFalse";

    private static final String TOPIC_URI_SYNCHRONOUS_FALSE_THROWS_EXCEPTION =
            "persistent://public/default/synchronousFalseThrowsException";

    private static final String TOPIC_URI_SYNCHRONOUS_FALSE_MANUAL_ACK =
            "persistent://public/default/synchronousFalseManualAck";

    private static final String PRODUCER = "camel-producer-1";

    @EndpointInject("pulsar:" + TOPIC_URI_SYNCHRONOUS_FALSE + "?numberOfConsumers=1&subscriptionType=Exclusive"
            + "&subscriptionName=camel-subscription&consumerQueueSize=1&consumerName=camel-consumer"
            + "&synchronous=false")
    private Endpoint synchronousFalse;

    @EndpointInject("pulsar:" + TOPIC_URI_SYNCHRONOUS_FALSE_THROWS_EXCEPTION + "?numberOfConsumers=1&subscriptionType=Exclusive"
            + "&subscriptionName=camel-subscription&consumerQueueSize=1&consumerName=camel-consumer"
            + "&synchronous=false")
    private Endpoint synchronousFalseThrowsException;

    @EndpointInject("pulsar:" + TOPIC_URI_SYNCHRONOUS_FALSE_MANUAL_ACK + "?numberOfConsumers=1&subscriptionType=Exclusive"
            + "&subscriptionName=camel-subscription&consumerQueueSize=1&consumerName=camel-consumer"
            + "&synchronous=false" + "&allowManualAcknowledgement=true" + "&ackTimeoutMillis=1000")
    private Endpoint synchronousFalseManualAck;

    @EndpointInject("mock:result")
    private MockEndpoint to;

    private CountDownLatch countDownLatch;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            Processor processor = new Processor() {
                @Override
                public void process(final Exchange exchange) throws InterruptedException {
                    LOGGER.info("Processing message {}", exchange.getIn().getBody());
                    countDownLatch.countDown();
                    countDownLatch.await(20, TimeUnit.SECONDS);
                }
            };

            Processor manualAckProcessor = new Processor() {
                @Override
                public void process(final Exchange exchange) throws PulsarClientException {
                    LOGGER.info("Processing message {}", exchange.getIn().getBody());
                    PulsarMessageReceipt receipt = (PulsarMessageReceipt)exchange.getIn().getHeader(
                            PulsarMessageHeaders.MESSAGE_RECEIPT);
                    receipt.acknowledge();
                }
            };

            @Override
            public void configure() {

                from(synchronousFalse)
                        .threads(2)
                            .process(processor)
                        .end()
                        .to(to);

                from(synchronousFalseThrowsException)
                        .threads(2)
                            .throwException(new RuntimeException("Processor throws exception."))
                        .end()
                        .to(to);

                from(synchronousFalseManualAck)
                        .threads(2)
                            .process(manualAckProcessor)
                        .end()
                        .to(to);
            }
        };
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();

        registerPulsarBeans(registry);

        return registry;
    }

    private void registerPulsarBeans(SimpleRegistry registry) throws PulsarClientException {
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
    public void testMessagesProcessedAsynchronously() throws Exception {
        countDownLatch = new CountDownLatch(2);

        to.expectedMessageCount(2);

        Producer<String> producer = givenPulsarClient().newProducer(Schema.STRING).producerName(PRODUCER)
                .topic(TOPIC_URI_SYNCHRONOUS_FALSE).create();

        producer.send("One");
        producer.send("Two");

        MockEndpoint.assertIsSatisfied(2, TimeUnit.SECONDS, to);
    }

    @Test
    public void testMessageProcessedAsynchronouslyThrowsException() throws Exception {
        throwsException(TOPIC_URI_SYNCHRONOUS_FALSE_THROWS_EXCEPTION);
    }

    public void throwsException(String topic) throws Exception {
        to.expectedMessageCount(0);
        Producer<String> producer = givenPulsarClient().newProducer(Schema.STRING).producerName(PRODUCER)
                .topic(topic).create();

        producer.send("One");

        MockEndpoint.assertIsSatisfied(2, TimeUnit.SECONDS, to);
    }

    @Test
    public void testMessagesProcessedAsynchronouslyManualAcknowledge() throws Exception {
        manualAcknowledgement(TOPIC_URI_SYNCHRONOUS_FALSE_MANUAL_ACK);
    }

    public void manualAcknowledgement(String topic) throws Exception {
        to.expectsNoDuplicates(body());
        to.expectedMessageCount(1);

        Producer<String> producer = givenPulsarClient().newProducer(Schema.STRING).producerName(PRODUCER)
                .topic(topic).create();

        producer.send("Hello World!");

        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, to);
    }

}
