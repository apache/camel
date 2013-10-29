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
package org.apache.camel.component.sjms.producer;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.component.sjms.SjmsEndpoint;
import org.apache.camel.component.sjms.SjmsExchangeMessageHelper;
import org.apache.camel.component.sjms.SjmsProducer;
import org.apache.camel.component.sjms.jms.JmsObjectFactory;
import org.apache.camel.component.sjms.jms.ObjectPool;
import org.apache.camel.component.sjms.tx.SessionTransactionSynchronization;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Camel Producer that provides the InOut Exchange pattern.
 */
public class InOutProducer extends SjmsProducer {

    /**
     * We use the {@link ReadWriteLock} to manage the {@link TreeMap} in place
     * of a {@link ConcurrentMap} because due to significant performance gains.
     * TODO Externalize the Exchanger Map to a store object
     */
    private static Map<String, Exchanger<Object>> exchangerMap = new TreeMap<String, Exchanger<Object>>();
    private ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * A pool of {@link MessageConsumerResource} objects that are the reply
     * consumers. 
     * TODO Add Class documentation for MessageProducerPool 
     * TODO Externalize
     */
    protected class MessageConsumerPool extends ObjectPool<MessageConsumerResource> {

        /**
         * TODO Add Constructor Javadoc
         * 
         * @param poolSize
         */
        public MessageConsumerPool(int poolSize) {
            super(poolSize);
        }

        @Override
        protected MessageConsumerResource createObject() throws Exception {
            MessageConsumerResource answer = null;
            Connection conn = null;
            Session session = null;
            try {
                conn = getConnectionResource().borrowConnection();
                if (isEndpointTransacted()) {
                    session = conn.createSession(true, Session.SESSION_TRANSACTED);
                } else {
                    session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
                }

                Destination replyToDestination = null;
                if (ObjectHelper.isEmpty(getNamedReplyTo())) {
                    replyToDestination = JmsObjectFactory.createTemporaryDestination(session, isTopic());
                } else {
                    replyToDestination = JmsObjectFactory.createDestination(session, getNamedReplyTo(), isTopic());
                }
                MessageConsumer messageConsumer = JmsObjectFactory.createMessageConsumer(session, replyToDestination, null, isTopic(), null, true);
                messageConsumer.setMessageListener(new MessageListener() {

                    @Override
                    public void onMessage(Message message) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Message Received in the Consumer Pool");
                            logger.debug("  Message : {}", message);
                        }
                        try {
                            Exchanger<Object> exchanger = exchangerMap.get(message.getJMSCorrelationID());
                            exchanger.exchange(message, getResponseTimeOut(), TimeUnit.MILLISECONDS);
                        } catch (Exception e) {
                            ObjectHelper.wrapRuntimeCamelException(e);
                        }

                    }
                });
                answer = new MessageConsumerResource(session, messageConsumer, replyToDestination);
            } catch (Exception e) {
                log.error("Unable to create the MessageConsumerResource: " + e.getLocalizedMessage());
                throw new CamelException(e);
            } finally {
                getConnectionResource().returnConnection(conn);
            }
            return answer;
        }

        @Override
        protected void destroyObject(MessageConsumerResource model) throws Exception {
            if (model.getMessageConsumer() != null) {
                model.getMessageConsumer().close();
            }

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

    /**
     * TODO Add Class documentation for MessageConsumerResource
     */
    protected class MessageConsumerResource {
        private final Session session;
        private final MessageConsumer messageConsumer;
        private final Destination replyToDestination;

        /**
         * TODO Add Constructor Javadoc
         * 
         * @param session
         * @param messageConsumer
         */
        public MessageConsumerResource(Session session, MessageConsumer messageConsumer, Destination replyToDestination) {
            this.session = session;
            this.messageConsumer = messageConsumer;
            this.replyToDestination = replyToDestination;
        }

        public Session getSession() {
            return session;
        }

        public MessageConsumer getMessageConsumer() {
            return messageConsumer;
        }

        public Destination getReplyToDestination() {
            return replyToDestination;
        }
    }

    protected class InternalTempDestinationListener implements MessageListener {
        private final Logger tempLogger = LoggerFactory.getLogger(InternalTempDestinationListener.class);
        private Exchanger<Object> exchanger;

        /**
         * TODO Add Constructor Javadoc
         * 
         * @param exchanger
         */
        public InternalTempDestinationListener(Exchanger<Object> exchanger) {
            this.exchanger = exchanger;
        }

        @Override
        public void onMessage(Message message) {
            if (tempLogger.isDebugEnabled()) {
                tempLogger.debug("Message Received in the Consumer Pool");
                tempLogger.debug("  Message : {}", message);
            }
            try {
                exchanger.exchange(message, getResponseTimeOut(), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                ObjectHelper.wrapRuntimeCamelException(e);
            }

        }
    }

    private MessageConsumerPool consumers;

    public InOutProducer(SjmsEndpoint endpoint) {
        super(endpoint);
        endpoint.getConsumerCount();
    }

    @Override
    protected void doStart() throws Exception {
        if (ObjectHelper.isEmpty(getNamedReplyTo())) {
            log.debug("No reply to destination is defined.  Using temporary destinations.");
        } else {
            log.debug("Using {} as the reply to destination.", getNamedReplyTo());
        }
        if (getConsumers() == null) {
            setConsumers(new MessageConsumerPool(getConsumerCount()));
            getConsumers().fillPool();
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (getConsumers() != null) {
            getConsumers().drainPool();
            setConsumers(null);
        }
    }

    @Override
    public MessageProducerResources doCreateProducerModel() throws Exception {
        MessageProducerResources answer = null;
        Connection conn = null;
        try {
            MessageProducer messageProducer = null;
            Session session = null;
            
            conn = getConnectionResource().borrowConnection();
            if (isEndpointTransacted()) {
                session = conn.createSession(true, getAcknowledgeMode());
            } else {
                session = conn.createSession(false, getAcknowledgeMode());
            }
            if (isTopic()) {
                messageProducer = JmsObjectFactory.createMessageProducer(session, getDestinationName(), isTopic(), isPersistent(), getTtl());
            } else {
                messageProducer = JmsObjectFactory.createQueueProducer(session, getDestinationName());
            }

            if (session == null) {
                throw new CamelException("Message Consumer Creation Exception: Session is NULL");
            }
            if (messageProducer == null) {
                throw new CamelException("Message Consumer Creation Exception: MessageProducer is NULL");
            }
            
            answer = new MessageProducerResources(session, messageProducer);

        } catch (Exception e) {
            log.error("Unable to create the MessageProducer: " + e.getLocalizedMessage());
        } finally {
            if (conn != null) {
                getConnectionResource().returnConnection(conn);
            }
        }
        
        return answer;
    }

    /**
     * TODO time out is actually double as it waits for the producer and then
     * waits for the response. Use an atomic long to manage the countdown
     * 
     * @see org.apache.camel.component.sjms.SjmsProducer#sendMessage(org.apache.camel.Exchange,
     *      org.apache.camel.AsyncCallback)
     * @param exchange
     * @param callback
     * @throws Exception
     */
    @Override
    public void sendMessage(final Exchange exchange, final AsyncCallback callback) throws Exception {
        if (getProducers() != null) {
            MessageProducerResources producer = null;
            try {
                producer = getProducers().borrowObject(getResponseTimeOut());
            } catch (Exception e1) {
                log.warn("The producer pool is exhausted.  Consider setting producerCount to a higher value or disable the fixed size of the pool by setting fixedResourcePool=false.");
                exchange.setException(new Exception("Producer Resource Pool is exhausted"));
            }
            if (producer != null) {

                if (isEndpointTransacted()) {
                    exchange.getUnitOfWork().addSynchronization(new SessionTransactionSynchronization(producer.getSession(), getCommitStrategy()));
                }

                Message request = SjmsExchangeMessageHelper.createMessage(exchange, producer.getSession(), getSjmsEndpoint().getJmsKeyFormatStrategy());

                // TODO just set the correlation id don't get it from the
                // message
                String correlationId = null;
                if (exchange.getIn().getHeader("JMSCorrelationID", String.class) == null) {
                    correlationId = UUID.randomUUID().toString().replace("-", "");
                } else {
                    correlationId = exchange.getIn().getHeader("JMSCorrelationID", String.class);
                }
                Object responseObject = null;
                Exchanger<Object> messageExchanger = new Exchanger<Object>();
                SjmsExchangeMessageHelper.setCorrelationId(request, correlationId);
                try {
                    lock.writeLock().lock();
                    exchangerMap.put(correlationId, messageExchanger);
                } finally {
                    lock.writeLock().unlock();
                }

                MessageConsumerResource consumer = consumers.borrowObject(getResponseTimeOut());
                SjmsExchangeMessageHelper.setJMSReplyTo(request, consumer.getReplyToDestination());
                consumers.returnObject(consumer);
                producer.getMessageProducer().send(request);

                // Return the producer to the pool so another waiting producer
                // can move forward
                // without waiting on us to complete the exchange
                try {
                    getProducers().returnObject(producer);
                } catch (Exception exception) {
                    // thrown if the pool is full. safe to ignore.
                }

                try {
                    responseObject = messageExchanger.exchange(null, getResponseTimeOut(), TimeUnit.MILLISECONDS);

                    try {
                        lock.writeLock().lock();
                        exchangerMap.remove(correlationId);
                    } finally {
                        lock.writeLock().unlock();
                    }
                } catch (InterruptedException e) {
                    log.debug("Exchanger was interrupted while waiting on response", e);
                    exchange.setException(e);
                } catch (TimeoutException e) {
                    log.debug("Exchanger timed out while waiting on response", e);
                    exchange.setException(e);
                }

                if (exchange.getException() == null) {
                    if (responseObject instanceof Throwable) {
                        exchange.setException((Throwable)responseObject);
                    } else if (responseObject instanceof Message) {
                        Message response = (Message)responseObject;
                        SjmsExchangeMessageHelper.populateExchange(response, exchange, true);
                    } else {
                        exchange.setException(new CamelException("Unknown response type: " + responseObject));
                    }
                }
            }

            callback.done(isSynchronous());
        }
    }

    public void setConsumers(MessageConsumerPool consumers) {
        this.consumers = consumers;
    }

    public MessageConsumerPool getConsumers() {
        return consumers;
    }
}
