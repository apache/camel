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

import java.util.concurrent.TimeUnit;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.pool.PooledConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertNotNull;

public class ComplexRequestReplyTest {

    private static final Logger LOG = LoggerFactory.getLogger(ComplexRequestReplyTest.class);

    private BrokerService brokerA;
    private BrokerService brokerB;
    private CamelContext senderContext;
    private CamelContext brokerAContext;
    private CamelContext brokerBContext;
    
    private String brokerAUri;
    private String brokerBUri;

    private String connectionUri;

    private final String fromEndpoint = "direct:test";
    private final String toEndpoint = "activemq:queue:send";
    private final String brokerEndpoint = "activemq:send";

    @Before
    public void setUp() throws Exception {

        createBrokerA();
        brokerAUri = brokerA.getTransportConnectors().get(0).getPublishableConnectString();
        createBrokerB();
        brokerBUri = brokerB.getTransportConnectors().get(0).getPublishableConnectString();

        connectionUri = "failover:(" + brokerAUri + "," + brokerBUri + ")?randomize=false";
        senderContext = createSenderContext();
    }

    @After
    public void tearDown() throws Exception {
        try {
            shutdownBrokerA();
        } catch (Exception ex) {
        }

        try {
            shutdownBrokerB();
        } catch (Exception e) {
        }
    }

    @Test
    public void testSendThenFailoverThenSend() throws Exception {

        ProducerTemplate requester = senderContext.createProducerTemplate();
        LOG.info("*** Sending Request 1");
        String response = (String)requester.requestBody(fromEndpoint, "This is a request");
        assertNotNull(response != null);
        LOG.info("Got response: " + response);

        /**
         * You actually don't need to restart the broker, just wait long enough
         * and the next next send will take out a closed connection and
         * reconnect, and if you happen to hit the broker you weren't on last
         * time, then you will see the failure.
         */

        TimeUnit.SECONDS.sleep(20);

        /**
         * I restart the broker after the wait that exceeds the idle timeout
         * value of the PooledConnectionFactory to show that it doesn't matter
         * now as the older connection has already been closed.
         */
        LOG.info("Restarting Broker A now.");
        shutdownBrokerA();
        createBrokerA();

        LOG.info("*** Sending Request 2");
        response = (String)requester.requestBody(fromEndpoint, "This is a request");
        assertNotNull(response != null);
        LOG.info("Got response: " + response);
    }

    private CamelContext createSenderContext() throws Exception {

        ActiveMQConnectionFactory amqFactory = new ActiveMQConnectionFactory(connectionUri);
        amqFactory.setWatchTopicAdvisories(false);

        PooledConnectionFactory pooled = new PooledConnectionFactory(amqFactory);
        pooled.setMaxConnections(1);
        pooled.setMaximumActiveSessionPerConnection(500);
        // If this is not zero the connection could get closed and the request
        // reply can fail.
        pooled.setIdleTimeout(0);

        CamelContext camelContext = new DefaultCamelContext();
        ActiveMQComponent amqComponent = new ActiveMQComponent();
        amqComponent.getConfiguration().setConnectionFactory(pooled);
        camelContext.addComponent("activemq", amqComponent);
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fromEndpoint).inOut(toEndpoint);
            }
        });
        camelContext.start();

        return camelContext;
    }

    private void createBrokerA() throws Exception {
        brokerA = createBroker("brokerA");
        brokerAContext = createBrokerCamelContext("brokerA");
        brokerA.start();
        brokerA.waitUntilStarted();
    }

    private void shutdownBrokerA() throws Exception {
        try {
            brokerAContext.stop();
        } catch (Exception e) {
            brokerA.stop();
            brokerA.waitUntilStopped();
            brokerA = null;
        }
    }

    private void createBrokerB() throws Exception {
        brokerB = createBroker("brokerB");
        brokerBContext = createBrokerCamelContext("brokerB");
        brokerB.start();
        brokerB.waitUntilStarted();
    }

    private void shutdownBrokerB() throws Exception {
        try {
            brokerBContext.stop();
        } finally {
            brokerB.stop();
            brokerB.waitUntilStopped();
            brokerB = null;
        }
    }

    private BrokerService createBroker(String name) throws Exception {
        BrokerService service = new BrokerService();
        service.setPersistent(false);
        service.setUseJmx(false);
        service.setBrokerName(name);
        service.addConnector("tcp://localhost:0");

        return service;
    }

    private CamelContext createBrokerCamelContext(String brokerName) throws Exception {

        CamelContext camelContext = new DefaultCamelContext();
        camelContext.addComponent("activemq", ActiveMQComponent.activeMQComponent("vm://" + brokerName + "?create=false&waitForStart=10000"));
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(brokerEndpoint).setBody().simple("Returning ${body}").log("***Reply sent to ${header.JMSReplyTo} CoorId = ${header.JMSCorrelationID}");
            }
        });
        camelContext.start();
        return camelContext;
    }
}
