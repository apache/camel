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

import org.apache.camel.Exchange;
import org.apache.camel.util.ExchangeHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.jms.BytesMessage;
import javax.jms.Destination;
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
import java.util.Set;

/**
 * A Strategy used to convert between a Camel {@JmsExchange} and {@JmsMessage}
 * to and from a JMS {@link Message}
 * 
 * @version $Revision$
 */
public class JmsBinding {
    private static final transient Log LOG = LogFactory.getLog(JmsBinding.class);

    /**
     * Extracts the body from the JMS message
     * 
     * @param exchange
     * @param message
     */
    public Object extractBodyFromJms(JmsExchange exchange, Message message) {
        try {
            if (message instanceof ObjectMessage) {
                ObjectMessage objectMessage = (ObjectMessage)message;
                return objectMessage.getObject();
            } else if (message instanceof TextMessage) {
                TextMessage textMessage = (TextMessage)message;
                return textMessage.getText();
            } else if (message instanceof MapMessage) {
                return createMapFromMapMessage((MapMessage)message);
            } else if (message instanceof BytesMessage || message instanceof StreamMessage) {
                // TODO we need a decoder to be able to process the message
                return message;
            } else {
                return null;
            }
        } catch (JMSException e) {
            throw new RuntimeJmsException("Failed to extract body due to: " + e + ". Message: " + message, e);
        }
    }

    /**
     * Creates a JMS message from the Camel exchange and message
     * 
     * @param session the JMS session used to create the message
     * @return a newly created JMS Message instance containing the
     * @throws JMSException if the message could not be created
     */
    public Message makeJmsMessage(Exchange exchange, Session session) throws JMSException {
        Message answer = null;
        if( exchange instanceof JmsExchange  ) {
            JmsExchange jmsExchange = (JmsExchange)exchange;
            answer = jmsExchange.getIn().getJmsMessage();
        }
        if( answer == null ) {
            answer = createJmsMessage(exchange.getIn().getBody(), session);
            appendJmsProperties(answer, exchange);
        }
        return answer;
    }

    /**
     * Appends the JMS headers from the Camel {@link JmsMessage}
     */
    public void appendJmsProperties(Message jmsMessage, Exchange exchange) throws JMSException {
        org.apache.camel.Message in = exchange.getIn();
        Set<Map.Entry<String, Object>> entries = in.getHeaders().entrySet();
        for (Map.Entry<String, Object> entry : entries) {
            String headerName = entry.getKey();
            Object headerValue = entry.getValue();
            
            if (headerName.startsWith("JMS") && !headerName.startsWith("JMSX")) {
                if (headerName.equals("JMSCorrelationID")) {
                    jmsMessage.setJMSCorrelationID(ExchangeHelper.convertToType(exchange, String.class, headerValue));
                }
                else if (headerName.equals("JMSCorrelationID")) {
                    jmsMessage.setJMSCorrelationID(ExchangeHelper.convertToType(exchange, String.class, headerValue));
                }
                else if (headerName.equals("JMSReplyTo")) {
                    jmsMessage.setJMSReplyTo(ExchangeHelper.convertToType(exchange, Destination.class, headerValue));
                }
                else if (headerName.equals("JMSType")) {
                    jmsMessage.setJMSType(ExchangeHelper.convertToType(exchange, String.class, headerValue));
                }
                else if (LOG.isDebugEnabled()) {
                    // The following properties are set by the MessageProducer
                    //   JMSDeliveryMode, JMSDestination, JMSExpiration, JMSPriority,
                    // The following are set on the underlying JMS provider
                    //   JMSMessageID, JMSTimestamp, JMSRedelivered
                    LOG.debug("Ignoring JMS header: " + headerName + " with value: " + headerValue);
                }
            }
            else if (shouldOutputHeader(in, headerName, headerValue)) {
                jmsMessage.setObjectProperty(headerName, headerValue);
            }
        }
    }

    protected Message createJmsMessage(Object body, Session session) throws JMSException {
        if (body instanceof String) {
            return session.createTextMessage((String)body);
        } else if (body instanceof Serializable) {
            return session.createObjectMessage((Serializable)body);
        } else {
            return session.createMessage();
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

    /**
     * Strategy to allow filtering of headers which are put on the JMS message
     */
    protected boolean shouldOutputHeader(org.apache.camel.Message camelMessage, String headerName, Object headerValue) {
        return headerValue != null;
    }
}
