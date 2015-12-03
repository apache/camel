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
package org.apache.camel.component.amqp;

import org.apache.activemq.broker.BrokerService;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.impl.PropertyPlaceholderDelegateRegistry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.apache.camel.component.amqp.AMQPComponent.amqpComponent;
import static org.apache.camel.component.amqp.AMQPConnectionDetails.AMQP_PORT;
import static org.apache.camel.component.amqp.AMQPConnectionDetails.discoverAMQP;

public class AMQPRouteTest extends CamelTestSupport {

    static int amqpPort = AvailablePortFinder.getNextAvailable();

    static BrokerService broker;

    @EndpointInject(uri = "mock:result")
    MockEndpoint resultEndpoint;

    String expectedBody = "Hello there!";

    @BeforeClass
    public static void beforeClass() throws Exception {
        broker = new BrokerService();
        broker.setPersistent(false);
        broker.addConnector("amqp://0.0.0.0:" + amqpPort);
        broker.start();

        System.setProperty(AMQP_PORT, amqpPort + "");
    }

    @AfterClass
    public static void afterClass() throws Exception {
        broker.stop();
    }

    @Test
    public void testJmsQueue() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.message(0).header("cheese").isEqualTo(123);
        template.sendBodyAndHeader("amqp-customized:queue:ping", expectedBody, "cheese", 123);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testRequestReply() {
        String response = template.requestBody("amqp-customized:queue:inOut", expectedBody, String.class);
        assertEquals("response", response);
    }

    @Test
    public void testJmsTopic() throws Exception {
        resultEndpoint.expectedMessageCount(2);
        resultEndpoint.message(0).header("cheese").isEqualTo(123);
        template.sendBodyAndHeader("amqp-customized:topic:ping", expectedBody, "cheese", 123);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testPrefixWildcard() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        template.sendBody("amqp-customized:wildcard.foo.bar", expectedBody);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testIncludeDestination() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.message(0).header("JMSDestination").isEqualTo("ping");
        template.sendBody("amqp-customized:queue:ping", expectedBody);
        resultEndpoint.assertIsSatisfied();
    }

    // Routes fixtures


    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        return registry;
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        JndiRegistry registry = (JndiRegistry)((PropertyPlaceholderDelegateRegistry)camelContext.getRegistry()).getRegistry();
        registry.bind("amqpConnection", discoverAMQP(camelContext));
        camelContext.addComponent("amqp-customized", amqpComponent("amqp://localhost:" + amqpPort));
        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("amqp-customized:queue:ping")
                    .to("log:routing")
                    .to("mock:result");

                from("amqp-customized:queue:inOut")
                        .setBody().constant("response");

                from("amqp-customized:topic:ping")
                        .to("log:routing")
                        .to("mock:result");

                from("amqp-customized:topic:ping")
                        .to("log:routing")
                        .to("mock:result");

                from("amqp-customized:queue:wildcard.>")
                        .to("log:routing")
                        .to("mock:result");

                from("amqp:queue:uriEndpoint")
                        .to("log:routing")
                        .to("mock:result");
            }
        };
    }

}
