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

import java.util.Map;
import java.util.concurrent.ExecutorService;
import javax.jms.ConnectionFactory;
import javax.jms.ExceptionListener;
import javax.jms.Session;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.LoggingLevel;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jms.connection.JmsTransactionManager;
import org.springframework.jms.connection.UserCredentialsConnectionFactoryAdapter;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.destination.DestinationResolver;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.ErrorHandler;

import static org.apache.camel.util.ObjectHelper.removeStartingCharacters;

/**
 * A <a href="http://activemq.apache.org/jms.html">JMS Component</a>
 *
 * @version 
 */
public class JmsComponent extends UriEndpointComponent implements ApplicationContextAware, HeaderFilterStrategyAware {

    private static final String KEY_FORMAT_STRATEGY_PARAM = "jmsKeyFormatStrategy";
    private JmsConfiguration configuration;
    private ApplicationContext applicationContext;
    private QueueBrowseStrategy queueBrowseStrategy;
    private HeaderFilterStrategy headerFilterStrategy;
    private ExecutorService asyncStartStopExecutorService;
    private MessageCreatedStrategy messageCreatedStrategy;

    public JmsComponent() {
        super(JmsEndpoint.class);
    }

    public JmsComponent(Class<? extends Endpoint> endpointClass) {
        super(endpointClass);
    }

    public JmsComponent(CamelContext context) {
        super(context, JmsEndpoint.class);
    }

    public JmsComponent(CamelContext context, Class<? extends Endpoint> endpointClass) {
        super(context, endpointClass);
    }

    public JmsComponent(JmsConfiguration configuration) {
        this();
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
        JmsConfiguration template = new JmsConfiguration(connectionFactory);
        template.setAcknowledgementMode(Session.CLIENT_ACKNOWLEDGE);
        return jmsComponent(template);
    }

    /**
     * Static builder method
     */
    public static JmsComponent jmsComponentAutoAcknowledge(ConnectionFactory connectionFactory) {
        JmsConfiguration template = new JmsConfiguration(connectionFactory);
        template.setAcknowledgementMode(Session.AUTO_ACKNOWLEDGE);
        return jmsComponent(template);
    }

    public static JmsComponent jmsComponentTransacted(ConnectionFactory connectionFactory) {
        JmsTransactionManager transactionManager = new JmsTransactionManager();
        transactionManager.setConnectionFactory(connectionFactory);
        return jmsComponentTransacted(connectionFactory, transactionManager);
    }

    @SuppressWarnings("deprecation")
    public static JmsComponent jmsComponentTransacted(ConnectionFactory connectionFactory,
                                                      PlatformTransactionManager transactionManager) {
        JmsConfiguration template = new JmsConfiguration(connectionFactory);
        template.setTransactionManager(transactionManager);
        template.setTransacted(true);
        template.setTransactedInOut(true);
        return jmsComponent(template);
    }

    // Properties
    // -------------------------------------------------------------------------

    public JmsConfiguration getConfiguration() {
        if (configuration == null) {
            configuration = createConfiguration();

            // If we are being configured with spring...
            if (applicationContext != null) {
                Map<String, ConnectionFactory> beansOfTypeConnectionFactory = applicationContext.getBeansOfType(ConnectionFactory.class);
                if (!beansOfTypeConnectionFactory.isEmpty()) {
                    ConnectionFactory cf = beansOfTypeConnectionFactory.values().iterator().next();
                    configuration.setConnectionFactory(cf);
                }
                Map<String, DestinationResolver> beansOfTypeDestinationResolver = applicationContext.getBeansOfType(DestinationResolver.class);
                if (!beansOfTypeDestinationResolver.isEmpty()) {
                    DestinationResolver destinationResolver = beansOfTypeDestinationResolver.values().iterator().next();
                    configuration.setDestinationResolver(destinationResolver);
                }
            }
        }
        return configuration;
    }

    /**
     * To use a shared JMS configuration
     */
    public void setConfiguration(JmsConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Specifies whether the consumer accept messages while it is stopping.
     * You may consider enabling this option, if you start and stop JMS routes at runtime, while there are still messages
     * enqued on the queue. If this option is false, and you stop the JMS route, then messages may be rejected,
     * and the JMS broker would have to attempt redeliveries, which yet again may be rejected, and eventually the message
     * may be moved at a dead letter queue on the JMS broker. To avoid this its recommended to enable this option.
     */
    public void setAcceptMessagesWhileStopping(boolean acceptMessagesWhileStopping) {
        getConfiguration().setAcceptMessagesWhileStopping(acceptMessagesWhileStopping);
    }

    /**
     * The JMS acknowledgement mode defined as an Integer.
     * Allows you to set vendor-specific extensions to the acknowledgment mode.
     * For the regular modes, it is preferable to use the acknowledgementModeName instead.
     */
    public void setAcknowledgementMode(int consumerAcknowledgementMode) {
        getConfiguration().setAcknowledgementMode(consumerAcknowledgementMode);
    }

    /**
     * Enables eager loading of JMS properties as soon as a message is loaded
     * which generally is inefficient as the JMS properties may not be required
     * but sometimes can catch early any issues with the underlying JMS provider
     * and the use of JMS properties
     */
    public void setEagerLoadingOfProperties(boolean eagerLoadingOfProperties) {
        getConfiguration().setEagerLoadingOfProperties(eagerLoadingOfProperties);
    }

    /**
     * The JMS acknowledgement name, which is one of: SESSION_TRANSACTED, CLIENT_ACKNOWLEDGE, AUTO_ACKNOWLEDGE, DUPS_OK_ACKNOWLEDGE
     */
    public void setAcknowledgementModeName(String consumerAcknowledgementMode) {
        getConfiguration().setAcknowledgementModeName(consumerAcknowledgementMode);
    }

    /**
     * Specifies whether the consumer container should auto-startup.
     */
    public void setAutoStartup(boolean autoStartup) {
        getConfiguration().setAutoStartup(autoStartup);
    }

    /**
     * Sets the cache level by ID for the underlying JMS resources. See cacheLevelName option for more details.
     */
    public void setCacheLevel(int cacheLevel) {
        getConfiguration().setCacheLevel(cacheLevel);
    }

    /**
     * Sets the cache level by name for the underlying JMS resources.
     * Possible values are: CACHE_AUTO, CACHE_CONNECTION, CACHE_CONSUMER, CACHE_NONE, and CACHE_SESSION.
     * The default setting is CACHE_AUTO. See the Spring documentation and Transactions Cache Levels for more information.
     */
    public void setCacheLevelName(String cacheName) {
        getConfiguration().setCacheLevelName(cacheName);
    }

    /**
     * Sets the cache level by name for the reply consumer when doing request/reply over JMS.
     * This option only applies when using fixed reply queues (not temporary).
     * Camel will by default use: CACHE_CONSUMER for exclusive or shared w/ replyToSelectorName.
     * And CACHE_SESSION for shared without replyToSelectorName. Some JMS brokers such as IBM WebSphere
     * may require to set the replyToCacheLevelName=CACHE_NONE to work.
     * Note: If using temporary queues then CACHE_NONE is not allowed,
     * and you must use a higher value such as CACHE_CONSUMER or CACHE_SESSION.
     */
    public void setReplyToCacheLevelName(String cacheName) {
        getConfiguration().setReplyToCacheLevelName(cacheName);
    }

    /**
     * Sets the JMS client ID to use. Note that this value, if specified, must be unique and can only be used by a single JMS connection instance.
     * It is typically only required for durable topic subscriptions.
     * <p/>
     * If using Apache ActiveMQ you may prefer to use Virtual Topics instead.
     */
    public void setClientId(String consumerClientId) {
        getConfiguration().setClientId(consumerClientId);
    }

    /**
     * Specifies the default number of concurrent consumers when consuming from JMS (not for request/reply over JMS).
     * See also the maxMessagesPerTask option to control dynamic scaling up/down of threads.
     * <p/>
     * When doing request/reply over JMS then the option replyToConcurrentConsumers is used to control number
     * of concurrent consumers on the reply message listener.
     */
    public void setConcurrentConsumers(int concurrentConsumers) {
        getConfiguration().setConcurrentConsumers(concurrentConsumers);
    }

    /**
     * Specifies the default number of concurrent consumers when doing request/reply over JMS.
     * See also the maxMessagesPerTask option to control dynamic scaling up/down of threads.
     */
    public void setReplyToConcurrentConsumers(int concurrentConsumers) {
        getConfiguration().setReplyToConcurrentConsumers(concurrentConsumers);
    }

    /**
     * Sets the default connection factory to be use
     */
    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        getConfiguration().setConnectionFactory(connectionFactory);
    }

    /**
     * Specifies whether persistent delivery is used by default.
     */
    public void setDeliveryPersistent(boolean deliveryPersistent) {
        getConfiguration().setDeliveryPersistent(deliveryPersistent);
    }

    /**
     * Specifies the delivery mode to be used. Possible values are
     * Possibles values are those defined by javax.jms.DeliveryMode.
     * NON_PERSISTENT = 1 and PERSISTENT = 2.
     */
    public void setDeliveryMode(Integer deliveryMode) {
        getConfiguration().setDeliveryMode(deliveryMode);
    }

    /**
     * The durable subscriber name for specifying durable topic subscriptions. The clientId option must be configured as well.
     */
    public void setDurableSubscriptionName(String durableSubscriptionName) {
        getConfiguration().setDurableSubscriptionName(durableSubscriptionName);
    }

    /**
     * Specifies the JMS Exception Listener that is to be notified of any underlying JMS exceptions.
     */
    public void setExceptionListener(ExceptionListener exceptionListener) {
        getConfiguration().setExceptionListener(exceptionListener);
    }

    /**
     * Specifies a org.springframework.util.ErrorHandler to be invoked in case of any uncaught exceptions thrown while processing a Message.
     * By default these exceptions will be logged at the WARN level, if no errorHandler has been configured.
     * You can configure logging level and whether stack traces should be logged using errorHandlerLoggingLevel and errorHandlerLogStackTrace options.
     * This makes it much easier to configure, than having to code a custom errorHandler.
     */
    public void setErrorHandler(ErrorHandler errorHandler) {
        getConfiguration().setErrorHandler(errorHandler);
    }

    /**
     * Allows to configure the default errorHandler logging level for logging uncaught exceptions.
     */
    public void setErrorHandlerLoggingLevel(LoggingLevel errorHandlerLoggingLevel) {
        getConfiguration().setErrorHandlerLoggingLevel(errorHandlerLoggingLevel);
    }

    /**
     * Allows to control whether stacktraces should be logged or not, by the default errorHandler.
     */
    public void setErrorHandlerLogStackTrace(boolean errorHandlerLogStackTrace) {
        getConfiguration().setErrorHandlerLogStackTrace(errorHandlerLogStackTrace);
    }

    /**
     * Set if the deliveryMode, priority or timeToLive qualities of service should be used when sending messages.
     * This option is based on Spring's JmsTemplate. The deliveryMode, priority and timeToLive options are applied to the current endpoint.
     * This contrasts with the preserveMessageQos option, which operates at message granularity,
     * reading QoS properties exclusively from the Camel In message headers.
     */
    public void setExplicitQosEnabled(boolean explicitQosEnabled) {
        getConfiguration().setExplicitQosEnabled(explicitQosEnabled);
    }

    /**
     * Specifies whether the listener session should be exposed when consuming messages.
     */
    public void setExposeListenerSession(boolean exposeListenerSession) {
        getConfiguration().setExposeListenerSession(exposeListenerSession);
    }

    /**
     * Specifies the limit for idle executions of a receive task, not having received any message within its execution.
     * If this limit is reached, the task will shut down and leave receiving to other executing tasks
     * (in the case of dynamic scheduling; see the maxConcurrentConsumers setting).
     * There is additional doc available from Spring.
     */
    public void setIdleTaskExecutionLimit(int idleTaskExecutionLimit) {
        getConfiguration().setIdleTaskExecutionLimit(idleTaskExecutionLimit);
    }

    /**
     * Specify the limit for the number of consumers that are allowed to be idle at any given time.
     */
    public void setIdleConsumerLimit(int idleConsumerLimit) {
        getConfiguration().setIdleConsumerLimit(idleConsumerLimit);
    }

    /**
     * Specifies the maximum number of concurrent consumers when consuming from JMS (not for request/reply over JMS).
     * See also the maxMessagesPerTask option to control dynamic scaling up/down of threads.
     * <p/>
     * When doing request/reply over JMS then the option replyToMaxConcurrentConsumers is used to control number
     * of concurrent consumers on the reply message listener.
     */
    public void setMaxConcurrentConsumers(int maxConcurrentConsumers) {
        getConfiguration().setMaxConcurrentConsumers(maxConcurrentConsumers);
    }

    /**
     * Specifies the maximum number of concurrent consumers when using request/reply over JMS.
     * See also the maxMessagesPerTask option to control dynamic scaling up/down of threads.
     */
    public void setReplyToMaxConcurrentConsumers(int maxConcurrentConsumers) {
        getConfiguration().setReplyToMaxConcurrentConsumers(maxConcurrentConsumers);
    }

    /**
     * The number of messages per task. -1 is unlimited.
     * If you use a range for concurrent consumers (eg min < max), then this option can be used to set
     * a value to eg 100 to control how fast the consumers will shrink when less work is required.
     */
    public void setMaxMessagesPerTask(int maxMessagesPerTask) {
        getConfiguration().setMaxMessagesPerTask(maxMessagesPerTask);
    }

    /**
     * To use a custom Spring org.springframework.jms.support.converter.MessageConverter so you can be in control
     * how to map to/from a javax.jms.Message.
     */
    public void setMessageConverter(MessageConverter messageConverter) {
        getConfiguration().setMessageConverter(messageConverter);
    }

    /**
     * Specifies whether Camel should auto map the received JMS message to a suited payload type, such as javax.jms.TextMessage to a String etc.
     * See section about how mapping works below for more details.
     */
    public void setMapJmsMessage(boolean mapJmsMessage) {
        getConfiguration().setMapJmsMessage(mapJmsMessage);
    }

    /**
     * When sending, specifies whether message IDs should be added.
     */
    public void setMessageIdEnabled(boolean messageIdEnabled) {
        getConfiguration().setMessageIdEnabled(messageIdEnabled);
    }

    /**
     * Specifies whether timestamps should be enabled by default on sending messages.
     */
    public void setMessageTimestampEnabled(boolean messageTimestampEnabled) {
        getConfiguration().setMessageTimestampEnabled(messageTimestampEnabled);
    }

    /**
     * If true, Camel will always make a JMS message copy of the message when it is passed to the producer for sending.
     * Copying the message is needed in some situations, such as when a replyToDestinationSelectorName is set
     * (incidentally, Camel will set the alwaysCopyMessage option to true, if a replyToDestinationSelectorName is set)
     */
    public void setAlwaysCopyMessage(boolean alwaysCopyMessage) {
        getConfiguration().setAlwaysCopyMessage(alwaysCopyMessage);
    }

    /**
     * Specifies whether JMSMessageID should always be used as JMSCorrelationID for InOut messages.
     */
    public void setUseMessageIDAsCorrelationID(boolean useMessageIDAsCorrelationID) {
        getConfiguration().setUseMessageIDAsCorrelationID(useMessageIDAsCorrelationID);
    }

    /**
     * Values greater than 1 specify the message priority when sending (where 0 is the lowest priority and 9 is the highest).
     * The explicitQosEnabled option must also be enabled in order for this option to have any effect.
     */
    public void setPriority(int priority) {
        getConfiguration().setPriority(priority);
    }

    /**
     * Specifies whether to inhibit the delivery of messages published by its own connection.
     */
    public void setPubSubNoLocal(boolean pubSubNoLocal) {
        getConfiguration().setPubSubNoLocal(pubSubNoLocal);
    }

    /**
     * The timeout for receiving messages (in milliseconds).
     */
    public void setReceiveTimeout(long receiveTimeout) {
        getConfiguration().setReceiveTimeout(receiveTimeout);
    }

    /**
     * Specifies the interval between recovery attempts, i.e. when a connection is being refreshed, in milliseconds.
     * The default is 5000 ms, that is, 5 seconds.
     */
    public void setRecoveryInterval(long recoveryInterval) {
        getConfiguration().setRecoveryInterval(recoveryInterval);
    }

    /**
     * Deprecated: Enabled by default, if you specify a durableSubscriptionName and a clientId.
     */
    @Deprecated
    public void setSubscriptionDurable(boolean subscriptionDurable) {
        getConfiguration().setSubscriptionDurable(subscriptionDurable);
    }

    /**
     * Allows you to specify a custom task executor for consuming messages.
     */
    public void setTaskExecutor(TaskExecutor taskExecutor) {
        getConfiguration().setTaskExecutor(taskExecutor);
    }

    /**
     * When sending messages, specifies the time-to-live of the message (in milliseconds).
     */
    public void setTimeToLive(long timeToLive) {
        getConfiguration().setTimeToLive(timeToLive);
    }

    /**
     * Specifies whether to use transacted mode
     */
    public void setTransacted(boolean consumerTransacted) {
        getConfiguration().setTransacted(consumerTransacted);
    }

    /**
     * If true, Camel will create a JmsTransactionManager, if there is no transactionManager injected when option transacted=true.
     */
    public void setLazyCreateTransactionManager(boolean lazyCreating) {
        getConfiguration().setLazyCreateTransactionManager(lazyCreating);
    }

    /**
     * The Spring transaction manager to use.
     */
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        getConfiguration().setTransactionManager(transactionManager);
    }

    /**
     * The name of the transaction to use.
     */
    public void setTransactionName(String transactionName) {
        getConfiguration().setTransactionName(transactionName);
    }

    /**
     * The timeout value of the transaction (in seconds), if using transacted mode.
     */
    public void setTransactionTimeout(int transactionTimeout) {
        getConfiguration().setTransactionTimeout(transactionTimeout);
    }

    /**
     * Specifies whether to test the connection on startup.
     * This ensures that when Camel starts that all the JMS consumers have a valid connection to the JMS broker.
     * If a connection cannot be granted then Camel throws an exception on startup.
     * This ensures that Camel is not started with failed connections.
     * The JMS producers is tested as well.
     */
    public void setTestConnectionOnStartup(boolean testConnectionOnStartup) {
        getConfiguration().setTestConnectionOnStartup(testConnectionOnStartup);
    }

    /**
     * Whether to startup the JmsConsumer message listener asynchronously, when starting a route.
     * For example if a JmsConsumer cannot get a connection to a remote JMS broker, then it may block while retrying
     * and/or failover. This will cause Camel to block while starting routes. By setting this option to true,
     * you will let routes startup, while the JmsConsumer connects to the JMS broker using a dedicated thread
     * in asynchronous mode. If this option is used, then beware that if the connection could not be established,
     * then an exception is logged at WARN level, and the consumer will not be able to receive messages;
     * You can then restart the route to retry.
     */
    public void setAsyncStartListener(boolean asyncStartListener) {
        getConfiguration().setAsyncStartListener(asyncStartListener);
    }

    /**
     * Whether to stop the JmsConsumer message listener asynchronously, when stopping a route.
     */
    public void setAsyncStopListener(boolean asyncStopListener) {
        getConfiguration().setAsyncStopListener(asyncStopListener);
    }

    /**
     * When using mapJmsMessage=false Camel will create a new JMS message to send to a new JMS destination
     * if you touch the headers (get or set) during the route. Set this option to true to force Camel to send
     * the original JMS message that was received.
     */
    public void setForceSendOriginalMessage(boolean forceSendOriginalMessage) {
        getConfiguration().setForceSendOriginalMessage(forceSendOriginalMessage);
    }

    /**
     * The timeout for waiting for a reply when using the InOut Exchange Pattern (in milliseconds).
     * The default is 20 seconds. You can include the header "CamelJmsRequestTimeout" to override this endpoint configured
     * timeout value, and thus have per message individual timeout values.
     * See also the requestTimeoutCheckerInterval option.
     */
    public void setRequestTimeout(long requestTimeout) {
        getConfiguration().setRequestTimeout(requestTimeout);
    }

    /**
     * Configures how often Camel should check for timed out Exchanges when doing request/reply over JMS.
     * By default Camel checks once per second. But if you must react faster when a timeout occurs,
     * then you can lower this interval, to check more frequently. The timeout is determined by the option requestTimeout.
     */
    public void setRequestTimeoutCheckerInterval(long requestTimeoutCheckerInterval) {
        getConfiguration().setRequestTimeoutCheckerInterval(requestTimeoutCheckerInterval);
    }

    /**
     * You can transfer the exchange over the wire instead of just the body and headers.
     * The following fields are transferred: In body, Out body, Fault body, In headers, Out headers, Fault headers,
     * exchange properties, exchange exception.
     * This requires that the objects are serializable. Camel will exclude any non-serializable objects and log it at WARN level.
     * You must enable this option on both the producer and consumer side, so Camel knows the payloads is an Exchange and not a regular payload.
     */
    public void setTransferExchange(boolean transferExchange) {
        getConfiguration().setTransferExchange(transferExchange);
    }

    /**
     * If enabled and you are using Request Reply messaging (InOut) and an Exchange failed on the consumer side,
     * then the caused Exception will be send back in response as a javax.jms.ObjectMessage.
     * If the client is Camel, the returned Exception is rethrown. This allows you to use Camel JMS as a bridge
     * in your routing - for example, using persistent queues to enable robust routing.
     * Notice that if you also have transferExchange enabled, this option takes precedence.
     * The caught exception is required to be serializable.
     * The original Exception on the consumer side can be wrapped in an outer exception
     * such as org.apache.camel.RuntimeCamelException when returned to the producer.
     */
    public void setTransferException(boolean transferException) {
        getConfiguration().setTransferException(transferException);
    }

    /**
     * Allows you to use your own implementation of the org.springframework.jms.core.JmsOperations interface.
     * Camel uses JmsTemplate as default. Can be used for testing purpose, but not used much as stated in the spring API docs.
     */
    public void setJmsOperations(JmsOperations jmsOperations) {
        getConfiguration().setJmsOperations(jmsOperations);
    }

    /**
     * A pluggable org.springframework.jms.support.destination.DestinationResolver that allows you to use your own resolver
     * (for example, to lookup the real destination in a JNDI registry).
     */
    public void setDestinationResolver(DestinationResolver destinationResolver) {
        getConfiguration().setDestinationResolver(destinationResolver);
    }

    /**
     * Allows for explicitly specifying which kind of strategy to use for replyTo queues when doing request/reply over JMS.
     * Possible values are: Temporary, Shared, or Exclusive.
     * By default Camel will use temporary queues. However if replyTo has been configured, then Shared is used by default.
     * This option allows you to use exclusive queues instead of shared ones.
     * See Camel JMS documentation for more details, and especially the notes about the implications if running in a clustered environment,
     * and the fact that Shared reply queues has lower performance than its alternatives Temporary and Exclusive.
     */
    public void setReplyToType(ReplyToType replyToType) {
        getConfiguration().setReplyToType(replyToType);
    }

    /**
     * Set to true, if you want to send message using the QoS settings specified on the message,
     * instead of the QoS settings on the JMS endpoint. The following three headers are considered JMSPriority, JMSDeliveryMode,
     * and JMSExpiration. You can provide all or only some of them. If not provided, Camel will fall back to use the
     * values from the endpoint instead. So, when using this option, the headers override the values from the endpoint.
     * The explicitQosEnabled option, by contrast, will only use options set on the endpoint, and not values from the message header.
     */
    public void setPreserveMessageQos(boolean preserveMessageQos) {
        getConfiguration().setPreserveMessageQos(preserveMessageQos);
    }

    /**
     * Whether the JmsConsumer processes the Exchange asynchronously.
     * If enabled then the JmsConsumer may pickup the next message from the JMS queue,
     * while the previous message is being processed asynchronously (by the Asynchronous Routing Engine).
     * This means that messages may be processed not 100% strictly in order. If disabled (as default)
     * then the Exchange is fully processed before the JmsConsumer will pickup the next message from the JMS queue.
     * Note if transacted has been enabled, then asyncConsumer=true does not run asynchronously, as transaction
     * must be executed synchronously (Camel 3.0 may support async transactions).
     */
    public void setAsyncConsumer(boolean asyncConsumer) {
        getConfiguration().setAsyncConsumer(asyncConsumer);
    }

    /**
     * Whether to allow sending messages with no body. If this option is false and the message body is null, then an JMSException is thrown.
     */
    public void setAllowNullBody(boolean allowNullBody) {
        getConfiguration().setAllowNullBody(allowNullBody);
    }

    /**
     * Only applicable when sending to JMS destination using InOnly (eg fire and forget).
     * Enabling this option will enrich the Camel Exchange with the actual JMSMessageID
     * that was used by the JMS client when the message was sent to the JMS destination.
     */
    public void setIncludeSentJMSMessageID(boolean includeSentJMSMessageID) {
        getConfiguration().setIncludeSentJMSMessageID(includeSentJMSMessageID);
    }

    /**
     * Whether to include all JMSXxxx properties when mapping from JMS to Camel Message.
     * Setting this to true will include properties such as JMSXAppID, and JMSXUserID etc.
     * Note: If you are using a custom headerFilterStrategy then this option does not apply.
     */
    public void setIncludeAllJMSXProperties(boolean includeAllJMSXProperties) {
        getConfiguration().setIncludeAllJMSXProperties(includeAllJMSXProperties);
    }

    /**
     * Specifies what default TaskExecutor type to use in the DefaultMessageListenerContainer,
     * for both consumer endpoints and the ReplyTo consumer of producer endpoints.
     * Possible values: SimpleAsync (uses Spring's SimpleAsyncTaskExecutor) or ThreadPool
     * (uses Spring's ThreadPoolTaskExecutor with optimal values - cached threadpool-like).
     * If not set, it defaults to the previous behaviour, which uses a cached thread pool
     * for consumer endpoints and SimpleAsync for reply consumers.
     * The use of ThreadPool is recommended to reduce "thread trash" in elastic configurations
     * with dynamically increasing and decreasing concurrent consumers.
     */
    public void setDefaultTaskExecutorType(DefaultTaskExecutorType type) {
        getConfiguration().setDefaultTaskExecutorType(type);
    }

    /**
     * Pluggable strategy for encoding and decoding JMS keys so they can be compliant with the JMS specification.
     * Camel provides two implementations out of the box: default and passthrough.
     * The default strategy will safely marshal dots and hyphens (. and -). The passthrough strategy leaves the key as is.
     * Can be used for JMS brokers which do not care whether JMS header keys contain illegal characters.
     * You can provide your own implementation of the org.apache.camel.component.jms.JmsKeyFormatStrategy
     * and refer to it using the # notation.
     */
    public void setJmsKeyFormatStrategy(JmsKeyFormatStrategy jmsKeyFormatStrategy) {
        getConfiguration().setJmsKeyFormatStrategy(jmsKeyFormatStrategy);
    }

    /**
     * Pluggable strategy for encoding and decoding JMS keys so they can be compliant with the JMS specification.
     * Camel provides two implementations out of the box: default and passthrough.
     * The default strategy will safely marshal dots and hyphens (. and -). The passthrough strategy leaves the key as is.
     * Can be used for JMS brokers which do not care whether JMS header keys contain illegal characters.
     * You can provide your own implementation of the org.apache.camel.component.jms.JmsKeyFormatStrategy
     * and refer to it using the # notation.
     */
    public void setJmsKeyFormatStrategy(String jmsKeyFormatStrategyName) {
        // allow to configure a standard by its name, which is simpler
        JmsKeyFormatStrategy strategy = resolveStandardJmsKeyFormatStrategy(jmsKeyFormatStrategyName);
        if (strategy == null) {
            throw new IllegalArgumentException("JmsKeyFormatStrategy with name " + jmsKeyFormatStrategyName + " is not a standard supported name");
        } else {
            getConfiguration().setJmsKeyFormatStrategy(strategy);
        }
    }

    /**
     * Sets the Spring ApplicationContext to use
     */
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
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

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
     */
    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        this.headerFilterStrategy = strategy;
    }

    public MessageCreatedStrategy getMessageCreatedStrategy() {
        return messageCreatedStrategy;
    }

    /**
     * To use the given MessageCreatedStrategy which are invoked when Camel creates new instances of <tt>javax.jms.Message</tt>
     * objects when Camel is sending a JMS message.
     */
    public void setMessageCreatedStrategy(MessageCreatedStrategy messageCreatedStrategy) {
        this.messageCreatedStrategy = messageCreatedStrategy;
    }

    // Implementation methods
    // -------------------------------------------------------------------------


    @Override
    protected void doStart() throws Exception {
        if (headerFilterStrategy == null) {
            headerFilterStrategy = new JmsHeaderFilterStrategy(getConfiguration().isIncludeAllJMSXProperties());
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

        String username = getAndRemoveParameter(parameters, "username", String.class);
        String password = getAndRemoveParameter(parameters, "password", String.class);
        if (username != null && password != null) {
            cf = endpoint.getConfiguration().getConnectionFactory();
            UserCredentialsConnectionFactoryAdapter ucfa = new UserCredentialsConnectionFactoryAdapter();
            ucfa.setTargetConnectionFactory(cf);
            ucfa.setPassword(password);
            ucfa.setUsername(username);
            endpoint.getConfiguration().setConnectionFactory(ucfa);
        } else {
            if (username != null || password != null) {
                // exclude the the saturation of username and password are all empty
                throw new IllegalArgumentException("The JmsComponent's username or password is null");
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

        setProperties(endpoint.getConfiguration(), parameters);
        endpoint.setHeaderFilterStrategy(getHeaderFilterStrategy());

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
