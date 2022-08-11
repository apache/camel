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

import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.TextMessage;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.jms.core.JmsTemplate;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.apache.camel.test.infra.activemq.common.ConnectionFactoryHelper.createConnectionFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JmsCustomJMSReplyToIssueTest extends AbstractJMSTest {

    private JmsComponent amq;

    @Test
    public void testCustomJMSReplyTo() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        // start a inOnly route
        template.sendBody("direct:start", "Hello World");

        // now consume using something that is not Camel
        Thread.sleep(1000);

        JmsTemplate jms = new JmsTemplate(amq.getConfiguration().getConnectionFactory());
        TextMessage msg = (TextMessage) jms.receive("in");
        assertEquals("Hello World", msg.getText());

        // there should be a JMSReplyTo so we know where to send the reply
        Destination replyTo = msg.getJMSReplyTo();
        assertEquals("queue://myReplyQueue", replyTo.toString());

        // send reply
        template.sendBody("activemq:" + replyTo, "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory
                = createConnectionFactory(service);
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));
        amq = camelContext.getComponent("activemq", JmsComponent.class);

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // must preserve QoS so Camel will send JMSReplyTo even if message is inOnly
                from("direct:start").process(exchange -> {
                    exchange.getMessage().setBody("Hello World");
                    // set the JMSReplyTo to force sending the reply here
                    exchange.getMessage().setHeader("JMSReplyTo", "myReplyQueue");
                }).to("activemq:queue:in?preserveMessageQos=true");

                from("activemq:queue:myReplyQueue").to("mock:result");
            }
        };
    }

}
