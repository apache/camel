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
package org.apache.camel.jms;

import org.apache.camel.CamelContainer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultEndpoint;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.MessageCreator;
import org.springframework.jms.listener.AbstractMessageListenerContainer;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.MessageListener;
import javax.jms.Destination;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @version $Revision$
 */
public class JmsEndpoint extends DefaultEndpoint<JmsExchange> implements MessageListener {

    private JmsOperations template;
    private Destination destination;
    private AbstractMessageListenerContainer listenerContainer;
    private Processor<Exchange> processor;
    private AtomicBoolean startedConsuming = new AtomicBoolean(false);

    public JmsEndpoint(String endpointUri, CamelContainer container, Destination destination, JmsOperations template, AbstractMessageListenerContainer listenerContainer) {
        super(endpointUri, container);
        this.destination = destination;
        this.template = template;
        this.listenerContainer = listenerContainer;
        this.listenerContainer.setMessageListener(this);
        this.listenerContainer.setDestination(destination);
    }

    public void onMessage(Message message) {
        Exchange exchange = createExchange(message);
        processor.onExchange(exchange);
    }

    public void setProcessor(Processor<Exchange> processor) {
        this.processor = processor;
        if (startedConsuming.compareAndSet(false, true)) {
            listenerContainer.afterPropertiesSet();
            listenerContainer.initialize();
            listenerContainer.start();
        }
    }

    public void send(Exchange exchange) {
        // lets convert to the type of an exchange
        JmsExchange jmsExchange = convertTo(JmsExchange.class, exchange);
        send(jmsExchange);
    }

    public void send(final JmsExchange exchange) {
        template.send(getDestination(), new MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                return exchange.createMessage(session);
            }
        });
    }

    /**
     * Returns the JMS destination for this endpoint
     */
    public Destination getDestination() {
        return destination;
    }

    public JmsOperations getTemplate() {
        return template;
    }

    public JmsExchange createExchange() {
        return new DefaultJmsExchange();
    }


    public JmsExchange createExchange(Message message) {
        return new DefaultJmsExchange(message);
    }


    protected MessageListener createMessageListener(Processor<Exchange> processor) {
        return new MessageListenerProcessor(processor);
    }
}
