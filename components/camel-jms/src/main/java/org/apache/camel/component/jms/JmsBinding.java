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

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
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
import org.apache.camel.impl.DefaultHeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ExchangeHelper;
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
    private JmsEndpoint endpoint;
    private XmlConverter xmlConverter = new XmlConverter();
    private HeaderFilterStrategy headerFilterStrategy;

    public JmsBinding() {
        headerFilterStrategy = new JmsHeaderFilterStrategy();
    }

    public JmsBinding(JmsEndpoint endpoint) {
        this.endpoint = endpoint;
        headerFilterStrategy = endpoint.getHeaderFilterStrategy();
        if (headerFilterStrategy == null) {
            headerFilterStrategy = new JmsHeaderFilterStrategy();
        }
    }

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

    public Map<String, Object> extractHeadersFromJms(Message jmsMessage) {
        Map<String, Object> map = new HashMap<String, Object>();
        if (jmsMessage != null) {
            // lets populate the standard JMS message headers
            try {
                map.put("JMSCorrelationID", jmsMessage.getJMSCorrelationID());
                map.put("JMSDeliveryMode", jmsMessage.getJMSDeliveryMode());
                map.put("JMSDestination", jmsMessage.getJMSDestination());
                map.put("JMSExpiration", jmsMessage.getJMSExpiration());
                map.put("JMSMessageID", jmsMessage.getJMSMessageID());
                map.put("JMSPriority", jmsMessage.getJMSPriority());
                map.put("JMSRedelivered", jmsMessage.getJMSRedelivered());
                map.put("JMSReplyTo", jmsMessage.getJMSReplyTo());
                map.put("JMSTimestamp", jmsMessage.getJMSTimestamp());
                map.put("JMSType", jmsMessage.getJMSType());

                // TODO this works around a bug in the ActiveMQ property handling
                map.put("JMSXGroupID", jmsMessage.getStringProperty("JMSXGroupID"));
            } catch (JMSException e) {
                throw new MessageJMSPropertyAccessException(e);
            }

            Enumeration names;
            try {
                names = jmsMessage.getPropertyNames();
            } catch (JMSException e) {
                throw new MessagePropertyNamesAccessException(e);
            }
            while (names.hasMoreElements()) {
                String name = names.nextElement().toString();
                try {
                    Object value = jmsMessage.getObjectProperty(name);
                    if (headerFilterStrategy != null
                            && headerFilterStrategy.applyFilterToExternalHeaders(name, value)) {
                        continue;
                    }

                    // must decode back from safe JMS header name to original header name
                    // when storing on this Camel JmsMessage object.
                    String key = JmsBinding.decodeFromSafeJmsHeaderName(name);
                    map.put(key, value);
                } catch (JMSException e) {
                    throw new MessagePropertyAccessException(name, e);
                }
            }
        }

        return map;
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
        boolean alwaysCopy = (endpoint != null) ? endpoint.getConfiguration().isAlwaysCopyMessage() : false;
        if (!alwaysCopy && camelMessage instanceof JmsMessage) {
            JmsMessage jmsMessage = (JmsMessage)camelMessage;
            if (!jmsMessage.shouldCreateNewMessage()) {
                answer = jmsMessage.getJmsMessage();
            }
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
            appendJmsProperty(jmsMessage, exchange, in, headerName, headerValue);
        }
    }

    public void appendJmsProperty(Message jmsMessage, Exchange exchange, org.apache.camel.Message in,
                                  String headerName, Object headerValue) throws JMSException {
        if (headerName.startsWith("JMS") && !headerName.startsWith("JMSX")) {
            if (headerName.equals("JMSCorrelationID")) {
                jmsMessage.setJMSCorrelationID(ExchangeHelper.convertToType(exchange, String.class,
                    headerValue));
            } else if (headerName.equals("JMSReplyTo") && headerValue != null) {
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
            // must encode to safe JMS header name before setting property on jmsMessage
            String key = encodeToSafeJmsHeaderName(headerName);
            // only primitive headers and strings is allowed as properties
            // see message properties: http://java.sun.com/j2ee/1.4/docs/api/javax/jms/Message.html
            Object value = getValidJMSHeaderValue(headerName, headerValue);
            if (value != null) {
                jmsMessage.setObjectProperty(key, value);
            } else if (LOG.isDebugEnabled()) {
                // okay the value is not a primitive or string so we can not sent it over the wire
                LOG.debug("Ignoring non primitive header: " + headerName + " of class: "
                    + headerValue.getClass().getName() + " with value: " + headerValue);
            }
        }
    }

    /**
     * Strategy to test if the given header is valid according to the JMS spec to be set as a property
     * on the JMS message.
     * <p/>
     * This default implementation will allow:
     * <ul>
     *   <li>any primitives and their counter Objects (Integer, Double etc.)</li>
     *   <li>String and any other litterals, Character, CharSequence</li>
     *   <li>Boolean</li>
     *   <li>BigDecimal and BigInteger</li>
     *   <li>java.util.Date</li>
     * </ul>
     *
     * @param headerName   the header name
     * @param headerValue  the header value
     * @return  the value to use, <tt>null</tt> to ignore this header
     */
    protected Object getValidJMSHeaderValue(String headerName, Object headerValue) {
        if (headerValue.getClass().isPrimitive()) {
            return headerValue;
        } else if (headerValue instanceof String) {
            return headerValue;
        } else if (headerValue instanceof Number) {
            return headerValue;
        } else if (headerValue instanceof Character) {
            return headerValue.toString();
        } else if (headerValue instanceof BigDecimal || headerValue instanceof BigInteger) {
            return headerValue.toString();
        } else if (headerValue instanceof CharSequence) {
            return headerValue.toString();
        } else if (headerValue instanceof Boolean) {
            return headerValue.toString();
        } else if (headerValue instanceof Date) {
            return headerValue.toString();
        }
        return null;
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
                // if MapMessage creation failed then fall back to Object Message
                LOG.warn("Can not populate MapMessage will fall back to ObjectMessage, cause by: " + e.getMessage());
            }
        }
        if (body instanceof String) {
            return session.createTextMessage((String)body);
        }
        if (body instanceof File || body instanceof Reader || body instanceof InputStream || body instanceof ByteBuffer) {
            BytesMessage result = session.createBytesMessage();
            byte[] bytes = context.getTypeConverter().convertTo(byte[].class, body);
            result.writeBytes(bytes);
            return result;
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

    /**
     * @deprecated Please use {@link DefaultHeaderFilterStrategy#getOutFilter()}
     */
    public Set<String> getIgnoreJmsHeaders() {
        if (headerFilterStrategy instanceof DefaultHeaderFilterStrategy) {
            return ((DefaultHeaderFilterStrategy)headerFilterStrategy)
                .getOutFilter();
        } else {
            // Shouldn't get here unless a strategy that isn't an extension of
            // DefaultHeaderPropagationStrategy has been injected.
            return null;
        }
    }

    /**
     * @deprecated Please use {@link DefaultHeaderFilterStrategy#setOutFilter()}
     */
    public void setIgnoreJmsHeaders(Set<String> ignoreJmsHeaders) {
        if (headerFilterStrategy instanceof DefaultHeaderFilterStrategy) {
            ((DefaultHeaderFilterStrategy)headerFilterStrategy)
                .setOutFilter(ignoreJmsHeaders);
        } else {
            // Shouldn't get here unless a strategy that isn't an extension of
            // DefaultHeaderPropagationStrategy has been injected.
        }
    }

    /**
     * Strategy to allow filtering of headers which are put on the JMS message
     * <p/>
     * <b>Note</b>: Currently only supports sending java identifiers as keys
     */
    protected boolean shouldOutputHeader(org.apache.camel.Message camelMessage, String headerName,
                                         Object headerValue) {

        return headerFilterStrategy == null
            || !headerFilterStrategy.applyFilterToCamelHeaders(headerName, headerValue);
    }

    /**
     * Encoder to encode JMS header keys that is that can be sent over the JMS transport.
     * <p/>
     * For example: Sending dots is the key is not allowed. Especially the Bean component has
     * this problem if you want to provide the method name to invoke on the bean.
     * <p/>
     * <b>Note</b>: Currently this encoder is simple as it only supports encoding dots to underscores.
     *
     * @param headerName the header name
     * @return the key to use instead for storing properties and to be for lookup of the same property
     */
    public static String encodeToSafeJmsHeaderName(String headerName) {
        return headerName.replace(".", "_");
    }

    /**
     * Decode operation for the {@link #encodeToSafeJmsHeaderName(String)}.
     *
     * @param headerName the header name
     * @return the original key
     */
    public static String decodeFromSafeJmsHeaderName(String headerName) {
        return headerName.replace("_", ".");
    }

}
