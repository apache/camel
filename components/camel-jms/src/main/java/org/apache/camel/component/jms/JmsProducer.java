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
package org.apache.camel.component.jms;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.component.jms.JmsConfiguration.CamelJmsTeemplate102;
import org.apache.camel.component.jms.JmsConfiguration.CamelJmsTemplate;
import org.apache.camel.component.jms.requestor.DeferredRequestReplyMap;
import org.apache.camel.component.jms.requestor.DeferredRequestReplyMap.DeferredMessageSentCallback;
import org.apache.camel.component.jms.requestor.PersistentReplyToRequestor;
import org.apache.camel.component.jms.requestor.Requestor;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.UuidGenerator;
import org.apache.camel.util.ValueHolder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.MessageCreator;

/**
 * @version $Revision$
 */
public class JmsProducer extends DefaultProducer {
    private static final transient Log LOG = LogFactory.getLog(JmsProducer.class);
    private RequestorAffinity affinity;
    private final JmsEndpoint endpoint;
    private JmsOperations inOnlyTemplate;
    private JmsOperations inOutTemplate;
    private UuidGenerator uuidGenerator;
    private DeferredRequestReplyMap deferredRequestReplyMap;
    private Requestor requestor;
    private AtomicBoolean started = new AtomicBoolean(false);

    private enum RequestorAffinity {
        PER_COMPONENT(0),
        PER_ENDPOINT(1),
        PER_PRODUCER(2);
        private int value;
        private RequestorAffinity(int value) {
            this.value = value;
        }
    }

    public JmsProducer(JmsEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        JmsConfiguration c = endpoint.getConfiguration();
        affinity = RequestorAffinity.PER_PRODUCER;
        if (c.getReplyTo() != null) {
            if (c.getReplyToTempDestinationAffinity().equals(JmsConfiguration.REPLYTO_TEMP_DEST_AFFINITY_PER_ENDPOINT)) {
                affinity = RequestorAffinity.PER_ENDPOINT;
            } else if (c.getReplyToTempDestinationAffinity().equals(JmsConfiguration.REPLYTO_TEMP_DEST_AFFINITY_PER_COMPONENT)) {
                affinity = RequestorAffinity.PER_COMPONENT;
            }
        }
    }

    public long getRequestTimeout() {
        return endpoint.getConfiguration().getRequestTimeout();
    }

    protected void doStart() throws Exception {
        super.doStart();
    }

    protected void testAndSetRequestor() throws RuntimeCamelException {
        if (!started.get()) {
            synchronized (this) {
                if (started.get()) {
                    return;
                }
                try {
                    JmsConfiguration c = endpoint.getConfiguration();
                    if (c.getReplyTo() != null) {
                        requestor = new PersistentReplyToRequestor(endpoint.getConfiguration(), endpoint.getExecutorService());
                        requestor.start();
                    } else {
                        if (affinity == RequestorAffinity.PER_PRODUCER) {
                            requestor = new Requestor(endpoint.getConfiguration(), endpoint.getExecutorService());
                            requestor.start();
                        } else if (affinity == RequestorAffinity.PER_ENDPOINT) {
                            requestor = endpoint.getRequestor();
                        } else if (affinity == RequestorAffinity.PER_COMPONENT) {
                            requestor = ((JmsComponent)endpoint.getComponent()).getRequestor();
                        }
                    }
                } catch (Exception e) {
                    throw new FailedToCreateProducerException(endpoint, e);
                }
                deferredRequestReplyMap = requestor.getDeferredRequestReplyMap(this);
                started.set(true);
            }
        }
    }

    protected void testAndUnsetRequestor() throws Exception  {
        if (started.get()) {
            synchronized (this) {
                if (!started.get()) {
                    return;
                }
                requestor.removeDeferredRequestReplyMap(this);
                if (affinity == RequestorAffinity.PER_PRODUCER) {
                    requestor.stop();
                }
                started.set(false);
            }
        }
    }

    protected void doStop() throws Exception {
        testAndUnsetRequestor();
        super.doStop();
    }

    public void process(final Exchange exchange) {
        final org.apache.camel.Message in = exchange.getIn();

        String destinationName = endpoint.getDestinationName();
        Destination destination = exchange.getProperty(JmsConstants.JMS_DESTINATION, Destination.class);
        if (destination == null) {
            destination = endpoint.getDestination();
        }
        if (exchange.getPattern().isOutCapable()) {

            testAndSetRequestor();

            // note due to JMS transaction semantics we cannot use a single transaction
            // for sending the request and receiving the response
            final Destination replyTo = requestor.getReplyTo();

            if (replyTo == null) {
                throw new RuntimeExchangeException("Failed to resolve replyTo destination", exchange);
            }

            final boolean msgIdAsCorrId = endpoint.getConfiguration().isUseMessageIDAsCorrelationID();
            String correlationId = in.getHeader("JMSCorrelationID", String.class);

            if (correlationId == null && !msgIdAsCorrId) {
                in.setHeader("JMSCorrelationID", getUuidGenerator().generateId());
            }

            final ValueHolder<FutureTask> futureHolder = new ValueHolder<FutureTask>();
            final DeferredMessageSentCallback callback = msgIdAsCorrId ? deferredRequestReplyMap.createDeferredMessageSentCallback() : null;

            MessageCreator messageCreator = new MessageCreator() {
                public Message createMessage(Session session) throws JMSException {
                    Message message = endpoint.getBinding().makeJmsMessage(exchange, in, session, null);
                    message.setJMSReplyTo(replyTo);
                    requestor.setReplyToSelectorHeader(in, message);

                    FutureTask future;
                    future = (!msgIdAsCorrId)
                            ? requestor.getReceiveFuture(message.getJMSCorrelationID(), endpoint.getConfiguration().getRequestTimeout())
                            : requestor.getReceiveFuture(callback);

                    futureHolder.set(future);

                    if (LOG.isDebugEnabled()) {
                        LOG.debug(endpoint + " sending JMS message: " + message);
                    }
                    return message;
                }
            };

            CamelJmsTemplate template = null;
            CamelJmsTeemplate102 template102 = null;
            if (endpoint.isUseVersion102()) {
                template102 = (CamelJmsTeemplate102)getInOutTemplate();
            } else {
                template = (CamelJmsTemplate)getInOutTemplate();
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Using JMS API " + (endpoint.isUseVersion102() ? "v1.0.2" : "v1.1"));
            }

            if (destinationName != null) {
                if (template != null) {
                    template.send(destinationName, messageCreator, callback);
                } else {
                    template102.send(destinationName, messageCreator, callback);
                }
            } else if (destination != null) {
                if (template != null) {
                    template.send(destination, messageCreator, callback);
                } else {
                    template102.send(destination, messageCreator, callback);
                }
            } else {
                throw new IllegalArgumentException("Neither destination nor destinationName is specified on this endpoint: " + endpoint);
            }

            setMessageId(exchange);

            // lets wait and return the response
            long requestTimeout = endpoint.getConfiguration().getRequestTimeout();
            try {
                Message message = null;
                try {
                    if (requestTimeout < 0) {
                        message = (Message)futureHolder.get().get();
                    } else {
                        message = (Message)futureHolder.get().get(requestTimeout, TimeUnit.MILLISECONDS);
                    }
                } catch (InterruptedException e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Future interupted: " + e, e);
                    }
                } catch (TimeoutException e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Future timed out: " + e, e);
                    }
                }
                if (message != null) {
                    // the response can be an exception
                    JmsMessage response = new JmsMessage(message, endpoint.getBinding());
                    Object body = response.getBody();

                    if (endpoint.isTransferException() && body instanceof Exception) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Reply recieved. Setting reply as Exception: " + body);
                        }
                        // we got an exception back and endpoint was configued to transfer exception
                        // therefore set response as exception
                        exchange.setException((Exception) body);
                    } else {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Reply recieved. Setting reply as OUT message: " + body);
                        }
                        // regular response
                        exchange.setOut(response);
                    }

                    // correlation
                    if (correlationId != null) {
                        message.setJMSCorrelationID(correlationId);
                        exchange.getOut(false).setHeader("JMSCorrelationID", correlationId);
                    }
                } else {
                    // no response, so lets set a timed out exception
                    exchange.setException(new ExchangeTimedOutException(exchange, requestTimeout));
                }
            } catch (Exception e) {
                exchange.setException(e);
            }
        } else {
            MessageCreator messageCreator = new MessageCreator() {
                public Message createMessage(Session session) throws JMSException {
                    Message message = endpoint.getBinding().makeJmsMessage(exchange, in, session, null);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(endpoint + " sending JMS message: " + message);
                    }
                    return message;
                }
            };
            if (destination != null) {
                getInOnlyTemplate().send(destination, messageCreator);
            } else if (destinationName != null) {
                getInOnlyTemplate().send(destinationName, messageCreator);
            } else  {
                throw new IllegalArgumentException("Neither destination nor destinationName is specified on this endpoint: " + endpoint);
            }

            setMessageId(exchange);
        }
    }

    protected void setMessageId(Exchange exchange) {
        if (!(exchange instanceof JmsExchange)) {
            return;
        }
        try {
            JmsExchange jmsExchange = JmsExchange.class.cast(exchange);
            JmsMessage out = jmsExchange.getOut(false);
            if (out != null) {
                out.setMessageId(out.getJmsMessage().getJMSMessageID());
            }
        } catch (JMSException e) {
            LOG.warn("Unable to retrieve JMSMessageID from outgoing JMS Message and set it into Camel's MessageId", e);
        }
    }

    public JmsOperations getInOnlyTemplate() {
        if (inOnlyTemplate == null) {
            inOnlyTemplate = endpoint.createInOnlyTemplate();
        }
        return inOnlyTemplate;
    }

    public void setInOnlyTemplate(JmsOperations inOnlyTemplate) {
        this.inOnlyTemplate = inOnlyTemplate;
    }

    public JmsOperations getInOutTemplate() {
        if (inOutTemplate == null) {
            inOutTemplate = endpoint.createInOutTemplate();
        }
        return inOutTemplate;
    }

    public void setInOutTemplate(JmsOperations inOutTemplate) {
        this.inOutTemplate = inOutTemplate;
    }

    public UuidGenerator getUuidGenerator() {
        if (uuidGenerator == null) {
            uuidGenerator = new UuidGenerator();
        }
        return uuidGenerator;
    }

    public void setUuidGenerator(UuidGenerator uuidGenerator) {
        this.uuidGenerator = uuidGenerator;
    }
}
