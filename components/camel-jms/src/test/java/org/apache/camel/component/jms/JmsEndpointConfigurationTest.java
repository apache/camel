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
package org.apache.camel.component.jms;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.DeliveryMode;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.connection.UserCredentialsConnectionFactoryAdapter;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.listener.SimpleMessageListenerContainer;
import org.springframework.jms.support.converter.SimpleMessageConverter;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class JmsEndpointConfigurationTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    private static final Logger LOG = LoggerFactory.getLogger(JmsEndpointConfigurationTest.class);

    @RegisterExtension
    public ArtemisService service = ArtemisServiceFactory.createVMService();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @BindToRegistry("myConnectionFactory")
    private final ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(service.serviceAddress());
    private final Processor failProcessor = exchange -> fail("Should not be reached");

    private final Processor dummyProcessor = exchange -> LOG.info("Received: {}", exchange);

    @Override
    protected String getComponentName() {
        return "jms";
    }

    @Test
    public void testDurableSubscriberConfiguredWithDoubleSlash() throws Exception {
        JmsEndpoint endpoint
                = resolveMandatoryEndpoint(
                        "jms://topic:Foo.Bar.JmsEndpointConfigurationTest?durableSubscriptionName=James&clientId=ABC",
                        JmsEndpoint.class);
        assertDurableSubscriberEndpointIsValid(endpoint);
    }

    @Test
    public void testDurableSubscriberConfiguredWithNoSlashes() throws Exception {
        JmsEndpoint endpoint
                = resolveMandatoryEndpoint(
                        "jms:topic:Foo.Bar.JmsEndpointConfigurationTest?durableSubscriptionName=James&clientId=ABC",
                        JmsEndpoint.class);
        assertDurableSubscriberEndpointIsValid(endpoint);
    }

    @Test
    public void testDurableSharedSubscriber() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint(
                "jms:topic:Foo.Bar.JmsEndpointConfigurationTest?subscriptionDurable=true&subscriptionShared=true&subscriptionName=James",
                JmsEndpoint.class);
        JmsConfiguration configuration = endpoint.getConfiguration();
        assertTrue(configuration.isSubscriptionDurable(), "isSubscriptionDurable()");
        assertTrue(configuration.isSubscriptionShared(), "isSubscriptionShared()");
        assertEquals("James", configuration.getSubscriptionName(), "getSubscriptionName()");

        JmsConsumer consumer = endpoint.createConsumer(exchange -> LOG.info("Received: {}", exchange));
        AbstractMessageListenerContainer listenerContainer = consumer.getListenerContainer();
        assertTrue(listenerContainer.isSubscriptionDurable(), "isSubscriptionDurable()");
        assertTrue(listenerContainer.isSubscriptionShared(), "isSubscriptionShared()");
        assertEquals("James", listenerContainer.getSubscriptionName(), "getSubscriptionName()");
    }

    @Test
    public void testNonDurableSharedSubscriber() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint(
                "jms:topic:Foo.Bar.JmsEndpointConfigurationTest?subscriptionShared=true&subscriptionName=James",
                JmsEndpoint.class);
        JmsConfiguration configuration = endpoint.getConfiguration();
        assertFalse(configuration.isSubscriptionDurable(), "isSubscriptionDurable()");
        assertTrue(configuration.isSubscriptionShared(), "isSubscriptionShared()");
        assertEquals("James", configuration.getSubscriptionName(), "getSubscriptionName()");

        JmsConsumer consumer = endpoint.createConsumer(exchange -> LOG.info("Received: {}", exchange));
        AbstractMessageListenerContainer listenerContainer = consumer.getListenerContainer();
        assertFalse(listenerContainer.isSubscriptionDurable(), "isSubscriptionDurable()");
        assertTrue(listenerContainer.isSubscriptionShared(), "isSubscriptionShared()");
        assertEquals("James", listenerContainer.getSubscriptionName(), "getSubscriptionName()");
    }

    @Test
    public void testSetUsernameAndPassword() {
        JmsEndpoint endpoint = resolveMandatoryEndpoint(
                "jms:topic:Foo.Bar.JmsEndpointConfigurationTest?username=James&password=ABC", JmsEndpoint.class);
        ConnectionFactory cf = endpoint.getConfiguration().getConnectionFactory();
        assertNotNull(cf, "The connectionFactory should not be null");
        assertTrue(cf instanceof UserCredentialsConnectionFactoryAdapter,
                "The connectionFactory should be the instance of UserCredentialsConnectionFactoryAdapter");
    }

    @Test
    public void testSetConnectionFactoryAndUsernameAndPassword() {
        JmsEndpoint endpoint = resolveMandatoryEndpoint(
                "jms:topic:Foo.Bar.JmsEndpointConfigurationTest?connectionFactory=#myConnectionFactory&username=James&password=ABC",
                JmsEndpoint.class);
        ConnectionFactory cf = endpoint.getConfiguration().getConnectionFactory();
        assertNotNull(cf, "The connectionFactory should not be null");
        assertTrue(cf instanceof UserCredentialsConnectionFactoryAdapter,
                "The connectionFactory should be the instance of UserCredentialsConnectionFactoryAdapter");
    }

    @Test
    public void testNotSetUsernameOrPassword() {
        try {
            resolveMandatoryEndpoint("jms:topic:Foo.Bar.JmsEndpointConfigurationTest?username=James");
            fail("Expect the exception here");
        } catch (ResolveEndpointFailedException refe) {
            // expected
        }

        try {
            resolveMandatoryEndpoint("jms:topic:Foo.Bar.JmsEndpointConfigurationTest?password=ABC");
            fail("Expect the exception here");
        } catch (ResolveEndpointFailedException refe) {
            // expected
        }
    }

    @Test
    public void testSelector() throws Exception {
        JmsEndpoint endpoint
                = resolveMandatoryEndpoint("jms:Foo.JmsEndpointConfigurationTest?selector=foo%3D'ABC'", JmsEndpoint.class);
        JmsConsumer consumer = endpoint.createConsumer(dummyProcessor);

        AbstractMessageListenerContainer container = consumer.getListenerContainer();
        assertEquals("foo='ABC'", container.getMessageSelector(), "selector");

        Object object = container.getMessageListener();
        EndpointMessageListener messageListener = assertIsInstanceOf(EndpointMessageListener.class, object);
        assertFalse(messageListener.isDisableReplyTo(), "Should not have replyToDisabled");
        assertFalse(messageListener.isEagerLoadingOfProperties(), "Should not have isEagerLoadingOfProperties()");
    }

    @Test
    public void testConfigureMessageListener() throws Exception {
        JmsEndpoint endpoint
                = resolveMandatoryEndpoint("jms:Foo.Bar?disableReplyTo=true&eagerLoadingOfProperties=true", JmsEndpoint.class);
        JmsConsumer consumer = endpoint.createConsumer(dummyProcessor);

        AbstractMessageListenerContainer container = consumer.getListenerContainer();
        Object object = container.getMessageListener();
        EndpointMessageListener messageListener = assertIsInstanceOf(EndpointMessageListener.class, object);
        assertTrue(messageListener.isDisableReplyTo(), "Should have replyToDisabled");
        assertTrue(messageListener.isEagerLoadingOfProperties(), "Should have isEagerLoadingOfProperties()");
    }

    @Test
    public void testCreateSimpleMessageListener() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:Foo.Bar?consumerType=Simple", JmsEndpoint.class);
        JmsConsumer consumer = endpoint.createConsumer(dummyProcessor);

        AbstractMessageListenerContainer container = consumer.getListenerContainer();
        assertTrue(container instanceof SimpleMessageListenerContainer, "Should have been a SimpleMessageListenerContainer");
    }

    @Test
    public void testCacheConsumerEnabledForQueue() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:Foo.Bar", JmsEndpoint.class);
        assertCacheLevel(endpoint, DefaultMessageListenerContainer.CACHE_AUTO);
    }

    @Test
    public void testCacheConsumerEnabledForTopic() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:topic:Foo.Bar.JmsEndpointConfigurationTest", JmsEndpoint.class);
        assertCacheLevel(endpoint, DefaultMessageListenerContainer.CACHE_AUTO);
    }

    @Test
    public void testReplyToPesistentDelivery() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:queue:Foo.JmsEndpointConfigurationTest", JmsEndpoint.class);
        endpoint.getConfiguration().setDeliveryPersistent(true);
        endpoint.getConfiguration().setReplyToDeliveryPersistent(false);
        Producer producer = endpoint.createProducer();
        assertNotNull(producer, "The producer should not be null");
        JmsConsumer consumer = endpoint.createConsumer(dummyProcessor);
        JmsOperations operations = consumer.getEndpointMessageListener().getTemplate();
        assertTrue(operations instanceof JmsTemplate);
        JmsTemplate template = (JmsTemplate) operations;
        assertEquals(DeliveryMode.NON_PERSISTENT, template.getDeliveryMode(),
                "Wrong delivery mode on reply template; expected  " + " DeliveryMode.NON_PERSISTENT but was DeliveryMode.PERSISTENT");
    }

    @Test
    public void testMaxConcurrentConsumers() {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:queue:Foo.JmsEndpointConfigurationTest?maxConcurrentConsumers=5",
                JmsEndpoint.class);
        assertEquals(5, endpoint.getMaxConcurrentConsumers());
    }

    @Test
    public void testMaxConcurrentConsumersForSimpleConsumer() {
        JmsEndpoint endpoint
                = resolveMandatoryEndpoint(
                        "jms:queue:Foo.JmsEndpointConfigurationTest?maxConcurrentConsumers=5&consumerType=Simple",
                        JmsEndpoint.class);
        assertEquals(5, endpoint.getMaxConcurrentConsumers());
    }

    @Test
    public void testInvalidMaxConcurrentConsumers() throws Exception {
        JmsEndpoint endpoint
                = resolveMandatoryEndpoint(
                        "jms:queue:Foo.JmsEndpointConfigurationTest?concurrentConsumers=5&maxConcurrentConsumers=2",
                        JmsEndpoint.class);
        try {
            endpoint.createConsumer(failProcessor);
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Property maxConcurrentConsumers: 2 must be higher than concurrentConsumers: 5", e.getMessage());
        }
    }

    @Test
    public void testInvalidMaxConcurrentConsumersForSimpleConsumer() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint(
                "jms:queue:Foo.JmsEndpointConfigurationTest?concurrentConsumers=5&maxConcurrentConsumers=2&consumerType=Simple",
                JmsEndpoint.class);

        try {
            endpoint.createConsumer(failProcessor);
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Property maxConcurrentConsumers: 2 must be higher than concurrentConsumers: 5", e.getMessage());
        }
    }

    @Test
    public void testSessionTransacted() throws Exception {
        JmsEndpoint endpoint = resolveMandatoryEndpoint(
                "jms:queue:Foo.JmsEndpointConfigurationTest?transacted=true&lazyCreateTransactionManager=false",
                JmsEndpoint.class);
        AbstractMessageListenerContainer container = endpoint.createConsumer(dummyProcessor).getListenerContainer();
        assertTrue(container.isSessionTransacted(), "The JMS sessions will not be transactional!");
        assertFalse(endpoint.isLazyCreateTransactionManager(), "The transactionManager gets lazily generated!");
        assertNull(endpoint.getTransactionManager(), "The endpoint has an injected TransactionManager!");

        endpoint = resolveMandatoryEndpoint("jms:queue:Foo.JmsEndpointConfigurationTest?transacted=true", JmsEndpoint.class);
        container = endpoint.createConsumer(dummyProcessor).getListenerContainer();
        assertTrue(container.isSessionTransacted(), "The JMS sessions will not be transactional!");
        assertTrue(endpoint.isLazyCreateTransactionManager(), "The transactionManager doesn't get lazily generated!");
        assertNotNull(endpoint.getTransactionManager(), "The endpoint has no injected TransactionManager!");
    }

    @Test
    public void testConcurrentConsumers() {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:queue:Foo.JmsEndpointConfigurationTest?concurrentConsumers=4",
                JmsEndpoint.class);
        assertEquals(4, endpoint.getConcurrentConsumers());
    }

    @Test
    public void testConcurrentConsumersForSimpleConsumer() {
        JmsEndpoint endpoint
                = resolveMandatoryEndpoint(
                        "jms:queue:Foo.JmsEndpointConfigurationTest?concurrentConsumers=4&consumerType=Simple",
                        JmsEndpoint.class);
        assertEquals(4, endpoint.getConcurrentConsumers());
    }

    @Test
    public void testPubSubNoLocalForSimpleConsumer() {
        JmsEndpoint endpoint
                = resolveMandatoryEndpoint("jms:queue:Foo.JmsEndpointConfigurationTest?pubSubNoLocal=true&consumerType=Simple",
                        JmsEndpoint.class);
        assertTrue(endpoint.isPubSubNoLocal(), "PubSubNoLocal should be true");
    }

    @Test
    public void testIdleTaskExecutionLimit() {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:queue:Foo.JmsEndpointConfigurationTest?idleTaskExecutionLimit=50",
                JmsEndpoint.class);
        assertEquals(50, endpoint.getIdleTaskExecutionLimit());
        assertTrue(endpoint.isAutoStartup());
    }

    @Test
    public void testIdleConsumerLimit() {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:queue:Foo.JmsEndpointConfigurationTest?idleConsumerLimit=51",
                JmsEndpoint.class);
        assertEquals(51, endpoint.getIdleConsumerLimit());
        assertTrue(endpoint.isAutoStartup());
        assertEquals("Foo.JmsEndpointConfigurationTest", endpoint.getEndpointConfiguredDestinationName());
    }

    @Test
    public void testLazyCreateTransactionManager() {
        JmsEndpoint endpoint = resolveMandatoryEndpoint(
                "jms:queue:Foo.JmsEndpointConfigurationTest?lazyCreateTransactionManager=true", JmsEndpoint.class);
        assertTrue(endpoint.getConfiguration().isLazyCreateTransactionManager());
    }

    @Test
    public void testDefaultEndpointOptions() {
        JmsEndpoint endpoint = resolveMandatoryEndpoint("jms:queue:Foo.JmsEndpointConfigurationTest.New", JmsEndpoint.class);

        assertNotNull(endpoint.getBinding());
        assertNotNull(endpoint.getCamelContext());
        assertNull(endpoint.getDefaultTaskExecutorType());
        assertNull(endpoint.getMessageListenerContainerFactory());
        assertEquals(5000, endpoint.getRecoveryInterval());
        assertEquals(1000, endpoint.getReceiveTimeout());
        assertEquals("JmsConsumer[Foo.JmsEndpointConfigurationTest.New]", endpoint.getThreadName());
        assertEquals(-1, endpoint.getTimeToLive());
        assertEquals(-1, endpoint.getTransactionTimeout());
        assertEquals(1, endpoint.getAcknowledgementMode());
        assertNull(endpoint.getAcknowledgementModeName());
        assertEquals(-1, endpoint.getCacheLevel());
        assertNull(endpoint.getCacheLevelName());
        assertNotNull(endpoint.getCamelContext().getName());
        assertNull(endpoint.getClientId());
        assertNotNull(endpoint.getConnectionFactory());
        assertEquals(1, endpoint.getConcurrentConsumers());
        assertNull(endpoint.getDeliveryMode());
        assertEquals("Foo.JmsEndpointConfigurationTest.New", endpoint.getDestinationName());
        assertNull(endpoint.getDestinationResolver());
        assertNull(endpoint.getDurableSubscriptionName());
        assertEquals("jms://queue:Foo.JmsEndpointConfigurationTest.New", endpoint.getEndpointKey());
        assertEquals("jms://queue:Foo.JmsEndpointConfigurationTest.New", endpoint.getEndpointUri());
        assertNull(endpoint.getExceptionListener());
        assertNull(endpoint.getErrorHandler());
        assertEquals(LoggingLevel.WARN, endpoint.getErrorHandlerLoggingLevel());
        assertEquals(1, endpoint.getIdleTaskExecutionLimit());
        assertEquals(1, endpoint.getIdleConsumerLimit());
        assertNull(endpoint.getJmsMessageType());
        assertNull(endpoint.getJmsOperations());
        assertNull(endpoint.getListenerConnectionFactory());
        assertNotNull(endpoint.getConfiguration().getOrCreateListenerConnectionFactory());
        assertEquals(0, endpoint.getMaxConcurrentConsumers());
        assertEquals(-1, endpoint.getMaxMessagesPerTask());
        assertNull(endpoint.getMessageConverter());
        assertNotEquals(0, endpoint.getPriority());
        assertNotEquals(0, endpoint.getReceiveTimeout());
        assertNotEquals(0, endpoint.getRecoveryInterval());
        assertNull(endpoint.getReplyTo());
        assertNull(endpoint.getReplyToType());
        assertNull(endpoint.getReplyToCacheLevelName());
        assertNull(endpoint.getReplyToDestinationSelectorName());
        assertEquals(20000L, endpoint.getRequestTimeout());
        assertEquals(1000L, endpoint.getRequestTimeoutCheckerInterval());
        assertEquals(0, endpoint.getRunningMessageListeners());
        assertNull(endpoint.getSelector());
        assertEquals(ServiceStatus.Started, endpoint.getStatus());
        assertEquals(-1, endpoint.getTimeToLive());
        assertNull(endpoint.getTransactionName());
        assertEquals(-1, endpoint.getTransactionTimeout());
        assertNull(endpoint.getTaskExecutor());
        assertNull(endpoint.getTemplateConnectionFactory());
        assertNotNull(endpoint.getConfiguration().getOrCreateTemplateConnectionFactory());
        assertNull(endpoint.getTransactionManager());
        assertEquals("Foo.JmsEndpointConfigurationTest.New", endpoint.getEndpointConfiguredDestinationName());

        assertFalse(endpoint.isAcceptMessagesWhileStopping());
        assertFalse(endpoint.isAllowReplyManagerQuickStop());
        assertFalse(endpoint.isAlwaysCopyMessage());
        assertTrue(endpoint.isAllowNullBody());
        assertFalse(endpoint.isAsyncConsumer());
        assertTrue(endpoint.isAutoStartup());
        assertFalse(endpoint.isAsyncStartListener());
        assertFalse(endpoint.isAsyncStopListener());
        assertTrue(endpoint.isDeliveryPersistent());
        assertFalse(endpoint.isDisableReplyTo());
        assertFalse(endpoint.isDisableTimeToLive());
        assertFalse(endpoint.isEagerLoadingOfProperties());
        assertTrue(endpoint.isErrorHandlerLogStackTrace());
        assertFalse(endpoint.isExplicitQosEnabled());
        assertTrue(endpoint.isExposeListenerSession());
        assertFalse(endpoint.isForceSendOriginalMessage());
        assertFalse(endpoint.isIncludeAllJMSXProperties());
        assertFalse(endpoint.isIncludeSentJMSMessageID());
        assertTrue(endpoint.isLazyCreateTransactionManager());
        assertFalse(endpoint.isLenientProperties());
        assertTrue(endpoint.isMessageIdEnabled());
        assertTrue(endpoint.isMessageTimestampEnabled());
        assertFalse(endpoint.isPreserveMessageQos());
        assertFalse(endpoint.isPubSubDomain());
        assertFalse(endpoint.isPubSubNoLocal());
        assertTrue(endpoint.isReplyToDeliveryPersistent());
        assertFalse(endpoint.isUseMessageIDAsCorrelationID());
        assertTrue(endpoint.isSingleton());
        assertFalse(endpoint.isSubscriptionDurable());
        assertFalse(endpoint.isTestConnectionOnStartup());
        assertFalse(endpoint.isTransacted());
        assertFalse(endpoint.isTransferExchange());
        assertFalse(endpoint.isTransferException());
        assertFalse(endpoint.isTransferException());
        assertFalse(endpoint.isFormatDateHeadersToIso8601());
    }

    @Test
    public void testSettingEndpointOptions() {
        JmsEndpoint endpoint
                = resolveMandatoryEndpoint("jms:queue:Foo.JmsEndpointConfigurationTest.NonCached", JmsEndpoint.class);

        endpoint.setAcceptMessagesWhileStopping(true);
        assertTrue(endpoint.isAcceptMessagesWhileStopping());

        endpoint.setAllowReplyManagerQuickStop(true);
        assertTrue(endpoint.isAllowReplyManagerQuickStop());

        endpoint.setAcknowledgementMode(2);
        assertEquals(2, endpoint.getAcknowledgementMode());

        endpoint.setAcknowledgementModeName("CLIENT_ACKNOWLEDGE");
        assertEquals("CLIENT_ACKNOWLEDGE", endpoint.getAcknowledgementModeName());

        endpoint.setAlwaysCopyMessage(true);
        assertTrue(endpoint.isAlwaysCopyMessage());

        endpoint.setCacheLevel(2);
        assertEquals(2, endpoint.getCacheLevel());

        endpoint.setCacheLevelName("foo");
        assertEquals("foo", endpoint.getCacheLevelName());

        endpoint.setClientId("bar");
        assertEquals("bar", endpoint.getClientId());

        endpoint.setConcurrentConsumers(5);
        assertEquals(5, endpoint.getConcurrentConsumers());

        endpoint.setDeliveryPersistent(true);
        assertTrue(endpoint.isDeliveryPersistent());

        endpoint.setDestinationName("cool");
        assertEquals("cool", endpoint.getDestinationName());

        endpoint.setDisableReplyTo(true);
        assertTrue(endpoint.isDisableReplyTo());

        endpoint.setEagerLoadingOfProperties(true);
        assertTrue(endpoint.isEagerLoadingOfProperties());

        endpoint.setExceptionListener(exception -> {
        });
        assertNotNull(endpoint.getExceptionListener());

        endpoint.setErrorHandler(t -> {
        });
        assertNotNull(endpoint.getErrorHandler());

        endpoint.setExplicitQosEnabled(true);
        assertTrue(endpoint.isExplicitQosEnabled());

        endpoint.setExposeListenerSession(true);
        assertTrue(endpoint.isExposeListenerSession());

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
        assertTrue(endpoint.isMessageIdEnabled());

        endpoint.setMessageTimestampEnabled(true);
        assertTrue(endpoint.isMessageTimestampEnabled());

        endpoint.setPreserveMessageQos(true);
        assertTrue(endpoint.isPreserveMessageQos());

        endpoint.setPriority(6);
        assertEquals(6, endpoint.getPriority());

        endpoint.setPubSubNoLocal(true);
        assertTrue(endpoint.isPubSubNoLocal());

        endpoint.setPubSubNoLocal(true);
        assertTrue(endpoint.isPubSubNoLocal());

        assertFalse(endpoint.isPubSubDomain());

        endpoint.setReceiveTimeout(5000);
        assertEquals(5000, endpoint.getReceiveTimeout());

        endpoint.setRecoveryInterval(6000);
        assertEquals(6000, endpoint.getRecoveryInterval());

        endpoint.setReplyTo("bar");
        assertEquals("bar", endpoint.getReplyTo());

        endpoint.setReplyToDeliveryPersistent(true);
        assertTrue(endpoint.isReplyToDeliveryPersistent());

        endpoint.setReplyToDestinationSelectorName("me");
        assertEquals("me", endpoint.getReplyToDestinationSelectorName());

        endpoint.setRequestTimeout(3000);
        assertEquals(3000, endpoint.getRequestTimeout());

        endpoint.setSelector("you");
        assertEquals("you", endpoint.getSelector());

        endpoint.setTimeToLive(4000);
        assertEquals(4000, endpoint.getTimeToLive());

        endpoint.setTransacted(true);
        assertTrue(endpoint.isTransacted());

        endpoint.setTransferExchange(true);
        assertTrue(endpoint.isTransferExchange());

        endpoint.setTransferException(true);
        assertTrue(endpoint.isTransferException());

        endpoint.setJmsMessageType(JmsMessageType.Text);
        assertEquals(JmsMessageType.Text, endpoint.getJmsMessageType());

        endpoint.setFormatDateHeadersToIso8601(true);
        assertTrue(endpoint.isFormatDateHeadersToIso8601());
    }

    protected void assertCacheLevel(JmsEndpoint endpoint, int expected) throws Exception {
        JmsConsumer consumer = endpoint.createConsumer(dummyProcessor);

        AbstractMessageListenerContainer container = consumer.getListenerContainer();
        DefaultMessageListenerContainer defaultContainer = assertIsInstanceOf(DefaultMessageListenerContainer.class, container);
        int cacheLevel = defaultContainer.getCacheLevel();
        assertEquals(expected, cacheLevel, "CacheLevel");
    }

    protected void assertDurableSubscriberEndpointIsValid(JmsEndpoint endpoint) throws Exception {
        JmsConfiguration configuration = endpoint.getConfiguration();
        assertEquals("James", configuration.getDurableSubscriptionName(), "getDurableSubscriptionName()");
        assertEquals("ABC", configuration.getClientId(), "getClientId()");
        assertTrue(configuration.isDeliveryPersistent(), "isDeliveryPersistent()");

        JmsConsumer consumer = endpoint.createConsumer(exchange -> LOG.info("Received: {}", exchange));
        AbstractMessageListenerContainer listenerContainer = consumer.getListenerContainer();
        assertEquals("James", listenerContainer.getDurableSubscriptionName(), "getDurableSubscriptionName()");
        assertEquals("ABC", listenerContainer.getClientId(), "getClientId()");
        assertTrue(listenerContainer.isSubscriptionDurable(), "isSubscriptionDurable()");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return null;
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        consumer = camelContextExtension.getConsumerTemplate();
    }
}
