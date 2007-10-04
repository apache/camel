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
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.QueueSender;
import javax.jms.Session;
import javax.jms.TopicPublisher;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.ObjectHelper;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.JmsTemplate102;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer102;
import org.springframework.jms.listener.SimpleMessageListenerContainer;
import org.springframework.jms.listener.SimpleMessageListenerContainer102;
import org.springframework.jms.listener.serversession.ServerSessionFactory;
import org.springframework.jms.listener.serversession.ServerSessionMessageListenerContainer;
import org.springframework.jms.listener.serversession.ServerSessionMessageListenerContainer102;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.destination.DestinationResolver;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * @version $Revision$
 */
public class JmsConfiguration implements Cloneable {
    protected static final String TRANSACTED = "TRANSACTED";
    protected static final String CLIENT_ACKNOWLEDGE = "CLIENT_ACKNOWLEDGE";
    protected static final String AUTO_ACKNOWLEDGE = "AUTO_ACKNOWLEDGE";
    protected static final String DUPS_OK_ACKNOWLEDGE = "DUPS_OK_ACKNOWLEDGE";

    private JmsOperations jmsOperations;
    private DestinationResolver destinationResolver;
    private ConnectionFactory connectionFactory;
    private ConnectionFactory templateConnectionFactory;
    private ConnectionFactory listenerConnectionFactory;
    private int acknowledgementMode = -1;
    private String acknowledgementModeName = null;
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
    private String cacheLevelName = "CACHE_CONNECTION";
    private long recoveryInterval = -1;
    private long receiveTimeout = -1;
    private int idleTaskExecutionLimit = 1;
    private int maxConcurrentConsumers = 1;
    // JmsTemplate only
    private boolean useVersion102;
    private boolean explicitQosEnabled;
    private boolean deliveryPersistent = true;
    private long timeToLive = -1;
    private MessageConverter messageConverter;
    private boolean messageIdEnabled = true;
    private boolean messageTimestampEnabled = true;
    private int priority = -1;
    // Transaction related configuration
    private boolean transacted;
    private PlatformTransactionManager transactionManager;
    private String transactionName;
    private int transactionTimeout = -1;
    private boolean preserveMessageQos;

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

    public JmsOperations createJmsOperations(boolean pubSubDomain, String destination) {
        
        if ( jmsOperations !=null ) {
            return jmsOperations;
        }
        
        ConnectionFactory factory = getTemplateConnectionFactory();

        // I whish the spring templates had built in support for preserving the message
        // qos when doing a send. :(  
        JmsTemplate template = useVersion102 ? new JmsTemplate102(factory, pubSubDomain) {
            /**
             * Override so we can support preserving the Qos settings that have
             * been set on the message.
             */
            @Override
            protected void doSend(MessageProducer producer, Message message) throws JMSException {
                if (preserveMessageQos) {
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
                        ((TopicPublisher)producer).publish(message, message.getJMSDeliveryMode(), message.getJMSPriority(), ttl);
                    } else {
                        ((QueueSender)producer).send(message, message.getJMSDeliveryMode(), message.getJMSPriority(), ttl);
                    }
                } else {
                    super.doSend(producer, message);
                }
            }
        } : new JmsTemplate(factory) {
            /**
             * Override so we can support preserving the Qos settings that have
             * been set on the message.
             */
            @Override
            protected void doSend(MessageProducer producer, Message message) throws JMSException {
                if (preserveMessageQos) {
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
        };
        
        template.setPubSubDomain(pubSubDomain);
        if( destinationResolver!=null ) {
            template.setDestinationResolver(destinationResolver);
        }
        template.setDefaultDestinationName(destination);
        
        template.setExplicitQosEnabled(explicitQosEnabled);
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
        }
        else {
            // This is here for completeness, but the template should not get used
            // for receiving messages.
            if (acknowledgementMode >= 0) {
                template.setSessionAcknowledgeMode(acknowledgementMode);
            } else if (acknowledgementModeName != null) {
                template.setSessionAcknowledgeModeName(acknowledgementModeName);
            }
        }
        return template;
    }

    public AbstractMessageListenerContainer createMessageListenerContainer() {
        AbstractMessageListenerContainer container = chooseMessageListenerContainerImplementation();
        configureMessageListenerContainer(container);
        return container;
    }

    protected void configureMessageListenerContainer(AbstractMessageListenerContainer container) {
        container.setConnectionFactory(getListenerConnectionFactory());
        if( destinationResolver!=null ) {
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
        }
        else {
            if (acknowledgementMode >= 0) {
                container.setSessionAcknowledgeMode(acknowledgementMode);
            } else if (acknowledgementModeName != null) {
                container.setSessionAcknowledgeModeName(acknowledgementModeName);
            }
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
                // Default to CACHE_CONSUMER unless specified. This works best
                // with most JMS providers.
                listenerContainer.setCacheLevel(DefaultMessageListenerContainer.CACHE_CONSUMER);
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
            if (transactionManager != null) {
                listenerContainer.setTransactionManager(transactionManager);
            }
            else if (transacted) {
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
     * {@link #createMessageListenerContainer()}
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
     * {@link JmsTemplate} via {@link #createJmsOperations(boolean, String)}
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
        return explicitQosEnabled;
    }

    public void setExplicitQosEnabled(boolean explicitQosEnabled) {
        this.explicitQosEnabled = explicitQosEnabled;
    }

    public boolean isDeliveryPersistent() {
        return deliveryPersistent;
    }

    public void setDeliveryPersistent(boolean deliveryPersistent) {
        this.deliveryPersistent = deliveryPersistent;
    }

    public long getTimeToLive() {
        return timeToLive;
    }

    public void setTimeToLive(long timeToLive) {
        this.timeToLive = timeToLive;
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

    // Implementation methods
    // -------------------------------------------------------------------------
    protected AbstractMessageListenerContainer chooseMessageListenerContainerImplementation() {
        // TODO we could allow a spring container to auto-inject these objects?
        switch (consumerType) {
        case Simple:
            return isUseVersion102() ? new SimpleMessageListenerContainer102() : new SimpleMessageListenerContainer();
        case ServerSessionPool:
            return isUseVersion102() ? new ServerSessionMessageListenerContainer102() : new ServerSessionMessageListenerContainer();
        case Default:
            return isUseVersion102() ? new DefaultMessageListenerContainer102() : new DefaultMessageListenerContainer();
        default:
            throw new IllegalArgumentException("Unknown consumer type: " + consumerType);
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
     * Set to true if you want to send message using the QoS settings specified 
     * on the message.  Normally the QoS settings used are the one configured
     * on this Object.
     * 
     * @param preserveMessageQos
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
}
