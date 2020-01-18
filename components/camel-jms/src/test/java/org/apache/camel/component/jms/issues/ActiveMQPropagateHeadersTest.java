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
package org.apache.camel.component.jms.issues;

import java.util.Date;
import java.util.List;

import javax.jms.ConnectionFactory;

import org.apache.activemq.command.ActiveMQQueue;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.CamelJmsTestHelper;
import org.apache.camel.component.mock.AssertionClause;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class ActiveMQPropagateHeadersTest extends CamelTestSupport {

    protected Object expectedBody = "<time>" + new Date() + "</time>";
    protected ActiveMQQueue replyQueue = new ActiveMQQueue("test.reply.queue");
    protected String correlationID = "ABC-123";
    protected String messageType = getClass().getName();

    @Test
    public void testForwardingAMessageAcrossJMSKeepingCustomJMSHeaders() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);

        resultEndpoint.expectedBodiesReceived(expectedBody);
        AssertionClause firstMessageExpectations = resultEndpoint.message(0);
        firstMessageExpectations.header("cheese").isEqualTo(123);
        firstMessageExpectations.header("JMSReplyTo").isEqualTo(replyQueue);
        firstMessageExpectations.header("JMSCorrelationID").isEqualTo(correlationID);
        firstMessageExpectations.header("JMSType").isEqualTo(messageType);

        template.sendBodyAndHeader("activemq:test.a", expectedBody, "cheese", 123);

        resultEndpoint.assertIsSatisfied();

        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        String replyTo = exchange.getIn().getHeader("JMSReplyTo", String.class);        
        assertEquals("ReplyTo", replyQueue.toString(), replyTo);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        // START SNIPPET: example
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));
        // END SNIPPET: example

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // must set option to preserve message QoS as we send an InOnly but put a JMSReplyTo
// that does not work well on the consumer side, as it would assume it should send a reply
// but we do not expect a reply as we are InOnly.
                from("activemq:test.a").process(exchange -> {
                    // set the JMS headers
                    Message in = exchange.getIn();
                    in.setHeader("JMSReplyTo", replyQueue);
                    in.setHeader("JMSCorrelationID", correlationID);
                    in.setHeader("JMSType", messageType);
                }).to("activemq:test.b?preserveMessageQos=true");

                from("activemq:test.b").to("mock:result");
            }
        };
    }
}
