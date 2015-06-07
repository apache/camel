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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
import org.apache.camel.component.sjms.MessageConsumerResources;
import org.apache.camel.component.sjms.MessageProducerResources;
import org.apache.camel.component.sjms.SjmsEndpoint;
import org.apache.camel.component.sjms.SjmsProducer;
import org.apache.camel.component.sjms.jms.JmsConstants;
import org.apache.camel.component.sjms.jms.JmsMessageHelper;
import org.apache.camel.component.sjms.jms.JmsObjectFactory;
import org.apache.camel.component.sjms.tx.SessionTransactionSynchronization;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.pool.BasePoolableObjectFactory;
import org.apache.commons.pool.impl.GenericObjectPool;

/**
 * A Camel Producer that provides the InOut Exchange pattern.
 */
public class InOutProducer extends SjmsProducer {

    private static final Map<String, Exchanger<Object>> EXCHANGERS = new ConcurrentHashMap<String, Exchanger<Object>>();

    /**
     * A pool of {@link MessageConsumerResources} objects that are the reply
     * consumers.
     */
    protected class MessageConsumerResourcesFactory extends BasePoolableObjectFactory<MessageConsumerResources> {

        @Override
        public MessageConsumerResources makeObject() throws Exception {
            MessageConsumerResources answer;
            Connection conn = getConnectionResource().borrowConnection();
            try {
                Session session;
                if (isEndpointTransacted()) {
                    session = conn.createSession(true, Session.SESSION_TRANSACTED);
                } else {
                    session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
                }

                Destination replyToDestination;
                if (ObjectHelper.isEmpty(getNamedReplyTo())) {
                    replyToDestination = getEndpoint().getDestinationCreationStrategy().createTemporaryDestination(session, isTopic());
                } else {
                    replyToDestination = getEndpoint().getDestinationCreationStrategy().createDestination(session, getNamedReplyTo(), isTopic());
                }
                MessageConsumer messageConsumer = JmsObjectFactory.createMessageConsumer(session, replyToDestination, null, isTopic(), null, true);
                messageConsumer.setMessageListener(new MessageListener() {

                    @Override
                    public void onMessage(final Message message) {
                        log.debug("Message Received in the Consumer Pool");
                        log.debug("  Message : {}", message);
                        try {
                            Exchanger<Object> exchanger = EXCHANGERS.get(message.getJMSCorrelationID());
                            exchanger.exchange(message, getResponseTimeOut(), TimeUnit.MILLISECONDS);
                        } catch (Exception e) {
                            log.error("Unable to exchange message: {}", message, e);
                        }

                    }
                });
                answer = new MessageConsumerResources(session, messageConsumer, replyToDestination);
            } catch (Exception e) {
                log.error("Unable to create the MessageConsumerResource: " + e.getLocalizedMessage());
                throw new CamelException(e);
            } finally {
                getConnectionResource().returnConnection(conn);
            }
            return answer;
        }

        @Override
        public void destroyObject(MessageConsumerResources model) throws Exception {
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

    private GenericObjectPool<MessageConsumerResources> consumers;

    public InOutProducer(final SjmsEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doStart() throws Exception {
        if (ObjectHelper.isEmpty(getNamedReplyTo())) {
            log.debug("No reply to destination is defined.  Using temporary destinations.");
        } else {
            log.debug("Using {} as the reply to destination.", getNamedReplyTo());
        }
        if (getConsumers() == null) {
            setConsumers(new GenericObjectPool<MessageConsumerResources>(new MessageConsumerResourcesFactory()));
            getConsumers().setMaxActive(getConsumerCount());
            getConsumers().setMaxIdle(getConsumerCount());
            while (getConsumers().getNumIdle() < getConsumers().getMaxIdle()) {
                getConsumers().addObject();
            }
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (getConsumers() != null) {
            getConsumers().close();
            setConsumers(null);
        }
    }

    @Override
    public MessageProducerResources doCreateProducerModel() throws Exception {
        MessageProducerResources answer;
        Connection conn = getConnectionResource().borrowConnection();
        try {
            Session session = conn.createSession(isEndpointTransacted(), getAcknowledgeMode());
            Destination destination = getEndpoint().getDestinationCreationStrategy().createDestination(session, getDestinationName(), isTopic());
            MessageProducer messageProducer = JmsObjectFactory.createMessageProducer(session, destination, isPersistent(), getTtl());

            answer = new MessageProducerResources(session, messageProducer);

        } catch (Exception e) {
            log.error("Unable to create the MessageProducer", e);
            throw e;
        } finally {
            getConnectionResource().returnConnection(conn);
        }

        return answer;
    }

    /**
     * TODO time out is actually double as it waits for the producer and then
     * waits for the response. Use an atomic long to manage the countdown
     */
    @Override
    public void sendMessage(final Exchange exchange, final AsyncCallback callback, final MessageProducerResources producer) throws Exception {
        if (isEndpointTransacted()) {
            exchange.getUnitOfWork().addSynchronization(new SessionTransactionSynchronization(producer.getSession(), getCommitStrategy()));
        }

        Message request = JmsMessageHelper.createMessage(exchange, producer.getSession(), getEndpoint());

        // TODO just set the correlation id don't get it from the
        // message
        String correlationId;
        if (exchange.getIn().getHeader(JmsConstants.JMS_CORRELATION_ID, String.class) == null) {
            correlationId = UUID.randomUUID().toString().replace("-", "");
        } else {
            correlationId = exchange.getIn().getHeader(JmsConstants.JMS_CORRELATION_ID, String.class);
        }
        Object responseObject = null;
        Exchanger<Object> messageExchanger = new Exchanger<Object>();
        JmsMessageHelper.setCorrelationId(request, correlationId);
        EXCHANGERS.put(correlationId, messageExchanger);

        MessageConsumerResources consumer = consumers.borrowObject();
        JmsMessageHelper.setJMSReplyTo(request, consumer.getReplyToDestination());
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
            EXCHANGERS.remove(correlationId);
        } catch (InterruptedException e) {
            log.debug("Exchanger was interrupted while waiting on response", e);
            exchange.setException(e);
        } catch (TimeoutException e) {
            log.debug("Exchanger timed out while waiting on response", e);
            exchange.setException(e);
        }

        if (exchange.getException() == null) {
            if (responseObject instanceof Throwable) {
                exchange.setException((Throwable) responseObject);
            } else if (responseObject instanceof Message) {
                Message response = (Message) responseObject;
                JmsMessageHelper.populateExchange(response, exchange, true, getEndpoint().getJmsKeyFormatStrategy());
            } else {
                exchange.setException(new CamelException("Unknown response type: " + responseObject));
            }
        }

        callback.done(isSynchronous());
    }

    public void setConsumers(GenericObjectPool<MessageConsumerResources> consumers) {
        this.consumers = consumers;
    }

    public GenericObjectPool<MessageConsumerResources> getConsumers() {
        return consumers;
    }
}
