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
package org.apache.camel.component.sjms.producer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.component.sjms.MessageConsumerResources;
import org.apache.camel.component.sjms.MessageProducerResources;
import org.apache.camel.component.sjms.SjmsEndpoint;
import org.apache.camel.component.sjms.SjmsMessage;
import org.apache.camel.component.sjms.SjmsProducer;
import org.apache.camel.component.sjms.jms.ConnectionResource;
import org.apache.camel.component.sjms.jms.DestinationNameParser;
import org.apache.camel.component.sjms.jms.JmsConstants;
import org.apache.camel.component.sjms.jms.JmsMessageHelper;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.pool.BasePoolableObjectFactory;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Camel Producer that provides the InOut Exchange pattern.
 */
public class InOutProducer extends SjmsProducer {

    private static final Logger LOG = LoggerFactory.getLogger(InOutProducer.class);

    private static final Map<String, Exchanger<Object>> EXCHANGERS = new ConcurrentHashMap<>();

    private static final String GENERATED_CORRELATION_ID_PREFIX = "Camel-";
    private UuidGenerator uuidGenerator;
    private GenericObjectPool<MessageConsumerResources> consumers;

    public InOutProducer(final SjmsEndpoint endpoint) {
        super(endpoint);
    }

    public UuidGenerator getUuidGenerator() {
        return uuidGenerator;
    }

    public void setUuidGenerator(UuidGenerator uuidGenerator) {
        this.uuidGenerator = uuidGenerator;
    }

    /**
     * A pool of {@link MessageConsumerResources} objects that are the reply consumers.
     */
    protected class MessageConsumerResourcesFactory extends BasePoolableObjectFactory<MessageConsumerResources> {

        @Override
        public MessageConsumerResources makeObject() throws Exception {
            MessageConsumerResources answer;
            ConnectionResource connectionResource = getOrCreateConnectionResource();
            Connection conn = connectionResource.borrowConnection();
            try {
                Session session;
                if (isEndpointTransacted()) {
                    session = conn.createSession(true, Session.SESSION_TRANSACTED);
                } else {
                    session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
                }

                Destination replyToDestination;
                boolean isReplyToTopic;
                if (ObjectHelper.isEmpty(getNamedReplyTo())) {
                    isReplyToTopic = isTopic();
                    replyToDestination = getEndpoint().getDestinationCreationStrategy().createTemporaryDestination(session, isReplyToTopic);
                } else {
                    DestinationNameParser parser = new DestinationNameParser();
                    isReplyToTopic = parser.isNamedReplyToTopic(getNamedReplyTo(), isTopic());
                    replyToDestination = getEndpoint().getDestinationCreationStrategy().createDestination(session, getNamedReplyTo(), isReplyToTopic);
                }
                MessageConsumer messageConsumer = getEndpoint().getJmsObjectFactory().createMessageConsumer(session, replyToDestination, null, isReplyToTopic, null, true, false, false);
                messageConsumer.setMessageListener(new MessageListener() {
                    @Override
                    public void onMessage(final Message message) {
                        LOG.debug("Message Received in the Consumer Pool");
                        LOG.debug("  Message : {}", message);
                        try {
                            String correlationID = message.getJMSCorrelationID();
                            Exchanger<Object> exchanger = EXCHANGERS.get(correlationID);
                            if (exchanger != null) {
                                exchanger.exchange(message, getResponseTimeOut(), TimeUnit.MILLISECONDS);
                            } else {
                                // we could not correlate the received reply message to a matching request and therefore
                                // we cannot continue routing the unknown message
                                // log a warn and then ignore the message
                                LOG.warn("Reply received for unknown correlationID [{}] on reply destination [{}]. Current correlation map size: {}. The message will be ignored: {}",
                                    correlationID, replyToDestination, EXCHANGERS.size(), message);
                            }
                        } catch (Exception e) {
                            LOG.warn("Unable to exchange message: {}. This exception is ignored.", message, e);
                        }
                    }
                });
                answer = new MessageConsumerResources(session, messageConsumer, replyToDestination);
            } catch (Exception e) {
                LOG.error("Unable to create the MessageConsumerResource: {}", e.getLocalizedMessage());
                throw new CamelException(e);
            } finally {
                connectionResource.returnConnection(conn);
            }
            return answer;
        }

        @Override
        public boolean validateObject(MessageConsumerResources obj) {
            try {
                obj.getSession().getAcknowledgeMode();
                return true;
            } catch (JMSException ex) {
                LOG.error("Cannot validate session", ex);
            }
            return false;
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

    @Override
    protected void doStart() throws Exception {

        if (isEndpointTransacted()) {
            throw new IllegalArgumentException("InOut exchange pattern is incompatible with transacted=true as it cause a deadlock. Please use transacted=false or InOnly exchange pattern.");
        }

        if (ObjectHelper.isEmpty(getNamedReplyTo())) {
            LOG.debug("No reply to destination is defined. Using temporary destinations.");
        } else {
            LOG.debug("Using {} as the reply to destination.", getNamedReplyTo());
        }
        if (uuidGenerator == null) {
            // use the generator configured on the camel context
            uuidGenerator = getEndpoint().getCamelContext().getUuidGenerator();
        }
        if (consumers == null) {
            consumers = new GenericObjectPool<>(new MessageConsumerResourcesFactory());
            consumers.setMaxActive(getConsumerCount());
            consumers.setMaxIdle(getConsumerCount());
            consumers.setTestOnBorrow(getEndpoint().getComponent().isConnectionTestOnBorrow());
            while (consumers.getNumIdle() < consumers.getMaxIdle()) {
                consumers.addObject();
            }
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (consumers != null) {
            consumers.close();
            consumers = null;
        }
    }

    /**
     * TODO time out is actually double as it waits for the producer and then
     * waits for the response. Use an atomic long to manage the countdown
     */
    @Override
    public void sendMessage(final Exchange exchange, final AsyncCallback callback, final MessageProducerResources producer, final ReleaseProducerCallback releaseProducerCallback) throws Exception {
        Message request = getEndpoint().getBinding().makeJmsMessage(exchange, producer.getSession());

        String correlationId = exchange.getIn().getHeader(JmsConstants.JMS_CORRELATION_ID, String.class);
        if (correlationId == null) {
            // we append the 'Camel-' prefix to know it was generated by us
            correlationId = GENERATED_CORRELATION_ID_PREFIX + getUuidGenerator().generateUuid();
        }

        Object responseObject = null;
        Exchanger<Object> messageExchanger = new Exchanger<>();
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
            releaseProducerCallback.release(producer);
        } catch (Exception exception) {
            // thrown if the pool is full. safe to ignore.
        }

        try {
            responseObject = messageExchanger.exchange(null, getResponseTimeOut(), TimeUnit.MILLISECONDS);
            EXCHANGERS.remove(correlationId);
        } catch (InterruptedException e) {
            LOG.debug("Exchanger was interrupted while waiting on response", e);
            exchange.setException(e);
        } catch (TimeoutException e) {
            LOG.debug("Exchanger timed out while waiting on response", e);
            exchange.setException(e);
        }

        if (exchange.getException() == null) {
            if (responseObject instanceof Throwable) {
                exchange.setException((Throwable) responseObject);
            } else if (responseObject instanceof Message) {
                Message message = (Message) responseObject;

                SjmsMessage response = new SjmsMessage(exchange, message, consumer.getSession(), getEndpoint().getBinding());
                // the JmsBinding is designed to be "pull-based": it will populate the Camel message on demand
                // therefore, we link Exchange and OUT message before continuing, so that the JmsBinding has full access
                // to everything it may need, and can populate headers, properties, etc. accordingly (solves CAMEL-6218).
                exchange.setOut(response);
            } else {
                exchange.setException(new CamelException("Unknown response type: " + responseObject));
            }
        }

        callback.done(isSynchronous());
    }

}
