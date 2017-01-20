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
package org.apache.camel.component.jms;

import javax.jms.Message;

import org.apache.camel.Exchange;
import org.apache.camel.ServicePoolAware;
import org.apache.camel.impl.PollingConsumerSupport;
import org.apache.camel.util.ObjectHelper;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.destination.JmsDestinationAccessor;

/**
 *  A JMS {@link org.apache.camel.PollingConsumer}.
 */
public class JmsPollingConsumer extends PollingConsumerSupport implements ServicePoolAware {
    private JmsOperations template;
    private JmsEndpoint jmsEndpoint;

    public JmsPollingConsumer(JmsEndpoint endpoint, JmsOperations template) {
        super(endpoint);
        this.jmsEndpoint = endpoint;
        this.template = template;
    }
    
    public JmsPollingConsumer(JmsEndpoint endpoint) {
        this(endpoint, endpoint.createInOnlyTemplate());
    }

    @Override
    public JmsEndpoint getEndpoint() {
        return (JmsEndpoint)super.getEndpoint();
    }

    public Exchange receiveNoWait() {
        return receive(JmsDestinationAccessor.RECEIVE_TIMEOUT_NO_WAIT);
    }

    public Exchange receive() {
        return receive(JmsDestinationAccessor.RECEIVE_TIMEOUT_INDEFINITE_WAIT);
    }

    public Exchange receive(long timeout) {
        setReceiveTimeout(timeout);
        Message message;
        // using the selector
        if (ObjectHelper.isNotEmpty(jmsEndpoint.getSelector())) {
            message = template.receiveSelected(jmsEndpoint.getSelector());
        } else {
            message = template.receive();
        }
        if (message != null) {
            return getEndpoint().createExchange(message, null);
        }
        return null;
    }

    protected void doStart() throws Exception {
        // noop
    }

    protected void doStop() throws Exception {
        // noop
    }

    protected void setReceiveTimeout(long timeout) {
        if (template instanceof JmsTemplate) {
            JmsTemplate jmsTemplate = (JmsTemplate)template;
            jmsTemplate.setReceiveTimeout(timeout);
        } else {
            throw new IllegalArgumentException("Cannot set the receiveTimeout property on unknown JmsOperations type: " + template.getClass().getName());
        }
    }
}
