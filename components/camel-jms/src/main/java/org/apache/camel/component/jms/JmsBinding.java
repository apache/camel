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
import java.util.Map.Entry;
import java.util.Set;

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageFormatException;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;

import org.w3c.dom.Node;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StreamCache;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.impl.DefaultExchangeHolder;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.jms.JmsMessageHelper.normalizeDestinationName;
import static org.apache.camel.component.jms.JmsMessageType.Bytes;
import static org.apache.camel.component.jms.JmsMessageType.Map;
import static org.apache.camel.component.jms.JmsMessageType.Object;
import static org.apache.camel.component.jms.JmsMessageType.Text;

/**
 * A Strategy used to convert between a Camel {@link Exchange} and {@link JmsMessage}
 * to and from a JMS {@link Message}
 *
 * @version $Revision$
 */
public class JmsBinding {
    private static final transient Logger LOG = LoggerFactory.getLogger(JmsBinding.class);
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
            // is a custom message converter configured on endpoint then use it instead of doing the extraction
            // based on message type
            if (endpoint != null && endpoint.getMessageConverter() != null) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Extracting body using a custom MessageConverter: " + endpoint.getMessageConverter() + " from JMS message: " + message);
                }
                return endpoint.getMessageConverter().fromMessage(message);
            }

            // if we are configured to not map the jms message then return it as body
            if (endpoint != null && !endpoint.getConfiguration().isMapJmsMessage()) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Option map JMS message is false so using JMS message as body: " + message);
                }
                return message;
            }

            if (message instanceof ObjectMessage) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Extracting body as a ObjectMessage from JMS message: " + message);
                }
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
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Extracting body as a TextMessage from JMS message: " + message);
                }
                TextMessage textMessage = (TextMessage)message;
                return textMessage.getText();
            } else if (message instanceof MapMessage) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Extracting body as a MapMessage from JMS message: " + message);
                }
                return createMapFromMapMessage((MapMessage)message);
            } else if (message instanceof BytesMessage) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Extracting body as a BytesMessage from JMS message: " + message);
                }
                return createByteArrayFromBytesMessage((BytesMessage)message);
            } else if (message instanceof StreamMessage) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Extracting body as a StreamMessage from JMS message: " + message);
                }
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
                map.put("JMSTimestamp", jmsMessage.getJMSTimestamp());

                // to work around OracleAQ not supporting the JMSReplyTo header (CAMEL-2909)
                try {
                    map.put("JMSReplyTo", jmsMessage.getJMSReplyTo());
                } catch (JMSException e) {
                    LOG.trace("Cannot read JMSReplyTo header. Will ignore this exception.", e);
                }
                // to work around OracleAQ not supporting the JMSType header (CAMEL-2909)
                try {
                    map.put("JMSType", jmsMessage.getJMSType());
                } catch (JMSException e) {
                    LOG.trace("Cannot read JMSType header. Will ignore this exception.", e);
                }

                // this works around a bug in the ActiveMQ property handling
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
                    if (headerFilterStrategy != null
                        && headerFilterStrategy.applyFilterToExternalHeaders(name, value, exchange)) {
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
    
    public Object getObjectProperty(Message jmsMessage, String name) throws JMSException {
        // try a direct lookup first
        Object answer = jmsMessage.getObjectProperty(name);
        if (answer == null) {
            // then encode the key and do another lookup
            String key = jmsKeyFormatStrategy.encodeKey(name);
            answer = jmsMessage.getObjectProperty(key);
        }
        return answer;
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
     * @param cause optional exception occurred that should be sent as reply instead of a regular body
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
                // an exception occurred so send it as response
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Will create JmsMessage with caused exception: " + cause);
                }
                // create jms message containing the caused exception
                answer = createJmsMessage(cause, session);
            } else {
                ObjectHelper.notNull(camelMessage, "message");
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
        if (isStandardJMSHeader(headerName)) {
            if (headerName.equals("JMSCorrelationID")) {
                jmsMessage.setJMSCorrelationID(ExchangeHelper.convertToType(exchange, String.class, headerValue));
            } else if (headerName.equals("JMSReplyTo") && headerValue != null) {
                if (headerValue instanceof String) {
                    // if the value is a String we must normalize it first
                    headerValue = normalizeDestinationName((String) headerValue);
                }
                jmsMessage.setJMSReplyTo(ExchangeHelper.convertToType(exchange, Destination.class, headerValue));
            } else if (headerName.equals("JMSType")) {
                jmsMessage.setJMSType(ExchangeHelper.convertToType(exchange, String.class, headerValue));
            } else if (headerName.equals("JMSPriority")) {
                jmsMessage.setJMSPriority(ExchangeHelper.convertToType(exchange, Integer.class, headerValue));
            } else if (headerName.equals("JMSDeliveryMode")) {
                Integer deliveryMode = ExchangeHelper.convertToType(exchange, Integer.class, headerValue);
                jmsMessage.setJMSDeliveryMode(deliveryMode);
                jmsMessage.setIntProperty(JmsConstants.JMS_DELIVERY_MODE, deliveryMode);
            } else if (headerName.equals("JMSExpiration")) {
                jmsMessage.setJMSExpiration(ExchangeHelper.convertToType(exchange, Long.class, headerValue));
            } else if (LOG.isTraceEnabled()) {
                // The following properties are set by the MessageProducer:
                // JMSDestination
                // The following are set on the underlying JMS provider:
                // JMSMessageID, JMSTimestamp, JMSRedelivered
                // log at trace level to not spam log
                LOG.trace("Ignoring JMS header: " + headerName + " with value: " + headerValue);
            }
        } else if (shouldOutputHeader(in, headerName, headerValue, exchange)) {
            // only primitive headers and strings is allowed as properties
            // see message properties: http://java.sun.com/j2ee/1.4/docs/api/javax/jms/Message.html
            Object value = getValidJMSHeaderValue(headerName, headerValue);
            if (value != null) {
                // must encode to safe JMS header name before setting property on jmsMessage
                String key = jmsKeyFormatStrategy.encodeKey(headerName);
                // set the property
                JmsMessageHelper.setProperty(jmsMessage, key, value);
            } else if (LOG.isDebugEnabled()) {
                // okay the value is not a primitive or string so we cannot sent it over the wire
                LOG.debug("Ignoring non primitive header: " + headerName + " of class: "
                    + headerValue.getClass().getName() + " with value: " + headerValue);
            }
        }
    }

    /**
     * Is the given header a standard JMS header
     * @param headerName the header name
     * @return <tt>true</tt> if its a standard JMS header
     */
    protected boolean isStandardJMSHeader(String headerName) {
        if (!headerName.startsWith("JMS")) {
            return false;
        }
        if (headerName.startsWith("JMSX")) {
            return false;
        }
        // IBM WebSphereMQ uses JMS_IBM as special headers
        if (headerName.startsWith("JMS_")) {
            return false;
        }

        // the 4th char must be a letter to be a standard JMS header
        if (headerName.length() > 3) {
            Character fourth = headerName.charAt(3);
            if (Character.isLetter(fourth)) {
                return true;
            }
        }

        return false;
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
        if (headerValue instanceof String) {
            return headerValue;
        } else if (headerValue instanceof Number) {
            return headerValue;
        } else if (headerValue instanceof Character) {
            return headerValue;
        } else if (headerValue instanceof CharSequence) {
            return headerValue.toString();
        } else if (headerValue instanceof Boolean) {
            return headerValue;
        } else if (headerValue instanceof Date) {
            return headerValue.toString();
        }
        return null;
    }

    protected Message createJmsMessage(Exception cause, Session session) throws JMSException {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Using JmsMessageType: " + Object);
        }
        return session.createObjectMessage(cause);
    }

    protected Message createJmsMessage(Exchange exchange, Object body, Map<String, Object> headers, Session session, CamelContext context) throws JMSException {
        JmsMessageType type = null;

        // special for transferExchange
        if (endpoint != null && endpoint.isTransferExchange()) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Option transferExchange=true so we use JmsMessageType: Object");
            }
            Serializable holder = DefaultExchangeHolder.marshal(exchange);
            return session.createObjectMessage(holder);
        }

        // use a custom message converter
        if (endpoint != null && endpoint.getMessageConverter() != null) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Creating JmsMessage using a custom MessageConverter: " + endpoint.getMessageConverter() + " with body: " + body);
            }
            return endpoint.getMessageConverter().toMessage(body, session);
        }

        // check if header have a type set, if so we force to use it
        if (headers.containsKey(JmsConstants.JMS_MESSAGE_TYPE)) {
            type = context.getTypeConverter().convertTo(JmsMessageType.class, headers.get(JmsConstants.JMS_MESSAGE_TYPE));
        } else if (endpoint != null && endpoint.getConfiguration().getJmsMessageType() != null) {
            // force a specific type from the endpoint configuration
            type = endpoint.getConfiguration().getJmsMessageType();
        } else {
            type = getJMSMessageTypeForBody(exchange, body, headers, session, context);
        }

        // create the JmsMessage based on the type
        if (type != null) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Using JmsMessageType: " + type);
            }
            return createJmsMessageForType(exchange, body, headers, session, context, type);
        }

        // warn if the body could not be mapped
        if (body != null && LOG.isWarnEnabled()) {
            LOG.warn("Cannot determine specific JmsMessage type to use from body class."
                    + " Will use generic JmsMessage."
                    + " Body class: " + ObjectHelper.classCanonicalName(body)
                    + ". If you want to send a POJO then your class might need to implement java.io.Serializable"
                    + ", or you can force a specific type by setting the jmsMessageType option on the JMS endpoint.");
        }

        // return a default message
        return session.createMessage();
    }

    /**
     * Return the {@link JmsMessageType} 
     * 
     * @return type or null if no mapping was possible
     */
    protected JmsMessageType getJMSMessageTypeForBody(Exchange exchange, Object body, Map<String, Object> headers, Session session, CamelContext context) {
        JmsMessageType type = null;
        // let body determine the type
        if (body instanceof Node || body instanceof String) {
            type = Text;
        } else if (body instanceof byte[] || body instanceof GenericFile || body instanceof File || body instanceof Reader
                || body instanceof InputStream || body instanceof ByteBuffer || body instanceof StreamCache) {
            type = Bytes;
        } else if (body instanceof Map) {
            type = Map;
        } else if (body instanceof Serializable) {
            type = Object;            
        } else if (exchange.getContext().getTypeConverter().convertTo(File.class, body) != null 
                || exchange.getContext().getTypeConverter().convertTo(InputStream.class, body) != null) {
            type = Bytes;
        }
        return type;
    }
    
    /**
     * 
     * Create the {@link Message} 
     * 
     * @return jmsMessage or null if the mapping was not successfully
     */
    protected Message createJmsMessageForType(Exchange exchange, Object body, Map<String, Object> headers, Session session, CamelContext context, JmsMessageType type) throws JMSException {
        switch (type) {
        case Text: {
            TextMessage message = session.createTextMessage();
            String payload = context.getTypeConverter().convertTo(String.class, exchange, body);
            message.setText(payload);
            return message;
        }
        case Bytes: {
            BytesMessage message = session.createBytesMessage();
            byte[] payload = context.getTypeConverter().convertTo(byte[].class, exchange, body);
            message.writeBytes(payload);
            return message;
        }
        case Map: {
            MapMessage message = session.createMapMessage();
            Map payload = context.getTypeConverter().convertTo(Map.class, exchange, body);
            populateMapMessage(message, payload, context);
            return message;
        }
        case Object:
            Serializable payload;
            try {
                payload = context.getTypeConverter().mandatoryConvertTo(Serializable.class, exchange, body);
            } catch (NoTypeConversionAvailableException e) {
                // cannot convert to serializable then thrown an exception to avoid sending a null message
                JMSException cause = new MessageFormatException(e.getMessage());
                cause.initCause(e);
                throw cause;
            }
            return session.createObjectMessage(payload);
        default:
            break;
        }
        return null;
    }
    /**
     * Populates a {@link MapMessage} from a {@link Map} instance.
     */
    protected void populateMapMessage(MapMessage message, Map<?, ?> map, CamelContext context)
        throws JMSException {
        for (Entry<?, ?> entry : map.entrySet()) {
            String keyString = CamelContextHelper.convertTo(context, String.class, entry.getKey());
            if (keyString != null) {
                message.setObject(keyString, entry.getValue());
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
