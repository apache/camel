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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import javax.jms.Connection;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;

import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.component.sjms.consumer.AbstractMessageHandler;
import org.apache.camel.component.sjms.consumer.InOnlyMessageHandler;
import org.apache.camel.component.sjms.consumer.InOutMessageHandler;
import org.apache.camel.component.sjms.jms.ConnectionResource;
import org.apache.camel.component.sjms.taskmanager.TimedTaskManager;
import org.apache.camel.component.sjms.tx.BatchTransactionCommitStrategy;
import org.apache.camel.component.sjms.tx.DefaultTransactionCommitStrategy;
import org.apache.camel.component.sjms.tx.SessionBatchTransactionSynchronization;
import org.apache.camel.component.sjms.tx.SessionTransactionSynchronization;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.util.backoff.BackOff;
import org.apache.camel.util.backoff.BackOffTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The SjmsConsumer is the base class for the SJMS MessageListener pool.
 */
public class SjmsConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(SjmsConsumer.class);

    private final Map<Connection, List<MessageConsumerResources>> consumers = new WeakHashMap<>();
    private ScheduledExecutorService scheduler;
    private Future<?> asyncStart;
    private BackOffTimer.Task rescheduleTask;

    public SjmsConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public SjmsEndpoint getEndpoint() {
        return (SjmsEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        this.scheduler = getEndpoint().getCamelContext().getExecutorServiceManager().newDefaultScheduledThreadPool(this, "SjmsConsumer");
        if (getEndpoint().isAsyncStartListener()) {
            asyncStart = getEndpoint().getComponent().getAsyncStartStopExecutorService().submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        fillConsumersPool();
                    } catch (Throwable e) {
                        LOG.warn("Error starting listener container on destination: " + getDestinationName() + ". This exception will be ignored.", e);
                        if (getEndpoint().isReconnectOnError()) {
                            scheduleRefill(); //we should try to fill consumer pool on next time
                        }
                    }
                }

                @Override
                public String toString() {
                    return "AsyncStartListenerTask[" + getDestinationName() + "]";
                }
            });
        } else {
            fillConsumersPool();
        }
    }

    private void fillConsumersPool() throws Exception {
        synchronized (consumers) {
            while (consumers.values().stream().collect(Collectors.summarizingInt(List::size)).getSum() < getConsumerCount()) {
                addConsumer();
            }
        }
    }

    public void destroyObject(MessageConsumerResources model) {
        try {
            if (model.getMessageConsumer() != null) {
                model.getMessageConsumer().close();
            }

            // If the resource has a
            if (model.getSession() != null) {
                if (model.getSession().getTransacted()) {
                    try {
                        model.getSession().rollback();
                    } catch (Exception e) {
                        // Do nothing. Just make sure we are cleaned up
                    }
                }
                model.getSession().close();
            }
        } catch (JMSException ex) {
            LOG.warn("Exception caught on closing consumer", ex);
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (asyncStart != null && !asyncStart.isDone()) {
            asyncStart.cancel(true);
        }
        if (rescheduleTask != null) {
            rescheduleTask.cancel();
        }
        if (getEndpoint().isAsyncStopListener()) {
            getEndpoint().getComponent().getAsyncStartStopExecutorService().submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        synchronized (consumers) {
                            consumers.values().stream().flatMap(Collection::stream).forEach(SjmsConsumer.this::destroyObject);
                            consumers.clear();
                        }
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
            synchronized (consumers) {
                consumers.values().stream().flatMap(Collection::stream).forEach(SjmsConsumer.this::destroyObject);
                consumers.clear();
            }
        }
        if (this.scheduler != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownGraceful(this.scheduler);
        }
    }

    /**
     * Creates a {@link MessageConsumerResources} with a dedicated
     * {@link Session} required for transacted and InOut consumers.
     */
    private void addConsumer() throws Exception {
        MessageConsumerResources answer;
        ConnectionResource connectionResource = getOrCreateConnectionResource();
        Connection conn = connectionResource.borrowConnection();
        try {
            Session session = conn.createSession(isTransacted(), isTransacted() ? Session.SESSION_TRANSACTED : Session.AUTO_ACKNOWLEDGE);
            MessageConsumer messageConsumer = getEndpoint().getJmsObjectFactory().createMessageConsumer(session, getEndpoint());
            MessageListener handler = createMessageHandler(session);
            messageConsumer.setMessageListener(handler);

            if (getEndpoint().isReconnectOnError()) {
                ExceptionListener exceptionListener = conn.getExceptionListener();
                ReconnectExceptionListener reconnectExceptionListener = new ReconnectExceptionListener(conn);
                if (exceptionListener == null) {
                    exceptionListener = reconnectExceptionListener;
                } else {
                    exceptionListener = new AggregatedExceptionListener(exceptionListener, reconnectExceptionListener);
                }
                conn.setExceptionListener(exceptionListener);
            }
            answer = new MessageConsumerResources(session, messageConsumer);
            consumers.compute(conn, (key, oldValue) -> {
                if (oldValue == null) {
                    oldValue = new ArrayList<>();
                }
                oldValue.add(answer);
                return oldValue;
            });
        } catch (Exception e) {
            LOG.error("Unable to create the MessageConsumer", e);
            throw e;
        } finally {
            connectionResource.returnConnection(conn);
        }
    }

    /**
     * Helper factory method used to create a MessageListener based on the MEP
     *
     * @param session a session is only required if we are a transacted consumer
     * @return the listener
     */
    protected MessageListener createMessageHandler(Session session) {

        TransactionCommitStrategy commitStrategy;
        if (getTransactionCommitStrategy() != null) {
            commitStrategy = getTransactionCommitStrategy();
        } else if (getTransactionBatchCount() > 0) {
            commitStrategy = new BatchTransactionCommitStrategy(getTransactionBatchCount());
        } else {
            commitStrategy = new DefaultTransactionCommitStrategy();
        }

        Synchronization synchronization;
        if (commitStrategy instanceof BatchTransactionCommitStrategy) {
            TimedTaskManager timedTaskManager = getEndpoint().getComponent().getTimedTaskManager();
            synchronization = new SessionBatchTransactionSynchronization(timedTaskManager, session, commitStrategy, getTransactionBatchTimeout());
        } else {
            synchronization = new SessionTransactionSynchronization(session, commitStrategy);
        }

        AbstractMessageHandler messageHandler;
        if (getEndpoint().getExchangePattern().equals(ExchangePattern.InOnly)) {
            if (isTransacted() || isSynchronous()) {
                messageHandler = new InOnlyMessageHandler(getEndpoint(), scheduler, synchronization);
            } else {
                messageHandler = new InOnlyMessageHandler(getEndpoint(), scheduler);
            }
        } else {
            if (isTransacted() || isSynchronous()) {
                messageHandler = new InOutMessageHandler(getEndpoint(), scheduler, synchronization);
            } else {
                messageHandler = new InOutMessageHandler(getEndpoint(), scheduler);
            }
        }

        messageHandler.setSession(session);
        messageHandler.setProcessor(getAsyncProcessor());
        messageHandler.setSynchronous(isSynchronous());
        messageHandler.setTransacted(isTransacted());
        messageHandler.setSharedJMSSession(isSharedJMSSession());
        messageHandler.setTopic(isTopic());
        return messageHandler;
    }

    /**
     * @deprecated use {@link #getOrCreateConnectionResource()}
     */
    @Deprecated
    protected ConnectionResource getConnectionResource() {
        return getEndpoint().getConnectionResource();
    }

    protected ConnectionResource getOrCreateConnectionResource() {
        ConnectionResource answer = getEndpoint().getConnectionResource();
        if (answer == null) {
            answer = getEndpoint().createConnectionResource(this);
        }
        return answer;
    }

    public int getAcknowledgementMode() {
        return getEndpoint().getAcknowledgementMode().intValue();
    }

    /**
     * Use to determine if transactions are enabled or disabled.
     *
     * @return true if transacted, otherwise false
     */
    public boolean isTransacted() {
        return getEndpoint().isTransacted();
    }

    /**
     * Use to determine if JMS session should be propagated to share with other SJMS endpoints.
     *
     * @return true if shared, otherwise false
     */
    public boolean isSharedJMSSession() {
        return getEndpoint().isSharedJMSSession();
    }

    /**
     * Use to determine whether or not to process exchanges synchronously.
     *
     * @return true if synchronous
     */
    public boolean isSynchronous() {
        return getEndpoint().isSynchronous();
    }

    /**
     * The destination name for this consumer.
     *
     * @return String
     */
    public String getDestinationName() {
        return getEndpoint().getDestinationName();
    }

    /**
     * Returns the number of consumer listeners.
     *
     * @return the consumerCount
     */
    public int getConsumerCount() {
        return getEndpoint().getConsumerCount();
    }

    /**
     * Flag set by the endpoint used by consumers and producers to determine if
     * the consumer is a JMS Topic.
     *
     * @return the topic true if consumer is a JMS Topic, default is false
     */
    public boolean isTopic() {
        return getEndpoint().isTopic();
    }

    /**
     * Gets the JMS Message selector syntax.
     */
    public String getMessageSelector() {
        return getEndpoint().getMessageSelector();
    }

    /**
     * Gets the durable subscription Id.
     *
     * @return the durableSubscriptionId
     */
    public String getDurableSubscriptionId() {
        return getEndpoint().getDurableSubscriptionId();
    }

    /**
     * Gets the commit strategy.
     *
     * @return the transactionCommitStrategy
     */
    public TransactionCommitStrategy getTransactionCommitStrategy() {
        return getEndpoint().getTransactionCommitStrategy();
    }

    /**
     * If transacted, returns the nubmer of messages to be processed before
     * committing the transaction.
     *
     * @return the transactionBatchCount
     */
    public int getTransactionBatchCount() {
        return getEndpoint().getTransactionBatchCount();
    }

    /**
     * Returns the timeout value for batch transactions.
     *
     * @return long
     */
    public long getTransactionBatchTimeout() {
        return getEndpoint().getTransactionBatchTimeout();
    }

    private boolean refillPool(BackOffTimer.Task task) {
        LOG.debug("Refill consumers pool task running");
        try {
            fillConsumersPool();
            LOG.info("Refill consumers pool completed (attempt: {})", task.getCurrentAttempts());
            return false;
        } catch (Exception ex) {
            LOG.warn("Refill consumers pool failed (attempt: {}) due to: {}. Will try again in {} millis. (stacktrace in DEBUG level)",
                    task.getCurrentAttempts(), ex.getMessage(), task.getCurrentDelay());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Refill consumers pool failed", ex);
            }
        }
        return true;
    }

    private void scheduleRefill() {
        if (rescheduleTask == null || rescheduleTask.getStatus() != BackOffTimer.Task.Status.Active) {
            BackOff backOff = BackOff.builder().delay(getEndpoint().getReconnectBackOff()).build();
            rescheduleTask = new BackOffTimer(scheduler).schedule(backOff, this::refillPool);
        }
    }

    private final class ReconnectExceptionListener implements ExceptionListener {
        private final WeakReference<Connection> connection;

        private ReconnectExceptionListener(Connection connection) {
            this.connection = new WeakReference<>(connection);
        }

        @Override
        public void onException(JMSException exception) {
            LOG.debug("Handling JMSException for reconnecting", exception);
            Connection currentConnection = connection.get();
            if (currentConnection != null) {
                synchronized (consumers) {
                    List<MessageConsumerResources> toClose = consumers.get(currentConnection);
                    if (toClose != null) {
                        toClose.forEach(SjmsConsumer.this::destroyObject);
                    }
                    consumers.remove(currentConnection);
                }
                scheduleRefill();
            }
        }

        //hash and equals to prevent multiple instances for same connection
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ReconnectExceptionListener that = (ReconnectExceptionListener) o;
            return Objects.equals(connection.get(), that.connection.get());
        }

        @Override
        public int hashCode() {
            final Connection currentConnection = this.connection.get();
            return currentConnection == null ? 0 : currentConnection.hashCode();
        }
    }
}
