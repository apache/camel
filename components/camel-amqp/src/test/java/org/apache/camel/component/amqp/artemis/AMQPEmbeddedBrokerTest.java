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
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.amqp.AMQPComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.amqp.AMQPConnectionDetails.AMQP_PORT;
import static org.apache.camel.component.amqp.AMQPConnectionDetails.AMQP_SET_TOPIC_PREFIX;
import static org.apache.camel.component.amqp.AMQPConnectionDetails.discoverAMQP;

public class AMQPEmbeddedBrokerTest extends CamelTestSupport {

    @RegisterExtension
    static ArtemisService service = ArtemisServiceFactory.createSingletonAMQPService();

    @EndpointInject("mock:result")
    MockEndpoint resultEndpoint;

    String expectedBody = "Hello there!";

    @BeforeAll
    public static void beforeClass() throws Exception {
        System.setProperty(AMQP_PORT, service.brokerPort() + "");
        System.setProperty(AMQP_SET_TOPIC_PREFIX, "false");
    }

    @BeforeEach
    void prepareTest() {
        resultEndpoint.expectedMessageCount(1);
        template.sendBody("direct:send-topic", expectedBody);
    }

    @Test
    public void testTopicWithoutPrefix() throws Exception {
        resultEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.getRegistry().bind("amqpConnection", discoverAMQP(camelContext));
        camelContext.addComponent("amqp-customized", new AMQPComponent());
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
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
