package org.apache.camel.builder.component.dsl;

import org.apache.camel.Component;
import org.apache.camel.builder.component.AbstractComponentBuilder;
import org.apache.camel.builder.component.ComponentBuilder;
import org.apache.camel.component.activemq.ActiveMQComponent;

public interface ActiveMQComponentBuilderFactory {

    static ActiveMQComponentBuilder activemq() {
        return new ActiveMQComponentBuilderImpl();
    }

    interface ActiveMQComponentBuilder extends ComponentBuilder {
        default ActiveMQComponentBuilder withComponentName(String name) {
            doSetComponentName(name);
            return this;
        }
        default ActiveMQComponentBuilder setBrokerURL(java.lang.String brokerURL) {
            doSetProperty("brokerURL", brokerURL);
            return this;
        }
        default ActiveMQComponentBuilder setTrustAllPackages(
                boolean trustAllPackages) {
            doSetProperty("trustAllPackages", trustAllPackages);
            return this;
        }
        default ActiveMQComponentBuilder setUsePooledConnection(
                boolean usePooledConnection) {
            doSetProperty("usePooledConnection", usePooledConnection);
            return this;
        }
        default ActiveMQComponentBuilder setUseSingleConnection(
                boolean useSingleConnection) {
            doSetProperty("useSingleConnection", useSingleConnection);
            return this;
        }
        default ActiveMQComponentBuilder setConfiguration(
                org.apache.camel.component.jms.JmsConfiguration configuration) {
            doSetProperty("configuration", configuration);
            return this;
        }
        default ActiveMQComponentBuilder setAllowAutoWiredConnectionFactory(
                boolean allowAutoWiredConnectionFactory) {
            doSetProperty("allowAutoWiredConnectionFactory", allowAutoWiredConnectionFactory);
            return this;
        }
        default ActiveMQComponentBuilder setAllowAutoWiredDestinationResolver(
                boolean allowAutoWiredDestinationResolver) {
            doSetProperty("allowAutoWiredDestinationResolver", allowAutoWiredDestinationResolver);
            return this;
        }
        default ActiveMQComponentBuilder setAcceptMessagesWhileStopping(
                boolean acceptMessagesWhileStopping) {
            doSetProperty("acceptMessagesWhileStopping", acceptMessagesWhileStopping);
            return this;
        }
        default ActiveMQComponentBuilder setAllowReplyManagerQuickStop(
                boolean allowReplyManagerQuickStop) {
            doSetProperty("allowReplyManagerQuickStop", allowReplyManagerQuickStop);
            return this;
        }
        default ActiveMQComponentBuilder setAcknowledgementMode(
                int acknowledgementMode) {
            doSetProperty("acknowledgementMode", acknowledgementMode);
            return this;
        }
        default ActiveMQComponentBuilder setEagerPoisonBody(
                java.lang.String eagerPoisonBody) {
            doSetProperty("eagerPoisonBody", eagerPoisonBody);
            return this;
        }
        default ActiveMQComponentBuilder setEagerLoadingOfProperties(
                boolean eagerLoadingOfProperties) {
            doSetProperty("eagerLoadingOfProperties", eagerLoadingOfProperties);
            return this;
        }
        default ActiveMQComponentBuilder setAcknowledgementModeName(
                java.lang.String acknowledgementModeName) {
            doSetProperty("acknowledgementModeName", acknowledgementModeName);
            return this;
        }
        default ActiveMQComponentBuilder setAutoStartup(boolean autoStartup) {
            doSetProperty("autoStartup", autoStartup);
            return this;
        }
        default ActiveMQComponentBuilder setCacheLevel(int cacheLevel) {
            doSetProperty("cacheLevel", cacheLevel);
            return this;
        }
        default ActiveMQComponentBuilder setCacheLevelName(
                java.lang.String cacheLevelName) {
            doSetProperty("cacheLevelName", cacheLevelName);
            return this;
        }
        default ActiveMQComponentBuilder setReplyToCacheLevelName(
                java.lang.String replyToCacheLevelName) {
            doSetProperty("replyToCacheLevelName", replyToCacheLevelName);
            return this;
        }
        default ActiveMQComponentBuilder setClientId(java.lang.String clientId) {
            doSetProperty("clientId", clientId);
            return this;
        }
        default ActiveMQComponentBuilder setConcurrentConsumers(
                int concurrentConsumers) {
            doSetProperty("concurrentConsumers", concurrentConsumers);
            return this;
        }
        default ActiveMQComponentBuilder setReplyToConcurrentConsumers(
                int replyToConcurrentConsumers) {
            doSetProperty("replyToConcurrentConsumers", replyToConcurrentConsumers);
            return this;
        }
        default ActiveMQComponentBuilder setConnectionFactory(
                javax.jms.ConnectionFactory connectionFactory) {
            doSetProperty("connectionFactory", connectionFactory);
            return this;
        }
        default ActiveMQComponentBuilder setUsername(java.lang.String username) {
            doSetProperty("username", username);
            return this;
        }
        default ActiveMQComponentBuilder setPassword(java.lang.String password) {
            doSetProperty("password", password);
            return this;
        }
        default ActiveMQComponentBuilder setDeliveryPersistent(
                boolean deliveryPersistent) {
            doSetProperty("deliveryPersistent", deliveryPersistent);
            return this;
        }
        default ActiveMQComponentBuilder setDeliveryMode(
                java.lang.Integer deliveryMode) {
            doSetProperty("deliveryMode", deliveryMode);
            return this;
        }
        default ActiveMQComponentBuilder setDurableSubscriptionName(
                java.lang.String durableSubscriptionName) {
            doSetProperty("durableSubscriptionName", durableSubscriptionName);
            return this;
        }
        default ActiveMQComponentBuilder setExceptionListener(
                javax.jms.ExceptionListener exceptionListener) {
            doSetProperty("exceptionListener", exceptionListener);
            return this;
        }
        default ActiveMQComponentBuilder setErrorHandler(
                org.springframework.util.ErrorHandler errorHandler) {
            doSetProperty("errorHandler", errorHandler);
            return this;
        }
        default ActiveMQComponentBuilder setErrorHandlerLoggingLevel(
                org.apache.camel.LoggingLevel errorHandlerLoggingLevel) {
            doSetProperty("errorHandlerLoggingLevel", errorHandlerLoggingLevel);
            return this;
        }
        default ActiveMQComponentBuilder setErrorHandlerLogStackTrace(
                boolean errorHandlerLogStackTrace) {
            doSetProperty("errorHandlerLogStackTrace", errorHandlerLogStackTrace);
            return this;
        }
        default ActiveMQComponentBuilder setExplicitQosEnabled(
                boolean explicitQosEnabled) {
            doSetProperty("explicitQosEnabled", explicitQosEnabled);
            return this;
        }
        default ActiveMQComponentBuilder setExposeListenerSession(
                boolean exposeListenerSession) {
            doSetProperty("exposeListenerSession", exposeListenerSession);
            return this;
        }
        default ActiveMQComponentBuilder setIdleTaskExecutionLimit(
                int idleTaskExecutionLimit) {
            doSetProperty("idleTaskExecutionLimit", idleTaskExecutionLimit);
            return this;
        }
        default ActiveMQComponentBuilder setIdleConsumerLimit(
                int idleConsumerLimit) {
            doSetProperty("idleConsumerLimit", idleConsumerLimit);
            return this;
        }
        default ActiveMQComponentBuilder setMaxConcurrentConsumers(
                int maxConcurrentConsumers) {
            doSetProperty("maxConcurrentConsumers", maxConcurrentConsumers);
            return this;
        }
        default ActiveMQComponentBuilder setReplyToMaxConcurrentConsumers(
                int replyToMaxConcurrentConsumers) {
            doSetProperty("replyToMaxConcurrentConsumers", replyToMaxConcurrentConsumers);
            return this;
        }
        default ActiveMQComponentBuilder setReplyOnTimeoutToMaxConcurrentConsumers(
                int replyOnTimeoutToMaxConcurrentConsumers) {
            doSetProperty("replyOnTimeoutToMaxConcurrentConsumers", replyOnTimeoutToMaxConcurrentConsumers);
            return this;
        }
        default ActiveMQComponentBuilder setMaxMessagesPerTask(
                int maxMessagesPerTask) {
            doSetProperty("maxMessagesPerTask", maxMessagesPerTask);
            return this;
        }
        default ActiveMQComponentBuilder setMessageConverter(
                org.springframework.jms.support.converter.MessageConverter messageConverter) {
            doSetProperty("messageConverter", messageConverter);
            return this;
        }
        default ActiveMQComponentBuilder setMapJmsMessage(boolean mapJmsMessage) {
            doSetProperty("mapJmsMessage", mapJmsMessage);
            return this;
        }
        default ActiveMQComponentBuilder setMessageIdEnabled(
                boolean messageIdEnabled) {
            doSetProperty("messageIdEnabled", messageIdEnabled);
            return this;
        }
        default ActiveMQComponentBuilder setMessageTimestampEnabled(
                boolean messageTimestampEnabled) {
            doSetProperty("messageTimestampEnabled", messageTimestampEnabled);
            return this;
        }
        default ActiveMQComponentBuilder setAlwaysCopyMessage(
                boolean alwaysCopyMessage) {
            doSetProperty("alwaysCopyMessage", alwaysCopyMessage);
            return this;
        }
        default ActiveMQComponentBuilder setUseMessageIDAsCorrelationID(
                boolean useMessageIDAsCorrelationID) {
            doSetProperty("useMessageIDAsCorrelationID", useMessageIDAsCorrelationID);
            return this;
        }
        default ActiveMQComponentBuilder setPriority(int priority) {
            doSetProperty("priority", priority);
            return this;
        }
        default ActiveMQComponentBuilder setPubSubNoLocal(boolean pubSubNoLocal) {
            doSetProperty("pubSubNoLocal", pubSubNoLocal);
            return this;
        }
        default ActiveMQComponentBuilder setReceiveTimeout(long receiveTimeout) {
            doSetProperty("receiveTimeout", receiveTimeout);
            return this;
        }
        default ActiveMQComponentBuilder setRecoveryInterval(
                long recoveryInterval) {
            doSetProperty("recoveryInterval", recoveryInterval);
            return this;
        }
        default ActiveMQComponentBuilder setTaskExecutor(
                org.springframework.core.task.TaskExecutor taskExecutor) {
            doSetProperty("taskExecutor", taskExecutor);
            return this;
        }
        default ActiveMQComponentBuilder setTimeToLive(long timeToLive) {
            doSetProperty("timeToLive", timeToLive);
            return this;
        }
        default ActiveMQComponentBuilder setTransacted(boolean transacted) {
            doSetProperty("transacted", transacted);
            return this;
        }
        default ActiveMQComponentBuilder setLazyCreateTransactionManager(
                boolean lazyCreateTransactionManager) {
            doSetProperty("lazyCreateTransactionManager", lazyCreateTransactionManager);
            return this;
        }
        default ActiveMQComponentBuilder setTransactionManager(
                org.springframework.transaction.PlatformTransactionManager transactionManager) {
            doSetProperty("transactionManager", transactionManager);
            return this;
        }
        default ActiveMQComponentBuilder setTransactionName(
                java.lang.String transactionName) {
            doSetProperty("transactionName", transactionName);
            return this;
        }
        default ActiveMQComponentBuilder setTransactionTimeout(
                int transactionTimeout) {
            doSetProperty("transactionTimeout", transactionTimeout);
            return this;
        }
        default ActiveMQComponentBuilder setTestConnectionOnStartup(
                boolean testConnectionOnStartup) {
            doSetProperty("testConnectionOnStartup", testConnectionOnStartup);
            return this;
        }
        default ActiveMQComponentBuilder setAsyncStartListener(
                boolean asyncStartListener) {
            doSetProperty("asyncStartListener", asyncStartListener);
            return this;
        }
        default ActiveMQComponentBuilder setAsyncStopListener(
                boolean asyncStopListener) {
            doSetProperty("asyncStopListener", asyncStopListener);
            return this;
        }
        default ActiveMQComponentBuilder setForceSendOriginalMessage(
                boolean forceSendOriginalMessage) {
            doSetProperty("forceSendOriginalMessage", forceSendOriginalMessage);
            return this;
        }
        default ActiveMQComponentBuilder setRequestTimeout(long requestTimeout) {
            doSetProperty("requestTimeout", requestTimeout);
            return this;
        }
        default ActiveMQComponentBuilder setRequestTimeoutCheckerInterval(
                long requestTimeoutCheckerInterval) {
            doSetProperty("requestTimeoutCheckerInterval", requestTimeoutCheckerInterval);
            return this;
        }
        default ActiveMQComponentBuilder setTransferExchange(
                boolean transferExchange) {
            doSetProperty("transferExchange", transferExchange);
            return this;
        }
        default ActiveMQComponentBuilder setTransferException(
                boolean transferException) {
            doSetProperty("transferException", transferException);
            return this;
        }
        default ActiveMQComponentBuilder setJmsOperations(
                org.springframework.jms.core.JmsOperations jmsOperations) {
            doSetProperty("jmsOperations", jmsOperations);
            return this;
        }
        default ActiveMQComponentBuilder setDestinationResolver(
                org.springframework.jms.support.destination.DestinationResolver destinationResolver) {
            doSetProperty("destinationResolver", destinationResolver);
            return this;
        }
        default ActiveMQComponentBuilder setReplyToType(
                org.apache.camel.component.jms.ReplyToType replyToType) {
            doSetProperty("replyToType", replyToType);
            return this;
        }
        default ActiveMQComponentBuilder setPreserveMessageQos(
                boolean preserveMessageQos) {
            doSetProperty("preserveMessageQos", preserveMessageQos);
            return this;
        }
        default ActiveMQComponentBuilder setAsyncConsumer(boolean asyncConsumer) {
            doSetProperty("asyncConsumer", asyncConsumer);
            return this;
        }
        default ActiveMQComponentBuilder setAllowNullBody(boolean allowNullBody) {
            doSetProperty("allowNullBody", allowNullBody);
            return this;
        }
        default ActiveMQComponentBuilder setIncludeSentJMSMessageID(
                boolean includeSentJMSMessageID) {
            doSetProperty("includeSentJMSMessageID", includeSentJMSMessageID);
            return this;
        }
        default ActiveMQComponentBuilder setIncludeAllJMSXProperties(
                boolean includeAllJMSXProperties) {
            doSetProperty("includeAllJMSXProperties", includeAllJMSXProperties);
            return this;
        }
        default ActiveMQComponentBuilder setDefaultTaskExecutorType(
                org.apache.camel.component.jms.DefaultTaskExecutorType defaultTaskExecutorType) {
            doSetProperty("defaultTaskExecutorType", defaultTaskExecutorType);
            return this;
        }
        default ActiveMQComponentBuilder setJmsKeyFormatStrategy(
                org.apache.camel.component.jms.JmsKeyFormatStrategy jmsKeyFormatStrategy) {
            doSetProperty("jmsKeyFormatStrategy", jmsKeyFormatStrategy);
            return this;
        }
        default ActiveMQComponentBuilder setAllowAdditionalHeaders(
                java.lang.String allowAdditionalHeaders) {
            doSetProperty("allowAdditionalHeaders", allowAdditionalHeaders);
            return this;
        }
        default ActiveMQComponentBuilder setQueueBrowseStrategy(
                org.apache.camel.component.jms.QueueBrowseStrategy queueBrowseStrategy) {
            doSetProperty("queueBrowseStrategy", queueBrowseStrategy);
            return this;
        }
        default ActiveMQComponentBuilder setMessageCreatedStrategy(
                org.apache.camel.component.jms.MessageCreatedStrategy messageCreatedStrategy) {
            doSetProperty("messageCreatedStrategy", messageCreatedStrategy);
            return this;
        }
        default ActiveMQComponentBuilder setWaitForProvisionCorrelationToBeUpdatedCounter(
                int waitForProvisionCorrelationToBeUpdatedCounter) {
            doSetProperty("waitForProvisionCorrelationToBeUpdatedCounter", waitForProvisionCorrelationToBeUpdatedCounter);
            return this;
        }
        default ActiveMQComponentBuilder setWaitForProvisionCorrelationToBeUpdatedThreadSleepingTime(
                long waitForProvisionCorrelationToBeUpdatedThreadSleepingTime) {
            doSetProperty("waitForProvisionCorrelationToBeUpdatedThreadSleepingTime", waitForProvisionCorrelationToBeUpdatedThreadSleepingTime);
            return this;
        }
        default ActiveMQComponentBuilder setCorrelationProperty(
                java.lang.String correlationProperty) {
            doSetProperty("correlationProperty", correlationProperty);
            return this;
        }
        default ActiveMQComponentBuilder setSubscriptionDurable(
                boolean subscriptionDurable) {
            doSetProperty("subscriptionDurable", subscriptionDurable);
            return this;
        }
        default ActiveMQComponentBuilder setSubscriptionShared(
                boolean subscriptionShared) {
            doSetProperty("subscriptionShared", subscriptionShared);
            return this;
        }
        default ActiveMQComponentBuilder setSubscriptionName(
                java.lang.String subscriptionName) {
            doSetProperty("subscriptionName", subscriptionName);
            return this;
        }
        default ActiveMQComponentBuilder setStreamMessageTypeEnabled(
                boolean streamMessageTypeEnabled) {
            doSetProperty("streamMessageTypeEnabled", streamMessageTypeEnabled);
            return this;
        }
        default ActiveMQComponentBuilder setFormatDateHeadersToIso8601(
                boolean formatDateHeadersToIso8601) {
            doSetProperty("formatDateHeadersToIso8601", formatDateHeadersToIso8601);
            return this;
        }
        default ActiveMQComponentBuilder setHeaderFilterStrategy(
                org.apache.camel.spi.HeaderFilterStrategy headerFilterStrategy) {
            doSetProperty("headerFilterStrategy", headerFilterStrategy);
            return this;
        }
        default ActiveMQComponentBuilder setBasicPropertyBinding(
                boolean basicPropertyBinding) {
            doSetProperty("basicPropertyBinding", basicPropertyBinding);
            return this;
        }
        default ActiveMQComponentBuilder setLazyStartProducer(
                boolean lazyStartProducer) {
            doSetProperty("lazyStartProducer", lazyStartProducer);
            return this;
        }
        default ActiveMQComponentBuilder setBridgeErrorHandler(
                boolean bridgeErrorHandler) {
            doSetProperty("bridgeErrorHandler", bridgeErrorHandler);
            return this;
        }
    }

    class ActiveMQComponentBuilderImpl
            extends
                AbstractComponentBuilder
            implements
                ActiveMQComponentBuilder {
        public ActiveMQComponentBuilderImpl() {
            super("activemq");
        }
        @Override
        protected Component buildConcreteComponent() {
            return new ActiveMQComponent();
        }
    }
}