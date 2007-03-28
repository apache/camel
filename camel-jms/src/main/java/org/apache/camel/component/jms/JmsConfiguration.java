/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.jms;

import org.apache.camel.RuntimeCamelException;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.JmsTemplate102;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.listener.SimpleMessageListenerContainer;
import org.springframework.jms.listener.serversession.ServerSessionFactory;
import org.springframework.jms.listener.serversession.ServerSessionMessageListenerContainer;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.jms.ConnectionFactory;
import javax.jms.ExceptionListener;

/**
 * @version $Revision$
 */
public class JmsConfiguration implements Cloneable {
    protected static final String TRANSACTED = "TRANSACTED";
    private ConnectionFactory connectionFactory;
    private ConnectionFactory producerConnectionFactory;
    private boolean useVersion102;
    private boolean autoStartup;
    private boolean acceptMessagesWhileStopping;
    private String consumerClientId;
    private String durableSubscriptionName;
    private ExceptionListener exceptionListener;
    private String producerAcknowledgementMode = TRANSACTED;
    private boolean subscriptionDurable;
    private String consumerAcknowledgementMode = TRANSACTED;
    private boolean exposeListenerSession;
    // not used for ServerSessionMessageListenerContainer 
    private TaskExecutor taskExecutor;
    // SimpleMessageListenerContainer only
    private boolean pubSubNoLocal;
    // not used for ServerSessionMessageListenerContainer
    private int concurrentConsumers = -1;
    // not used for SimpleMessageListenerContainer
    private int maxMessagesPerTask = -1;
    // ServerSessionMessageListenerContainer only
    private ServerSessionFactory serverSessionFactory;
    //  DefaultMessageListenerContainer only
    private int cacheLevel = -1;
    private String cacheName;
    private long recoveryInterval = -1;
    private long receiveTimeout = -1;
    private PlatformTransactionManager transactionManager;
    private String transactionName;
    private int transactionTimeout = -1;
    private int idleTaskExecutionLimit = -1;
    private int maxConcurrentConsumers = -1;
    // JmsTemplate only
    private boolean explicitQosEnabled;
    private boolean deliveryPersistent = true;
    private long timeToLive = -1;
    private MessageConverter messageConverter;
    private boolean messageIdEnabled = true;
    private boolean messageTimestampEnabled;
    private int priority = -1;

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
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public JmsOperations createJmsOperations(boolean pubSubDomain, String destination) {
        JmsTemplate template = useVersion102
                ? new JmsTemplate102(getProducerConnectionFactory(), pubSubDomain)
                : new JmsTemplate(getProducerConnectionFactory());
        template.setPubSubDomain(pubSubDomain);
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

        boolean transacted = TRANSACTED.equals(producerAcknowledgementMode);
        template.setSessionTransacted(transacted);
        if (!transacted) {
            // TODO not sure if Spring can handle TRANSACTED as an ack mode
            template.setSessionAcknowledgeModeName(producerAcknowledgementMode);
        }
        return template;
    }

    public AbstractMessageListenerContainer createMessageListenerContainer() {
        AbstractMessageListenerContainer container = chooseMessageListenerContainerImplementation();
        configureMessageListenerContainer(container);
        return container;
    }

    protected void configureMessageListenerContainer(AbstractMessageListenerContainer container) {
        container.setConnectionFactory(getConnectionFactory());
        if (autoStartup) {
            container.setAutoStartup(true);
        }
        if (consumerClientId != null) {
            container.setClientId(consumerClientId);
        }
        if (durableSubscriptionName != null) {
            container.setDurableSubscriptionName(durableSubscriptionName);
        }
        if (exceptionListener != null) {
            container.setExceptionListener(exceptionListener);
        }
        container.setAcceptMessagesWhileStopping(acceptMessagesWhileStopping);
        container.setExposeListenerSession(exposeListenerSession);
        boolean transacted = TRANSACTED.equals(consumerAcknowledgementMode);
        container.setSessionTransacted(transacted);
        if (!transacted) {
            // TODO not sure if Spring can handle TRANSACTED as an ack mode
            container.setSessionAcknowledgeModeName(consumerAcknowledgementMode);
        }
        container.setSubscriptionDurable(subscriptionDurable);

        if (container instanceof DefaultMessageListenerContainer) {
            // this includes DefaultMessageListenerContainer102
            DefaultMessageListenerContainer listenerContainer = (DefaultMessageListenerContainer) container;
            if (concurrentConsumers >= 0) {
                listenerContainer.setConcurrentConsumers(concurrentConsumers);
            }
            if (cacheLevel >= 0) {
                listenerContainer.setCacheLevel(cacheLevel);
            }
            if (cacheName != null) {
                listenerContainer.setCacheLevelName(cacheName);
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
            if (transactionName != null) {
                listenerContainer.setTransactionName(transactionName);
            }
            if (transactionTimeout >= 0) {
                listenerContainer.setTransactionTimeout(transactionTimeout);
            }
        }
        else if (container instanceof ServerSessionMessageListenerContainer) {
            // this includes ServerSessionMessageListenerContainer102
            ServerSessionMessageListenerContainer listenerContainer = (ServerSessionMessageListenerContainer) container;
            if (maxMessagesPerTask >= 0) {
                listenerContainer.setMaxMessagesPerTask(maxMessagesPerTask);
            }
            if (serverSessionFactory != null) {
                listenerContainer.setServerSessionFactory(serverSessionFactory);
            }
        }
        else if (container instanceof SimpleMessageListenerContainer) {
            // this includes SimpleMessageListenerContainer102
            SimpleMessageListenerContainer listenerContainer = (SimpleMessageListenerContainer) container;
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
    //-------------------------------------------------------------------------
    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public ConnectionFactory getProducerConnectionFactory() {
        if (producerConnectionFactory == null) {
            return getConnectionFactory();
        }
        return producerConnectionFactory;
    }

    /**
     * Allows the connection factory for the producer side (sending) to be different from the connection factory used for consuming.
     * By default the {@link #getConnectionFactory()} will be used for both.
     *
     * @param producerConnectionFactory the connection factory to be used for sending.
     */
    public void setProducerConnectionFactory(ConnectionFactory producerConnectionFactory) {
        this.producerConnectionFactory = producerConnectionFactory;
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

    public String getConsumerClientId() {
        return consumerClientId;
    }

    public void setConsumerClientId(String consumerClientId) {
        this.consumerClientId = consumerClientId;
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

    public String getProducerAcknowledgementMode() {
        return producerAcknowledgementMode;
    }

    public void setProducerAcknowledgementMode(String producerAcknowledgementMode) {
        this.producerAcknowledgementMode = producerAcknowledgementMode;
    }

    public boolean isSubscriptionDurable() {
        return subscriptionDurable;
    }

    public void setSubscriptionDurable(boolean subscriptionDurable) {
        this.subscriptionDurable = subscriptionDurable;
    }

    public String getConsumerAcknowledgementMode() {
        return consumerAcknowledgementMode;
    }

    public void setConsumerAcknowledgementMode(String consumerAcknowledgementMode) {
        this.consumerAcknowledgementMode = consumerAcknowledgementMode;
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

    public String getCacheName() {
        return cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
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

    // Implementation methods
    //-------------------------------------------------------------------------
    protected AbstractMessageListenerContainer chooseMessageListenerContainerImplementation() {
        // TODO use an enum to auto-switch container types?

        //return new SimpleMessageListenerContainer();
        return new DefaultMessageListenerContainer();
    }
}
