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
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.util.Wait;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class DlqTest extends CamelSpringTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(DlqTest.class);
    BrokerService broker;
    int messageCount;

    @Test
    public void testSendToDlq() throws Exception {
        sendJMSMessageToKickOffRoute();

        LOG.info("Wait for dlq message...");

        assertTrue(Wait.waitFor(new Wait.Condition() {
            @Override
            public boolean isSatisified() throws Exception {
                return broker.getAdminView().getTotalEnqueueCount() == 2;
            }
        }));
    }

    private void sendJMSMessageToKickOffRoute() throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("vm://testDlq");
        factory.setWatchTopicAdvisories(false);
        Connection connection = factory.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer producer = session.createProducer(new ActiveMQQueue("fidEtpOrders"));
        TextMessage message = session.createTextMessage("Some Text, messageCount:" + messageCount++);
        message.setJMSCorrelationID("pleaseCorrelate");
        producer.send(message);
        connection.close();
    }

    private BrokerService createBroker(boolean deleteAllMessages) throws Exception {
        BrokerService brokerService = new BrokerService();
        brokerService.setDeleteAllMessagesOnStartup(deleteAllMessages);
        brokerService.setBrokerName("testDlq");
        brokerService.setAdvisorySupport(false);
        brokerService.setDataDirectory("target/data");
        return brokerService;
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {

        deleteDirectory("target/data");

        // make broker available to recovery processing on app context start
        try {
            broker = createBroker(true);
            broker.start();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start broker", e);
        }

        return new ClassPathXmlApplicationContext("org/apache/camel/component/activemq/dlq.xml");
    }

    public static class CanError {
        public String enrich(String body) throws Exception {
            LOG.info("Got body: " + body);
            throw new RuntimeException("won't enrich today!");
        }
    }
}
