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

import java.util.Arrays;

import javax.jms.BytesMessage;
import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jms.core.JmsTemplate;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class ConsumeJmsBytesMessageTest extends CamelTestSupport {
    protected JmsTemplate jmsTemplate;
    private MockEndpoint endpoint;

    @Test
    public void testConsumeBytesMessage() throws Exception {
        endpoint.expectedMessageCount(1);

        jmsTemplate.setPubSubDomain(false);
        jmsTemplate.send("test.bytes", session -> {
            BytesMessage bytesMessage = session.createBytesMessage();
            bytesMessage.writeByte((byte) 1);
            bytesMessage.writeByte((byte) 2);
            bytesMessage.writeByte((byte) 3);
            return bytesMessage;
        });

        endpoint.assertIsSatisfied();
        assertCorrectBytesReceived();
    }

    @Test
    public void testSendBytesMessage() throws Exception {

        endpoint.expectedMessageCount(1);

        byte[] bytes = new byte[] {1, 2, 3};

        template.sendBody("direct:test", bytes);

        endpoint.assertIsSatisfied();
        assertCorrectBytesReceived();
    }

    protected void assertCorrectBytesReceived() {
        Exchange exchange = endpoint.getReceivedExchanges().get(0);
        // This should be a JMS Exchange
        assertNotNull(ExchangeHelper.getBinding(exchange, JmsBinding.class));
        JmsMessage in = (JmsMessage) exchange.getIn();
        assertNotNull(in);
        
        byte[] bytes = exchange.getIn().getBody(byte[].class);
        log.info("Received bytes: " + Arrays.toString(bytes));

        assertNotNull("Should have received a bytes message!", bytes);
        assertIsInstanceOf(BytesMessage.class, in.getJmsMessage());
        assertEquals("Wrong byte 1", 1, bytes[0]);
        assertEquals("Wrong payload lentght", 3, bytes.length);
    }


    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        endpoint = getMockEndpoint("mock:result");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        jmsTemplate = new JmsTemplate(connectionFactory);
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("activemq:test.bytes").to("mock:result");
                from("direct:test").to("activemq:test.bytes");
            }
        };
    }
}
