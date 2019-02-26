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
package org.apache.camel.component.activemq;

import java.net.URI;
import java.util.Arrays;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.network.DiscoveryNetworkConnector;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CamelJmsRequestReplyNobTest extends CamelSpringTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(CamelJmsRequestReplyNobTest.class);

    BrokerService consumerBroker;
    BrokerService producerBroker;

    @Test
    public void testRoundTrip() throws Exception {
        Destination destination = getMandatoryBean(Destination.class, "consumeFrom");

        // lets create a message
        ConnectionFactory factoryCON = getMandatoryBean(ConnectionFactory.class, "CON");

        Connection consumerConnection = factoryCON.createConnection();
        consumerConnection.start();
        Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        LOG.info("Consuming from: " + destination);
        MessageConsumer consumer = consumerSession.createConsumer(destination);

        // lets create a message
        ConnectionFactory factoryPRO = getMandatoryBean(ConnectionFactory.class, "PRO");

        Connection producerConnection = factoryPRO.createConnection();
        producerConnection.start();
        Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        MessageProducer producer = producerSession.createProducer(producerSession.createQueue("incoming1"));
        Message message = producerSession.createTextMessage("Where are you");
        message.setStringProperty("foo", "bar");
        producer.send(message);

        message = consumer.receive(10000);
        assertNotNull("Should have received a message from destination: " + destination, message);

        TextMessage textMessage = assertIsInstanceOf(TextMessage.class, message);
        assertEquals("Message body", "If you don't ask me my name, I'm not going to tell you!", textMessage.getText());

    }

    private BrokerService createBroker(String name) throws Exception {
        BrokerService brokerService = new BrokerService();
        brokerService.setDeleteAllMessagesOnStartup(true);
        brokerService.setBrokerName(name);
        brokerService.setUseJmx(false);
        brokerService.setPersistent(false);
        brokerService.addConnector("tcp://0.0.0.0:0");
        return brokerService;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        try {
            consumerBroker = createBroker("CON");
            producerBroker = createBroker("PRO");
            DiscoveryNetworkConnector discoveryNetworkConnector = new DiscoveryNetworkConnector();
            discoveryNetworkConnector.setUri(new URI("static:" + consumerBroker.getTransportConnectorByScheme("tcp").getPublishableConnectString()));
            discoveryNetworkConnector.setDuplex(true);
            discoveryNetworkConnector.setNetworkTTL(2);
            discoveryNetworkConnector.setDynamicallyIncludedDestinations(Arrays.asList(new ActiveMQDestination[] {new ActiveMQQueue("service1")}));
            discoveryNetworkConnector.setDestinationFilter("ActiveMQ.Advisory.TempQueue,ActiveMQ.Advisory.TempTopic,ActiveMQ.Advisory.Consumer.Queue.>");
            producerBroker.addNetworkConnector(discoveryNetworkConnector);
            consumerBroker.start();
            producerBroker.start();

        } catch (Exception e) {
            throw new RuntimeException("Failed to start broker", e);
        }
        return new ClassPathXmlApplicationContext("org/apache/camel/component/activemq/requestReply.xml");
    }
}
