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
package org.apache.camel.component.sjms.producer;

import java.util.UUID;

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
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.component.sjms.SjmsConstants;
import org.apache.camel.component.sjms.jms.DefaultDestinationCreationStrategy;
import org.apache.camel.component.sjms.jms.DestinationCreationStrategy;
import org.apache.camel.component.sjms.jms.Jms11ObjectFactory;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.impl.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


public class InOutQueueProducerTest extends JmsTestSupport {

    private DestinationCreationStrategy strategy = new DefaultDestinationCreationStrategy();

    protected ActiveMQConnectionFactory connectionFactory;

    protected Session session;

    int counter = 0;

/*    @RegisterExtension
    public static ArtemisService service = ArtemisServiceFactory.createSingletonVMService();*/

    private static final String TEST_DESTINATION_NAME = "in.out.queue.producer.test.InOutQueueProducerTest";

    public InOutQueueProducerTest() {
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
    }

    @Test
    public void testInOutQueueProducerHeader() throws Exception {
        MessageConsumer mc = createQueueConsumer("foo");
        assertNotNull(mc);
        final String requestText = "Hello World!";
        final String responseText = "How are you";
        mc.setMessageListener(new MyMessageListener(requestText, responseText));

        Object responseObject
                = template.requestBodyAndHeader("direct:start", requestText, SjmsConstants.JMS_DESTINATION_NAME, "foo");
        assertNotNull(responseObject);
        assertTrue(responseObject instanceof String);
        assertEquals(responseText, responseObject);
        mc.close();
    }

    @Test
    public void testInOutQueueProducerWithCorrelationId() throws Exception {
        MessageConsumer mc = createQueueConsumer(TEST_DESTINATION_NAME + ".request");
        assertNotNull(mc);
        final String requestText = "Hello World!";
        final String responseText = "How are you";
        mc.setMessageListener(new MyMessageListener(requestText, responseText));
        final String correlationId = UUID.randomUUID().toString().replace("-", "");
        Exchange exchange = template.request("direct:start", exchange1 -> {
            exchange1.getMessage().setBody(requestText);
            exchange1.getMessage().setHeader("JMSCorrelationID", correlationId);
        });
        assertNotNull(exchange);
        assertTrue(exchange.getIn().getBody() instanceof String);
        assertEquals(responseText, exchange.getIn().getBody());
        assertEquals(correlationId, exchange.getIn().getHeader("JMSCorrelationID", String.class));
        mc.close();

    }

    /*
     * @see org.apache.camel.test.junit5.CamelTestSupport#createRouteBuilder()
     *
     * @return
     *
     * @throws Exception
     */

    @Override
    @ContextFixture
    public void configureCamelContext(CamelContext camelContext) throws Exception {
        //configureJMSCamelContext(camelContext);
        super.configureCamelContext(camelContext);
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

                TextMessage response = session.createTextMessage();
                response.setText(responseText);
                response.setJMSCorrelationID(request.getJMSCorrelationID());
                MessageProducer mp = session.createProducer(message.getJMSReplyTo());
                mp.send(response);
                mp.close();
            } catch (JMSException e) {
                fail(e.getLocalizedMessage());
            }
        }
    }
}
