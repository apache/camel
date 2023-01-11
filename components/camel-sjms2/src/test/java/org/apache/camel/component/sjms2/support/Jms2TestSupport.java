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
package org.apache.camel.component.sjms2.support;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;

import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.sjms.jms.DefaultDestinationCreationStrategy;
import org.apache.camel.component.sjms.jms.DestinationCreationStrategy;
import org.apache.camel.component.sjms2.Sjms2Component;
import org.apache.camel.component.sjms2.jms.Jms2ObjectFactory;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A support class that builds up and tears down an ActiveMQ Artemis instance to be used for unit testing.
 */
public class Jms2TestSupport extends CamelTestSupport {

    @RegisterExtension
    public ArtemisService service = ArtemisServiceFactory.createTCPAllProtocolsService();

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Produce
    protected ProducerTemplate template;
    private Connection connection;
    private Session session;
    private DestinationCreationStrategy destinationCreationStrategy = new DefaultDestinationCreationStrategy();

    @Override
    protected boolean useJmx() {
        return false;
    }

    @Override
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

    /*
     * @see org.apache.camel.test.junit5.CamelTestSupport#createCamelContext()
     * @return
     * @throws Exception
     */
    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory = getConnectionFactory();
        connection = connectionFactory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Sjms2Component component = new Sjms2Component();
        component.setConnectionFactory(connectionFactory);
        camelContext.addComponent("sjms2", component);
        return camelContext;
    }

    protected ConnectionFactory getConnectionFactory() throws Exception {
        final String protocol = System.getProperty("protocol", "CORE").toUpperCase();

        //Currently AMQP and HORENTQ don't operate in exactly the same way on artemis as OPENWIRE
        //and CORE so its not possible to write protocol agnostic tests but in the future releases
        //of artemis we may be able test against them in an agnostic way.
        switch (protocol) {
            case "OPENWIRE":
                return new ActiveMQConnectionFactory(service.serviceAddress());
            default:
                return ActiveMQJMSClient.createConnectionFactory(service.serviceAddress(), "test");
        }
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public Session getSession() {
        return session;
    }

    public MessageConsumer createQueueConsumer(String destination) throws Exception {
        return new Jms2ObjectFactory().createMessageConsumer(session,
                destinationCreationStrategy.createDestination(session, destination, false), null, false, null, true, false);
    }

    public MessageConsumer createTopicConsumer(String destination, String messageSelector) throws Exception {
        return new Jms2ObjectFactory().createMessageConsumer(session,
                destinationCreationStrategy.createDestination(session, destination, true), messageSelector, true, null, true,
                false);
    }
}
