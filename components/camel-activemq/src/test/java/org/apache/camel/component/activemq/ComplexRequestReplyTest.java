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
import org.apache.activemq.pool.PooledConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.activemq.support.ActiveMQSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.activemq.services.ActiveMQEmbeddedService;
import org.apache.camel.test.infra.activemq.services.ActiveMQEmbeddedServiceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ComplexRequestReplyTest implements ActiveMQSupport {

    @RegisterExtension
    public static ActiveMQEmbeddedService serviceA = ActiveMQEmbeddedServiceBuilder.defaultBroker()
            .withBrokerName(ComplexRequestReplyTest.class, "brokerA")
            .withPersistent(false)
            .withAdvisorySupport(true)
            .withTcpTransport()
            .build();

    @RegisterExtension
    public static ActiveMQEmbeddedService serviceB = ActiveMQEmbeddedServiceBuilder.defaultBroker()
            .withBrokerName(ComplexRequestReplyTest.class, "brokerB")
            .withDeleteAllMessagesOnStartup(false)
            .withPersistent(false)
            .withAdvisorySupport(true)
            .withTcpTransport()
            .build();

    private static final Logger LOG = LoggerFactory.getLogger(ComplexRequestReplyTest.class);

    private CamelContext senderContext;

    private String connectionUri;
    private final String fromEndpoint = "direct:test";
    private final String toEndpoint = "activemq:queue:send";

    @BeforeEach
    public void setUp() throws Exception {
        String brokerAUri = serviceA.serviceAddress();
        createBrokerCamelContext("brokerA");

        String brokerBUri = serviceB.serviceAddress();
        createBrokerCamelContext("brokerB");

        connectionUri = "failover:(" + brokerAUri + "," + brokerBUri + ")?randomize=false";
        senderContext = createSenderContext();
    }

    @Test
    public void testSendThenFailoverThenSend() throws Exception {

        ProducerTemplate requester = senderContext.createProducerTemplate();
        LOG.info("*** Sending Request 1");
        String response = (String) requester.requestBody(fromEndpoint, "This is a request");
        assertNotNull(response);
        LOG.info("Got response: {}", response);

        /**
         * You actually don't need to restart the broker, just wait long enough and the next next send will take out a
         * closed connection and reconnect, and if you happen to hit the broker you weren't on last time, then you will
         * see the failure.
         */

        TimeUnit.SECONDS.sleep(2);

        /**
         * I restart the broker after the wait that exceeds the idle timeout value of the PooledConnectionFactory to
         * show that it doesn't matter now as the older connection has already been closed.
         */
        LOG.info("Restarting Broker A now.");

        String prevUri = serviceA.serviceAddress();

        serviceA.shutdown();
        serviceA = ActiveMQEmbeddedServiceBuilder.defaultBroker()
                .withBrokerName(ComplexRequestReplyTest.class, "brokerA")
                .withPersistent(false)
                .withAdvisorySupport(true)
                //                .withTcpTransport()
                .build();
        serviceA.getBrokerService().addConnector(prevUri);

        LOG.info("*** Sending Request 2");
        response = (String) requester.requestBody(fromEndpoint, "This is a request");
        assertNotNull(response);
        LOG.info("Got response: {}", response);
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
            public void configure() {
                from(fromEndpoint).to(ExchangePattern.InOut, toEndpoint);
            }
        });
        camelContext.start();

        return camelContext;
    }

    private CamelContext createBrokerCamelContext(String brokerName) throws Exception {
        final String brokerEndpoint = "activemq:send";

        CamelContext camelContext = new DefaultCamelContext();
        camelContext.addComponent("activemq",
                ActiveMQComponent.activeMQComponent(vmUri(brokerName + "?create=false&waitForStart=1000")));
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(brokerEndpoint).setBody().simple("Returning ${body}")
                        .log("***Reply sent to ${header.JMSReplyTo} CoorId = ${header.JMSCorrelationID}");
            }
        });
        camelContext.start();
        return camelContext;
    }
}
