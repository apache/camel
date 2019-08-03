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
import javax.jms.Message;
import javax.jms.Session;

import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class ActiveMQOriginalDestinationTest extends CamelTestSupport {

    protected String componentName = "activemq";

    @Test
    public void testActiveMQOriginalDestination() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("activemq:queue:foo", "Hello World");

        assertMockEndpointsSatisfied();

        // consume from bar
        Exchange out = consumer.receive("activemq:queue:bar", 5000);
        assertNotNull(out);

        // and we should have foo as the original destination
        JmsMessage msg = out.getIn(JmsMessage.class);
        Message jms = msg.getJmsMessage();
        ActiveMQMessage amq = assertIsInstanceOf(ActiveMQMessage.class, jms);
        ActiveMQDestination original = amq.getOriginalDestination();
        assertNotNull(original);
        assertEquals("foo", original.getPhysicalName());
        assertEquals("Queue", original.getDestinationTypeAsString());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent(componentName, jmsComponentAutoAcknowledge(connectionFactory));

        JmsComponent jms = camelContext.getComponent(componentName, JmsComponent.class);
        jms.setMessageCreatedStrategy(new OriginalDestinationPropagateStrategy());

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("activemq:queue:foo")
                    .to("activemq:queue:bar")
                    .to("mock:result");
            }
        };
    }

    /**
     * A strategy to enrich JMS message with their original destination if the Camel
     * route originates from a JMS destination.
     * <p/>
     * This implementation uses ActiveMQ specific code which can be moved to activemq-camel
     * when it supports Camel 2.16
     */
    private class OriginalDestinationPropagateStrategy implements MessageCreatedStrategy {

        // TODO: This is supported out of the box from ActiveMQ 5.14 onwards, and hence remove OriginalDestinationPropagateStrategy

        @Override
        public void onMessageCreated(Message message, Session session, Exchange exchange, Throwable cause) {
            if (exchange.getIn() instanceof JmsMessage) {
                JmsMessage msg = exchange.getIn(JmsMessage.class);
                Message jms = msg.getJmsMessage();
                if (message instanceof ActiveMQMessage) {
                    ActiveMQMessage amq = (ActiveMQMessage) jms;
                    ActiveMQDestination from = amq.getDestination();

                    if (from != null && message instanceof ActiveMQMessage) {
                        ((ActiveMQMessage) message).setOriginalDestination(from);
                    }
                }
            }
        }
    }
}
