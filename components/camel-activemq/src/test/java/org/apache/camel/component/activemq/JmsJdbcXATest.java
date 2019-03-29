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
import java.util.concurrent.Executors;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.sql.DataSource;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerPlugin;
import org.apache.activemq.broker.BrokerPluginSupport;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.ConnectionContext;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.TransactionId;
import org.apache.activemq.util.Wait;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * shows broker 'once only delivery' and recovery with XA
 */
public class JmsJdbcXATest extends CamelSpringTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(JmsJdbcXATest.class);
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
    public void testRecoveryCommit() throws Exception {
        java.sql.Connection jdbcConn = initDb();

        sendJMSMessageToKickOffRoute();
        LOG.info("waiting for route to kick in, it will kill the broker on first 2pc commit");
        // will be stopped by the plugin on first 2pc commit
        broker.waitUntilStopped();
        assertEquals("message in db, commit to db worked", 1, dumpDb(jdbcConn));

        LOG.info("Broker stopped, restarting...");
        broker = createBroker(false);
        broker.start();
        broker.waitUntilStarted();
        assertEquals("pending transactions", 1, broker.getBroker().getPreparedTransactions(null).length);

        // TM stays actively committing first message ack which won't get
        // redelivered - xa once only delivery
        LOG.info("waiting for recovery to complete");
        assertTrue("recovery complete in time", Wait.waitFor(new Wait.Condition() {
            @Override
            public boolean isSatisified() throws Exception {
                return broker.getBroker().getPreparedTransactions(null).length == 0;
            }
        }));
        // verify recovery complete
        assertEquals("recovery complete", 0, broker.getBroker().getPreparedTransactions(null).length);

        final java.sql.Connection freshConnection = getJDBCConnection();
        assertTrue("did not get replay", Wait.waitFor(new Wait.Condition() {
            @Override
            public boolean isSatisified() throws Exception {
                return 1 == dumpDb(freshConnection);
            }
        }));
        assertEquals("still one message in db", 1, dumpDb(freshConnection));

        // let once complete ok
        sendJMSMessageToKickOffRoute();

        assertTrue("got second message", Wait.waitFor(new Wait.Condition() {
            @Override
            public boolean isSatisified() throws Exception {
                return 2 == dumpDb(freshConnection);
            }
        }));
        assertEquals("two messages in db", 2, dumpDb(freshConnection));
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
            broker.setPlugins(new BrokerPlugin[] {new BrokerPluginSupport() {
                @Override
                public void commitTransaction(ConnectionContext context, TransactionId xid, boolean onePhase) throws Exception {
                    if (onePhase) {
                        super.commitTransaction(context, xid, onePhase);
                    } else {
                        // die before doing the commit
                        // so commit will hang as if reply is lost
                        context.setDontSendReponse(true);
                        Executors.newSingleThreadExecutor().execute(new Runnable() {
                            @Override
                            public void run() {
                                LOG.info("Stopping broker post commit...");
                                try {
                                    broker.stop();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }
                }
            }});
            broker.start();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start broker", e);
        }

        return new ClassPathXmlApplicationContext("org/apache/camel/component/activemq/jmsXajdbc.xml");
    }
}
