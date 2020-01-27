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

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.TextMessage;

import org.apache.camel.Exchange;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.Test;

public class InOutTempQueueProducerTest extends JmsTestSupport {
    
    @Override
    protected boolean useJmx() {
        return true;
    }

    @Test
    public void testInOutQueueProducer() throws Exception {
        String queueName = "in.out.queue.producer.test.request";
        MessageConsumer mc = createQueueConsumer(queueName);
        assertNotNull(mc);
        final String requestText = "Hello World!";
        final String responseText = "How are you";
        mc.setMessageListener(new MyMessageListener(requestText, responseText));
        Object responseObject = template.requestBody("sjms:queue:" + queueName + "?exchangePattern=InOut", requestText);
        assertNotNull(responseObject);
        assertTrue(responseObject instanceof String);
        assertEquals(responseText, responseObject);
        mc.close();

    }

    @Test
    public void testInOutQueueProducerWithCorrelationId() throws Exception {
        String queueName = "in.out.queue.producer.test.request";
        MessageConsumer mc = createQueueConsumer(queueName);
        assertNotNull(mc);
        final String requestText = "Hello World!";
        final String responseText = "How are you";
        mc.setMessageListener(new MyMessageListener(requestText, responseText));
        final String correlationId = UUID.randomUUID().toString().replace("-", "");
        Exchange exchange = template.request("sjms:queue:" + queueName + "?exchangePattern=InOut", exchange1 -> {
            exchange1.getIn().setBody(requestText);
            exchange1.getIn().setHeader("JMSCorrelationID", correlationId);
        });
        assertNotNull(exchange);
        assertTrue(exchange.getIn().getBody() instanceof String);
        assertEquals(responseText, exchange.getMessage().getBody());
        assertEquals(correlationId, exchange.getMessage().getHeader("JMSCorrelationID", String.class));
        mc.close();

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
