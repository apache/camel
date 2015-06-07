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
package org.apache.camel.component.sjms.producer;

import java.util.UUID;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.TextMessage;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.support.JmsTestSupport;

import org.junit.Test;

public class InOutQueueProducerTest extends JmsTestSupport {
    
    private static final String TEST_DESTINATION_NAME = "in.out.queue.producer.test";
    
    public InOutQueueProducerTest() {
    }
    
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

    }

    @Test
    public void testInOutQueueProducerWithCorrelationId() throws Exception {
        MessageConsumer mc = createQueueConsumer(TEST_DESTINATION_NAME + ".request");
        assertNotNull(mc);
        final String requestText = "Hello World!";
        final String responseText = "How are you";
        mc.setMessageListener(new MyMessageListener(requestText, responseText));
        final String correlationId = UUID.randomUUID().toString().replace("-", "");
        Exchange exchange = template.request("direct:start", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getOut().setBody(requestText);
                exchange.getOut().setHeader("JMSCorrelationID", correlationId);
            }
        });
        assertNotNull(exchange);
        assertTrue(exchange.getIn().getBody() instanceof String);
        assertEquals(responseText, exchange.getIn().getBody());
        assertEquals(correlationId, exchange.getIn().getHeader("JMSCorrelationID", String.class));
        mc.close();

    }

    /*
     * @see org.apache.camel.test.junit4.CamelTestSupport#createRouteBuilder()
     * 
     * @return
     * 
     * @throws Exception
     */
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .to("log:" + TEST_DESTINATION_NAME + ".in.log.1?showBody=true")
                    .inOut("sjms:queue:" + TEST_DESTINATION_NAME + ".request" + "?namedReplyTo="
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
                TextMessage request = (TextMessage)message;
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
