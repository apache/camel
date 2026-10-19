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
package org.apache.camel.component.sjms.batch;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsMessage;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.sjms.batch.BatchTestHelper.assertBatchSizesInOrder;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.getBatchExchanges;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BatchConsumerPoisonMessageTest extends JmsTestSupport {

    private static final String SJMS_FROMF_URI = "sjms:queue:%s?batching=true&batchSize=5&objectMessageEnabled=true";
    private static final String QUEUE_NAME
            = "batch.consumer.queue.BatchConsumerPoisonMessageTest";
    private static final String MOCK_RESULT = "mock:result";

    @Test
    public void testBatchConsumerPoisonMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint(MOCK_RESULT);
        mock.expectedMessageCount(1);

        MyMessageProducer producer = new MyMessageProducer(getSession(), QUEUE_NAME);
        for (int i = 1; i <= 4; i++) {
            producer.sendTextMessage("Hello!");
        }
        producer.sendObjectMessage(new Poison());

        mock.assertIsSatisfied();
        assertBatchSizesInOrder(mock, 5);

        List<Exchange> exchanges = getBatchExchanges(mock.getExchanges().get(0));

        Exchange exchange = exchanges.get(0);
        assertEquals("Hello!", exchange.getIn().getBody(String.class));
        assertNull(exchange.getException());
        assertInstanceOf(Message.class, exchange.getIn(SjmsMessage.class).getJmsMessage());

        Exchange poisonExchange = exchanges.get(4);
        assertTrue(poisonExchange.getIn().getBody(String.class)
                .startsWith("Poison JMS message payload: Failed"));
        assertNotNull(poisonExchange.getException());
        assertInstanceOf(Message.class, poisonExchange.getIn(SjmsMessage.class).getJmsMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                fromF(SJMS_FROMF_URI, QUEUE_NAME)
                        .to(MOCK_RESULT);
            }
        };
    }

    public static class Poison implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @Serial
        private void readObject(ObjectInputStream in) throws IOException {
            throw new IOException("poison!");
        }
    }

    private static class MyMessageProducer {
        private final Session session;
        private final MessageProducer producer;

        public MyMessageProducer(Session session, String queue) throws JMSException {
            this.session = session;
            Destination destination = session.createQueue(queue);
            this.producer = session.createProducer(destination);
        }

        public void sendTextMessage(String message) throws JMSException {
            producer.send(session.createTextMessage(message));
        }

        public void sendObjectMessage(Serializable message) throws JMSException {
            producer.send(session.createObjectMessage(message));
        }
    }
}
