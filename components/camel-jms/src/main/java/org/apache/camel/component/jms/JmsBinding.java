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

import java.io.Serializable;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import javax.xml.transform.TransformerException;

import org.w3c.dom.Node;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;



/**
 * A Strategy used to convert between a Camel {@link JmsExchange} and {@link JmsMessage}
 * to and from a JMS {@link Message}
 *
 * @version $Revision$
 */
public class JmsBinding {
    private static final transient Log LOG = LogFactory.getLog(JmsBinding.class);
    private Set<String> ignoreJmsHeaders;
    private XmlConverter xmlConverter = new XmlConverter();

    /**
     * Extracts the body from the JMS message
     *
     * @param exchange the exchange
     * @param message  the message to extract its body
     * @return the body, can be <tt>null</tt>
     */
    public Object extractBodyFromJms(Exchange exchange, Message message) {
        try {
            if (message instanceof ObjectMessage) {
                ObjectMessage objectMessage = (ObjectMessage)message;
                return objectMessage.getObject();
            } else if (message instanceof TextMessage) {
                TextMessage textMessage = (TextMessage)message;
                return textMessage.getText();
            } else if (message instanceof MapMessage) {
                return createMapFromMapMessage((MapMessage)message);
            } else if (message instanceof BytesMessage) {
                return createByteArrayFromBytesMessage((BytesMessage)message);
            } else if (message instanceof StreamMessage) {
                return message;
            } else {
                return null;
            }
        } catch (JMSException e) {
            throw new RuntimeJmsException("Failed to extract body due to: " + e + ". Message: " + message, e);
        }
    }

    protected byte[] createByteArrayFromBytesMessage(BytesMessage message) throws JMSException {
        if (message.getBodyLength() > Integer.MAX_VALUE) {
            return null;
        }
        byte[] result = new byte[(int)message.getBodyLength()];
        message.readBytes(result);
        return result;
    }

    /**
     * Creates a JMS message from the Camel exchange and message
     *
     * @param session the JMS session used to create the message
     * @return a newly created JMS Message instance containing the
     * @throws JMSException if the message could not be created
     */
    public Message makeJmsMessage(Exchange exchange, Session session) throws JMSException {
        return makeJmsMessage(exchange, exchange.getIn(), session);
    }

    /**
     * Creates a JMS message from the Camel exchange and message
     *
     * @param session the JMS session used to create the message
     * @return a newly created JMS Message instance containing the
     * @throws JMSException if the message could not be created
     */
    public Message makeJmsMessage(Exchange exchange, org.apache.camel.Message camelMessage, Session session)
        throws JMSException {
        Message answer = null;
        if (camelMessage instanceof JmsMessage) {
            JmsMessage jmsMessage = (JmsMessage)camelMessage;
            answer = jmsMessage.getJmsMessage();
        }
        if (answer == null) {
            answer = createJmsMessage(camelMessage.getBody(), session, exchange.getContext());
            appendJmsProperties(answer, exchange, camelMessage);
        }
        return answer;
    }

    /**
     * Appends the JMS headers from the Camel {@link JmsMessage}
     */
    public void appendJmsProperties(Message jmsMessage, Exchange exchange) throws JMSException {
        appendJmsProperties(jmsMessage, exchange, exchange.getIn());
    }

    /**
     * Appends the JMS headers from the Camel {@link JmsMessage}
     */
    public void appendJmsProperties(Message jmsMessage, Exchange exchange, org.apache.camel.Message in)
        throws JMSException {
        Set<Map.Entry<String, Object>> entries = in.getHeaders().entrySet();
        for (Map.Entry<String, Object> entry : entries) {
            String headerName = entry.getKey();
            Object headerValue = entry.getValue();

            if (headerName.startsWith("JMS") && !headerName.startsWith("JMSX")) {
                if (headerName.equals("JMSCorrelationID")) {
                    jmsMessage.setJMSCorrelationID(ExchangeHelper.convertToType(exchange, String.class,
                                                                                headerValue));
                } else if (headerName.equals("JMSCorrelationID")) {
                    jmsMessage.setJMSCorrelationID(ExchangeHelper.convertToType(exchange, String.class,
                                                                                headerValue));
                } else if (headerName.equals("JMSReplyTo")) {
                    jmsMessage.setJMSReplyTo(ExchangeHelper.convertToType(exchange, Destination.class,
                                                                          headerValue));
                } else if (headerName.equals("JMSType")) {
                    jmsMessage.setJMSType(ExchangeHelper.convertToType(exchange, String.class, headerValue));
                } else if (LOG.isDebugEnabled()) {
                    // The following properties are set by the MessageProducer
                    // JMSDeliveryMode, JMSDestination, JMSExpiration,
                    // JMSPriority,
                    // The following are set on the underlying JMS provider
                    // JMSMessageID, JMSTimestamp, JMSRedelivered
                    LOG.debug("Ignoring JMS header: " + headerName + " with value: " + headerValue);
                }
            } else if (shouldOutputHeader(in, headerName, headerValue)) {
                jmsMessage.setObjectProperty(headerName, headerValue);
            }
        }
    }

    protected Message createJmsMessage(Object body, Session session, CamelContext context)
        throws JMSException {
        if (body instanceof Node) {
            // lets convert the document to a String format
            try {
                body = xmlConverter.toString((Node)body);
            } catch (TransformerException e) {
                JMSException jmsException = new JMSException(e.getMessage());
                jmsException.setLinkedException(e);
                throw jmsException;
            }
        }
        if (body instanceof byte[]) {
            BytesMessage result = session.createBytesMessage();
            result.writeBytes((byte[])body);
            return result;
        }
        if (body instanceof Map) {
            MapMessage result = session.createMapMessage();
            Map<?, ?> map = (Map<?, ?>)body;
            try {
                populateMapMessage(result, map, context);
                return result;
            } catch (JMSException e) {
                // if MapMessage creation failed then fall back to Object
                // Message
            }
        }
        if (body instanceof String) {
            return session.createTextMessage((String)body);
        }
        if (body instanceof Serializable) {
            return session.createObjectMessage((Serializable)body);
        }
        return session.createMessage();
    }

    /**
     * Populates a {@link MapMessage} from a {@link Map} instance.
     */
    protected void populateMapMessage(MapMessage message, Map<?, ?> map, CamelContext context)
        throws JMSException {
        for (Object key : map.keySet()) {
            String keyString = CamelContextHelper.convertTo(context, String.class, key);
            if (keyString != null) {
                message.setObject(keyString, map.get(key));
            }
        }
    }

    /**
     * Extracts a {@link Map} from a {@link MapMessage}
     */
    public Map<String, Object> createMapFromMapMessage(MapMessage message) throws JMSException {
        Map<String, Object> answer = new HashMap<String, Object>();
        Enumeration names = message.getMapNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement().toString();
            Object value = message.getObject(name);
            answer.put(name, value);
        }
        return answer;
    }

    public Set<String> getIgnoreJmsHeaders() {
        if (ignoreJmsHeaders == null) {
            ignoreJmsHeaders = new HashSet<String>();
            populateIgnoreJmsHeaders(ignoreJmsHeaders);
        }
        return ignoreJmsHeaders;
    }

    public void setIgnoreJmsHeaders(Set<String> ignoreJmsHeaders) {
        this.ignoreJmsHeaders = ignoreJmsHeaders;
    }

    /**
     * Strategy to allow filtering of headers which are put on the JMS message
     */
    protected boolean shouldOutputHeader(org.apache.camel.Message camelMessage, String headerName,
                                         Object headerValue) {
        return headerValue != null && !getIgnoreJmsHeaders().contains(headerName)
               && ObjectHelper.isJavaIdentifier(headerName);
    }

    /**
     * Populate any JMS headers that should be excluded from being copied from
     * an input message onto an outgoing message
     */
    protected void populateIgnoreJmsHeaders(Set<String> set) {
        // ignore provider specified JMS extension headers
        // see page 39 of JMS 1.1 specification
        String[] ignore = {"JMSXUserID", "JMSXAppID", "JMSXDeliveryCount", "JMSXProducerTXID",
                           "JMSXConsumerTXID", "JMSXRcvTimestamp", "JMSXState"};
        set.addAll(Arrays.asList(ignore));
    }
}
