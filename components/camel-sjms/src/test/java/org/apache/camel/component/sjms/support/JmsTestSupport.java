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
import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.component.sjms.jms.DefaultDestinationCreationStrategy;
import org.apache.camel.component.sjms.jms.DestinationCreationStrategy;
import org.apache.camel.component.sjms.jms.Jms11ObjectFactory;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.TransientCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.impl.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;

/**
 * A support class that builds up and tears down an ActiveMQ instance to be used for unit testing.
 */
//@ContextFixture
public class JmsTestSupport extends CamelTestSupport {

    protected static ActiveMQConnectionFactory connectionFactory;
    protected static Session session;

    @RegisterExtension
    public static ArtemisService service = ArtemisServiceFactory.createSingletonVMService();

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected boolean addSjmsComponent = true;

    protected ConsumerTemplate consumer;

    protected ProducerTemplate template;
    protected String brokerUri;
    protected Properties properties;

    private Connection connection;
    private DestinationCreationStrategy destinationCreationStrategy = new DefaultDestinationCreationStrategy();

    private static int counter;

    /**
     * Set up the Broker
     */
/*    @Override
    protected void doPreSetup() throws Exception {
        deleteDirectory("target/activemq-data");
        properties = new Properties();
        final URL url = getClass().getResource("/test-options.properties");
        try (InputStream inStream = url.openStream()) {
            properties.load(inStream);
        }
        brokerUri = service.serviceAddress();
    }*/

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

    @AfterEach
    public void tearDown() throws Exception {
        //super.tearDown();
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

    public void setSession(Session session) {
        JmsTestSupport.session = session;
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
    //@BeforeEach
/*    @Override
    protected void configureCamelContext(CamelContext camelContext) throws Exception {
        connectionFactory = new ActiveMQConnectionFactory(service.serviceAddress());

        Connection connection = connectionFactory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        SjmsComponent component = new SjmsComponent();
        component.setConnectionFactory(connectionFactory);
        camelContext.addComponent("sjms", component);
    }*/
/*    @Override
    @ContextFixture
    protected void configureCamelContext(CamelContext camelContext) throws Exception {
*//*        counter++;
        System.out.println(counter);*//*
        configureJMSCamelContext(camelContext);
    }*/

    @Override
    @ContextFixture
    protected synchronized void configureCamelContext(CamelContext context) throws Exception {
        configureJMSCamelContext(context);
    }

    //@ContextFixture
    protected static void configureJMSCamelContext(CamelContext camelContext) throws Exception {
        connectionFactory = new ActiveMQConnectionFactory(service.serviceAddress());

        Connection connection = connectionFactory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        SjmsComponent component = new SjmsComponent();
        component.setConnectionFactory(connectionFactory);
        camelContext.addComponent("sjms", component);
    }
}
