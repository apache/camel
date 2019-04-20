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
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.pulsar.utils.AutoConfiguration;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.impl.ClientBuilderImpl;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.PulsarContainer;

public class PulsarProducerInTest extends CamelTestSupport {

    private static final String TOPIC_URI = "persistent://public/default/camel-producer-topic";
    private static final String PRODUCER = "camel-producer";

    @Rule
    public PulsarContainer pulsarContainer = new PulsarContainer();

    @Produce(uri = "direct:start")
    private ProducerTemplate producerTemplate;

    @EndpointInject(uri = "pulsar:" + TOPIC_URI
        + "?numberOfConsumers=1&subscriptionType=Exclusive"
        + "&subscriptionName=camel-subscription&consumerQueueSize=1"
        + "&consumerName=camel-consumer"
        + "&producerName=" + PRODUCER
    )
    private Endpoint from;

    @EndpointInject(uri = "mock:result")
    private MockEndpoint to;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                from("direct:start").to(from);
                from(from).to(to);
            }
        };
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();

        registerPulsarBeans(jndi);

        return jndi;
    }

    private void registerPulsarBeans(final JndiRegistry jndi) throws PulsarClientException {
        PulsarClient pulsarClient = givenPulsarClient();
        AutoConfiguration autoConfiguration = new AutoConfiguration(null, null);

        jndi.bind("pulsarClient", pulsarClient);
        PulsarComponent comp = new PulsarComponent(context);
        comp.setAutoConfiguration(autoConfiguration);
        comp.setPulsarClient(pulsarClient);
        jndi.bind("pulsar", comp);
    }

    private PulsarClient givenPulsarClient() throws PulsarClientException {
        return new ClientBuilderImpl()
            .serviceUrl(pulsarContainer.getPulsarBrokerUrl())
            .ioThreads(1)
            .listenerThreads(1)
            .build();
    }

    @Test
    public void testAMessageToRouteIsSentAndThenConsumed() throws Exception {
        to.expectedMessageCount(3);

        producerTemplate.sendBody("Hello ");
        producerTemplate.sendBody("World ");
        producerTemplate.sendBody(10);

        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, to);
    }
}
