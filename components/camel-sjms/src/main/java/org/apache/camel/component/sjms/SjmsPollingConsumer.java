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
package org.apache.camel.component.sjms;

import jakarta.jms.Message;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.PollingConsumerSupport;

/**
 * A JMS {@link org.apache.camel.PollingConsumer}.
 */
public class SjmsPollingConsumer extends PollingConsumerSupport {
    private final SjmsTemplate template;
    private final SjmsEndpoint jmsEndpoint;

    public SjmsPollingConsumer(SjmsEndpoint endpoint, SjmsTemplate template) {
        super(endpoint);
        this.jmsEndpoint = endpoint;
        this.template = template;
    }

    @Override
    public SjmsEndpoint getEndpoint() {
        return (SjmsEndpoint) super.getEndpoint();
    }

    @Override
    public Exchange receiveNoWait() {
        return receive(-1);
    }

    @Override
    public Exchange receive() {
        return receive(0);
    }

    @Override
    public Exchange receive(long timeout) {
        try {
            // read the message before the JMS consumer, session and connection are closed
            return template.receive(jmsEndpoint.getDestinationName(), jmsEndpoint.getMessageSelector(),
                    jmsEndpoint.isTopic(), timeout, this::createExchange);
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * Creates the exchange for the received message, and reads the body and the headers of the message. It is called
     * before the JMS consumer, session and connection are closed, as a JMS client may not read the message after that:
     * the ActiveMQ Artemis client streams the body of a large message through the consumer.
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

}
