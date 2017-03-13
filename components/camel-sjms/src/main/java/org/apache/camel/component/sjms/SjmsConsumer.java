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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.jms.Connection;
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
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.spi.Synchronization;
import org.apache.commons.pool.BasePoolableObjectFactory;
import org.apache.commons.pool.impl.GenericObjectPool;

/**
 * The SjmsConsumer is the base class for the SJMS MessageListener pool.
 */
public class SjmsConsumer extends DefaultConsumer {

    protected GenericObjectPool<MessageConsumerResources> consumers;
    private ExecutorService executor;
    private Future<?> asyncStart;

    /**
     * A pool of MessageConsumerResources created at the initialization of the associated consumer.
     */
    protected class MessageConsumerResourcesFactory extends BasePoolableObjectFactory<MessageConsumerResources> {

        /**
         * Creates a new MessageConsumerResources instance.
         *
         * @see org.apache.commons.pool.PoolableObjectFactory#makeObject()
         */
        @Override
        public MessageConsumerResources makeObject() throws Exception {
            return createConsumer();
        }

        /**
         * Cleans up the MessageConsumerResources.
         *
         * @see org.apache.commons.pool.PoolableObjectFactory#destroyObject(java.lang.Object)
         */
        @Override
        public void destroyObject(MessageConsumerResources model) throws Exception {
            if (model != null) {
                // First clean up our message consumer
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
            }
        }
    }

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

        this.executor = getEndpoint().getCamelContext().getExecutorServiceManager().newDefaultThreadPool(this, "SjmsConsumer");
        if (consumers == null) {
            consumers = new GenericObjectPool<MessageConsumerResources>(new MessageConsumerResourcesFactory());
            consumers.setMaxActive(getConsumerCount());
            consumers.setMaxIdle(getConsumerCount());
            if (getEndpoint().isAsyncStartListener()) {
                asyncStart = getEndpoint().getComponent().getAsyncStartStopExecutorService().submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            fillConsumersPool();
                        } catch (Throwable e) {
                            log.warn("Error starting listener container on destination: " + getDestinationName() + ". This exception will be ignored.", e);
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
    }

    private void fillConsumersPool() throws Exception {
        while (consumers.getNumIdle() < consumers.getMaxIdle()) {
            consumers.addObject();
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (asyncStart != null && !asyncStart.isDone()) {
            asyncStart.cancel(true);
        }
        if (consumers != null) {
            if (getEndpoint().isAsyncStopListener()) {
                getEndpoint().getComponent().getAsyncStartStopExecutorService().submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            consumers.close();
                            consumers = null;
                        } catch (Throwable e) {
                            log.warn("Error stopping listener container on destination: " + getDestinationName() + ". This exception will be ignored.", e);
                        }
                    }

                    @Override
                    public String toString() {
                        return "AsyncStopListenerTask[" + getDestinationName() + "]";
                    }
                });
            } else {
                consumers.close();
                consumers = null;
            }
        }
        if (this.executor != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownGraceful(this.executor);
        }
    }

    /**
     * Creates a {@link MessageConsumerResources} with a dedicated
     * {@link Session} required for transacted and InOut consumers.
     */
    private MessageConsumerResources createConsumer() throws Exception {
        MessageConsumerResources answer;
        ConnectionResource connectionResource = getOrCreateConnectionResource();
        Connection conn = connectionResource.borrowConnection();
        try {
            Session session = conn.createSession(isTransacted(), isTransacted() ? Session.SESSION_TRANSACTED : Session.AUTO_ACKNOWLEDGE);
            MessageConsumer messageConsumer = getEndpoint().getJmsObjectFactory().createMessageConsumer(session, getEndpoint());
            MessageListener handler = createMessageHandler(session);
            messageConsumer.setMessageListener(handler);

            answer = new MessageConsumerResources(session, messageConsumer);
        } catch (Exception e) {
            log.error("Unable to create the MessageConsumer", e);
            throw e;
        } finally {
            connectionResource.returnConnection(conn);
        }
        return answer;
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
            if (isTransacted()) {
                messageHandler = new InOnlyMessageHandler(getEndpoint(), executor, synchronization);
            } else {
                messageHandler = new InOnlyMessageHandler(getEndpoint(), executor);
            }
        } else {
            if (isTransacted()) {
                messageHandler = new InOutMessageHandler(getEndpoint(), executor, synchronization);
            } else {
                messageHandler = new InOutMessageHandler(getEndpoint(), executor);
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

}
