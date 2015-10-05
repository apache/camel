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
package org.apache.camel.component.sjms;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import javax.jms.ConnectionFactory;

import org.apache.camel.CamelException;
import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.sjms.jms.ConnectionFactoryResource;
import org.apache.camel.component.sjms.jms.ConnectionResource;
import org.apache.camel.component.sjms.jms.DefaultJmsKeyFormatStrategy;
import org.apache.camel.component.sjms.jms.DestinationCreationStrategy;
import org.apache.camel.component.sjms.jms.JmsKeyFormatStrategy;
import org.apache.camel.component.sjms.jms.MessageCreatedStrategy;
import org.apache.camel.component.sjms.taskmanager.TimedTaskManager;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <a href="http://camel.apache.org/sjms">Simple JMS</a> component.
 */
public class SjmsComponent extends UriEndpointComponent implements HeaderFilterStrategyAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(SjmsComponent.class);

    private ConnectionFactory connectionFactory;
    private ConnectionResource connectionResource;
    private HeaderFilterStrategy headerFilterStrategy = new SjmsHeaderFilterStrategy();
    private JmsKeyFormatStrategy jmsKeyFormatStrategy = new DefaultJmsKeyFormatStrategy();
    private Integer connectionCount = 1;
    private TransactionCommitStrategy transactionCommitStrategy;
    private TimedTaskManager timedTaskManager;
    private DestinationCreationStrategy destinationCreationStrategy;
    private ExecutorService asyncStartStopExecutorService;
    private MessageCreatedStrategy messageCreatedStrategy;

    public SjmsComponent() {
        super(SjmsEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        validateMepAndReplyTo(parameters);
        SjmsEndpoint endpoint = new SjmsEndpoint(uri, this, remaining);
        setProperties(endpoint, parameters);
        if (endpoint.isTransacted()) {
            endpoint.setSynchronous(true);
        }
        if (transactionCommitStrategy != null) {
            endpoint.setTransactionCommitStrategy(transactionCommitStrategy);
        }
        if (destinationCreationStrategy != null) {
            endpoint.setDestinationCreationStrategy(destinationCreationStrategy);
        }
        if (headerFilterStrategy != null) {
            endpoint.setHeaderFilterStrategy(headerFilterStrategy);
        }
        if (messageCreatedStrategy != null) {
            endpoint.setMessageCreatedStrategy(messageCreatedStrategy);
        }
        return endpoint;
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

        LOGGER.trace("Verify ConnectionResource");
        if (getConnectionResource() == null) {
            LOGGER.debug("No ConnectionResource provided. Initialize the ConnectionFactoryResource.");
            // We always use a connection pool, even for a pool of 1
            ConnectionFactoryResource connections = new ConnectionFactoryResource(getConnectionCount(), getConnectionFactory());
            connections.fillPool();
            setConnectionResource(connections);
        } else if (getConnectionResource() instanceof ConnectionFactoryResource) {
            ((ConnectionFactoryResource) getConnectionResource()).fillPool();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (timedTaskManager != null) {
            timedTaskManager.cancelTasks();
        }

        if (getConnectionResource() != null) {
            if (getConnectionResource() instanceof ConnectionFactoryResource) {
                ((ConnectionFactoryResource) getConnectionResource()).drainPool();
            }
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

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return this.headerFilterStrategy;
    }

    /**
     * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
     */
    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
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

}
