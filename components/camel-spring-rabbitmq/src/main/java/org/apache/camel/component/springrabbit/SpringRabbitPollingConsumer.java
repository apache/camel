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
package org.apache.camel.component.springrabbit;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.PollingConsumerSupport;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public class SpringRabbitPollingConsumer extends PollingConsumerSupport {

    private final RabbitTemplate template;
    private final SpringRabbitMQEndpoint jmsEndpoint;

    public SpringRabbitPollingConsumer(SpringRabbitMQEndpoint endpoint, RabbitTemplate template) {
        super(endpoint);
        this.jmsEndpoint = endpoint;
        this.template = template;
    }

    @Override
    public SpringRabbitMQEndpoint getEndpoint() {
        return (SpringRabbitMQEndpoint) super.getEndpoint();
    }

    @Override
    public Exchange receiveNoWait() {
        return receive(0);
    }

    @Override
    public Exchange receive() {
        return receive(-1);
    }

    @Override
    public Exchange receive(long timeout) {
        try {
            Message message;
            if (timeout == 0) {
                message = template.receive(jmsEndpoint.getQueues());
            } else {
                message = template.receive(jmsEndpoint.getQueues(), timeout);
            }
            if (message != null) {
                return getEndpoint().createExchange(message);
            }
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
        return null;
    }

    @Override
    protected void doInit() throws Exception {
        if (getEndpoint().getQueues() == null) {
            throw new IllegalArgumentException("Queues must be configured when using PollingConsumer");
        }
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
