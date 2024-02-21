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
package org.apache.camel.component.sjms.producer;

import jakarta.jms.Connection;
import jakarta.jms.Session;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.component.sjms.jms.DefaultDestinationCreationStrategy;
import org.apache.camel.component.sjms.jms.DestinationCreationStrategy;
import org.apache.camel.test.infra.artemis.services.ArtemisEmbeddedServiceBuilder;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class QueueProducerQoSTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(QueueProducerQoSTest.class);

    private static final String TEST_INONLY_DESTINATION_NAME = "queue.producer.test.qos.inonly.QueueProducerQoSTest";
    private static final String TEST_INOUT_DESTINATION_NAME = "queue.producer.test.qos.inout.QueueProducerQoSTest";

    private static final String EXPIRED_MESSAGE_ROUTE_ID = "expiredAdvisoryRoute";
    private static final String MOCK_EXPIRED_ADVISORY = "mock:expiredAdvisory";

    @RegisterExtension
    public ArtemisService service = new ArtemisEmbeddedServiceBuilder()
            .withPersistent(true)
            .withCustomConfiguration(configuration -> configureArtemis(configuration))
            .build();

    protected ActiveMQConnectionFactory connectionFactory;

    @EndpointInject(MOCK_EXPIRED_ADVISORY)
    MockEndpoint mockExpiredAdvisory;

    private Connection connection;
    private Session session;
    private DestinationCreationStrategy destinationCreationStrategy = new DefaultDestinationCreationStrategy();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        connectionFactory = new ActiveMQConnectionFactory(service.serviceAddress());

        connection = connectionFactory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        SjmsComponent component = new SjmsComponent();
        component.setConnectionFactory(connectionFactory);
        camelContext.addComponent("sjms", component);

        return camelContext;
    }

    @Test
    public void testInOutQueueProducerTTL() throws Exception {
        mockExpiredAdvisory.expectedMessageCount(1);

        String endpoint = String.format("sjms:queue:%s?timeToLive=1000&exchangePattern=InOut&requestTimeout=500",
                TEST_INOUT_DESTINATION_NAME);

        try {
            template.requestBody(endpoint, "test message");
            fail("we aren't expecting any consumers, so should not succeed");
        } catch (Exception e) {
            // we are expecting an exception here because there are no consumers on this queue,
            // so we will not be able to do a real InOut/request-response, but that's okay
            // we're just interested in the message becoming expired
        }

        MockEndpoint.assertIsSatisfied(context);

        assertEquals(0, service.countMessages(TEST_INOUT_DESTINATION_NAME),
                "There were unexpected messages left in the queue: " + TEST_INOUT_DESTINATION_NAME);
    }

    @Test
    public void testInOnlyQueueProducerTTL() throws Exception {
        mockExpiredAdvisory.expectedMessageCount(1);

        String endpoint = String.format("sjms:queue:%s?timeToLive=1000", TEST_INONLY_DESTINATION_NAME);
        template.sendBody(endpoint, "test message");

        MockEndpoint.assertIsSatisfied(context);

        assertEquals(0, service.countMessages(TEST_INONLY_DESTINATION_NAME),
                "There were unexpected messages left in the queue: " + TEST_INONLY_DESTINATION_NAME);
    }

    private static Configuration configureArtemis(Configuration configuration) {
        return configuration.addAddressSetting("#",
                new AddressSettings()
                        .setDeadLetterAddress(SimpleString.toSimpleString("DLQ"))
                        .setExpiryAddress(SimpleString.toSimpleString("ExpiryQueue"))
                        .setExpiryDelay(1000L))
                .setMessageExpiryScanPeriod(500L);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("sjms:topic:ExpiryQueue")
                        .routeId(EXPIRED_MESSAGE_ROUTE_ID)
                        .log("Expired message")
                        .to(MOCK_EXPIRED_ADVISORY);
            }
        };
    }
}
