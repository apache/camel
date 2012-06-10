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

import org.apache.activemq.ActiveMQConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;

import org.junit.Test;

import org.springframework.jms.connection.UserCredentialsConnectionFactoryAdapter;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.listener.SimpleMessageListenerContainer;
import org.springframework.jms.support.converter.SimpleMessageConverter;
import org.springframework.util.ErrorHandler;

/**
 * @version 
 */
public class JmsEndpointConfigurationTest extends CamelTestSupport {

    private final Processor failProcessor = new Processor() {
        public void process(Exchange exchange) throws Exception {
            fail("Should not be reached");
        }
    };

    private final Processor dummyProcessor = new Processor() {
        public void process(Exchange exchange) throws Exception {
            log.info("Received: " + exchange);
        }
    };

    @Test
    public void testDurableSubscriberConfiguredWithDoubleSlash() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms://topic:Foo.Bar?durableSubscriptionName=James&clientId=ABC", JmsEndpoint.class);
        assertDurableSubscriberEndpointIsValid(endpoint);
    }

    @Test
    public void testDurableSubscriberConfiguredWithNoSlashes() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:topic:Foo.Bar?durableSubscriptionName=James&clientId=ABC", JmsEndpoint.class);
        assertDurableSubscriberEndpointIsValid(endpoint);
    }

    @Test
    public void testSetUsernameAndPassword() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:topic:Foo.Bar?username=James&password=ABC", JmsEndpoint.class);
        ConnectionFactory cf = endpoint.getConfiguration().getConnectionFactory();
        assertNotNull("The connectionFactory should not be null", cf);
        assertTrue("The connectionFactory should be the instance of UserCredentialsConnectionFactoryAdapter", cf instanceof UserCredentialsConnectionFactoryAdapter);
    }

    @Test
    public void testSetConnectionFactoryAndUsernameAndPassword() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:topic:Foo.Bar?connectionFactory=#myConnectionFactory&username=James&password=ABC", JmsEndpoint.class);
        ConnectionFactory cf = endpoint.getConfiguration().getConnectionFactory();
        assertNotNull("The connectionFactory should not be null", cf);
        assertTrue("The connectionFactory should be the instance of UserCredentialsConnectionFactoryAdapter", cf instanceof UserCredentialsConnectionFactoryAdapter);
    }

    @Test
    public void testNotSetUsernameOrPassword() {
        try {
            resolveMandatoryEndpoint("jms:topic:Foo.Bar?username=James");
            fail("Expect the exception here");
        } catch (ResolveEndpointFailedException refe) {
            assertEquals("Failed to resolve endpoint: jms://topic:Foo.Bar?username=James due to: The JmsComponent's username or password is null", refe.getMessage());
        }

        try {
            resolveMandatoryEndpoint("jms:topic:Foo.Bar?password=ABC");
            fail("Expect the exception here");
        } catch (ResolveEndpointFailedException refe) {
            assertEquals("Failed to resolve endpoint: jms://topic:Foo.Bar?password=ABC due to: The JmsComponent's username or password is null", refe.getMessage());
        }
    }

    @Test
    public void testSelector() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:Foo.Bar?selector=foo%3D'ABC'", JmsEndpoint.class);
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
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:Foo.Bar?disableReplyTo=true&eagerLoadingOfProperties=true", JmsEndpoint.class);
        JmsConsumer consumer = endpoint.createConsumer(dummyProcessor);

        AbstractMessageListenerContainer container = consumer.getListenerContainer();
        Object object = container.getMessageListener();
        EndpointMessageListener messageListener = assertIsInstanceOf(EndpointMessageListener.class, object);
        assertTrue("Should have replyToDisabled", messageListener.isDisableReplyTo());
        assertTrue("Should have isEagerLoadingOfProperties()", messageListener.isEagerLoadingOfProperties());
    }

    @Test
    public void testCreateSimpleMessageListener() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:Foo.Bar?consumerType=Simple", JmsEndpoint.class);
        JmsConsumer consumer = endpoint.createConsumer(dummyProcessor);

        AbstractMessageListenerContainer container = consumer.getListenerContainer();
        assertTrue("Should have been a SimpleMessageListenerContainer", container instanceof SimpleMessageListenerContainer);
    }

    @Test
    public void testCacheConsumerEnabledForQueue() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:Foo.Bar", JmsEndpoint.class);
        assertCacheLevel(endpoint, DefaultMessageListenerContainer.CACHE_AUTO);
    }

    @Test
    public void testCacheConsumerEnabledForTopic() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:topic:Foo.Bar", JmsEndpoint.class);
        assertCacheLevel(endpoint, DefaultMessageListenerContainer.CACHE_AUTO);
    }

    @Test
    public void testReplyToPesistentDelivery() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:queue:Foo", JmsEndpoint.class);
        endpoint.getConfiguration().setDeliveryPersistent(true);
        endpoint.getConfiguration().setReplyToDeliveryPersistent(false);
        Producer producer = endpoint.createProducer();
        assertNotNull("The producer should not be null", producer);
        JmsConsumer consumer = endpoint.createConsumer(dummyProcessor);
        JmsOperations operations = consumer.getEndpointMessageListener().getTemplate();
        assertTrue(operations instanceof JmsTemplate);
        JmsTemplate template = (JmsTemplate) operations;
        assertTrue("Wrong delivery mode on reply template; expected  " + " DeliveryMode.NON_PERSISTENT but was DeliveryMode.PERSISTENT",
                   template.getDeliveryMode() == DeliveryMode.NON_PERSISTENT);
    }

    @Test
    public void testMaxConcurrentConsumers() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:queue:Foo?maxConcurrentConsumers=5", JmsEndpoint.class);
        assertEquals(5, endpoint.getMaxConcurrentConsumers());
    }

    @Test
    public void testMaxConcurrentConsumersForSimpleConsumer() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:queue:Foo?maxConcurrentConsumers=5&consumerType=Simple", JmsEndpoint.class);
        assertEquals(5, endpoint.getMaxConcurrentConsumers());
    }

    @Test
    public void testInvalidMaxConcurrentConsumers() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:queue:Foo?concurrentConsumers=5&maxConcurrentConsumers=2", JmsEndpoint.class);
        try {
            endpoint.createConsumer(failProcessor);
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Property maxConcurrentConsumers: 2 must be higher than concurrentConsumers: 5", e.getMessage());
        }
    }

    @Test
    public void testInvalidMaxConcurrentConsumersForSimpleConsumer() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:queue:Foo?concurrentConsumers=5&maxConcurrentConsumers=2&consumerType=Simple", JmsEndpoint.class);

        try {
            endpoint.createConsumer(failProcessor);
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Property maxConcurrentConsumers: 2 must be higher than concurrentConsumers: 5", e.getMessage());
        }
    }

    @Test
    public void testSessionTransacted() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:queue:Foo?transacted=true&lazyCreateTransactionManager=false", JmsEndpoint.class);
        AbstractMessageListenerContainer container = endpoint.createConsumer(dummyProcessor).getListenerContainer();
        assertTrue("The JMS sessions will not be transactional!", container.isSessionTransacted());
        assertFalse("The transactionManager gets lazily generated!", endpoint.isLazyCreateTransactionManager());
        assertNull("The endpoint has an injected TransactionManager!", endpoint.getTransactionManager());

        endpoint = resolveMandatoryEndpoint("jms:queue:Foo?transacted=true", JmsEndpoint.class);
        container = endpoint.createConsumer(dummyProcessor).getListenerContainer();
        assertTrue("The JMS sessions will not be transactional!", container.isSessionTransacted());
        assertTrue("The transactionManager doesn't get lazily generated!", endpoint.isLazyCreateTransactionManager());
        assertNotNull("The endpoint has no injected TransactionManager!", endpoint.getTransactionManager());
    }

    @Test
    public void testConcurrentConsumers() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:queue:Foo?concurrentConsumers=4", JmsEndpoint.class);
        assertEquals(4, endpoint.getConcurrentConsumers());
    }

    @Test
    public void testConcurrentConsumersForSimpleConsumer() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:queue:Foo?concurrentConsumers=4&consumerType=Simple", JmsEndpoint.class);
        assertEquals(4, endpoint.getConcurrentConsumers());
    }

    @Test
    public void testPubSubNoLocalForSimpleConsumer() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:queue:Foo?pubSubNoLocal=true&consumerType=Simple", JmsEndpoint.class);
        assertTrue("PubSubNoLocal should be true", endpoint.isPubSubNoLocal());
    }

    @Test
    public void testIdleTaskExecutionLimit() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:queue:Foo?idleTaskExecutionLimit=50", JmsEndpoint.class);
        assertEquals(50, endpoint.getIdleTaskExecutionLimit());
        assertEquals(true, endpoint.isAutoStartup());
    }

    @Test
    public void testIdleConsumerLimit() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:queue:Foo?idleConsumerLimit=51", JmsEndpoint.class);
        assertEquals(51, endpoint.getIdleConsumerLimit());
        assertEquals(true, endpoint.isAutoStartup());
        assertEquals("Foo", endpoint.getEndpointConfiguredDestinationName());
    }

    @Test
    public void testLazyCreateTransactionManager() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:queue:Foo?lazyCreateTransactionManager=true", JmsEndpoint.class);
        assertEquals(true, endpoint.getConfiguration().isLazyCreateTransactionManager());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testDefaultEndpointOptions() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:queue:Foo", JmsEndpoint.class);

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
        assertNull(endpoint.getErrorHandler());
        assertEquals(1, endpoint.getIdleTaskExecutionLimit());
        assertEquals(1, endpoint.getIdleConsumerLimit());
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
        assertEquals("Foo", endpoint.getEndpointConfiguredDestinationName());

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

    @SuppressWarnings("deprecation")
    @Test
    public void testSettingEndpointOptions() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:queue:Foo", JmsEndpoint.class);

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

        endpoint.setErrorHandler(new ErrorHandler() {
            public void handleError(Throwable t) {
            }
        });
        assertNotNull(endpoint.getErrorHandler());

        endpoint.setExplicitQosEnabled(true);
        assertEquals(true, endpoint.isExplicitQosEnabled());

        endpoint.setExposeListenerSession(true);
        assertEquals(true, endpoint.isExposeListenerSession());

        endpoint.setIdleTaskExecutionLimit(5);
        assertEquals(5, endpoint.getIdleTaskExecutionLimit());

        endpoint.setIdleConsumerLimit(5);
        assertEquals(5, endpoint.getIdleConsumerLimit());

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
        camelContext.addComponent("jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myConnectionFactory", new ActiveMQConnectionFactory("vm:myBroker"));
        return jndi;
    }

}
