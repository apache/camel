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
package org.apache.camel.component.jms;


import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ResolveEndpointFailedException;
import org.springframework.jms.connection.UserCredentialsConnectionFactoryAdapter;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import static org.apache.camel.component.jms.JmsComponent.jmsComponentClientAcknowledge;



/**
 * @version $Revision$
 */
public class JmsEndpointConfigurationTest extends ContextTestSupport {
    private Processor dummyProcessor = new Processor() {
        public void process(Exchange exchange) throws Exception {
            log.info("Received: " + exchange);
        }
    };

    public void testDurableSubscriberConfiguredWithDoubleSlash() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms://topic:Foo.Bar?durableSubscriptionName=James&clientId=ABC");
        assertDurableSubscriberEndpointIsValid(endpoint);
    }

    public void testDurableSubscriberConfiguredWithNoSlashes() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:topic:Foo.Bar?durableSubscriptionName=James&clientId=ABC");
        assertDurableSubscriberEndpointIsValid(endpoint);
    }
    
    public void testSetUsernameAndPassword() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:topic:Foo.Bar?username=James&password=ABC");
        ConnectionFactory cf = endpoint.getConfiguration().getConnectionFactory();
        assertNotNull("The connectionFactory should not be null", cf);
        assertTrue("The connectionFactory should be the instance of UserCredentialsConnectionFactoryAdapter",
                   cf instanceof UserCredentialsConnectionFactoryAdapter);        
    }
    
    public void testNotSetUsernameOrPassword() {
        try {
            JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:topic:Foo.Bar?username=James");
            fail("Expect the exception here");
        } catch (ResolveEndpointFailedException exception) {
            // Expect the exception here
        }
        
        try {
            JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:topic:Foo.Bar?password=ABC");
            fail("Expect the exception here");
        } catch (ResolveEndpointFailedException exception) {
            // Expect the exception here
        }
        
    }

    public void testSelector() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:Foo.Bar?selector=foo%3D'ABC'");
        JmsConsumer consumer = endpoint.createConsumer(dummyProcessor);

        AbstractMessageListenerContainer container = consumer.getListenerContainer();
        assertEquals("selector", "foo='ABC'", container.getMessageSelector());

        Object object = container.getMessageListener();
        EndpointMessageListener messageListener = assertIsInstanceOf(EndpointMessageListener.class, object);
        assertFalse("Should not have replyToDisabled", messageListener.isDisableReplyTo());
        assertFalse("Should not have isEagerLoadingOfProperties()", messageListener.isEagerLoadingOfProperties());
    }

    public void testConfigureMessageListener() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:Foo.Bar?disableReplyTo=true&eagerLoadingOfProperties=true");
        JmsConsumer consumer = endpoint.createConsumer(dummyProcessor);

        AbstractMessageListenerContainer container = consumer.getListenerContainer();
        Object object = container.getMessageListener();
        EndpointMessageListener messageListener = assertIsInstanceOf(EndpointMessageListener.class, object);
        assertTrue("Should have replyToDisabled", messageListener.isDisableReplyTo());
        assertTrue("Should have isEagerLoadingOfProperties()", messageListener.isEagerLoadingOfProperties());
    }


    public void testCacheConsumerEnabledForQueue() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:Foo.Bar");
        assertCacheLevel(endpoint, DefaultMessageListenerContainer.CACHE_CONSUMER);
    }

    public void testCacheConsumerEnabledForTopic() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:topic:Foo.Bar");
        assertCacheLevel(endpoint, DefaultMessageListenerContainer.CACHE_CONSUMER);
    }
    
    public void testReplyToPesistentDelivery() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:queue:Foo");
        endpoint.getConfiguration().setDeliveryPersistent(true);
        endpoint.getConfiguration().setReplyToDeliveryPersistent(false);
        JmsProducer producer = endpoint.createProducer();
        JmsConsumer consumer = endpoint.createConsumer(dummyProcessor);
        JmsOperations operations = consumer.getEndpointMessageListener().getTemplate();
        assertTrue(operations instanceof JmsTemplate);
        JmsTemplate template = (JmsTemplate)operations;
        assertTrue("Wrong delivery mode on reply template; expected  " 
                     + " DeliveryMode.NON_PERSISTENT but was DeliveryMode.PERSISTENT", 
                     template.getDeliveryMode() == DeliveryMode.NON_PERSISTENT);
    }

    protected void assertCacheLevel(JmsEndpoint endpoint, int expected) throws Exception {
        JmsConsumer consumer = endpoint.createConsumer(dummyProcessor);

        AbstractMessageListenerContainer container = consumer.getListenerContainer();
        DefaultMessageListenerContainer defaultContainer = assertIsInstanceOf(DefaultMessageListenerContainer.class, container);
        int cacheLevel = defaultContainer.getCacheLevel();
        assertEquals("CacheLevel", expected, cacheLevel);
    }

    protected void assertDurableSubscriberEndpointIsValid(JmsEndpoint endpoint) throws Exception {
        JmsConfiguration configuration = endpoint.getConfiguration();
        assertEquals("getDurableSubscriptionName()", "James", configuration.getDurableSubscriptionName());
        assertEquals("getClientId()", "ABC", configuration.getClientId());
        assertEquals("isDeliveryPersistent()", true, configuration.isDeliveryPersistent());

        JmsConsumer consumer = endpoint.createConsumer(new Processor() {
            public void process(Exchange exchange) throws Exception {
                log.info("Received: " + exchange);
            }
        });
        AbstractMessageListenerContainer listenerContainer = consumer.getListenerContainer();
        assertEquals("getDurableSubscriptionName()", "James", listenerContainer.getDurableSubscriptionName());
        assertEquals("getClientId()", "ABC", listenerContainer.getClientId());
        assertEquals("isSubscriptionDurable()", true, listenerContainer.isSubscriptionDurable());
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        camelContext.addComponent("jms", jmsComponentClientAcknowledge(connectionFactory));

        return camelContext;
    }
}
