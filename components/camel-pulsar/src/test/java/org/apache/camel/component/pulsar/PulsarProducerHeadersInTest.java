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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.pulsar.utils.AutoConfiguration;
import org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.impl.ClientBuilderImpl;
import org.junit.Test;

public class PulsarProducerHeadersInTest extends PulsarTestSupport {

    private static final String TOPIC_URI = "persistent://public/default/camel-producer-topic";
    private static final String PRODUCER = "camel-producer";

    @Produce("direct:start")
    private ProducerTemplate producerTemplate;

    @EndpointInject("pulsar:" + TOPIC_URI
            + "?numberOfConsumers=1&subscriptionType=Exclusive"
            + "&subscriptionName=camel-subscription"
            + "&consumerQueueSize=1"
            + "&consumerName=camel-consumer"
            + "&producerName=" + PRODUCER
    )
    private Endpoint pulsar;

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                from("direct:start").to(pulsar);
                from(pulsar).to(mock);
            }
        };
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
        return new ClientBuilderImpl()
                .serviceUrl(getPulsarBrokerUrl())
                .ioThreads(1)
                .listenerThreads(1)
                .build();
    }

    @Test
    public void propertyHeaderSetsPulsarProperties() throws InterruptedException {
        Map<String, String> properties = new HashMap<>();
        properties.put("testProperty", "testValue");
        mock.expectedHeaderReceived(PulsarMessageHeaders.PROPERTIES, properties);

        producerTemplate.sendBodyAndHeader("test", PulsarMessageHeaders.PROPERTIES_OUT, properties);

        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, mock);
    }

    @Test
    public void eventTimeHeaderSetsPulsarEventTime() throws InterruptedException {
        long eventTime = 10000;
        mock.expectedHeaderReceived(PulsarMessageHeaders.EVENT_TIME, eventTime);

        producerTemplate.sendBodyAndHeader("test", PulsarMessageHeaders.EVENT_TIME_OUT, eventTime);

        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, mock);
    }

    @Test
    public void keyHeaderSetsPulsarKey() throws InterruptedException {
        String key = "testKey";
        mock.expectedHeaderReceived(PulsarMessageHeaders.KEY, key);

        producerTemplate.sendBodyAndHeader("test", PulsarMessageHeaders.KEY_OUT, key);

        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, mock);
    }
}
