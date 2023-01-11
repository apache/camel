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
package org.apache.camel.itest.jms2;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.Topic;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.server.QueueQueryResult;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.camel.CamelContext;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;

/**
 * A support class that builds up and tears down an ActiveMQ Artemis instance to be used for unit testing.
 */
public class BaseJms2TestSupport extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(BaseJms2TestSupport.class);

    @Produce
    protected ProducerTemplate template;
    protected String brokerUri;
    protected int port;
    protected EmbeddedActiveMQ broker;
    protected Connection connection;
    protected Session session;

    /**
     * Set up the Broker
     *
     * @see              CamelTestSupport#doPreSetup()
     *
     * @throws Exception
     */
    @Override
    protected void doPreSetup() throws Exception {
        broker = new EmbeddedActiveMQ();
        deleteDirectory("target/data");
        port = AvailablePortFinder.getNextAvailable();
        brokerUri = "tcp://localhost:" + port;
        configureBroker(this.broker);
        startBroker();
    }

    protected void configureBroker(EmbeddedActiveMQ broker) throws Exception {
        Configuration configuration = new ConfigurationImpl()
                .setPersistenceEnabled(false)
                .setJournalDirectory("target/data/journal")
                .setSecurityEnabled(false)
                .addAcceptorConfiguration("connector", brokerUri + "?protocols=CORE,AMQP")
                .addAcceptorConfiguration("vm", "vm://123")
                .addConnectorConfiguration("connector", new TransportConfiguration(NettyConnectorFactory.class.getName()));

        broker.setConfiguration(configuration);
    }

    private void startBroker() throws Exception {
        broker.start();
        LOG.info("Started Embedded JMS Server");
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        DefaultCamelContext dcc = (DefaultCamelContext) context;
        while (!dcc.isStopped()) {
            LOG.info("Waiting on the Camel Context to stop");
        }
        LOG.info("Closing JMS Session");
        if (getSession() != null) {
            getSession().close();
            setSession(null);
        }
        LOG.info("Closing JMS Connection");
        if (connection != null) {
            connection.stop();
            connection = null;
        }
        LOG.info("Stopping the ActiveMQ Broker");
        if (broker != null) {
            broker.stop();
            broker = null;
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory = getConnectionFactory();
        connection = connectionFactory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        JmsComponent component = new JmsComponent();
        component.getConfiguration().setConnectionFactory(connectionFactory);
        component.getConfiguration().setClientId(getClientId());
        camelContext.addComponent("jms", component);
        return camelContext;
    }

    protected String getClientId() {
        return null;
    }

    protected ConnectionFactory getConnectionFactory() throws Exception {
        return ActiveMQJMSClient.createConnectionFactory(brokerUri, "test");
    }

    public QueueQueryResult getQueueQueryResult(String queueQuery) throws Exception {
        return broker.getActiveMQServer().queueQuery(new SimpleString(queueQuery));
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public Session getSession() {
        return session;
    }

    public MessageConsumer createQueueConsumer(String destination) throws Exception {
        Queue queue = session.createQueue(destination);
        return session.createConsumer(queue);
    }

    public MessageConsumer createTopicConsumer(String destination, String messageSelector) throws Exception {
        Topic topic = session.createTopic(destination);
        return session.createConsumer(topic, messageSelector);
    }

}
