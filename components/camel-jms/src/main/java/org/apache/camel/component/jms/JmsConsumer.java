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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;

import org.apache.camel.FailedToCreateConsumerException;
import org.apache.camel.Processor;
import org.apache.camel.Suspendable;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.listener.SimpleMessageListenerContainer;
import org.springframework.jms.support.JmsUtils;

/**
 * A {@link org.apache.camel.Consumer} which uses Spring's {@link AbstractMessageListenerContainer} implementations to
 * consume JMS messages.
 *
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
    // makes the ownership check of the asynchronous start task atomic with the detach in doStop, and its status
    // checks with doResume
    private final Lock containerLock = new ReentrantLock();
    // guarded by containerLock: doStart counted the current listener container as running on the endpoint
    private boolean listenerContainerCounted;
    // guarded by containerLock: an asynchronous start task of the current listener container has not checked the
    // status of the consumer yet
    private boolean asyncStartPending;

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
        messageListener = new EndpointMessageListener(this, endpoint, processor);
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
     * The {@link AbstractMessageListenerContainer} may use a private thread pool, and then when this consumer is
     * stopped, we need to shutdown this thread pool as well, to clean up all resources. If a shared thread pool is used
     * by the {@link AbstractMessageListenerContainer} then the lifecycle of that shared thread pool is handled
     * elsewhere (not by this consumer); and therefore the <tt>shutdownExecutorService</tt> parameter should be
     * <tt>false</tt>.
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
        startListenerContainer(listenerContainer);
    }

    private void startListenerContainer(AbstractMessageListenerContainer container) {
        LOG.trace("Starting listener container {} on destination {}", container, getDestinationName(container));
        container.start();
        LOG.debug("Started listener container {} on destination {}", container, getDestinationName(container));
    }

    /**
     * Pre tests the connection before starting the listening.
     * <p/>
     * In case of connection failure the exception is thrown which prevents Camel from starting.
     *
     * @throws FailedToCreateConsumerException is thrown if testing the connection failed
     */
    protected void testConnectionOnStartup() throws FailedToCreateConsumerException {
        testConnectionOnStartup(listenerContainer);
    }

    private void testConnectionOnStartup(AbstractMessageListenerContainer container) throws FailedToCreateConsumerException {
        try {
            LOG.debug("Testing JMS Connection on startup for destination: {}", getDestinationName(container));
            ConnectionFactory connectionfactory = container.getConnectionFactory();
            if (connectionfactory != null) {
                Connection con = connectionfactory.createConnection();
                JmsUtils.closeConnection(con);
            } else {
                LOG.error("connection factory is null");
                throw new IllegalStateException("connection factory is null");
            }
            LOG.debug("Successfully tested JMS Connection on startup for destination: {}", getDestinationName(container));
        } catch (Exception e) {
            String msg = "Cannot get JMS Connection on startup for destination " + getDestinationName(container);
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
        final AbstractMessageListenerContainer container = listenerContainer;
        warnIfTopicConsumersDoNotShareSubscription(container);
        getEndpoint().onListenerContainerStarting();
        containerLock.lock();
        try {
            listenerContainerCounted = true;
        } finally {
            containerLock.unlock();
        }
        prepareAndStartListenerContainer(container, getEndpoint().getConfiguration().isAsyncStartListener());
    }

    /**
     * Initializes and starts the listener container, in a task of the component's async start/stop thread pool when
     * async is true, otherwise on the calling thread.
     */
    private void prepareAndStartListenerContainer(AbstractMessageListenerContainer container, boolean async) {
        if (!async) {
            prepareAndStartListenerContainer(container);
            return;
        }
        containerLock.lock();
        try {
            asyncStartPending = true;
        } finally {
            containerLock.unlock();
        }
        getEndpoint().getAsyncStartStopExecutorService().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    prepareAndStartListenerContainer(container);
                } catch (Exception e) {
                    LOG.warn("Error starting listener container on destination: {}. This exception will be ignored.",
                            getDestinationName(container), e);
                }
            }

            @Override
            public String toString() {
                return "AsyncStartListenerTask[" + getDestinationName(container) + "]";
            }
        });
    }

    protected void prepareAndStartListenerContainer() {
        prepareAndStartListenerContainer(listenerContainer);
    }

    /**
     * On a topic, each consumer of the listener container is a subscriber of its own, unless the subscription is
     * shared: with more than one consumer, every message is processed once per consumer.
     */
    private void warnIfTopicConsumersDoNotShareSubscription(AbstractMessageListenerContainer container) {
        if (!container.isPubSubDomain() || container.isSubscriptionShared()) {
            return;
        }
        int min;
        int max;
        if (container instanceof DefaultMessageListenerContainer dmlc) {
            min = dmlc.getConcurrentConsumers();
            max = dmlc.getMaxConcurrentConsumers();
        } else if (container instanceof SimpleMessageListenerContainer) {
            // the simple container does not scale: it uses maxConcurrentConsumers when set
            JmsConfiguration configuration = getEndpoint().getConfiguration();
            min = Math.max(configuration.getConcurrentConsumers(), configuration.getMaxConcurrentConsumers());
            max = min;
        } else {
            return;
        }
        if (max <= 1) {
            return;
        }
        String consumers = min == max ? String.valueOf(max) : "up to " + max;
        String effect = container.isSubscriptionDurable()
                ? "a durable subscription that is not shared accepts only one consumer"
                : "each consumer has its own subscription, so every message is processed " + consumers + " times";
        LOG.warn("The consumer on topic {} has {} concurrent consumers without a shared subscription: {}."
                 + " Set subscriptionShared=true (JMS 2.0) with a subscriptionName to share the messages between the consumers,"
                 + " or use a single consumer (concurrentConsumers=1 and no maxConcurrentConsumers).",
                getDestinationName(container), consumers, effect);
    }

    private void prepareAndStartListenerContainer(AbstractMessageListenerContainer container) {
        if (listenerContainer != container) {
            // an early exit without the lock: the check that decides is the one under containerLock below.
            // The consumer was stopped (and maybe started again with a new listener container) before this
            // asynchronous start task ran
            LOG.debug("Not starting listener container {} on destination {} as the consumer no longer uses it", container,
                    getDestinationName(container));
            return;
        }

        // only start listener if auto start is enabled or we are explicit invoking start later
        boolean autoStart = initialized || getEndpoint().isAutoStartup();
        boolean start = false;
        try {
            // initialize only once: every initialization of a DefaultMessageListenerContainer schedules another
            // concurrentConsumers invokers. The container is already active when an earlier start of it failed
            // (testConnectionOnStartup) and the consumer was suspended and resumed
            if (!container.isActive()) {
                container.afterPropertiesSet();
            }

            containerLock.lock();
            try {
                if (listenerContainer == container) {
                    // a consumer suspended before this point is started by doResume, and so is one suspended and
                    // resumed after it: doResume starts the container itself once initialized is true
                    start = autoStart && !isSuspendingOrSuspended();
                    // mark as initialized after the auto-startup check
                    initialized = true;
                    asyncStartPending = false;
                }
            } finally {
                containerLock.unlock();
            }

            if (start) {
                // should we pre test connections before starting?
                if (getEndpoint().isTestConnectionOnStartup()) {
                    testConnectionOnStartup(container);
                }
                startListenerContainer(container);
            }
        } catch (RuntimeException e) {
            containerLock.lock();
            try {
                if (listenerContainer == container) {
                    // the start did not complete: a resume starts the same listener container again
                    initialized = false;
                    asyncStartPending = false;
                }
            } finally {
                containerLock.unlock();
            }
            throw e;
        }

        containerLock.lock();
        try {
            if (listenerContainer == container) {
                if (start && isSuspendingOrSuspended()) {
                    // suspended while this task was starting the container, and doSuspend may have run before
                    // container.start(): stop it here. Under the lock, so a resume that follows reads initialized
                    // after this stop and starts the container again
                    LOG.debug("Stopping listener container {} on destination {} as the consumer was suspended",
                            container, getDestinationName(container));
                    container.stop();
                }
                return;
            }
        } finally {
            containerLock.unlock();
        }
        // the consumer was stopped while this task was starting the container, and its stop may have run before
        // container.start(): stop and destroy it here
        LOG.debug("Stopping listener container {} on destination {} as the consumer was stopped while starting it",
                container, getDestinationName(container));
        try {
            container.stop();
        } finally {
            container.destroy();
        }
    }

    /**
     * Stops and destroys the listener container of this consumer, which creates a new one when it is started again.
     */
    protected void stopAndDestroyListenerContainer() {
        DetachedListenerContainer detached = detachListenerContainer();
        if (detached != null) {
            stopAndDestroyListenerContainer(detached);
        }
    }

    /**
     * Takes the listener container (and its private thread pool) away from this consumer, so a later start creates a
     * new one and the (possibly asynchronous) stop of this one cannot touch it.
     */
    private DetachedListenerContainer detachListenerContainer() {
        containerLock.lock();
        try {
            AbstractMessageListenerContainer container = listenerContainer;
            if (container == null) {
                return null;
            }
            DetachedListenerContainer answer
                    = new DetachedListenerContainer(container, executorService, shutdownExecutorService);
            // null container and listener so they are fully re created if this consumer is restarted
            // then we will use updated configuration from jms endpoint that may have been managed using JMX
            listenerContainer = null;
            messageListener = null;
            initialized = false;
            listenerContainerCounted = false;
            asyncStartPending = false;
            executorService = null;
            return answer;
        } finally {
            containerLock.unlock();
        }
    }

    private void stopAndDestroyListenerContainer(DetachedListenerContainer detached) {
        try {
            try {
                detached.container().stop();
            } finally {
                // also when stop fails (a SimpleMessageListenerContainer does not swallow a failing Connection.stop())
                detached.container().destroy();
            }
        } finally {
            getEndpoint().onListenerContainerStopped();
            // shutdown thread pool if listener container was using a private thread pool
            if (detached.shutdownExecutorService() && detached.executorService() != null) {
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(detached.executorService());
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        final DetachedListenerContainer detached = detachListenerContainer();
        if (detached != null) {
            if (getEndpoint().getConfiguration().isAsyncStopListener()) {
                getEndpoint().getAsyncStartStopExecutorService().submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            stopAndDestroyListenerContainer(detached);
                        } catch (Exception e) {
                            LOG.warn("Error stopping listener container on destination: {}. This exception will be ignored.",
                                    getDestinationName(detached.container()), e);
                        }
                    }

                    @Override
                    public String toString() {
                        return "AsyncStopListenerTask[" + getDestinationName(detached.container()) + "]";
                    }
                });
            } else {
                stopAndDestroyListenerContainer(detached);
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
        final AbstractMessageListenerContainer container;
        final boolean wasInitialized;
        final boolean startAgain;
        containerLock.lock();
        try {
            container = listenerContainer;
            wasInitialized = initialized;
            if (!wasInitialized && asyncStartPending) {
                // the asynchronous start task has not checked the status yet: it starts the container, as the
                // consumer is no longer suspended
                LOG.debug("Resuming consumer on destination {} while the asynchronous start of its listener container"
                          + " is pending",
                        getDestinationName(container));
                return;
            }
            // the listener container was started and counted before, but its start failed (for example
            // testConnectionOnStartup with asyncStartListener=true)
            startAgain = !wasInitialized && listenerContainerCounted;
        } finally {
            containerLock.unlock();
        }

        if (startAgain) {
            // start the same listener container again: it is already counted as running on the endpoint
            LOG.info("Starting listener container on destination {} again, as its earlier start did not complete",
                    getDestinationName(container));
            prepareAndStartListenerContainer(container, getEndpoint().getConfiguration().isAsyncStartListener());
        } else if (!wasInitialized) {
            // we may not have been started before, and now the end user calls resume, so lets handle that and start it first
            doStart();
        } else if (container != null) {
            // also when an asynchronous start task is still starting the container: a suspend meanwhile may have
            // stopped it after the task's start()
            startListenerContainer(container);
        } else {
            LOG.warn(
                    "The listenerContainer is not instantiated. Probably there was a timeout during the Suspend operation. Please restart your consumer route.");
        }
    }

    private String getDestinationName() {
        return getDestinationName(listenerContainer);
    }

    private static String getDestinationName(AbstractMessageListenerContainer container) {
        if (container == null) {
            return null;
        } else if (container.getDestination() != null) {
            return container.getDestination().toString();
        } else {
            return container.getDestinationName();
        }
    }

    /**
     * Set the JMS message selector expression (or {@code null} if none). Default is none.
     * <p>
     * See the JMS specification for a detailed definition of selector expressions.
     * <p>
     * Note: The message selector may be replaced at runtime, with the listener container picking up the new selector
     * value immediately (works e.g. with DefaultMessageListenerContainer, as long as the cache level is less than
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

    /**
     * A listener container detached from this consumer by a stop, with the private thread pool it uses (if any).
     */
    private record DetachedListenerContainer(AbstractMessageListenerContainer container, ExecutorService executorService,
            boolean shutdownExecutorService) {
    }

}
