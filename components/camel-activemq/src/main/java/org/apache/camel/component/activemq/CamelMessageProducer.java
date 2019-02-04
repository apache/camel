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

import javax.jms.Destination;
import javax.jms.IllegalStateException;
import javax.jms.JMSException;
import javax.jms.Message;

import org.apache.activemq.ActiveMQMessageProducerSupport;
import org.apache.activemq.ActiveMQSession;
import org.apache.activemq.util.JMSExceptionSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.apache.camel.component.jms.JmsMessage;
import org.apache.camel.util.ObjectHelper;

/**
 * A JMS {@link javax.jms.MessageProducer} which sends message exchanges to a
 * Camel {@link Endpoint}
 */
public class CamelMessageProducer extends ActiveMQMessageProducerSupport {

    protected Producer producer;

    private final CamelDestination destination;
    private final Endpoint endpoint;
    private boolean closed;

    public CamelMessageProducer(CamelDestination destination, Endpoint endpoint, ActiveMQSession session) throws JMSException {
        super(session);
        this.destination = destination;
        this.endpoint = endpoint;
        try {
            this.producer = endpoint.createProducer();
        } catch (JMSException e) {
            throw e;
        } catch (Exception e) {
            throw JMSExceptionSupport.create(e);
        }
    }

    public CamelDestination getDestination() throws JMSException {
        return destination;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void close() throws JMSException {
        if (!closed) {
            closed = true;
            try {
                producer.stop();
            } catch (JMSException e) {
                throw e;
            } catch (Exception e) {
                throw JMSExceptionSupport.create(e);
            }
        }
    }

    public void send(Destination destination, Message message, int deliveryMode, int priority, long timeToLive) throws JMSException {
        CamelDestination camelDestination = null;
        if (ObjectHelper.equal(destination, this.destination)) {
            camelDestination = this.destination;
        } else {
            // TODO support any CamelDestination?
            throw new IllegalArgumentException("Invalid destination setting: " + destination + " when expected: " + this.destination);
        }
        try {
            Exchange exchange = endpoint.createExchange(ExchangePattern.InOnly);
            exchange.setIn(new JmsMessage(exchange, message, null, camelDestination.getBinding()));
            producer.process(exchange);
        } catch (JMSException e) {
            throw e;
        } catch (Exception e) {
            throw JMSExceptionSupport.create(e);
        }
    }

    protected void checkClosed() throws IllegalStateException {
        if (closed) {
            throw new IllegalStateException("The producer is closed");
        }
    }
}
