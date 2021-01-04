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

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.camel.CamelException;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.component.sjms.MessageConsumerResources;
import org.apache.camel.component.sjms.SjmsConstants;
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

import static org.apache.camel.component.sjms.SjmsHelper.closeProducer;

/**
 * A Camel Producer that provides the InOut Exchange pattern.
 */
public class InOutProducer extends SjmsProducer {

    // TODO: reply manager

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

    @Deprecated
    protected ConnectionResource getOrCreateConnectionResource() {
        ConnectionResource answer = getEndpoint().getConnectionResource();
        if (answer == null) {
            answer = getEndpoint().createConnectionResource(this);
        }
        return answer;
    }

    /**
     * A pool of {@link MessageConsumerResources} objects that are the reply consumers.
     */
    @Deprecated
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
                if (ObjectHelper.isEmpty(getReplyTo())) {
                    isReplyToTopic = isTopic();
                    replyToDestination = getEndpoint().getDestinationCreationStrategy().createTemporaryDestination(session,
                            isReplyToTopic);
                } else {
                    isReplyToTopic = DestinationNameParser.isReplyToTopic(getReplyTo(), isTopic());
                    replyToDestination = getEndpoint().getDestinationCreationStrategy().createDestination(session,
                            getReplyTo(), isReplyToTopic);
                }
                MessageConsumer messageConsumer = getEndpoint().getJmsObjectFactory().createMessageConsumer(session,
                        replyToDestination, null, isReplyToTopic, null, true, false, false);
                messageConsumer.setMessageListener(new MessageListener() {
                    @Override
                    public void onMessage(final Message message) {
                        LOG.debug("Message Received in the Consumer Pool");
                        LOG.debug("  Message : {}", message);
                        try {
                            String correlationID = message.getJMSCorrelationID();
                            Exchanger<Object> exchanger = EXCHANGERS.get(correlationID);
                            if (exchanger != null) {
                                exchanger.exchange(message, getEndpoint().getRequestTimeout(), TimeUnit.MILLISECONDS);
                            } else {
                                // we could not correlate the received reply message to a matching request and therefore
                                // we cannot continue routing the unknown message
                                // log a warn and then ignore the message
                                LOG.warn(
                                        "Reply received for unknown correlationID [{}] on reply destination [{}]. Current correlation map size: {}. The message will be ignored: {}",
                                        correlationID, replyToDestination, EXCHANGERS.size(), message);
                            }
                        } catch (Exception e) {
                            LOG.warn("Unable to match correlated exchange message: {}. This exception is ignored.", message, e);
                        }
                    }
                });
                answer = new MessageConsumerResources(session, messageConsumer, replyToDestination);
            } catch (Exception e) {
                LOG.error("Unable to create the MessageConsumerResource: {}", e.getMessage());
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
                LOG.debug("Cannot validate session. This exception is ignored.", ex);
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
            throw new IllegalArgumentException(
                    "InOut exchange pattern is incompatible with transacted=true as it cause a deadlock. Please use transacted=false or InOnly exchange pattern.");
        }

        if (ObjectHelper.isEmpty(getReplyTo())) {
            LOG.debug("No reply to destination is defined. Using temporary destinations.");
        } else {
            LOG.debug("Using {} as the reply to destination.", getReplyTo());
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
            try {
                consumers.close();
            } catch (Exception e) {
                // ignore
            }
            consumers = null;
        }
    }

    @Override
    protected void sendMessage(Exchange exchange, Session session, String destinationName) {
        try {
            template.execute(session, sc -> {
                MessageProducer producer = null;
                try {
                    Message request = getEndpoint().getBinding().makeJmsMessage(exchange, sc);

                    String correlationId = exchange.getIn().getHeader(JmsConstants.JMS_CORRELATION_ID, String.class);
                    if (correlationId == null) {
                        // we append the 'Camel-' prefix to know it was generated by us
                        correlationId = GENERATED_CORRELATION_ID_PREFIX + getUuidGenerator().generateUuid();
                    }

                    // the request timeout can be overruled by a header otherwise the endpoint configured value is used
                    final long timeout
                            = exchange.getIn().getHeader(SjmsConstants.JMS_REQUEST_TIMEOUT, getEndpoint().getRequestTimeout(),
                                    long.class);

                    Object responseObject = null;
                    Exchanger<Object> messageExchanger = new Exchanger<>();
                    JmsMessageHelper.setCorrelationId(request, correlationId);
                    EXCHANGERS.put(correlationId, messageExchanger);

                    MessageConsumerResources consumer = consumers.borrowObject();
                    JmsMessageHelper.setJMSReplyTo(request, consumer.getReplyToDestination());
                    consumers.returnObject(consumer);

                    producer = getEndpoint().getJmsObjectFactory().createMessageProducer(sc, getEndpoint(), destinationName);
                    template.send(producer, request);

                    try {
                        responseObject = messageExchanger.exchange(null, timeout, TimeUnit.MILLISECONDS);
                        EXCHANGERS.remove(correlationId);
                    } catch (Throwable e) {
                        exchange.setException(e);
                    }

                    if (exchange.getException() == null) {
                        if (responseObject instanceof Throwable) {
                            exchange.setException((Throwable) responseObject);
                        } else if (responseObject instanceof Message) {
                            Message message = (Message) responseObject;

                            SjmsMessage response
                                    = new SjmsMessage(exchange, message, consumer.getSession(), getEndpoint().getBinding());
                            // the JmsBinding is designed to be "pull-based": it will populate the Camel message on demand
                            // therefore, we link Exchange and OUT message before continuing, so that the JmsBinding has full access
                            // to everything it may need, and can populate headers, properties, etc. accordingly (solves CAMEL-6218).
                            exchange.setOut(response);
                        } else {
                            exchange.setException(new CamelException("Unknown response type: " + responseObject));
                        }
                    }

                } finally {
                    closeProducer(producer);
                }
                return null;
            });
        } catch (Exception e) {
            exchange.setException(new CamelExchangeException("Unable to complete sending the JMS message", exchange, e));
        }
    }

}
