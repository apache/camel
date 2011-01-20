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

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.component.jms.JmsConfiguration.CamelJmsTemplate;
import org.apache.camel.component.jms.reply.ReplyManager;
import org.apache.camel.component.jms.reply.UseMessageIdAsCorrelationIdMessageSentCallback;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ValueHolder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.MessageCreator;

import static org.apache.camel.component.jms.JmsMessageHelper.normalizeDestinationName;

/**
 * @version $Revision$
 */
public class JmsProducer extends DefaultAsyncProducer {
    private static final transient Log LOG = LogFactory.getLog(JmsProducer.class);
    private final JmsEndpoint endpoint;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private JmsOperations inOnlyTemplate;
    private JmsOperations inOutTemplate;
    private UuidGenerator uuidGenerator;
    private ReplyManager replyManager;

    public JmsProducer(JmsEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    protected void initReplyManager() {
        if (!started.get()) {
            synchronized (this) {
                if (started.get()) {
                    return;
                }
                try {
                    if (endpoint.getReplyTo() != null) {
                        replyManager = endpoint.getReplyManager(endpoint.getReplyTo());
                        LOG.info("Using JmsReplyManager: " + replyManager + " to process replies from: " + endpoint.getReplyTo());
                    } else {
                        replyManager = endpoint.getReplyManager();
                        LOG.info("Using JmsReplyManager: " + replyManager + " to process replies from temporary queue");
                    }
                } catch (Exception e) {
                    throw new FailedToCreateProducerException(endpoint, e);
                }
                started.set(true);
            }
        }
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        // deny processing if we are not started
        if (!isRunAllowed()) {
            if (exchange.getException() == null) {
                exchange.setException(new RejectedExecutionException());
            }
            // we cannot process so invoke callback
            callback.done(true);
            return true;
        }

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

        initReplyManager();

        // when using message id as correlation id, we need at first to use a provisional correlation id
        // which we then update to the real JMSMessageID when the message has been sent
        // this is done with the help of the MessageSentCallback
        final boolean msgIdAsCorrId = endpoint.getConfiguration().isUseMessageIDAsCorrelationID();
        final String provisionalCorrelationId = msgIdAsCorrId ? getUuidGenerator().generateUuid() : null;
        MessageSentCallback messageSentCallback = null;
        if (msgIdAsCorrId) {
            messageSentCallback = new UseMessageIdAsCorrelationIdMessageSentCallback(replyManager, provisionalCorrelationId, endpoint.getRequestTimeout());
        }
        final ValueHolder<MessageSentCallback> sentCallback = new ValueHolder<MessageSentCallback>(messageSentCallback);

        final String originalCorrelationId = in.getHeader("JMSCorrelationID", String.class);
        if (originalCorrelationId == null && !msgIdAsCorrId) {
            in.setHeader("JMSCorrelationID", getUuidGenerator().generateUuid());
        }

        MessageCreator messageCreator = new MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                Message message = endpoint.getBinding().makeJmsMessage(exchange, in, session, null);

                // get the reply to destination to be used from the reply manager
                Destination replyTo = replyManager.getReplyTo();
                if (replyTo == null) {
                    throw new RuntimeExchangeException("Failed to resolve replyTo destination", exchange);
                }
                message.setJMSReplyTo(replyTo);
                replyManager.setReplyToSelectorHeader(in, message);

                String correlationId = determineCorrelationId(message, provisionalCorrelationId);
                replyManager.registerReply(replyManager, exchange, callback, originalCorrelationId, correlationId, endpoint.getRequestTimeout());

                return message;
            }
        };

        doSend(true, destinationName, destination, messageCreator, sentCallback.get());

        // after sending then set the OUT message id to the JMSMessageID so its identical
        setMessageId(exchange);

        // continue routing asynchronously (reply will be processed async when its received)
        return false;
    }

    /**
     * Strategy to determine which correlation id to use among <tt>JMSMessageID</tt> and <tt>JMSCorrelationID</tt>.
     *
     * @param message   the JMS message
     * @param provisionalCorrelationId an optional provisional correlation id, which is preferred to be used
     * @return the correlation id to use
     * @throws JMSException can be thrown
     */
    protected String determineCorrelationId(Message message, String provisionalCorrelationId) throws JMSException {
        if (provisionalCorrelationId != null) {
            return provisionalCorrelationId;
        }

        final String messageId = message.getJMSMessageID();
        final String correlationId = message.getJMSCorrelationID();
        if (endpoint.getConfiguration().isUseMessageIDAsCorrelationID()) {
            return messageId;
        } else if (ObjectHelper.isEmpty(correlationId)) {
            // correlation id is empty so fallback to message id
            return messageId;
        } else {
            return correlationId;
        }
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
        final String to = destinationName != null ? destinationName : "" + destination;

        MessageCreator messageCreator = new MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                Message answer = endpoint.getBinding().makeJmsMessage(exchange, in, session, null);

                // when in InOnly mode the JMSReplyTo is a bit complicated
                // we only want to set the JMSReplyTo on the answer if
                // there is a JMSReplyTo from the header/endpoint and
                // we have been told to preserveMessageQos

                Object jmsReplyTo = answer.getJMSReplyTo();
                if (endpoint.isDisableReplyTo()) {
                    // honor disable reply to configuration
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("ReplyTo is disabled on endpoint: " + endpoint);
                    }
                    answer.setJMSReplyTo(null);
                } else {
                    // if the binding did not create the reply to then we have to try to create it here
                    if (jmsReplyTo == null) {
                        // prefer reply to from header over endpoint configured
                        jmsReplyTo = exchange.getIn().getHeader("JMSReplyTo", String.class);
                        if (jmsReplyTo == null) {
                            jmsReplyTo = endpoint.getReplyTo();
                        }
                    }
                }

                // we must honor these special flags to preserve QoS
                // as we are not OUT capable and thus do not expect a reply, and therefore
                // the consumer of this message should not return a reply so we remove it
                // unless we use preserveMessageQos=true to tell that we still want to use JMSReplyTo
                if (jmsReplyTo != null && !(endpoint.isPreserveMessageQos() || endpoint.isExplicitQosEnabled())) {
                    LOG.warn("Disabling JMSReplyTo: " + jmsReplyTo + " for destination: " + to
                        + ". Use preserveMessageQos=true to force Camel to keep the JMSReplyTo on endpoint: " + endpoint);
                    jmsReplyTo = null;
                }

                // the reply to is a String, so we need to look up its Destination instance
                // and if needed create the destination using the session if needed to
                if (jmsReplyTo != null && jmsReplyTo instanceof String) {
                    // must normalize the destination name
                    String replyTo = normalizeDestinationName((String) jmsReplyTo);
                    // we need to null it as we use the String to resolve it as a Destination instance
                    jmsReplyTo = null;

                    // try using destination resolver to lookup the destination
                    if (endpoint.getDestinationResolver() != null) {
                        jmsReplyTo = endpoint.getDestinationResolver().resolveDestinationName(session, replyTo, endpoint.isPubSubDomain());
                    }
                    if (jmsReplyTo == null) {
                        // okay then fallback and create the queue
                        if (endpoint.isPubSubDomain()) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Creating JMSReplyTo topic: " + replyTo);
                            }
                            jmsReplyTo = session.createTopic(replyTo);
                        } else {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Creating JMSReplyTo queue: " + replyTo);
                            }
                            jmsReplyTo = session.createQueue(replyTo);
                        }
                    }
                }

                // set the JMSReplyTo on the answer if we are to use it
                Destination replyTo = null;
                if (jmsReplyTo instanceof Destination) {
                    replyTo = (Destination) jmsReplyTo;
                }
                if (replyTo != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Using JMSReplyTo destination: " + replyTo);
                    }
                    answer.setJMSReplyTo(replyTo);
                } else {
                    // do not use JMSReplyTo
                    answer.setJMSReplyTo(null);
                }

                return answer;
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
     * @param inOut           use inOut or inOnly template
     * @param destinationName the destination name
     * @param destination     the destination (if no name provided)
     * @param messageCreator  the creator to create the {@link Message} to send
     * @param callback        optional callback for inOut messages
     */
    protected void doSend(boolean inOut, String destinationName, Destination destination,
                          MessageCreator messageCreator, MessageSentCallback callback) {

        CamelJmsTemplate template = (CamelJmsTemplate) (inOut ? getInOutTemplate() : getInOnlyTemplate());

        if (LOG.isTraceEnabled()) {
            LOG.trace("Using " + (inOut ? "inOut" : "inOnly") + " jms template");
        }

        // destination should be preferred
        if (destination != null) {
            if (inOut) {
                if (template != null) {
                    template.send(destination, messageCreator, callback);
                }
            } else {
                if (template != null) {
                    template.send(destination, messageCreator);
                }
            }
        } else if (destinationName != null) {
            if (inOut) {
                if (template != null) {
                    template.send(destinationName, messageCreator, callback);
                }
            } else {
                if (template != null) {
                    template.send(destinationName, messageCreator);
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
        return uuidGenerator;
    }

    public void setUuidGenerator(UuidGenerator uuidGenerator) {
        this.uuidGenerator = uuidGenerator;
    }

    protected void doStart() throws Exception {
        super.doStart();
        if (uuidGenerator == null) {
            // use the generator configured on the camel context
            uuidGenerator = getEndpoint().getCamelContext().getUuidGenerator();
        }
    }

    protected void doStop() throws Exception {
        super.doStop();
    }
}
