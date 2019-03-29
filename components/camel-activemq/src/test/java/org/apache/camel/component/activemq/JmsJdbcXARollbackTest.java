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

import java.sql.ResultSet;
import java.sql.SQLException;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.sql.DataSource;
import javax.transaction.TransactionManager;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.policy.SharedDeadLetterStrategy;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.util.Wait;
import org.apache.camel.Exchange;
import org.apache.camel.component.jms.JmsMessage;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.transaction.jta.JtaTransactionManager;

/**
 * shows rollback and redelivery dlq respected with external tm
 */
public class JmsJdbcXARollbackTest extends CamelSpringTestSupport {
    static TransactionManager[] transactionManager = new TransactionManager[1];
    private static final Logger LOG = LoggerFactory.getLogger(JmsJdbcXARollbackTest.class);
    BrokerService broker;
    int messageCount;

    public java.sql.Connection initDb() throws Exception {
        String createStatement = "CREATE TABLE SCP_INPUT_MESSAGES (" + "id int NOT NULL GENERATED ALWAYS AS IDENTITY, " + "messageId varchar(96) NOT NULL, "
                                 + "messageCorrelationId varchar(96) NOT NULL, " + "messageContent varchar(2048) NOT NULL, " + "PRIMARY KEY (id) )";

        java.sql.Connection conn = getJDBCConnection();
        try {
            conn.createStatement().execute(createStatement);
        } catch (SQLException alreadyExists) {
            log.info("ex on create tables", alreadyExists);
        }

        try {
            conn.createStatement().execute("DELETE FROM SCP_INPUT_MESSAGES");
        } catch (SQLException ex) {
            log.info("ex on create delete all", ex);
        }

        return conn;
    }

    private java.sql.Connection getJDBCConnection() throws Exception {
        DataSource dataSource = getMandatoryBean(DataSource.class, "managedDataSourceWithRecovery");
        return dataSource.getConnection();
    }

    private int dumpDb(java.sql.Connection jdbcConn) throws Exception {
        int count = 0;
        ResultSet resultSet = jdbcConn.createStatement().executeQuery("SELECT * FROM SCP_INPUT_MESSAGES");
        while (resultSet.next()) {
            count++;
            log.info("message - seq:" + resultSet.getInt(1) + ", id: " + resultSet.getString(2) + ", corr: " + resultSet.getString(3) + ", content: " + resultSet.getString(4));
        }
        return count;
    }

    @Test
    public void testConsumeRollback() throws Exception {
        java.sql.Connection jdbcConn = initDb();

        initTMRef();
        sendJMSMessageToKickOffRoute();

        // should go to dlq eventually
        Wait.waitFor(new Wait.Condition() {
            @Override
            public boolean isSatisified() throws Exception {
                return consumedFrom(SharedDeadLetterStrategy.DEFAULT_DEAD_LETTER_QUEUE_NAME);
            }
        });
        assertEquals("message in db, commit to db worked", 0, dumpDb(jdbcConn));
        assertFalse("Nothing to to out q", consumedFrom("scp_transacted_out"));

    }

    private boolean consumedFrom(String qName) throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("vm://testXA");
        factory.setWatchTopicAdvisories(false);
        Connection connection = factory.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer consumer = session.createConsumer(new ActiveMQQueue(qName));
        Message message = consumer.receive(500);
        LOG.info("Got from queue:{} {}", qName, message);
        connection.close();
        return message != null;
    }

    private void initTMRef() {
        transactionManager[0] = getMandatoryBean(JtaTransactionManager.class, "jtaTransactionManager").getTransactionManager();

    }

    private void sendJMSMessageToKickOffRoute() throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("vm://testXA");
        factory.setWatchTopicAdvisories(false);
        Connection connection = factory.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer producer = session.createProducer(new ActiveMQQueue("scp_transacted"));
        TextMessage message = session.createTextMessage("Some Text, messageCount:" + messageCount++);
        message.setJMSCorrelationID("pleaseCorrelate");
        producer.send(message);
        connection.close();
    }

    private BrokerService createBroker(boolean deleteAllMessages) throws Exception {
        BrokerService brokerService = new BrokerService();
        brokerService.setDeleteAllMessagesOnStartup(deleteAllMessages);
        brokerService.setBrokerName("testXA");
        brokerService.setAdvisorySupport(false);
        brokerService.setUseJmx(false);
        brokerService.setDataDirectory("target/data");
        brokerService.addConnector("tcp://0.0.0.0:61616");
        return brokerService;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {

        deleteDirectory("target/data/howl");

        // make broker available to recovery processing on app context start
        try {
            broker = createBroker(true);
            broker.start();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start broker", e);
        }

        return new ClassPathXmlApplicationContext("org/apache/camel/component/activemq/jmsXajdbcRollback.xml");
    }

    public static class MarkRollbackOnly {
        public String enrich(Exchange exchange) throws Exception {
            LOG.info("Got exchange: " + exchange);
            LOG.info("Got message: " + ((JmsMessage)exchange.getIn()).getJmsMessage());

            LOG.info("Current tx: " + transactionManager[0].getTransaction());
            LOG.info("Marking rollback only...");
            transactionManager[0].getTransaction().setRollbackOnly();
            return "Some Text";
        }
    }
}
