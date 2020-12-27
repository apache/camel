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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.sjms.jms.ConnectionResource;
import org.apache.camel.component.sjms.tx.SessionTransactionSynchronization;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base SjmsProducer class.
 */
public abstract class SjmsProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SjmsProducer.class);

    private GenericObjectPool<MessageProducerResources> producers;
    private boolean useProducers = true;
    private ExecutorService executor;
    private Future<?> asyncStart;

    public SjmsProducer(Endpoint endpoint) {
        super(endpoint);
    }

    /**
     * Used to disable using the internal producer pool when using dynamic endpoints with toD
     */
    protected void disableProducers() {
        useProducers = false;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (useProducers) {
            if (!isSynchronous()) {
                this.executor
                        = getEndpoint().getCamelContext().getExecutorServiceManager().newDefaultThreadPool(this,
                                "SjmsProducer");
            }
            if (getProducers() == null) {
                GenericObjectPool<MessageProducerResources> producers
                        = new GenericObjectPool<>(new MessageProducerPool(this));
                setProducers(producers);
                producers.setMaxActive(getProducerCount());
                producers.setMaxIdle(getProducerCount());
                producers.setTestOnBorrow(getEndpoint().getComponent().isConnectionTestOnBorrow());
                producers.setLifo(false);
                if (getEndpoint().isPrefillPool()) {
                    if (getEndpoint().isAsyncStartListener()) {
                        asyncStart = getEndpoint().getComponent().getAsyncStartStopExecutorService().submit(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    fillProducersPool();
                                } catch (Throwable e) {
                                    LOG.warn("Error filling producer pool for destination: {}. This exception will be ignored.",
                                            getDestinationName(), e);
                                }
                            }

                            @Override
                            public String toString() {
                                return "AsyncStartListenerTask[" + getDestinationName() + "]";
                            }
                        });
                    } else {
                        fillProducersPool();
                    }
                }
            }
        }
    }

    private void fillProducersPool() throws Exception {
        while (producers.getNumIdle() < producers.getMaxIdle()) {
            producers.addObject();
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (asyncStart != null && !asyncStart.isDone()) {
            asyncStart.cancel(true);
        }
        if (getProducers() != null) {
            if (getEndpoint().isAsyncStopListener()) {
                getEndpoint().getComponent().getAsyncStartStopExecutorService().submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            getProducers().close();
                            setProducers(null);
                        } catch (Throwable e) {
                            LOG.warn("Error closing producers on destination: {}. This exception will be ignored.",
                                    getDestinationName(), e);
                        }
                    }

                    @Override
                    public String toString() {
                        return "AsyncStopListenerTask[" + getDestinationName() + "]";
                    }
                });
            } else {
                getProducers().close();
                setProducers(null);
            }
        }
        if (this.executor != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownGraceful(this.executor);
        }
    }

    @Override
    public SjmsEndpoint getEndpoint() {
        return (SjmsEndpoint) super.getEndpoint();
    }

    protected MessageProducerResources doCreateProducerModel(Session session) throws Exception {
        MessageProducer messageProducer = getEndpoint().getJmsObjectFactory().createMessageProducer(session, getEndpoint());
        return new MessageProducerResources(session, messageProducer, getCommitStrategy());
    }

    protected Session createSession() throws Exception {
        ConnectionResource connectionResource = getOrCreateConnectionResource();
        Connection conn = connectionResource.borrowConnection();
        try {
            return conn.createSession(isEndpointTransacted(), getAcknowledgeMode());
        } finally {
            connectionResource.returnConnection(conn);
        }
    }

    protected interface ReleaseProducerCallback {
        void release(MessageProducerResources producer);
    }

    private static class CloseProducerCallback implements ReleaseProducerCallback {
        @Override
        public void release(MessageProducerResources producer) {
            try {
                producer.getMessageProducer().close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private class ReturnProducerCallback implements ReleaseProducerCallback {
        @Override
        public void release(MessageProducerResources producer) {
            try {
                getProducers().returnObject(producer);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public abstract void sendMessage(
            Exchange exchange, AsyncCallback callback, MessageProducerResources producer,
            ReleaseProducerCallback releaseProducerCallback);

    public abstract void sendMessage(
            Exchange exchange, AsyncCallback callback, Session session, String destinationName);

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        String destinationName = exchange.getMessage().getHeader(SjmsConstants.JMS_DESTINATION_NAME, String.class);
        if (destinationName != null) {
            // remove the header so it wont be propagated
            exchange.getMessage().removeHeader(SjmsConstants.JMS_DESTINATION_NAME);
        }
        if (destinationName != null) {
            return doProcess(exchange, callback, destinationName);
        } else {
            return doProcess(exchange, callback);
        }
    }

    protected boolean doProcess(final Exchange exchange, AsyncCallback callback, final String destinationName) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Processing Exchange.id:{}", exchange.getExchangeId());
        }

        try {
            AsyncCallback ac = callback;
            Session session;
            if (isEndpointTransacted() && isSharedJMSSession()) {
                session = exchange.getIn().getHeader(SjmsConstants.JMS_SESSION, Session.class);
            } else {
                // TODO: Either rewrite to have connection factory externally pooled
                // or we have some built in session pooling
                session = createSession();
                ac = doneSync -> {
                    try {
                        // ensure session is closed after use
                        session.close();
                    } catch (Exception e) {
                        // ignore
                    }
                    callback.done(doneSync);
                };
            }
            final AsyncCallback asyncCallback = ac;

            if (!isSynchronous()) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("  Sending message asynchronously: {}", exchange.getExchangeId());
                }
                getExecutor().execute(() -> sendMessage(exchange, asyncCallback, session, destinationName));
                return false;
            } else {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("  Sending message synchronously: {}", exchange.getExchangeId());
                }
                sendMessage(exchange, asyncCallback, session, destinationName);
            }
        } catch (Exception e) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Processing Exchange.id:{}", exchange.getExchangeId() + " - FAILED");
            }
            if (LOG.isTraceEnabled()) {
                LOG.trace("Exception: {}", e.getMessage(), e);
            }
            // an error occurred so set on exchange and call the callback
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Processing Exchange.id:{}", exchange.getExchangeId() + " - SUCCESS");
        }
        return true;
    }

    protected boolean doProcess(final Exchange exchange, final AsyncCallback callback) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Processing Exchange.id:{}", exchange.getExchangeId());
        }

        try {
            final MessageProducerResources producer;
            final ReleaseProducerCallback releaseProducerCallback;
            if (isEndpointTransacted() && isSharedJMSSession()) {
                Session session = exchange.getIn().getHeader(SjmsConstants.JMS_SESSION, Session.class);
                if (session != null && session.getTransacted()) {
                    // Join existing transacted session - Synchronization must have been added
                    // by the session initiator
                    producer = doCreateProducerModel(session);
                    releaseProducerCallback = new CloseProducerCallback();
                } else {
                    // Propagate JMS session and register Synchronization as an initiator
                    producer = getProducers().borrowObject();
                    releaseProducerCallback = new ReturnProducerCallback();
                    exchange.getIn().setHeader(SjmsConstants.JMS_SESSION, producer.getSession());
                    exchange.getUnitOfWork().addSynchronization(
                            new SessionTransactionSynchronization(producer.getSession(), producer.getCommitStrategy()));
                }
            } else {
                producer = getProducers().borrowObject();
                releaseProducerCallback = new ReturnProducerCallback();
                if (isEndpointTransacted()) {
                    exchange.getUnitOfWork().addSynchronization(
                            new SessionTransactionSynchronization(producer.getSession(), producer.getCommitStrategy()));
                }
            }

            if (!isSynchronous()) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("  Sending message asynchronously: {}", exchange.getExchangeId());
                }
                getExecutor().execute(() -> sendMessage(exchange, callback, producer, releaseProducerCallback));
                return false;
            } else {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("  Sending message synchronously: {}", exchange.getExchangeId());
                }
                sendMessage(exchange, callback, producer, releaseProducerCallback);
            }
        } catch (Exception e) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Processing Exchange.id:{}", exchange.getExchangeId() + " - FAILED");
            }
            if (LOG.isTraceEnabled()) {
                LOG.trace("Exception: {}", e.getMessage(), e);
            }
            // an error occurred so set on exchange and call the callback
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Processing Exchange.id:{}", exchange.getExchangeId() + " - SUCCESS");
        }
        return true;
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

    /**
     * Gets the acknowledgment mode for this instance of DestinationProducer.
     */
    public int getAcknowledgeMode() {
        return getEndpoint().getAcknowledgementMode().intValue();
    }

    /**
     * Gets the synchronous value for this instance of DestinationProducer.
     */
    public boolean isSynchronous() {
        return getEndpoint().isSynchronous();
    }

    /**
     * Gets the replyTo for this instance of DestinationProducer.
     */
    public String getReplyTo() {
        return getEndpoint().getNamedReplyTo();
    }

    /**
     * Gets the destinationName for this instance of DestinationProducer.
     */
    public String getDestinationName() {
        return getEndpoint().getDestinationName();
    }

    /**
     * Sets the producer pool for this instance of SjmsProducer.
     */
    public void setProducers(GenericObjectPool<MessageProducerResources> producers) {
        this.producers = producers;
    }

    /**
     * Gets the MessageProducerPool value of producers for this instance of SjmsProducer.
     */
    public GenericObjectPool<MessageProducerResources> getProducers() {
        return producers;
    }

    /**
     * Test to verify if this endpoint is a JMS Topic or Queue.
     */
    public boolean isTopic() {
        return getEndpoint().isTopic();
    }

    /**
     * Test to determine if this endpoint should use a JMS Transaction.
     */
    public boolean isEndpointTransacted() {
        return getEndpoint().isTransacted();
    }

    /**
     * Test to determine if this endpoint should share a JMS Session with other SJMS endpoints.
     */
    public boolean isSharedJMSSession() {
        return getEndpoint().isSharedJMSSession();
    }

    /**
     * Returns the named reply to value for this producer
     */
    public String getNamedReplyTo() {
        return getEndpoint().getNamedReplyTo();
    }

    /**
     * Gets the producerCount for this instance of SjmsProducer.
     */
    public int getProducerCount() {
        return getEndpoint().getProducerCount();
    }

    /**
     * Gets consumerCount for this instance of SjmsProducer.
     */
    public int getConsumerCount() {
        return getEndpoint().getConsumerCount();
    }

    /**
     * Gets the executor for this instance of SjmsProducer.
     */
    public ExecutorService getExecutor() {
        return executor;
    }

    /**
     * Gets the ttl for this instance of SjmsProducer.
     */
    public long getTtl() {
        return getEndpoint().getTtl();
    }

    /**
     * Gets the boolean value of persistent for this instance of SjmsProducer.
     */
    public boolean isPersistent() {
        return getEndpoint().isPersistent();
    }

    /**
     * Gets responseTimeOut for this instance of SjmsProducer.
     */
    public long getResponseTimeOut() {
        return getEndpoint().getResponseTimeOut();
    }

    /**
     * Gets commitStrategy for this instance of SjmsProducer.
     */
    protected TransactionCommitStrategy getCommitStrategy() {
        if (isEndpointTransacted()) {
            return getEndpoint().getTransactionCommitStrategy();
        }
        return null;
    }

}
