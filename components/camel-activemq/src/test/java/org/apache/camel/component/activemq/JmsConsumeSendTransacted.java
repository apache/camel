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

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class JmsConsumeSendTransacted extends CamelSpringTestSupport {
    BrokerService broker;
    int messageCount;

    @Test
    public void testTransactedRoute() throws Exception {
        sendJMSMessageToKickOffRoute();

        // camel route will use a single transaction for send and and ack
        consumeMessages();
    }

    private void consumeMessages() throws Exception {

        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("vm://testTran");
        factory.setWatchTopicAdvisories(false);
        Connection connection = factory.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer consumer = session.createConsumer(new ActiveMQQueue("to"));

        int messagesToConsume = messageCount;
        while (messagesToConsume > 0) {
            Message message = consumer.receive(5000);
            if (message != null) {
                messagesToConsume--;
            }
        }
    }

    private void sendJMSMessageToKickOffRoute() throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("vm://testTran");
        factory.setWatchTopicAdvisories(false);
        Connection connection = factory.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer producer = session.createProducer(new ActiveMQQueue("from"));
        TextMessage message = session.createTextMessage("Some Text, messageCount:" + messageCount++);
        message.setIntProperty("seq", messageCount);
        producer.send(message);
        connection.close();
    }

    private BrokerService createBroker(boolean deleteAllMessages) throws Exception {
        BrokerService brokerService = new BrokerService();
        brokerService.setDeleteAllMessagesOnStartup(deleteAllMessages);
        brokerService.setBrokerName("testTran");
        brokerService.setAdvisorySupport(false);
        brokerService.setUseJmx(false);
        brokerService.setDataDirectory("target/data");
        brokerService.addConnector("tcp://0.0.0.0:61616");
        return brokerService;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        try {
            broker = createBroker(true);
            broker.start();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start broker", e);
        }

        return new ClassPathXmlApplicationContext("org/apache/camel/component/activemq/jmsConsumeSendTransacted.xml");
    }
}
