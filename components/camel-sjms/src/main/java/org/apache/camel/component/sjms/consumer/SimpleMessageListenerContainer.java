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

package org.apache.camel.component.sjms.consumer;

import static org.apache.camel.component.sjms.SjmsHelper.*;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.ExceptionListener;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.Session;

import org.apache.camel.CamelContext;
import org.apache.camel.component.sjms.SessionMessageListener;
import org.apache.camel.component.sjms.SjmsEndpoint;
import org.apache.camel.component.sjms.jms.DestinationCreationStrategy;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.support.task.BackgroundTask;
import org.apache.camel.support.task.TaskRunFailureException;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleMessageListenerContainer extends ServiceSupport
        implements org.apache.camel.component.sjms.MessageListenerContainer, ExceptionListener {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleMessageListenerContainer.class);

    private final SjmsEndpoint endpoint;
    private SessionMessageListener messageListener;
    private String clientId;
    private int concurrentConsumers = 1;
    private ExceptionListener exceptionListener;
    private String destinationName;
    private DestinationCreationStrategy destinationCreationStrategy;

    private final Lock connectionLock = new ReentrantLock();
    private Connection connection;
    private volatile boolean connectionStarted;
    private final Lock consumerLock = new ReentrantLock();
    private Set<MessageConsumer> consumers;
    private Set<Session> sessions;
    private ScheduledExecutorService recoverPool;
    private BackgroundTask recoverTask;
    private Future<?> recoverFuture;

    public SimpleMessageListenerContainer(SjmsEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public SjmsEndpoint getEndpoint() {
        return endpoint;
    }

    public void setMessageListener(SessionMessageListener messageListener) {
        this.messageListener = messageListener;
    }

    public void setExceptionListener(ExceptionListener exceptionListener) {
        this.exceptionListener = exceptionListener;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public void setDestinationName(String destinationName) {
        this.destinationName = destinationName;
    }

    public DestinationCreationStrategy getDestinationCreationStrategy() {
        return destinationCreationStrategy;
    }

    public void setDestinationCreationStrategy(DestinationCreationStrategy destinationCreationStrategy) {
        this.destinationCreationStrategy = destinationCreationStrategy;
    }

    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    public void setConcurrentConsumers(int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    @Override
    public void afterPropertiesConfigured(CamelContext camelContext) {
        // noop
    }

    @Override
    public ConnectionFactory getConnectionFactory() {
        return endpoint.getConnectionFactory();
    }

    protected void configureConsumer(MessageConsumer consumer, Session session) throws Exception {
        consumer.setMessageListener(new SimpleMessageListener(messageListener, session));
    }

    private class SimpleMessageListener implements MessageListener {

        private final SessionMessageListener messageListener;
        private final Session session;

        public SimpleMessageListener(SessionMessageListener messageListener, Session session) {
            this.messageListener = messageListener;
            this.session = session;
        }

        @Override
        public void onMessage(Message message) {
            try {
                doOnMessage(message);
            } catch (Exception e) {
                if (e instanceof JMSException) {
                    if (endpoint.getExceptionListener() != null) {
                        endpoint.getExceptionListener().onException((JMSException) e);
                    }
                } else {
                    LOG.warn("Execution of JMS message listener failed. This exception is ignored.", e);
                }
            }
        }

        protected void doOnMessage(Message message) throws Exception {
            try {
                messageListener.onMessage(message, session);
            } catch (Exception e) {
                // unexpected error so rollback
                rollbackIfNeeded(session);
                throw e;
            }
            // success then commit if we need to
            commitIfNeeded(session, message);
        }
    }

    @Override
    public void onException(JMSException exception) {
        if (exceptionListener != null) {
            try {
                exceptionListener.onException(exception);
            } catch (Exception e) {
                // ignore
            }
        }
        if (endpoint.getExceptionListener() != null) {
            try {
                endpoint.getExceptionListener().onException(exception);
            } catch (Exception e) {
                // ignore
            }
        }

        connectionLock.lock();
        try {
            this.sessions = null;
            this.consumers = null;
        } finally {
            connectionLock.unlock();
        }
        scheduleConnectionRecovery();
    }

    protected boolean recoverConnection(BackgroundTask task) {
        LOG.debug("Recovering from JMS Connection exception (attempt: {})", task.iteration());
        try {
            refreshConnection();
            initConsumers();
            LOG.debug("Successfully recovered JMS Connection (attempt: {})", task.iteration());
            // success so do not try again
            return false;
        } catch (Exception e) {
            String message = "Failed to recover JMS Connection (attempt: " + task.iteration() + "). Will try again in "
                    + endpoint.getRecoveryInterval() + " millis";
            LOG.warn(message);
            // make the task runner aware of the exception (will retry)
            throw new TaskRunFailureException(message, e);
        }
    }

    protected void scheduleConnectionRecovery() {
        connectionLock.lock();
        try {
            if (recoverPool == null) {
                recoverPool = endpoint.getCamelContext()
                        .getExecutorServiceManager()
                        .newSingleThreadScheduledExecutor(this, "SjmsConnectionRecovery");
            }
            if (recoverTask == null) {
                recoverTask = createTask();
                recoverFuture = recoverTask.schedule(endpoint.getCamelContext(), () -> recoverConnection(recoverTask));
            }
        } finally {
            connectionLock.unlock();
        }
    }

    private BackgroundTask createTask() {
        return Tasks.backgroundTask()
                .withScheduledExecutor(recoverPool)
                .withBudget(Budgets.iterationTimeBudget()
                        .withInterval(Duration.ofMillis(endpoint.getRecoveryInterval()))
                        .withInitialDelay(Duration.ofSeconds(1))
                        .withUnlimitedDuration()
                        .build())
                .withName("SjmsConnectionRecovery")
                .build();
    }

    @Override
    protected void doStart() throws Exception {
        createConnection();
        initConsumers();

        startConnection();
    }

    @Override
    protected void doStop() throws Exception {
        stopConnection();
        stopConsumers();
        if (recoverPool != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdown(recoverPool);
            recoverPool = null;
        }
        if (recoverFuture != null && recoverTask != null && recoverTask.isRunning()) {
            recoverFuture.cancel(true);
            recoverTask = null;
            recoverFuture = null;
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        closeConnection(connection);
        this.connection = null;
    }

    protected void initConsumers() throws Exception {
        consumerLock.lock();
        try {
            if (consumers == null) {
                LOG.debug(
                        "Initializing {} concurrent consumers as JMS listener on destination: {}",
                        concurrentConsumers,
                        destinationName);
                sessions = new HashSet<>(concurrentConsumers);
                consumers = new HashSet<>(concurrentConsumers);
                for (int i = 0; i < this.concurrentConsumers; i++) {
                    Session session = createSession(connection, endpoint);
                    MessageConsumer consumer = createMessageConsumer(session);
                    configureConsumer(consumer, session);
                    sessions.add(session);
                    consumers.add(consumer);
                }
            }
        } finally {
            consumerLock.unlock();
        }
    }

    protected Session createSession(Connection connection, SjmsEndpoint endpoint) throws Exception {
        return connection.createSession(
                endpoint.isTransacted(), endpoint.getAcknowledgementMode().intValue());
    }

    protected MessageConsumer createMessageConsumer(Session session) throws Exception {
        return endpoint.getJmsObjectFactory().createMessageConsumer(session, endpoint);
    }

    protected void stopConsumers() {
        consumerLock.lock();
        try {
            if (consumers != null) {
                LOG.debug("Stopping JMS MessageConsumers");
                for (MessageConsumer consumer : this.consumers) {
                    closeConsumer(consumer);
                }
                if (this.sessions != null) {
                    LOG.debug("Stopping JMS Sessions");
                    for (Session session : this.sessions) {
                        closeSession(session);
                    }
                }
            }
        } finally {
            consumerLock.unlock();
        }
    }

    protected void createConnection() throws Exception {
        connectionLock.lock();
        try {
            if (this.connection == null) {
                Connection con = null;
                try {
                    con = endpoint.getConnectionFactory().createConnection();
                    String cid = clientId != null ? clientId : endpoint.getClientId();
                    if (cid != null) {
                        con.setClientID(cid);
                    }
                    con.setExceptionListener(this);
                } catch (JMSException e) {
                    closeConnection(con);
                    throw e;
                }
                this.connection = con;
                LOG.debug("Created JMS Connection");
            }
        } finally {
            connectionLock.unlock();
        }
    }

    protected final void refreshConnection() throws Exception {
        connectionLock.lock();
        try {
            closeConnection(connection);
            this.connection = null;
            createConnection();
            if (this.connectionStarted) {
                startConnection();
            }
        } finally {
            connectionLock.unlock();
        }
    }

    protected void startConnection() throws Exception {
        connectionLock.lock();
        try {
            this.connectionStarted = true;
            if (this.connection != null) {
                try {
                    this.connection.start();
                } catch (jakarta.jms.IllegalStateException e) {
                    // ignore as it may already be started
                }
            }
        } finally {
            connectionLock.unlock();
        }
    }

    protected void stopConnection() {
        connectionLock.lock();
        try {
            this.connectionStarted = false;
            if (this.connection != null) {
                try {
                    this.connection.stop();
                } catch (Exception e) {
                    LOG.debug("Error stopping connection. This exception is ignored.", e);
                }
            }
        } finally {
            connectionLock.unlock();
        }
    }
}
