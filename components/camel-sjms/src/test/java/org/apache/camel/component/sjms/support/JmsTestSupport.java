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
package org.apache.camel.component.sjms.support;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.jmx.DestinationViewMBean;
import org.apache.camel.CamelContext;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.component.sjms.jms.DefaultDestinationCreationStrategy;
import org.apache.camel.component.sjms.jms.DestinationCreationStrategy;
import org.apache.camel.component.sjms.jms.Jms11ObjectFactory;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;


/**
 * A support class that builds up and tears down an ActiveMQ instance to be used
 * for unit testing.
 */
public class JmsTestSupport extends CamelTestSupport {

    @Produce
    protected ProducerTemplate template;
    protected String brokerUri;
    private BrokerService broker;
    private Connection connection;
    private Session session;
    private DestinationCreationStrategy destinationCreationStrategy = new DefaultDestinationCreationStrategy();

    /** 
     * Set up the Broker
     *
     * @see org.apache.camel.test.junit4.CamelTestSupport#doPreSetup()
     *
     * @throws Exception
     */
    @Override
    protected void doPreSetup() throws Exception {
        deleteDirectory("target/activemq-data");
        broker = new BrokerService();
        int port = AvailablePortFinder.getNextAvailable(33333);
        brokerUri = "tcp://localhost:" + port;
        broker.getManagementContext().setConnectorPort(AvailablePortFinder.getNextAvailable(port + 1));
        configureBroker(broker);
        startBroker();
    }

    protected void configureBroker(BrokerService broker) throws Exception {
        broker.setUseJmx(true);
        broker.setPersistent(false);
        broker.deleteAllMessages();
        broker.addConnector(brokerUri);
    }

    private void startBroker() throws Exception {
        broker.start();
        broker.waitUntilStarted();
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

    /*
     * @see org.apache.camel.test.junit4.CamelTestSupport#createCamelContext()
     * @return
     * @throws Exception
     */
    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerUri);
        connection = connectionFactory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        SjmsComponent component = new SjmsComponent();
        component.setConnectionCount(1);
        component.setConnectionFactory(connectionFactory);
        camelContext.addComponent("sjms", component);
        return camelContext;
    }

    public DestinationViewMBean getQueueMBean(String queueName) throws MalformedObjectNameException {
        return getDestinationMBean(queueName, false);
    }
    public DestinationViewMBean getDestinationMBean(String destinationName, boolean topic) throws MalformedObjectNameException {
        String domain = "org.apache.activemq";
        String destinationType = topic ? "Topic" : "Queue";
        ObjectName name = new ObjectName(String.format("%s:type=Broker,brokerName=localhost,destinationType=%s,destinationName=%s",
                domain, destinationType, destinationName));
        return (DestinationViewMBean) broker.getManagementContext().newProxyInstance(name, DestinationViewMBean.class, true);
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public Session getSession() {
        return session;
    }

    public MessageConsumer createQueueConsumer(String destination) throws Exception {
        return new Jms11ObjectFactory().createMessageConsumer(session, destinationCreationStrategy.createDestination(session, destination, false), null, false, null, true, false);
    }

    public MessageConsumer createTopicConsumer(String destination, String messageSelector) throws Exception {
        return new Jms11ObjectFactory().createMessageConsumer(session, destinationCreationStrategy.createDestination(session, destination, true), messageSelector, true, null, true, false);
    }
}
