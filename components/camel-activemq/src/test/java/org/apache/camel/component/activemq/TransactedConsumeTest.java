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
import java.util.concurrent.atomic.AtomicLong;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.policy.PolicyEntry;
import org.apache.activemq.broker.region.policy.PolicyMap;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.activemq.store.kahadb.KahaDBPersistenceAdapter;
import org.apache.activemq.store.kahadb.disk.journal.Journal;
import org.apache.activemq.util.Wait;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.activemq.support.ActiveMQSpringTestSupport;
import org.apache.camel.component.jms.JmsMessage;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TransactedConsumeTest extends ActiveMQSpringTestSupport {

    static AtomicLong firstConsumed = new AtomicLong();
    static AtomicLong consumed = new AtomicLong();

    private static final Logger LOG = LoggerFactory.getLogger(TransactedConsumeTest.class);

    BrokerService broker;
    int messageCount = 10000;

    @Test
    public void testConsume() throws Exception {
        LOG.info("Wait for dequeue message...");

        assertTrue(Wait.waitFor(new Wait.Condition() {
            @Override
            public boolean isSatisified() throws Exception {
                return broker.getAdminView().getTotalDequeueCount() >= messageCount;
            }
        }, 20 * 60 * 1000));
        long duration = System.currentTimeMillis() - firstConsumed.get();
        LOG.info("Done message consumption in {} millis", duration);
    }

    private void sendJMSMessageToKickOffRoute() throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(vmUri());
        factory.setUseAsyncSend(true);
        factory.setWatchTopicAdvisories(false);
        factory.setObjectMessageSerializationDefered(true);
        Connection connection = factory.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer producer = session.createProducer(new ActiveMQQueue("scp_transacted"));
        for (int i = 0; i < messageCount; i++) {
            TextMessage message = session.createTextMessage("Some Text, messageCount:" + i);
            message.setJMSCorrelationID("pleaseCorrelate");
            producer.send(message);
        }
        LOG.info("Sent: " + messageCount);
        connection.close();
    }

    private BrokerService createBroker() throws Exception {
        BrokerService brokerService = createBroker(true, true);
        brokerService.setUseJmx(true);
        brokerService.getManagementContext().setUseMBeanServer(false);
        PolicyMap policyMap = new PolicyMap();
        PolicyEntry defaultPolicy = new PolicyEntry();
        policyMap.setDefaultEntry(defaultPolicy);
        brokerService.setDestinationPolicy(policyMap);

        brokerService.setAdvisorySupport(false);
        brokerService.setDataDirectory(testDirectory().toString());
        // AMQPersistenceAdapter amq = new AMQPersistenceAdapter();
        // amq.setDirectory(new File("target/data"));
        // brokerService.setPersistenceAdapter(amq);
        KahaDBPersistenceAdapter kahaDBPersistenceAdapter = (KahaDBPersistenceAdapter) brokerService
                .getPersistenceAdapter();
        kahaDBPersistenceAdapter.setJournalDiskSyncStrategy(Journal.JournalDiskSyncStrategy.NEVER.toString());
        return brokerService;
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        // make broker available to recovery processing on app context start
        try {
            broker = createBroker();
            broker.start();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start broker", e);
        }

        try {
            sendJMSMessageToKickOffRoute();
        } catch (Exception e) {
            throw new RuntimeException("Failed to fill q", e);
        }

        return super.createApplicationContext();
    }

    @Override
    protected Map<String, String> getTranslationProperties() {
        Map<String, String> map = super.getTranslationProperties();
        map.put("brokerUri", getBrokerUri(broker));
        return map;
    }

    static class ConnectionLog implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            if (consumed.getAndIncrement() == 0) {
                firstConsumed.set(System.currentTimeMillis());
            }
            ActiveMQTextMessage m = (ActiveMQTextMessage) ((JmsMessage) exchange.getIn()).getJmsMessage();
            // Thread.currentThread().sleep(500);
            if (consumed.get() % 500 == 0) {
                LOG.info("received on {}", m.getConnection());
            }
        }
    }

}
