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

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Session;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.RollbackExchangeException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.LoggingExceptionHandler;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.MessageCreator;

import static org.apache.camel.util.ObjectHelper.wrapRuntimeCamelException;

/**
 * A JMS {@link MessageListener} which can be used to delegate processing to a
 * Camel endpoint.
 *
 * Note that instance of this object has to be thread safe (reentrant)
 *
 * @version $Revision$
 */
public class EndpointMessageListener implements MessageListener {
    private static final transient Log LOG = LogFactory.getLog(EndpointMessageListener.class);
    private ExceptionHandler exceptionHandler;
    private JmsEndpoint endpoint;
    private Processor processor;
    private JmsBinding binding;
    private boolean eagerLoadingOfProperties;
    private Object replyToDestination;
    private JmsOperations template;
    private boolean disableReplyTo;

    public EndpointMessageListener(JmsEndpoint endpoint, Processor processor) {
        this.endpoint = endpoint;
        this.processor = processor;
        endpoint.getConfiguration().configure(this);
    }

    public void onMessage(final Message message) {
        LOG.trace("onMessage START");

        if (LOG.isDebugEnabled()) {
            LOG.debug(endpoint + " consumer received JMS message: " + message);
        }

        RuntimeCamelException rce = null;
        try {
            Object replyDestination = getReplyToDestination(message);
            final Exchange exchange = createExchange(message, replyDestination);
            if (eagerLoadingOfProperties) {
                exchange.getIn().getHeaders();
            }

            // process the exchange
            if (LOG.isTraceEnabled()) {
                LOG.trace("onMessage.process START");
            }

            String correlationId = message.getJMSCorrelationID();
            if (correlationId != null) {
                LOG.debug("Received Message has JMSCorrelationID [" + correlationId + "]");
            }
            
            processor.process(exchange);
            if (LOG.isTraceEnabled()) {
                LOG.trace("onMessage.process END");
            }

            // get the correct jms message to send as reply
            JmsMessage body = null;
            Exception cause = null;
            boolean sendReply = false;
            if (exchange.isFailed() || exchange.isRollbackOnly()) {
                if (exchange.getException() != null) {
                    // an exception occurred while processing
                    if (endpoint.isTransferException()) {
                        // send the exception as reply
                        body = null;
                        cause = exchange.getException();
                        sendReply = true;
                    } else {
                        // only throw exception if endpoint is not configured to transfer exceptions back to caller
                        // do not send a reply but wrap and rethrow the exception
                        rce = wrapRuntimeCamelException(exchange.getException());
                    }
                } else if (exchange.isRollbackOnly()) {
                    // rollback only so wrap an exception so we can rethrow the exception to cause rollback
                    rce = wrapRuntimeCamelException(new RollbackExchangeException(exchange));
                } else if (exchange.getOut().getBody() != null) {
                    // a fault occurred while processing
                    body = (JmsMessage) exchange.getOut();
                    sendReply = true;
                }
            } else if (exchange.hasOut()) {
                // process OK so get the reply
                body = (JmsMessage) exchange.getOut();
                sendReply = true;
            }

            // send the reply if we got a response and the exchange is out capable
            if (rce == null && sendReply && !disableReplyTo && exchange.getPattern().isOutCapable()) {
                LOG.trace("onMessage.sendReply START");
                if (replyDestination instanceof Destination) {
                    sendReply((Destination)replyDestination, message, exchange, body, cause);
                } else {
                    sendReply((String)replyDestination, message, exchange, body, cause);
                }
                LOG.trace("onMessage.sendReply END");
            }

        } catch (Exception e) {
            rce = wrapRuntimeCamelException(e);
        }

        if (rce != null) {
            handleException(rce);
            if (LOG.isTraceEnabled()) {
                LOG.trace("onMessage END throwing exception: " + rce.getMessage());
            }
            throw rce;
        }

        LOG.trace("onMessage END");
    }

    public Exchange createExchange(Message message, Object replyDestination) {
        Exchange exchange = new DefaultExchange(endpoint, endpoint.getExchangePattern());
        JmsBinding binding = getBinding();
        exchange.setProperty(Exchange.BINDING, binding);
        exchange.setIn(new JmsMessage(message, binding));

        // lets set to an InOut if we have some kind of reply-to destination
        if (replyDestination != null && !disableReplyTo) {
            // only change pattern if not already out capable
            if (!exchange.getPattern().isOutCapable()) {
                exchange.setPattern(ExchangePattern.InOut);
            }
        }
        return exchange;
    }

    // Properties
    // -------------------------------------------------------------------------
    public JmsBinding getBinding() {
        if (binding == null) {
            binding = new JmsBinding(endpoint);
        }
        return binding;
    }

    /**
     * Sets the binding used to convert from a Camel message to and from a JMS
     * message
     *
     * @param binding the binding to use
     */
    public void setBinding(JmsBinding binding) {
        this.binding = binding;
    }

    public ExceptionHandler getExceptionHandler() {
        if (exceptionHandler == null) {
            exceptionHandler = new LoggingExceptionHandler(getClass());
        }
        return exceptionHandler;
    }

    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    public boolean isEagerLoadingOfProperties() {
        return eagerLoadingOfProperties;
    }

    public void setEagerLoadingOfProperties(boolean eagerLoadingOfProperties) {
        this.eagerLoadingOfProperties = eagerLoadingOfProperties;
    }

    public synchronized JmsOperations getTemplate() {
        if (template == null) {
            template = endpoint.createInOnlyTemplate();
        }
        return template;
    }

    public void setTemplate(JmsOperations template) {
        this.template = template;
    }

    public boolean isDisableReplyTo() {
        return disableReplyTo;
    }

    /**
     * Allows the reply-to behaviour to be disabled
     */
    public void setDisableReplyTo(boolean disableReplyTo) {
        this.disableReplyTo = disableReplyTo;
    }

    public Object getReplyToDestination() {
        return replyToDestination;
    }

    /**
     * Provides an explicit reply to destination which overrides
     * any incoming value of {@link Message#getJMSReplyTo()}
     *
     * @param replyToDestination the destination that should be used to send replies to
     * as either a String or {@link javax.jms.Destination} type.
     */
    public void setReplyToDestination(Object replyToDestination) {
        this.replyToDestination = replyToDestination;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    /**
     * Strategy to determine which correlation id to use among <tt>JMSMessageID</tt> and <tt>JMSCorrelationID</tt>.
     *
     * @param message the JMS message
     * @return the correlation id to use
     * @throws JMSException can be thrown
     */
    protected String determineCorrelationId(final Message message) throws JMSException {
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

    protected void sendReply(Destination replyDestination, final Message message, final Exchange exchange,
                             final JmsMessage out, final Exception cause) {
        if (replyDestination == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Cannot send reply message as there is no replyDestination for: " + out);
            }
            return;
        }
        getTemplate().send(replyDestination, new MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                Message reply = endpoint.getBinding().makeJmsMessage(exchange, out, session, cause);
                final String correlationID = determineCorrelationId(message);
                reply.setJMSCorrelationID(correlationID);

                if (LOG.isDebugEnabled()) {
                    LOG.debug(endpoint + " sending reply JMS message [correlationId:" + correlationID + "]: " + reply);
                }
                return reply;
            }
        });
    }

    protected void sendReply(String replyDestination, final Message message, final Exchange exchange,
                             final JmsMessage out, final Exception cause) {
        if (replyDestination == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Cannot send reply message as there is no replyDestination for: " + out);
            }
            return;
        }
        getTemplate().send(replyDestination, new MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                Message reply = endpoint.getBinding().makeJmsMessage(exchange, out, session, cause);
                final String correlationID = determineCorrelationId(message);
                reply.setJMSCorrelationID(correlationID);

                if (LOG.isDebugEnabled()) {
                    LOG.debug(endpoint + " sending reply JMS message [correlationId:" + correlationID + "]: " + reply);
                }
                return reply;
            }
        });
    }

    protected Object getReplyToDestination(Message message) throws JMSException {
        // lets send a response back if we can
        Object destination = getReplyToDestination();
        if (destination == null) {
            try {
                destination = message.getJMSReplyTo();
            } catch (JMSException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Cannot read JMSReplyTo header. Will ignore this exception.", e);
                }
            }
        }
        return destination;
    }

    /**
     * Handles the given exception using the {@link #getExceptionHandler()}
     *
     * @param t the exception to handle
     */
    protected void handleException(Throwable t) {
        getExceptionHandler().handleException(t);
    }

}
