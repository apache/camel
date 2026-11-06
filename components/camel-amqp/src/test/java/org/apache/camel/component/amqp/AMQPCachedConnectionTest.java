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
package org.apache.camel.component.amqp;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.artemis.services.AbstractArtemisEmbeddedService;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The producers of a component that creates the connection factory itself send over one shared connection.
 */
public class AMQPCachedConnectionTest {

    @RegisterExtension
    static ArtemisService service = ArtemisServiceFactory.createAMQPService();

    private static final int MESSAGES = 10;

    private static ActiveMQServer broker() {
        return ((AbstractArtemisEmbeddedService) service).getEmbeddedBrokerService().getActiveMQServer();
    }

    private static String brokerUrl() {
        return "amqp://localhost:" + service.brokerPort();
    }

    @Test
    public void brokerUrlSendsOverOneConnection() throws Exception {
        AMQPComponent amqp = new AMQPComponent();
        amqp.setBrokerUrl(brokerUrl());

        assertEquals(1, connectionsOpenedToSend(amqp, "cached.brokerUrl", ""));
    }

    @Test
    public void hostPortAndCredentialsSendOverOneConnection() throws Exception {
        AMQPComponent amqp = new AMQPComponent();
        amqp.setHost("localhost");
        amqp.setPort(service.brokerPort());
        amqp.setUsername("camel");
        amqp.setPassword("rider");

        assertEquals(1, connectionsOpenedToSend(amqp, "cached.hostPort", ""));
    }

    @Test
    public void factoryMethodSendsOverOneConnection() throws Exception {
        AMQPComponent amqp = AMQPComponent.amqpComponent(brokerUrl());

        assertEquals(1, connectionsOpenedToSend(amqp, "cached.factoryMethod", ""));
    }

    @Test
    public void cacheConnectionDisabledOpensOneConnectionPerMessage() throws Exception {
        AMQPComponent amqp = new AMQPComponent();
        amqp.setBrokerUrl(brokerUrl());
        amqp.setCacheConnection(false);

        assertEquals(MESSAGES, connectionsOpenedToSend(amqp, "cached.disabled", ""));
    }

    @Test
    public void connectionFactoryOfTheComponentIsUsedAsIs() throws Exception {
        AMQPComponent amqp = new AMQPComponent();
        amqp.setConnectionFactory(new JmsConnectionFactory(brokerUrl()));

        assertEquals(MESSAGES, connectionsOpenedToSend(amqp, "cached.componentFactory", ""));
    }

    @Test
    public void connectionFactoryOfTheEndpointIsUsedAsIs() throws Exception {
        AMQPComponent amqp = new AMQPComponent();
        amqp.setBrokerUrl(brokerUrl());

        assertEquals(MESSAGES, connectionsOpenedToSend(amqp, "cached.endpointFactory", "?connectionFactory=#endpointFactory"));
    }

    @Test
    public void credentialsOfTheEndpointUseTheirOwnConnections() throws Exception {
        AMQPComponent amqp = new AMQPComponent();
        amqp.setBrokerUrl(brokerUrl());

        assertEquals(MESSAGES,
                connectionsOpenedToSend(amqp, "cached.endpointCredentials", "?username=other&password=secret"));
    }

    @Test
    public void sharedConnectionIsClosedWhenTheComponentStops() throws Exception {
        AMQPComponent amqp = new AMQPComponent();
        amqp.setBrokerUrl(brokerUrl());
        CamelContext context = new DefaultCamelContext();
        context.addComponent("amqp", amqp);
        context.start();
        try {
            context.createProducerTemplate().sendBody("amqp:queue:cached.stop", "Hello");
            assertEquals(1, broker().getConnectionCount(), "the shared connection stays open after the send");
        } finally {
            context.stop();
        }
        await().atMost(10, TimeUnit.SECONDS).until(() -> broker().getConnectionCount() == 0);
    }

    @Test
    public void sendingWorksAgainAfterABrokerRestart() throws Exception {
        AMQPComponent amqp = new AMQPComponent();
        amqp.setBrokerUrl(brokerUrl());
        CamelContext context = new DefaultCamelContext();
        context.addComponent("amqp", amqp);
        context.start();
        try {
            ProducerTemplate template = context.createProducerTemplate();
            template.sendBody("amqp:queue:cached.restart", "before");

            service.restart();

            // the broker drops the shared connection: the next send opens a new one
            await().atMost(30, TimeUnit.SECONDS).ignoreExceptions().until(() -> {
                template.sendBody("amqp:queue:cached.restart", "after");
                return true;
            });
            assertEquals(1, service.countMessages("cached.restart"));
        } finally {
            context.stop();
        }
    }

    @Test
    public void pollingConsumerClosesItsConsumerAfterTheReceive() throws Exception {
        AMQPComponent amqp = new AMQPComponent();
        amqp.setBrokerUrl(brokerUrl());
        CamelContext context = new DefaultCamelContext();
        context.addComponent("amqp", amqp);
        context.start();
        try {
            context.createProducerTemplate().sendBody("amqp:queue:cached.poll", "Hello");

            assertEquals("Hello",
                    context.createConsumerTemplate().receiveBody("amqp:queue:cached.poll", 5000, String.class));
            // a consumer left open would take the next messages of the queue ahead
            assertEquals(0, broker().locateQueue("cached.poll").getConsumerCount());
        } finally {
            context.stop();
        }
    }

    @Test
    public void sendFromATransactedConsumerIsRolledBackWithIt() throws Exception {
        AMQPComponent amqp = new AMQPComponent();
        amqp.setBrokerUrl(brokerUrl());
        AtomicInteger deliveries = new AtomicInteger();
        CamelContext context = new DefaultCamelContext();
        context.addComponent("amqp", amqp);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("amqp:queue:cached.tx.in?transacted=true")
                        .to("amqp:queue:cached.tx.out")
                        .process(exchange -> {
                            if (deliveries.incrementAndGet() == 1) {
                                throw new IllegalStateException("Forced failure of the first delivery");
                            }
                        });

                from("amqp:queue:cached.tx.out").to("mock:out");
            }
        });
        context.start();
        try {
            MockEndpoint out = context.getEndpoint("mock:out", MockEndpoint.class);
            out.expectedBodiesReceived("Hello");
            out.setAssertPeriod(1000);

            context.createProducerTemplate().sendBody("amqp:queue:cached.tx.in", "Hello");

            // the message sent by the failed delivery is rolled back, only the one of the redelivery is sent
            MockEndpoint.assertIsSatisfied(context);
            assertEquals(2, deliveries.get());
        } finally {
            context.stop();
        }
    }

    private static long connectionsOpenedToSend(AMQPComponent amqp, String queue, String endpointOptions)
            throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.getRegistry().bind("endpointFactory", new JmsConnectionFactory(brokerUrl()));
        context.addComponent("amqp", amqp);
        context.start();
        try {
            ProducerTemplate template = context.createProducerTemplate();
            long before = broker().getTotalConnectionCount();
            for (int i = 0; i < MESSAGES; i++) {
                template.sendBody("amqp:queue:" + queue + endpointOptions, "Hello " + i);
            }
            long opened = broker().getTotalConnectionCount() - before;
            assertEquals(MESSAGES, service.countMessages(queue));
            return opened;
        } finally {
            context.stop();
        }
    }
}
