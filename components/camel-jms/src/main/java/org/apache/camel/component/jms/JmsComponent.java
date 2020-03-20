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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import javax.jms.ConnectionFactory;
import javax.jms.ExceptionListener;
import javax.jms.Session;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.LoggingLevel;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HeaderFilterStrategyComponent;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jms.connection.JmsTransactionManager;
import org.springframework.jms.connection.UserCredentialsConnectionFactoryAdapter;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.destination.DestinationResolver;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.ErrorHandler;

import static org.apache.camel.util.StringHelper.removeStartingCharacters;

/**
 * JMS component which uses Spring JMS.
 */
@Component("jms")
public class JmsComponent extends HeaderFilterStrategyComponent {

    private static final Logger LOG = LoggerFactory.getLogger(JmsComponent.class);
    private static final String KEY_FORMAT_STRATEGY_PARAM = "jmsKeyFormatStrategy";

    private ExecutorService asyncStartStopExecutorService;

    @Metadata(label = "advanced", description = "To use a shared JMS configuration")
    private JmsConfiguration configuration;
    @Metadata(label = "advanced", description = "To use a custom QueueBrowseStrategy when browsing queues")
    private QueueBrowseStrategy queueBrowseStrategy;
    @Metadata(label = "advanced", description = "Whether to auto-discover ConnectionFactory from the registry, if no connection factory has been configured."
            + " If only one instance of ConnectionFactory is found then it will be used. This is enabled by default.", defaultValue = "true")
    private boolean allowAutoWiredConnectionFactory = true;
    @Metadata(label = "advanced", description = "Whether to auto-discover DestinationResolver from the registry, if no destination resolver has been configured."
            + " If only one instance of DestinationResolver is found then it will be used. This is enabled by default.", defaultValue = "true")
    private boolean allowAutoWiredDestinationResolver = true;

    public JmsComponent() {
        this.configuration = createConfiguration();
    }

    public JmsComponent(CamelContext context) {
        super(context);
        this.configuration = createConfiguration();
    }

    public JmsComponent(JmsConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Static builder method
     */
    public static JmsComponent jmsComponent() {
        return new JmsComponent();
    }

    /**
     * Static builder method
     */
    public static JmsComponent jmsComponent(JmsConfiguration configuration) {
        return new JmsComponent(configuration);
    }

    /**
     * Static builder method
     */
    public static JmsComponent jmsComponent(ConnectionFactory connectionFactory) {
        return jmsComponent(new JmsConfiguration(connectionFactory));
    }

    /**
     * Static builder method
     */
    public static JmsComponent jmsComponentClientAcknowledge(ConnectionFactory connectionFactory) {
        JmsConfiguration configuration = new JmsConfiguration(connectionFactory);
        configuration.setAcknowledgementMode(Session.CLIENT_ACKNOWLEDGE);
        return jmsComponent(configuration);
    }

    /**
     * Static builder method
     */
    public static JmsComponent jmsComponentAutoAcknowledge(ConnectionFactory connectionFactory) {
        JmsConfiguration configuration = new JmsConfiguration(connectionFactory);
        configuration.setAcknowledgementMode(Session.AUTO_ACKNOWLEDGE);
        return jmsComponent(configuration);
    }

    public static JmsComponent jmsComponentTransacted(ConnectionFactory connectionFactory) {
        JmsTransactionManager transactionManager = new JmsTransactionManager();
        transactionManager.setConnectionFactory(connectionFactory);
        return jmsComponentTransacted(connectionFactory, transactionManager);
    }

    public static JmsComponent jmsComponentTransacted(ConnectionFactory connectionFactory,
                                                      PlatformTransactionManager transactionManager) {
        JmsConfiguration configuration = new JmsConfiguration(connectionFactory);
        configuration.setTransactionManager(transactionManager);
        configuration.setTransacted(true);
        return jmsComponent(configuration);
    }

    // Properties
    // -------------------------------------------------------------------------

    public JmsConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use a shared JMS configuration
     */
    public void setConfiguration(JmsConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Whether to auto-discover ConnectionFactory from the registry, if no connection factory has been configured.
     * If only one instance of ConnectionFactory is found then it will be used. This is enabled by default.
     */
    public boolean isAllowAutoWiredConnectionFactory() {
        return allowAutoWiredConnectionFactory;
    }

    public void setAllowAutoWiredConnectionFactory(boolean allowAutoWiredConnectionFactory) {
        this.allowAutoWiredConnectionFactory = allowAutoWiredConnectionFactory;
    }

    /**
     * Whether to auto-discover DestinationResolver from the registry, if no destination resolver has been configured.
     * If only one instance of DestinationResolver is found then it will be used. This is enabled by default.
     */
    public boolean isAllowAutoWiredDestinationResolver() {
        return allowAutoWiredDestinationResolver;
    }

    public void setAllowAutoWiredDestinationResolver(boolean allowAutoWiredDestinationResolver) {
        this.allowAutoWiredDestinationResolver = allowAutoWiredDestinationResolver;
    }

    public QueueBrowseStrategy getQueueBrowseStrategy() {
        if (queueBrowseStrategy == null) {
            queueBrowseStrategy = new DefaultQueueBrowseStrategy();
        }
        return queueBrowseStrategy;
    }

    /**
     * To use a custom QueueBrowseStrategy when browsing queues
     */
    public void setQueueBrowseStrategy(QueueBrowseStrategy queueBrowseStrategy) {
        this.queueBrowseStrategy = queueBrowseStrategy;
    }

    // Delegates
    // -------------------------------------------------------------------------

    public JmsConfiguration copy() {
        return configuration.copy();
    }

    public JmsOperations createInOutTemplate(JmsEndpoint endpoint, boolean pubSubDomain, String destination, long requestTimeout) {
        return configuration.createInOutTemplate(endpoint, pubSubDomain, destination, requestTimeout);
    }

    public JmsOperations createInOnlyTemplate(JmsEndpoint endpoint, boolean pubSubDomain, String destination) {
        return configuration.createInOnlyTemplate(endpoint, pubSubDomain, destination);
    }

    public AbstractMessageListenerContainer createMessageListenerContainer(JmsEndpoint endpoint) throws Exception {
        return configuration.createMessageListenerContainer(endpoint);
    }

    public AbstractMessageListenerContainer chooseMessageListenerContainerImplementation(JmsEndpoint endpoint) {
        return configuration.chooseMessageListenerContainerImplementation(endpoint);
    }

    public ConsumerType getConsumerType() {
        return configuration.getConsumerType();
    }

    public void setConsumerType(ConsumerType consumerType) {
        configuration.setConsumerType(consumerType);
    }

    public ConnectionFactory getConnectionFactory() {
        return configuration.getConnectionFactory();
    }

    public ConnectionFactory getOrCreateConnectionFactory() {
        return configuration.getOrCreateConnectionFactory();
    }

    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        configuration.setConnectionFactory(connectionFactory);
    }

    public String getUsername() {
        return configuration.getUsername();
    }

    public void setUsername(String username) {
        configuration.setUsername(username);
    }

    public String getPassword() {
        return configuration.getPassword();
    }

    public void setPassword(String password) {
        configuration.setPassword(password);
    }

    public ConnectionFactory getListenerConnectionFactory() {
        return configuration.getListenerConnectionFactory();
    }

    public ConnectionFactory getOrCreateListenerConnectionFactory() {
        return configuration.getOrCreateListenerConnectionFactory();
    }

    public void setListenerConnectionFactory(ConnectionFactory listenerConnectionFactory) {
        configuration.setListenerConnectionFactory(listenerConnectionFactory);
    }

    public ConnectionFactory getTemplateConnectionFactory() {
        return configuration.getTemplateConnectionFactory();
    }

    public ConnectionFactory getOrCreateTemplateConnectionFactory() {
        return configuration.getOrCreateTemplateConnectionFactory();
    }

    public void setTemplateConnectionFactory(ConnectionFactory templateConnectionFactory) {
        configuration.setTemplateConnectionFactory(templateConnectionFactory);
    }

    public boolean isAutoStartup() {
        return configuration.isAutoStartup();
    }

    public void setAutoStartup(boolean autoStartup) {
        configuration.setAutoStartup(autoStartup);
    }

    public boolean isAcceptMessagesWhileStopping() {
        return configuration.isAcceptMessagesWhileStopping();
    }

    public void setAcceptMessagesWhileStopping(boolean acceptMessagesWhileStopping) {
        configuration.setAcceptMessagesWhileStopping(acceptMessagesWhileStopping);
    }

    public boolean isAllowReplyManagerQuickStop() {
        return configuration.isAllowReplyManagerQuickStop();
    }

    public void setAllowReplyManagerQuickStop(boolean allowReplyManagerQuickStop) {
        configuration.setAllowReplyManagerQuickStop(allowReplyManagerQuickStop);
    }

    public String getClientId() {
        return configuration.getClientId();
    }

    public void setClientId(String consumerClientId) {
        configuration.setClientId(consumerClientId);
    }

    public String getDurableSubscriptionName() {
        return configuration.getDurableSubscriptionName();
    }

    public void setDurableSubscriptionName(String durableSubscriptionName) {
        configuration.setDurableSubscriptionName(durableSubscriptionName);
    }

    public ExceptionListener getExceptionListener() {
        return configuration.getExceptionListener();
    }

    public void setExceptionListener(ExceptionListener exceptionListener) {
        configuration.setExceptionListener(exceptionListener);
    }

    public void setErrorHandler(ErrorHandler errorHandler) {
        configuration.setErrorHandler(errorHandler);
    }

    public ErrorHandler getErrorHandler() {
        return configuration.getErrorHandler();
    }

    public LoggingLevel getErrorHandlerLoggingLevel() {
        return configuration.getErrorHandlerLoggingLevel();
    }

    public void setErrorHandlerLoggingLevel(LoggingLevel errorHandlerLoggingLevel) {
        configuration.setErrorHandlerLoggingLevel(errorHandlerLoggingLevel);
    }

    public boolean isErrorHandlerLogStackTrace() {
        return configuration.isErrorHandlerLogStackTrace();
    }

    public void setErrorHandlerLogStackTrace(boolean errorHandlerLogStackTrace) {
        configuration.setErrorHandlerLogStackTrace(errorHandlerLogStackTrace);
    }

    public String getAcknowledgementModeName() {
        return configuration.getAcknowledgementModeName();
    }

    public void setAcknowledgementModeName(String consumerAcknowledgementMode) {
        configuration.setAcknowledgementModeName(consumerAcknowledgementMode);
    }

    public boolean isExposeListenerSession() {
        return configuration.isExposeListenerSession();
    }

    public void setExposeListenerSession(boolean exposeListenerSession) {
        configuration.setExposeListenerSession(exposeListenerSession);
    }

    public TaskExecutor getTaskExecutor() {
        return configuration.getTaskExecutor();
    }

    public void setTaskExecutor(TaskExecutor taskExecutor) {
        configuration.setTaskExecutor(taskExecutor);
    }

    public boolean isPubSubNoLocal() {
        return configuration.isPubSubNoLocal();
    }

    public void setPubSubNoLocal(boolean pubSubNoLocal) {
        configuration.setPubSubNoLocal(pubSubNoLocal);
    }

    public int getConcurrentConsumers() {
        return configuration.getConcurrentConsumers();
    }

    public void setConcurrentConsumers(int concurrentConsumers) {
        configuration.setConcurrentConsumers(concurrentConsumers);
    }

    public int getReplyToConcurrentConsumers() {
        return configuration.getReplyToConcurrentConsumers();
    }

    public void setReplyToConcurrentConsumers(int replyToConcurrentConsumers) {
        configuration.setReplyToConcurrentConsumers(replyToConcurrentConsumers);
    }

    public int getMaxMessagesPerTask() {
        return configuration.getMaxMessagesPerTask();
    }

    public void setMaxMessagesPerTask(int maxMessagesPerTask) {
        configuration.setMaxMessagesPerTask(maxMessagesPerTask);
    }

    public int getCacheLevel() {
        return configuration.getCacheLevel();
    }

    public void setCacheLevel(int cacheLevel) {
        configuration.setCacheLevel(cacheLevel);
    }

    public String getCacheLevelName() {
        return configuration.getCacheLevelName();
    }

    public void setCacheLevelName(String cacheName) {
        configuration.setCacheLevelName(cacheName);
    }

    public long getRecoveryInterval() {
        return configuration.getRecoveryInterval();
    }

    public void setRecoveryInterval(long recoveryInterval) {
        configuration.setRecoveryInterval(recoveryInterval);
    }

    public long getReceiveTimeout() {
        return configuration.getReceiveTimeout();
    }

    public void setReceiveTimeout(long receiveTimeout) {
        configuration.setReceiveTimeout(receiveTimeout);
    }

    public PlatformTransactionManager getTransactionManager() {
        return configuration.getTransactionManager();
    }

    public PlatformTransactionManager getOrCreateTransactionManager() {
        return configuration.getOrCreateTransactionManager();
    }

    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        configuration.setTransactionManager(transactionManager);
    }

    public String getTransactionName() {
        return configuration.getTransactionName();
    }

    public void setTransactionName(String transactionName) {
        configuration.setTransactionName(transactionName);
    }

    public int getTransactionTimeout() {
        return configuration.getTransactionTimeout();
    }

    public void setTransactionTimeout(int transactionTimeout) {
        configuration.setTransactionTimeout(transactionTimeout);
    }

    public int getIdleTaskExecutionLimit() {
        return configuration.getIdleTaskExecutionLimit();
    }

    public void setIdleTaskExecutionLimit(int idleTaskExecutionLimit) {
        configuration.setIdleTaskExecutionLimit(idleTaskExecutionLimit);
    }

    public int getIdleConsumerLimit() {
        return configuration.getIdleConsumerLimit();
    }

    public void setIdleConsumerLimit(int idleConsumerLimit) {
        configuration.setIdleConsumerLimit(idleConsumerLimit);
    }

    public int getWaitForProvisionCorrelationToBeUpdatedCounter() {
        return configuration.getWaitForProvisionCorrelationToBeUpdatedCounter();
    }

    public void setWaitForProvisionCorrelationToBeUpdatedCounter(int counter) {
        configuration.setWaitForProvisionCorrelationToBeUpdatedCounter(counter);
    }

    public long getWaitForProvisionCorrelationToBeUpdatedThreadSleepingTime() {
        return configuration.getWaitForProvisionCorrelationToBeUpdatedThreadSleepingTime();
    }

    public void setWaitForProvisionCorrelationToBeUpdatedThreadSleepingTime(long sleepingTime) {
        configuration.setWaitForProvisionCorrelationToBeUpdatedThreadSleepingTime(sleepingTime);
    }

    public int getMaxConcurrentConsumers() {
        return configuration.getMaxConcurrentConsumers();
    }

    public void setMaxConcurrentConsumers(int maxConcurrentConsumers) {
        configuration.setMaxConcurrentConsumers(maxConcurrentConsumers);
    }

    public int getReplyToMaxConcurrentConsumers() {
        return configuration.getReplyToMaxConcurrentConsumers();
    }

    public void setReplyToMaxConcurrentConsumers(int replyToMaxConcurrentConsumers) {
        configuration.setReplyToMaxConcurrentConsumers(replyToMaxConcurrentConsumers);
    }

    public int getReplyToOnTimeoutMaxConcurrentConsumers() {
        return configuration.getReplyToOnTimeoutMaxConcurrentConsumers();
    }

    public void setReplyToOnTimeoutMaxConcurrentConsumers(int replyToOnTimeoutMaxConcurrentConsumers) {
        configuration.setReplyToOnTimeoutMaxConcurrentConsumers(replyToOnTimeoutMaxConcurrentConsumers);
    }

    public boolean isExplicitQosEnabled() {
        return configuration.isExplicitQosEnabled();
    }

    public Boolean getExplicitQosEnabled() {
        return configuration.getExplicitQosEnabled();
    }

    public void setExplicitQosEnabled(boolean explicitQosEnabled) {
        configuration.setExplicitQosEnabled(explicitQosEnabled);
    }

    public boolean isDeliveryPersistent() {
        return configuration.isDeliveryPersistent();
    }

    public void setDeliveryPersistent(boolean deliveryPersistent) {
        configuration.setDeliveryPersistent(deliveryPersistent);
    }

    public Integer getDeliveryMode() {
        return configuration.getDeliveryMode();
    }

    public void setDeliveryMode(Integer deliveryMode) {
        configuration.setDeliveryMode(deliveryMode);
    }

    public boolean isReplyToDeliveryPersistent() {
        return configuration.isReplyToDeliveryPersistent();
    }

    public void setReplyToDeliveryPersistent(boolean replyToDeliveryPersistent) {
        configuration.setReplyToDeliveryPersistent(replyToDeliveryPersistent);
    }

    public long getTimeToLive() {
        return configuration.getTimeToLive();
    }

    public void setTimeToLive(long timeToLive) {
        configuration.setTimeToLive(timeToLive);
    }

    public MessageConverter getMessageConverter() {
        return configuration.getMessageConverter();
    }

    public void setMessageConverter(MessageConverter messageConverter) {
        configuration.setMessageConverter(messageConverter);
    }

    public boolean isMapJmsMessage() {
        return configuration.isMapJmsMessage();
    }

    public void setMapJmsMessage(boolean mapJmsMessage) {
        configuration.setMapJmsMessage(mapJmsMessage);
    }

    public boolean isMessageIdEnabled() {
        return configuration.isMessageIdEnabled();
    }

    public void setMessageIdEnabled(boolean messageIdEnabled) {
        configuration.setMessageIdEnabled(messageIdEnabled);
    }

    public boolean isMessageTimestampEnabled() {
        return configuration.isMessageTimestampEnabled();
    }

    public void setMessageTimestampEnabled(boolean messageTimestampEnabled) {
        configuration.setMessageTimestampEnabled(messageTimestampEnabled);
    }

    public int getPriority() {
        return configuration.getPriority();
    }

    public void setPriority(int priority) {
        configuration.setPriority(priority);
    }

    public int getAcknowledgementMode() {
        return configuration.getAcknowledgementMode();
    }

    public void setAcknowledgementMode(int consumerAcknowledgementMode) {
        configuration.setAcknowledgementMode(consumerAcknowledgementMode);
    }

    public boolean isTransacted() {
        return configuration.isTransacted();
    }

    public void setTransacted(boolean consumerTransacted) {
        configuration.setTransacted(consumerTransacted);
    }

    public boolean isLazyCreateTransactionManager() {
        return configuration.isLazyCreateTransactionManager();
    }

    public void setLazyCreateTransactionManager(boolean lazyCreating) {
        configuration.setLazyCreateTransactionManager(lazyCreating);
    }

    public String getEagerPoisonBody() {
        return configuration.getEagerPoisonBody();
    }

    public void setEagerPoisonBody(String eagerPoisonBody) {
        configuration.setEagerPoisonBody(eagerPoisonBody);
    }

    public boolean isEagerLoadingOfProperties() {
        return configuration.isEagerLoadingOfProperties();
    }

    public void setEagerLoadingOfProperties(boolean eagerLoadingOfProperties) {
        configuration.setEagerLoadingOfProperties(eagerLoadingOfProperties);
    }

    public boolean isDisableReplyTo() {
        return configuration.isDisableReplyTo();
    }

    public void setDisableReplyTo(boolean disableReplyTo) {
        configuration.setDisableReplyTo(disableReplyTo);
    }

    public void setPreserveMessageQos(boolean preserveMessageQos) {
        configuration.setPreserveMessageQos(preserveMessageQos);
    }

    public JmsOperations getJmsOperations() {
        return configuration.getJmsOperations();
    }

    public void setJmsOperations(JmsOperations jmsOperations) {
        configuration.setJmsOperations(jmsOperations);
    }

    public DestinationResolver getDestinationResolver() {
        return configuration.getDestinationResolver();
    }

    public void setDestinationResolver(DestinationResolver destinationResolver) {
        configuration.setDestinationResolver(destinationResolver);
    }

    public static DestinationResolver createDestinationResolver(DestinationEndpoint destinationEndpoint) {
        return JmsConfiguration.createDestinationResolver(destinationEndpoint);
    }

    public void configureMessageListenerContainer(AbstractMessageListenerContainer container, JmsEndpoint endpoint) throws Exception {
        configuration.configureMessageListenerContainer(container, endpoint);
    }

    public void configureMessageListener(EndpointMessageListener listener) {
        configuration.configureMessageListener(listener);
    }

    public int defaultCacheLevel(JmsEndpoint endpoint) {
        return configuration.defaultCacheLevel(endpoint);
    }

    public ConnectionFactory createConnectionFactory() {
        return configuration.createConnectionFactory();
    }

    public ConnectionFactory createListenerConnectionFactory() {
        return configuration.createListenerConnectionFactory();
    }

    public ConnectionFactory createTemplateConnectionFactory() {
        return configuration.createTemplateConnectionFactory();
    }

    public PlatformTransactionManager createTransactionManager() {
        return configuration.createTransactionManager();
    }

    public boolean isPreserveMessageQos() {
        return configuration.isPreserveMessageQos();
    }

    public void configuredQoS() {
        configuration.configuredQoS();
    }

    public boolean isAlwaysCopyMessage() {
        return configuration.isAlwaysCopyMessage();
    }

    public void setAlwaysCopyMessage(boolean alwaysCopyMessage) {
        configuration.setAlwaysCopyMessage(alwaysCopyMessage);
    }

    public boolean isUseMessageIDAsCorrelationID() {
        return configuration.isUseMessageIDAsCorrelationID();
    }

    public void setUseMessageIDAsCorrelationID(boolean useMessageIDAsCorrelationID) {
        configuration.setUseMessageIDAsCorrelationID(useMessageIDAsCorrelationID);
    }

    public long getRequestTimeout() {
        return configuration.getRequestTimeout();
    }

    public void setRequestTimeout(long requestTimeout) {
        configuration.setRequestTimeout(requestTimeout);
    }

    public long getRequestTimeoutCheckerInterval() {
        return configuration.getRequestTimeoutCheckerInterval();
    }

    public void setRequestTimeoutCheckerInterval(long requestTimeoutCheckerInterval) {
        configuration.setRequestTimeoutCheckerInterval(requestTimeoutCheckerInterval);
    }

    public String getReplyTo() {
        return configuration.getReplyTo();
    }

    public void setReplyTo(String replyToDestination) {
        configuration.setReplyTo(replyToDestination);
    }

    public String getReplyToDestinationSelectorName() {
        return configuration.getReplyToDestinationSelectorName();
    }

    public void setReplyToDestinationSelectorName(String replyToDestinationSelectorName) {
        configuration.setReplyToDestinationSelectorName(replyToDestinationSelectorName);
    }

    public String getReplyToOverride() {
        return configuration.getReplyToOverride();
    }

    public void setReplyToOverride(String replyToDestination) {
        configuration.setReplyToOverride(replyToDestination);
    }

    public boolean isReplyToSameDestinationAllowed() {
        return configuration.isReplyToSameDestinationAllowed();
    }

    public void setReplyToSameDestinationAllowed(boolean replyToSameDestinationAllowed) {
        configuration.setReplyToSameDestinationAllowed(replyToSameDestinationAllowed);
    }

    public JmsMessageType getJmsMessageType() {
        return configuration.getJmsMessageType();
    }

    public void setJmsMessageType(JmsMessageType jmsMessageType) {
        configuration.setJmsMessageType(jmsMessageType);
    }

    public boolean supportBlobMessage() {
        return configuration.supportBlobMessage();
    }

    public JmsKeyFormatStrategy getJmsKeyFormatStrategy() {
        return configuration.getJmsKeyFormatStrategy();
    }

    public void setJmsKeyFormatStrategy(JmsKeyFormatStrategy jmsKeyFormatStrategy) {
        configuration.setJmsKeyFormatStrategy(jmsKeyFormatStrategy);
    }

    public boolean isTransferExchange() {
        return configuration.isTransferExchange();
    }

    public void setTransferExchange(boolean transferExchange) {
        configuration.setTransferExchange(transferExchange);
    }

    public boolean isAllowSerializedHeaders() {
        return configuration.isAllowSerializedHeaders();
    }

    public void setAllowSerializedHeaders(boolean allowSerializedHeaders) {
        configuration.setAllowSerializedHeaders(allowSerializedHeaders);
    }

    public boolean isTransferException() {
        return configuration.isTransferException();
    }

    public void setTransferException(boolean transferException) {
        configuration.setTransferException(transferException);
    }

    public boolean isAsyncStartListener() {
        return configuration.isAsyncStartListener();
    }

    public void setAsyncStartListener(boolean asyncStartListener) {
        configuration.setAsyncStartListener(asyncStartListener);
    }

    public boolean isAsyncStopListener() {
        return configuration.isAsyncStopListener();
    }

    public void setAsyncStopListener(boolean asyncStopListener) {
        configuration.setAsyncStopListener(asyncStopListener);
    }

    public boolean isTestConnectionOnStartup() {
        return configuration.isTestConnectionOnStartup();
    }

    public void setTestConnectionOnStartup(boolean testConnectionOnStartup) {
        configuration.setTestConnectionOnStartup(testConnectionOnStartup);
    }

    public void setForceSendOriginalMessage(boolean forceSendOriginalMessage) {
        configuration.setForceSendOriginalMessage(forceSendOriginalMessage);
    }

    public boolean isForceSendOriginalMessage() {
        return configuration.isForceSendOriginalMessage();
    }

    public boolean isDisableTimeToLive() {
        return configuration.isDisableTimeToLive();
    }

    public void setDisableTimeToLive(boolean disableTimeToLive) {
        configuration.setDisableTimeToLive(disableTimeToLive);
    }

    public ReplyToType getReplyToType() {
        return configuration.getReplyToType();
    }

    public void setReplyToType(ReplyToType replyToType) {
        configuration.setReplyToType(replyToType);
    }

    public boolean isAsyncConsumer() {
        return configuration.isAsyncConsumer();
    }

    public void setAsyncConsumer(boolean asyncConsumer) {
        configuration.setAsyncConsumer(asyncConsumer);
    }

    public void setReplyToCacheLevelName(String name) {
        configuration.setReplyToCacheLevelName(name);
    }

    public String getReplyToCacheLevelName() {
        return configuration.getReplyToCacheLevelName();
    }

    public boolean isAllowNullBody() {
        return configuration.isAllowNullBody();
    }

    public void setAllowNullBody(boolean allowNullBody) {
        configuration.setAllowNullBody(allowNullBody);
    }

    public MessageListenerContainerFactory getMessageListenerContainerFactory() {
        return configuration.getMessageListenerContainerFactory();
    }

    public void setMessageListenerContainerFactory(MessageListenerContainerFactory messageListenerContainerFactory) {
        configuration.setMessageListenerContainerFactory(messageListenerContainerFactory);
    }

    public boolean isIncludeSentJMSMessageID() {
        return configuration.isIncludeSentJMSMessageID();
    }

    public void setIncludeSentJMSMessageID(boolean includeSentJMSMessageID) {
        configuration.setIncludeSentJMSMessageID(includeSentJMSMessageID);
    }

    public DefaultTaskExecutorType getDefaultTaskExecutorType() {
        return configuration.getDefaultTaskExecutorType();
    }

    public void setDefaultTaskExecutorType(DefaultTaskExecutorType defaultTaskExecutorType) {
        configuration.setDefaultTaskExecutorType(defaultTaskExecutorType);
    }

    public boolean isIncludeAllJMSXProperties() {
        return configuration.isIncludeAllJMSXProperties();
    }

    public void setIncludeAllJMSXProperties(boolean includeAllJMSXProperties) {
        configuration.setIncludeAllJMSXProperties(includeAllJMSXProperties);
    }

    public String getSelector() {
        return configuration.getSelector();
    }

    public void setSelector(String selector) {
        configuration.setSelector(selector);
    }

    public void setCorrelationProperty(String correlationProperty) {
        configuration.setCorrelationProperty(correlationProperty);
    }

    public String getCorrelationProperty() {
        return configuration.getCorrelationProperty();
    }

    public String getAllowAdditionalHeaders() {
        return configuration.getAllowAdditionalHeaders();
    }

    public void setAllowAdditionalHeaders(String allowAdditionalHeaders) {
        configuration.setAllowAdditionalHeaders(allowAdditionalHeaders);
    }

    public boolean isSubscriptionDurable() {
        return configuration.isSubscriptionDurable();
    }

    public void setSubscriptionDurable(boolean subscriptionDurable) {
        configuration.setSubscriptionDurable(subscriptionDurable);
    }

    public boolean isSubscriptionShared() {
        return configuration.isSubscriptionShared();
    }

    public void setSubscriptionShared(boolean subscriptionShared) {
        configuration.setSubscriptionShared(subscriptionShared);
    }

    public String getSubscriptionName() {
        return configuration.getSubscriptionName();
    }

    public void setSubscriptionName(String subscriptionName) {
        configuration.setSubscriptionName(subscriptionName);
    }

    public boolean isStreamMessageTypeEnabled() {
        return configuration.isStreamMessageTypeEnabled();
    }

    public void setStreamMessageTypeEnabled(boolean streamMessageTypeEnabled) {
        configuration.setStreamMessageTypeEnabled(streamMessageTypeEnabled);
    }

    public boolean isFormatDateHeadersToIso8601() {
        return configuration.isFormatDateHeadersToIso8601();
    }

    public void setFormatDateHeadersToIso8601(boolean formatDateHeadersToIso8601) {
        configuration.setFormatDateHeadersToIso8601(formatDateHeadersToIso8601);
    }

    public long getDeliveryDelay() {
        return configuration.getDeliveryDelay();
    }

    public void setDeliveryDelay(long deliveryDelay) {
        configuration.setDeliveryDelay(deliveryDelay);
    }

    public MessageCreatedStrategy getMessageCreatedStrategy() {
        return configuration.getMessageCreatedStrategy();
    }

    public void setMessageCreatedStrategy(MessageCreatedStrategy messageCreatedStrategy) {
        configuration.setMessageCreatedStrategy(messageCreatedStrategy);
    }

    public boolean isArtemisStreamingEnabled() {
        return configuration.isArtemisStreamingEnabled();
    }

    public void setArtemisStreamingEnabled(boolean artemisStreamingEnabled) {
        configuration.setArtemisStreamingEnabled(artemisStreamingEnabled);
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    @Override
    protected void doStart() throws Exception {
        // only attempt to set connection factory if there is no transaction manager
        if (configuration.getConnectionFactory() == null && configuration.getOrCreateTransactionManager() == null && isAllowAutoWiredConnectionFactory()) {
            Set<ConnectionFactory> beans = getCamelContext().getRegistry().findByType(ConnectionFactory.class);
            if (beans.size() == 1) {
                ConnectionFactory cf = beans.iterator().next();
                configuration.setConnectionFactory(cf);
            } else if (beans.size() > 1) {
                LOG.debug("Cannot autowire ConnectionFactory as " + beans.size() + " instances found in registry.");
            }
        }

        if (configuration.getDestinationResolver() == null && isAllowAutoWiredDestinationResolver()) {
            Set<DestinationResolver> beans = getCamelContext().getRegistry().findByType(DestinationResolver.class);
            if (beans.size() == 1) {
                DestinationResolver destinationResolver = beans.iterator().next();
                configuration.setDestinationResolver(destinationResolver);
            } else if (beans.size() > 1) {
                LOG.debug("Cannot autowire ConnectionFactory as " + beans.size() + " instances found in registry.");
            }
        }

        if (getHeaderFilterStrategy() == null) {
            setHeaderFilterStrategy(new JmsHeaderFilterStrategy(configuration.isIncludeAllJMSXProperties()));
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        if (asyncStartStopExecutorService != null) {
            getCamelContext().getExecutorServiceManager().shutdownNow(asyncStartStopExecutorService);
            asyncStartStopExecutorService = null;
        }
        super.doShutdown();
    }

    protected synchronized ExecutorService getAsyncStartStopExecutorService() {
        if (asyncStartStopExecutorService == null) {
            // use a cached thread pool for async start tasks as they can run for a while, and we need a dedicated thread
            // for each task, and the thread pool will shrink when no more tasks running
            asyncStartStopExecutorService = getCamelContext().getExecutorServiceManager().newCachedThreadPool(this, "AsyncStartStopListener");
        }
        return asyncStartStopExecutorService;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
        throws Exception {

        boolean pubSubDomain = false;
        boolean tempDestination = false;

        if (ObjectHelper.isNotEmpty(remaining)) {
            if (remaining.startsWith(JmsConfiguration.QUEUE_PREFIX)) {
                pubSubDomain = false;
                remaining = removeStartingCharacters(remaining.substring(JmsConfiguration.QUEUE_PREFIX.length()), '/');
            } else if (remaining.startsWith(JmsConfiguration.TOPIC_PREFIX)) {
                pubSubDomain = true;
                remaining = removeStartingCharacters(remaining.substring(JmsConfiguration.TOPIC_PREFIX.length()), '/');
            } else if (remaining.startsWith(JmsConfiguration.TEMP_QUEUE_PREFIX)) {
                pubSubDomain = false;
                tempDestination = true;
                remaining = removeStartingCharacters(remaining.substring(JmsConfiguration.TEMP_QUEUE_PREFIX.length()), '/');
            } else if (remaining.startsWith(JmsConfiguration.TEMP_TOPIC_PREFIX)) {
                pubSubDomain = true;
                tempDestination = true;
                remaining = removeStartingCharacters(remaining.substring(JmsConfiguration.TEMP_TOPIC_PREFIX.length()), '/');
            }
        }

        final String subject = convertPathToActualDestination(remaining, parameters);

        // lets make sure we copy the configuration as each endpoint can
        // customize its own version
        JmsConfiguration newConfiguration = getConfiguration().copy();
        JmsEndpoint endpoint;
        if (pubSubDomain) {
            if (tempDestination) {
                endpoint = createTemporaryTopicEndpoint(uri, this, subject, newConfiguration);
            } else {
                endpoint = createTopicEndpoint(uri, this, subject, newConfiguration);
            }
        } else {
            QueueBrowseStrategy strategy = getQueueBrowseStrategy();
            if (tempDestination) {
                endpoint = createTemporaryQueueEndpoint(uri, this, subject, newConfiguration, strategy);
            } else {
                endpoint = createQueueEndpoint(uri, this, subject, newConfiguration, strategy);
            }
        }

        // resolve any custom connection factory first
        ConnectionFactory cf = resolveAndRemoveReferenceParameter(parameters, "connectionFactory", ConnectionFactory.class);
        if (cf != null) {
            endpoint.getConfiguration().setConnectionFactory(cf);
        }

        // if username or password provided then wrap the connection factory
        String cfUsername = getAndRemoveParameter(parameters, "username", String.class, getConfiguration().getUsername());
        String cfPassword = getAndRemoveParameter(parameters, "password", String.class, getConfiguration().getPassword());
        if (cfUsername != null && cfPassword != null) {
            cf = endpoint.getConfiguration().getOrCreateConnectionFactory();
            ObjectHelper.notNull(cf, "ConnectionFactory");
            LOG.debug("Wrapping existing ConnectionFactory with UserCredentialsConnectionFactoryAdapter using username: {} and password: ******", cfUsername);
            UserCredentialsConnectionFactoryAdapter ucfa = new UserCredentialsConnectionFactoryAdapter();
            ucfa.setTargetConnectionFactory(cf);
            ucfa.setPassword(cfPassword);
            ucfa.setUsername(cfUsername);
            endpoint.getConfiguration().setConnectionFactory(ucfa);
        } else {
            // if only username or password was provided then fail
            if (cfUsername != null || cfPassword != null) {
                if (cfUsername == null) {
                    throw new IllegalArgumentException("Username must also be provided when using username/password as credentials.");
                } else {
                    throw new IllegalArgumentException("Password must also be provided when using username/password as credentials.");
                }
            }
        }

        // jms header strategy
        String strategyVal = getAndRemoveParameter(parameters, KEY_FORMAT_STRATEGY_PARAM, String.class);
        JmsKeyFormatStrategy strategy = resolveStandardJmsKeyFormatStrategy(strategyVal);
        if (strategy != null) {
            endpoint.setJmsKeyFormatStrategy(strategy);
        } else {
            // its not a standard, but a reference
            parameters.put(KEY_FORMAT_STRATEGY_PARAM, strategyVal);
            endpoint.setJmsKeyFormatStrategy(resolveAndRemoveReferenceParameter(
                    parameters, KEY_FORMAT_STRATEGY_PARAM, JmsKeyFormatStrategy.class));
        }

        MessageListenerContainerFactory messageListenerContainerFactory = resolveAndRemoveReferenceParameter(parameters,
                "messageListenerContainerFactoryRef", MessageListenerContainerFactory.class);
        if (messageListenerContainerFactory == null) {
            messageListenerContainerFactory = resolveAndRemoveReferenceParameter(parameters,
                    "messageListenerContainerFactory", MessageListenerContainerFactory.class);
        }
        if (messageListenerContainerFactory != null) {
            endpoint.setMessageListenerContainerFactory(messageListenerContainerFactory);
        }

        endpoint.setHeaderFilterStrategy(getHeaderFilterStrategy());
        setProperties(endpoint, parameters);

        return endpoint;
    }

    protected JmsEndpoint createTemporaryTopicEndpoint(String uri, JmsComponent component, String subject, JmsConfiguration configuration) {
        return new JmsTemporaryTopicEndpoint(uri, component, subject, configuration);
    }

    protected JmsEndpoint createTopicEndpoint(String uri, JmsComponent component, String subject, JmsConfiguration configuration) {
        return new JmsEndpoint(uri, component, subject, true, configuration);
    }

    protected JmsEndpoint createTemporaryQueueEndpoint(String uri, JmsComponent component, String subject, JmsConfiguration configuration, QueueBrowseStrategy queueBrowseStrategy) {
        return new JmsTemporaryQueueEndpoint(uri, component, subject, configuration, queueBrowseStrategy);
    }

    protected JmsEndpoint createQueueEndpoint(String uri, JmsComponent component, String subject, JmsConfiguration configuration, QueueBrowseStrategy queueBrowseStrategy) {
        return new JmsQueueEndpoint(uri, component, subject, configuration, queueBrowseStrategy);
    }

    /**
     * Resolves the standard supported {@link JmsKeyFormatStrategy} by a name which can be:
     * <ul>
     *     <li>default - to use the default strategy</li>
     *     <li>passthrough - to use the passthrough strategy</li>
     * </ul>
     *
     * @param name  the name
     * @return the strategy, or <tt>null</tt> if not a standard name.
     */
    private static JmsKeyFormatStrategy resolveStandardJmsKeyFormatStrategy(String name) {
        if ("default".equalsIgnoreCase(name)) {
            return new DefaultJmsKeyFormatStrategy();
        } else if ("passthrough".equalsIgnoreCase(name)) {
            return new PassThroughJmsKeyFormatStrategy();
        } else {
            return null;
        }
    }

    /**
     * A strategy method allowing the URI destination to be translated into the
     * actual JMS destination name (say by looking up in JNDI or something)
     */
    protected String convertPathToActualDestination(String path, Map<String, Object> parameters) {
        return path;
    }

    /**
     * Factory method to create the default configuration instance
     *
     * @return a newly created configuration object which can then be further
     *         customized
     */
    protected JmsConfiguration createConfiguration() {
        return new JmsConfiguration();
    }

}
