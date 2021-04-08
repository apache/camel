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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.activemq.services.ActiveMQEmbeddedService;
import org.apache.camel.test.infra.activemq.services.ActiveMQEmbeddedServiceBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.amqp.AMQPConnectionDetails.AMQP_PORT;
import static org.apache.camel.component.amqp.AMQPConnectionDetails.discoverAMQP;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AMQPToDSendDynamicTest extends CamelTestSupport {

    static int amqpPort = AvailablePortFinder.getNextAvailable();

    @RegisterExtension
    public static ActiveMQEmbeddedService service = ActiveMQEmbeddedServiceBuilder
            .defaultBroker()
            .withAmqpTransport(amqpPort)
            .build();

    @BeforeAll
    public static void beforeClass() throws Exception {
        System.setProperty(AMQP_PORT, amqpPort + "");
    }

    @Test
    public void testToD() throws Exception {
        template.sendBodyAndHeader("direct:start", "Hello bar", "where", "bar");
        template.sendBodyAndHeader("direct:start", "Hello beer", "where", "beer");

        // there should only be one amqp endpoint
        long count = context.getEndpoints().stream().filter(e -> e.getEndpointUri().startsWith("amqp:")).count();
        assertEquals(1, count, "There should only be 1 amqp endpoint");

        // and the messages should be in the queues
        String out = consumer.receiveBody("amqp:queue:bar", 2000, String.class);
        assertEquals("Hello bar", out);
        out = consumer.receiveBody("amqp:queue:beer", 2000, String.class);
        assertEquals("Hello beer", out);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.getRegistry().bind("amqpConnection", discoverAMQP(camelContext));
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // route message dynamic using toD
                from("direct:start").toD("amqp:queue:${header.where}");
            }
        };
    }

}
