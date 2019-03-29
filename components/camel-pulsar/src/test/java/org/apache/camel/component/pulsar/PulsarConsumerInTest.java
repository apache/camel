/**
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

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.pulsar.utils.AutoConfiguration;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.impl.ClientBuilderImpl;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Ignore //TODO use TestContainers to spin up local pulsar broker
public class PulsarConsumerInTest extends CamelTestSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(PulsarConsumerInTest.class);
    private static final String PULSAR_CLUSTER_URL = "pulsar://localhost:6650";

    private static final String TOPIC_URI = "persistent://public/default/camel-topic";
    private static final String PRODUCER = "camel-producer";

    @EndpointInject(uri = "pulsar:" + TOPIC_URI
        + "?numberOfConsumers=1&subscriptionType=Exclusive"
        + "&subscriptionName=camel-subscription&consumerQueueSize=1&consumerName=camel-consumer"
    )
    private Endpoint from;

    @EndpointInject(uri = "mock:result")
    private MockEndpoint to;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            Processor processor = new Processor() {
                @Override
                public void process(final Exchange exchange) {
                    LOGGER.info("Processing message {}", exchange.getIn().getBody());
                }
            };

            @Override
            public void configure() {
                from(from).to(to).unmarshal().string().process(processor);
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
        jndi.bind("pulsar", new PulsarComponent(context(), autoConfiguration, pulsarClient));
    }

    private PulsarClient givenPulsarClient() throws PulsarClientException {
        return new ClientBuilderImpl()
            .serviceUrl(PULSAR_CLUSTER_URL)
            .ioThreads(1)
            .listenerThreads(1)
            .build();
    }

    @Test
    public void givenARunningPulsarCluster_whenIPublishAMessageToCluster_verifyInMessageIsConsumed() throws Exception {
        to.expectedMessageCount(1);

        Producer<String> producer = givenPulsarClient().newProducer(Schema.STRING)
            .producerName(PRODUCER)
            .topic(TOPIC_URI)
            .create();

        producer.send("Hello World!");

        to.assertIsSatisfied();
    }
}
