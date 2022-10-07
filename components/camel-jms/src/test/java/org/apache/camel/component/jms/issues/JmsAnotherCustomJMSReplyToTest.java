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

import java.util.concurrent.TimeUnit;

import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.TextMessage;

import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.jms.core.JmsTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JmsAnotherCustomJMSReplyToTest extends AbstractJMSTest {
    private JmsComponent amq;

    @Test
    public void testCustomJMSReplyToInOnly() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("My name is Arnio");

        // start a inOnly route
        template.sendBody("activemq:queue:JmsAnotherCustomJMSReplyToTest", "Hello, I'm here");

        JmsTemplate jms = new JmsTemplate(amq.getConfiguration().getConnectionFactory());
        TextMessage msg = (TextMessage) jms.receive("JmsAnotherCustomJMSReplyToTest.dest");
        assertEquals("What's your name", msg.getText());

        // there should be a JMSReplyTo so we know where to send the reply
        Destination replyTo = msg.getJMSReplyTo();
        assertEquals("queue://JmsAnotherCustomJMSReplyToTest.reply", replyTo.toString());

        // send reply
        template.sendBody("activemq:" + replyTo, "My name is Arnio");

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> MockEndpoint.assertIsSatisfied(context));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {
                from("activemq:queue:JmsAnotherCustomJMSReplyToTest")
                        .setExchangePattern(ExchangePattern.InOnly)
                        .process(exchange -> {
                            exchange.getIn().setBody("What's your name");
                            exchange.getIn().setHeader("JMSReplyTo", "JmsAnotherCustomJMSReplyToTest.reply");
                        })
                        .to("activemq:queue:JmsAnotherCustomJMSReplyToTest.dest?preserveMessageQos=true");

                from("activemq:queue:JmsAnotherCustomJMSReplyToTest.reply").to("mock:result");
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
}
