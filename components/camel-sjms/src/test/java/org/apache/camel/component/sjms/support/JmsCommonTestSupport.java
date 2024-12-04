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
package org.apache.camel.component.sjms.support;

import java.io.IOException;
import java.util.Properties;

import jakarta.jms.Connection;
import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.component.sjms.jms.DefaultDestinationCreationStrategy;
import org.apache.camel.component.sjms.jms.DestinationCreationStrategy;
import org.apache.camel.component.sjms.jms.Jms11ObjectFactory;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.test.junit5.TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;

/**
 * A support class that builds up and tears down an ActiveMQ instance to be used for unit testing.
 */
public abstract class JmsCommonTestSupport extends CamelTestSupport {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected boolean addSjmsComponent = true;

    @Produce
    protected ProducerTemplate template;
    protected String brokerUri;
    protected Properties properties;
    protected ActiveMQConnectionFactory connectionFactory;

    private Connection connection;
    private Session session;
    private DestinationCreationStrategy destinationCreationStrategy = new DefaultDestinationCreationStrategy();

    @BeforeEach
    protected final void preTestCleanup() {
        deleteDirectory("target/activemq-data");
        properties = new Properties();
    }

    @BeforeEach
    protected void loadTestProperties() throws IOException {
        TestSupport.loadExternalProperties(properties, getClass(), "/test-options.properties");
    }

    protected abstract String getBrokerUri();

    protected void setupFactoryExternal(ActiveMQConnectionFactory factory, ArtemisService service) {
        if (service.userName() != null) {
            factory.setUser(service.userName());
        }

        if (service.password() != null) {
            factory.setPassword(service.password());
        }
    }

    protected abstract void setupFactoryExternal(ActiveMQConnectionFactory factory);

    @AfterEach
    public void closeSessions() throws JMSException {
        closeSession();
        closeConnection();
    }

    private void closeConnection() throws JMSException {
        log.info("Closing JMS Connection");
        if (connection != null) {
            connection.stop();
            connection = null;
        }
    }

    private void closeSession() throws JMSException {
        log.info("Closing JMS Session");
        if (getSession() != null) {
            getSession().close();
            setSession(null);
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        brokerUri = getBrokerUri();

        connectionFactory = new ActiveMQConnectionFactory(brokerUri);

        setupFactoryExternal(connectionFactory);
        connect();
        if (addSjmsComponent) {
            SjmsComponent component = new SjmsComponent();
            component.setConnectionFactory(connectionFactory);
            camelContext.addComponent("sjms", component);
        }
        return camelContext;
    }

    protected void connect() throws JMSException {
        connection = connectionFactory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public Session getSession() {
        return session;
    }

    public Connection getConnection() {
        return connection;
    }

    public MessageConsumer createQueueConsumer(String destination) throws Exception {
        return new Jms11ObjectFactory().createMessageConsumer(session,
                destinationCreationStrategy.createDestination(session, destination, false), null, false, null, true, false);
    }

    public MessageConsumer createTopicConsumer(String destination, String messageSelector) throws Exception {
        return new Jms11ObjectFactory().createMessageConsumer(session,
                destinationCreationStrategy.createDestination(session, destination, true), messageSelector, true, null, true,
                false);
    }

}
