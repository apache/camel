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
import javax.jms.ExceptionListener;
import javax.jms.JMSException;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.processor.CamelLogger;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.springframework.jms.connection.UserCredentialsConnectionFactoryAdapter;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.support.converter.SimpleMessageConverter;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * @version 
 */
public class JmsEndpointConfigurationTest extends CamelTestSupport {

    private Processor dummyProcessor = new Processor() {
        public void process(Exchange exchange) throws Exception {
            log.info("Received: " + exchange);
        }
    };

    @Test
    public void testDurableSubscriberConfiguredWithDoubleSlash() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms://topic:Foo.Bar?durableSubscriptionName=James&clientId=ABC");
        assertDurableSubscriberEndpointIsValid(endpoint);
    }

    @Test
    public void testDurableSubscriberConfiguredWithNoSlashes() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:topic:Foo.Bar?durableSubscriptionName=James&clientId=ABC");
        assertDurableSubscriberEndpointIsValid(endpoint);
    }
 
    @Test
    public void testSetUsernameAndPassword() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:topic:Foo.Bar?username=James&password=ABC");
        ConnectionFactory cf = endpoint.getConfiguration().getConnectionFactory();
        assertNotNull("The connectionFactory should not be null", cf);
        assertTrue("The connectionFactory should be the instance of UserCredentialsConnectionFactoryAdapter",
                   cf instanceof UserCredentialsConnectionFactoryAdapter);        
    }
 
    @Test
    public void testNotSetUsernameOrPassword() {
        try {
            resolveMandatoryEndpoint("jms:topic:Foo.Bar?username=James");
            fail("Expect the exception here");
        } catch (ResolveEndpointFailedException exception) {
            // Expect the exception here
        }
        
        try {
            resolveMandatoryEndpoint("jms:topic:Foo.Bar?password=ABC");
            fail("Expect the exception here");
        } catch (ResolveEndpointFailedException exception) {
            // Expect the exception here
        }
        
    }

    @Test
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

    @Test
    public void testConfigureMessageListener() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:Foo.Bar?disableReplyTo=true&eagerLoadingOfProperties=true");
        JmsConsumer consumer = endpoint.createConsumer(dummyProcessor);

        AbstractMessageListenerContainer container = consumer.getListenerContainer();
        Object object = container.getMessageListener();
        EndpointMessageListener messageListener = assertIsInstanceOf(EndpointMessageListener.class, object);
        assertTrue("Should have replyToDisabled", messageListener.isDisableReplyTo());
        assertTrue("Should have isEagerLoadingOfProperties()", messageListener.isEagerLoadingOfProperties());
    }

    @Test
    public void testCacheConsumerEnabledForQueue() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:Foo.Bar");
        assertCacheLevel(endpoint, DefaultMessageListenerContainer.CACHE_AUTO);
    }

    @Test
    public void testCacheConsumerEnabledForTopic() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:topic:Foo.Bar");
        assertCacheLevel(endpoint, DefaultMessageListenerContainer.CACHE_AUTO);
    }

    @Test
    public void testReplyToPesistentDelivery() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:queue:Foo");
        endpoint.getConfiguration().setDeliveryPersistent(true);
        endpoint.getConfiguration().setReplyToDeliveryPersistent(false);
        Producer producer = endpoint.createProducer();
        assertNotNull("The producer should not be null", producer);
        JmsConsumer consumer = endpoint.createConsumer(dummyProcessor);
        JmsOperations operations = consumer.getEndpointMessageListener().getTemplate();
        assertTrue(operations instanceof JmsTemplate);
        JmsTemplate template = (JmsTemplate)operations;
        assertTrue("Wrong delivery mode on reply template; expected  " 
                     + " DeliveryMode.NON_PERSISTENT but was DeliveryMode.PERSISTENT", 
                     template.getDeliveryMode() == DeliveryMode.NON_PERSISTENT);
    }

    @Test
    public void testMaxConcurrentConsumers() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:queue:Foo?maxConcurrentConsumers=5");
        assertEquals(5, endpoint.getMaxConcurrentConsumers());
    }

    @Test
    public void testInvalidMaxConcurrentConsumers() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:queue:Foo?concurrentConsumers=5&maxConcurrentConsumers=2");
        try {
            endpoint.createConsumer(new CamelLogger());
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Property maxConcurrentConsumers: 2 must be higher than concurrentConsumers: 5", e.getMessage());
        }
    }

    @Test
    public void testConcurrentConsumers() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:queue:Foo?concurrentConsumers=4");
        assertEquals(4, endpoint.getConcurrentConsumers());
    }

    @Test
    public void testIdleTaskExecutionLimit() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:queue:Foo?idleTaskExecutionLimit=50");
        assertEquals(50, endpoint.getIdleTaskExecutionLimit());
        assertEquals(true, endpoint.isAutoStartup());
    }

    @Test
    public void testLazyCreateTransactionManager() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:queue:Foo?lazyCreateTransactionManager=true");
        assertEquals(true, endpoint.getConfiguration().isLazyCreateTransactionManager());
    }

    @Test
    public void testDefaultEndpointOptions() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:queue:Foo");

        assertNotNull(endpoint.getBinding());
        assertNotNull(endpoint.getCamelContext());
        assertEquals(-1, endpoint.getRecoveryInterval());
        assertEquals(-1, endpoint.getTimeToLive());
        assertEquals(-1, endpoint.getTransactionTimeout());
        assertEquals(null, endpoint.getAcknowledgementModeName());
        assertEquals(1, endpoint.getAcknowledgementMode());
        assertEquals(-1, endpoint.getCacheLevel());
        assertEquals(null, endpoint.getCacheLevelName());
        assertNotNull(endpoint.getCamelId());
        assertEquals(null, endpoint.getClientId());
        assertNotNull(endpoint.getConnectionFactory());
        assertEquals(1, endpoint.getConcurrentConsumers());
        assertNull(endpoint.getDestination());
        assertEquals("Foo", endpoint.getDestinationName());
        assertNull(endpoint.getDestinationResolver());
        assertEquals(null, endpoint.getDurableSubscriptionName());
        assertEquals("jms://queue:Foo", endpoint.getEndpointKey());
        assertEquals("jms://queue:Foo", endpoint.getEndpointUri());
        assertNull(endpoint.getExceptionListener());
        assertEquals(1, endpoint.getIdleTaskExecutionLimit());
        assertEquals(null, endpoint.getJmsMessageType());
        assertNull(endpoint.getJmsOperations());
        assertNotNull(endpoint.getListenerConnectionFactory());
        assertEquals(0, endpoint.getMaxConcurrentConsumers());
        assertEquals(-1, endpoint.getMaxMessagesPerTask());
        assertEquals(null, endpoint.getMessageConverter());
        assertNotNull(endpoint.getMetadataJmsOperations());
        assertNotNull(endpoint.getPriority());
        assertNotNull(endpoint.getProviderMetadata());
        assertNotNull(endpoint.getReceiveTimeout());
        assertNotNull(endpoint.getRecoveryInterval());
        assertNull(endpoint.getReplyTo());
        assertNull(endpoint.getReplyToDestinationSelectorName());
        assertEquals(20000, endpoint.getRequestTimeout());
        assertNull(endpoint.getSelector());
        assertEquals(-1, endpoint.getTimeToLive());
        assertNull(endpoint.getTransactionName());
        assertEquals(-1, endpoint.getTransactionTimeout());
        assertNull(endpoint.getTaskExecutor());
        assertNotNull(endpoint.getTemplateConnectionFactory());
        assertNull(endpoint.getTransactionManager());

        assertEquals(false, endpoint.isAcceptMessagesWhileStopping());
        assertEquals(false, endpoint.isAlwaysCopyMessage());
        assertEquals(true, endpoint.isAutoStartup());
        assertEquals(true, endpoint.isDeliveryPersistent());
        assertEquals(false, endpoint.isDisableReplyTo());
        assertEquals(false, endpoint.isEagerLoadingOfProperties());
        assertEquals(false, endpoint.isExplicitQosEnabled());
        assertEquals(true, endpoint.isExposeListenerSession());
        assertEquals(false, endpoint.isLenientProperties());
        assertEquals(true, endpoint.isMessageIdEnabled());
        assertEquals(true, endpoint.isMessageTimestampEnabled());
        assertEquals(false, endpoint.isPreserveMessageQos());
        assertEquals(false, endpoint.isPubSubDomain());
        assertEquals(false, endpoint.isPubSubNoLocal());
        assertEquals(true, endpoint.isReplyToDeliveryPersistent());
        assertEquals(false, endpoint.isUseMessageIDAsCorrelationID());
        assertEquals(true, endpoint.isSingleton());
        assertEquals(false, endpoint.isSubscriptionDurable());
        assertEquals(false, endpoint.isTransacted());
        assertEquals(false, endpoint.isTransactedInOut());
        assertEquals(false, endpoint.isTransferException());
    }

    @Test
    public void testSettingEndpointOptions() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:queue:Foo");

        endpoint.setAcceptMessagesWhileStopping(true);
        assertEquals(true, endpoint.isAcceptMessagesWhileStopping());

        endpoint.setAcknowledgementMode(2);
        assertEquals(2, endpoint.getAcknowledgementMode());

        endpoint.setAcknowledgementModeName("CLIENT_ACKNOWLEDGE");
        assertEquals("CLIENT_ACKNOWLEDGE", endpoint.getAcknowledgementModeName());

        endpoint.setAlwaysCopyMessage(true);
        assertEquals(true, endpoint.isAlwaysCopyMessage());

        endpoint.setCacheLevel(2);
        assertEquals(2, endpoint.getCacheLevel());

        endpoint.setCacheLevelName("foo");
        assertEquals("foo", endpoint.getCacheLevelName());

        endpoint.setClientId("bar");
        assertEquals("bar", endpoint.getClientId());

        endpoint.setConcurrentConsumers(5);
        assertEquals(5, endpoint.getConcurrentConsumers());

        endpoint.setDeliveryPersistent(true);
        assertEquals(true, endpoint.isDeliveryPersistent());

        endpoint.setDestinationName("cool");
        assertEquals("cool", endpoint.getDestinationName());

        endpoint.setDisableReplyTo(true);
        assertEquals(true, endpoint.isDisableReplyTo());

        endpoint.setEagerLoadingOfProperties(true);
        assertEquals(true, endpoint.isEagerLoadingOfProperties());

        endpoint.setExceptionListener(new ExceptionListener() {
            public void onException(JMSException exception) {
            }
        });
        assertNotNull(endpoint.getExceptionListener());

        endpoint.setExplicitQosEnabled(true);
        assertEquals(true, endpoint.isExplicitQosEnabled());

        endpoint.setExposeListenerSession(true);
        assertEquals(true, endpoint.isExposeListenerSession());

        endpoint.setIdleTaskExecutionLimit(5);
        assertEquals(5, endpoint.getIdleTaskExecutionLimit());

        endpoint.setMaxConcurrentConsumers(4);
        assertEquals(4, endpoint.getMaxConcurrentConsumers());

        endpoint.setMaxMessagesPerTask(9);
        assertEquals(9, endpoint.getMaxMessagesPerTask());

        endpoint.setMessageConverter(new SimpleMessageConverter());
        assertNotNull(endpoint.getMessageConverter());

        endpoint.setMessageIdEnabled(true);
        assertEquals(true, endpoint.isMessageIdEnabled());

        endpoint.setMessageTimestampEnabled(true);
        assertEquals(true, endpoint.isMessageTimestampEnabled());

        endpoint.setPreserveMessageQos(true);
        assertEquals(true, endpoint.isPreserveMessageQos());

        endpoint.setPriority(6);
        assertEquals(6, endpoint.getPriority());

        endpoint.setPubSubNoLocal(true);
        assertEquals(true, endpoint.isPubSubNoLocal());

        endpoint.setPubSubNoLocal(true);
        assertEquals(true, endpoint.isPubSubNoLocal());

        assertEquals(false, endpoint.isPubSubDomain());

        endpoint.setReceiveTimeout(5000);
        assertEquals(5000, endpoint.getReceiveTimeout());

        endpoint.setRecoveryInterval(6000);
        assertEquals(6000, endpoint.getRecoveryInterval());

        endpoint.setReplyTo("bar");
        assertEquals("bar", endpoint.getReplyTo());

        endpoint.setReplyToDeliveryPersistent(true);
        assertEquals(true, endpoint.isReplyToDeliveryPersistent());

        endpoint.setReplyToDestinationSelectorName("me");
        assertEquals("me", endpoint.getReplyToDestinationSelectorName());

        endpoint.setRequestTimeout(3000);
        assertEquals(3000, endpoint.getRequestTimeout());

        endpoint.setSelector("you");
        assertEquals("you", endpoint.getSelector());

        endpoint.setTimeToLive(4000);
        assertEquals(4000, endpoint.getTimeToLive());

        endpoint.setTransacted(true);
        assertEquals(true, endpoint.isTransacted());

        endpoint.setTransactedInOut(true);
        assertEquals(true, endpoint.isTransactedInOut());

        endpoint.setTransferExchange(true);
        assertEquals(true, endpoint.isTransferExchange());

        endpoint.setTransferException(true);
        assertEquals(true, endpoint.isTransferException());

        endpoint.setJmsMessageType(JmsMessageType.Text);
        assertEquals(JmsMessageType.Text, endpoint.getJmsMessageType());
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

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("jms", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }
}
