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

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.component.jms.JmsConfiguration.CamelJmsTemplate;
import org.apache.camel.component.jms.JmsConfiguration.CamelJmsTemplate102;
import org.apache.camel.component.jms.requestor.DeferredRequestReplyMap;
import org.apache.camel.component.jms.requestor.DeferredRequestReplyMap.DeferredMessageSentCallback;
import org.apache.camel.component.jms.requestor.PersistentReplyToRequestor;
import org.apache.camel.component.jms.requestor.Requestor;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.util.UuidGenerator;
import org.apache.camel.util.ValueHolder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.MessageCreator;

/**
 * @version $Revision$
 */
public class JmsProducer extends DefaultAsyncProducer {
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
                        requestor = new PersistentReplyToRequestor(endpoint.getConfiguration(), endpoint.getRequestorExecutorService());
                        requestor.start();
                    } else {
                        if (affinity == RequestorAffinity.PER_PRODUCER) {
                            requestor = new Requestor(endpoint.getConfiguration(), endpoint.getRequestorExecutorService());
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

    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (!endpoint.isDisableReplyTo() && exchange.getPattern().isOutCapable()) {
            // in out requires a bit more work than in only
            return processInOut(exchange, callback);
        } else {
            // in only
            return processInOnly(exchange, callback);
        }
    }

    protected boolean processInOut(final Exchange exchange, final AsyncCallback callback) {
        final org.apache.camel.Message in = exchange.getIn();

        String destinationName = in.getHeader(JmsConstants.JMS_DESTINATION_NAME, String.class);
        // remove the header so it wont be propagated
        in.removeHeader(JmsConstants.JMS_DESTINATION_NAME);
        if (destinationName == null) {
            destinationName = endpoint.getDestinationName();
        }

        Destination destination = in.getHeader(JmsConstants.JMS_DESTINATION, Destination.class);
        // remove the header so it wont be propagated
        in.removeHeader(JmsConstants.JMS_DESTINATION);
        if (destination == null) {
            destination = endpoint.getDestination();
        }
        if (destination != null) {
            // prefer to use destination over destination name
            destinationName = null;
        }

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
            in.setHeader("JMSCorrelationID", getUuidGenerator().generateUuid());
        }

        final ValueHolder<FutureTask> futureHolder = new ValueHolder<FutureTask>();
        final DeferredMessageSentCallback jmsCallback = msgIdAsCorrId ? deferredRequestReplyMap.createDeferredMessageSentCallback() : null;

        MessageCreator messageCreator = new MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                Message message = endpoint.getBinding().makeJmsMessage(exchange, in, session, null);
                message.setJMSReplyTo(replyTo);
                requestor.setReplyToSelectorHeader(in, message);

                FutureTask future;
                future = (!msgIdAsCorrId)
                        ? requestor.getReceiveFuture(message.getJMSCorrelationID(), endpoint.getConfiguration().getRequestTimeout())
                        : requestor.getReceiveFuture(jmsCallback);

                futureHolder.set(future);
                return message;
            }
        };

        doSend(true, destinationName, destination, messageCreator, jmsCallback);

        // after sending then set the OUT message id to the JMSMessageID so its identical
        setMessageId(exchange);

        // now we should routing asynchronously to not block while waiting for the reply
        // TODO:
        // we need a thread pool to use for continue routing messages, just like a seda consumer
        // and we need options to configure it as well so you can indicate how many threads to use
        // TODO: Also consider requestTimeout

        // lets wait and return the response
        long requestTimeout = endpoint.getConfiguration().getRequestTimeout();
        try {
            Message message = null;
            try {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Message sent, now waiting for reply at: " + replyTo.toString());
                }
                if (requestTimeout <= 0) {
                    message = (Message)futureHolder.get().get();
                } else {
                    message = (Message)futureHolder.get().get(requestTimeout, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Future interrupted: " + e, e);
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
                        LOG.debug("Reply received. Setting reply as an Exception: " + body);
                    }
                    // we got an exception back and endpoint was configured to transfer exception
                    // therefore set response as exception
                    exchange.setException((Exception) body);
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Reply received. Setting reply as OUT message: " + body);
                    }
                    // regular response
                    exchange.setOut(response);
                }

                // restore correlation id in case the remote server messed with it
                if (correlationId != null) {
                    message.setJMSCorrelationID(correlationId);
                    exchange.getOut().setHeader("JMSCorrelationID", correlationId);
                }
            } else {
                // no response, so lets set a timed out exception
                exchange.setException(new ExchangeTimedOutException(exchange, requestTimeout));
            }
        } catch (Exception e) {
            exchange.setException(e);
        }

        // TODO: should be async
        callback.done(true);
        return true;
    }

    protected boolean processInOnly(final Exchange exchange, final AsyncCallback callback) {
        final org.apache.camel.Message in = exchange.getIn();

        String destinationName = in.getHeader(JmsConstants.JMS_DESTINATION_NAME, String.class);
        if (destinationName != null) {
            // remove the header so it wont be propagated
            in.removeHeader(JmsConstants.JMS_DESTINATION_NAME);
        }
        if (destinationName == null) {
            destinationName = endpoint.getDestinationName();
        }

        Destination destination = in.getHeader(JmsConstants.JMS_DESTINATION, Destination.class);
        if (destination != null) {
            // remove the header so it wont be propagated
            in.removeHeader(JmsConstants.JMS_DESTINATION);
        }
        if (destination == null) {
            destination = endpoint.getDestination();
        }
        if (destination != null) {
            // prefer to use destination over destination name
            destinationName = null;
        }

        // we must honor these special flags to preserve QoS
        if (!endpoint.isPreserveMessageQos() && !endpoint.isExplicitQosEnabled()) {
            Object replyTo = exchange.getIn().getHeader("JMSReplyTo");
            if (replyTo != null) {
                // we are routing an existing JmsMessage, origin from another JMS endpoint
                // then we need to remove the existing JMSReplyTo
                // as we are not out capable and thus do not expect a reply, and therefore
                // the consumer of this message we send should not return a reply
                String to = destinationName != null ? destinationName : "" + destination;
                LOG.warn("Disabling JMSReplyTo as this Exchange is not OUT capable with JMSReplyTo: " + replyTo
                        + " for destination: " + to + ". Use preserveMessageQos=true to force Camel to keep the JMSReplyTo."
                        + " Exchange: " + exchange);
                exchange.getIn().setHeader("JMSReplyTo", null);
            }
        }

        MessageCreator messageCreator = new MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                return endpoint.getBinding().makeJmsMessage(exchange, in, session, null);
            }
        };

        doSend(false, destinationName, destination, messageCreator, null);

        // after sending then set the OUT message id to the JMSMessageID so its identical
        setMessageId(exchange);

        // we are synchronous so return true
        callback.done(true);
        return true;
    }

    /**
     * Sends the message using the JmsTemplate.
     *
     * @param inOut  use inOut or inOnly template
     * @param destinationName the destination name
     * @param destination     the destination (if no name provided)
     * @param messageCreator  the creator to create the javax.jms.Message to send
     * @param callback        optional callback for inOut messages
     */
    protected void doSend(boolean inOut, String destinationName, Destination destination,
                          MessageCreator messageCreator, DeferredMessageSentCallback callback) {

        CamelJmsTemplate template = null;
        CamelJmsTemplate102 template102 = null;
        if (endpoint.isUseVersion102()) {
            template102 = (JmsConfiguration.CamelJmsTemplate102) (inOut ? getInOutTemplate() : getInOnlyTemplate());
        } else {
            template = (CamelJmsTemplate) (inOut ? getInOutTemplate() : getInOnlyTemplate());
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Using " + (inOut ? "inOut" : "inOnly") + " jms template to send with API "
                    + (endpoint.isUseVersion102() ? "v1.0.2" : "v1.1"));
        }

        // destination should be preferred
        if (destination != null) {
            if (inOut) {
                if (template != null) {
                    template.send(destination, messageCreator, callback);
                } else if (template102 != null) {
                    template102.send(destination, messageCreator, callback);
                }
            } else {
                if (template != null) {
                    template.send(destination, messageCreator);
                } else if (template102 != null) {
                    template102.send(destination, messageCreator);
                }
            }
        } else if (destinationName != null) {
            if (inOut) {
                if (template != null) {
                    template.send(destinationName, messageCreator, callback);
                } else if (template102 != null) {
                    template102.send(destinationName, messageCreator, callback);
                }
            } else {
                if (template != null) {
                    template.send(destinationName, messageCreator);
                } else if (template102 != null) {
                    template102.send(destinationName, messageCreator);
                }
            }
        } else {
            throw new IllegalArgumentException("Neither destination nor destinationName is specified on this endpoint: " + endpoint);
        }
    }

    protected void setMessageId(Exchange exchange) {
        if (exchange.hasOut()) {
            JmsMessage out = (JmsMessage) exchange.getOut();
            try {
                if (out != null && out.getJmsMessage() != null) {
                    out.setMessageId(out.getJmsMessage().getJMSMessageID());
                }
            } catch (JMSException e) {
                LOG.warn("Unable to retrieve JMSMessageID from outgoing "
                    + "JMS Message and set it into Camel's MessageId", e);
            }
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
            uuidGenerator = UuidGenerator.get();
        }
        return uuidGenerator;
    }

    public void setUuidGenerator(UuidGenerator uuidGenerator) {
        this.uuidGenerator = uuidGenerator;
    }

}
