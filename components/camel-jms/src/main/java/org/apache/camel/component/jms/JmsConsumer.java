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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
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
        createAndStartListenerContainer(false);
    }

    /**
     * Creates the listener container (unless there is one), counts it as running on the endpoint, and initializes and
     * starts it.
     *
     * @param start whether to start the listener container also when auto startup is disabled
     */
    private void createAndStartListenerContainer(boolean start) throws Exception {
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
        prepareAndStartListenerContainer(container, getEndpoint().getConfiguration().isAsyncStartListener(), start);
    }

    /**
     * Initializes and starts the listener container, in a task of the component's async start/stop thread pool when
     * async is true, otherwise on the calling thread.
     *
     * @param start whether to start the listener container also when auto startup is disabled
     */
    private void prepareAndStartListenerContainer(AbstractMessageListenerContainer container, boolean async, boolean start) {
        if (!async) {
            prepareAndStartListenerContainer(container, start);
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
                    prepareAndStartListenerContainer(container, start);
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
        prepareAndStartListenerContainer(listenerContainer, false);
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

    /**
     * @param startAnyway whether to start the listener container also when auto startup is disabled
     */
    private void prepareAndStartListenerContainer(AbstractMessageListenerContainer container, boolean startAnyway) {
        if (listenerContainer != container) {
            // an early exit without the lock: the check that decides is the one under containerLock below.
            // The consumer was stopped (and maybe started again with a new listener container) before this
            // asynchronous start task ran
            LOG.debug("Not starting listener container {} on destination {} as the consumer no longer uses it", container,
                    getDestinationName(container));
            return;
        }

        // only start listener if auto start is enabled or we are explicit invoking start later
        boolean autoStart = startAnyway || initialized || getEndpoint().isAutoStartup();
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
            // a later start creates the listener container, and starts it, as the first start did
            initialized = false;
            return doDetachListenerContainer();
        } finally {
            containerLock.unlock();
        }
    }

    /**
     * Takes the given listener container away from this consumer, when its stop for a suspend did not end in time. The
     * consumer stays initialized: a resume creates a new listener container and starts it, as it starts the one it
     * keeps after a suspend that ended in time.
     */
    private DetachedListenerContainer detachSuspendedListenerContainer(AbstractMessageListenerContainer container) {
        containerLock.lock();
        try {
            return listenerContainer == container ? doDetachListenerContainer() : null;
        } finally {
            containerLock.unlock();
        }
    }

    private DetachedListenerContainer doDetachListenerContainer() {
        AbstractMessageListenerContainer container = listenerContainer;
        if (container == null) {
            return null;
        }
        DetachedListenerContainer answer = new DetachedListenerContainer(container, executorService, shutdownExecutorService);
        // null container and listener so they are fully re created if this consumer is restarted
        // then we will use updated configuration from jms endpoint that may have been managed using JMX
        listenerContainer = null;
        messageListener = null;
        // the detached listener container is no longer counted once it is destroyed, and its start task (if any) no
        // longer starts it
        listenerContainerCounted = false;
        asyncStartPending = false;
        executorService = null;
        return answer;
    }

    private void stopAndDestroyListenerContainer(DetachedListenerContainer detached) {
        try {
            detached.container().stop();
        } finally {
            // also when stop fails (a SimpleMessageListenerContainer does not swallow a failing Connection.stop())
            destroyListenerContainer(detached);
        }
    }

    private void destroyListenerContainer(DetachedListenerContainer detached) {
        try {
            detached.container().destroy();
        } finally {
            getEndpoint().onListenerContainerStopped();
            // shutdown thread pool if listener container was using a private thread pool
            ExecutorService pool = detached.executorService();
            if (detached.shutdownExecutorService() && pool != null) {
                if (pool.isShutdown()) {
                    // shut down (and so no longer managed) when the consumer gave up waiting for the stop
                    pool.shutdownNow();
                } else {
                    getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(pool);
                }
            }
        }
    }

    /**
     * Runs the stop of a listener container on the async start/stop thread pool of the component, and waits for it at
     * most the time to wait for a thread pool to shut down
     * ({@code ExecutorServiceManager.getShutdownAwaitTermination()}, 10 seconds by default).
     * <p/>
     * The stop of a listener container stops its JMS connection, and a JMS client may not return from that call before
     * a timeout of its own elapses when the broker does not answer (the call timeout of ActiveMQ Artemis, 30 seconds by
     * default), whether the thread is interrupted or not. When the wait ends before the stop (the time elapsed, or the
     * waiting thread was interrupted), the listener container is no longer used by this consumer, and its stop goes on
     * in the background: see {@link ListenerContainerStop}.
     * <p/>
     * A suspend or stop from a thread that processes a message of the listener container (such as a route policy that
     * suspends the consumer when an exchange is done) stops the listener container on that thread, as before: a JMS
     * client stops a connection at once from its own message listener, but waits for that message listener from another
     * thread.
     *
     * @param operation the operation of this consumer that stops the listener container, for the log
     */
    private void stopWithinBound(String operation, ListenerContainerStop stop) {
        long timeout = getEndpoint().getCamelContext().getExecutorServiceManager().getShutdownAwaitTermination();
        if (EndpointMessageListener.isProcessingOnCurrentThread(stop.container.getMessageListener())
                || !runInBackground(stop)) {
            // from the message listener, or no thread pool to run the stop on: stop the listener container on this thread
            stop.run();
        }
        String reason = stop.await(timeout);
        if (reason != null) {
            LOG.warn("The {} of the consumer on destination {} {}: the JMS client may be waiting for a broker that does"
                     + " not answer. The listener container is stopped and destroyed in the background, and the consumer"
                     + " creates a new one when it is resumed or started again.",
                    operation, getDestinationName(stop.container), reason);
            DetachedListenerContainer detached = stop.givenUpContainer();
            if (detached != null && detached.shutdownExecutorService() && detached.executorService() != null) {
                // the threads of its private thread pool end once the listener container is destroyed: the pool takes
                // no new task, and the ExecutorServiceManager no longer waits for it when the CamelContext stops
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdown(detached.executorService());
            }
        }
    }

    private boolean runInBackground(Runnable task) {
        if (getEndpoint().getComponent() == null) {
            return false;
        }
        try {
            getEndpoint().getAsyncStartStopExecutorService().execute(task);
            return true;
        } catch (RejectedExecutionException e) {
            LOG.debug("Cannot run the task {} on the async start/stop thread pool: it runs on this thread", task, e);
            return false;
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
                stopWithinBound("stop", new ListenerContainerStop(detached));
            }
        }

        super.doStop();
    }

    @Override
    protected void doSuspend() throws Exception {
        AbstractMessageListenerContainer container = listenerContainer;
        if (container != null) {
            stopWithinBound("suspend", new ListenerContainerStop(container));
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
            if (wasInitialized && container == null) {
                // a suspend that did not end in time took the listener container away from this consumer. The new
                // one is initialized by its (possibly asynchronous) start, as at the first start
                initialized = false;
            }
        } finally {
            containerLock.unlock();
        }

        if (startAgain) {
            // start the same listener container again: it is already counted as running on the endpoint
            LOG.info("Starting listener container on destination {} again, as its earlier start did not complete",
                    getDestinationName(container));
            prepareAndStartListenerContainer(container, getEndpoint().getConfiguration().isAsyncStartListener(), false);
        } else if (!wasInitialized) {
            // we may not have been started before, and now the end user calls resume, so lets handle that and start it first
            doStart();
        } else if (container != null) {
            // also when an asynchronous start task is still starting the container: a suspend meanwhile may have
            // stopped it after the task's start()
            startListenerContainer(container);
        } else {
            // create a new listener container, and start it as the resume starts the listener container that a suspend
            // that ends in time keeps (also without auto startup)
            createAndStartListenerContainer(true);
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
     * A listener container detached from this consumer by a stop, or by a suspend that did not end in time, with the
     * private thread pool it uses (if any).
     */
    private record DetachedListenerContainer(AbstractMessageListenerContainer container, ExecutorService executorService,
            boolean shutdownExecutorService) {
    }

    /**
     * The stop of a listener container (for a suspend), or its stop and destroy (for a stop of this consumer, which
     * detached it already), which this consumer waits for at most a given time.
     * <p/>
     * When the consumer gives up waiting, the stop goes on in the background. For a suspend, the consumer detaches the
     * listener container: a resume (or a start) creates a new one, and the listener container is destroyed once its
     * stop returns. Its private thread pool takes no new task from the time the consumer gives up, and is shut down
     * once the listener container is destroyed. An exception of the stop is then logged, as nobody waits for it
     * anymore.
     */
    private final class ListenerContainerStop implements Runnable {

        private final AbstractMessageListenerContainer container;
        // for a stop of the consumer: the listener container it detached, to stop and destroy (null for a suspend)
        private final DetachedListenerContainer stopped;
        private final Lock lock = new ReentrantLock();
        private final Condition ended = lock.newCondition();
        // guarded by lock
        private boolean done;
        private boolean givenUp;
        // a RuntimeException or an Error
        private Throwable failure;
        // for a suspend: the listener container the consumer detached when it gave up waiting, to destroy once stopped
        private DetachedListenerContainer suspended;

        /**
         * The stop of the listener container, for a suspend.
         */
        ListenerContainerStop(AbstractMessageListenerContainer container) {
            this.container = container;
            this.stopped = null;
        }

        /**
         * The stop and destroy of the detached listener container, for a stop of the consumer.
         */
        ListenerContainerStop(DetachedListenerContainer detached) {
            this.container = detached.container();
            this.stopped = detached;
        }

        @Override
        public void run() {
            Throwable error = null;
            try {
                if (stopped != null) {
                    stopAndDestroyListenerContainer(stopped);
                } else {
                    container.stop();
                }
            } catch (RuntimeException | Error e) {
                // handed over to the consumer, as the stop of the listener container on its own thread would
                error = e;
            }
            DetachedListenerContainer toDestroy;
            lock.lock();
            try {
                done = true;
                failure = error;
                ended.signalAll();
                if (!givenUp) {
                    // the consumer gets the outcome
                    return;
                }
                toDestroy = suspended;
            } finally {
                lock.unlock();
            }
            if (error != null) {
                LOG.warn("Error stopping listener container on destination: {} in the background."
                         + " This exception will be ignored.",
                        getDestinationName(container), error);
            }
            if (toDestroy != null) {
                try {
                    destroyListenerContainer(toDestroy);
                } catch (RuntimeException | Error e) {
                    LOG.warn("Error destroying listener container on destination: {} in the background."
                             + " This exception will be ignored.",
                            getDestinationName(container), e);
                }
            }
        }

        /**
         * Waits for the stop at most the given time. When the stop ended in time and failed, its exception is thrown.
         *
         * @return <tt>null</tt> when the stop ended in time, otherwise why the consumer gave up waiting
         */
        String await(long timeout) {
            boolean interrupted = false;
            lock.lock();
            try {
                long nanos = TimeUnit.MILLISECONDS.toNanos(timeout);
                while (!done && nanos > 0) {
                    try {
                        nanos = ended.awaitNanos(nanos);
                    } catch (InterruptedException e) {
                        // such as the graceful shutdown when its timeout elapses: stop waiting
                        interrupted = true;
                        break;
                    }
                }
                if (done) {
                    if (failure instanceof Error error) {
                        throw error;
                    } else if (failure != null) {
                        throw (RuntimeException) failure;
                    }
                    return null;
                }
                givenUp = true;
                if (stopped == null) {
                    // the suspend did not end in time: this consumer no longer uses the listener container
                    suspended = detachSuspendedListenerContainer(container);
                }
                return interrupted
                        ? "was interrupted before its listener container stopped"
                        : "did not end within " + timeout + " millis, as its listener container did not stop";
            } finally {
                lock.unlock();
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        /**
         * The listener container that this consumer gave up waiting for, which is destroyed in the background.
         */
        DetachedListenerContainer givenUpContainer() {
            lock.lock();
            try {
                if (!givenUp) {
                    return null;
                }
                return stopped != null ? stopped : suspended;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public String toString() {
            return (stopped != null ? "StopListenerTask[" : "SuspendListenerTask[") + getDestinationName(container) + "]";
        }
    }

}
