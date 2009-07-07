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

import javax.jms.Destination;
import javax.jms.TextMessage;

import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.springframework.jms.core.JmsTemplate;
import static org.apache.activemq.camel.component.ActiveMQComponent.activeMQComponent;

/**
 * @version $Revision$
 */
public class JmsAnotherCustomJMSReplyToTest extends ContextTestSupport {

    private ActiveMQComponent amq;
    private static String MQURI = "vm://localhost?broker.persistent=false&broker.useJmx=false";

    public void testCustomJMSReplyToInOnly() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("My name is Arnio");

        // start a inOnly route
        template.sendBody("activemq:queue:hello", "Hello, I'm here");

        // now consume using something that is not Camel
        Thread.sleep(1000);

        JmsTemplate jms = new JmsTemplate(amq.getConfiguration().getConnectionFactory());
        TextMessage msg = (TextMessage) jms.receive("nameRequestor");
        assertEquals("What's your name", msg.getText());

        // there should be a JMSReplyTo so we know where to send the reply
        Destination replyTo = msg.getJMSReplyTo();
        assertEquals("queue://nameReplyQueue", replyTo.toString());

        // send reply
        template.sendBody("activemq:" + replyTo.toString(), "My name is Arnio");

        Thread.sleep(2000);
        assertMockEndpointsSatisfied();
    }


    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            public void configure() throws Exception {
                from("activemq:queue:hello")
                        .inOnly()
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                exchange.getIn().setBody("What's your name");
                                exchange.getIn().setHeader("JMSReplyTo", "nameReplyQueue");
                            }
                        })
                        .to("activemq:queue:nameRequestor?preserveMessageQos=true");

                from("activemq:queue:nameReplyQueue").to("mock:result");
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
