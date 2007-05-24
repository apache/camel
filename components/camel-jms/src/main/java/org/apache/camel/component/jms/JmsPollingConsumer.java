/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.jms;

import org.apache.camel.impl.PollingConsumerSupport;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.JmsTemplate102;

import javax.jms.Message;

/**
 * @version $Revision: 1.1 $
 */
public class JmsPollingConsumer extends PollingConsumerSupport<JmsExchange> {
    private JmsOperations template;

    public JmsPollingConsumer(JmsEndpoint endpoint, JmsOperations template) {
        super(endpoint);
        this.template = template;
    }

    @Override
    public JmsEndpoint getEndpoint() {
        return (JmsEndpoint) super.getEndpoint();
    }

    public JmsExchange receiveNoWait() {
        return receive(0);
    }

    public JmsExchange receive() {
        return receive(-1);
    }

    public JmsExchange receive(long timeout) {
        setReceiveTimeout(timeout);
        Message message = template.receive();
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
            JmsTemplate jmsTemplate = (JmsTemplate) template;
            jmsTemplate.setReceiveTimeout(timeout);
        }
        else if (template instanceof JmsTemplate102) {
            JmsTemplate102 jmsTemplate102 = (JmsTemplate102) template;
            jmsTemplate102.setReceiveTimeout(timeout);
        }
        else {
            throw new IllegalArgumentException("Cannot set the receiveTimeout property on unknown JmsOperations type: " + template);
        }
    }
}
