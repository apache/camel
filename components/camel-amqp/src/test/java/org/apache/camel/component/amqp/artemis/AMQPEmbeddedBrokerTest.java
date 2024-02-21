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
package org.apache.camel.component.amqp.artemis;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.amqp.AMQPComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.amqp.AMQPConnectionDetails.AMQP_PORT;
import static org.apache.camel.component.amqp.AMQPConnectionDetails.AMQP_SET_TOPIC_PREFIX;
import static org.apache.camel.component.amqp.AMQPConnectionDetails.discoverAMQP;

public class AMQPEmbeddedBrokerTest {

    @Order(1)
    @RegisterExtension
    public static ArtemisService service = ArtemisServiceFactory.createSingletonAMQPService();

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    private MockEndpoint resultEndpoint;

    private final String expectedBody = "Hello there!";

    @BeforeEach
    void prepareTest() {
        resultEndpoint = contextExtension.getMockEndpoint("mock:result");

        resultEndpoint.expectedMessageCount(1);
        contextExtension.getProducerTemplate().sendBody("direct:send-topic", expectedBody);
    }

    @Test
    public void testTopicWithoutPrefix() throws Exception {
        resultEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS);
    }

    @ContextFixture
    public static void setupRoutes(CamelContext context) {
        System.setProperty(AMQP_PORT, String.valueOf(service.brokerPort()));
        System.setProperty(AMQP_SET_TOPIC_PREFIX, "false");

        context.getRegistry().bind("amqpConnection", discoverAMQP(context));
        context.addComponent("amqp-customized", new AMQPComponent());
    }

    @RouteFixture
    public static void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(createRouteBuilder());
    }

    private static RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:send-topic")
                        .to("amqp-customized:topic:topic.ping");

                from("amqp-customized:topic:topic.ping")
                        .to("log:routing")
                        .to("mock:result");
            }
        };
    }
}
