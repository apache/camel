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
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.amqp.AMQPConnectionDetails.discoverAMQP;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AMQPToDSendDynamicTest extends AMQPTestSupport {
    private ProducerTemplate template;
    private ConsumerTemplate consumer;

    @Test
    public void testToD() {
        template.sendBodyAndHeader("direct:start", "Hello bar", "where", "bar");
        template.sendBodyAndHeader("direct:start", "Hello beer", "where", "beer");

        // there should only be one amqp endpoint
        long count = contextExtension.getContext().getEndpoints().stream().filter(e -> e.getEndpointUri().startsWith("amqp:"))
                .count();
        assertEquals(1, count, "There should only be 1 amqp endpoint");

        // and the messages should be in the queues
        String out = consumer.receiveBody("amqp:queue:bar", 2000, String.class);
        assertEquals("Hello bar", out);
        out = consumer.receiveBody("amqp:queue:beer", 2000, String.class);
        assertEquals("Hello beer", out);
    }

    @BeforeAll
    static void startContext() {
        System.setProperty(AMQPConnectionDetails.AMQP_PORT, String.valueOf(service.brokerPort()));
    }

    @BeforeEach
    void setupTemplate() {
        template = contextExtension.getProducerTemplate();
        consumer = contextExtension.getConsumerTemplate();
    }

    @ContextFixture
    public void configureContext(CamelContext camelContext) {
        System.setProperty(AMQPConnectionDetails.AMQP_PORT, String.valueOf(service.brokerPort()));

        camelContext.getRegistry().bind("amqpConnection", discoverAMQP(camelContext));
    }

    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(createRouteBuilder());
    }

    private RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // route message dynamic using toD
                from("direct:start").toD("amqp:queue:${header.where}");
            }
        };
    }
}
