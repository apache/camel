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
import org.apache.camel.impl.DefaultExchange;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

/**
 * @version $Revision$
 */
public class DefaultJmsExchange extends DefaultExchange implements JmsExchange {

    public DefaultJmsExchange(CamelContext container) {
        super(container);
    }

    public DefaultJmsExchange(CamelContext container, Message message) {
        super(container);
        setIn(new DefaultJmsMessage(message));
    }

    @Override
    public Exchange newInstance() {
        return new DefaultJmsExchange(getContext());
    }

    public Message createMessage(Session session) throws JMSException {
        Message request = getInMessage();
        if (request == null) {
            request = session.createMessage();

            /** TODO
            if (lazyHeaders != null) {
                // lets add any lazy headers
                for (Map.Entry<String, Object> entry : lazyHeaders.entrySet()) {
                    request.setObjectProperty(entry.getKey(), entry.getValue());
                }
            }
             */
        }
        return request;
    }

    public Message getInMessage() {
        JmsMessage jmsMessage = (JmsMessage) getIn();
        if (jmsMessage != null) {
            return jmsMessage.getJmsMessage();
        }
        return null;
    }

    @Override
    protected org.apache.camel.Message createInMessage() {
        return new DefaultJmsMessage();
    }

    @Override
    protected org.apache.camel.Message createOutMessage() {
        return new DefaultJmsMessage();
    }
}
