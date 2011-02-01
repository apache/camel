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
import org.apache.camel.impl.PollingConsumerSupport;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.JmsTemplate;

/**
 * @version $Revision$
 */
public class JmsPollingConsumer extends PollingConsumerSupport {
    private JmsOperations template;
    private JmsEndpoint jmsEndpoint;
    private final boolean spring20x;

    public JmsPollingConsumer(JmsEndpoint endpoint, JmsOperations template) {
        super(endpoint);
        this.jmsEndpoint = endpoint;
        this.template = template;
        this.spring20x = JmsHelper.isSpring20x(endpoint != null ? endpoint.getCamelContext() : null);
    }

    @Override
    public JmsEndpoint getEndpoint() {
        return (JmsEndpoint)super.getEndpoint();
    }

    public Exchange receiveNoWait() {
        // spring have changed the semantic of the receive timeout mode
        // so we need to determine if running spring 2.0.x or 2.5.x or newer
        if (spring20x) {
            // spring 2.0.x
            return receive(0L);
        } else {
            // spring 2.5.x
            // no wait using -1L does not work properly so wait at most 1 millis to simulate no wait
            return receive(1);
        }
    }

    public Exchange receive() {
        // spring have changed the semantic of the receive timeout mode
        // so we need to determine if running spring 2.0.x or 2.5.x or newer
        if (spring20x) {
            // spring 2.0.x
            return receive(-1L);
        } else {
            // spring 2.5.x
            return receive(0L);
        }
    }

    public Exchange receive(long timeout) {
        setReceiveTimeout(timeout);
        Message message = null;
        // using the selector
        if (jmsEndpoint.getSelector() != null && jmsEndpoint.getSelector().length() > 0) {
            message = template.receiveSelected(jmsEndpoint.getSelector());
        } else {
            message = template.receive();
        }
        if (message != null) {
            return getEndpoint().createExchange(message);
        }
        return null;
    }

    protected void doStart() throws Exception {
    }

    protected void doStop() throws Exception {
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
