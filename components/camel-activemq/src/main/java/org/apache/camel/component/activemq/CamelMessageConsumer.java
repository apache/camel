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
package org.apache.camel.component.activemq;

import javax.jms.IllegalStateException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;

import org.apache.activemq.ActiveMQSession;
import org.apache.activemq.util.JMSExceptionSupport;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;

/**
 * A JMS {@link javax.jms.MessageConsumer} which consumes message exchanges from
 * a Camel {@link Endpoint}
 */
public class CamelMessageConsumer implements MessageConsumer {
    private final CamelDestination destination;
    private final Endpoint endpoint;
    private final ActiveMQSession session;
    private final String messageSelector;
    private final boolean noLocal;
    private MessageListener messageListener;
    private Consumer consumer;
    private PollingConsumer pollingConsumer;
    private boolean closed;

    public CamelMessageConsumer(CamelDestination destination, Endpoint endpoint, ActiveMQSession session, String messageSelector, boolean noLocal) {
        this.destination = destination;
        this.endpoint = endpoint;
        this.session = session;
        this.messageSelector = messageSelector;
        this.noLocal = noLocal;
    }

    public void close() throws JMSException {
        if (!closed) {
            closed = true;
            try {
                if (consumer != null) {
                    consumer.stop();
                }
                if (pollingConsumer != null) {
                    pollingConsumer.stop();
                }
            } catch (JMSException e) {
                throw e;
            } catch (Exception e) {
                throw JMSExceptionSupport.create(e);
            }
        }
    }

    public MessageListener getMessageListener() throws JMSException {
        return messageListener;
    }

    public void setMessageListener(MessageListener messageListener) throws JMSException {
        this.messageListener = messageListener;
        if (messageListener != null && consumer == null) {
            consumer = createConsumer();
        }
    }

    public Message receive() throws JMSException {
        Exchange exchange = getPollingConsumer().receive();
        return createMessage(exchange);
    }

    public Message receive(long timeoutMillis) throws JMSException {
        Exchange exchange = getPollingConsumer().receive(timeoutMillis);
        return createMessage(exchange);
    }

    public Message receiveNoWait() throws JMSException {
        Exchange exchange = getPollingConsumer().receiveNoWait();
        return createMessage(exchange);
    }

    // Properties
    // -----------------------------------------------------------------------

    public CamelDestination getDestination() {
        return destination;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public String getMessageSelector() {
        return messageSelector;
    }

    public boolean isNoLocal() {
        return noLocal;
    }

    public ActiveMQSession getSession() {
        return session;
    }

    // Implementation methods
    // -----------------------------------------------------------------------

    protected PollingConsumer getPollingConsumer() throws JMSException {
        try {
            if (pollingConsumer == null) {
                pollingConsumer = endpoint.createPollingConsumer();
                pollingConsumer.start();
            }
            return pollingConsumer;
        } catch (JMSException e) {
            throw e;
        } catch (Exception e) {
            throw JMSExceptionSupport.create(e);
        }
    }

    protected Message createMessage(Exchange exchange) throws JMSException {
        if (exchange != null) {
            Message message = destination.getBinding().makeJmsMessage(exchange, session);
            return message;
        } else {
            return null;
        }
    }

    protected Consumer createConsumer() throws JMSException {
        try {
            Consumer answer = endpoint.createConsumer(new Processor() {
                public void process(Exchange exchange) throws Exception {
                    Message message = createMessage(exchange);
                    getMessageListener().onMessage(message);
                }
            });
            answer.start();
            return answer;
        } catch (JMSException e) {
            throw e;
        } catch (Exception e) {
            throw JMSExceptionSupport.create(e);
        }
    }

    protected void checkClosed() throws javax.jms.IllegalStateException {
        if (closed) {
            throw new IllegalStateException("The producer is closed");
        }
    }
}
