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

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.pulsar.utils.AutoConfiguration;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.internal.PulsarAdminBuilderImpl;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.impl.ClientBuilderImpl;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PulsarConsumerPatternInTest extends PulsarTestSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(PulsarConsumerPatternInTest.class);

    private static final String TOPIC_URI = "persistent://public/default/camel-foo";
    private static final String TOPIC_TWO_URI = "persistent://public/default/camel-bar";
    private static final String TOPIC_PATTERN_URI = "persistent://public/default/camel-.*";
    private static final String PRODUCER = "camel-producer-1";

    @EndpointInject("pulsar:" + TOPIC_PATTERN_URI + "?topicsPattern=true"
                    + "&subscriptionName=camel-subscription&consumerQueueSize=5&consumerName=camel-consumer")
    private Endpoint from;

    @EndpointInject("mock:result")
    private MockEndpoint to;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
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

    private PulsarAdmin givenPulsarAdmin() throws PulsarClientException {
        return new PulsarAdminBuilderImpl().serviceHttpUrl(getPulsarAdminUrl()).build();
    }

    @Test
    public void testAMessageToClusterIsConsumed() throws Exception {
        // must create topics first when using topic patterns for the consumer
        givenPulsarAdmin().topics().createNonPartitionedTopic("camel-foo");
        givenPulsarAdmin().topics().createNonPartitionedTopic("camel-bar");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(from).to(to).process(e -> LOGGER.info("Processing message {}", e.getIn().getBody(String.class)));
            }
        });
        context.start();

        to.expectedBodiesReceivedInAnyOrder("Hello World!", "Bye World!");

        Producer<String> producer
                = givenPulsarClient().newProducer(Schema.STRING).producerName(PRODUCER).topic(TOPIC_URI).create();
        producer.send("Hello World!");

        Producer<String> producer2
                = givenPulsarClient().newProducer(Schema.STRING).producerName(PRODUCER).topic(TOPIC_TWO_URI).create();
        producer2.send("Bye World!");

        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, to);
    }
}
