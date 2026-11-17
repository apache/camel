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
package org.apache.camel.component.sjms.consumer;

import java.util.Arrays;

import jakarta.jms.Connection;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.ObjectMessage;
import jakarta.jms.Session;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Test;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The polling consumer (pollEnrich and ConsumerTemplate) reads the message before the JMS consumer, session and
 * connection are closed. The Artemis client streams the body of a large message through the consumer, so the body
 * cannot be read after the consumer is closed.
 */
public class SjmsPollingConsumerLargeMessageTest extends JmsTestSupport {

    private static final String QUEUE = "SjmsPollingConsumerLargeMessageTest";
    private static final byte[] LARGE = payload(200_000);
    private static final byte[] SMALL = payload(512);

    @Override
    protected String getBrokerUri() {
        // every message above 1 KB is an Artemis large message
        return super.getBrokerUri() + "?minLargeMessageSize=1024";
    }

    @Test
    void consumerTemplateReceivesLargeBytesMessage() {
        template.sendBody("sjms:queue:" + QUEUE + ".receive", LARGE);

        Exchange exchange = consumer.receive("sjms:queue:" + QUEUE + ".receive", 5000);

        assertNotNull(exchange);
        assertArrayEquals(LARGE, exchange.getIn().getBody(byte[].class));
    }

    @Test
    void consumerTemplateReceivesLargeBytesMessageWithSelector() {
        String uri = "sjms:queue:" + QUEUE + ".selector";
        template.sendBodyAndHeader(uri, "other", "kind", "other");
        template.sendBodyAndHeader(uri, LARGE, "kind", "large");

        assertArrayEquals(LARGE, consumer.receiveBody(uri + "?messageSelector=kind='large'", 5000, byte[].class));
        assertEquals("other", consumer.receiveBody(uri + "?messageSelector=kind='other'", 5000, String.class));
    }

    @Test
    void pollEnrichReceivesLargeBytesMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:" + QUEUE + ".result");
        mock.expectedMessageCount(1);
        template.sendBody("sjms:queue:" + QUEUE + ".pollEnrich", LARGE);

        template.sendBody("direct:" + QUEUE + ".pollEnrich", "trigger");

        MockEndpoint.assertIsSatisfied(context);
        assertArrayEquals(LARGE, mock.getReceivedExchanges().get(0).getIn().getBody(byte[].class));
    }

    @Test
    void transactedConsumerTemplateCommitsTheReceive() {
        String uri = "sjms:queue:" + QUEUE + ".transacted?transacted=true";
        template.sendBody(uri, LARGE);

        assertArrayEquals(LARGE, consumer.receiveBody(uri, 5000, byte[].class));
        // the message is not delivered again
        assertNull(consumer.receive(uri, 1000));
    }

    @Test
    void clientAcknowledgeConsumerTemplateAcknowledgesTheMessage() {
        String uri = "sjms:queue:" + QUEUE + ".clientAck?acknowledgementMode=CLIENT_ACKNOWLEDGE";
        template.sendBody(uri, LARGE);

        assertArrayEquals(LARGE, consumer.receiveBody(uri, 5000, byte[].class));
        // the message is not delivered again
        assertNull(consumer.receive(uri, 1000));
    }

    @Test
    void transactedConsumerTemplateRollsBackMessageWhoseBodyCannotBeRead() throws Exception {
        String queue = QUEUE + ".unreadable";
        sendObjectMessage(queue, "not allowed");

        // objectMessageEnabled is false, so the body cannot be read: the receive fails and is rolled back
        RuntimeCamelException e = assertThrows(RuntimeCamelException.class,
                () -> consumer.receive("sjms:queue:" + queue + "?transacted=true", 5000));
        assertObjectMessageDisabled(e);

        Message message = receiveMessage(queue);
        assertEquals("not allowed", assertInstanceOf(ObjectMessage.class, message).getObject());
    }

    @Test
    void clientAcknowledgeConsumerTemplateRecoversMessageWhoseBodyCannotBeRead() throws Exception {
        String queue = QUEUE + ".unreadableClientAck";
        String options = "?acknowledgementMode=CLIENT_ACKNOWLEDGE";
        // a pooled session (as with a pooled connection factory) is not closed after the receive, but goes back to
        // the pool
        JmsPoolConnectionFactory pooledConnectionFactory = new JmsPoolConnectionFactory();
        pooledConnectionFactory.setConnectionFactory(connectionFactory);
        pooledConnectionFactory.setMaxConnections(1);
        try {
            SjmsComponent pooled = new SjmsComponent();
            pooled.setConnectionFactory(pooledConnectionFactory);
            context.addComponent("pooledsjms", pooled);
            sendObjectMessage(queue, "not allowed");

            // objectMessageEnabled is false, so the body cannot be read: the receive fails and the session is recovered
            RuntimeCamelException e = assertThrows(RuntimeCamelException.class,
                    () -> consumer.receive("pooledsjms:queue:" + queue + options, 5000));
            assertObjectMessageDisabled(e);

            // the next receive on the pooled session acknowledges its own message only
            template.sendBody("sjms:queue:" + queue + ".next", "next");
            assertEquals("next",
                    consumer.receiveBody("pooledsjms:queue:" + queue + ".next" + options, 5000, String.class));

            Message message = receiveMessage(queue);
            assertEquals("not allowed", assertInstanceOf(ObjectMessage.class, message).getObject());
            assertTrue(message.getJMSRedelivered());
        } finally {
            pooledConnectionFactory.stop();
        }
    }

    @Test
    void consumerTemplateReceivesSmallBytesMessage() {
        template.sendBody("sjms:queue:" + QUEUE + ".small", SMALL);

        assertArrayEquals(SMALL, consumer.receiveBody("sjms:queue:" + QUEUE + ".small", 5000, byte[].class));
    }

    @Test
    void routeReceivesLargeBytesMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:" + QUEUE + ".route");
        mock.expectedMessageCount(1);

        template.sendBody("sjms:queue:" + QUEUE + ".route", LARGE);

        MockEndpoint.assertIsSatisfied(context);
        assertArrayEquals(LARGE, mock.getReceivedExchanges().get(0).getIn().getBody(byte[].class));
    }

    private void sendObjectMessage(String queue, String body) throws Exception {
        try (Connection connection = connectionFactory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
             MessageProducer producer = session.createProducer(session.createQueue(queue))) {
            producer.send(session.createObjectMessage(body));
        }
    }

    private Message receiveMessage(String queue) throws Exception {
        try (Connection connection = connectionFactory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
             MessageConsumer messageConsumer = session.createConsumer(session.createQueue(queue))) {
            connection.start();
            Message message = messageConsumer.receive(5000);
            assertNotNull(message, "the message is still on the queue");
            // read the body while the session is open
            ((ObjectMessage) message).getObject();
            return message;
        }
    }

    private static void assertObjectMessageDisabled(RuntimeCamelException e) {
        IllegalStateException cause = assertInstanceOf(IllegalStateException.class, e.getCause());
        assertTrue(cause.getMessage().contains("objectMessageEnabled=true"), cause.getMessage());
    }

    private static byte[] payload(int size) {
        byte[] answer = new byte[size];
        Arrays.fill(answer, (byte) 'a');
        for (int i = 0; i < size; i += 1000) {
            answer[i] = (byte) ('0' + (i / 1000) % 10);
        }
        return answer;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("sjms:queue:" + QUEUE + ".route").to("mock:" + QUEUE + ".route");

                from("direct:" + QUEUE + ".pollEnrich")
                        .pollEnrich("sjms:queue:" + QUEUE + ".pollEnrich", 5000)
                        .to("mock:" + QUEUE + ".result");
            }
        };
    }
}
