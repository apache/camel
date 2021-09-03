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
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.activemq.services.ActiveMQEmbeddedService;
import org.apache.camel.test.infra.activemq.services.ActiveMQEmbeddedServiceBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.amqp.AMQPComponent.amqpComponent;
import static org.apache.camel.component.amqp.AMQPConnectionDetails.AMQP_PORT;

public class AMQPRouteTraceFrameTest extends CamelTestSupport {

    static int amqpPort = AvailablePortFinder.getNextAvailable();

    @RegisterExtension
    public static ActiveMQEmbeddedService service = ActiveMQEmbeddedServiceBuilder
            .defaultBroker()
            .withAmqpTransport(amqpPort)
            .build();

    @EndpointInject("mock:result")
    MockEndpoint resultEndpoint;

    String expectedBody = "Hello there!";

    @BeforeAll
    public static void beforeClass() throws Exception {
        System.setProperty(AMQP_PORT, amqpPort + "");
    }

    @Test
    public void testTraceFrame() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.message(0).header("cheese").isEqualTo(123);
        template.sendBodyAndHeader("amqp-customized:queue:ping", expectedBody, "cheese", 123);
        resultEndpoint.assertIsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        JmsConnectionFactory connectionFactory
                = new JmsConnectionFactory(service.serviceAddress() + "?amqp.traceFrames=true");

        AMQPComponent amqp = amqpComponent(service.serviceAddress());
        amqp.getConfiguration().setConnectionFactory(connectionFactory);

        camelContext.addComponent("amqp-customized", amqp);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("amqp-customized:queue:ping")
                        .to("log:routing")
                        .to("mock:result");
            }
        };
    }

}
