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
package org.apache.camel.component.sjms.jms;

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
import org.apache.camel.WrappedFile;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultExchangeHolder;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.sjms.jms.JmsMessageHelper.normalizeDestinationName;

/**
 * A Strategy used to convert between a Camel {@link org.apache.camel.Exchange} and {@link org.apache.camel.Message}
 * to and from a JMS {@link javax.jms.Message}
 */
public class JmsBinding {

    private static final Logger LOG = LoggerFactory.getLogger(JmsBinding.class);
    private final boolean mapJmsMessage;
    private final boolean allowNullBody;
    private final HeaderFilterStrategy headerFilterStrategy;
    private final JmsKeyFormatStrategy jmsJmsKeyFormatStrategy;
    private final MessageCreatedStrategy messageCreatedStrategy;

    public JmsBinding(boolean mapJmsMessage, boolean allowNullBody,
                      HeaderFilterStrategy headerFilterStrategy, JmsKeyFormatStrategy jmsJmsKeyFormatStrategy,
                      MessageCreatedStrategy messageCreatedStrategy) {
        this.mapJmsMessage = mapJmsMessage;
        this.allowNullBody = allowNullBody;
        this.headerFilterStrategy = headerFilterStrategy;
        this.jmsJmsKeyFormatStrategy = jmsJmsKeyFormatStrategy;
        this.messageCreatedStrategy = messageCreatedStrategy;
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

            // TODO: new options to support

            // is a custom message converter configured on endpoint then use it instead of doing the extraction
            // based on message type
/*            if (endpoint != null && endpoint.getMessageConverter() != null) {
                if (log.isTraceEnabled()) {
                    log.trace("Extracting body using a custom MessageConverter: {} from JMS message: {}", endpoint.getMessageConverter(), message);
                }
                return endpoint.getMessageConverter().fromMessage(message);
            }
*/
            // if we are configured to not map the jms message then return it as body
            if (!mapJmsMessage) {
                LOG.trace("Option map JMS message is false so using JMS message as body: {}", message);
                return message;
            }

            if (message instanceof ObjectMessage) {
                LOG.trace("Extracting body as a ObjectMessage from JMS message: {}", message);
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
                LOG.trace("Extracting body as a TextMessage from JMS message: {}", message);
                TextMessage textMessage = (TextMessage)message;
                return textMessage.getText();
            } else if (message instanceof MapMessage) {
                LOG.trace("Extracting body as a MapMessage from JMS message: {}", message);
                return createMapFromMapMessage((MapMessage)message);
            } else if (message instanceof BytesMessage) {
                LOG.trace("Extracting body as a BytesMessage from JMS message: {}", message);
                return createByteArrayFromBytesMessage((BytesMessage)message);
            } else if (message instanceof StreamMessage) {
                LOG.trace("Extracting body as a StreamMessage from JMS message: {}", message);
                return message;
            } else {
                return null;
            }
        } catch (JMSException e) {
            throw new RuntimeCamelException("Failed to extract body due to: " + e + ". Message: " + message, e);
        }
    }

    public Map<String, Object> extractHeadersFromJms(Message jmsMessage, Exchange exchange) {
        Map<String, Object> map = new HashMap<>();
        if (jmsMessage != null) {
            // lets populate the standard JMS message headers
            try {
                map.put("JMSCorrelationID", jmsMessage.getJMSCorrelationID());
                map.put("JMSCorrelationIDAsBytes", JmsMessageHelper.getJMSCorrelationIDAsBytes(jmsMessage));
                map.put("JMSDeliveryMode", jmsMessage.getJMSDeliveryMode());
                map.put("JMSDestination", jmsMessage.getJMSDestination());
                map.put("JMSExpiration", jmsMessage.getJMSExpiration());
                map.put("JMSMessageID", jmsMessage.getJMSMessageID());
                map.put("JMSPriority", jmsMessage.getJMSPriority());
                map.put("JMSRedelivered", jmsMessage.getJMSRedelivered());
                map.put("JMSTimestamp", jmsMessage.getJMSTimestamp());

                map.put("JMSReplyTo", JmsMessageHelper.getJMSReplyTo(jmsMessage));
                map.put("JMSType", JmsMessageHelper.getJMSType(jmsMessage));

                // this works around a bug in the ActiveMQ property handling
                map.put(JmsConstants.JMSX_GROUP_ID, JmsMessageHelper.getStringProperty(jmsMessage, JmsConstants.JMSX_GROUP_ID));
                map.put("JMSXUserID", JmsMessageHelper.getStringProperty(jmsMessage, "JMSXUserID"));
            } catch (JMSException e) {
                throw new RuntimeCamelException(e);
            }

            Enumeration<?> names;
            try {
                names = jmsMessage.getPropertyNames();
            } catch (JMSException e) {
                throw new RuntimeCamelException(e);
            }
            while (names.hasMoreElements()) {
                String name = names.nextElement().toString();
                try {
                    Object value = JmsMessageHelper.getProperty(jmsMessage, name);
                    if (headerFilterStrategy != null
                            && headerFilterStrategy.applyFilterToExternalHeaders(name, value, exchange)) {
                        continue;
                    }

                    // must decode back from safe JMS header name to original header name
                    // when storing on this Camel JmsMessage object.
                    String key = jmsJmsKeyFormatStrategy.decodeKey(name);
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
            String key = jmsJmsKeyFormatStrategy.encodeKey(name);
            answer = jmsMessage.getObjectProperty(key);
        }
        return answer;
    }

    protected byte[] createByteArrayFromBytesMessage(BytesMessage message) throws JMSException {
        if (message.getBodyLength() > Integer.MAX_VALUE) {
            LOG.warn("Length of BytesMessage is too long: {}", message.getBodyLength());
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
        Message answer = makeJmsMessage(exchange, exchange.getIn().getBody(), exchange.getIn().getHeaders(), session, null);
        if (answer != null && messageCreatedStrategy != null) {
            messageCreatedStrategy.onMessageCreated(answer, session, exchange, null);
        }
        return answer;
    }

    /**
     * Creates a JMS message from the Camel exchange and message
     *
     * @param exchange the current exchange
     * @param body the message body
     * @param headers the message headers
     * @param session the JMS session used to create the message
     * @param cause optional exception occurred that should be sent as reply instead of a regular body
     * @return a newly created JMS Message instance containing the
     * @throws JMSException if the message could not be created
     */
    public Message makeJmsMessage(Exchange exchange, Object body, Map headers, Session session, Exception cause) throws JMSException {
        Message answer = null;

        // TODO: look at supporting some of these options

/*        boolean alwaysCopy = endpoint != null && endpoint.getConfiguration().isAlwaysCopyMessage();
        boolean force = endpoint != null && endpoint.getConfiguration().isForceSendOriginalMessage();
        if (!alwaysCopy && camelMessage instanceof JmsMessage) {
            JmsMessage jmsMessage = (JmsMessage)camelMessage;
            if (!jmsMessage.shouldCreateNewMessage() || force) {
                answer = jmsMessage.getJmsMessage();

                if (!force) {
                    // answer must match endpoint type
                    JmsMessageType type = endpoint != null ? endpoint.getConfiguration().getJmsMessageType() : null;
                    if (type != null && answer != null) {
                        if (type == JmsMessageType.Text) {
                            answer = answer instanceof TextMessage ? answer : null;
                        } else if (type == JmsMessageType.Bytes) {
                            answer = answer instanceof BytesMessage ? answer : null;
                        } else if (type == JmsMessageType.Map) {
                            answer = answer instanceof MapMessage ? answer : null;
                        } else if (type == JmsMessageType.Object) {
                            answer = answer instanceof ObjectMessage ? answer : null;
                        } else if (type == JmsMessageType.Stream) {
                            answer = answer instanceof StreamMessage ? answer : null;
                        }
                    }
                }
            }
        }
*/

        if (answer == null) {
            if (cause != null) {
                // an exception occurred so send it as response
                LOG.debug("Will create JmsMessage with caused exception: {}", cause);
                // create jms message containing the caused exception
                answer = createJmsMessage(cause, session);
            } else {
                // create regular jms message using the camel message body
                answer = createJmsMessage(exchange, body, headers, session, exchange.getContext());
                appendJmsProperties(answer, exchange, headers);
            }
        }

        if (answer != null && messageCreatedStrategy != null) {
            messageCreatedStrategy.onMessageCreated(answer, session, exchange, null);
        }
        return answer;
    }

    /**
     * Appends the JMS headers from the Camel {@link Message}
     */
    public void appendJmsProperties(Message jmsMessage, Exchange exchange, Map<String, Object> headers) throws JMSException {
        if (headers != null) {
            Set<Map.Entry<String, Object>> entries = headers.entrySet();
            for (Map.Entry<String, Object> entry : entries) {
                String headerName = entry.getKey();
                Object headerValue = entry.getValue();
                appendJmsProperty(jmsMessage, exchange, headerName, headerValue);
            }
        }
    }

    public void appendJmsProperty(Message jmsMessage, Exchange exchange, String headerName, Object headerValue) throws JMSException {
        if (isStandardJMSHeader(headerName)) {
            if (headerName.equals("JMSCorrelationID")) {
                jmsMessage.setJMSCorrelationID(ExchangeHelper.convertToType(exchange, String.class, headerValue));
            } else if (headerName.equals("JMSReplyTo") && headerValue != null) {
                if (headerValue instanceof String) {
                    // if the value is a String we must normalize it first, and must include the prefix
                    // as ActiveMQ requires that when converting the String to a javax.jms.Destination type
                    headerValue = normalizeDestinationName((String) headerValue, true);
                }
                Destination replyTo = ExchangeHelper.convertToType(exchange, Destination.class, headerValue);
                JmsMessageHelper.setJMSReplyTo(jmsMessage, replyTo);
            } else if (headerName.equals("JMSType")) {
                jmsMessage.setJMSType(ExchangeHelper.convertToType(exchange, String.class, headerValue));
            } else if (headerName.equals("JMSPriority")) {
                jmsMessage.setJMSPriority(ExchangeHelper.convertToType(exchange, Integer.class, headerValue));
            } else if (headerName.equals("JMSDeliveryMode")) {
                JmsMessageHelper.setJMSDeliveryMode(exchange, jmsMessage, headerValue);
            } else if (headerName.equals("JMSExpiration")) {
                jmsMessage.setJMSExpiration(ExchangeHelper.convertToType(exchange, Long.class, headerValue));
            } else {
                // The following properties are set by the MessageProducer:
                // JMSDestination
                // The following are set on the underlying JMS provider:
                // JMSMessageID, JMSTimestamp, JMSRedelivered
                // log at trace level to not spam log
                LOG.trace("Ignoring JMS header: {} with value: {}", headerName, headerValue);
            }
        } else if (shouldOutputHeader(headerName, headerValue, exchange)) {
            // only primitive headers and strings is allowed as properties
            // see message properties: http://java.sun.com/j2ee/1.4/docs/api/javax/jms/Message.html
            Object value = getValidJMSHeaderValue(headerName, headerValue);
            if (value != null) {
                // must encode to safe JMS header name before setting property on jmsMessage
                String key = jmsJmsKeyFormatStrategy.encodeKey(headerName);
                // set the property
                JmsMessageHelper.setProperty(jmsMessage, key, value);
            } else if (LOG.isDebugEnabled()) {
                // okay the value is not a primitive or string so we cannot sent it over the wire
                LOG.debug("Ignoring non primitive header: {} of class: {} with value: {}",
                        new Object[]{headerName, headerValue.getClass().getName(), headerValue});
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
        // vendors will use JMS_XXX as their special headers (where XXX is vendor name, such as JMS_IBM)
        if (headerName.startsWith("JMS_")) {
            return false;
        }

        // the 4th char must be a letter to be a standard JMS header
        if (headerName.length() > 3) {
            char fourth = headerName.charAt(3);
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
        } else if (headerValue instanceof BigInteger) {
            return headerValue.toString();
        } else if (headerValue instanceof BigDecimal) {
            return headerValue.toString();
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
        LOG.trace("Using JmsMessageType: {}", JmsMessageType.Object);
        Message answer = session.createObjectMessage(cause);
        // ensure default delivery mode is used by default
        answer.setJMSDeliveryMode(Message.DEFAULT_DELIVERY_MODE);
        return answer;
    }

    protected Message createJmsMessage(Exchange exchange, Object body, Map<String, Object> headers, Session session, CamelContext context) throws JMSException {
        JmsMessageType type = null;

        // TODO: support some of these options?

/*        // special for transferExchange
        if (endpoint != null && endpoint.isTransferExchange()) {
            log.trace("Option transferExchange=true so we use JmsMessageType: Object");
            Serializable holder = DefaultExchangeHolder.marshal(exchange);
            Message answer = session.createObjectMessage(holder);
            // ensure default delivery mode is used by default
            answer.setJMSDeliveryMode(Message.DEFAULT_DELIVERY_MODE);
            return answer;
        }

        // use a custom message converter
        if (endpoint != null && endpoint.getMessageConverter() != null) {
            if (log.isTraceEnabled()) {
                log.trace("Creating JmsMessage using a custom MessageConverter: {} with body: {}", endpoint.getMessageConverter(), body);
            }
            return endpoint.getMessageConverter().toMessage(body, session);
        }
*/
        // check if header have a type set, if so we force to use it
/*
        if (headers.containsKey(JmsConstants.JMS_MESSAGE_TYPE)) {
            type = context.getTypeConverter().convertTo(JmsMessageType.class, headers.get(JmsConstants.JMS_MESSAGE_TYPE));
        } else if (endpoint != null && endpoint.getConfiguration().getJmsMessageType() != null) {
            // force a specific type from the endpoint configuration
            type = endpoint.getConfiguration().getJmsMessageType();
        } else {
*/
        type = getJMSMessageTypeForBody(exchange, body, headers, session, context);
        //}

        // create the JmsMessage based on the type
        if (type != null) {
            if (body == null && !allowNullBody) {
                throw new JMSException("Cannot send message as message body is null, and option allowNullBody is false.");
            }
            LOG.trace("Using JmsMessageType: {}", type);
            Message answer = createJmsMessageForType(exchange, body, headers, session, context, type);
            // ensure default delivery mode is used by default
            answer.setJMSDeliveryMode(Message.DEFAULT_DELIVERY_MODE);
            return answer;
        }

        // check for null body
        if (body == null && !allowNullBody) {
            throw new JMSException("Cannot send message as message body is null, and option allowNullBody is false.");
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
        Message answer = session.createMessage();
        // ensure default delivery mode is used by default
        answer.setJMSDeliveryMode(Message.DEFAULT_DELIVERY_MODE);
        return answer;
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
            type = JmsMessageType.Text;
        } else if (body instanceof byte[] || body instanceof WrappedFile || body instanceof File || body instanceof Reader
                || body instanceof InputStream || body instanceof ByteBuffer || body instanceof StreamCache) {
            type = JmsMessageType.Bytes;
        } else if (body instanceof Map) {
            type = JmsMessageType.Map;
        } else if (body instanceof Serializable) {
            type = JmsMessageType.Object;
        } else if (exchange.getContext().getTypeConverter().tryConvertTo(File.class, body) != null
                || exchange.getContext().getTypeConverter().tryConvertTo(InputStream.class, body) != null) {
            type = JmsMessageType.Bytes;
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
                if (body != null) {
                    String payload = context.getTypeConverter().convertTo(String.class, exchange, body);
                    message.setText(payload);
                }
                return message;
            }
            case Bytes: {
                BytesMessage message = session.createBytesMessage();
                if (body != null) {
                    byte[] payload = context.getTypeConverter().convertTo(byte[].class, exchange, body);
                    message.writeBytes(payload);
                }
                return message;
            }
            case Map: {
                MapMessage message = session.createMapMessage();
                if (body != null) {
                    Map<?, ?> payload = context.getTypeConverter().convertTo(Map.class, exchange, body);
                    populateMapMessage(message, payload, context);
                }
                return message;
            }
            case Object:
                ObjectMessage message = session.createObjectMessage();
                if (body != null) {
                    try {
                        Serializable payload = context.getTypeConverter().mandatoryConvertTo(Serializable.class, exchange, body);
                        message.setObject(payload);
                    } catch (NoTypeConversionAvailableException e) {
                        // cannot convert to serializable then thrown an exception to avoid sending a null message
                        JMSException cause = new MessageFormatException(e.getMessage());
                        cause.initCause(e);
                        throw cause;
                    }
                }
                return message;
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
        for (Map.Entry<?, ?> entry : map.entrySet()) {
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
        Map<String, Object> answer = new HashMap<>();
        Enumeration<?> names = message.getMapNames();
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
    protected boolean shouldOutputHeader(String headerName, Object headerValue, Exchange exchange) {
        return headerFilterStrategy == null
                || !headerFilterStrategy.applyFilterToCamelHeaders(headerName, headerValue, exchange);
    }

}
