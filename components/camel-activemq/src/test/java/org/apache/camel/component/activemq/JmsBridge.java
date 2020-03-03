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

import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerPlugin;
import org.apache.activemq.broker.BrokerPluginSupport;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.ConnectionContext;
import org.apache.activemq.broker.ProducerBrokerExchange;
import org.apache.activemq.broker.TransportConnector;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ConnectionInfo;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class JmsBridge extends CamelSpringTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(JmsBridge.class);

    BrokerService brokerSub;
    BrokerService brokerPub;

    int messageCount;
    final int backLog = 50;
    final int errorLimit = 10;
    AtomicInteger sendCount = new AtomicInteger();
    AtomicInteger connectionCount = new AtomicInteger();

    @Test
    public void testBridgeWorks() throws Exception {
        sendJMSMessageToKickOffRoute();

        consumeMessages();

        LOG.info("ConnectionCount: " + connectionCount.get());
        assertEquals("x connections", 5 + errorLimit, connectionCount.get());
    }

    private void consumeMessages() throws Exception {

        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("vm://sub");
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
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("vm://pub");
        factory.setWatchTopicAdvisories(false);
        Connection connection = factory.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer producer = session.createProducer(new ActiveMQQueue("from"));

        for (int i = 0; i < backLog; i++) {
            TextMessage message = session.createTextMessage("Some Text, messageCount:" + messageCount++);
            message.setIntProperty("seq", messageCount);
            producer.send(message);
        }
        connection.close();
    }

    private BrokerService createBroker(String name, int port, boolean deleteAllMessages) throws Exception {
        BrokerService brokerService = new BrokerService();
        brokerService.setDeleteAllMessagesOnStartup(deleteAllMessages);
        brokerService.setBrokerName(name);
        brokerService.setAdvisorySupport(false);
        brokerService.setUseJmx(false);
        brokerService.setDataDirectory("target/data");
        if (port > 0) {
            brokerService.addConnector("tcp://0.0.0.0:" + port);
        }
        return brokerService;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {

        try {
            brokerSub = createBroker("sub", 61617, true);
            brokerSub.setPlugins(new BrokerPlugin[] {new BrokerPluginSupport() {
                @Override
                public void send(ProducerBrokerExchange producerExchange, org.apache.activemq.command.Message messageSend) throws Exception {
                    if (sendCount.incrementAndGet() <= errorLimit) {
                        throw new RuntimeException("You need to try send " + errorLimit + " times!");
                    }
                    super.send(producerExchange, messageSend);
                }

                @Override
                public void addConnection(ConnectionContext context, ConnectionInfo info) throws Exception {
                    if (((TransportConnector)context.getConnector()).getConnectUri().getScheme().equals("tcp") && connectionCount.incrementAndGet() <= errorLimit) {
                        throw new SecurityException("You need to try connect " + errorLimit + " times!");
                    }
                    super.addConnection(context, info);
                }
            }});
            brokerSub.start();

            brokerPub = createBroker("pub", 61616, true);
            brokerPub.start();

        } catch (Exception e) {
            throw new RuntimeException("Failed to start broker", e);
        }

        return new ClassPathXmlApplicationContext("org/apache/camel/component/activemq/jmsBridge.xml");
    }
}
