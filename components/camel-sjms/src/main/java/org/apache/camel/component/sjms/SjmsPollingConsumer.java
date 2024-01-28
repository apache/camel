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
            Message message = template.receive(jmsEndpoint.getDestinationName(), jmsEndpoint.getMessageSelector(),
                    jmsEndpoint.isTopic(), timeout);
            if (message != null) {
                return getEndpoint().createExchange(message, null);
            }
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
        return null;
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
