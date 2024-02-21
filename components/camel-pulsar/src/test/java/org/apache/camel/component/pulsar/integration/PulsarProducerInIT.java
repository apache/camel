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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.pulsar.PulsarComponent;
import org.apache.camel.component.pulsar.utils.AutoConfiguration;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.impl.ClientBuilderImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PulsarProducerInIT extends PulsarITSupport {

    private static final String TOPIC_URI = "persistent://public/default/camel-producer-topic";
    private static final String PRODUCER = "camel-producer";

    @Produce("direct:start")
    private ProducerTemplate producerTemplate;

    @Produce("direct:start1")
    private ProducerTemplate producerTemplate1;

    @EndpointInject("pulsar:" + TOPIC_URI + "?numberOfConsumers=1&subscriptionType=Exclusive"
                    + "&subscriptionName=camel-subscription&consumerQueueSize=1"
                    + "&consumerName=camel-consumer" + "&producerName=" + PRODUCER)
    private Endpoint from;

    @EndpointInject("pulsar:" + TOPIC_URI + "1?numberOfConsumers=1&subscriptionType=Exclusive"
                    + "&subscriptionName=camel-subscription&consumerQueueSize=1"
                    + "&batchingEnabled=false" + "&chunkingEnabled=true"
                    + "&consumerName=camel-consumer" + "&producerName=" + PRODUCER)
    private Endpoint from1;

    @EndpointInject("mock:result")
    private MockEndpoint to;

    @EndpointInject("mock:result1")
    private MockEndpoint to1;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                from("direct:start").to(from);
                from(from).to(to);

                from("direct:start1").to(from1);
                from(from1).to(to1);
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
    public void testAMessageToRouteIsSentAndThenConsumed() throws Exception {
        to.expectedMessageCount(3);

        producerTemplate.sendBody("Hello ");
        producerTemplate.sendBody("World ");
        producerTemplate.sendBody(10);

        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, to);
    }

    @Test
    public void testLargeMessageWithChunkingDisabled() {
        Throwable e = assertThrows(CamelExecutionException.class,
                () -> producerTemplate.sendBody(new byte[10 * 1024 * 1024]));
        assertTrue(ExceptionUtils.getThrowableList(e).stream().anyMatch(ex -> ex instanceof PulsarClientException));
    }

    @Test
    public void testLargeMessageWithChunkingEnabled() throws Exception {
        to1.expectedMessageCount(1);
        producerTemplate1.sendBody(new byte[10 * 1024 * 1024]);
        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, to1);
    }
}
