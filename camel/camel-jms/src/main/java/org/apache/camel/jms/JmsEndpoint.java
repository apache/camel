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

import org.apache.camel.Exchange;
import org.apache.camel.ExchangeConverter;
import org.apache.camel.impl.DefaultEndpoint;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.MessageCreator;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

/**
 * @version $Revision$
 */
public class JmsEndpoint extends DefaultEndpoint<JmsExchange> {

    private JmsOperations template;
    private Destination destination;


    public JmsEndpoint(String uri, ExchangeConverter exchangeConverter, Destination destination, JmsOperations template) {
        super(uri, exchangeConverter);
        this.destination = destination;
        this.template = template;
    }

    public void send(final JmsExchange exchange) {
        template.send(getDestination(), new MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                return exchange.createMessage(session);
            }
        });
    }

    public void send(Exchange exchange) {
        // lets convert to the type of an exchange
        JmsExchange jmsExchange = convertTo(JmsExchange.class, exchange);
        send(jmsExchange);
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
}
