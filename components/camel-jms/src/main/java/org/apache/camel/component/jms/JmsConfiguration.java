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
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.camel.LoggingLevel;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jms.JmsException;
import org.springframework.jms.connection.JmsTransactionManager;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.jms.core.SessionCallback;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.listener.SimpleMessageListenerContainer;
import org.springframework.jms.support.JmsUtils;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.destination.DestinationResolver;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.Assert;
import org.springframework.util.ErrorHandler;

import static org.apache.camel.component.jms.JmsMessageHelper.normalizeDestinationName;

/**
 * @version
 */
@UriParams
public class JmsConfiguration implements Cloneable {

    public static final String QUEUE_PREFIX = "queue:";
    public static final String TOPIC_PREFIX = "topic:";
    public static final String TEMP_QUEUE_PREFIX = "temp:queue:";
    public static final String TEMP_TOPIC_PREFIX = "temp:topic:";

    private static final Logger LOG = LoggerFactory.getLogger(JmsConfiguration.class);
    private JmsOperations jmsOperations;
    private DestinationResolver destinationResolver;
    private ConnectionFactory connectionFactory;
    private ConnectionFactory templateConnectionFactory;
    private ConnectionFactory listenerConnectionFactory;
    private int acknowledgementMode = -1;
    @UriParam
    private String acknowledgementModeName;
    // Used to configure the spring Container
    @UriParam
    private ExceptionListener exceptionListener;
    @UriParam(defaultValue = "Default")
    private ConsumerType consumerType = ConsumerType.Default;
    @UriParam
    private ErrorHandler errorHandler;
    @UriParam(defaultValue = "WARN")
    private LoggingLevel errorHandlerLoggingLevel = LoggingLevel.WARN;
    @UriParam(defaultValue = "true")
    private boolean errorHandlerLogStackTrace = true;
    @UriParam(defaultValue = "true")
    private boolean autoStartup = true;
    @UriParam
    private boolean acceptMessagesWhileStopping;
    @UriParam
    private String clientId;
    @UriParam
    private String durableSubscriptionName;
    private boolean subscriptionDurable;
    @UriParam
    private boolean exposeListenerSession = true;
    private TaskExecutor taskExecutor;
    @UriParam
    private boolean pubSubNoLocal;
    @UriParam(defaultValue = "1")
    private int concurrentConsumers = 1;
    @UriParam(defaultValue = "-1")
    private int maxMessagesPerTask = -1;
    private int cacheLevel = -1;
    @UriParam
    private String cacheLevelName;
    @UriParam(defaultValue = "5000")
    private long recoveryInterval = 5000;
    @UriParam(defaultValue = "1000")
    private long receiveTimeout = 1000;
    @UriParam(defaultValue = "20000")
    private long requestTimeout = 20000L;
    @UriParam(defaultValue = "1000")
    private long requestTimeoutCheckerInterval = 1000L;
    @UriParam(defaultValue = "1")
    private int idleTaskExecutionLimit = 1;
    @UriParam(defaultValue = "1")
    private int idleConsumerLimit = 1;
    @UriParam
    private int maxConcurrentConsumers;
    // JmsTemplate only
    @UriParam
    private Boolean explicitQosEnabled;
    @UriParam(defaultValue = "true")
    private boolean deliveryPersistent = true;
    @UriParam
    private Integer deliveryMode;
    @UriParam(defaultValue = "true")
    private boolean replyToDeliveryPersistent = true;
    @UriParam(defaultValue = "-1")
    private long timeToLive = -1;
    private MessageConverter messageConverter;
    @UriParam(defaultValue = "true")
    private boolean mapJmsMessage = true;
    @UriParam(defaultValue = "true")
    private boolean messageIdEnabled = true;
    @UriParam(defaultValue = "true")
    private boolean messageTimestampEnabled = true;
    @UriParam(defaultValue = "-1")
    private int priority = -1;
    // Transaction related configuration
    @UriParam
    private boolean transacted;
    private boolean transactedInOut;
    @UriParam(defaultValue = "true")
    private boolean lazyCreateTransactionManager = true;
    private PlatformTransactionManager transactionManager;
    @UriParam
    private String transactionName;
    @UriParam(defaultValue = "-1")
    private int transactionTimeout = -1;
    @UriParam
    private boolean preserveMessageQos;
    @UriParam
    private boolean disableReplyTo;
    @UriParam
    private boolean eagerLoadingOfProperties;
    // Always make a JMS message copy when it's passed to Producer
    @UriParam
    private boolean alwaysCopyMessage;
    @UriParam
    private boolean useMessageIDAsCorrelationID;
    private JmsProviderMetadata providerMetadata = new JmsProviderMetadata();
    private JmsOperations metadataJmsOperations;
    @UriParam
    private String replyToDestination;
    @UriParam
    private String replyToDestinationSelectorName;
    @UriParam
    private String replyToOverride;
    @UriParam
    private JmsMessageType jmsMessageType;
    @UriParam
    private JmsKeyFormatStrategy jmsKeyFormatStrategy;
    @UriParam
    private boolean transferExchange;
    @UriParam
    private boolean transferException;
    @UriParam
    private boolean testConnectionOnStartup;
    @UriParam
    private boolean asyncStartListener;
    @UriParam
    private boolean asyncStopListener;
    // if the message is a JmsMessage and mapJmsMessage=false, force the
    // producer to send the javax.jms.Message body to the next JMS destination
    @UriParam
    private boolean forceSendOriginalMessage;
    // to force disabling time to live (works in both in-only or in-out mode)
    @UriParam
    private boolean disableTimeToLive;
    @UriParam
    private ReplyToType replyToType;
    @UriParam
    private boolean asyncConsumer;
    // the cacheLevelName of reply manager
    @UriParam
    private String replyToCacheLevelName;
    @UriParam(defaultValue = "true")
    private boolean allowNullBody = true;
    private MessageListenerContainerFactory messageListenerContainerFactory;
    @UriParam
    private boolean includeSentJMSMessageID;
    @UriParam
    private DefaultTaskExecutorType defaultTaskExecutorType;
    @UriParam
    private boolean includeAllJMSXProperties;

    public JmsConfiguration() {
    }

    public JmsConfiguration(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * Returns a copy of this configuration
     */
    public JmsConfiguration copy() {
        try {
            return (JmsConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public static class CamelJmsTemplate extends JmsTemplate {
        private JmsConfiguration config;

        public CamelJmsTemplate(JmsConfiguration config, ConnectionFactory connectionFactory) {
            super(connectionFactory);
            this.config = config;
        }

        public void send(final String destinationName,
                         final MessageCreator messageCreator,
                         final MessageSentCallback callback) throws JmsException {
            execute(new SessionCallback<Object>() {
                public Object doInJms(Session session) throws JMSException {
                    Destination destination = resolveDestinationName(session, destinationName);
                    return doSendToDestination(destination, messageCreator, callback, session);
                }
            }, false);
        }

        public void send(final Destination destination,
                         final MessageCreator messageCreator,
                         final MessageSentCallback callback) throws JmsException {
            execute(new SessionCallback<Object>() {
                public Object doInJms(Session session) throws JMSException {
                    return doSendToDestination(destination, messageCreator, callback, session);
                }
            }, false);
        }

        public void send(final String destinationName,
                         final MessageCreator messageCreator) throws JmsException {
            execute(new SessionCallback<Object>() {
                public Object doInJms(Session session) throws JMSException {
                    Destination destination = resolveDestinationName(session, destinationName);
                    return doSendToDestination(destination, messageCreator, null, session);
                }
            }, false);
        }

        public void send(final Destination destination,
                         final MessageCreator messageCreator) throws JmsException {
            execute(new SessionCallback<Object>() {
                public Object doInJms(Session session) throws JMSException {
                    return doSendToDestination(destination, messageCreator, null, session);
                }
            }, false);
        }

        private Object doSendToDestination(final Destination destination,
                                           final MessageCreator messageCreator,
                                           final MessageSentCallback callback,
                                           final Session session) throws JMSException {

            Assert.notNull(messageCreator, "MessageCreator must not be null");
            MessageProducer producer = createProducer(session, destination);
            Message message;
            try {
                message = messageCreator.createMessage(session);
                doSend(producer, message);
                if (message != null && callback != null) {
                    callback.sent(session, message, destination);
                }
                // Check commit - avoid commit call within a JTA transaction.
                if (session.getTransacted() && isSessionLocallyTransacted(session)) {
                    // Transacted session created by this template -> commit.
                    JmsUtils.commitIfNecessary(session);
                }
            } finally {
                JmsUtils.closeMessageProducer(producer);
            }
            return null;
        }

        /**
         * Override so we can support preserving the Qos settings that have
         * been set on the message.
         */
        @Override
        protected void doSend(MessageProducer producer, Message message) throws JMSException {
            if (config.isPreserveMessageQos()) {
                long ttl = message.getJMSExpiration();
                if (ttl != 0) {
                    ttl = ttl - System.currentTimeMillis();
                    // Message had expired.. so set the ttl as small as possible
                    if (ttl <= 0) {
                        ttl = 1;
                    }
                }

                int priority = message.getJMSPriority();
                if (priority < 0 || priority > 9) {
                    // use priority from endpoint if not provided on message with a valid range
                    priority = this.getPriority();
                }

                // if a delivery mode was set as a JMS header then we have used a temporary
                // property to store it - CamelJMSDeliveryMode. Otherwise we could not keep
                // track whether it was set or not as getJMSDeliveryMode() will default return 1 regardless
                // if it was set or not, so we can never tell if end user provided it in a header
                int deliveryMode;
                if (JmsMessageHelper.hasProperty(message, JmsConstants.JMS_DELIVERY_MODE)) {
                    deliveryMode = message.getIntProperty(JmsConstants.JMS_DELIVERY_MODE);
                    // remove the temporary property
                    JmsMessageHelper.removeJmsProperty(message, JmsConstants.JMS_DELIVERY_MODE);
                } else {
                    // use the existing delivery mode from the message
                    deliveryMode = message.getJMSDeliveryMode();
                }

                // need to log just before so the message is 100% correct when logged
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Sending JMS message to: {} with message: {}", producer.getDestination(), message);
                }
                producer.send(message, deliveryMode, priority, ttl);
            } else {
                // need to log just before so the message is 100% correct when logged
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Sending JMS message to: {} with message: {}", producer.getDestination(), message);
                }
                super.doSend(producer, message);
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Sent JMS message to: {} with message: {}", producer.getDestination(), message);
                }
            }
        }
    }

    /**
     * Creates a {@link JmsOperations} object used for request/response using a request timeout value
     */
    public JmsOperations createInOutTemplate(JmsEndpoint endpoint, boolean pubSubDomain, String destination, long requestTimeout) {
        JmsOperations answer = createInOnlyTemplate(endpoint, pubSubDomain, destination);
        if (answer instanceof JmsTemplate && requestTimeout > 0) {
            JmsTemplate jmsTemplate = (JmsTemplate) answer;
            jmsTemplate.setExplicitQosEnabled(true);

            // prefer to use timeToLive over requestTimeout if both specified
            long ttl = timeToLive > 0 ? timeToLive : requestTimeout;
            if (ttl > 0 && !isDisableTimeToLive()) {
                // only use TTL if not disabled
                jmsTemplate.setTimeToLive(ttl);
            }

            jmsTemplate.setSessionTransacted(isTransactedInOut());
            if (isTransactedInOut()) {
                jmsTemplate.setSessionAcknowledgeMode(Session.SESSION_TRANSACTED);
            } else {
                if (acknowledgementMode >= 0) {
                    jmsTemplate.setSessionAcknowledgeMode(acknowledgementMode);
                } else if (acknowledgementModeName != null) {
                    jmsTemplate.setSessionAcknowledgeModeName(acknowledgementModeName);
                } else {
                    // default to AUTO
                    jmsTemplate.setSessionAcknowledgeMode(Session.AUTO_ACKNOWLEDGE);
                }
            }
        }
        return answer;
    }

    /**
     * Creates a {@link JmsOperations} object used for one way messaging
     */
    public JmsOperations createInOnlyTemplate(JmsEndpoint endpoint, boolean pubSubDomain, String destination) {
        if (jmsOperations != null) {
            return jmsOperations;
        }

        ConnectionFactory factory = getTemplateConnectionFactory();
        JmsTemplate template = new CamelJmsTemplate(this, factory);

        template.setPubSubDomain(pubSubDomain);
        if (destinationResolver != null) {
            template.setDestinationResolver(destinationResolver);
            if (endpoint instanceof DestinationEndpoint) {
                LOG.debug("You are overloading the destinationResolver property on a DestinationEndpoint; are you sure you want to do that?");
            }
        } else if (endpoint instanceof DestinationEndpoint) {
            DestinationEndpoint destinationEndpoint = (DestinationEndpoint) endpoint;
            template.setDestinationResolver(createDestinationResolver(destinationEndpoint));
        }
        template.setDefaultDestinationName(destination);

        template.setExplicitQosEnabled(isExplicitQosEnabled());

        // have to use one or the other.. doesn't make sense to use both
        if (deliveryMode != null) {
            template.setDeliveryMode(deliveryMode);
        } else {
            template.setDeliveryPersistent(deliveryPersistent);
        }

        if (messageConverter != null) {
            template.setMessageConverter(messageConverter);
        }
        template.setMessageIdEnabled(messageIdEnabled);
        template.setMessageTimestampEnabled(messageTimestampEnabled);
        if (priority >= 0) {
            template.setPriority(priority);
        }
        template.setPubSubNoLocal(pubSubNoLocal);
        // only set TTL if we have a positive value and it has not been disabled
        if (timeToLive >= 0 && !isDisableTimeToLive()) {
            template.setTimeToLive(timeToLive);
        }

        template.setSessionTransacted(transacted);
        if (transacted) {
            template.setSessionAcknowledgeMode(Session.SESSION_TRANSACTED);
        } else {
            // This is here for completeness, but the template should not get
            // used for receiving messages.
            if (acknowledgementMode >= 0) {
                template.setSessionAcknowledgeMode(acknowledgementMode);
            } else if (acknowledgementModeName != null) {
                template.setSessionAcknowledgeModeName(acknowledgementModeName);
            }
        }
        return template;
    }

    public AbstractMessageListenerContainer createMessageListenerContainer(JmsEndpoint endpoint) throws Exception {
        AbstractMessageListenerContainer container = chooseMessageListenerContainerImplementation(endpoint);
        configureMessageListenerContainer(container, endpoint);
        return container;
    }

    public AbstractMessageListenerContainer chooseMessageListenerContainerImplementation(JmsEndpoint endpoint) {
        switch (consumerType) {
        case Simple:
            return new SimpleJmsMessageListenerContainer(endpoint);
        case Default:
            return new DefaultJmsMessageListenerContainer(endpoint);
        case Custom:
            return getCustomMessageListenerContainer(endpoint);
        default:
            throw new IllegalArgumentException("Unknown consumer type: " + consumerType);
        }
    }

    private AbstractMessageListenerContainer getCustomMessageListenerContainer(JmsEndpoint endpoint) {
        if (messageListenerContainerFactory != null) {
            return messageListenerContainerFactory.createMessageListenerContainer(endpoint);
        }
        return null;
    }

    // Properties
    // -------------------------------------------------------------------------

    public ConsumerType getConsumerType() {
        return consumerType;
    }

    public void setConsumerType(ConsumerType consumerType) {
        this.consumerType = consumerType;
    }

    public ConnectionFactory getConnectionFactory() {
        if (connectionFactory == null) {
            connectionFactory = createConnectionFactory();
        }
        return connectionFactory;
    }

    /**
     * Sets the default connection factory to be used if a connection factory is
     * not specified for either
     * {@link #setTemplateConnectionFactory(ConnectionFactory)} or
     * {@link #setListenerConnectionFactory(ConnectionFactory)}
     *
     * @param connectionFactory the default connection factory to use
     */
    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public ConnectionFactory getListenerConnectionFactory() {
        if (listenerConnectionFactory == null) {
            listenerConnectionFactory = createListenerConnectionFactory();
        }
        return listenerConnectionFactory;
    }

    /**
     * Sets the connection factory to be used for consuming messages
     *
     * @param listenerConnectionFactory the connection factory to use for
     *                                  consuming messages
     */
    public void setListenerConnectionFactory(ConnectionFactory listenerConnectionFactory) {
        this.listenerConnectionFactory = listenerConnectionFactory;
    }

    public ConnectionFactory getTemplateConnectionFactory() {
        if (templateConnectionFactory == null) {
            templateConnectionFactory = createTemplateConnectionFactory();
        }
        return templateConnectionFactory;
    }

    /**
     * Sets the connection factory to be used for sending messages via the
     * {@link JmsTemplate} via {@link #createInOnlyTemplate(JmsEndpoint,boolean, String)}
     *
     * @param templateConnectionFactory the connection factory for sending messages
     */
    public void setTemplateConnectionFactory(ConnectionFactory templateConnectionFactory) {
        this.templateConnectionFactory = templateConnectionFactory;
    }

    public boolean isAutoStartup() {
        return autoStartup;
    }

    public void setAutoStartup(boolean autoStartup) {
        this.autoStartup = autoStartup;
    }

    public boolean isAcceptMessagesWhileStopping() {
        return acceptMessagesWhileStopping;
    }

    public void setAcceptMessagesWhileStopping(boolean acceptMessagesWhileStopping) {
        this.acceptMessagesWhileStopping = acceptMessagesWhileStopping;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String consumerClientId) {
        this.clientId = consumerClientId;
    }

    public String getDurableSubscriptionName() {
        return durableSubscriptionName;
    }

    public void setDurableSubscriptionName(String durableSubscriptionName) {
        this.durableSubscriptionName = durableSubscriptionName;
    }

    public ExceptionListener getExceptionListener() {
        return exceptionListener;
    }

    public void setExceptionListener(ExceptionListener exceptionListener) {
        this.exceptionListener = exceptionListener;
    }

    public void setErrorHandler(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    public LoggingLevel getErrorHandlerLoggingLevel() {
        return errorHandlerLoggingLevel;
    }

    public void setErrorHandlerLoggingLevel(LoggingLevel errorHandlerLoggingLevel) {
        this.errorHandlerLoggingLevel = errorHandlerLoggingLevel;
    }

    public boolean isErrorHandlerLogStackTrace() {
        return errorHandlerLogStackTrace;
    }

    public void setErrorHandlerLogStackTrace(boolean errorHandlerLogStackTrace) {
        this.errorHandlerLogStackTrace = errorHandlerLogStackTrace;
    }

    @Deprecated
    public boolean isSubscriptionDurable() {
        return subscriptionDurable;
    }

    @Deprecated
    public void setSubscriptionDurable(boolean subscriptionDurable) {
        this.subscriptionDurable = subscriptionDurable;
    }

    public String getAcknowledgementModeName() {
        return acknowledgementModeName;
    }

    public void setAcknowledgementModeName(String consumerAcknowledgementMode) {
        this.acknowledgementModeName = consumerAcknowledgementMode;
        this.acknowledgementMode = -1;
    }

    public boolean isExposeListenerSession() {
        return exposeListenerSession;
    }

    public void setExposeListenerSession(boolean exposeListenerSession) {
        this.exposeListenerSession = exposeListenerSession;
    }

    public TaskExecutor getTaskExecutor() {
        return taskExecutor;
    }

    public void setTaskExecutor(TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    public boolean isPubSubNoLocal() {
        return pubSubNoLocal;
    }

    public void setPubSubNoLocal(boolean pubSubNoLocal) {
        this.pubSubNoLocal = pubSubNoLocal;
    }

    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    public void setConcurrentConsumers(int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    public int getMaxMessagesPerTask() {
        return maxMessagesPerTask;
    }

    public void setMaxMessagesPerTask(int maxMessagesPerTask) {
        this.maxMessagesPerTask = maxMessagesPerTask;
    }

    public int getCacheLevel() {
        return cacheLevel;
    }

    public void setCacheLevel(int cacheLevel) {
        this.cacheLevel = cacheLevel;
    }

    public String getCacheLevelName() {
        return cacheLevelName;
    }

    public void setCacheLevelName(String cacheName) {
        this.cacheLevelName = cacheName;
    }

    public long getRecoveryInterval() {
        return recoveryInterval;
    }

    public void setRecoveryInterval(long recoveryInterval) {
        this.recoveryInterval = recoveryInterval;
    }

    public long getReceiveTimeout() {
        return receiveTimeout;
    }

    public void setReceiveTimeout(long receiveTimeout) {
        this.receiveTimeout = receiveTimeout;
    }

    public PlatformTransactionManager getTransactionManager() {
        if (transactionManager == null && isTransacted() && isLazyCreateTransactionManager()) {
            transactionManager = createTransactionManager();
        }
        return transactionManager;
    }

    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public String getTransactionName() {
        return transactionName;
    }

    public void setTransactionName(String transactionName) {
        this.transactionName = transactionName;
    }

    public int getTransactionTimeout() {
        return transactionTimeout;
    }

    public void setTransactionTimeout(int transactionTimeout) {
        this.transactionTimeout = transactionTimeout;
    }

    public int getIdleTaskExecutionLimit() {
        return idleTaskExecutionLimit;
    }

    public void setIdleTaskExecutionLimit(int idleTaskExecutionLimit) {
        this.idleTaskExecutionLimit = idleTaskExecutionLimit;
    }

    public int getIdleConsumerLimit() {
        return idleConsumerLimit;
    }

    public void setIdleConsumerLimit(int idleConsumerLimit) {
        this.idleConsumerLimit = idleConsumerLimit;
    }

    public int getMaxConcurrentConsumers() {
        return maxConcurrentConsumers;
    }

    public void setMaxConcurrentConsumers(int maxConcurrentConsumers) {
        this.maxConcurrentConsumers = maxConcurrentConsumers;
    }

    public boolean isExplicitQosEnabled() {
        return explicitQosEnabled != null ? explicitQosEnabled : false;
    }

    public void setExplicitQosEnabled(boolean explicitQosEnabled) {
        this.explicitQosEnabled = explicitQosEnabled;
    }

    public boolean isDeliveryPersistent() {
        return deliveryPersistent;
    }

    public void setDeliveryPersistent(boolean deliveryPersistent) {
        this.deliveryPersistent = deliveryPersistent;
        configuredQoS();
    }

    public Integer getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(Integer deliveryMode) {
        this.deliveryMode = deliveryMode;
        configuredQoS();
    }

    public boolean isReplyToDeliveryPersistent() {
        return replyToDeliveryPersistent;
    }

    public void setReplyToDeliveryPersistent(boolean replyToDeliveryPersistent) {
        this.replyToDeliveryPersistent = replyToDeliveryPersistent;
    }

    public long getTimeToLive() {
        return timeToLive;
    }

    public void setTimeToLive(long timeToLive) {
        this.timeToLive = timeToLive;
        configuredQoS();
    }

    public MessageConverter getMessageConverter() {
        return messageConverter;
    }

    public void setMessageConverter(MessageConverter messageConverter) {
        this.messageConverter = messageConverter;
    }

    public boolean isMapJmsMessage() {
        return mapJmsMessage;
    }

    public void setMapJmsMessage(boolean mapJmsMessage) {
        this.mapJmsMessage = mapJmsMessage;
    }

    public boolean isMessageIdEnabled() {
        return messageIdEnabled;
    }

    public void setMessageIdEnabled(boolean messageIdEnabled) {
        this.messageIdEnabled = messageIdEnabled;
    }

    public boolean isMessageTimestampEnabled() {
        return messageTimestampEnabled;
    }

    public void setMessageTimestampEnabled(boolean messageTimestampEnabled) {
        this.messageTimestampEnabled = messageTimestampEnabled;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
        configuredQoS();
    }

    public int getAcknowledgementMode() {
        return acknowledgementMode;
    }

    public void setAcknowledgementMode(int consumerAcknowledgementMode) {
        this.acknowledgementMode = consumerAcknowledgementMode;
        this.acknowledgementModeName = null;
    }

    public boolean isTransacted() {
        return transacted;
    }

    public void setTransacted(boolean consumerTransacted) {
        this.transacted = consumerTransacted;
    }

    /**
     * Should InOut operations (request reply) default to using transacted mode?
     * <p/>
     * By default this is false as you need to commit the outgoing request before you can consume the input
     */
    @Deprecated
    public boolean isTransactedInOut() {
        return transactedInOut;
    }

    @Deprecated
    public void setTransactedInOut(boolean transactedInOut) {
        this.transactedInOut = transactedInOut;
    }

    public boolean isLazyCreateTransactionManager() {
        return lazyCreateTransactionManager;
    }

    public void setLazyCreateTransactionManager(boolean lazyCreating) {
        this.lazyCreateTransactionManager = lazyCreating;
    }

    public boolean isEagerLoadingOfProperties() {
        return eagerLoadingOfProperties;
    }

    /**
     * Enables eager loading of JMS properties as soon as a message is loaded
     * which generally is inefficient as the JMS properties may not be required
     * but sometimes can catch early any issues with the underlying JMS provider
     * and the use of JMS properties
     *
     * @param eagerLoadingOfProperties whether or not to enable eager loading of
     *                                 JMS properties on inbound messages
     */
    public void setEagerLoadingOfProperties(boolean eagerLoadingOfProperties) {
        this.eagerLoadingOfProperties = eagerLoadingOfProperties;
    }

    public boolean isDisableReplyTo() {
        return disableReplyTo;
    }

    /**
     * Disables the use of the JMSReplyTo header for consumers so that inbound
     * messages are treated as InOnly rather than InOut requests.
     *
     * @param disableReplyTo whether or not to disable the use of JMSReplyTo
     *                       header indicating an InOut
     */
    public void setDisableReplyTo(boolean disableReplyTo) {
        this.disableReplyTo = disableReplyTo;
    }

    /**
     * Set to true if you want to send message using the QoS settings specified
     * on the message. Normally the QoS settings used are the one configured on
     * this Object.
     */
    public void setPreserveMessageQos(boolean preserveMessageQos) {
        this.preserveMessageQos = preserveMessageQos;
    }

    public JmsOperations getJmsOperations() {
        return jmsOperations;
    }

    public void setJmsOperations(JmsOperations jmsOperations) {
        this.jmsOperations = jmsOperations;
    }

    public DestinationResolver getDestinationResolver() {
        return destinationResolver;
    }

    public void setDestinationResolver(DestinationResolver destinationResolver) {
        this.destinationResolver = destinationResolver;
    }

    public JmsProviderMetadata getProviderMetadata() {
        return providerMetadata;
    }

    /**
     * Allows the provider metadata to be explicitly configured. Typically this is not required
     * and Camel will auto-detect the provider metadata from the underlying provider.
     */
    public void setProviderMetadata(JmsProviderMetadata providerMetadata) {
        this.providerMetadata = providerMetadata;
    }

    public JmsOperations getMetadataJmsOperations(JmsEndpoint endpoint) {
        if (metadataJmsOperations == null) {
            metadataJmsOperations = getJmsOperations();
            if (metadataJmsOperations == null) {
                metadataJmsOperations = createInOnlyTemplate(endpoint, false, null);
            }
        }
        return metadataJmsOperations;
    }

    /**
     * Sets the {@link JmsOperations} used to deduce the {@link JmsProviderMetadata} details which if none
     * is customized one is lazily created on demand
     */
    public void setMetadataJmsOperations(JmsOperations metadataJmsOperations) {
        this.metadataJmsOperations = metadataJmsOperations;
    }


    // Implementation methods
    // -------------------------------------------------------------------------

    public static DestinationResolver createDestinationResolver(final DestinationEndpoint destinationEndpoint) {
        return new DestinationResolver() {
            public Destination resolveDestinationName(Session session, String destinationName, boolean pubSubDomain) throws JMSException {
                return destinationEndpoint.getJmsDestination(session);
            }
        };
    }

    protected void configureMessageListenerContainer(AbstractMessageListenerContainer container,
                                                     JmsEndpoint endpoint) throws Exception {
        container.setConnectionFactory(getListenerConnectionFactory());
        if (endpoint instanceof DestinationEndpoint) {
            container.setDestinationResolver(createDestinationResolver((DestinationEndpoint) endpoint));
        } else if (destinationResolver != null) {
            container.setDestinationResolver(destinationResolver);
        }
        container.setAutoStartup(autoStartup);

        if (durableSubscriptionName != null) {
            container.setDurableSubscriptionName(durableSubscriptionName);
            container.setSubscriptionDurable(true);
        }
        if (clientId != null) {
            container.setClientId(clientId);
        }

        if (exceptionListener != null) {
            container.setExceptionListener(exceptionListener);
        }

        if (errorHandler != null) {
            container.setErrorHandler(errorHandler);
        } else {
            ErrorHandler handler = new DefaultSpringErrorHandler(endpoint.getCamelContext(), EndpointMessageListener.class, getErrorHandlerLoggingLevel(), isErrorHandlerLogStackTrace());
            container.setErrorHandler(handler);
        }

        container.setAcceptMessagesWhileStopping(acceptMessagesWhileStopping);
        container.setExposeListenerSession(exposeListenerSession);
        container.setSessionTransacted(transacted);
        if (transacted) {
            container.setSessionAcknowledgeMode(Session.SESSION_TRANSACTED);
        } else {
            if (acknowledgementMode >= 0) {
                container.setSessionAcknowledgeMode(acknowledgementMode);
            } else if (acknowledgementModeName != null) {
                container.setSessionAcknowledgeModeName(acknowledgementModeName);
            }
        }

        if (endpoint.getSelector() != null && endpoint.getSelector().length() != 0) {
            container.setMessageSelector(endpoint.getSelector());
        }

        if (container instanceof DefaultMessageListenerContainer) {
            DefaultMessageListenerContainer listenerContainer = (DefaultMessageListenerContainer) container;
            configureDefaultMessageListenerContainer(endpoint, listenerContainer);
        } else if (container instanceof SimpleMessageListenerContainer) {
            SimpleMessageListenerContainer listenerContainer = (SimpleMessageListenerContainer) container;
            configureSimpleMessageListenerContainer(listenerContainer);
        }
    }

    private void configureSimpleMessageListenerContainer(SimpleMessageListenerContainer listenerContainer) {
        if (maxConcurrentConsumers > 0) {
            if (maxConcurrentConsumers < concurrentConsumers) {
                throw new IllegalArgumentException("Property maxConcurrentConsumers: " + maxConcurrentConsumers + " must be higher than concurrentConsumers: "
                        + concurrentConsumers);
            }
            listenerContainer.setConcurrency(concurrentConsumers + "-" + maxConcurrentConsumers);
        } else if (concurrentConsumers >= 0) {
            listenerContainer.setConcurrentConsumers(concurrentConsumers);
        }

        listenerContainer.setPubSubNoLocal(pubSubNoLocal);
        if (taskExecutor != null) {
            listenerContainer.setTaskExecutor(taskExecutor);
        }
    }

    private void configureDefaultMessageListenerContainer(JmsEndpoint endpoint, DefaultMessageListenerContainer container) {
        if (concurrentConsumers >= 0) {
            container.setConcurrentConsumers(concurrentConsumers);
        }

        if (cacheLevel >= 0) {
            container.setCacheLevel(cacheLevel);
        } else if (cacheLevelName != null) {
            container.setCacheLevelName(cacheLevelName);
        } else {
            container.setCacheLevel(defaultCacheLevel(endpoint));
        }

        if (idleTaskExecutionLimit >= 0) {
            container.setIdleTaskExecutionLimit(idleTaskExecutionLimit);
        }
        if (idleConsumerLimit >= 0) {
            container.setIdleConsumerLimit(idleConsumerLimit);
        }
        if (maxConcurrentConsumers > 0) {
            if (maxConcurrentConsumers < concurrentConsumers) {
                throw new IllegalArgumentException("Property maxConcurrentConsumers: " + maxConcurrentConsumers
                        + " must be higher than concurrentConsumers: " + concurrentConsumers);
            }
            container.setMaxConcurrentConsumers(maxConcurrentConsumers);
        }
        if (maxMessagesPerTask >= 0) {
            container.setMaxMessagesPerTask(maxMessagesPerTask);
        }
        container.setPubSubNoLocal(pubSubNoLocal);
        if (receiveTimeout >= 0) {
            container.setReceiveTimeout(receiveTimeout);
        }
        if (recoveryInterval >= 0) {
            container.setRecoveryInterval(recoveryInterval);
        }
        if (taskExecutor != null) {
            container.setTaskExecutor(taskExecutor);
        }
        PlatformTransactionManager tm = getTransactionManager();
        if (tm != null) {
            container.setTransactionManager(tm);
        } else if (transactionManager == null && transacted && !lazyCreateTransactionManager) {
            container.setSessionTransacted(true);
        }
        if (transactionName != null) {
            container.setTransactionName(transactionName);
        }
        if (transactionTimeout >= 0) {
            container.setTransactionTimeout(transactionTimeout);
        }
    }

    public void configureMessageListener(EndpointMessageListener listener) {
        if (isDisableReplyTo()) {
            listener.setDisableReplyTo(true);
        }
        if (isEagerLoadingOfProperties()) {
            listener.setEagerLoadingOfProperties(true);
        }
        if (getReplyTo() != null) {
            listener.setReplyToDestination(getReplyTo());
        }

        // TODO: REVISIT: We really ought to change the model and let JmsProducer
        // and JmsConsumer have their own JmsConfiguration instance
        // This way producer's and consumer's QoS can differ and be
        // independently configured
        JmsOperations operations = listener.getTemplate();
        if (operations instanceof JmsTemplate) {
            JmsTemplate template = (JmsTemplate) operations;
            template.setDeliveryPersistent(isReplyToDeliveryPersistent());
        }
    }

    /**
     * Defaults the JMS cache level if none is explicitly specified.
     * <p/>
     * Will return <tt>CACHE_AUTO</tt> which will pickup and use <tt>CACHE_NONE</tt>
     * if transacted has been enabled, otherwise it will use <tt>CACHE_CONSUMER</tt>
     * which is the most efficient.
     *
     * @param endpoint the endpoint
     * @return the cache level
     */
    protected int defaultCacheLevel(JmsEndpoint endpoint) {
        return DefaultMessageListenerContainer.CACHE_AUTO;
    }

    /**
     * Factory method which allows derived classes to customize the lazy
     * creation
     */
    protected ConnectionFactory createConnectionFactory() {
        ObjectHelper.notNull(connectionFactory, "connectionFactory");
        return null;
    }

    /**
     * Factory method which allows derived classes to customize the lazy
     * creation
     */
    protected ConnectionFactory createListenerConnectionFactory() {
        return getConnectionFactory();
    }

    /**
     * Factory method which allows derived classes to customize the lazy
     * creation
     */
    protected ConnectionFactory createTemplateConnectionFactory() {
        return getConnectionFactory();
    }

    /**
     * Factory method which which allows derived classes to customize the lazy
     * transaction manager creation
     */
    protected PlatformTransactionManager createTransactionManager() {
        JmsTransactionManager answer = new JmsTransactionManager();
        answer.setConnectionFactory(getConnectionFactory());
        return answer;
    }

    public boolean isPreserveMessageQos() {
        return preserveMessageQos;
    }

    /**
     * When one of the QoS properties are configured such as {@link #setDeliveryPersistent(boolean)},
     * {@link #setPriority(int)} or {@link #setTimeToLive(long)} then we should auto default the
     * setting of {@link #setExplicitQosEnabled(boolean)} if its not been configured yet
     */
    protected void configuredQoS() {
        if (explicitQosEnabled == null) {
            explicitQosEnabled = true;
        }
    }

    public boolean isAlwaysCopyMessage() {
        return alwaysCopyMessage;
    }

    public void setAlwaysCopyMessage(boolean alwaysCopyMessage) {
        this.alwaysCopyMessage = alwaysCopyMessage;
    }

    public boolean isUseMessageIDAsCorrelationID() {
        return useMessageIDAsCorrelationID;
    }

    public void setUseMessageIDAsCorrelationID(boolean useMessageIDAsCorrelationID) {
        this.useMessageIDAsCorrelationID = useMessageIDAsCorrelationID;
    }

    public long getRequestTimeout() {
        return requestTimeout;
    }

    /**
     * Sets the timeout in milliseconds which requests should timeout after
     */
    public void setRequestTimeout(long requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public long getRequestTimeoutCheckerInterval() {
        return requestTimeoutCheckerInterval;
    }

    /**
     * Sets the interval in milliseconds how often the request timeout checker should run.
     */
    public void setRequestTimeoutCheckerInterval(long requestTimeoutCheckerInterval) {
        this.requestTimeoutCheckerInterval = requestTimeoutCheckerInterval;
    }

    public String getReplyTo() {
        return replyToDestination;
    }

    public void setReplyTo(String replyToDestination) {
        this.replyToDestination = normalizeDestinationName(replyToDestination);
    }

    public String getReplyToDestinationSelectorName() {
        return replyToDestinationSelectorName;
    }

    public void setReplyToDestinationSelectorName(String replyToDestinationSelectorName) {
        this.replyToDestinationSelectorName = replyToDestinationSelectorName;
        // in case of consumer -> producer and a named replyTo correlation selector
        // message pass through is impossible as we need to set the value of selector into
        // outgoing message, which would be read-only if pass through were to remain enabled
        if (replyToDestinationSelectorName != null) {
            setAlwaysCopyMessage(true);
        }
    }

    public String getReplyToOverride() {
        return replyToOverride;
    }

    public void setReplyToOverride(String replyToDestination) {
        this.replyToOverride = normalizeDestinationName(replyToDestination);
    }

    public JmsMessageType getJmsMessageType() {
        return jmsMessageType;
    }

    public void setJmsMessageType(JmsMessageType jmsMessageType) {
        if (jmsMessageType == JmsMessageType.Blob && !supportBlobMessage()) {
            throw new IllegalArgumentException("BlobMessage is not supported by this implementation");
        }
        this.jmsMessageType = jmsMessageType;
    }

    /**
     * Should get overridden by implementations which support BlobMessages
     *
     * @return false
     */
    protected boolean supportBlobMessage() {
        return false;
    }

    public JmsKeyFormatStrategy getJmsKeyFormatStrategy() {
        if (jmsKeyFormatStrategy == null) {
            jmsKeyFormatStrategy = new DefaultJmsKeyFormatStrategy();
        }
        return jmsKeyFormatStrategy;
    }

    public void setJmsKeyFormatStrategy(JmsKeyFormatStrategy jmsKeyFormatStrategy) {
        this.jmsKeyFormatStrategy = jmsKeyFormatStrategy;
    }

    public boolean isTransferExchange() {
        return transferExchange;
    }

    public void setTransferExchange(boolean transferExchange) {
        this.transferExchange = transferExchange;
    }

    public boolean isTransferException() {
        return transferException;
    }

    public void setTransferException(boolean transferException) {
        this.transferException = transferException;
    }

    public boolean isAsyncStartListener() {
        return asyncStartListener;
    }

    public void setAsyncStartListener(boolean asyncStartListener) {
        this.asyncStartListener = asyncStartListener;
    }

    public boolean isAsyncStopListener() {
        return asyncStopListener;
    }

    public void setAsyncStopListener(boolean asyncStopListener) {
        this.asyncStopListener = asyncStopListener;
    }

    public boolean isTestConnectionOnStartup() {
        return testConnectionOnStartup;
    }

    public void setTestConnectionOnStartup(boolean testConnectionOnStartup) {
        this.testConnectionOnStartup = testConnectionOnStartup;
    }

    public void setForceSendOriginalMessage(boolean forceSendOriginalMessage) {
        this.forceSendOriginalMessage = forceSendOriginalMessage;
    }

    public boolean isForceSendOriginalMessage() {
        return forceSendOriginalMessage;
    }

    public boolean isDisableTimeToLive() {
        return disableTimeToLive;
    }

    public void setDisableTimeToLive(boolean disableTimeToLive) {
        this.disableTimeToLive = disableTimeToLive;
    }

    /**
     * Gets the reply to type.
     * <p/>
     * Will only return a value if this option has been explicit configured.
     *
     * @return the reply type if configured, otherwise <tt>null</tt>
     */
    public ReplyToType getReplyToType() {
        return replyToType;
    }

    public void setReplyToType(ReplyToType replyToType) {
        this.replyToType = replyToType;
    }

    public boolean isAsyncConsumer() {
        return asyncConsumer;
    }

    /**
     * Sets whether asynchronous routing is enabled on {@link JmsConsumer}.
     * <p/>
     * By default this is <tt>false</tt>. If configured as <tt>true</tt> then
     * the {@link JmsConsumer} will process the {@link org.apache.camel.Exchange} asynchronous.
     */
    public void setAsyncConsumer(boolean asyncConsumer) {
        this.asyncConsumer = asyncConsumer;
    }

    public void setReplyToCacheLevelName(String name) {
        this.replyToCacheLevelName = name;
    }

    public String getReplyToCacheLevelName() {
        return replyToCacheLevelName;
    }

    public boolean isAllowNullBody() {
        return allowNullBody;
    }

    /**
     * Whether to allow sending with no doy (eg as null)
     */
    public void setAllowNullBody(boolean allowNullBody) {
        this.allowNullBody = allowNullBody;
    }

    public MessageListenerContainerFactory getMessageListenerContainerFactory() {
        return messageListenerContainerFactory;
    }

    public void setMessageListenerContainerFactory(MessageListenerContainerFactory messageListenerContainerFactory) {
        this.messageListenerContainerFactory = messageListenerContainerFactory;
    }

    public boolean isIncludeSentJMSMessageID() {
        return includeSentJMSMessageID;
    }

    /**
     * Whether to include the actual JMSMessageID set on the Message by the JMS vendor
     * on the Camel Message as a header when sending InOnly messages.
     * <p/>
     * Can be enable to gather the actual JMSMessageID for InOnly messages, which allows to access
     * the message id, which can be used for logging and tracing purposes.
     * <p/>
     * This option is default <tt>false</tt>.
     */
    public void setIncludeSentJMSMessageID(boolean includeSentJMSMessageID) {
        this.includeSentJMSMessageID = includeSentJMSMessageID;
    }

    public DefaultTaskExecutorType getDefaultTaskExecutorType() {
        return defaultTaskExecutorType;
    }

    /**
     * Indicates what type of {@link TaskExecutor} to use by default for JMS consumers.
     * Refer to the documentation of {@link DefaultTaskExecutorType} for available options.
     */
    public void setDefaultTaskExecutorType(DefaultTaskExecutorType defaultTaskExecutorType) {
        this.defaultTaskExecutorType = defaultTaskExecutorType;
    }

    public boolean isIncludeAllJMSXProperties() {
        return includeAllJMSXProperties;
    }

    /**
     * Whether to include all <tt>JMSX</tt> properties as Camel headers when binding from JMS to Camel Message.
     * <p/>
     * By default a number of properties is excluded accordingly to the table of JMS properties in the JMS 1.1 spec,
     * on page 39.
     */
    public void setIncludeAllJMSXProperties(boolean includeAllJMSXProperties) {
        this.includeAllJMSXProperties = includeAllJMSXProperties;
    }
}
