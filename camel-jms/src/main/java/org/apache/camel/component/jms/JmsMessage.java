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

import org.apache.camel.impl.DefaultMessage;

import javax.jms.JMSException;
import javax.jms.Message;
import java.util.Enumeration;
import java.util.Map;

/**
 * Represents a {@link org.apache.camel.Message} for working with JMS
 *
 * @version $Revision:520964 $
 */
public class JmsMessage extends DefaultMessage {
    private Message jmsMessage;

    public JmsMessage() {
    }

    public JmsMessage(Message jmsMessage) {
        this.jmsMessage = jmsMessage;
    }

    @Override
    public String toString() {
        if (jmsMessage != null) {
            return "JmsMessage: " + jmsMessage;
        }
        else {
            return "JmsMessage: " + getBody();
        }
    }

    @Override
    public JmsExchange getExchange() {
        return (JmsExchange) super.getExchange();
    }

    /**
     * Returns the underlying JMS message
     *
     * @return the underlying JMS message
     */
    public Message getJmsMessage() {
        return jmsMessage;
    }

    public void setJmsMessage(Message jmsMessage) {
        this.jmsMessage = jmsMessage;
    }

    public Object getHeader(String name) {
        Object answer = null;
        if (jmsMessage != null) {
            try {
                answer = jmsMessage.getObjectProperty(name);
            }
            catch (JMSException e) {
                throw new MessagePropertyAcessException(name, e);
            }
        }
        if (answer == null) {
            answer = super.getHeader(name);
        }
        return answer;
    }

    @Override
    public JmsMessage newInstance() {
        return new JmsMessage();
    }

    @Override
    protected Object createBody() {
        if (jmsMessage != null) {
            return getExchange().getBinding().extractBodyFromJms(getExchange(), jmsMessage);
        }
        return null;
    }

    @Override
    protected void populateInitialHeaders(Map<String, Object> map) {
        if (jmsMessage != null) {
            Enumeration names;
            try {
                names = jmsMessage.getPropertyNames();
            }
            catch (JMSException e) {
                throw new MessagePropertyNamesAcessException(e);
            }
            while (names.hasMoreElements()) {
                String name = names.nextElement().toString();
                try {
                    Object value = jmsMessage.getObjectProperty(name);
                    map.put(name, value);
                }
                catch (JMSException e) {
                    throw new MessagePropertyAcessException(name, e);
                }
            }
        }
    }
}

