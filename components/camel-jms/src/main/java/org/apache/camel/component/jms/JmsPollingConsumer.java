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
package org.apache.camel.component.jms;

import jakarta.jms.Message;

import org.apache.camel.Exchange;
import org.apache.camel.component.jms.JmsConfiguration.CamelJmsTemplate;
import org.apache.camel.support.PollingConsumerSupport;
import org.apache.camel.util.ObjectHelper;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.destination.JmsDestinationAccessor;

/**
 * A JMS {@link org.apache.camel.PollingConsumer}.
 */
public class JmsPollingConsumer extends PollingConsumerSupport {
    private final JmsOperations template;
    private final JmsEndpoint jmsEndpoint;

    public JmsPollingConsumer(JmsEndpoint endpoint, JmsOperations template) {
        super(endpoint);
        this.jmsEndpoint = endpoint;
        this.template = template;
    }

    @Override
    public JmsEndpoint getEndpoint() {
        return (JmsEndpoint) super.getEndpoint();
    }

    @Override
    public Exchange receiveNoWait() {
        return receive(JmsDestinationAccessor.RECEIVE_TIMEOUT_NO_WAIT);
    }

    @Override
    public Exchange receive() {
        return receive(JmsDestinationAccessor.RECEIVE_TIMEOUT_INDEFINITE_WAIT);
    }

    @Override
    public Exchange receive(long timeout) {
        String selector = ObjectHelper.isNotEmpty(jmsEndpoint.getSelector()) ? jmsEndpoint.getSelector() : null;
        Message message;
        // synchronize to make set-timeout + receive atomic, as the polling consumer
        // may be shared across concurrent pollEnrich / ConsumerTemplate callers
        synchronized (template) {
            setReceiveTimeout(timeout);
            if (template instanceof CamelJmsTemplate camelJmsTemplate) {
                // read the message before the JMS consumer and session are closed
                return camelJmsTemplate.receiveSelectedAndApply(selector, this::createExchange);
            }
            if (selector != null) {
                message = template.receiveSelected(selector);
            } else {
                message = template.receive();
            }
        }
        if (message != null) {
            return getEndpoint().createExchange(message, null);
        }
        return null;
    }

    /**
     * Creates the exchange for the received message, and reads the body and the headers of the message. It is called
     * before the JMS consumer and session are closed, as a JMS client may not read the message after that: the Apache
     * Artemis client streams the body of a large message through the consumer.
     */
    protected Exchange createExchange(Message message) {
        Exchange exchange = getEndpoint().createExchange(message, null);
        exchange.getIn().getBody();
        exchange.getIn().getHeaders();
        return exchange;
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    protected void setReceiveTimeout(long timeout) {
        if (template instanceof JmsTemplate jmsTemplate) {
            jmsTemplate.setReceiveTimeout(timeout);
        } else {
            throw new IllegalArgumentException(
                    "Cannot set the receiveTimeout property on unknown JmsOperations type: " + template.getClass().getName());
        }
    }
}
