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

import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import jakarta.jms.Connection;
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
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;

/**
 * A support class that builds up and tears down an ActiveMQ instance to be used for unit testing.
 */
public class JmsTestSupport extends CamelTestSupport {
    @RegisterExtension
    public ArtemisService service = ArtemisServiceFactory.createSingletonVMService();

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

    /**
     * Set up the Broker
     */
    @Override
    protected void doPreSetup() throws Exception {
        deleteDirectory("target/activemq-data");
        properties = new Properties();
        final URL url = getClass().getResource("/test-options.properties");
        try (InputStream inStream = url.openStream()) {
            properties.load(inStream);
        }

        brokerUri = service.serviceAddress();
    }

    @Override
    protected boolean useJmx() {
        return false;
    }

    protected void setupFactoryExternal(ActiveMQConnectionFactory factory) {
        if (service.userName() != null) {
            factory.setUser(service.userName());
        }

        if (service.password() != null) {
            factory.setPassword(service.password());
        }
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        DefaultCamelContext dcc = (DefaultCamelContext) context;
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
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        connectionFactory = new ActiveMQConnectionFactory(brokerUri);

        setupFactoryExternal(connectionFactory);
        connection = connectionFactory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        if (addSjmsComponent) {
            SjmsComponent component = new SjmsComponent();
            component.setConnectionFactory(connectionFactory);
            camelContext.addComponent("sjms", component);
        }
        return camelContext;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public Session getSession() {
        return session;
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

    public void reconnect() throws Exception {
        reconnect(0);
    }

    public void reconnect(int waitingMillis) throws Exception {
        log.info("Closing JMS Session");
        getSession().close();
        log.info("Closing JMS Connection");
        connection.stop();
        log.info("Stopping the ActiveMQ Broker");
        service.restart();
        brokerUri = service.serviceAddress();
        Thread.sleep(waitingMillis);
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerUri);
        setupFactoryExternal(connectionFactory);
        connection = connectionFactory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    }
}
