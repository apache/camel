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
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class JmsRouteWithInOnlyAndMultipleAcksTest extends CamelTestSupport {

    protected String componentName = "amq";
    
    @Test
    public void testSendOrderWithMultipleAcks() throws Exception {
        MockEndpoint inbox = getMockEndpoint("mock:inbox");
        inbox.expectedBodiesReceived("Camel in Action");

        String orderId = "1";
        MockEndpoint notifCollector = getMockEndpoint("mock:orderNotificationAckCollector");
        notifCollector.expectedMessageCount(2);
        notifCollector.expectedHeaderReceived("JMSCorrelationID", orderId);
        notifCollector.setResultWaitTime(10000);
        
        Object out = template.requestBodyAndHeader("amq:queue:inbox", "Camel in Action", "JMSCorrelationID", orderId);
        assertEquals("OK: Camel in Action", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("orderService", new MyOrderServiceBean());
        jndi.bind("orderServiceNotificationWithAck-1", new MyOrderServiceNotificationWithAckBean("1"));
        jndi.bind("orderServiceNotificationWithAck-2", new MyOrderServiceNotificationWithAckBean("2"));
        return jndi;
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent(componentName, jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // this route picks up an order request
                // send out a one way notification to multiple
                // topic subscribers, lets a bean handle
                // the order and then delivers a reply back to
                // the original order request initiator
                from("amq:queue:inbox")
                    .to("mock:inbox")
                    .inOnly("amq:topic:orderServiceNotification")
                    .bean("orderService", "handleOrder");

                // this route collects an order request notification
                // and sends back an acknowledgment back to a queue
                from("amq:topic:orderServiceNotification")
                    .bean("orderServiceNotificationWithAck-1", "handleOrderNotificationWithAck")
                    .to("amq:queue:orderServiceNotificationAck");
                
                // this route collects an order request notification
                // and sends back an acknowledgment back to a queue
                from("amq:topic:orderServiceNotification")
                    .bean("orderServiceNotificationWithAck-2", "handleOrderNotificationWithAck")
                    .to("amq:queue:orderServiceNotificationAck");

                // this route collects all order notifications acknowledgments
                from("amq:queue:orderServiceNotificationAck")
                    .to("mock:orderNotificationAckCollector");
            }
        };
    }

    public static class MyOrderServiceBean {
        public String handleOrder(String body) {
            return "OK: " + body;
        }
    }

    public static class MyOrderServiceNotificationWithAckBean {
        private String id;

        public MyOrderServiceNotificationWithAckBean(String id) {
            this.id = id;
        }
        public String handleOrderNotificationWithAck(String body) {
            return "Ack-" + id + ":" + body;
        }
    }
}
