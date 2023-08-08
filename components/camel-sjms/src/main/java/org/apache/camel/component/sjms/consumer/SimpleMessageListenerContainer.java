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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

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
import org.apache.camel.util.backoff.BackOff;
import org.apache.camel.util.backoff.BackOffTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.sjms.SjmsHelper.*;

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

    private final Object connectionLock = new Object();
    private Connection connection;
    private volatile boolean connectionStarted;
    private final Object consumerLock = new Object();
    private Set<MessageConsumer> consumers;
    private Set<Session> sessions;
    private BackOffTimer.Task recoverTask;
    private ScheduledExecutorService scheduler;

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

        synchronized (this.connectionLock) {
            this.sessions = null;
            this.consumers = null;
        }
        scheduleConnectionRecovery();
    }

    protected boolean recoverConnection(BackOffTimer.Task task) throws Exception {
        LOG.debug("Recovering from JMS Connection exception (attempt: {})", task.getCurrentAttempts());
        try {
            refreshConnection();
            initConsumers();
            LOG.debug("Successfully recovered JMS Connection (attempt: {})", task.getCurrentAttempts());
            // success so do not try again
            return false;
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Failed to recover JMS Connection. Will try again in {} millis", task.getCurrentDelay(), e);
            }
            // try again
            return true;
        }
    }

    protected void scheduleConnectionRecovery() {
        if (scheduler == null) {
            this.scheduler = endpoint.getCamelContext().getExecutorServiceManager().newSingleThreadScheduledExecutor(this,
                    "SimpleMessageListenerContainer");
        }

        // we need to recover using a background task
        if (recoverTask == null || recoverTask.getStatus() != BackOffTimer.Task.Status.Active) {
            BackOff backOff = BackOff.builder().delay(endpoint.getRecoveryInterval()).build();
            recoverTask = new BackOffTimer(scheduler).schedule(backOff, this::recoverConnection);
        }
    }

    @Override
    protected void doStart() throws Exception {
        createConnection();
        initConsumers();

        startConnection();
    }

    @Override
    protected void doStop() throws Exception {
        if (recoverTask != null) {
            recoverTask.cancel();
        }
        stopConnection();
        stopConsumers();
        if (scheduler != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdown(scheduler);
            scheduler = null;
        }
    }

    protected void initConsumers() throws Exception {
        synchronized (this.consumerLock) {
            if (consumers == null) {
                LOG.debug("Initializing {} concurrent consumers as JMS listener on destination: {}", concurrentConsumers,
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
        }
    }

    protected Session createSession(Connection connection, SjmsEndpoint endpoint) throws Exception {
        return connection.createSession(endpoint.isTransacted(), endpoint.getAcknowledgementMode().intValue());
    }

    protected MessageConsumer createMessageConsumer(Session session) throws Exception {
        return endpoint.getJmsObjectFactory().createMessageConsumer(session, endpoint);
    }

    protected void stopConsumers() {
        synchronized (this.consumerLock) {
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
        }
    }

    protected void createConnection() throws Exception {
        synchronized (this.connectionLock) {
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
        }
    }

    protected final void refreshConnection() throws Exception {
        synchronized (this.connectionLock) {
            closeConnection(connection);
            this.connection = null;
            createConnection();
            if (this.connectionStarted) {
                startConnection();
            }
        }
    }

    protected void startConnection() throws Exception {
        synchronized (this.connectionLock) {
            this.connectionStarted = true;
            if (this.connection != null) {
                try {
                    this.connection.start();
                } catch (jakarta.jms.IllegalStateException e) {
                    // ignore as it may already be started
                }
            }
        }
    }

    protected void stopConnection() {
        synchronized (this.connectionLock) {
            this.connectionStarted = false;
            if (this.connection != null) {
                try {
                    this.connection.stop();
                } catch (Exception e) {
                    LOG.debug("Error stopping connection. This exception is ignored.", e);
                }
            }
        }
    }

}
