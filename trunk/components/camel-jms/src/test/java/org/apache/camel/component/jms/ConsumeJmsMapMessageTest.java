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

import java.util.HashMap;
import java.util.Map;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
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
public class ConsumeJmsMapMessageTest extends CamelTestSupport {
    protected JmsTemplate jmsTemplate;
    private MockEndpoint endpoint;

    @Test
    public void testConsumeMapMessage() throws Exception {
        endpoint.expectedMessageCount(1);

        jmsTemplate.setPubSubDomain(false);
        jmsTemplate.send("test.map", new MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                MapMessage mapMessage = session.createMapMessage();
                mapMessage.setString("foo", "abc");
                mapMessage.setString("bar", "xyz");
                return mapMessage;
            }
        });

        endpoint.assertIsSatisfied();
        assertCorrectMapReceived();
    }

    protected void assertCorrectMapReceived() {
        Exchange exchange = endpoint.getReceivedExchanges().get(0);
        // This should be a JMS Exchange
        assertNotNull(ExchangeHelper.getBinding(exchange, JmsBinding.class));
        JmsMessage in = (JmsMessage) exchange.getIn();
        assertNotNull(in);
        
        Map<?, ?> map = exchange.getIn().getBody(Map.class);
        log.info("Received map: " + map);

        assertNotNull("Should have received a map message!", map);
        assertIsInstanceOf(MapMessage.class, in.getJmsMessage());
        assertEquals("map.foo", "abc", map.get("foo"));
        assertEquals("map.bar", "xyz", map.get("bar"));
        assertEquals("map.size", 2, map.size());
    }

    @Test
    public void testSendMapMessage() throws Exception {

        endpoint.expectedMessageCount(1);

        Map<String, String> map = new HashMap<String, String>();
        map.put("foo", "abc");
        map.put("bar", "xyz");

        template.sendBody("direct:test", map);

        endpoint.assertIsSatisfied();
        assertCorrectMapReceived();
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
                from("activemq:test.map").to("mock:result");
                from("direct:test").to("activemq:test.map");
            }
        };
    }
}
