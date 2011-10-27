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
package org.apache.camel.component.jms;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 *
 */
public class FromTextToBytesMessageTest extends CamelTestSupport {

    @Test
    public void testTextToBytes() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:bar");
        mock.expectedMessageCount(1);

        template.sendBody("activemq:queue:foo", "3");

        assertMockEndpointsSatisfied();

        javax.jms.Message msg = mock.getReceivedExchanges().get(0).getIn(JmsMessage.class).getJmsMessage();
        assertNotNull(msg);
        assertIsInstanceOf(javax.jms.BytesMessage.class, msg);
    }

    @Test
    public void testTextToBytesHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:bar");
        mock.expectedMessageCount(1);

        template.sendBody("activemq:queue:header", "3");

        assertMockEndpointsSatisfied();

        javax.jms.Message msg = mock.getReceivedExchanges().get(0).getIn(JmsMessage.class).getJmsMessage();
        assertNotNull(msg);
        assertIsInstanceOf(javax.jms.BytesMessage.class, msg);
    }

    @Test
    public void testTextToText() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:bar");
        mock.expectedMessageCount(1);

        template.sendBody("activemq:queue:text", "Hello");

        assertMockEndpointsSatisfied();

        javax.jms.Message msg = mock.getReceivedExchanges().get(0).getIn(JmsMessage.class).getJmsMessage();
        assertNotNull(msg);
        assertIsInstanceOf(javax.jms.TextMessage.class, msg);
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("activemq:queue:foo?jmsMessageType=Text")
                    .to("activemq:queue:bar?jmsMessageType=Bytes");

                from("activemq:queue:header?jmsMessageType=Text")
                    .setHeader("myHeader", constant("123"))
                    .to("activemq:queue:bar?jmsMessageType=Bytes");

                from("activemq:queue:text?jmsMessageType=Text")
                    .to("activemq:queue:bar?jmsMessageType=Text");

                from("activemq:queue:bar")
                    .to("mock:bar");
            }
        };
    }
}
