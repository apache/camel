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

import java.beans.DesignMode;
import java.io.File;
import java.util.Enumeration;
import java.util.Map;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.TemporaryTopic;
import javax.jms.Topic;
import org.apache.camel.impl.DefaultMessage;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Represents a {@link org.apache.camel.Message} for working with JMS
 *
 * @version $Revision:520964 $
 */
public class JmsMessage extends DefaultMessage {
    private static final transient Log log = LogFactory.getLog(JmsMessage.class);
    private Message jmsMessage;

    public JmsMessage() {
    }

    public JmsMessage(Message jmsMessage) {
        setJmsMessage(jmsMessage);
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

       
    /**
     * Returns the underlying JMS message
     *
     * @return the underlying JMS message
     */
    public Message getJmsMessage() {
        return jmsMessage;
    }

    public void setJmsMessage(Message jmsMessage){
        this.jmsMessage=jmsMessage;
        try{
            String id=getDestinationAsString(jmsMessage.getJMSDestination());
            id+=getSanitizedString(jmsMessage.getJMSMessageID());
            setMessageId(id);
        }catch(JMSException e){
            log.error("Failed to get message id from message "+jmsMessage,e);
        }
    }

    public Object getHeader(String name) {
        Object answer = null;
        if (jmsMessage != null) {
            try {
                answer = jmsMessage.getObjectProperty(name);
            }
            catch (JMSException e) {
                throw new MessagePropertyAccessException(name, e);
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
        if (jmsMessage != null && getExchange() instanceof JmsExchange) {
            JmsExchange exchange = (JmsExchange)getExchange();
            return (exchange.getBinding().extractBodyFromJms(exchange, jmsMessage));
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
                throw new MessagePropertyNamesAccessException(e);
            }
            while (names.hasMoreElements()) {
                String name = names.nextElement().toString();
                try {
                    Object value = jmsMessage.getObjectProperty(name);
                    map.put(name, value);
                }
                catch (JMSException e) {
                    throw new MessagePropertyAccessException(name, e);
                }
            }
        }
    }
    
    private String getDestinationAsString(Destination destination) throws JMSException {
        String result = "";
        if (destination instanceof Topic) {
            result += "topic" + File.separator + getSanitizedString(((Topic)destination).getTopicName());
        }else {
            result += "queue" + File.separator + getSanitizedString(((Queue)destination).getQueueName());
        }
        result += File.separator;
        return result;
    }
    private String getSanitizedString(Object value) {
        return value != null ? value.toString().replaceAll("[^a-zA-Z0-9\\.\\_\\-]", "_") : "";
    }
}

