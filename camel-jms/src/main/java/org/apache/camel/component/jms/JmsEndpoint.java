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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.MessageCreator;
import org.springframework.jms.listener.AbstractMessageListenerContainer;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Session;

/**
 * @version $Revision:520964 $
 */
public class JmsEndpoint extends DefaultEndpoint<JmsExchange> implements MessageListener {
    private static final Log log = LogFactory.getLog(JmsEndpoint.class);
    private JmsBinding binding;
    private JmsOperations template;
    private AbstractMessageListenerContainer listenerContainer;
    private String destination;

    public JmsEndpoint(String endpointUri, CamelContext container, String destination, JmsOperations template, AbstractMessageListenerContainer listenerContainer) {
        super(endpointUri, container);
        this.destination = destination;
        this.template = template;
        this.listenerContainer = listenerContainer;
        this.listenerContainer.setMessageListener(this);
    }

    public void onMessage(Message message) {
        if (log.isDebugEnabled()) {
            log.debug(JmsEndpoint.this + " receiving JMS message: " + message);
        }
        JmsExchange exchange = createExchange(message);
        getInboundProcessor().onExchange(exchange);
    }

    public void onExchange(Exchange exchange) {
        // lets convert to the type of an exchange
        JmsExchange jmsExchange = convertTo(JmsExchange.class, exchange);
        onExchange(jmsExchange);
    }

    public void onExchange(final JmsExchange exchange) {
        template.send(destination, new MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                Message message = getBinding().createJmsMessage(exchange, exchange.getIn(), session);
                if (log.isDebugEnabled()) {
                    log.debug(JmsEndpoint.this + " sending JMS message: " + message);
                }
                return message;
            }
        });
    }

    public JmsExchange createExchange() {
        return new JmsExchange(getContext(), getBinding());
    }

    public JmsExchange createExchange(Message message) {
        return new JmsExchange(getContext(), getBinding(), message);
    }

    // Properties
    //-------------------------------------------------------------------------
    public JmsBinding getBinding() {
        if (binding == null) {
            binding = new JmsBinding();
        }
        return binding;
    }

    /**
     * Sets the binding used to convert from a Camel message to and from a JMS message
     *
     * @param binding the binding to use
     */
    public void setBinding(JmsBinding binding) {
        this.binding = binding;
    }

    public JmsOperations getTemplate() {
        return template;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected void doActivate() {
        super.doActivate();
        listenerContainer.afterPropertiesSet();
        listenerContainer.initialize();
        listenerContainer.start();
    }

    protected void doDeactivate() {
        listenerContainer.stop();
        listenerContainer.destroy();
        super.doDeactivate();
    }
}
