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

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.Topic;

import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Service;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.SynchronousDelegateProducer;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.listener.SimpleMessageListenerContainer;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.destination.DestinationResolver;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.ErrorHandler;

/**
 * The jms component allows messages to be sent to (or consumed from) a JMS Queue or Topic.
 *
 * This component uses Spring JMS and supports JMS 1.1 and 2.0 API.
 */
@ManagedResource(description = "Managed JMS Endpoint")
@UriEndpoint(firstVersion = "1.0.0", scheme = "jms", title = "JMS", syntax = "jms:destinationType:destinationName", label = "messaging")
@Metadata(excludeProperties = "bridgeErrorHandler")
public class JmsEndpoint extends DefaultEndpoint implements AsyncEndpoint, HeaderFilterStrategyAware, MultipleConsumersSupport, Service {

    private static final Logger LOG = LoggerFactory.getLogger(JmsEndpoint.class);

    private final AtomicInteger runningMessageListeners = new AtomicInteger();
    private boolean pubSubDomain;
    private JmsBinding binding;
    @UriPath(defaultValue = "queue", enums = "queue,topic,temp-queue,temp-topic", description = "The kind of destination to use")
    private String destinationType;
    @UriPath(description = "Name of the queue or topic to use as destination")
    @Metadata(required = true)
    private String destinationName;
    private Destination destination;
    @UriParam(label = "advanced", description = "To use a custom HeaderFilterStrategy to filter header to and from Camel message.")
    private HeaderFilterStrategy headerFilterStrategy;
    @UriParam
    private JmsConfiguration configuration;

    public JmsEndpoint() {
        this(null, null);
    }

    public JmsEndpoint(Topic destination) throws JMSException {
        this("jms:topic:" + destination.getTopicName(), null);
        this.destination = destination;
        this.destinationType = "topic";
    }

    public JmsEndpoint(String uri, JmsComponent component, String destinationName, boolean pubSubDomain, JmsConfiguration configuration) {
        super(UnsafeUriCharactersEncoder.encode(uri), component);
        this.configuration = configuration;
        this.destinationName = destinationName;
        this.pubSubDomain = pubSubDomain;
        if (pubSubDomain) {
            this.destinationType = "topic";
        } else {
            this.destinationType = "queue";
        }
    }

    public JmsEndpoint(String endpointUri, JmsBinding binding, JmsConfiguration configuration, String destinationName, boolean pubSubDomain) {
        super(UnsafeUriCharactersEncoder.encode(endpointUri), null);
        this.binding = binding;
        this.configuration = configuration;
        this.destinationName = destinationName;
        this.pubSubDomain = pubSubDomain;
        if (pubSubDomain) {
            this.destinationType = "topic";
        } else {
            this.destinationType = "queue";
        }
    }

    public JmsEndpoint(String endpointUri, String destinationName, boolean pubSubDomain) {
        this(UnsafeUriCharactersEncoder.encode(endpointUri), null, new JmsConfiguration(), destinationName, pubSubDomain);
        this.binding = new JmsBinding(this);
        if (pubSubDomain) {
            this.destinationType = "topic";
        } else {
            this.destinationType = "queue";
        }
    }

    /**
     * Creates a pub-sub endpoint with the given destination
     */
    public JmsEndpoint(String endpointUri, String destinationName) {
        this(UnsafeUriCharactersEncoder.encode(endpointUri), destinationName, true);
    }

    /**
     * Returns a new JMS endpoint for the given JMS destination using the configuration from the given JMS component
     */
    public static JmsEndpoint newInstance(Destination destination, JmsComponent component) throws JMSException {
        JmsEndpoint answer = newInstance(destination);
        JmsConfiguration newConfiguration = component.getConfiguration().copy();
        answer.setConfiguration(newConfiguration);
        answer.setCamelContext(component.getCamelContext());
        return answer;
    }

    /**
     * Returns a new JMS endpoint for the given JMS destination
     */
    public static JmsEndpoint newInstance(Destination destination) throws JMSException {
        if (destination instanceof TemporaryQueue) {
            return new JmsTemporaryQueueEndpoint((TemporaryQueue) destination);
        } else if (destination instanceof TemporaryTopic) {
            return new JmsTemporaryTopicEndpoint((TemporaryTopic) destination);
        } else if (destination instanceof Queue) {
            return new JmsQueueEndpoint((Queue) destination);
        } else {
            return new JmsEndpoint((Topic) destination);
        }
    }

    @Override
    public Producer createProducer() throws Exception {
        Producer answer = new JmsProducer(this);
        if (isSynchronous()) {
            return new SynchronousDelegateProducer(answer);
        } else {
            return answer;
        }
    }

    @Override
    public JmsConsumer createConsumer(Processor processor) throws Exception {
        AbstractMessageListenerContainer listenerContainer = createMessageListenerContainer();
        return createConsumer(processor, listenerContainer);
    }

    public AbstractMessageListenerContainer createMessageListenerContainer() throws Exception {
        return configuration.createMessageListenerContainer(this);
    }

    public void configureListenerContainer(AbstractMessageListenerContainer listenerContainer, JmsConsumer consumer) {
        if (destinationName != null) {
            listenerContainer.setDestinationName(destinationName);
            LOG.debug("Using destinationName: {} on listenerContainer: {}", destinationName, listenerContainer);
        } else if (destination != null) {
            listenerContainer.setDestination(destination);
            LOG.debug("Using destination: {} on listenerContainer: {}", destinationName, listenerContainer);
        } else {
            DestinationResolver resolver = getDestinationResolver();
            if (resolver != null) {
                listenerContainer.setDestinationResolver(resolver);
            } else {
                throw new IllegalArgumentException("Neither destination, destinationName or destinationResolver are specified on this endpoint!");
            }
            LOG.debug("Using destinationResolver: {} on listenerContainer: {}", resolver, listenerContainer);
        }
        listenerContainer.setPubSubDomain(pubSubDomain);

        // include destination name as part of thread and transaction name
        String consumerName = getThreadName();

        if (configuration.getTaskExecutor() != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Using custom TaskExecutor: {} on listener container: {}", configuration.getTaskExecutor(), listenerContainer);
            }
            setContainerTaskExecutor(listenerContainer, configuration.getTaskExecutor());
            // we are using a shared thread pool that this listener container is using.
            // store a reference to the consumer, but we should not shutdown the thread pool when the consumer stops
            // as the lifecycle of the shared thread pool is handled elsewhere
            if (configuration.getTaskExecutor() instanceof ExecutorService) {
                consumer.setListenerContainerExecutorService((ExecutorService) configuration.getTaskExecutor(), false);
            }
        } else if (!(listenerContainer instanceof DefaultJmsMessageListenerContainer) || configuration.getDefaultTaskExecutorType() == null) {
            // preserve backwards compatibility if an explicit Default TaskExecutor Type was not set;
            // otherwise, defer the creation of the TaskExecutor
            // use a cached pool as DefaultMessageListenerContainer will throttle pool sizing
            ExecutorService executor = getCamelContext().getExecutorServiceManager().newCachedThreadPool(consumer, consumerName);
            setContainerTaskExecutor(listenerContainer, executor);
            // we created a new private thread pool that this listener container is using, now store a reference on the consumer
            // so when the consumer is stopped we can shutdown the thread pool also, to ensure all resources is shutdown
            consumer.setListenerContainerExecutorService(executor, true);
        } else {
            // do nothing, as we're working with a DefaultJmsMessageListenerContainer with an explicit DefaultTaskExecutorType,
            // so DefaultJmsMessageListenerContainer#createDefaultTaskExecutor will handle the creation
            LOG.debug("Deferring creation of TaskExecutor for listener container: {} as per policy: {}",
                    listenerContainer, getDefaultTaskExecutorType());
        }

        // set a default transaction name if none provided
        if (configuration.getTransactionName() == null) {
            if (listenerContainer instanceof DefaultMessageListenerContainer) {
                ((DefaultMessageListenerContainer) listenerContainer).setTransactionName(consumerName);
            }
        }

        // now configure the JMS 2.0 API
        if (configuration.getDurableSubscriptionName() != null) {
            listenerContainer.setDurableSubscriptionName(configuration.getDurableSubscriptionName());
        } else if (configuration.isSubscriptionDurable()) {
            listenerContainer.setSubscriptionDurable(true);
        }
        if (configuration.getSubscriptionName() != null) {
            listenerContainer.setSubscriptionName(configuration.getSubscriptionName());
        }
        listenerContainer.setSubscriptionShared(configuration.isSubscriptionShared());
    }

    private void setContainerTaskExecutor(AbstractMessageListenerContainer listenerContainer, Executor executor) {
        if (listenerContainer instanceof SimpleMessageListenerContainer) {
            ((SimpleMessageListenerContainer) listenerContainer).setTaskExecutor(executor);
        } else if (listenerContainer instanceof DefaultMessageListenerContainer) {
            ((DefaultMessageListenerContainer) listenerContainer).setTaskExecutor(executor);
        }
    }

    /**
     * Gets the destination name which was configured from the endpoint uri.
     *
     * @return the destination name resolved from the endpoint uri
     */
    public String getEndpointConfiguredDestinationName() {
        String remainder = StringHelper.after(getEndpointKey(), "//");
        if (remainder != null && remainder.contains("?")) {
            // remove parameters
            remainder = StringHelper.before(remainder, "?");
        }
        return JmsMessageHelper.normalizeDestinationName(remainder);
    }

    /**
     * Creates a consumer using the given processor and listener container
     *
     * @param processor         the processor to use to process the messages
     * @param listenerContainer the listener container
     * @return a newly created consumer
     * @throws Exception if the consumer cannot be created
     */
    public JmsConsumer createConsumer(Processor processor, AbstractMessageListenerContainer listenerContainer) throws Exception {
        JmsConsumer consumer = new JmsConsumer(this, processor, listenerContainer);
        configureListenerContainer(listenerContainer, consumer);
        configureConsumer(consumer);

        if (isBridgeErrorHandler()) {
            throw new IllegalArgumentException("BridgeErrorHandler is not support on JMS endpoint");
        }

        String replyTo = consumer.getEndpoint().getReplyTo();
        if (replyTo != null && consumer.getEndpoint().getDestinationName().equals(replyTo)) {
            throw new IllegalArgumentException("Invalid Endpoint configuration: " + consumer.getEndpoint()
                    + ". ReplyTo=" + replyTo + " cannot be the same as the destination name on the JmsConsumer as that"
                    + " would lead to the consumer sending reply messages to itself in an endless loop.");
        }

        return consumer;
    }

    @Override
    public PollingConsumer createPollingConsumer() throws Exception {
        JmsPollingConsumer answer = new JmsPollingConsumer(this, createInOnlyTemplate());
        configurePollingConsumer(answer);
        return answer;
    }

    @Override
    public Exchange createExchange(ExchangePattern pattern) {
        Exchange exchange = super.createExchange(pattern);
        exchange.setProperty(Exchange.BINDING, getBinding());
        return exchange;
    }

    public Exchange createExchange(Message message, Session session) {
        Exchange exchange = createExchange(getExchangePattern());
        exchange.setIn(new JmsMessage(exchange, message, session, getBinding()));
        return exchange;
    }

    /**
     * Factory method for creating a new template for InOnly message exchanges
     */
    public JmsOperations createInOnlyTemplate() {
        return configuration.createInOnlyTemplate(this, pubSubDomain, destinationName);
    }

    /**
     * Factory method for creating a new template for InOut message exchanges
     */
    public JmsOperations createInOutTemplate() {
        return configuration.createInOutTemplate(this, pubSubDomain, destinationName, configuration.getRequestTimeout());
    }

    @Override
    public boolean isMultipleConsumersSupported() {
        // JMS allows multiple consumers on both queues and topics
        return true;
    }

    public String getThreadName() {
        return "JmsConsumer[" + getEndpointConfiguredDestinationName() + "]";
    }

    // Properties
    // -------------------------------------------------------------------------

    @Override
    public JmsComponent getComponent() {
        return (JmsComponent) super.getComponent();
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        if (headerFilterStrategy == null) {
            headerFilterStrategy = new JmsHeaderFilterStrategy(isIncludeAllJMSXProperties());
        }
        return headerFilterStrategy;
    }

    /**
     * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
     */
    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        this.headerFilterStrategy = strategy;
    }

    public JmsBinding getBinding() {
        if (binding == null) {
            binding = createBinding();
        }
        return binding;
    }

    /**
     * Creates the {@link org.apache.camel.component.jms.JmsBinding} to use.
     */
    protected JmsBinding createBinding() {
        return new JmsBinding(this);
    }

    /**
     * Sets the binding used to convert from a Camel message to and from a JMS
     * message
     */
    public void setBinding(JmsBinding binding) {
        this.binding = binding;
    }

    public String getDestinationType() {
        return destinationType;
    }

    /**
     * The kind of destination to use
     */
    public void setDestinationType(String destinationType) {
        this.destinationType = destinationType;
    }

    public String getDestinationName() {
        return destinationName;
    }

    /**
     * Name of the queue or topic to use as destination
     */
    public void setDestinationName(String destinationName) {
        this.destinationName = destinationName;
    }

    public Destination getDestination() {
        return destination;
    }

    /**
     * Allows a specific JMS Destination object to be used as the destination
     */
    public void setDestination(Destination destination) {
        this.destination = destination;
    }

    public JmsConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(JmsConfiguration configuration) {
        this.configuration = configuration;
    }

    @ManagedAttribute
    public boolean isPubSubDomain() {
        return pubSubDomain;
    }

    protected ExecutorService getAsyncStartStopExecutorService() {
        if (getComponent() == null) {
            throw new IllegalStateException("AsyncStartStopListener requires JmsComponent to be configured on this endpoint: " + this);
        }
        // use shared thread pool from component
        return getComponent().getAsyncStartStopExecutorService();
    }

    public void onListenerContainerStarting(AbstractMessageListenerContainer container) {
        runningMessageListeners.incrementAndGet();
    }

    public void onListenerContainerStopped(AbstractMessageListenerContainer container) {
        runningMessageListeners.decrementAndGet();
    }

    /**
     * State whether this endpoint is running (eg started)
     */
    protected boolean isRunning() {
        return isStarted();
    }

    @Override
    public void stop() {
        int running = runningMessageListeners.get();
        if (running <= 0) {
            super.stop();
        } else {
            LOG.trace("There are still {} running message listeners. Cannot stop endpoint {}", running, this);
        }
    }

    @Override
    public void shutdown() {
        int running = runningMessageListeners.get();
        if (running <= 0) {
            super.shutdown();
        } else {
            LOG.trace("There are still {} running message listeners. Cannot shutdown endpoint {}", running, this);
        }
    }

    // Delegated properties from the configuration
    //-------------------------------------------------------------------------
    @ManagedAttribute
    public int getAcknowledgementMode() {
        return getConfiguration().getAcknowledgementMode();
    }

    @ManagedAttribute
    public String getAcknowledgementModeName() {
        return getConfiguration().getAcknowledgementModeName();
    }

    @ManagedAttribute
    public int getCacheLevel() {
        return getConfiguration().getCacheLevel();
    }

    @ManagedAttribute
    public String getCacheLevelName() {
        return getConfiguration().getCacheLevelName();
    }

    @ManagedAttribute
    public String getReplyToCacheLevelName() {
        return getConfiguration().getReplyToCacheLevelName();
    }

    @ManagedAttribute
    public String getClientId() {
        return getConfiguration().getClientId();
    }

    @ManagedAttribute
    public int getConcurrentConsumers() {
        return getConfiguration().getConcurrentConsumers();
    }

    @ManagedAttribute
    public int getReplyToConcurrentConsumers() {
        return getConfiguration().getReplyToConcurrentConsumers();
    }

    public ConnectionFactory getConnectionFactory() {
        return getConfiguration().getConnectionFactory();
    }

    public DestinationResolver getDestinationResolver() {
        return getConfiguration().getDestinationResolver();
    }

    @ManagedAttribute
    public String getDurableSubscriptionName() {
        return getConfiguration().getDurableSubscriptionName();
    }

    public ExceptionListener getExceptionListener() {
        return getConfiguration().getExceptionListener();
    }

    public ErrorHandler getErrorHandler() {
        return getConfiguration().getErrorHandler();
    }

    public LoggingLevel getErrorHandlerLoggingLevel() {
        return getConfiguration().getErrorHandlerLoggingLevel();
    }

    @ManagedAttribute
    public boolean isErrorHandlerLogStackTrace() {
        return getConfiguration().isErrorHandlerLogStackTrace();
    }

    @ManagedAttribute
    public void setErrorHandlerLogStackTrace(boolean errorHandlerLogStackTrace) {
        getConfiguration().setErrorHandlerLogStackTrace(errorHandlerLogStackTrace);
    }

    @ManagedAttribute
    public int getIdleTaskExecutionLimit() {
        return getConfiguration().getIdleTaskExecutionLimit();
    }

    @ManagedAttribute
    public int getIdleConsumerLimit() {
        return getConfiguration().getIdleConsumerLimit();
    }

    public JmsOperations getJmsOperations() {
        return getConfiguration().getJmsOperations();
    }

    public ConnectionFactory getListenerConnectionFactory() {
        return getConfiguration().getListenerConnectionFactory();
    }

    @ManagedAttribute
    public int getMaxConcurrentConsumers() {
        return getConfiguration().getMaxConcurrentConsumers();
    }

    @ManagedAttribute
    public int getReplyToMaxConcurrentConsumers() {
        return getConfiguration().getReplyToMaxConcurrentConsumers();
    }

    @ManagedAttribute
    public int getReplyToOnTimeoutMaxConcurrentConsumers() {
        return getConfiguration().getReplyToOnTimeoutMaxConcurrentConsumers();
    }

    @ManagedAttribute
    public int getMaxMessagesPerTask() {
        return getConfiguration().getMaxMessagesPerTask();
    }

    public MessageConverter getMessageConverter() {
        return getConfiguration().getMessageConverter();
    }

    @ManagedAttribute
    public int getPriority() {
        return getConfiguration().getPriority();
    }

    @ManagedAttribute
    public long getReceiveTimeout() {
        return getConfiguration().getReceiveTimeout();
    }

    @ManagedAttribute
    public long getRecoveryInterval() {
        return getConfiguration().getRecoveryInterval();
    }

    @ManagedAttribute
    public String getReplyTo() {
        return getConfiguration().getReplyTo();
    }

    @ManagedAttribute
    public String getReplyToOverride() {
        return getConfiguration().getReplyToOverride();
    }

    @ManagedAttribute
    public boolean isReplyToSameDestinationAllowed() {
        return getConfiguration().isReplyToSameDestinationAllowed();
    }

    @ManagedAttribute
    public String getReplyToDestinationSelectorName() {
        return getConfiguration().getReplyToDestinationSelectorName();
    }

    @ManagedAttribute
    public long getRequestTimeout() {
        return getConfiguration().getRequestTimeout();
    }

    @ManagedAttribute
    public long getRequestTimeoutCheckerInterval() {
        return getConfiguration().getRequestTimeoutCheckerInterval();
    }

    public TaskExecutor getTaskExecutor() {
        return getConfiguration().getTaskExecutor();
    }

    public ConnectionFactory getTemplateConnectionFactory() {
        return getConfiguration().getTemplateConnectionFactory();
    }

    @ManagedAttribute
    public long getTimeToLive() {
        return getConfiguration().getTimeToLive();
    }

    public PlatformTransactionManager getTransactionManager() {
        return getConfiguration().getTransactionManager();
    }

    @ManagedAttribute
    public String getTransactionName() {
        return getConfiguration().getTransactionName();
    }

    @ManagedAttribute
    public int getTransactionTimeout() {
        return getConfiguration().getTransactionTimeout();
    }

    @ManagedAttribute
    public boolean isAcceptMessagesWhileStopping() {
        return getConfiguration().isAcceptMessagesWhileStopping();
    }

    @ManagedAttribute
    public boolean isAllowReplyManagerQuickStop() {
        return getConfiguration().isAllowReplyManagerQuickStop();
    }
    
    @ManagedAttribute
    public boolean isAlwaysCopyMessage() {
        return getConfiguration().isAlwaysCopyMessage();
    }

    @ManagedAttribute
    public boolean isAutoStartup() {
        return getConfiguration().isAutoStartup();
    }

    @ManagedAttribute
    public boolean isDeliveryPersistent() {
        return getConfiguration().isDeliveryPersistent();
    }

    @ManagedAttribute
    public Integer getDeliveryMode() {
        return getConfiguration().getDeliveryMode();
    }

    @ManagedAttribute
    public boolean isDisableReplyTo() {
        return getConfiguration().isDisableReplyTo();
    }

    @ManagedAttribute
    public String getEagerPoisonBody() {
        return getConfiguration().getEagerPoisonBody();
    }

    @ManagedAttribute
    public boolean isEagerLoadingOfProperties() {
        return getConfiguration().isEagerLoadingOfProperties();
    }

    @ManagedAttribute
    public boolean isExplicitQosEnabled() {
        return getConfiguration().isExplicitQosEnabled();
    }

    @ManagedAttribute
    public boolean isExposeListenerSession() {
        return getConfiguration().isExposeListenerSession();
    }

    @ManagedAttribute
    public boolean isMessageIdEnabled() {
        return getConfiguration().isMessageIdEnabled();
    }

    @ManagedAttribute
    public boolean isMessageTimestampEnabled() {
        return getConfiguration().isMessageTimestampEnabled();
    }

    @ManagedAttribute
    public boolean isPreserveMessageQos() {
        return getConfiguration().isPreserveMessageQos();
    }

    @ManagedAttribute
    public boolean isPubSubNoLocal() {
        return getConfiguration().isPubSubNoLocal();
    }

    @ManagedAttribute
    public boolean isReplyToDeliveryPersistent() {
        return getConfiguration().isReplyToDeliveryPersistent();
    }

    @ManagedAttribute
    public boolean isTransacted() {
        return getConfiguration().isTransacted();
    }

    @ManagedAttribute
    public boolean isLazyCreateTransactionManager() {
        return getConfiguration().isLazyCreateTransactionManager();
    }

    @ManagedAttribute
    public boolean isUseMessageIDAsCorrelationID() {
        return getConfiguration().isUseMessageIDAsCorrelationID();
    }

    @ManagedAttribute
    public void setAcceptMessagesWhileStopping(boolean acceptMessagesWhileStopping) {
        getConfiguration().setAcceptMessagesWhileStopping(acceptMessagesWhileStopping);
    }

    @ManagedAttribute
    public void setAllowReplyManagerQuickStop(boolean allowReplyManagerQuickStop) {
        getConfiguration().setAllowReplyManagerQuickStop(allowReplyManagerQuickStop);
    }
    
    @ManagedAttribute
    public void setAcknowledgementMode(int consumerAcknowledgementMode) {
        getConfiguration().setAcknowledgementMode(consumerAcknowledgementMode);
    }

    @ManagedAttribute
    public void setAcknowledgementModeName(String consumerAcknowledgementMode) {
        getConfiguration().setAcknowledgementModeName(consumerAcknowledgementMode);
    }

    @ManagedAttribute
    public void setAlwaysCopyMessage(boolean alwaysCopyMessage) {
        getConfiguration().setAlwaysCopyMessage(alwaysCopyMessage);
    }

    @ManagedAttribute
    public void setAutoStartup(boolean autoStartup) {
        getConfiguration().setAutoStartup(autoStartup);
    }

    @ManagedAttribute
    public void setCacheLevel(int cacheLevel) {
        getConfiguration().setCacheLevel(cacheLevel);
    }

    @ManagedAttribute
    public void setCacheLevelName(String cacheName) {
        getConfiguration().setCacheLevelName(cacheName);
    }

    @ManagedAttribute
    public void setReplyToCacheLevelName(String cacheName) {
        getConfiguration().setReplyToCacheLevelName(cacheName);
    }

    @ManagedAttribute
    public void setClientId(String consumerClientId) {
        getConfiguration().setClientId(consumerClientId);
    }

    @ManagedAttribute
    public void setConcurrentConsumers(int concurrentConsumers) {
        getConfiguration().setConcurrentConsumers(concurrentConsumers);
    }

    @ManagedAttribute
    public void setReplyToConcurrentConsumers(int concurrentConsumers) {
        getConfiguration().setReplyToConcurrentConsumers(concurrentConsumers);
    }

    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        getConfiguration().setConnectionFactory(connectionFactory);
    }

    @ManagedAttribute
    public void setDeliveryPersistent(boolean deliveryPersistent) {
        getConfiguration().setDeliveryPersistent(deliveryPersistent);
    }

    @ManagedAttribute
    public void setDeliveryMode(Integer deliveryMode) {
        getConfiguration().setDeliveryMode(deliveryMode);
    }

    public void setDestinationResolver(DestinationResolver destinationResolver) {
        getConfiguration().setDestinationResolver(destinationResolver);
    }

    @ManagedAttribute
    public void setDisableReplyTo(boolean disableReplyTo) {
        getConfiguration().setDisableReplyTo(disableReplyTo);
    }

    @ManagedAttribute
    public void setDurableSubscriptionName(String durableSubscriptionName) {
        getConfiguration().setDurableSubscriptionName(durableSubscriptionName);
    }

    @ManagedAttribute
    public void setEagerPoisonBody(String eagerPoisonBody) {
        getConfiguration().setEagerPoisonBody(eagerPoisonBody);
    }

    @ManagedAttribute
    public void setEagerLoadingOfProperties(boolean eagerLoadingOfProperties) {
        getConfiguration().setEagerLoadingOfProperties(eagerLoadingOfProperties);
    }

    public void setExceptionListener(ExceptionListener exceptionListener) {
        getConfiguration().setExceptionListener(exceptionListener);
    }

    public void setErrorHandler(ErrorHandler errorHandler) {
        getConfiguration().setErrorHandler(errorHandler);
    }

    @ManagedAttribute
    public void setExplicitQosEnabled(boolean explicitQosEnabled) {
        getConfiguration().setExplicitQosEnabled(explicitQosEnabled);
    }

    @ManagedAttribute
    public void setExposeListenerSession(boolean exposeListenerSession) {
        getConfiguration().setExposeListenerSession(exposeListenerSession);
    }

    @ManagedAttribute
    public void setIdleTaskExecutionLimit(int idleTaskExecutionLimit) {
        getConfiguration().setIdleTaskExecutionLimit(idleTaskExecutionLimit);
    }

    @ManagedAttribute
    public void setIdleConsumerLimit(int idleConsumerLimit) {
        getConfiguration().setIdleConsumerLimit(idleConsumerLimit);
    }

    public void setJmsOperations(JmsOperations jmsOperations) {
        getConfiguration().setJmsOperations(jmsOperations);
    }

    public void setListenerConnectionFactory(ConnectionFactory listenerConnectionFactory) {
        getConfiguration().setListenerConnectionFactory(listenerConnectionFactory);
    }

    @ManagedAttribute
    public void setMaxConcurrentConsumers(int maxConcurrentConsumers) {
        getConfiguration().setMaxConcurrentConsumers(maxConcurrentConsumers);
    }

    @ManagedAttribute
    public void setReplyToMaxConcurrentConsumers(int maxConcurrentConsumers) {
        getConfiguration().setReplyToMaxConcurrentConsumers(maxConcurrentConsumers);
    }

    @ManagedAttribute
    public void setMaxMessagesPerTask(int maxMessagesPerTask) {
        getConfiguration().setMaxMessagesPerTask(maxMessagesPerTask);
    }

    public void setMessageConverter(MessageConverter messageConverter) {
        getConfiguration().setMessageConverter(messageConverter);
    }

    @ManagedAttribute
    public void setMessageIdEnabled(boolean messageIdEnabled) {
        getConfiguration().setMessageIdEnabled(messageIdEnabled);
    }

    @ManagedAttribute
    public void setMessageTimestampEnabled(boolean messageTimestampEnabled) {
        getConfiguration().setMessageTimestampEnabled(messageTimestampEnabled);
    }

    @ManagedAttribute
    public void setPreserveMessageQos(boolean preserveMessageQos) {
        getConfiguration().setPreserveMessageQos(preserveMessageQos);
    }

    @ManagedAttribute
    public void setPriority(int priority) {
        getConfiguration().setPriority(priority);
    }

    @ManagedAttribute
    public void setPubSubNoLocal(boolean pubSubNoLocal) {
        getConfiguration().setPubSubNoLocal(pubSubNoLocal);
    }

    @ManagedAttribute
    public void setReceiveTimeout(long receiveTimeout) {
        getConfiguration().setReceiveTimeout(receiveTimeout);
    }

    @ManagedAttribute
    public void setRecoveryInterval(long recoveryInterval) {
        getConfiguration().setRecoveryInterval(recoveryInterval);
    }

    @ManagedAttribute
    public void setReplyTo(String replyToDestination) {
        getConfiguration().setReplyTo(replyToDestination);
    }

    @ManagedAttribute
    public void setReplyToOverride(String replyToDestination) {
        getConfiguration().setReplyToOverride(replyToDestination);
    }

    @ManagedAttribute
    public void setReplyToSameDestinationAllowed(boolean replyToSameDestinationAllowed) {
        getConfiguration().setReplyToSameDestinationAllowed(replyToSameDestinationAllowed);
    }

    @ManagedAttribute
    public void setReplyToDeliveryPersistent(boolean replyToDeliveryPersistent) {
        getConfiguration().setReplyToDeliveryPersistent(replyToDeliveryPersistent);
    }

    @ManagedAttribute
    public void setReplyToDestinationSelectorName(String replyToDestinationSelectorName) {
        getConfiguration().setReplyToDestinationSelectorName(replyToDestinationSelectorName);
    }

    @ManagedAttribute
    public void setRequestTimeout(long requestTimeout) {
        getConfiguration().setRequestTimeout(requestTimeout);
    }

    public void setTaskExecutor(TaskExecutor taskExecutor) {
        getConfiguration().setTaskExecutor(taskExecutor);
    }

    public void setTemplateConnectionFactory(ConnectionFactory templateConnectionFactory) {
        getConfiguration().setTemplateConnectionFactory(templateConnectionFactory);
    }

    @ManagedAttribute
    public void setTimeToLive(long timeToLive) {
        getConfiguration().setTimeToLive(timeToLive);
    }

    @ManagedAttribute
    public void setTransacted(boolean consumerTransacted) {
        getConfiguration().setTransacted(consumerTransacted);
    }

    @ManagedAttribute
    public void setLazyCreateTransactionManager(boolean lazyCreating) {
        getConfiguration().setLazyCreateTransactionManager(lazyCreating);
    }

    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        getConfiguration().setTransactionManager(transactionManager);
    }

    @ManagedAttribute
    public void setTransactionName(String transactionName) {
        getConfiguration().setTransactionName(transactionName);
    }

    @ManagedAttribute
    public void setTransactionTimeout(int transactionTimeout) {
        getConfiguration().setTransactionTimeout(transactionTimeout);
    }

    @ManagedAttribute
    public void setUseMessageIDAsCorrelationID(boolean useMessageIDAsCorrelationID) {
        getConfiguration().setUseMessageIDAsCorrelationID(useMessageIDAsCorrelationID);
    }

    public JmsMessageType getJmsMessageType() {
        return getConfiguration().getJmsMessageType();
    }

    public void setJmsMessageType(JmsMessageType jmsMessageType) {
        getConfiguration().setJmsMessageType(jmsMessageType);
    }

    public JmsKeyFormatStrategy getJmsKeyFormatStrategy() {
        return getConfiguration().getJmsKeyFormatStrategy();
    }

    public void setJmsKeyFormatStrategy(JmsKeyFormatStrategy jmsHeaderStrategy) {
        getConfiguration().setJmsKeyFormatStrategy(jmsHeaderStrategy);
    }

    public MessageCreatedStrategy getMessageCreatedStrategy() {
        return getConfiguration().getMessageCreatedStrategy();
    }

    public void setMessageCreatedStrategy(MessageCreatedStrategy messageCreatedStrategy) {
        getConfiguration().setMessageCreatedStrategy(messageCreatedStrategy);
    }

    @ManagedAttribute
    public boolean isTransferExchange() {
        return getConfiguration().isTransferExchange();
    }

    @ManagedAttribute
    public void setTransferExchange(boolean transferExchange) {
        getConfiguration().setTransferExchange(transferExchange);
    }

    @ManagedAttribute
    public boolean isAllowSerializedHeaders() {
        return getConfiguration().isAllowSerializedHeaders();
    }

    @ManagedAttribute
    public void setAllowSerializedHeaders(boolean allowSerializedHeaders) {
        getConfiguration().setAllowSerializedHeaders(allowSerializedHeaders);
    }

    @ManagedAttribute
    public boolean isTransferException() {
        return getConfiguration().isTransferException();
    }

    @ManagedAttribute
    public void setTransferException(boolean transferException) {
        getConfiguration().setTransferException(transferException);
    }

    @ManagedAttribute
    public boolean isTestConnectionOnStartup() {
        return configuration.isTestConnectionOnStartup();
    }

    @ManagedAttribute
    public void setTestConnectionOnStartup(boolean testConnectionOnStartup) {
        configuration.setTestConnectionOnStartup(testConnectionOnStartup);
    }

    @ManagedAttribute
    public boolean isForceSendOriginalMessage() {
        return configuration.isForceSendOriginalMessage();
    }

    @ManagedAttribute
    public void setForceSendOriginalMessage(boolean forceSendOriginalMessage) {
        configuration.setForceSendOriginalMessage(forceSendOriginalMessage);
    }

    @ManagedAttribute
    public boolean isDisableTimeToLive() {
        return configuration.isDisableTimeToLive();
    }

    @ManagedAttribute
    public void setDisableTimeToLive(boolean disableTimeToLive) {
        configuration.setDisableTimeToLive(disableTimeToLive);
    }

    @ManagedAttribute
    public void setAsyncConsumer(boolean asyncConsumer) {
        configuration.setAsyncConsumer(asyncConsumer);
    }

    @ManagedAttribute
    public boolean isAsyncConsumer() {
        return configuration.isAsyncConsumer();
    }

    @ManagedAttribute
    public void setAsyncStartListener(boolean asyncStartListener) {
        configuration.setAsyncStartListener(asyncStartListener);
    }

    @ManagedAttribute
    public boolean isAsyncStartListener() {
        return configuration.isAsyncStartListener();
    }

    @ManagedAttribute
    public void setAsyncStopListener(boolean asyncStopListener) {
        configuration.setAsyncStopListener(asyncStopListener);
    }

    @ManagedAttribute
    public boolean isAsyncStopListener() {
        return configuration.isAsyncStopListener();
    }

    @ManagedAttribute
    public boolean isAllowNullBody() {
        return configuration.isAllowNullBody();
    }

    @ManagedAttribute
    public void setAllowNullBody(boolean allowNullBody) {
        configuration.setAllowNullBody(allowNullBody);
    }

    @ManagedAttribute
    public boolean isIncludeSentJMSMessageID() {
        return configuration.isIncludeSentJMSMessageID();
    }

    @ManagedAttribute
    public void setIncludeSentJMSMessageID(boolean includeSentJMSMessageID) {
        configuration.setIncludeSentJMSMessageID(includeSentJMSMessageID);
    }

    @ManagedAttribute
    public boolean isIncludeAllJMSXProperties() {
        return configuration.isIncludeAllJMSXProperties();
    }

    @ManagedAttribute
    public void setIncludeAllJMSXProperties(boolean includeAllJMSXProperties) {
        configuration.setIncludeAllJMSXProperties(includeAllJMSXProperties);
    }

    @ManagedAttribute
    public DefaultTaskExecutorType getDefaultTaskExecutorType() {
        return configuration.getDefaultTaskExecutorType();
    }

    public void setDefaultTaskExecutorType(DefaultTaskExecutorType type) {
        configuration.setDefaultTaskExecutorType(type);
    }

    @ManagedAttribute
    public String getAllowAdditionalHeaders() {
        return configuration.getAllowAdditionalHeaders();
    }

    @ManagedAttribute
    public void setAllowAdditionalHeaders(String allowAdditionalHeaders) {
        configuration.setAllowAdditionalHeaders(allowAdditionalHeaders);
    }

    public MessageListenerContainerFactory getMessageListenerContainerFactory() {
        return configuration.getMessageListenerContainerFactory();
    }

    public void setMessageListenerContainerFactory(MessageListenerContainerFactory messageListenerContainerFactory) {
        configuration.setMessageListenerContainerFactory(messageListenerContainerFactory);
        configuration.setConsumerType(ConsumerType.Custom);
    }

    @ManagedAttribute
    public boolean isSubscriptionDurable() {
        return getConfiguration().isSubscriptionDurable();
    }

    @ManagedAttribute
    public void setSubscriptionDurable(boolean subscriptionDurable) {
        getConfiguration().setSubscriptionDurable(subscriptionDurable);
    }

    @ManagedAttribute
    public boolean isSubscriptionShared() {
        return getConfiguration().isSubscriptionShared();
    }

    @ManagedAttribute
    public void setSubscriptionShared(boolean subscriptionShared) {
        getConfiguration().setSubscriptionShared(subscriptionShared);
    }

    @ManagedAttribute
    public String getSubscriptionName() {
        return getConfiguration().getSubscriptionName();
    }

    @ManagedAttribute
    public void setSubscriptionName(String subscriptionName) {
        getConfiguration().setSubscriptionName(subscriptionName);
    }


    @ManagedAttribute
    public String getReplyToType() {
        if (configuration.getReplyToType() != null) {
            return configuration.getReplyToType().name();
        } else {
            return null;
        }
    }

    @ManagedAttribute
    public void setReplyToType(String replyToType) {
        ReplyToType type = ReplyToType.valueOf(replyToType);
        configuration.setReplyToType(type);
    }

    @ManagedAttribute(description = "Number of running message listeners")
    public int getRunningMessageListeners() {
        return runningMessageListeners.get();
    }

    @ManagedAttribute
    public String getSelector() {
        return configuration.getSelector();
    }

    public void setSelector(String selector) {
        configuration.setSelector(selector);
    }

    @ManagedAttribute
    public int getWaitForProvisionCorrelationToBeUpdatedCounter() {
        return configuration.getWaitForProvisionCorrelationToBeUpdatedCounter();
    }

    @ManagedAttribute
    public void setWaitForProvisionCorrelationToBeUpdatedCounter(int counter) {
        configuration.setWaitForProvisionCorrelationToBeUpdatedCounter(counter);
    }

    @ManagedAttribute
    public long getWaitForProvisionCorrelationToBeUpdatedThreadSleepingTime() {
        return configuration.getWaitForProvisionCorrelationToBeUpdatedThreadSleepingTime();
    }

    @ManagedAttribute
    public void setWaitForProvisionCorrelationToBeUpdatedThreadSleepingTime(long sleepingTime) {
        configuration.setWaitForProvisionCorrelationToBeUpdatedThreadSleepingTime(sleepingTime);
    }

    @ManagedAttribute
    public boolean isFormatDateHeadersToIso8601() {
        return configuration.isFormatDateHeadersToIso8601();
    }

    @ManagedAttribute
    public void setFormatDateHeadersToIso8601(boolean formatDateHeadersToIso8601) {
        configuration.setFormatDateHeadersToIso8601(formatDateHeadersToIso8601);
    }

    @ManagedAttribute
    public boolean isArtemisStreamingEnabled() {
        return configuration.isArtemisStreamingEnabled();
    }

    @ManagedAttribute
    public void setArtemisStreamingEnabled(boolean artemisStreamingEnabled) {
        configuration.setArtemisStreamingEnabled(artemisStreamingEnabled);
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    @Override
    protected String createEndpointUri() {
        String scheme = "jms";
        if (destination != null) {
            return scheme + ":" + destination;
        } else if (destinationName != null) {
            return scheme + ":" + destinationName;
        }
        DestinationResolver resolver = getDestinationResolver();
        if (resolver != null) {
            return scheme + ":" + resolver;
        }
        return super.createEndpointUri();
    }

}
