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
import javax.jms.MessageListener;
import javax.jms.Session;

/**
 * @version $Revision$
 */
public class JmsEndpoint extends DefaultEndpoint<JmsExchange> implements MessageListener {

    private JmsOperations template;
    private AbstractMessageListenerContainer listenerContainer;

    public JmsEndpoint(String endpointUri, CamelContainer container, JmsOperations template, AbstractMessageListenerContainer listenerContainer) {
        super(endpointUri, container);
        this.template = template;
        this.listenerContainer = listenerContainer;
        this.listenerContainer.setMessageListener(this);
    }

    public void onMessage(Message message) {
        JmsExchange exchange = createExchange(message);
        getInboundProcessor().onExchange(exchange);
    }


    public void send(Exchange exchange) {
        // lets convert to the type of an exchange
        JmsExchange jmsExchange = convertTo(JmsExchange.class, exchange);
        send(jmsExchange);
    }

    public void send(final JmsExchange exchange) {
        template.send(new MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                return exchange.createMessage(session);
            }
        });
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
