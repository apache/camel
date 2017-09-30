/**
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

import java.util.Arrays;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.server.QueueQueryResult;
import org.apache.activemq.artemis.jms.server.config.ConnectionFactoryConfiguration;
import org.apache.activemq.artemis.jms.server.config.JMSConfiguration;
import org.apache.activemq.artemis.jms.server.config.JMSQueueConfiguration;
import org.apache.activemq.artemis.jms.server.config.impl.ConnectionFactoryConfigurationImpl;
import org.apache.activemq.artemis.jms.server.config.impl.JMSConfigurationImpl;
import org.apache.activemq.artemis.jms.server.config.impl.JMSQueueConfigurationImpl;
import org.apache.activemq.artemis.jms.server.embedded.EmbeddedJMS;
import org.apache.camel.CamelContext;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;

/**
 * A support class that builds up and tears down an ActiveMQ Artemis instance to be used
 * for unit testing.
 */
public class BaseJms2TestSupport extends CamelTestSupport {

    @Produce
    protected ProducerTemplate template;
    protected String brokerUri;
    protected int port;
    protected EmbeddedJMS broker;
    protected Connection connection;
    protected Session session;

    /**
     * Set up the Broker
     *
     * @see CamelTestSupport#doPreSetup()
     *
     * @throws Exception
     */
    @Override
    protected void doPreSetup() throws Exception {
        broker = new EmbeddedJMS();
        deleteDirectory("target/data");
        port = AvailablePortFinder.getNextAvailable(33333);
        brokerUri = "tcp://localhost:" + port;
        configureBroker(this.broker);
        startBroker();
    }

    protected void configureBroker(EmbeddedJMS broker) throws Exception {
        Configuration configuration = new ConfigurationImpl()
            .setPersistenceEnabled(false)
            .setJournalDirectory("target/data/journal")
            .setSecurityEnabled(false)
            .addAcceptorConfiguration("connector", brokerUri + "?protocols=CORE,AMQP")
            .addAcceptorConfiguration("vm", "vm://123")
            .addConnectorConfiguration("connector", new TransportConfiguration(NettyConnectorFactory.class.getName()));

        JMSConfiguration jmsConfig = new JMSConfigurationImpl();

        ConnectionFactoryConfiguration cfConfig = new ConnectionFactoryConfigurationImpl().setName("cf").setConnectorNames(
            Arrays.asList("connector")).setBindings("cf");
        jmsConfig.getConnectionFactoryConfigurations().add(cfConfig);

        JMSQueueConfiguration queueConfig = new JMSQueueConfigurationImpl().setName("queue1").setDurable(false).setBindings("queue/queue1");
        jmsConfig.getQueueConfigurations().add(queueConfig);

        JMSQueueConfiguration topicConfig = new JMSQueueConfigurationImpl().setName("foo").setDurable(true).setBindings("topic/foo");
        jmsConfig.getQueueConfigurations().add(topicConfig);

        broker.setConfiguration(configuration).setJmsConfiguration(jmsConfig);
    }

    private void startBroker() throws Exception {
        broker.start();
        log.info("Started Embedded JMS Server");
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        DefaultCamelContext dcc = (DefaultCamelContext)context;
        while (!dcc.isStopped()) {
            log.info("Waiting on the Camel Context to stop");
        }
        log.info("Closing JMS Session");
        if (getSession() != null) {
            getSession().close();
            setSession(null);
        }
        log.info("Closing JMS Connection");
        if (connection != null) {
            connection.stop();
            connection = null;
        }
        log.info("Stopping the ActiveMQ Broker");
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
        component.setConnectionFactory(connectionFactory);
        component.setClientId(getClientId());
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
