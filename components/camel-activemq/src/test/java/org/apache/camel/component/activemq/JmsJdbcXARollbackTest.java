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
import java.util.Map;

import jakarta.jms.Connection;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import javax.sql.DataSource;
import jakarta.transaction.TransactionManager;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.region.policy.SharedDeadLetterStrategy;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.util.Wait;
import org.apache.camel.Exchange;
import org.apache.camel.component.activemq.support.ActiveMQSpringTestSupport;
import org.apache.camel.component.jms.JmsMessage;
import org.apache.camel.test.infra.activemq.services.ActiveMQEmbeddedService;
import org.apache.camel.test.infra.activemq.services.ActiveMQEmbeddedServiceBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.jta.JtaTransactionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * shows rollback and redelivery dlq respected with external tm
 */
public class JmsJdbcXARollbackTest extends ActiveMQSpringTestSupport {

    static TransactionManager transactionManager;

    private static final Logger LOG = LoggerFactory.getLogger(JmsJdbcXARollbackTest.class);

    @RegisterExtension
    public ActiveMQEmbeddedService service = ActiveMQEmbeddedServiceBuilder
            .defaultBroker()
            .withTcpTransport()
            .build();

    int messageCount;

    public java.sql.Connection initDb() throws Exception {
        String createStatement = "CREATE TABLE SCP_INPUT_MESSAGES (" + "id int NOT NULL GENERATED ALWAYS AS IDENTITY, "
                                 + "messageId varchar(96) NOT NULL, " + "messageCorrelationId varchar(96) NOT NULL, "
                                 + "messageContent varchar(2048) NOT NULL, " + "PRIMARY KEY (id) )";

        java.sql.Connection conn = getJDBCConnection();
        try {
            conn.createStatement().execute(createStatement);
        } catch (SQLException alreadyExists) {
            LOG.info("ex on create tables", alreadyExists);
        }

        try {
            conn.createStatement().execute("DELETE FROM SCP_INPUT_MESSAGES");
        } catch (SQLException ex) {
            LOG.info("ex on create delete all", ex);
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
            LOG.info("message - seq: {}, id: {}, corr: {}, content: {}", resultSet.getInt(1), resultSet.getString(2),
                    resultSet.getString(3), resultSet.getString(4));
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
        assertEquals(0, dumpDb(jdbcConn), "message in db, commit to db worked");
        assertFalse(consumedFrom("scp_transacted_out"), "Nothing to to out q");

    }

    private boolean consumedFrom(String qName) throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(vmUri());
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
        transactionManager = getMandatoryBean(JtaTransactionManager.class, "jtaTransactionManager")
                .getTransactionManager();
    }

    private void sendJMSMessageToKickOffRoute() throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(vmUri());
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

    @Override
    protected Map<String, String> getTranslationProperties() {
        Map<String, String> map = super.getTranslationProperties();
        map.put("brokerUri", service.serviceAddress());
        return map;
    }

    public static class MarkRollbackOnly {
        public String enrich(Exchange exchange) throws Exception {
            LOG.info("Got exchange: {}", exchange);
            LOG.info("Got message: {}", ((JmsMessage) exchange.getIn()).getJmsMessage());

            LOG.info("Current tx: {}", transactionManager.getTransaction());
            LOG.info("Marking rollback only...");
            transactionManager.getTransaction().setRollbackOnly();
            return "Some Text";
        }
    }
}
