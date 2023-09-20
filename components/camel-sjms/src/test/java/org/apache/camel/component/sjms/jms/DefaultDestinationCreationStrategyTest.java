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
package org.apache.camel.component.sjms.jms;

import jakarta.jms.Connection;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TemporaryQueue;
import jakarta.jms.TemporaryTopic;
import jakarta.jms.Topic;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.infra.core.impl.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DefaultDestinationCreationStrategyTest extends CamelTestSupport {
    private DestinationCreationStrategy strategy = new DefaultDestinationCreationStrategy();

    protected ActiveMQConnectionFactory connectionFactory;

    protected Session session;

    @RegisterExtension
    public static ArtemisService service = ArtemisServiceFactory.createSingletonVMService();

    @Test
    public void testQueueCreation() throws Exception {
        Queue destination = (Queue) strategy.createDestination(session, "queue://test", false);
        assertNotNull(destination);
        assertEquals("test", destination.getQueueName());

        destination = (Queue) strategy.createDestination(session, "queue:test", false);
        assertNotNull(destination);
        assertEquals("test", destination.getQueueName());

        destination = (Queue) strategy.createDestination(session, "test", false);
        assertNotNull(destination);
        assertEquals("test", destination.getQueueName());
    }

    @Test
    public void testTopicCreation() throws Exception {
        Topic destination = (Topic) strategy.createDestination(session, "topic://test", true);
        assertNotNull(destination);
        assertEquals("test", destination.getTopicName());

        destination = (Topic) strategy.createDestination(session, "topic:test", true);
        assertNotNull(destination);
        assertEquals("test", destination.getTopicName());

        destination = (Topic) strategy.createDestination(session, "test", true);
        assertNotNull(destination);
        assertEquals("test", destination.getTopicName());
    }

    @Test
    public void testTemporaryQueueCreation() throws Exception {
        TemporaryQueue destination = (TemporaryQueue) strategy.createTemporaryDestination(session, false);
        assertNotNull(destination);
        assertNotNull(destination.getQueueName());
    }

    @Test
    public void testTemporaryTopicCreation() throws Exception {
        TemporaryTopic destination = (TemporaryTopic) strategy.createTemporaryDestination(session, true);
        assertNotNull(destination);
        assertNotNull(destination.getTopicName());
    }

    @Override
    protected void configureCamelContext(CamelContext camelContext) throws Exception {
        connectionFactory = new ActiveMQConnectionFactory(service.serviceAddress());

        Connection connection = connectionFactory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        SjmsComponent component = new SjmsComponent();
        component.setConnectionFactory(connectionFactory);
        camelContext.addComponent("sjms", component);
    }
}
