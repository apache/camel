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
import javax.jms.ConnectionFactory;
import javax.jms.ExceptionListener;
import javax.jms.Session;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.util.CastUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import static org.apache.camel.util.ObjectHelper.removeStartingCharacters;

/**
 * A <a href="http://activemq.apache.org/jms.html">JMS Component</a>
 *
 * @version 
 */
public class JmsComponent extends DefaultComponent implements ApplicationContextAware, HeaderFilterStrategyAware {

    private static final transient Logger LOG = LoggerFactory.getLogger(JmsComponent.class);
    private static final String KEY_FORMAT_STRATEGY_PARAM = "jmsKeyFormatStrategy";
    private JmsConfiguration configuration;
    private ApplicationContext applicationContext;
    private QueueBrowseStrategy queueBrowseStrategy;
    private HeaderFilterStrategy headerFilterStrategy = new JmsHeaderFilterStrategy();

    public JmsComponent() {
    }

    public JmsComponent(CamelContext context) {
        super(context);
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
                Map<String, Object> beansOfType = 
                    CastUtils.cast(applicationContext.getBeansOfType(ConnectionFactory.class));
                if (!beansOfType.isEmpty()) {
                    ConnectionFactory cf = (ConnectionFactory)beansOfType.values().iterator().next();
                    configuration.setConnectionFactory(cf);
                }
                beansOfType = CastUtils.cast(applicationContext.getBeansOfType(DestinationResolver.class));
                if (!beansOfType.isEmpty()) {
                    DestinationResolver destinationResolver = (DestinationResolver)beansOfType.values()
                        .iterator().next();
                    configuration.setDestinationResolver(destinationResolver);
                }
            }
        }
        return configuration;
    }

    /**
     * Sets the JMS configuration
     *
     * @param configuration the configuration to use by default for endpoints
     */
    public void setConfiguration(JmsConfiguration configuration) {
        this.configuration = configuration;
    }

    public void setAcceptMessagesWhileStopping(boolean acceptMessagesWhileStopping) {
        getConfiguration().setAcceptMessagesWhileStopping(acceptMessagesWhileStopping);
    }

    public void setAcknowledgementMode(int consumerAcknowledgementMode) {
        getConfiguration().setAcknowledgementMode(consumerAcknowledgementMode);
    }

    public void setEagerLoadingOfProperties(boolean eagerLoadingOfProperties) {
        getConfiguration().setEagerLoadingOfProperties(eagerLoadingOfProperties);
    }

    public void setAcknowledgementModeName(String consumerAcknowledgementMode) {
        getConfiguration().setAcknowledgementModeName(consumerAcknowledgementMode);
    }

    public void setAutoStartup(boolean autoStartup) {
        getConfiguration().setAutoStartup(autoStartup);
    }

    public void setCacheLevel(int cacheLevel) {
        getConfiguration().setCacheLevel(cacheLevel);
    }

    public void setCacheLevelName(String cacheName) {
        getConfiguration().setCacheLevelName(cacheName);
    }

    public void setClientId(String consumerClientId) {
        getConfiguration().setClientId(consumerClientId);
    }

    public void setConcurrentConsumers(int concurrentConsumers) {
        getConfiguration().setConcurrentConsumers(concurrentConsumers);
    }

    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        getConfiguration().setConnectionFactory(connectionFactory);
    }

    public void setDeliveryPersistent(boolean deliveryPersistent) {
        getConfiguration().setDeliveryPersistent(deliveryPersistent);
    }

    public void setDurableSubscriptionName(String durableSubscriptionName) {
        getConfiguration().setDurableSubscriptionName(durableSubscriptionName);
    }

    public void setExceptionListener(ExceptionListener exceptionListener) {
        getConfiguration().setExceptionListener(exceptionListener);
    }

    public void setExplicitQosEnabled(boolean explicitQosEnabled) {
        getConfiguration().setExplicitQosEnabled(explicitQosEnabled);
    }

    public void setExposeListenerSession(boolean exposeListenerSession) {
        getConfiguration().setExposeListenerSession(exposeListenerSession);
    }

    public void setIdleTaskExecutionLimit(int idleTaskExecutionLimit) {
        getConfiguration().setIdleTaskExecutionLimit(idleTaskExecutionLimit);
    }

    public void setMaxConcurrentConsumers(int maxConcurrentConsumers) {
        getConfiguration().setMaxConcurrentConsumers(maxConcurrentConsumers);
    }

    public void setMaxMessagesPerTask(int maxMessagesPerTask) {
        getConfiguration().setMaxMessagesPerTask(maxMessagesPerTask);
    }

    public void setMessageConverter(MessageConverter messageConverter) {
        getConfiguration().setMessageConverter(messageConverter);
    }

    public void setMapJmsMessage(boolean mapJmsMessage) {
        getConfiguration().setMapJmsMessage(mapJmsMessage);
    }

    public void setMessageIdEnabled(boolean messageIdEnabled) {
        getConfiguration().setMessageIdEnabled(messageIdEnabled);
    }

    public void setMessageTimestampEnabled(boolean messageTimestampEnabled) {
        getConfiguration().setMessageTimestampEnabled(messageTimestampEnabled);
    }

    public void setAlwaysCopyMessage(boolean alwaysCopyMessage) {
        getConfiguration().setAlwaysCopyMessage(alwaysCopyMessage);
    }

    public void setUseMessageIDAsCorrelationID(boolean useMessageIDAsCorrelationID) {
        getConfiguration().setUseMessageIDAsCorrelationID(useMessageIDAsCorrelationID);
    }

    public void setPriority(int priority) {
        getConfiguration().setPriority(priority);
    }

    public void setPubSubNoLocal(boolean pubSubNoLocal) {
        getConfiguration().setPubSubNoLocal(pubSubNoLocal);
    }

    public void setReceiveTimeout(long receiveTimeout) {
        getConfiguration().setReceiveTimeout(receiveTimeout);
    }

    public void setRecoveryInterval(long recoveryInterval) {
        getConfiguration().setRecoveryInterval(recoveryInterval);
    }

    @Deprecated
    public void setSubscriptionDurable(boolean subscriptionDurable) {
        getConfiguration().setSubscriptionDurable(subscriptionDurable);
    }

    public void setTaskExecutor(TaskExecutor taskExecutor) {
        getConfiguration().setTaskExecutor(taskExecutor);
    }

    public void setTimeToLive(long timeToLive) {
        getConfiguration().setTimeToLive(timeToLive);
    }

    public void setTransacted(boolean consumerTransacted) {
        getConfiguration().setTransacted(consumerTransacted);
    }

    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        getConfiguration().setTransactionManager(transactionManager);
    }

    public void setTransactionName(String transactionName) {
        getConfiguration().setTransactionName(transactionName);
    }

    public void setTransactionTimeout(int transactionTimeout) {
        getConfiguration().setTransactionTimeout(transactionTimeout);
    }

    public void setTestConnectionOnStartup(boolean testConnectionOnStartup) {
        getConfiguration().setTestConnectionOnStartup(testConnectionOnStartup);
    }

    public void setRequestTimeout(long requestTimeout) {
        getConfiguration().setRequestTimeout(requestTimeout);
    }

    public void setTransferExchange(boolean transferExchange) {
        getConfiguration().setTransferExchange(transferExchange);
    }

    public void setTransferException(boolean transferException) {
        getConfiguration().setTransferException(transferException);
    }

    public void setJmsOperations(JmsOperations jmsOperations) {
        getConfiguration().setJmsOperations(jmsOperations);
    }

    public void setDestinationResolver(DestinationResolver destinationResolver) {
        getConfiguration().setDestinationResolver(destinationResolver);
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public QueueBrowseStrategy getQueueBrowseStrategy() {
        if (queueBrowseStrategy == null) {
            queueBrowseStrategy = new DefaultQueueBrowseStrategy();
        }
        return queueBrowseStrategy;
    }

    public void setQueueBrowseStrategy(QueueBrowseStrategy queueBrowseStrategy) {
        this.queueBrowseStrategy = queueBrowseStrategy;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        this.headerFilterStrategy = strategy;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    @Override
    protected void doStop() throws Exception {
        super.doStop();
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
                endpoint = new JmsTemporaryTopicEndpoint(uri, this, subject, newConfiguration);
            } else {
                endpoint = new JmsEndpoint(uri, this, subject, pubSubDomain, newConfiguration);
            }
        } else {
            QueueBrowseStrategy strategy = getQueueBrowseStrategy();
            if (tempDestination) {
                endpoint = new JmsTemporaryQueueEndpoint(uri, this, subject, newConfiguration, strategy);
            } else {
                endpoint = new JmsQueueEndpoint(uri, this, subject, newConfiguration, strategy);
            }
        }

        String selector = getAndRemoveParameter(parameters, "selector", String.class);
        if (selector != null) {
            endpoint.setSelector(selector);
        }
        String username = getAndRemoveParameter(parameters, "username", String.class);
        String password = getAndRemoveParameter(parameters, "password", String.class);
        if (username != null && password != null) {
            ConnectionFactory cf = endpoint.getConfiguration().getConnectionFactory();
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
        if ("default".equalsIgnoreCase(strategyVal)) {
            endpoint.setJmsKeyFormatStrategy(new DefaultJmsKeyFormatStrategy());
        } else if ("passthrough".equalsIgnoreCase(strategyVal)) {
            endpoint.setJmsKeyFormatStrategy(new PassThroughJmsKeyFormatStrategy());
        } else { // a reference
            parameters.put(KEY_FORMAT_STRATEGY_PARAM, strategyVal);
            endpoint.setJmsKeyFormatStrategy(resolveAndRemoveReferenceParameter(
                    parameters, KEY_FORMAT_STRATEGY_PARAM, JmsKeyFormatStrategy.class));
        }
        
        setProperties(endpoint.getConfiguration(), parameters);
        endpoint.setHeaderFilterStrategy(getHeaderFilterStrategy());

        return endpoint;
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
