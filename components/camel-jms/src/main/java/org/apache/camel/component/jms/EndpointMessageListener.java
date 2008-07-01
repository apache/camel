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

import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.MessageCreator;

/**
 * A JMS {@link MessageListener} which can be used to delegate processing to a
 * Camel endpoint.
 *
 * Note that instance of this object has to be thread safe (reentrant)
 * 
 * @version $Revision$    ;';;;
 */
public class EndpointMessageListener implements MessageListener {
    private static final transient Log LOG = LogFactory.getLog(EndpointMessageListener.class);
    private JmsEndpoint endpoint;
    private Processor processor;
    private JmsBinding binding;
    private boolean eagerLoadingOfProperties;
    private Destination replyToDestination;
    private JmsOperations template;
    private boolean disableReplyTo;

    public EndpointMessageListener(JmsEndpoint endpoint, Processor processor) {
        this.endpoint = endpoint;
        this.processor = processor;
        endpoint.getConfiguration().configure(this);
    }

    public void onMessage(final Message message) {
        RuntimeCamelException rce = null;
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug(endpoint + " consumer receiving JMS message: " + message);
            }
            Destination replyDestination = getReplyToDestination(message);
            final JmsExchange exchange = createExchange(message, replyDestination);
            if (eagerLoadingOfProperties) {
                exchange.getIn().getHeaders();
            }
            processor.process(exchange);
            final JmsMessage out = exchange.getOut(false);
            if (exchange.getException() != null) {
                rce = new RuntimeCamelException(exchange.getException());
            }
            if (rce == null && out != null && !disableReplyTo) {
                sendReply(replyDestination, message, exchange, out);
            }
        } catch (Exception e) {
            rce = new RuntimeCamelException(e);
        }
        if (rce != null) {
            LOG.warn(endpoint + " consumer caught an exception while processing "
                     + "JMS message: " + message, rce);
            throw rce;
        }
    }

    public JmsExchange createExchange(Message message, Destination replyDestination) {
        JmsExchange exchange = new JmsExchange(endpoint.getCamelContext(), endpoint.getExchangePattern(), getBinding(), message);
        // lets set to an InOut if we have some kind of reply-to destination
        if (replyDestination != null && !disableReplyTo) {
            exchange.setProperty("org.apache.camel.jms.replyDestination", replyDestination);
            exchange.setPattern(ExchangePattern.InOut);
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

    public Destination getReplyToDestination() {
        return replyToDestination;
    }

    /**
     * Provides an explicit reply to destination which overrides
     * any incoming value of {@link Message#getJMSReplyTo()}
     *
     * @param replyToDestination the destination that should be used to send replies to
     */
    public void setReplyToDestination(Destination replyToDestination) {
        this.replyToDestination = replyToDestination;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected void sendReply(Destination replyDestination, final Message message, final JmsExchange exchange, final JmsMessage out) {
        if (replyDestination == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Cannot send reply message as there is no replyDestination for: " + out);
            }
            return;
        }
        getTemplate().send(replyDestination, new MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                Message reply = endpoint.getBinding().makeJmsMessage(exchange, out, session);

                if (endpoint.getConfiguration().isUseMessageIDAsCorrelationID()) {
                    String messageID = exchange.getIn().getHeader("JMSMessageID", String.class);
                    reply.setJMSCorrelationID(messageID);
                } else {
                    String correlationID = message.getJMSCorrelationID();
                    if (correlationID != null) {
                        reply.setJMSCorrelationID(correlationID);
                    }
                }

                if (LOG.isDebugEnabled()) {
                    LOG.debug(endpoint + " sending reply JMS message: " + reply);
                }
                return reply;
            }
        });
    }

    protected Destination getReplyToDestination(Message message) throws JMSException {
        // lets send a response back if we can
        Destination destination = replyToDestination;
        if (destination == null) {
            destination = message.getJMSReplyTo();
        }
        return destination;
    }
}
