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
package org.apache.camel.component.jms.issues;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;

import static org.apache.activemq.camel.component.ActiveMQComponent.activeMQComponent;
/**
 * Unit test using a fixed replyTo specified on the JMS endpoint
 * 
 * @version $Revision$
 */
public class JmsJMSReplyToEndpointUsingInOutTest extends ContextTestSupport {
    private static final String MQURI = "vm://localhost?broker.persistent=false&broker.useJmx=false";
    private ActiveMQComponent amq;
    

    public void testCustomJMSReplyToInOut() throws Exception {

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("My name is Arnio");

        // do not use Camel to send and receive to simulate a non Camel client

        // use another thread to listen and send the reply
        Executors.newFixedThreadPool(1).submit(new Callable<Object>() {
            public Object call() throws Exception {
                JmsTemplate jms = new JmsTemplate(amq.getConfiguration().getConnectionFactory());

                final TextMessage msg = (TextMessage) jms.receive("nameRequestor");
                assertEquals("What's your name", msg.getText());

                // there should be a JMSReplyTo so we know where to send the reply
                final Destination replyTo = msg.getJMSReplyTo();

                // send reply
                jms.send(replyTo, new MessageCreator() {
                    public Message createMessage(Session session) throws JMSException {
                        TextMessage replyMsg = session.createTextMessage();
                        replyMsg.setText("My name is Arnio");
                        replyMsg.setJMSCorrelationID(msg.getJMSCorrelationID());
                        return replyMsg;
                    }
                });

                return null;
            }
        });


        // now get started and send the first message that gets the ball rolling
        JmsTemplate jms = new JmsTemplate(amq.getConfiguration().getConnectionFactory());

        jms.send("hello", new MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                TextMessage msg = session.createTextMessage();
                msg.setText("Hello, I'm here");
                return msg;
            }
        });

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            public void configure() throws Exception {
                from("activemq:queue:hello")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            exchange.getOut().setBody("What's your name");
                        }
                    })
                    // use in out to get a reply as well
                    .to(ExchangePattern.InOut, "activemq:queue:nameRequestor?replyTo=queue:namedReplyQueue")
                    // and send the reply to our mock for validation
                    .to("mock:result");
            }
        };
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        amq = activeMQComponent(MQURI);
        camelContext.addComponent("activemq", amq);
        return camelContext;
    }


}