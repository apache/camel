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

import jakarta.jms.Connection;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.util.Wait;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.activemq.support.ActiveMQSpringTestSupport;
import org.apache.camel.test.infra.activemq.services.ActiveMQEmbeddedService;
import org.apache.camel.test.infra.activemq.services.ActiveMQEmbeddedServiceBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class DlqTest extends ActiveMQSpringTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(DlqTest.class);

    @RegisterExtension
    public ActiveMQEmbeddedService service = ActiveMQEmbeddedServiceBuilder.defaultBroker()
            .withBrokerName(DlqTest.class.getSimpleName())
            .withUseJmx(true)
            .build();

    private int messageCount;

    @Test
    public void testSendToDlq() throws Exception {
        sendJMSMessageToKickOffRoute();

        LOG.info("Wait for dlq message...");
        assertTrue(Wait.waitFor(() -> service.getBrokerService().getAdminView().getTotalEnqueueCount() == 2));
    }

    private void sendJMSMessageToKickOffRoute() throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(vmUri());
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

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        service.initialize();
        return super.createApplicationContext();
    }

    public static class CanError {
        public String enrich(String body) {
            LOG.info("Got body: " + body);
            throw new RuntimeCamelException("won't enrich today!");
        }
    }
}
