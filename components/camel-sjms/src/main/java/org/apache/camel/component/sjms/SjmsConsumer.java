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

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;

import org.apache.camel.CamelException;
import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.component.sjms.consumer.AbstractMessageHandler;
import org.apache.camel.component.sjms.consumer.InOnlyMessageHandler;
import org.apache.camel.component.sjms.consumer.InOutMessageHandler;
import org.apache.camel.component.sjms.jms.ConnectionResource;
import org.apache.camel.component.sjms.jms.JmsObjectFactory;
import org.apache.camel.component.sjms.jms.ObjectPool;
import org.apache.camel.component.sjms.taskmanager.TimedTaskManager;
import org.apache.camel.component.sjms.tx.BatchTransactionCommitStrategy;
import org.apache.camel.component.sjms.tx.DefaultTransactionCommitStrategy;
import org.apache.camel.component.sjms.tx.SessionBatchTransactionSynchronization;
import org.apache.camel.component.sjms.tx.SessionTransactionSynchronization;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.spi.Synchronization;

/**
 * The SjmsConsumer is the base class for the SJMS MessageListener pool.
 * 
 */
public class SjmsConsumer extends DefaultConsumer {

    protected MessageConsumerPool consumers;
    private final ExecutorService executor;

    /**
     * A pool of MessageConsumerResources created at the initialization of the associated consumer.
     */
    protected class MessageConsumerPool extends ObjectPool<MessageConsumerResources> {

        public MessageConsumerPool() {
            super(getConsumerCount());
        }

        /** 
         * Creates a new MessageConsumerResources instance.
         *
         * @see org.apache.camel.component.sjms.jms.ObjectPool#createObject()
         */
        @Override
        protected MessageConsumerResources createObject() throws Exception {
            MessageConsumerResources model = createConsumer();
            return model;
        }

        /** 
         * Cleans up the MessageConsumerResources.
         *
         * @see org.apache.camel.component.sjms.jms.ObjectPool#destroyObject(java.lang.Object)
         */
        @Override
        protected void destroyObject(MessageConsumerResources model) throws Exception {
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

    protected class MessageConsumerResources {
        private final Session session;
        private final MessageConsumer messageConsumer;

        public MessageConsumerResources(MessageConsumer messageConsumer) {
            this.session = null;
            this.messageConsumer = messageConsumer;
        }

        public MessageConsumerResources(Session session, MessageConsumer messageConsumer) {
            this.session = session;
            this.messageConsumer = messageConsumer;
        }

        /**
         * Gets the Session value of session for this instance of
         * MessageProducerModel.
         * 
         * @return the session
         */
        public Session getSession() {
            return session;
        }

        /**
         * Gets the QueueSender value of queueSender for this instance of
         * MessageProducerModel.
         * 
         * @return the queueSender
         */
        public MessageConsumer getMessageConsumer() {
            return messageConsumer;
        }
    }

    public SjmsConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.executor = endpoint.getCamelContext().getExecutorServiceManager().newDefaultThreadPool(this, "SjmsConsumer");
    }

    @Override
    public SjmsEndpoint getEndpoint() {
        return (SjmsEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        consumers = new MessageConsumerPool();
        consumers.fillPool();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (consumers != null) {
            consumers.drainPool();
            consumers = null;
        }
    }

    /**
     * Creates a {@link MessageConsumerResources} with a dedicated
     * {@link Session} required for transacted and InOut consumers.
     */
    @SuppressWarnings("unused")
    private MessageConsumerResources createConsumer() throws Exception {
        MessageConsumerResources answer = null;
        Connection conn = null;
        try {
            conn = getConnectionResource().borrowConnection();
            
            Session session = null;
            MessageConsumer messageConsumer = null;
            if (isTransacted()) {
                session = conn.createSession(true, Session.SESSION_TRANSACTED);
            } else {
                session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            }
            messageConsumer = JmsObjectFactory.createMessageConsumer(session, getDestinationName(), getMessageSelector(), isTopic(), getDurableSubscriptionId());
            MessageListener handler = createMessageHandler(session);
            messageConsumer.setMessageListener(handler);
            
            if (session == null) {
                throw new CamelException("Message Consumer Creation Exception: Session is NULL");
            }
            if (messageConsumer == null) {
                throw new CamelException("Message Consumer Creation Exception: MessageConsumer is NULL");
            }
            answer = new MessageConsumerResources(session, messageConsumer);
        } catch (Exception e) {
            log.error("Unable to create the MessageConsumer: " + e.getLocalizedMessage());
        } finally {
            if (conn != null) {
                getConnectionResource().returnConnection(conn);
            }
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

        TransactionCommitStrategy commitStrategy = null;
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
        messageHandler.setTopic(isTopic());
        return messageHandler;
    }

    protected ConnectionResource getConnectionResource() {
        return getEndpoint().getConnectionResource();
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
