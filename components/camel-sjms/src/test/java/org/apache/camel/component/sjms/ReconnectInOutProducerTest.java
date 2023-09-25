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
package org.apache.camel.component.sjms;

import jakarta.jms.Connection;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@DisabledIfSystemProperty(named = "activemq.instance.type", matches = "remote",
                          disabledReason = "Requires control of ActiveMQ, so it can only run locally (embedded or container)")
public class ReconnectInOutProducerTest extends JmsTestSupport {

    protected ActiveMQConnectionFactory connectionFactory;

    protected Session session;

    @RegisterExtension
    public static ArtemisService service = ArtemisServiceFactory.createSingletonVMService();

    private static final String TEST_DESTINATION_NAME = "in.out.queue.producer.test.ReconnectInOutProducerTest";

    @Override
    protected boolean useJmx() {
        return false;
    }

    @Test
    public void testInOutQueueProducer() throws Exception {
        MessageConsumer mc = createQueueConsumer(TEST_DESTINATION_NAME + ".request");
        assertNotNull(mc);
        final String requestText = "Hello World!";
        final String responseText = "How are you";
        mc.setMessageListener(new MyMessageListener(requestText, responseText));
        Object responseObject = template.requestBody("direct:start", requestText);
        assertNotNull(responseObject);
        assertTrue(responseObject instanceof String);
        assertEquals(responseText, responseObject);
        mc.close();

        reconnect();

        mc = createQueueConsumer(TEST_DESTINATION_NAME + ".request");
        assertNotNull(mc);
        mc.setMessageListener(new MyMessageListener(requestText, responseText));
        responseObject = template.requestBody("direct:start", requestText);
        assertNotNull(responseObject);
        assertTrue(responseObject instanceof String);
        assertEquals(responseText, responseObject);
        mc.close();

    }

    @Override
    protected void configureCamelContext(CamelContext camelContext) throws Exception {
        connectionFactory = new ActiveMQConnectionFactory(service.serviceAddress());

        Connection connection = connectionFactory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        SjmsComponent component = new SjmsComponent();
        component.setConnectionFactory(connectionFactory);
        camelContext.addComponent("sjms", component);
    }

    @Override
    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        final RouteBuilder routeBuilder = createRouteBuilder();

        if (routeBuilder != null) {
            context.addRoutes(routeBuilder);
        }
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to("log:" + TEST_DESTINATION_NAME + ".in.log.1?showBody=true")
                        .to(ExchangePattern.InOut, "sjms:queue:" + TEST_DESTINATION_NAME + ".request" + "?replyTo="
                                                   + TEST_DESTINATION_NAME + ".response")
                        .to("log:" + TEST_DESTINATION_NAME + ".out.log.1?showBody=true");
            }
        };
    }

    protected class MyMessageListener implements MessageListener {
        private String requestText;
        private String responseText;

        public MyMessageListener(String request, String response) {
            this.requestText = request;
            this.responseText = response;
        }

        @Override
        public void onMessage(Message message) {
            try {
                TextMessage request = (TextMessage) message;
                assertNotNull(request);
                String text = request.getText();
                assertEquals(requestText, text);

                TextMessage response = getSession().createTextMessage();
                response.setText(responseText);
                response.setJMSCorrelationID(request.getJMSCorrelationID());
                MessageProducer mp = getSession().createProducer(message.getJMSReplyTo());
                mp.send(response);
                mp.close();
            } catch (JMSException e) {
                fail(e.getLocalizedMessage());
            }
        }
    }
}
