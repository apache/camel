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

import org.apache.activemq.pool.PooledConnectionFactory;
import org.apache.activemq.spring.ActiveMQConnectionFactory;
import org.apache.camel.Endpoint;
import org.apache.camel.component.jms.JmsConfiguration;
import org.apache.camel.component.jms.JmsConsumer;
import org.apache.camel.component.jms.JmsEndpoint;
import org.apache.camel.component.jms.JmsProducer;
import org.apache.camel.support.processor.CamelLogProcessor;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.springframework.jms.connection.SingleConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.AbstractMessageListenerContainer;

/**
 * 
 */
public class ActiveMQConfigureTest extends CamelTestSupport {

    @Test
    public void testJmsTemplateUsesPoolingConnectionFactory() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("activemq:test.foo");
        JmsProducer producer = (JmsProducer)endpoint.createProducer();

        JmsTemplate template = assertIsInstanceOf(JmsTemplate.class, producer.getInOutTemplate());
        assertEquals("pubSubDomain", false, template.isPubSubDomain());
        assertIsInstanceOf(PooledConnectionFactory.class, template.getConnectionFactory());
    }

    @Test
    public void testJmsTemplateUsesSingleConnectionFactory() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("activemq:test.foo?useSingleConnection=true");
        JmsProducer producer = (JmsProducer)endpoint.createProducer();

        JmsTemplate template = assertIsInstanceOf(JmsTemplate.class, producer.getInOutTemplate());
        assertEquals("pubSubDomain", false, template.isPubSubDomain());
        SingleConnectionFactory connectionFactory = assertIsInstanceOf(SingleConnectionFactory.class, template.getConnectionFactory());
        assertIsInstanceOf(ActiveMQConnectionFactory.class, connectionFactory.getTargetConnectionFactory());
    }

    @Test
    public void testSessionTransactedWithoutTransactionManager() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("activemq:test.foo?transacted=true&lazyCreateTransactionManager=false");
        JmsConfiguration configuration = endpoint.getConfiguration();

        assertIsInstanceOf(ActiveMQConfiguration.class, configuration);

        assertTrue("The JMS sessions are not transacted!", endpoint.isTransacted());
        assertTrue("The JMS sessions are not transacted!", configuration.isTransacted());

        assertNull("A transaction manager has been lazy-created!", endpoint.getTransactionManager());
        assertNull("A transaction manager has been lazy-created!", configuration.getTransactionManager());
    }

    @Test
    public void testJmsTemplateDoesNotUsePoolingConnectionFactory() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("activemq:test.foo?usePooledConnection=false");
        JmsProducer producer = (JmsProducer)endpoint.createProducer();

        JmsTemplate template = assertIsInstanceOf(JmsTemplate.class, producer.getInOutTemplate());
        assertEquals("pubSubDomain", false, template.isPubSubDomain());
        assertIsInstanceOf(ActiveMQConnectionFactory.class, template.getConnectionFactory());
    }

    @Test
    public void testListenerContainerUsesSpringConnectionFactory() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("activemq:topic:test.foo");
        JmsConsumer consumer = endpoint.createConsumer(new CamelLogProcessor());

        AbstractMessageListenerContainer listenerContainer = consumer.getListenerContainer();
        assertEquals("pubSubDomain", true, listenerContainer.isPubSubDomain());
        assertIsInstanceOf(PooledConnectionFactory.class, listenerContainer.getConnectionFactory());
    }

    @Override
    protected JmsEndpoint resolveMandatoryEndpoint(String uri) {
        Endpoint endpoint = super.resolveMandatoryEndpoint(uri);
        return assertIsInstanceOf(JmsEndpoint.class, endpoint);
    }
}
