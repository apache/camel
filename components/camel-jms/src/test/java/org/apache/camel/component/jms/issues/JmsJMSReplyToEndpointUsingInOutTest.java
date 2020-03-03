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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.TextMessage;

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.CamelJmsTestHelper;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.springframework.jms.core.JmsTemplate;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * Unit test using a fixed replyTo specified on the JMS endpoint
 */
public class JmsJMSReplyToEndpointUsingInOutTest extends CamelTestSupport {
    private JmsComponent amq;

    @Test
    public void testCustomJMSReplyToInOut() throws Exception {

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("My name is Arnio");

        // do not use Camel to send and receive to simulate a non Camel client

        // use another thread to listen and send the reply
        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.submit(() -> {
            JmsTemplate jms = new JmsTemplate(amq.getConfiguration().getConnectionFactory());

            final TextMessage msg = (TextMessage) jms.receive("nameRequestor");
            assertEquals("What's your name", msg.getText());

            // there should be a JMSReplyTo so we know where to send the reply
            final Destination replyTo = msg.getJMSReplyTo();

            // send reply
            jms.send(replyTo, session -> {
                TextMessage replyMsg = session.createTextMessage();
                replyMsg.setText("My name is Arnio");
                replyMsg.setJMSCorrelationID(msg.getJMSCorrelationID());
                return replyMsg;
            });

            return null;
        });


        // now get started and send the first message that gets the ball rolling
        JmsTemplate jms = new JmsTemplate(amq.getConfiguration().getConnectionFactory());

        jms.send("hello", session -> {
            TextMessage msg = session.createTextMessage();
            msg.setText("Hello, I'm here");
            return msg;
        });

        assertMockEndpointsSatisfied();
        executor.shutdownNow();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            public void configure() throws Exception {
                from("activemq:queue:hello")
                    .process(exchange -> exchange.getMessage().setBody("What's your name"))
                    // use in out to get a reply as well
                    .to(ExchangePattern.InOut, "activemq:queue:nameRequestor?replyTo=queue:namedReplyQueue")
                    // and send the reply to our mock for validation
                    .to("mock:result");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));
        amq = camelContext.getComponent("activemq", JmsComponent.class);
        return camelContext;
    }


}
