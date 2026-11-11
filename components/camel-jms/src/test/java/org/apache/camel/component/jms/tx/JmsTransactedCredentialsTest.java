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
package org.apache.camel.component.jms.tx;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.jms.Connection;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.artemis.services.AbstractArtemisEmbeddedService;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A message sent from a transacted route takes part in the transaction of the consumer also when the endpoints use a
 * username and password.
 */
public class JmsTransactedCredentialsTest {

    @RegisterExtension
    static ArtemisService service = ArtemisServiceFactory.createVMService();

    @Test
    public void sendIsRolledBackWithoutCredentials() throws Exception {
        assertSendIsRolledBack(jmsComponent(false), "noCredentials", "");
    }

    @Test
    public void sendIsRolledBackWithCredentialsOnTheComponent() throws Exception {
        assertSendIsRolledBack(jmsComponent(true), "componentCredentials", "");
    }

    @Test
    public void sendIsRolledBackWithTheSameCredentialsOnTheEndpoints() throws Exception {
        assertSendIsRolledBack(jmsComponent(false), "endpointCredentials", "username=camel&password=rider");
    }

    @Test
    public void sendFromTheConsumerUsesTheSessionOfTheListenerWithCredentials() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addComponent("jms", jmsComponent(true));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:queue:credentials.session.in").to("jms:queue:credentials.session.out");
                from("jms:queue:credentials.session.out").to("mock:out");
            }
        });
        context.start();
        ActiveMQServer broker = ((AbstractArtemisEmbeddedService) service).getEmbeddedBrokerService().getActiveMQServer();
        try (Connection connection = new ActiveMQConnectionFactory(service.serviceAddress()).createConnection()) {
            MockEndpoint out = context.getEndpoint("mock:out", MockEndpoint.class);
            out.expectedMessageCount(5);
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(session.createQueue("credentials.session.in"));
            long before = broker.getTotalConnectionCount();

            for (int i = 0; i < 5; i++) {
                producer.send(session.createTextMessage("Hello " + i));
            }

            MockEndpoint.assertIsSatisfied(context);
            // the consumer sends on the session of its listener, instead of opening a connection per message
            assertEquals(0, broker.getTotalConnectionCount() - before);
        } finally {
            context.stop();
        }
    }

    private static JmsComponent jmsComponent(boolean credentials) {
        JmsComponent jms = new JmsComponent();
        jms.setConnectionFactory(new ActiveMQConnectionFactory(service.serviceAddress()));
        if (credentials) {
            jms.setUsername("camel");
            jms.setPassword("rider");
        }
        return jms;
    }

    private static void assertSendIsRolledBack(JmsComponent jms, String name, String endpointOptions) throws Exception {
        AtomicInteger deliveries = new AtomicInteger();
        CamelContext context = new DefaultCamelContext();
        context.addComponent("jms", jms);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:queue:credentials." + name + ".in?transacted=true"
                     + (endpointOptions.isEmpty() ? "" : "&" + endpointOptions))
                        .to("jms:queue:credentials." + name + ".out" + (endpointOptions.isEmpty() ? "" : "?" + endpointOptions))
                        .process(exchange -> {
                            if (deliveries.incrementAndGet() == 1) {
                                throw new IllegalStateException("Forced failure of the first delivery");
                            }
                        });

                from("jms:queue:credentials." + name + ".out").to("mock:out");
            }
        });
        context.start();
        try {
            MockEndpoint out = context.getEndpoint("mock:out", MockEndpoint.class);
            out.expectedBodiesReceived("Hello");
            out.setAssertPeriod(1000);

            context.createProducerTemplate().sendBody("jms:queue:credentials." + name + ".in", "Hello");

            // the message sent by the failed delivery is rolled back, only the one of the redelivery is sent
            MockEndpoint.assertIsSatisfied(context);
            assertEquals(2, deliveries.get());
        } finally {
            context.stop();
        }
    }
}
