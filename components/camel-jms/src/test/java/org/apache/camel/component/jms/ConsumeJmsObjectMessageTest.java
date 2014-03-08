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

import java.io.Serializable;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.Session;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.ExchangeHelper;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * @version 
 */
public class ConsumeJmsObjectMessageTest extends CamelTestSupport {
    protected JmsTemplate jmsTemplate;
    private MockEndpoint endpoint;

    @Test
    public void testConsumeObjectMessage() throws Exception {
        endpoint.expectedMessageCount(1);

        jmsTemplate.setPubSubDomain(false);
        jmsTemplate.send("test.object", new MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                ObjectMessage msg = session.createObjectMessage();

                MyUser user = new MyUser();
                user.setName("Claus");
                msg.setObject(user);

                return msg;
            }
        });

        endpoint.assertIsSatisfied();
        assertCorrectObjectReceived();
    }

    @Test
    public void testSendBytesMessage() throws Exception {
        endpoint.expectedMessageCount(1);

        MyUser user = new MyUser();
        user.setName("Claus");
        template.sendBody("direct:test", user);

        endpoint.assertIsSatisfied();
        assertCorrectObjectReceived();
    }

    protected void assertCorrectObjectReceived() {
        Exchange exchange = endpoint.getReceivedExchanges().get(0);
        // This should be a JMS Exchange
        assertNotNull(ExchangeHelper.getBinding(exchange, JmsBinding.class));
        JmsMessage in = (JmsMessage) exchange.getIn();
        assertNotNull(in);
        assertIsInstanceOf(ObjectMessage.class, in.getJmsMessage());

        MyUser user = exchange.getIn().getBody(MyUser.class);
        assertEquals("Claus", user.getName());
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        endpoint = getMockEndpoint("mock:result");
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        jmsTemplate = new JmsTemplate(connectionFactory);
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("activemq:test.object").to("mock:result");
                from("direct:test").to("activemq:test.object");
            }
        };
    }

    public static class MyUser implements Serializable {

        private static final long serialVersionUID = 1L;
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}