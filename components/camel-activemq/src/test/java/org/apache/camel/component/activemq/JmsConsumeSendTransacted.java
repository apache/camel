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
package org.apache.camel.component.activemq;

import java.util.Map;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.camel.component.activemq.support.ActiveMQSpringTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JmsConsumeSendTransacted extends ActiveMQSpringTestSupport {

    BrokerService broker;
    final int messagesSent = 1;
    int messagesToConsume;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        broker = createBroker();
        broker.start();
        super.setUp();
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        broker.stop();
    }

    @Test
    public void testTransactedRoute() throws Exception {
        sendJMSMessageToKickOffRoute();

        // camel route will use a single transaction for send and and ack
        consumeMessages();
        assertEquals(0, messagesToConsume, "Some messages were not consumed");
    }

    private void consumeMessages() throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(vmUri());
        factory.setWatchTopicAdvisories(false);
        Connection connection = factory.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer consumer = session.createConsumer(new ActiveMQQueue("to"));

        messagesToConsume = messagesSent;
        while (messagesToConsume > 0) {
            Message message = consumer.receive(5000);
            if (message != null) {
                messagesToConsume--;
            }
        }
    }

    private void sendJMSMessageToKickOffRoute() throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(vmUri());
        factory.setWatchTopicAdvisories(false);
        Connection connection = factory.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer producer = session.createProducer(new ActiveMQQueue("from"));
        TextMessage message = session.createTextMessage("Some Text, messageCount:" + messagesSent);
        message.setIntProperty("seq", messagesSent);
        producer.send(message);
        connection.close();
    }

    private BrokerService createBroker() throws Exception {
        return createBroker(true, true);
    }

    @Override
    protected Map<String, String> getTranslationProperties() {
        Map<String, String> props = super.getTranslationProperties();
        props.put("brokerUri", broker.getTransportConnectors().get(0).getUri().toString());
        return props;
    }

}
