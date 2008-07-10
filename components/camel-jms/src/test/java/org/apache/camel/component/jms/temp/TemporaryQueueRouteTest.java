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
package org.apache.camel.component.jms.temp;


import java.util.List;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQTempQueue;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.BrowsableQueueTest;
import org.apache.camel.component.jms.JmsQueueEndpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentClientAcknowledge;


/**
 * @version $Revision$
 */
public class TemporaryQueueRouteTest extends ContextTestSupport {
    private static final transient Log LOG = LogFactory.getLog(TemporaryQueueRouteTest.class);

    protected String endpointUri = "activemq:temp:queue:cheese";
    protected Object expectedBody = "<hello>world!</hello>";
    protected MyBean myBean = new MyBean();

    public void testSendMessage() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:result");
        endpoint.expectedBodiesReceived("Result");

        template.sendBody(endpointUri, ExchangePattern.InOut, expectedBody);

        endpoint.assertIsSatisfied();

        Message message = myBean.getMessage();
        assertNotNull("should have received a message", message);

        LOG.info("Received: " + message);
        Object header = message.getHeader("JMSDestination");
        isValidDestination(header);
    }

    protected void isValidDestination(Object header) {
        ActiveMQTempQueue destination = assertIsInstanceOf(ActiveMQTempQueue.class, header);
        LOG.info("Received message has a temporary queue: " + destination);
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        camelContext.addComponent("activemq", jmsComponentClientAcknowledge(connectionFactory));

        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(endpointUri).bean(myBean).to("mock:result");
            }
        };
    }

    public static class MyBean {
        private Message message;

        public String onMessage(Message message) {
            this.message = message;
            LOG.info("Invoked bean with: " + message);
            return "Result";
        }

        public Message getMessage() {
            return message;
        }
    }
}