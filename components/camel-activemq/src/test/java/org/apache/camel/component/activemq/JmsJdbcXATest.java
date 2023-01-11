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

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.Executors;

import jakarta.jms.Connection;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import javax.sql.DataSource;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerPlugin;
import org.apache.activemq.broker.BrokerPluginSupport;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.ConnectionContext;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.TransactionId;
import org.apache.activemq.util.Wait;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.activemq.support.ActiveMQSpringTestSupport;
import org.apache.camel.test.infra.activemq.services.ActiveMQEmbeddedService;
import org.apache.camel.test.infra.activemq.services.ActiveMQEmbeddedServiceBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.util.AssertionErrors.assertTrue;

/**
 * shows broker 'once only delivery' and recovery with XA
 */
public class JmsJdbcXATest extends ActiveMQSpringTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(JmsJdbcXATest.class);
    BrokerService broker;
    int messageCount;

    @TempDir
    Path dataDirectory;

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
    public void testRecoveryCommit() throws Exception {
        java.sql.Connection jdbcConn = initDb();

        sendJMSMessageToKickOffRoute();
        LOG.info("waiting for route to kick in, it will kill the broker on first 2pc commit");
        // will be stopped by the plugin on first 2pc commit
        broker.waitUntilStopped();
        assertEquals(1, dumpDb(jdbcConn), "message in db, commit to db worked");

        LOG.info("Broker stopped, restarting...");
        BrokerService oldBroker = broker;

        broker = ActiveMQEmbeddedServiceBuilder
                .defaultBroker()
                .withDeleteAllMessagesOnStartup(false)
                .withBrokerName(JmsJdbcXATest.class)
                .withDataDirectory(dataDirectory)
                .build()
                .getBrokerService();

        broker.addConnector(ActiveMQEmbeddedService.getBrokerUri(oldBroker, 0));
        broker.start();
        broker.waitUntilStarted();
        assertEquals(1, broker.getBroker().getPreparedTransactions(null).length, "pending transactions");

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
        assertEquals(0, broker.getBroker().getPreparedTransactions(null).length, "recovery complete");

        final java.sql.Connection freshConnection = getJDBCConnection();
        assertTrue("did not get replay", Wait.waitFor(new Wait.Condition() {
            @Override
            public boolean isSatisified() throws Exception {
                return 1 == dumpDb(freshConnection);
            }
        }));
        assertEquals(1, dumpDb(freshConnection), "still one message in db");

        // let once complete ok
        sendJMSMessageToKickOffRoute();

        assertTrue("got second message", Wait.waitFor(new Wait.Condition() {
            @Override
            public boolean isSatisified() throws Exception {
                return 2 == dumpDb(freshConnection);
            }
        }));
        assertEquals(2, dumpDb(freshConnection), "two messages in db");
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
        map.put("brokerUri", ActiveMQEmbeddedService.getBrokerUri(broker, 0));
        return map;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        // make broker available to recovery processing on app context start
        try {
            broker = ActiveMQEmbeddedServiceBuilder
                    .defaultBroker()
                    .withBrokerName(JmsJdbcXATest.class)
                    .withTcpTransport()
                    .withDataDirectory(dataDirectory)
                    .build()
                    .getBrokerService();

            broker.setPlugins(new BrokerPlugin[] { new BrokerPluginSupport() {
                @Override
                public void commitTransaction(ConnectionContext context, TransactionId xid, boolean onePhase)
                        throws Exception {
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
                                    LOG.warn("Failed to stop the broker: {}", e.getMessage(), e);
                                }
                            }
                        });
                    }
                }
            } });
            broker.start();
        } catch (Exception e) {
            throw new RuntimeCamelException("Failed to start broker", e);
        }

        return super.createApplicationContext();
    }
}
