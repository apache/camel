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

import java.util.concurrent.ExecutorService;

import javax.jms.Connection;

import org.apache.camel.FailedToCreateConsumerException;
import org.apache.camel.Processor;
import org.apache.camel.Suspendable;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.support.JmsUtils;

/**
 * A {@link org.apache.camel.Consumer} which uses Spring's {@link AbstractMessageListenerContainer} implementations
 * to consume JMS messages.
 * @see DefaultJmsMessageListenerContainer
 * @see SimpleJmsMessageListenerContainer
 */
@ManagedResource(description = "Managed JMS Consumer")
public class JmsConsumer extends DefaultConsumer implements Suspendable {

    private static final Logger LOG = LoggerFactory.getLogger(JmsConsumer.class);

    private volatile AbstractMessageListenerContainer listenerContainer;
    private volatile EndpointMessageListener messageListener;
    private volatile boolean initialized;
    private volatile ExecutorService executorService;
    private volatile boolean shutdownExecutorService;

    public JmsConsumer(JmsEndpoint endpoint, Processor processor, AbstractMessageListenerContainer listenerContainer) {
        super(endpoint, processor);
        this.listenerContainer = listenerContainer;
        this.listenerContainer.setMessageListener(getEndpointMessageListener());
    }

    @Override
    public JmsEndpoint getEndpoint() {
        return (JmsEndpoint) super.getEndpoint();
    }

    public AbstractMessageListenerContainer getListenerContainer() throws Exception {
        if (listenerContainer == null) {
            createMessageListenerContainer();
        }
        return listenerContainer;
    }

    public EndpointMessageListener getEndpointMessageListener() {
        if (messageListener == null) {
            createMessageListener(getEndpoint(), getProcessor());
        }
        return messageListener;
    }

    protected void createMessageListener(JmsEndpoint endpoint, Processor processor) {
        messageListener = new EndpointMessageListener(endpoint, processor);
        getEndpoint().getConfiguration().configureMessageListener(messageListener);
        messageListener.setBinding(endpoint.getBinding());
        messageListener.setAsync(endpoint.getConfiguration().isAsyncConsumer());
    }

    protected void createMessageListenerContainer() throws Exception {
        listenerContainer = getEndpoint().createMessageListenerContainer();
        getEndpoint().configureListenerContainer(listenerContainer, this);
        listenerContainer.setMessageListener(getEndpointMessageListener());
    }

    /**
     * Sets the {@link ExecutorService} the {@link AbstractMessageListenerContainer} is using (if any).
     * <p/>
     * The {@link AbstractMessageListenerContainer} may use a private thread pool, and then when this consumer
     * is stopped, we need to shutdown this thread pool as well, to clean up all resources.
     * If a shared thread pool is used by the {@link AbstractMessageListenerContainer} then the lifecycle
     * of that shared thread pool is handled elsewhere (not by this consumer); and therefore
     * the <tt>shutdownExecutorService</tt> parameter should be <tt>false</tt>.
     *
     * @param executorService         the thread pool
     * @param shutdownExecutorService whether to shutdown the thread pool when this consumer stops
     */
    void setListenerContainerExecutorService(ExecutorService executorService, boolean shutdownExecutorService) {
        this.executorService = executorService;
        this.shutdownExecutorService = shutdownExecutorService;
    }

    /**
     * Starts the JMS listener container
     * <p/>
     * Can be used to start this consumer later if it was configured to not auto startup.
     */
    public void startListenerContainer() {
        LOG.trace("Starting listener container {} on destination {}", listenerContainer, getDestinationName());
        listenerContainer.start();
        LOG.debug("Started listener container {} on destination {}", listenerContainer, getDestinationName());
    }

    /**
     * Pre tests the connection before starting the listening.
     * <p/>
     * In case of connection failure the exception is thrown which prevents Camel from starting.
     *
     * @throws FailedToCreateConsumerException is thrown if testing the connection failed
     */
    protected void testConnectionOnStartup() throws FailedToCreateConsumerException {
        try {
            LOG.debug("Testing JMS Connection on startup for destination: {}", getDestinationName());
            Connection con = listenerContainer.getConnectionFactory().createConnection();
            JmsUtils.closeConnection(con);
            LOG.debug("Successfully tested JMS Connection on startup for destination: {}", getDestinationName());
        } catch (Exception e) {
            String msg = "Cannot get JMS Connection on startup for destination " + getDestinationName();
            throw new FailedToCreateConsumerException(getEndpoint(), msg, e);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // create listener container
        if (listenerContainer == null) {
            createMessageListenerContainer();
        }
        getEndpoint().onListenerContainerStarting(listenerContainer);

        if (getEndpoint().getConfiguration().isAsyncStartListener()) {
            getEndpoint().getAsyncStartStopExecutorService().submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        prepareAndStartListenerContainer();
                    } catch (Throwable e) {
                        LOG.warn("Error starting listener container on destination: " + getDestinationName() + ". This exception will be ignored.", e);
                    }
                }

                @Override
                public String toString() {
                    return "AsyncStartListenerTask[" + getDestinationName() + "]";
                }
            });
        } else {
            prepareAndStartListenerContainer();
        }

        // mark as initialized for the first time
        initialized = true;
    }
    
    protected void prepareAndStartListenerContainer() {
        listenerContainer.afterPropertiesSet();

        // only start listener if auto start is enabled or we are explicit invoking start later
        if (initialized || getEndpoint().isAutoStartup()) {
            // should we pre test connections before starting?
            if (getEndpoint().isTestConnectionOnStartup()) {
                testConnectionOnStartup();
            }
            startListenerContainer();
        }
    }

    protected void stopAndDestroyListenerContainer() {
        if (listenerContainer != null) {
            try {
                listenerContainer.stop();
                listenerContainer.destroy();
            } finally {
                getEndpoint().onListenerContainerStopped(listenerContainer);
            }
        }
        // null container and listener so they are fully re created if this consumer is restarted
        // then we will use updated configuration from jms endpoint that may have been managed using JMX
        listenerContainer = null;
        messageListener = null;
        initialized = false;

        // shutdown thread pool if listener container was using a private thread pool
        if (shutdownExecutorService && executorService != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executorService);
        }
        executorService = null;
    }

    @Override
    protected void doStop() throws Exception {
        if (listenerContainer != null) {

            if (getEndpoint().getConfiguration().isAsyncStopListener()) {
                getEndpoint().getAsyncStartStopExecutorService().submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            stopAndDestroyListenerContainer();
                        } catch (Throwable e) {
                            LOG.warn("Error stopping listener container on destination: " + getDestinationName() + ". This exception will be ignored.", e);
                        }
                    }

                    @Override
                    public String toString() {
                        return "AsyncStopListenerTask[" + getDestinationName() + "]";
                    }
                });
            } else {
                stopAndDestroyListenerContainer();
            }
        }

        super.doStop();
    }

    @Override
    protected void doSuspend() throws Exception {
        if (listenerContainer != null) {
            listenerContainer.stop();
        }
    }

    @Override
    protected void doResume() throws Exception {
        // we may not have been started before, and now the end user calls resume, so lets handle that and start it first
        if (!initialized) {
            doStart();
        } else {
            if (listenerContainer != null) {
                startListenerContainer();
            } else {
                LOG.warn("The listenerContainer is not instantiated. Probably there was a timeout during the Suspend operation. Please restart your consumer route.");
            }
        }
    }

    private String getDestinationName() {
        if (listenerContainer.getDestination() != null) {
            return listenerContainer.getDestination().toString();
        } else {
            return listenerContainer.getDestinationName();
        }
    }

    /**
     * Set the JMS message selector expression (or {@code null} if none).
     * Default is none.
     * <p>See the JMS specification for a detailed definition of selector expressions.
     * <p>Note: The message selector may be replaced at runtime, with the listener
     * container picking up the new selector value immediately (works e.g. with
     * DefaultMessageListenerContainer, as long as the cache level is less than
     * CACHE_CONSUMER). However, this is considered advanced usage; use it with care!
     */
    @ManagedAttribute(description = "Changes the JMS selector, as long the cache level is less than CACHE_CONSUMER.")
    public String getMessageSelector() {
        if (listenerContainer != null) {
            return listenerContainer.getMessageSelector();
        } else {
            return null;
        }
    }

    @ManagedAttribute(description = "Changes the JMS selector, as long the cache level is less than CACHE_CONSUMER.")
    public void setMessageSelector(String messageSelector) {
        if (listenerContainer != null) {
            listenerContainer.setMessageSelector(messageSelector);
        }
    }

}
