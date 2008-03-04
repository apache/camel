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

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.component.jms.requestor.Requestor;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.UuidGenerator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.MessageCreator;

/**
 * @version $Revision$
 */
public class JmsProducer extends DefaultProducer {
    private static final transient Log LOG = LogFactory.getLog(JmsProducer.class);
    private final JmsEndpoint endpoint;
    private JmsOperations inOnlyTemplate;
    private JmsOperations inOutTemplate;
    private UuidGenerator uuidGenerator;

    public JmsProducer(JmsEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(final Exchange exchange) {
        final org.apache.camel.Message in = exchange.getIn();

        if (exchange.getPattern().isOutCapable()) {
            // create a temporary queue and consumer for responses...
            // note due to JMS transaction semantics we cannot use a single transaction
            // for sending the request and receiving the response
            Requestor requestor;
            try {
                requestor = endpoint.getRequestor();
            }
            catch (Exception e) {
                throw new RuntimeExchangeException(e, exchange);
            }

            final Destination replyTo = requestor.getReplyTo();

            String correlationId = in.getHeader("JMSCorrelationID", String.class);
            if (correlationId == null) {
                correlationId = getUuidGenerator().generateId();
                in.setHeader("JMSCorrelationID", correlationId);
            }

            // lets register the future object before we try send just in case
            long requestTimeout = endpoint.getRequestTimeout();
            FutureTask future = requestor.getReceiveFuture(correlationId, requestTimeout);

            getInOutTemplate().send(endpoint.getDestination(), new MessageCreator() {
                public Message createMessage(Session session) throws JMSException {
                    Message message = endpoint.getBinding().makeJmsMessage(exchange, in, session);
                    message.setJMSReplyTo(replyTo);

                    if (LOG.isDebugEnabled()) {
                        LOG.debug(endpoint + " sending JMS message: " + message);
                    }
                    return message;
                }
            });

            // lets wait and return the response
            try {
                Message message;
                if (requestTimeout < 0) {
                    message = (Message) future.get();
                }
                else {
                    message = (Message) future.get(requestTimeout, TimeUnit.MILLISECONDS);
                }
                if (message != null) {
                    exchange.setOut(new JmsMessage(message, endpoint.getBinding()));
                }
                else {
                    // lets set a timed out exception
                    exchange.setException(new ExchangeTimedOutException(exchange, requestTimeout));
                }
            }
            catch (Exception e) {
                exchange.setException(e);
            }
        }
        else {
            getInOnlyTemplate().send(endpoint.getDestination(), new MessageCreator() {
                public Message createMessage(Session session) throws JMSException {
                    Message message = endpoint.getBinding().makeJmsMessage(exchange, in, session);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(endpoint + " sending JMS message: " + message);
                    }
                    return message;
                }
            });
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
