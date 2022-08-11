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
package org.apache.camel.component.jms;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class JmsInOnlyWithReplyToTest extends AbstractJMSTest {

    @Test
    public void testSendInOnlyWithReplyTo() throws Exception {
        getMockEndpoint("mock:JmsInOnlyWithReplyToTest.foo").expectedBodiesReceived("World");
        getMockEndpoint("mock:JmsInOnlyWithReplyToTest.bar").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:done").expectedBodiesReceived("World");

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory
                = org.apache.camel.test.infra.activemq.common.ConnectionFactoryHelper.createConnectionFactory(service);
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("activemq:queue:JmsInOnlyWithReplyToTest.foo?replyTo=queue:JmsInOnlyWithReplyToTest.bar&preserveMessageQos=true")
                        .to("mock:done");

                from("activemq:queue:JmsInOnlyWithReplyToTest.foo")
                        .to("log:JmsInOnlyWithReplyToTest.foo?showAll=true", "mock:JmsInOnlyWithReplyToTest.foo")
                        .transform(body().prepend("Bye "));

                // we should disable reply to to avoid sending the message back to our self
                // after we have consumed it
                from("activemq:queue:JmsInOnlyWithReplyToTest.bar?disableReplyTo=true")
                        .to("log:JmsInOnlyWithReplyToTest.bar?showAll=true", "mock:JmsInOnlyWithReplyToTest.bar");
            }
        };
    }
}
