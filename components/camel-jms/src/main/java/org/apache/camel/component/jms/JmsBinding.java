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

import org.w3c.dom.Node;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.impl.DefaultExchangeHolder;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static org.apache.camel.component.jms.JmsMessageType.Bytes;
import static org.apache.camel.component.jms.JmsMessageType.Map;
import static org.apache.camel.component.jms.JmsMessageType.Object;
import static org.apache.camel.component.jms.JmsMessageType.Text;

/**
 * A Strategy used to convert between a Camel {@link JmsExchange} and {@link JmsMessage}
 * to and from a JMS {@link Message}
 *
 * @version $Revision$
 */
public class JmsBinding {
    private static final transient Log LOG = LogFactory.getLog(JmsBinding.class);
    private final JmsEndpoint endpoint;
    private final HeaderFilterStrategy headerFilterStrategy;
    private final JmsKeyFormatStrategy jmsKeyFormatStrategy;

    public JmsBinding() {
        this.endpoint = null;
        headerFilterStrategy = new JmsHeaderFilterStrategy();
        jmsKeyFormatStrategy = new DefaultJmsKeyFormatStrategy();
    }

    public JmsBinding(JmsEndpoint endpoint) {
        this.endpoint = endpoint;
        if (endpoint.getHeaderFilterStrategy() != null) {
            headerFilterStrategy = endpoint.getHeaderFilterStrategy();
        } else {
            headerFilterStrategy = new JmsHeaderFilterStrategy();
        }
        if (endpoint.getJmsKeyFormatStrategy() != null) {
            jmsKeyFormatStrategy = endpoint.getJmsKeyFormatStrategy();
        } else {
            jmsKeyFormatStrategy = new DefaultJmsKeyFormatStrategy();
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
                Object payload = objectMessage.getObject();
                if (payload instanceof DefaultExchangeHolder) {
                    DefaultExchangeHolder holder = (DefaultExchangeHolder) payload;
                    DefaultExchangeHolder.unmarshal(exchange, holder);
                    return exchange.getIn().getBody();
                } else {
                    return objectMessage.getObject();
                }
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
            throw new RuntimeCamelException("Failed to extract body due to: " + e + ". Message: " + message, e);
        }
    }

    public Map<String, Object> extractHeadersFromJms(Message jmsMessage, Exchange exchange) {
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
                throw new RuntimeCamelException(e);
            }

            Enumeration names;
            try {
                names = jmsMessage.getPropertyNames();
            } catch (JMSException e) {
                throw new RuntimeCamelException(e);
            }
            while (names.hasMoreElements()) {
                String name = names.nextElement().toString();
                try {
                    Object value = jmsMessage.getObjectProperty(name);
                    if (headerFilterStrategy != null && 
                        headerFilterStrategy.applyFilterToExternalHeaders(name, value, exchange)) {
                        continue;
                    }

                    // must decode back from safe JMS header name to original header name
                    // when storing on this Camel JmsMessage object.
                    String key = jmsKeyFormatStrategy.decodeKey(name);
                    map.put(key, value);
                } catch (JMSException e) {
                    throw new RuntimeCamelException(name, e);
                }
            }
        }

        return map;
    }

    protected byte[] createByteArrayFromBytesMessage(BytesMessage message) throws JMSException {
        if (message.getBodyLength() > Integer.MAX_VALUE) {
            LOG.warn("Length of BytesMessage is too long: " + message.getBodyLength());
            return null;
        }
        byte[] result = new byte[(int)message.getBodyLength()];
        message.readBytes(result);
        return result;
    }

    /**
     * Creates a JMS message from the Camel exchange and message
     *
     * @param exchange the current exchange
     * @param session the JMS session used to create the message
     * @return a newly created JMS Message instance containing the
     * @throws JMSException if the message could not be created
     */
    public Message makeJmsMessage(Exchange exchange, Session session) throws JMSException {
        return makeJmsMessage(exchange, exchange.getIn(), session, null);
    }

    /**
     * Creates a JMS message from the Camel exchange and message
     *
     * @param exchange the current exchange
     * @param camelMessage the body to make a javax.jms.Message as
     * @param session the JMS session used to create the message
     * @param cause optional exception occured that should be sent as reply instead of a regular body
     * @return a newly created JMS Message instance containing the
     * @throws JMSException if the message could not be created
     */
    public Message makeJmsMessage(Exchange exchange, org.apache.camel.Message camelMessage, Session session, Exception cause) throws JMSException {
        Message answer = null;

        boolean alwaysCopy = endpoint != null && endpoint.getConfiguration().isAlwaysCopyMessage();
        if (!alwaysCopy && camelMessage instanceof JmsMessage) {
            JmsMessage jmsMessage = (JmsMessage)camelMessage;
            if (!jmsMessage.shouldCreateNewMessage()) {
                answer = jmsMessage.getJmsMessage();
            }
        }

        if (answer == null) {
            if (cause != null) {
                // an exception occured so send it as response
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Will create JmsMessage with caused exception: " + cause);
                }
                // create jms message containg the caused exception
                answer = createJmsMessage(cause, session);
            } else {
                ObjectHelper.notNull(camelMessage, "message body");
                // create regular jms message using the camel message body
                answer = createJmsMessage(exchange, camelMessage.getBody(), camelMessage.getHeaders(), session, exchange.getContext());
                appendJmsProperties(answer, exchange, camelMessage);
            }
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
    public void appendJmsProperties(Message jmsMessage, Exchange exchange, org.apache.camel.Message in) throws JMSException {
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
                jmsMessage.setJMSCorrelationID(ExchangeHelper.convertToType(exchange, String.class, headerValue));
            } else if (headerName.equals("JMSReplyTo") && headerValue != null) {
                jmsMessage.setJMSReplyTo(ExchangeHelper.convertToType(exchange, Destination.class, headerValue));
            } else if (headerName.equals("JMSType")) {
                jmsMessage.setJMSType(ExchangeHelper.convertToType(exchange, String.class, headerValue));
            } else if (LOG.isDebugEnabled()) {
                // The following properties are set by the MessageProducer:
                // JMSDeliveryMode, JMSDestination, JMSExpiration, JMSPriorit
                // The following are set on the underlying JMS provider:
                // JMSMessageID, JMSTimestamp, JMSRedelivered
                LOG.debug("Ignoring JMS header: " + headerName + " with value: " + headerValue);
            }
        } else if (shouldOutputHeader(in, headerName, headerValue, exchange)) {
            // only primitive headers and strings is allowed as properties
            // see message properties: http://java.sun.com/j2ee/1.4/docs/api/javax/jms/Message.html
            Object value = getValidJMSHeaderValue(headerName, headerValue);
            if (value != null) {
                // must encode to safe JMS header name before setting property on jmsMessage
                String key = endpoint.getJmsKeyFormatStrategy().encodeKey(headerName);
                jmsMessage.setObjectProperty(key, value);
            } else if (LOG.isDebugEnabled()) {
                // okay the value is not a primitive or string so we cannot sent it over the wire
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
     *   <li>String and any other literals, Character, CharSequence</li>
     *   <li>Boolean</li>
     *   <li>Number</li>
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
        } else if (headerValue instanceof CharSequence) {
            return headerValue.toString();
        } else if (headerValue instanceof Boolean) {
            return headerValue.toString();
        } else if (headerValue instanceof Date) {
            return headerValue.toString();
        }
        return null;
    }

    protected Message createJmsMessage(Exception cause, Session session) throws JMSException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Using JmsMessageType: " + Object);
        }
        return session.createObjectMessage(cause);
    }

    protected Message createJmsMessage(Exchange exchange, Object body, Map<String, Object> headers, Session session, CamelContext context) throws JMSException {
        JmsMessageType type = null;

        // special for transferExchange
        if (endpoint != null && endpoint.isTransferExchange()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Option transferExchange=true so we use JmsMessageType: Object");
            }
            Serializable holder = DefaultExchangeHolder.marshal(exchange);
            return session.createObjectMessage(holder);
        }

        // check if header have a type set, if so we force to use it
        if (headers.containsKey(JmsConstants.JMS_MESSAGE_TYPE)) {
            type = context.getTypeConverter().convertTo(JmsMessageType.class, headers.get(JmsConstants.JMS_MESSAGE_TYPE));
        } else if (endpoint != null && endpoint.getConfiguration().getJmsMessageType() != null) {
            // force a specific type from the endpoint configuration
            type = endpoint.getConfiguration().getJmsMessageType();
        } else {
            // let body deterime the type
            if (body instanceof Node || body instanceof String) {
                type = Text;
            } else if (body instanceof byte[] || body instanceof GenericFile || body instanceof File || body instanceof Reader
                    || body instanceof InputStream || body instanceof ByteBuffer) {
                type = Bytes;
            } else if (body instanceof Map) {
                type = Map;
            } else if (body instanceof Serializable) {
                type = Object;
            }
        }

        // create the JmsMessage based on the type
        if (type != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Using JmsMessageType: " + type);
            }

            switch (type) {
            case Text: {
                TextMessage message = session.createTextMessage();
                String payload = context.getTypeConverter().convertTo(String.class, body);
                message.setText(payload);
                return message;
            }
            case Bytes: {
                BytesMessage message = session.createBytesMessage();
                byte[] payload = context.getTypeConverter().convertTo(byte[].class, body);
                message.writeBytes(payload);
                return message;
            }
            case Map: {
                MapMessage message = session.createMapMessage();
                Map payload = context.getTypeConverter().convertTo(Map.class, body);
                populateMapMessage(message, payload, context);
                return message;
            }
            case Object:
                return session.createObjectMessage((Serializable)body);
            case Strem:
                // TODO: Stream is not supported
                break;
            default:
                break;
            }
        }

        // TODO: should we throw an exception instead?
        if (LOG.isDebugEnabled()) {
            LOG.debug("Could not determine specific JmsMessage type to use from body."
                    + " Will use generic JmsMessage. Body class: " + body.getClass().getCanonicalName());
        }

        // return a default message
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
     * Strategy to allow filtering of headers which are put on the JMS message
     * <p/>
     * <b>Note</b>: Currently only supports sending java identifiers as keys
     */
    protected boolean shouldOutputHeader(org.apache.camel.Message camelMessage, String headerName,
                                         Object headerValue, Exchange exchange) {
        return headerFilterStrategy == null
            || !headerFilterStrategy.applyFilterToCamelHeaders(headerName, headerValue, exchange);
    }

}
