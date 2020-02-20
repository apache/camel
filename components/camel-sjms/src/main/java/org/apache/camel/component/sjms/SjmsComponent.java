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
package org.apache.camel.component.sjms;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelException;
import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.sjms.jms.ConnectionResource;
import org.apache.camel.component.sjms.jms.DefaultJmsKeyFormatStrategy;
import org.apache.camel.component.sjms.jms.DestinationCreationStrategy;
import org.apache.camel.component.sjms.jms.JmsKeyFormatStrategy;
import org.apache.camel.component.sjms.jms.MessageCreatedStrategy;
import org.apache.camel.component.sjms.taskmanager.TimedTaskManager;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HeaderFilterStrategyComponent;

/**
 * The <a href="http://camel.apache.org/sjms">Simple JMS</a> component.
 */
@Component("sjms")
public class SjmsComponent extends HeaderFilterStrategyComponent {

    private ExecutorService asyncStartStopExecutorService;

    @Metadata(label = "advanced", description = "A ConnectionFactory is required to enable the SjmsComponent. It can be set directly or set set as part of a ConnectionResource.")
    private ConnectionFactory connectionFactory;
    @Metadata(label = "advanced", description = "A ConnectionResource is an interface that allows for customization and container control of the ConnectionFactory."
                    + " * See Plugable Connection Resource Management for further details.")
    private ConnectionResource connectionResource;
    @Metadata(label = "advanced", description = "Pluggable strategy for encoding and decoding JMS keys so they can be compliant with the JMS specification."
        + " Camel provides one implementation out of the box: default. The default strategy will safely marshal dots and hyphens (. and -)."
        + " Can be used for JMS brokers which do not care whether JMS header keys contain illegal characters. You can provide your own implementation"
        + " of the org.apache.camel.component.jms.JmsKeyFormatStrategy and refer to it using the # notation.")
    private JmsKeyFormatStrategy jmsKeyFormatStrategy = new DefaultJmsKeyFormatStrategy();
    @Metadata(defaultValue = "1", description = "The maximum number of connections available to endpoints started under this component")
    private Integer connectionCount = 1;
    @Metadata(label = "transaction", description = "To configure which kind of commit strategy to use. Camel provides two implementations out of the box, default and batch.")
    private TransactionCommitStrategy transactionCommitStrategy;
    @Metadata(label = "advanced", description = "To use a custom TimedTaskManager")
    private TimedTaskManager timedTaskManager;
    @Metadata(label = "advanced", description = "To use a custom DestinationCreationStrategy.")
    private DestinationCreationStrategy destinationCreationStrategy;
    @Metadata(label = "advanced", description = "To use the given MessageCreatedStrategy which are invoked when Camel creates new instances"
        + " of <tt>javax.jms.Message</tt> objects when Camel is sending a JMS message.")
    private MessageCreatedStrategy messageCreatedStrategy;
    @Metadata(label = "advanced", defaultValue = "true", description = "When using the default {@link org.apache.camel.component.sjms.jms.ConnectionFactoryResource}"
        + " then should each {@link javax.jms.Connection} be tested (calling start) before returned from the pool.")
    private boolean connectionTestOnBorrow = true;
    @Metadata(label = "security", secret = true, description = "The username to use when creating {@link javax.jms.Connection} when using the"
        + " default {@link org.apache.camel.component.sjms.jms.ConnectionFactoryResource}.")
    private String connectionUsername;
    @Metadata(label = "security", secret = true, description = "The password to use when creating {@link javax.jms.Connection} when using the"
        + " default {@link org.apache.camel.component.sjms.jms.ConnectionFactoryResource}.")
    private String connectionPassword;
    @Metadata(label = "advanced", description = "The client ID to use when creating {@link javax.jms.Connection} when using the"
        + " default {@link org.apache.camel.component.sjms.jms.ConnectionFactoryResource}.")
    private String connectionClientId;
    @Metadata(label = "advanced", defaultValue = "5000", description = "The max wait time in millis to block and wait on free connection when the pool"
        + " is exhausted when using the default {@link org.apache.camel.component.sjms.jms.ConnectionFactoryResource}.")
    private long connectionMaxWait = 5000;
    @Metadata(label = "consumer", description = "Try to apply reconnection logic on consumer pool", defaultValue = "true")
    private boolean reconnectOnError = true;
    @Metadata(label = "consumer", description = "Backoff in millis on consumer pool reconnection attempts", defaultValue = "5000")
    private long reconnectBackOff = 5000;

    public SjmsComponent() {
    }

    protected SjmsComponent(Class<? extends Endpoint> endpointClass) {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        validateMepAndReplyTo(parameters);
        SjmsEndpoint endpoint = createSjmsEndpoint(uri, remaining);
        if (endpoint.isTransacted()) {
            endpoint.setSynchronous(true);
        }
        if (transactionCommitStrategy != null) {
            endpoint.setTransactionCommitStrategy(transactionCommitStrategy);
        }
        if (destinationCreationStrategy != null) {
            endpoint.setDestinationCreationStrategy(destinationCreationStrategy);
        }
        if (getHeaderFilterStrategy() != null) {
            endpoint.setHeaderFilterStrategy(getHeaderFilterStrategy());
        }
        if (messageCreatedStrategy != null) {
            endpoint.setMessageCreatedStrategy(messageCreatedStrategy);
        }
        endpoint.setReconnectOnError(reconnectOnError);
        endpoint.setReconnectBackOff(reconnectBackOff);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    protected SjmsEndpoint createSjmsEndpoint(String uri, String remaining) {
        return new SjmsEndpoint(uri, this, remaining);
    }

    /**
     * Helper method used to verify that when there is a namedReplyTo value we
     * are using the InOut MEP. If namedReplyTo is defined and the MEP is InOnly
     * the endpoint won't be expecting a reply so throw an error to alert the
     * user.
     *
     * @param parameters {@link Endpoint} parameters
     * @throws Exception throws a {@link CamelException} when MEP equals InOnly
     *                   and namedReplyTo is defined.
     */
    private static void validateMepAndReplyTo(Map<String, Object> parameters) throws Exception {
        boolean namedReplyToSet = parameters.containsKey("namedReplyTo");
        boolean mepSet = parameters.containsKey("exchangePattern");
        if (namedReplyToSet && mepSet) {
            if (!parameters.get("exchangePattern").equals(ExchangePattern.InOut.toString())) {
                String namedReplyTo = (String) parameters.get("namedReplyTo");
                ExchangePattern mep = ExchangePattern.valueOf((String) parameters.get("exchangePattern"));
                throw new CamelException("Setting parameter namedReplyTo=" + namedReplyTo + " requires a MEP of type InOut. Parameter exchangePattern is set to " + mep);
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        timedTaskManager = new TimedTaskManager();
    }

    @Override
    protected void doStop() throws Exception {
        if (timedTaskManager != null) {
            timedTaskManager.cancelTasks();
            timedTaskManager = null;
        }
        super.doStop();
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

    /**
     * A ConnectionFactory is required to enable the SjmsComponent.
     * It can be set directly or set set as part of a ConnectionResource.
     */
    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    /**
     * A ConnectionResource is an interface that allows for customization and container control of the ConnectionFactory.
     * See Plugable Connection Resource Management for further details.
     */
    public void setConnectionResource(ConnectionResource connectionResource) {
        this.connectionResource = connectionResource;
    }

    public ConnectionResource getConnectionResource() {
        return connectionResource;
    }

    /**
     * The maximum number of connections available to endpoints started under this component
     */
    public void setConnectionCount(Integer maxConnections) {
        this.connectionCount = maxConnections;
    }

    public Integer getConnectionCount() {
        return connectionCount;
    }

    /**
     * Pluggable strategy for encoding and decoding JMS keys so they can be compliant with the JMS specification.
     * Camel provides one implementation out of the box: default.
     * The default strategy will safely marshal dots and hyphens (. and -).
     * Can be used for JMS brokers which do not care whether JMS header keys contain illegal characters.
     * You can provide your own implementation of the org.apache.camel.component.jms.JmsKeyFormatStrategy
     * and refer to it using the # notation.
     */
    public void setJmsKeyFormatStrategy(JmsKeyFormatStrategy jmsKeyFormatStrategy) {
        this.jmsKeyFormatStrategy = jmsKeyFormatStrategy;
    }

    public JmsKeyFormatStrategy getJmsKeyFormatStrategy() {
        return jmsKeyFormatStrategy;
    }

    public TransactionCommitStrategy getTransactionCommitStrategy() {
        return transactionCommitStrategy;
    }

    /**
     * To configure which kind of commit strategy to use. Camel provides two implementations out
     * of the box, default and batch.
     */
    public void setTransactionCommitStrategy(TransactionCommitStrategy commitStrategy) {
        this.transactionCommitStrategy = commitStrategy;
    }

    public DestinationCreationStrategy getDestinationCreationStrategy() {
        return destinationCreationStrategy;
    }

    /**
     * To use a custom DestinationCreationStrategy.
     */
    public void setDestinationCreationStrategy(DestinationCreationStrategy destinationCreationStrategy) {
        this.destinationCreationStrategy = destinationCreationStrategy;
    }

    public TimedTaskManager getTimedTaskManager() {
        return timedTaskManager;
    }

    /**
     * To use a custom TimedTaskManager
     */
    public void setTimedTaskManager(TimedTaskManager timedTaskManager) {
        this.timedTaskManager = timedTaskManager;
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

    public boolean isConnectionTestOnBorrow() {
        return connectionTestOnBorrow;
    }

    /**
     * When using the default {@link org.apache.camel.component.sjms.jms.ConnectionFactoryResource} then should each {@link javax.jms.Connection}
     * be tested (calling start) before returned from the pool.
     */
    public void setConnectionTestOnBorrow(boolean connectionTestOnBorrow) {
        this.connectionTestOnBorrow = connectionTestOnBorrow;
    }

    public String getConnectionUsername() {
        return connectionUsername;
    }

    /**
     * The username to use when creating {@link javax.jms.Connection} when using the default {@link org.apache.camel.component.sjms.jms.ConnectionFactoryResource}.
     */
    public void setConnectionUsername(String connectionUsername) {
        this.connectionUsername = connectionUsername;
    }

    public String getConnectionPassword() {
        return connectionPassword;
    }

    /**
     * The password to use when creating {@link javax.jms.Connection} when using the default {@link org.apache.camel.component.sjms.jms.ConnectionFactoryResource}.
     */
    public void setConnectionPassword(String connectionPassword) {
        this.connectionPassword = connectionPassword;
    }

    public String getConnectionClientId() {
        return connectionClientId;
    }

    /**
     * The client ID to use when creating {@link javax.jms.Connection} when using the default {@link org.apache.camel.component.sjms.jms.ConnectionFactoryResource}.
     */
    public void setConnectionClientId(String connectionClientId) {
        this.connectionClientId = connectionClientId;
    }

    public long getConnectionMaxWait() {
        return connectionMaxWait;
    }

    /**
     * The max wait time in millis to block and wait on free connection when the pool is exhausted
     * when using the default {@link org.apache.camel.component.sjms.jms.ConnectionFactoryResource}.
     */
    public void setConnectionMaxWait(long connectionMaxWait) {
        this.connectionMaxWait = connectionMaxWait;
    }

    public boolean isReconnectOnError() {
        return reconnectOnError;
    }

    /**
     * Try to apply reconnection logic on consumer pool
     */
    public void setReconnectOnError(boolean reconnectOnError) {
        this.reconnectOnError = reconnectOnError;
    }

    public long getReconnectBackOff() {
        return reconnectBackOff;
    }

    /**
     * Backoff in millis on consumer pool reconnection attempts
     */
    public void setReconnectBackOff(long reconnectBackOff) {
        this.reconnectBackOff = reconnectBackOff;
    }
}
