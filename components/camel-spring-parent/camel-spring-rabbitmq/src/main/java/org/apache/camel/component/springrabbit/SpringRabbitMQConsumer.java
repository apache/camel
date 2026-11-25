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
package org.apache.camel.component.springrabbit;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Endpoint;
import org.apache.camel.FailedToCreateConsumerException;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Suspendable;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionListener;
import org.springframework.amqp.rabbit.connection.RabbitUtils;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;

public class SpringRabbitMQConsumer extends DefaultConsumer implements Suspendable {

    private static final Logger LOG = LoggerFactory.getLogger(SpringRabbitMQConsumer.class);

    private volatile AbstractMessageListenerContainer listenerContainer;
    private volatile EndpointMessageListener messageListener;
    private volatile boolean initialized;
    private volatile DeclaringConnectionListener declaringConnectionListener;
    // the queues of the listener container that a suspend left to stop in the background: the next listener container
    // consumes them, as only that listener container knows the name of a queue that it generated
    private volatile String[] detachedQueueNames;

    public SpringRabbitMQConsumer(Endpoint endpoint, Processor processor, AbstractMessageListenerContainer listenerContainer) {
        super(endpoint, processor);
        this.listenerContainer = listenerContainer;
        this.listenerContainer.setMessageListener(getEndpointMessageListener());
    }

    @Override
    public SpringRabbitMQEndpoint getEndpoint() {
        return (SpringRabbitMQEndpoint) super.getEndpoint();
    }

    public EndpointMessageListener getEndpointMessageListener() {
        if (messageListener == null) {
            createMessageListener(getEndpoint(), getProcessor());
        }
        return messageListener;
    }

    protected void createMessageListener(SpringRabbitMQEndpoint endpoint, Processor processor) {
        messageListener = new EndpointMessageListener(this, endpoint, processor);
        endpoint.configureMessageListener(messageListener);
    }

    /**
     * Starts the listener container
     * <p/>
     * Can be used to start this consumer later if it was configured to not auto startup.
     */
    public void startListenerContainer() {
        LOG.trace("Starting listener container {} on queues: {}", listenerContainer, getEndpoint().getQueues());
        listenerContainer.start();
        LOG.debug("Started listener container {} on queues: {}", listenerContainer, getEndpoint().getQueues());
    }

    /**
     * Pre tests the connection before starting the listening.
     * <p/>
     * In case of connection failure the exception is thrown which prevents Camel from starting.
     *
     * @throws FailedToCreateConsumerException is thrown if testing the connection failed
     */
    protected void testConnectionOnStartup() throws FailedToCreateConsumerException {
        Connection conn = null;
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Testing RabbitMQ Connection on startup for: {}", getEndpoint().getConnectionFactory().getHost());
            }
            conn = listenerContainer.getConnectionFactory().createConnection();

            LOG.debug("Successfully tested RabbitMQ Connection on startup for: {}",
                    getEndpoint().getConnectionFactory().getHost());
        } catch (Exception e) {
            throw new FailedToCreateConsumerException(getEndpoint(), e);
        } finally {
            RabbitUtils.closeConnection(conn);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // create listener container
        if (listenerContainer == null) {
            createMessageListenerContainer();
        }
        doPrepareAndStartListenerContainer();

        // mark as initialized for the first time
        initialized = true;
    }

    @Override
    protected void doStop() throws Exception {
        removeDeclaringConnectionListener();
        // also without a listener container (a suspend that did not wait for it to stop), so that a start creates
        // everything again
        stopAndDestroyListenerContainer();

        super.doStop();
    }

    @Override
    protected void doSuspend() throws Exception {
        AbstractMessageListenerContainer container = listenerContainer;
        if (container != null && !stopListenerContainer(container, false)) {
            // the listener container stops in the background: resume creates a new one, which that stop cannot touch
            detachedQueueNames = container.getQueueNames();
            listenerContainer = null;
        }
    }

    @Override
    protected void doResume() throws Exception {
        // we may not have been started before, and now the end user calls resume, so lets handle that and start it first
        if (!initialized) {
            doStart();
        } else if (listenerContainer != null) {
            startListenerContainer();
        } else {
            // the suspend did not wait for the listener container to stop, which goes on in the background
            LOG.debug("Creating a new listener container for {}, as the previous one did not stop in time when the consumer"
                      + " was suspended",
                    getEndpoint());
            // it declares with the previous listener container, also when the new one cannot be created
            removeDeclaringConnectionListener();
            createMessageListenerContainer();
            doPrepareAndStartListenerContainer();
        }
    }

    /**
     * Prepares and starts the listener container. The connection listener that declares on new connections replaces the
     * one of a previous preparation (a suspended consumer that is started, or resumed after its listener container did
     * not stop in time), and is removed when the start fails.
     */
    private void doPrepareAndStartListenerContainer() {
        removeDeclaringConnectionListener();
        try {
            prepareAndStartListenerContainer();
        } catch (Exception e) {
            removeDeclaringConnectionListener();
            throw e;
        }
    }

    protected void createMessageListenerContainer() {
        listenerContainer = getEndpoint().createMessageListenerContainer();
        listenerContainer.setMessageListener(getEndpointMessageListener());
        String[] queueNames = detachedQueueNames;
        detachedQueueNames = null;
        if (queueNames != null && listenerContainer.getQueueNames().length == 0) {
            listenerContainer.setQueueNames(queueNames);
        }
    }

    protected void prepareAndStartListenerContainer() {
        listenerContainer.afterPropertiesSet();

        if (getEndpoint().isAutoDeclare()) {
            // auto declare but without spring
            autoDeclareElements();
        }

        // only start listener if auto start is enabled or we are explicit invoking start later
        if (initialized || getEndpoint().isAutoStartup()) {
            // should we pre test connections before starting?
            if (getEndpoint().isTestConnectionOnStartup()) {
                testConnectionOnStartup();
            }
            startListenerContainer();
        }
    }

    /**
     * Declares the exchange, queue and binding of the endpoint now, and again on every new connection of the connection
     * factory while this consumer runs, as Spring's {@code RabbitAdmin} does for its declarables: a queue that the
     * broker deleted when the connection was lost (auto-delete, exclusive, or not durable and the broker restarted) is
     * declared again when the listener container reconnects.
     * <p/>
     * When the broker is not available, the consumer starts anyway, unless {@code testConnectionOnStartup} is enabled:
     * the listener container keeps trying to connect, and the elements are declared when it does.
     */
    private void autoDeclareElements() {
        ConnectionFactory connectionFactory = listenerContainer.getConnectionFactory();
        DeclaringConnectionListener listener = null;
        if (connectionFactory instanceof CachingConnectionFactory ccf
                && ccf.getCacheMode() == CachingConnectionFactory.CacheMode.CONNECTION) {
            // as RabbitAdmin, which does not declare on new connections with this cache mode
            LOG.debug("The connection factory caches connections: {} is declared when the consumer starts only",
                    getEndpoint());
        } else {
            listener = new DeclaringConnectionListener(listenerContainer);
            declaringConnectionListener = listener;
            // a connection factory that is already connected may call the listener now: it leaves that to this method
            connectionFactory.addConnectionListener(listener);
        }
        try {
            getEndpoint().declareElements(listenerContainer);
        } catch (AmqpConnectException e) {
            if (listener == null || getEndpoint().isTestConnectionOnStartup()) {
                throw e;
            }
            LOG.warn("Cannot declare the exchange, queue and binding of {} because the broker is not available: {}."
                     + " They are declared when the listener container connects.",
                    getEndpoint(), e.getMessage());
        } finally {
            if (listener != null) {
                listener.started();
            }
        }
    }

    private void removeDeclaringConnectionListener() {
        DeclaringConnectionListener listener = declaringConnectionListener;
        if (listener != null) {
            listener.connectionFactory().removeConnectionListener(listener);
            declaringConnectionListener = null;
        }
    }

    protected void stopAndDestroyListenerContainer() {
        AbstractMessageListenerContainer container = listenerContainer;
        // null container and listener so they are fully re created if this consumer is restarted
        // then we will use updated configuration from the endpoint that may have been managed using JMX
        listenerContainer = null;
        messageListener = null;
        initialized = false;
        detachedQueueNames = null;

        if (container != null) {
            stopListenerContainer(container, true);
        }
    }

    /**
     * Stops the listener container (and destroys it when asked), waiting for it at most the time to wait for a thread
     * pool to shut down ({@code ExecutorServiceManager.getShutdownAwaitTermination()}, 10 seconds by default).
     * <p/>
     * The stop of a listener container cancels its consumers (basic.cancel) and waits for the broker to answer, without
     * reacting to an interrupt: on a connection that stopped answering, up to the {@code channelRpcTimeout} of the
     * RabbitMQ client (10 minutes by default) or until the heartbeat closes the connection. So the stop runs on a
     * thread of the component: when the bound elapses, or the waiting thread is interrupted, it goes on in the
     * background, and destroys the listener container once it returns.
     *
     * @param  container the listener container, which the caller detaches from this consumer when it stops in the
     *                   background
     * @param  destroy   whether to destroy the listener container once it stopped
     * @return           {@code true} if the listener container stopped in time, {@code false} if it stops in the
     *                   background
     */
    private boolean stopListenerContainer(AbstractMessageListenerContainer container, boolean destroy) {
        long timeout = getEndpoint().getCamelContext().getExecutorServiceManager().getShutdownAwaitTermination();
        // whether this thread still waits for the stop: the first of the two that sets it to false (this thread when it
        // stops waiting, or the stop when it ends) completes the stop
        AtomicBoolean waiting = new AtomicBoolean(true);
        CompletableFuture<Void> stop;
        try {
            stop = CompletableFuture.runAsync(() -> doStopListenerContainer(container, destroy, waiting),
                    getEndpoint().getComponent().getStopListenerContainerExecutorService());
        } catch (RejectedExecutionException e) {
            // the component stopped meanwhile: as before, on this thread
            stopAndDestroy(container, destroy);
            return true;
        }

        try {
            stop.get(timeout, TimeUnit.MILLISECONDS);
            return true;
        } catch (ExecutionException e) {
            throw stopFailure(e.getCause());
        } catch (TimeoutException e) {
            if (waiting.compareAndSet(true, false)) {
                LOG.warn("The listener container of {} (queues: {}) did not stop within {} millis (the"
                         + " shutdownAwaitTermination of the ExecutorServiceManager): it may wait for the broker to answer"
                         + " the cancel of its consumers, on a connection that stopped answering. It stops in the"
                         + " background, and the consumer creates a new listener container when it is resumed or started"
                         + " again.",
                        getEndpoint(), Arrays.toString(container.getQueueNames()), timeout);
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (waiting.compareAndSet(true, false)) {
                LOG.warn("Interrupted while waiting for the listener container of {} (queues: {}) to stop. It stops in"
                         + " the background, and the consumer creates a new listener container when it is resumed or"
                         + " started again.",
                        getEndpoint(), Arrays.toString(container.getQueueNames()));
                return false;
            }
        }
        // the stop ended meanwhile
        try {
            stop.join();
        } catch (CompletionException e) {
            throw stopFailure(e.getCause());
        }
        return true;
    }

    /**
     * The stop that runs on a thread of the component. When nobody waits for it anymore, it destroys the listener
     * container and logs the outcome by itself, also when the stop fails with an error.
     */
    private void doStopListenerContainer(
            AbstractMessageListenerContainer container, boolean destroy, AtomicBoolean waiting) {
        Throwable failure = null;
        try {
            stopAndDestroy(container, destroy);
        } catch (RuntimeException | Error e) {
            failure = e;
        }
        if (waiting.compareAndSet(true, false)) {
            // the consumer waits for the stop: it gets the failure unchanged, as from a stop on its own thread
            if (failure instanceof RuntimeException e) {
                throw e;
            } else if (failure instanceof Error e) {
                throw e;
            }
            return;
        }

        String queues = Arrays.toString(container.getQueueNames());
        if (failure != null) {
            LOG.warn("Error stopping the listener container of {} (queues: {}) in the background. This exception is"
                     + " ignored.",
                    getEndpoint(), queues, failure);
        }
        if (!destroy) {
            try {
                container.destroy();
            } catch (RuntimeException | Error e) {
                LOG.warn("Error destroying the listener container of {} (queues: {}) in the background. This exception"
                         + " is ignored.",
                        getEndpoint(), queues, e);
            }
        }
        if (failure == null) {
            LOG.info("The listener container of {} (queues: {}) stopped in the background", getEndpoint(), queues);
        }
    }

    private static void stopAndDestroy(AbstractMessageListenerContainer container, boolean destroy) {
        try {
            container.stop();
        } catch (RuntimeException | Error e) {
            if (destroy) {
                try {
                    container.destroy();
                } catch (RuntimeException | Error destroyFailure) {
                    e.addSuppressed(destroyFailure);
                }
            }
            throw e;
        }
        if (destroy) {
            container.destroy();
        }
    }

    private static RuntimeException stopFailure(Throwable cause) {
        if (cause instanceof Error error) {
            throw error;
        }
        return RuntimeCamelException.wrapRuntimeException(cause);
    }

    /**
     * Declares the exchange, queue and binding of the endpoint on every new connection.
     */
    private final class DeclaringConnectionListener implements ConnectionListener {

        private final AbstractMessageListenerContainer container;
        private final AtomicBoolean declaring = new AtomicBoolean();
        // the thread that starts the consumer declares by itself, also when it creates the connection meanwhile
        private volatile Thread startingThread = Thread.currentThread();

        private DeclaringConnectionListener(AbstractMessageListenerContainer container) {
            this.container = container;
        }

        private ConnectionFactory connectionFactory() {
            return container.getConnectionFactory();
        }

        private void started() {
            startingThread = null;
        }

        @Override
        public void onCreate(Connection connection) {
            // skip a connection that the declarations create themselves, when the one they use is lost meanwhile: they
            // go on with the new one (as RabbitAdmin, which also skips it)
            if (Thread.currentThread() == startingThread || !declaring.compareAndSet(false, true)) {
                return;
            }
            try {
                LOG.debug("Declaring the exchange, queue and binding of {} on a new connection", getEndpoint());
                getEndpoint().declareElements(container);
            } catch (Exception e) {
                // the connection may be shared with other consumers and producers: do not fail it
                LOG.warn("Cannot declare the exchange, queue and binding of {} on a new connection. This exception is ignored.",
                        getEndpoint(), e);
            } finally {
                declaring.set(false);
            }
        }
    }

}
