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

import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.TextMessage;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.jms.core.JmsTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test using a fixed replyTo specified on the JMS endpoint
 */
@Timeout(30)
public class JmsJMSReplyToEndpointUsingInOutTest extends AbstractJMSTest {
    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;
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

            final TextMessage msg = (TextMessage) jms.receive("JmsJMSReplyToEndpointUsingInOutTest.namedRequestor");
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

        jms.send("JmsJMSReplyToEndpointUsingInOutTest", session -> {
            TextMessage msg = session.createTextMessage();
            msg.setText("Hello, I'm here");
            return msg;
        });

        MockEndpoint.assertIsSatisfied(context);
        executor.shutdownNow();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {
                from("activemq:queue:JmsJMSReplyToEndpointUsingInOutTest")
                        .process(exchange -> exchange.getMessage().setBody("What's your name"))
                        // use in out to get a reply as well
                        .to(ExchangePattern.InOut,
                                "activemq:queue:JmsJMSReplyToEndpointUsingInOutTest.namedRequestor?replyTo=queue:JmsJMSReplyToEndpointUsingInOutTest.namedReplyQueue")
                        // and send the reply to our mock for validation
                        .to("mock:result");
            }
        };
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected JmsComponent buildComponent(ConnectionFactory connectionFactory) {
        amq = super.buildComponent(connectionFactory);

        return amq;
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        consumer = camelContextExtension.getConsumerTemplate();
    }
}
