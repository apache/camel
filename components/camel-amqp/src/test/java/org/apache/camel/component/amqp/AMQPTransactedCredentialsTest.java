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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A message sent from a transacted route takes part in the transaction of the consumer also when the component has a
 * username and password.
 */
public class AMQPTransactedCredentialsTest {

    @RegisterExtension
    static ArtemisService service = ArtemisServiceFactory.createAMQPService();

    @Test
    public void sendIsRolledBackWithoutCredentials() throws Exception {
        assertSendIsRolledBack(false, "noCredentials", "");
    }

    @Test
    public void sendIsRolledBackWithCredentialsOnTheComponent() throws Exception {
        assertSendIsRolledBack(true, "componentCredentials", "");
    }

    @Test
    public void sendIsRolledBackWithTheSameCredentialsOnTheEndpoints() throws Exception {
        assertSendIsRolledBack(false, "endpointCredentials", "username=camel&password=rider");
    }

    private static void assertSendIsRolledBack(boolean credentials, String name, String endpointOptions)
            throws Exception {
        AMQPComponent amqp = new AMQPComponent();
        amqp.setBrokerUrl("amqp://localhost:" + service.brokerPort());
        if (credentials) {
            amqp.setUsername("camel");
            amqp.setPassword("rider");
        }
        AtomicInteger deliveries = new AtomicInteger();
        CamelContext context = new DefaultCamelContext();
        context.addComponent("amqp", amqp);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("amqp:queue:credentials." + name + ".in?transacted=true"
                     + (endpointOptions.isEmpty() ? "" : "&" + endpointOptions))
                        .to("amqp:queue:credentials." + name + ".out"
                            + (endpointOptions.isEmpty() ? "" : "?" + endpointOptions))
                        .process(exchange -> {
                            if (deliveries.incrementAndGet() == 1) {
                                throw new IllegalStateException("Forced failure of the first delivery");
                            }
                        });

                from("amqp:queue:credentials." + name + ".out").to("mock:out");
            }
        });
        context.start();
        try {
            MockEndpoint out = context.getEndpoint("mock:out", MockEndpoint.class);
            out.expectedBodiesReceived("Hello");
            out.setAssertPeriod(1000);

            context.createProducerTemplate().sendBody("amqp:queue:credentials." + name + ".in", "Hello");

            // the message sent by the failed delivery is rolled back, only the one of the redelivery is sent
            MockEndpoint.assertIsSatisfied(context);
            assertEquals(2, deliveries.get());
        } finally {
            context.stop();
        }
    }
}
