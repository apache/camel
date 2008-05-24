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
    RequestorAffinity affinity;
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
    };

    public JmsProducer(JmsEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        JmsConfiguration c = endpoint.getConfiguration();
        affinity = RequestorAffinity.PER_PRODUCER;
        if (c.getReplyTo() != null) {
            if (c.getReplyToTempDestinationAffinity().equals(c.REPLYTO_TEMP_DEST_AFFINITY_PER_ENDPOINT)) {
                affinity = RequestorAffinity.PER_ENDPOINT;
            } else if (c.getReplyToTempDestinationAffinity().equals(c.REPLYTO_TEMP_DEST_AFFINITY_PER_COMPONENT)) {
                affinity = RequestorAffinity.PER_COMPONENT;
            }
        }
    }

    public long getRequestTimeout() {
        return endpoint.getRequestTimeout();
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
                        requestor = new PersistentReplyToRequestor(endpoint.getConfiguration(), endpoint
                            .getExecutorService());
                        requestor.start();
                    } else {
                        if (affinity == RequestorAffinity.PER_PRODUCER) {
                            requestor = new Requestor(endpoint.getConfiguration(), endpoint
                                .getExecutorService());
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

            final CamelJmsTemplate template = (CamelJmsTemplate)getInOutTemplate();
            template.send(endpoint.getDestination(), new MessageCreator() {
                public Message createMessage(Session session) throws JMSException {
                    Message message = endpoint.getBinding().makeJmsMessage(exchange, in, session);
                    message.setJMSReplyTo(replyTo);
                    requestor.setReplyToSelectorHeader(in, message);

                    FutureTask future = null;
                    future = (!msgIdAsCorrId)
                        ? requestor.getReceiveFuture(message.getJMSCorrelationID(), endpoint
                            .getRequestTimeout()) : requestor.getReceiveFuture(callback);

                    futureHolder.set(future);

                    if (LOG.isDebugEnabled()) {
                        LOG.debug(endpoint + " sending JMS message: " + message);
                    }
                    return message;
                }
            }, callback);

            setMessageId(exchange);

            // lets wait and return the response
            long requestTimeout = endpoint.getRequestTimeout();
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
                    exchange.setOut(new JmsMessage(message, endpoint.getBinding()));
                    if (correlationId != null) {
                        message.setJMSCorrelationID(correlationId);
                        exchange.getOut(false).setHeader("JMSCorrelationID", correlationId);
                    }
                } else {
                    // lets set a timed out exception
                    exchange.setException(new ExchangeTimedOutException(exchange, requestTimeout));
                }
            } catch (Exception e) {
                exchange.setException(e);
            }
        } else {
            getInOnlyTemplate().send(endpoint.getDestination(), new MessageCreator() {
                public Message createMessage(Session session) throws JMSException {
                    Message message = endpoint.getBinding().makeJmsMessage(exchange, in, session);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(endpoint + " sending JMS message: " + message);
                    }
                    return message;
                }
            });

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
            LOG.warn("Unable to retrieve JMSMessageID from outgoing JMS Message and "
                     + "set it into Camel's MessageId", e);
        }
    }

    /**
     * Preserved for backwards compatibility.
     *
     * @deprecated
     * @see #getInOnlyTemplate()
     */
    public JmsOperations getTemplate() {
        return getInOnlyTemplate();
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
