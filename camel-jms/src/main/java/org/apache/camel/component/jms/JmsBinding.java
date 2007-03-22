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

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * A Strategy used to convert between a Camel {@JmsExchange} and {@JmsMessage} to and from a
 * JMS {@link Message}
 *
 * @version $Revision$
 */
public class JmsBinding {
    /**
     * Creates a JMS message from the Camel exchange and message
     *
     * @param session the JMS session used to create the message
     * @return a newly created JMS Message instance containing the
     * @throws JMSException if the message could not be created
     */
    public Message createJmsMessage(JmsExchange exchange, JmsMessage message, Session session) throws JMSException {
        Object value = message.getBody();
        if (value instanceof String) {
            return session.createTextMessage((String) value);
        }
        else if (value instanceof Serializable) {
            return session.createObjectMessage((Serializable) value);
        }
        else {
            return session.createMessage();
        }
    }

    /**
     * Extracts the body from the JMS message
     *
     * @param exchange
     * @param message
     */
    public Object extractBodyFromJms(JmsExchange exchange, Message message) {
        try {
            if (message instanceof ObjectMessage) {
                ObjectMessage objectMessage = (ObjectMessage) message;
                return objectMessage.getObject();
            }
            else if (message instanceof TextMessage) {
                TextMessage textMessage = (TextMessage) message;
                return textMessage.getText();
            }
            else if (message instanceof MapMessage) {
                return createMapFromMapMessage((MapMessage) message);
            }
            else if (message instanceof BytesMessage || message instanceof StreamMessage) {
                // TODO we need a decoder to be able to process the message
                return message;
            }
            else {
                return null;
            }
        }
        catch (JMSException e) {
            throw new RuntimeJmsException("Failed to extract body due to: " + e + ". Message: " + message, e);
        }
    }

    /**
     * Extracts a {@link Map} from a {@link MapMessage}
     */
    public Map<String, Object> createMapFromMapMessage(MapMessage message) throws JMSException {
        Map<String, Object> answer = new HashMap<String, Object>();
        Enumeration names = message.getPropertyNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement().toString();
            Object value = message.getObject(name);
            answer.put(name, value);
        }
        return answer;
    }
}
