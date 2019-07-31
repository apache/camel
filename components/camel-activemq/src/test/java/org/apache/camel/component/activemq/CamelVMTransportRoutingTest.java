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

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import junit.framework.TestCase;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.TransportConnector;
import org.apache.activemq.util.ThreadTracker;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// see: https://issues.apache.org/activemq/browse/AMQ-2966
public class CamelVMTransportRoutingTest extends TestCase {

    private static final Logger LOG = LoggerFactory.getLogger(CamelVMTransportRoutingTest.class);

    private BrokerService broker;
    private TransportConnector connector;
    private CamelContext camelContext;

    private Connection senderConnection;
    private Connection receiverConnection1;
    private Connection receiverConnection2;

    private final String msgString = "MESSAGE-TEXT";
    private final String senderTopic = "A";
    private final String receiverTopic = "B";

    @SuppressWarnings("unused")
    public void testSendReceiveWithCamelRouteIntercepting() throws Exception {

        final int msgCount = 1000;

        Session sendSession = senderConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Session receiverSession1 = receiverConnection1.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Session receiverSession2 = receiverConnection2.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Destination sendTo = sendSession.createTopic(senderTopic);
        Destination receiveFrom = receiverSession1.createTopic(receiverTopic);

        TextMessage message = sendSession.createTextMessage(msgString);

        MessageConsumer receiver1 = receiverSession1.createConsumer(receiveFrom);
        MessageConsumer receiver2 = receiverSession2.createConsumer(receiveFrom);

        MessageProducer sender = sendSession.createProducer(sendTo);
        for (int i = 0; i < msgCount; ++i) {
            sender.send(message);
        }

        for (int i = 0; i < msgCount; ++i) {

            LOG.debug("Attempting Received for Message #" + i);
            TextMessage received1 = (TextMessage)receiver1.receive(5000);
            Assert.assertNotNull(received1);
            Assert.assertEquals(msgString, received1.getText());
        }
    }

    protected BrokerService createBroker() throws Exception {

        BrokerService service = new BrokerService();
        service.setPersistent(false);
        service.setUseJmx(false);
        connector = service.addConnector("tcp://localhost:0");

        return service;
    }

    @Override
    public void setUp() throws Exception {

        broker = createBroker();
        broker.start();
        broker.waitUntilStarted();

        Thread.sleep(1000);

        createCamelContext();

        ActiveMQConnectionFactory connFactory = new ActiveMQConnectionFactory(connector.getConnectUri());
        senderConnection = connFactory.createConnection();
        receiverConnection1 = connFactory.createConnection();
        receiverConnection2 = connFactory.createConnection();

        receiverConnection1.start();
        receiverConnection2.start();
    }

    @Override
    public void tearDown() throws Exception {

        if (senderConnection != null) {
            senderConnection.close();
        }

        if (receiverConnection1 != null) {
            receiverConnection1.close();
        }

        if (receiverConnection2 != null) {
            receiverConnection2.close();
        }

        camelContext.stop();
        broker.stop();

        ThreadTracker.result();
    }

    private void createCamelContext() throws Exception {

        final String fromEndpoint = "activemq:topic:" + senderTopic;
        final String toEndpoint = "activemq:topic:" + receiverTopic;

        LOG.info("creating context and sending message");
        camelContext = new DefaultCamelContext();
        camelContext.addComponent("activemq", ActiveMQComponent.activeMQComponent("vm://localhost?create=false&waitForStart=10000"));
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fromEndpoint).to(toEndpoint);
            }
        });
        camelContext.start();
    }

}
