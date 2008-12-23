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
import javax.jms.QueueSender;
import javax.jms.Session;
import javax.jms.TopicPublisher;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PackageHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jms.JmsException;
import org.springframework.jms.connection.JmsResourceHolder;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.JmsTemplate102;
import org.springframework.jms.core.MessageCreator;
import org.springframework.jms.core.SessionCallback;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer102;
import org.springframework.jms.listener.SimpleMessageListenerContainer;
import org.springframework.jms.listener.SimpleMessageListenerContainer102;
import org.springframework.jms.listener.serversession.ServerSessionFactory;
import org.springframework.jms.listener.serversession.ServerSessionMessageListenerContainer;
import org.springframework.jms.listener.serversession.ServerSessionMessageListenerContainer102;
import org.springframework.jms.support.JmsUtils;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.destination.DestinationResolver;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.Assert;

import static org.apache.camel.util.ObjectHelper.removeStartingCharacters;


/**
 * @version $Revision$
 */
public class JmsConfiguration implements Cloneable {

    public static final String QUEUE_PREFIX = "queue:";
    public static final String TOPIC_PREFIX = "topic:";
    public static final String TEMP_QUEUE_PREFIX = "temp:queue:";
    public static final String TEMP_TOPIC_PREFIX = "temp:topic:";

    protected static final String TRANSACTED = "TRANSACTED";
    protected static final String CLIENT_ACKNOWLEDGE = "CLIENT_ACKNOWLEDGE";
    protected static final String AUTO_ACKNOWLEDGE = "AUTO_ACKNOWLEDGE";
    protected static final String DUPS_OK_ACKNOWLEDGE = "DUPS_OK_ACKNOWLEDGE";
    protected static final String REPLYTO_TEMP_DEST_AFFINITY_PER_COMPONENT = "component";
    protected static final String REPLYTO_TEMP_DEST_AFFINITY_PER_ENDPOINT = "endpoint";
    protected static final String REPLYTO_TEMP_DEST_AFFINITY_PER_PRODUCER = "producer";

    private static final transient Log LOG = LogFactory.getLog(JmsConfiguration.class);
    private JmsOperations jmsOperations;
    private DestinationResolver destinationResolver;
    private ConnectionFactory connectionFactory;
    private ConnectionFactory templateConnectionFactory;
    private ConnectionFactory listenerConnectionFactory;
    private int acknowledgementMode = -1;
    private String acknowledgementModeName;
    // Used to configure the spring Container
    private ExceptionListener exceptionListener;
    private ConsumerType consumerType = ConsumerType.Default;
    private boolean autoStartup = true;
    private boolean acceptMessagesWhileStopping;
    private String clientId;
    private String durableSubscriptionName;
    private boolean subscriptionDurable;
    private boolean exposeListenerSession = true;
    private TaskExecutor taskExecutor;
    private boolean pubSubNoLocal;
    private int concurrentConsumers = 1;
    private int maxMessagesPerTask = 1;
    private ServerSessionFactory serverSessionFactory;
    private int cacheLevel = -1;
    private String cacheLevelName;
    private long recoveryInterval = -1;
    private long receiveTimeout = -1;
    private long requestTimeout = 20000L;
    private int idleTaskExecutionLimit = 1;
    private int maxConcurrentConsumers = 1;
    // JmsTemplate only
    private boolean useVersion102;
    private Boolean explicitQosEnabled;
    private boolean deliveryPersistent = true;
    private boolean replyToDeliveryPersistent = true;
    private long timeToLive = -1;
    private MessageConverter messageConverter;
    private boolean messageIdEnabled = true;
    private boolean messageTimestampEnabled = true;
    private int priority = -1;
    // Transaction related configuration
    private boolean transacted;
    private boolean transactedInOut;
    private PlatformTransactionManager transactionManager;
    private String transactionName;
    private int transactionTimeout = -1;
    private boolean preserveMessageQos;
    private long requestMapPurgePollTimeMillis = 1000L;
    private boolean disableReplyTo;
    private boolean eagerLoadingOfProperties;
    // Always make a JMS message copy when it's passed to Producer
    private boolean alwaysCopyMessage;
    private boolean useMessageIDAsCorrelationID;
    private JmsProviderMetadata providerMetadata = new JmsProviderMetadata();
    private JmsOperations metadataJmsOperations;
    // defines the component created temporary replyTo destination sharing strategy:
    // possible values are: "component", "endpoint", "producer"
    // component - a single temp queue is shared among all producers for a given component instance
    // endpoint - a single temp queue is shared among all producers for a given endpoint instance
    // producer - a single temp queue is created per producer
    private String replyToTempDestinationAffinity = REPLYTO_TEMP_DEST_AFFINITY_PER_ENDPOINT;
    private String replyToDestination;
    private String replyToDestinationSelectorName;

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
            return (JmsConfiguration)clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }


    public static interface MessageSentCallback {
        void sent(Message message);
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
            execute(new SessionCallback() {
                public Object doInJms(Session session) throws JMSException {
                    Destination destination = resolveDestinationName(session, destinationName);
                    Assert.notNull(messageCreator, "MessageCreator must not be null");
                    MessageProducer producer = createProducer(session, destination);
                    Message message = null;
                    try {
                        message = messageCreator.createMessage(session);
                        if (logger.isDebugEnabled()) {
                            logger.debug("Sending created message: " + message);
                        }
                        doSend(producer, message);
                        // Check commit - avoid commit call within a JTA transaction.
                        if (session.getTransacted() && isSessionLocallyTransacted(session)) {
                            // Transacted session created by this template -> commit.
                            JmsUtils.commitIfNecessary(session);
                        }
                    } finally {
                        JmsUtils.closeMessageProducer(producer);
                    }
                    if (message != null && callback != null) {
                        callback.sent(message);
                    }
                    return null;
                }
            }, false);
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
                    // Message had expired.. so set the ttl as small as
                    // possible
                    if (ttl <= 0) {
                        ttl = 1;
                    }
                }
                producer.send(message, message.getJMSDeliveryMode(), message.getJMSPriority(), ttl);
            } else {
                super.doSend(producer, message);
            }
        }
    }

    public static class CamelJmsTeemplate102 extends JmsTemplate102 {
        private JmsConfiguration config;

        public CamelJmsTeemplate102(JmsConfiguration config, ConnectionFactory connectionFactory, boolean pubSubDomain) {
            super(connectionFactory, pubSubDomain);
            this.config = config;
        }

        public void send(final String destinationName,
                final MessageCreator messageCreator,
                final MessageSentCallback callback) throws JmsException {
            execute(new SessionCallback() {
                public Object doInJms(Session session) throws JMSException {
                    Destination destination = resolveDestinationName(session, destinationName);
                    Assert.notNull(messageCreator, "MessageCreator must not be null");
                    MessageProducer producer = createProducer(session, destination);
                    Message message = null;
                    try {
                        message = messageCreator.createMessage(session);
                        if (logger.isDebugEnabled()) {
                            logger.debug("Sending created message: " + message);
                        }
                        doSend(producer, message);
                        // Check commit - avoid commit call within a JTA
                        // transaction.
                        if (session.getTransacted() && isSessionLocallyTransacted(session)) {
                            // Transacted session created by this template ->
                            // commit.
                            JmsUtils.commitIfNecessary(session);
                        }
                    } finally {
                        JmsUtils.closeMessageProducer(producer);
                    }
                    if (message != null && callback != null) {
                        callback.sent(message);
                    }
                    return null;
                }
            }, false);
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
                    // Message had expired.. so set the ttl as small as
                    // possible
                    if (ttl <= 0) {
                        ttl = 1;
                    }
                }
                if (isPubSubDomain()) {
                    ((TopicPublisher) producer).publish(message, message.getJMSDeliveryMode(),
                                                        message.getJMSPriority(), ttl);
                } else {
                    ((QueueSender) producer).send(message, message.getJMSDeliveryMode(),
                                                  message.getJMSPriority(), ttl);
                }
            } else {
                super.doSend(producer, message);
            }
        }
    }


    /**
     * Creates a {@link JmsOperations} object used for request/response using a request
     * timeout value
     */
    public JmsOperations createInOutTemplate(JmsEndpoint endpoint, boolean pubSubDomain, String destination, long requestTimeout) {
        JmsOperations answer = createInOnlyTemplate(endpoint, pubSubDomain, destination);
        if (answer instanceof JmsTemplate && requestTimeout > 0) {
            JmsTemplate jmsTemplate = (JmsTemplate)answer;
            jmsTemplate.setExplicitQosEnabled(true);
            jmsTemplate.setTimeToLive(requestTimeout);
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

        JmsTemplate template = useVersion102
            ? new CamelJmsTeemplate102(this, factory, pubSubDomain)
            : new CamelJmsTemplate(this, factory);

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
        template.setDeliveryPersistent(deliveryPersistent);
        if (messageConverter != null) {
            template.setMessageConverter(messageConverter);
        }
        template.setMessageIdEnabled(messageIdEnabled);
        template.setMessageTimestampEnabled(messageTimestampEnabled);
        if (priority >= 0) {
            template.setPriority(priority);
        }
        template.setPubSubNoLocal(pubSubNoLocal);
        if (receiveTimeout >= 0) {
            template.setReceiveTimeout(receiveTimeout);
        }
        if (timeToLive >= 0) {
            template.setTimeToLive(timeToLive);
        }

        template.setSessionTransacted(transacted);
        if (transacted) {
            template.setSessionAcknowledgeMode(Session.SESSION_TRANSACTED);
        } else {
            // This is here for completeness, but the template should not get
            // used
            // for receiving messages.
            if (acknowledgementMode >= 0) {
                template.setSessionAcknowledgeMode(acknowledgementMode);
            } else if (acknowledgementModeName != null) {
                template.setSessionAcknowledgeModeName(acknowledgementModeName);
            }
        }
        return template;
    }

    public AbstractMessageListenerContainer createMessageListenerContainer(JmsEndpoint endpoint) {
        AbstractMessageListenerContainer container = chooseMessageListenerContainerImplementation();
        configureMessageListenerContainer(container, endpoint);
        return container;
    }


    // Properties
    // -------------------------------------------------------------------------
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
     * Sets the connection factory to be used for consuming messages via the
     * {@link #createMessageListenerContainer(JmsEndpoint)}
     *
     * @param listenerConnectionFactory the connection factory to use for
     *                consuming messages
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
     * @param templateConnectionFactory the connection factory for sending
     *                messages
     */
    public void setTemplateConnectionFactory(ConnectionFactory templateConnectionFactory) {
        this.templateConnectionFactory = templateConnectionFactory;
    }

    public boolean isUseVersion102() {
        return useVersion102;
    }

    public void setUseVersion102(boolean useVersion102) {
        this.useVersion102 = useVersion102;
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

    public boolean isSubscriptionDurable() {
        return subscriptionDurable;
    }

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

    public ServerSessionFactory getServerSessionFactory() {
        return serverSessionFactory;
    }

    public void setServerSessionFactory(ServerSessionFactory serverSessionFactory) {
        this.serverSessionFactory = serverSessionFactory;
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

    public ConsumerType getConsumerType() {
        return consumerType;
    }

    public void setConsumerType(ConsumerType consumerType) {
        this.consumerType = consumerType;
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
     *
     * By default this is false as you need to commit the outgoing request before you can consume the input
     *
     * @return
     */
    public boolean isTransactedInOut() {
        return transactedInOut;
    }

    public void setTransactedInOut(boolean transactedInOut) {
        this.transactedInOut = transactedInOut;
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
     *                JMS properties on inbound messages
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
     *                header indicating an InOut
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

    public long getRequestMapPurgePollTimeMillis() {
        return requestMapPurgePollTimeMillis;
    }

    /**
     * Sets the frequency that the requestMap for InOut exchanges is purged for
     * timed out message exchanges
     */
    public void setRequestMapPurgePollTimeMillis(long requestMapPurgePollTimeMillis) {
        this.requestMapPurgePollTimeMillis = requestMapPurgePollTimeMillis;
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
                                                     JmsEndpoint endpoint) {
        container.setConnectionFactory(getListenerConnectionFactory());
        if (endpoint instanceof DestinationEndpoint) {
            container.setDestinationResolver(createDestinationResolver((DestinationEndpoint) endpoint));
        } else if (destinationResolver != null) {
            container.setDestinationResolver(destinationResolver);
        }
        if (autoStartup) {
            container.setAutoStartup(true);
        }
        if (clientId != null) {
            container.setClientId(clientId);
        }
        container.setSubscriptionDurable(subscriptionDurable);
        if (durableSubscriptionName != null) {
            container.setDurableSubscriptionName(durableSubscriptionName);
        }

        // lets default to durable subscription if the subscriber name and
        // client ID are specified (as there's
        // no reason to specify them if not! :)
        if (durableSubscriptionName != null && clientId != null) {
            container.setSubscriptionDurable(true);
        }

        if (exceptionListener != null) {
            container.setExceptionListener(exceptionListener);
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
            // this includes DefaultMessageListenerContainer102
            DefaultMessageListenerContainer listenerContainer = (DefaultMessageListenerContainer)container;
            if (concurrentConsumers >= 0) {
                listenerContainer.setConcurrentConsumers(concurrentConsumers);
            }

            if (cacheLevel >= 0) {
                listenerContainer.setCacheLevel(cacheLevel);
            } else if (cacheLevelName != null) {
                listenerContainer.setCacheLevelName(cacheLevelName);
            } else {
                listenerContainer.setCacheLevel(defaultCacheLevel(endpoint));
            }

            if (idleTaskExecutionLimit >= 0) {
                listenerContainer.setIdleTaskExecutionLimit(idleTaskExecutionLimit);
            }
            if (maxConcurrentConsumers >= 0) {
                listenerContainer.setMaxConcurrentConsumers(maxConcurrentConsumers);
            }
            if (maxMessagesPerTask >= 0) {
                listenerContainer.setMaxMessagesPerTask(maxMessagesPerTask);
            }
            listenerContainer.setPubSubNoLocal(pubSubNoLocal);
            if (receiveTimeout >= 0) {
                listenerContainer.setReceiveTimeout(receiveTimeout);
            }
            if (recoveryInterval >= 0) {
                listenerContainer.setRecoveryInterval(recoveryInterval);
            }
            if (taskExecutor != null) {
                listenerContainer.setTaskExecutor(taskExecutor);
            }
            PlatformTransactionManager tm = getTransactionManager();
            if (tm != null && transacted) {
                listenerContainer.setTransactionManager(tm);
            } else if (transacted) {
                throw new IllegalArgumentException("Property transacted is enabled but a transactionManager was not injected!");
            }
            if (transactionName != null) {
                listenerContainer.setTransactionName(transactionName);
            }
            if (transactionTimeout >= 0) {
                listenerContainer.setTransactionTimeout(transactionTimeout);
            }
        } else if (container instanceof ServerSessionMessageListenerContainer) {
            // this includes ServerSessionMessageListenerContainer102
            ServerSessionMessageListenerContainer listenerContainer = (ServerSessionMessageListenerContainer)container;
            if (maxMessagesPerTask >= 0) {
                listenerContainer.setMaxMessagesPerTask(maxMessagesPerTask);
            }
            if (serverSessionFactory != null) {
                listenerContainer.setServerSessionFactory(serverSessionFactory);
            }
        } else if (container instanceof SimpleMessageListenerContainer) {
            // this includes SimpleMessageListenerContainer102
            SimpleMessageListenerContainer listenerContainer = (SimpleMessageListenerContainer)container;
            if (concurrentConsumers >= 0) {
                listenerContainer.setConcurrentConsumers(concurrentConsumers);
            }
            listenerContainer.setPubSubNoLocal(pubSubNoLocal);
            if (taskExecutor != null) {
                listenerContainer.setTaskExecutor(taskExecutor);
            }
        }
    }

    public void configure(EndpointMessageListener listener) {
        if (isDisableReplyTo()) {
            listener.setDisableReplyTo(true);
        }
        if (isEagerLoadingOfProperties()) {
            listener.setEagerLoadingOfProperties(true);
        }
        // TODO: REVISIT: We really ought to change the model and let JmsProducer
        // and JmsConsumer have their own JmsConfiguration instance
        // This way producer's and consumer's QoS can differ and be
        // independently configured
        JmsOperations operations = listener.getTemplate();
        if (operations instanceof JmsTemplate) {
            JmsTemplate template = (JmsTemplate)operations;
            template.setDeliveryPersistent(isReplyToDeliveryPersistent());
        }
    }

    public AbstractMessageListenerContainer chooseMessageListenerContainerImplementation() {
        // TODO we could allow a spring container to auto-inject these objects?
        switch (consumerType) {
        case Simple:
            return isUseVersion102()
                ? new SimpleMessageListenerContainer102() : new SimpleMessageListenerContainer();
        case ServerSessionPool:
            return isUseVersion102()
                ? new ServerSessionMessageListenerContainer102()
                : new ServerSessionMessageListenerContainer();
        case Default:
            return isUseVersion102()
                ? new DefaultMessageListenerContainer102() : new DefaultMessageListenerContainer();
        default:
            throw new IllegalArgumentException("Unknown consumer type: " + consumerType);
        }
    }

    /**
     * Defaults the JMS cache level if none is explicitly specified. Note that
     * due to this <a
     * href="http://opensource.atlassian.com/projects/spring/browse/SPR-3890">Spring
     * Bug</a> we cannot use CACHE_CONSUMER by default (which we should do as
     * its most efficient) unless the spring version is 2.5.1 or later. Instead
     * we use CACHE_CONNECTION - part from for non-durable topics which must use
     * CACHE_CONSUMER to avoid missing messages (due to the consumer being
     * created and destroyed per message).
     *
     * @param endpoint the endpoint
     * @return the cacne level
     */
    protected int defaultCacheLevel(JmsEndpoint endpoint) {
        // if we are on a new enough spring version we can assume CACHE_CONSUMER
        if (PackageHelper.isValidVersion("org.springframework.jms", 2.51D)) {
            return DefaultMessageListenerContainer.CACHE_CONSUMER;
        } else {
            if (endpoint.isPubSubDomain() && !isSubscriptionDurable()) {
                // we must cache the consumer or we will miss messages
                // see https://issues.apache.org/activemq/browse/CAMEL-253
                return DefaultMessageListenerContainer.CACHE_CONSUMER;
            } else {
                // to enable consuming and sending with a single JMS session (to
                // avoid XA) we can only use CACHE_CONNECTION
                // due to this bug :
                // http://opensource.atlassian.com/projects/spring/browse/SPR-3890
                return DefaultMessageListenerContainer.CACHE_CONNECTION;
            }
        }
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

    public String getReplyToTempDestinationAffinity() {
        return replyToTempDestinationAffinity;
    }

    public void setReplyToTempDestinationAffinity(
            String replyToTempDestinationAffinity) {
        this.replyToTempDestinationAffinity = replyToTempDestinationAffinity;
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

    public String getReplyTo() {
        return replyToDestination;
    }

    public void setReplyTo(String replyToDestination) {
        if (!replyToDestination.startsWith(QUEUE_PREFIX)) {
            throw new IllegalArgumentException("ReplyTo destination value has to be of type queue; "
                                              + "e.g: \"queue:replyQueue\"");
        }
        this.replyToDestination =
            removeStartingCharacters(replyToDestination.substring(QUEUE_PREFIX.length()), '/');
    }

    public String getReplyToDestinationSelectorName() {
        return replyToDestinationSelectorName;
    }

    public void setReplyToDestinationSelectorName(String replyToDestinationSelectorName) {
        this.replyToDestinationSelectorName = replyToDestinationSelectorName;
        // in case of consumer -> producer and a named replyTo correlation selector
        // message passthough is impossible as we need to set the value of selector into
        // outgoing message, which would be read-only if passthough were to remain enabled
        if (replyToDestinationSelectorName != null) {
            setAlwaysCopyMessage(true);
        }
    }
}
