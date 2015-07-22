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
package org.apache.camel.component.sjms.jms;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.jms.BytesMessage;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.component.sjms.SjmsConstants;
import org.apache.camel.component.sjms.SjmsEndpoint;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for {@link javax.jms.Message}.
 */
public final class JmsMessageHelper implements JmsConstants {

    private static final Logger LOGGER = LoggerFactory.getLogger(JmsMessageHelper.class);

    private JmsMessageHelper() {
    }

    public static Exchange createExchange(Message message, Endpoint endpoint) {
        return createExchange(message, endpoint, null);
    }

    /**
     * Creates an Exchange from a JMS Message.
     * @param message The JMS message.
     * @param endpoint The Endpoint to use to create the Exchange object.
     * @param keyFormatStrategy the a {@link KeyFormatStrategy} to used to
     *                          format keys in a JMS 1.1 compliant manner. If null the
     *                          {@link DefaultJmsKeyFormatStrategy} will be used.
     * @return Populated Exchange.
     */
    public static Exchange createExchange(Message message, Endpoint endpoint, KeyFormatStrategy keyFormatStrategy) {
        Exchange exchange = endpoint.createExchange();
        KeyFormatStrategy initialisedKeyFormatStrategy = (keyFormatStrategy == null)
                ? new DefaultJmsKeyFormatStrategy() : keyFormatStrategy;
        return populateExchange(message, exchange, false, initialisedKeyFormatStrategy);
    }

    @SuppressWarnings("unchecked")
    public static Exchange populateExchange(Message message, Exchange exchange, boolean out, KeyFormatStrategy keyFormatStrategy) {
        try {
            setJmsMessageHeaders(message, exchange, out, keyFormatStrategy);
            if (message != null) {
                // convert to JMS Message of the given type

                DefaultMessage bodyMessage;
                if (out) {
                    bodyMessage = (DefaultMessage) exchange.getOut();
                } else {
                    bodyMessage = (DefaultMessage) exchange.getIn();
                }
                switch (JmsMessageHelper.discoverJmsMessageType(message)) {
                case Bytes:
                    BytesMessage bytesMessage = (BytesMessage) message;
                    if (bytesMessage.getBodyLength() > Integer.MAX_VALUE) {
                        LOGGER.warn("Length of BytesMessage is too long: {}", bytesMessage.getBodyLength());
                        return null;
                    }
                    byte[] result = new byte[(int) bytesMessage.getBodyLength()];
                    bytesMessage.readBytes(result);
                    bodyMessage.setHeader(SjmsConstants.JMS_MESSAGE_TYPE, JmsMessageType.Bytes);
                    bodyMessage.setBody(result);
                    break;
                case Map:
                    Map<String, Object> body = new HashMap<String, Object>();
                    MapMessage mapMessage = (MapMessage) message;
                    Enumeration<String> names = mapMessage.getMapNames();
                    while (names.hasMoreElements()) {
                        String key = names.nextElement();
                        Object value = mapMessage.getObject(key);
                        body.put(key, value);
                    }
                    bodyMessage.setHeader(SjmsConstants.JMS_MESSAGE_TYPE, JmsMessageType.Map);
                    bodyMessage.setBody(body);
                    break;
                case Object:
                    ObjectMessage objMsg = (ObjectMessage) message;
                    bodyMessage.setHeader(SjmsConstants.JMS_MESSAGE_TYPE, JmsMessageType.Object);
                    bodyMessage.setBody(objMsg.getObject());
                    break;
                case Text:
                    TextMessage textMsg = (TextMessage) message;
                    bodyMessage.setHeader(SjmsConstants.JMS_MESSAGE_TYPE, JmsMessageType.Text);
                    bodyMessage.setBody(textMsg.getText());
                    break;
                case Stream:
                    StreamMessage streamMessage = (StreamMessage) message;
                    List<Object> list = new ArrayList<Object>();
                    Object obj;
                    while ((obj = streamMessage.readObject()) != null) {
                        list.add(obj);
                    }
                    bodyMessage.setHeader(SjmsConstants.JMS_MESSAGE_TYPE, JmsMessageType.Stream);
                    bodyMessage.setBody(list);
                    break;
                case Message:
                default:
                    // Do nothing. Only set the headers for an empty message
                    bodyMessage.setBody(message);
                    break;
                }
            }
        } catch (Exception e) {
            exchange.setException(e);
        }
        return exchange;
    }

    public static Message createMessage(Exchange exchange, Session session, SjmsEndpoint endpoint) throws Exception {
        Message answer;
        Object body;
        Map<String, Object> bodyHeaders;

        if (exchange.getOut().getBody() != null) {
            body = exchange.getOut().getBody();
            bodyHeaders = new HashMap<String, Object>(exchange.getOut().getHeaders());
        } else {
            body = exchange.getIn().getBody();
            bodyHeaders = new HashMap<String, Object>(exchange.getIn().getHeaders());
        }

        answer = createMessage(session, body, bodyHeaders, endpoint);
        return answer;
    }

    public static Message createMessage(Session session, Object payload, Map<String, Object> messageHeaders, SjmsEndpoint endpoint) throws Exception {
        return createMessage(session, payload, messageHeaders, endpoint.isAllowNullBody(), endpoint.getJmsKeyFormatStrategy(), endpoint.getCamelContext().getTypeConverter());
    }

    private static Message createMessage(Session session, Object payload, Map<String, Object> messageHeaders, boolean allowNullBody,
                                         KeyFormatStrategy keyFormatStrategy, TypeConverter typeConverter) throws Exception {
        Message answer = null;
        JmsMessageType messageType = JmsMessageHelper.discoverMessageTypeFromPayload(payload);

        switch (messageType) {
        case Bytes:
            BytesMessage bytesMessage = session.createBytesMessage();
            byte[] bytesToWrite = typeConverter.convertTo(byte[].class, payload);
            bytesMessage.writeBytes(bytesToWrite);
            answer = bytesMessage;
            break;
        case Map:
            MapMessage mapMessage = session.createMapMessage();
            Map objMap = (Map) payload;
            for (final Map.Entry entry : (Set<Map.Entry>)objMap.entrySet()) {
                mapMessage.setObject(entry.getKey().toString(), entry.getValue());
            }
            answer = mapMessage;
            break;
        case Object:
            ObjectMessage objectMessage = session.createObjectMessage();
            objectMessage.setObject((Serializable) payload);
            answer = objectMessage;
            break;
        case Text:
            TextMessage textMessage = session.createTextMessage();
            String convertedText = typeConverter.convertTo(String.class, payload);
            textMessage.setText(convertedText);
            answer = textMessage;
            break;
        case Stream:
            StreamMessage streamMessage = session.createStreamMessage();
            Collection collection = (Collection)payload;
            for (final Object obj : collection) {
                streamMessage.writeObject(obj);
            }
            answer = streamMessage;
            break;
        case Message:
            if (allowNullBody && payload == null) {
                answer = session.createMessage();
            } else if (payload != null) {
                throw new JMSException("Unsupported message body type " + ObjectHelper.classCanonicalName(payload));
            } else {
                throw new JMSException("Null body is not allowed");
            }
            break;
        default:
            break;
        }

        if (messageHeaders != null && !messageHeaders.isEmpty()) {
            answer = JmsMessageHelper.setJmsMessageHeaders(answer, messageHeaders, keyFormatStrategy);
        }
        return answer;
    }

    /**
     * Adds or updates the {@link Message} headers. Header names and values are
     * checked for JMS 1.1 compliance.
     *
     * @param jmsMessage        the {@link Message} to add or update the headers on
     * @param messageHeaders    a {@link Map} of String/Object pairs
     * @param keyFormatStrategy the a {@link KeyFormatStrategy} to used to
     *                          format keys in a JMS 1.1 compliant manner.
     * @return {@link Message}
     */
    private static Message setJmsMessageHeaders(final Message jmsMessage, Map<String, Object> messageHeaders, KeyFormatStrategy keyFormatStrategy) throws IllegalHeaderException {

        Map<String, Object> headers = new HashMap<String, Object>(messageHeaders);
        for (final Map.Entry<String, Object> entry : headers.entrySet()) {
            String headerName = entry.getKey();
            Object headerValue = entry.getValue();

            if (headerName.equalsIgnoreCase(JMS_CORRELATION_ID)) {
                if (headerValue == null) {
                    // Value can be null but we can't cast a null to a String
                    // so pass null to the setter
                    setCorrelationId(jmsMessage, null);
                } else if (headerValue instanceof String) {
                    setCorrelationId(jmsMessage, (String) headerValue);
                } else {
                    throw new IllegalHeaderException("The " + JMS_CORRELATION_ID + " must either be a String or null.  Found: " + headerValue.getClass().getName());
                }
            } else if (headerName.equalsIgnoreCase(JMS_REPLY_TO)) {
                if (headerValue instanceof String) {
                    // FIXME Setting the reply to appears broken. walk back
                    // through it. If the value is a String we must normalize it
                    // first
                } else {
                    // TODO write destination converter
                    // Destination replyTo =
                    // ExchangeHelper.convertToType(exchange,
                    // Destination.class,
                    // headerValue);
                    // jmsMessage.setJMSReplyTo(replyTo);
                }
            } else if (headerName.equalsIgnoreCase(JMS_TYPE)) {
                if (headerValue == null) {
                    // Value can be null but we can't cast a null to a String
                    // so pass null to the setter
                    setMessageType(jmsMessage, null);
                } else if (headerValue instanceof String) {
                    // Not null but is a String
                    setMessageType(jmsMessage, (String) headerValue);
                } else {
                    throw new IllegalHeaderException("The " + JMS_TYPE + " must either be a String or null.  Found: " + headerValue.getClass().getName());
                }
            } else if (headerName.equalsIgnoreCase(JMS_PRIORITY)) {
                if (headerValue instanceof Integer) {
                    try {
                        jmsMessage.setJMSPriority((Integer) headerValue);
                    } catch (JMSException e) {
                        throw new IllegalHeaderException("Failed to set the " + JMS_PRIORITY + " header. Cause: " + e.getLocalizedMessage(), e);
                    }
                } else {
                    throw new IllegalHeaderException("The " + JMS_PRIORITY + " must be a Integer.  Type found: " + headerValue.getClass().getName());
                }
            } else if (headerName.equalsIgnoreCase(JMS_DELIVERY_MODE)) {
                try {
                    JmsMessageHelper.setJMSDeliveryMode(jmsMessage, headerValue);
                } catch (JMSException e) {
                    throw new IllegalHeaderException("Failed to set the " + JMS_DELIVERY_MODE + " header. Cause: " + e.getLocalizedMessage(), e);
                }
            } else if (headerName.equalsIgnoreCase(JMS_EXPIRATION)) {
                if (headerValue instanceof Long) {
                    try {
                        jmsMessage.setJMSExpiration((Long) headerValue);
                    } catch (JMSException e) {
                        throw new IllegalHeaderException("Failed to set the " + JMS_EXPIRATION + " header. Cause: " + e.getLocalizedMessage(), e);
                    }
                } else {
                    throw new IllegalHeaderException("The " + JMS_EXPIRATION + " must be a Long.  Type found: " + headerValue.getClass().getName());
                }
            } else {
                LOGGER.trace("Ignoring JMS header: {} with value: {}", headerName, headerValue);
                if (headerName.equalsIgnoreCase(JMS_DESTINATION) || headerName.equalsIgnoreCase(JMS_MESSAGE_ID) || headerName.equalsIgnoreCase(JMS_TIMESTAMP)
                        || headerName.equalsIgnoreCase(JMS_REDELIVERED)) {
                    // The following properties are set by the
                    // MessageProducer:
                    // JMSDestination
                    // The following are set on the underlying JMS provider:
                    // JMSMessageID, JMSTimestamp, JMSRedelivered
                    // log at trace level to not spam log
                    LOGGER.trace("Ignoring JMS header: {} with value: {}", headerName, headerValue);
                } else {
                    if (!(headerValue instanceof JmsMessageType)) {
                        String encodedName = keyFormatStrategy.encodeKey(headerName);
                        try {
                            JmsMessageHelper.setProperty(jmsMessage, encodedName, headerValue);
                        } catch (JMSException e) {
                            throw new IllegalHeaderException("Failed to set the header " + encodedName + " header. Cause: " + e.getLocalizedMessage(), e);
                        }
                    }
                }
            }
        }
        return jmsMessage;
    }

    @SuppressWarnings("unchecked")
    public static Exchange setJmsMessageHeaders(final Message jmsMessage, final Exchange exchange, boolean out, KeyFormatStrategy keyFormatStrategy) throws JMSException {
        Map<String, Object> headers = new HashMap<String, Object>();
        if (jmsMessage != null) {
            // lets populate the standard JMS message headers
            try {
                headers.put(JMS_CORRELATION_ID, jmsMessage.getJMSCorrelationID());
                headers.put(JMS_DELIVERY_MODE, jmsMessage.getJMSDeliveryMode());
                headers.put(JMS_DESTINATION, jmsMessage.getJMSDestination());
                headers.put(JMS_EXPIRATION, jmsMessage.getJMSExpiration());
                headers.put(JMS_MESSAGE_ID, jmsMessage.getJMSMessageID());
                headers.put(JMS_PRIORITY, jmsMessage.getJMSPriority());
                headers.put(JMS_REDELIVERED, jmsMessage.getJMSRedelivered());
                headers.put(JMS_TIMESTAMP, jmsMessage.getJMSTimestamp());
                headers.put(JMS_REPLY_TO, getJMSReplyTo(jmsMessage));
                headers.put(JMS_TYPE, getJMSType(jmsMessage));

                // this works around a bug in the ActiveMQ property handling
                headers.put(JMSX_GROUP_ID, jmsMessage.getStringProperty(JMSX_GROUP_ID));
            } catch (JMSException e) {
                throw new RuntimeCamelException(e);
            }

            for (final Enumeration<String> enumeration = jmsMessage.getPropertyNames(); enumeration.hasMoreElements();) {
                String key = enumeration.nextElement();
                if (hasIllegalHeaderKey(key)) {
                    throw new IllegalHeaderException("Header " + key + " is not a legal JMS header name value");
                }
                Object value = jmsMessage.getObjectProperty(key);
                String decodedName = keyFormatStrategy.decodeKey(key);
                headers.put(decodedName, value);
            }
        }
        if (out) {
            exchange.getOut().setHeaders(headers);
        } else {
            exchange.getIn().setHeaders(headers);
        }
        return exchange;
    }

    /**
     * Gets the JMSType from the message.
     *
     * @param message the message
     * @return the type, can be <tt>null</tt>
     */
    public static String getJMSType(Message message) {
        try {
            return message.getJMSType();
        } catch (Exception e) {
            // ignore due OracleAQ does not support accessing JMSType
        }

        return null;
    }

    /**
     * Sets the JMSDeliveryMode on the message.
     *
     * @param message      the message
     * @param deliveryMode the delivery mode, either as a String or integer
     * @throws javax.jms.JMSException is thrown if error setting the delivery mode
     */
    public static void setJMSDeliveryMode(Message message, Object deliveryMode) throws JMSException {
        Integer mode;

        if (deliveryMode instanceof String) {
            String s = (String) deliveryMode;
            if (JMS_DELIVERY_MODE_PERSISTENT.equalsIgnoreCase(s)) {
                mode = DeliveryMode.PERSISTENT;
            } else if (JMS_DELIVERY_MODE_NON_PERSISTENT.equalsIgnoreCase(s)) {
                mode = DeliveryMode.NON_PERSISTENT;
            } else {
                // it may be a number in the String so try that
                Integer value = null;
                try {
                    value = Integer.valueOf(s);
                } catch (NumberFormatException e) {
                    // Do nothing. The error handler below is sufficient
                }
                if (value != null) {
                    mode = value;
                } else {
                    throw new IllegalArgumentException("Unknown delivery mode with value: " + deliveryMode);
                }
            }
        } else if (deliveryMode instanceof Integer) {
            // fallback and try to convert to a number
            mode = (Integer) deliveryMode;
        } else {
            throw new IllegalArgumentException("Unable to convert the given delivery mode of type " + deliveryMode.getClass().getName() + " with value: " + deliveryMode);
        }

        message.setJMSDeliveryMode(mode);
    }

    /**
     * Sets the correlation id on the JMS message.
     * <p/>
     * Will ignore exception thrown
     *
     * @param message the JMS message
     * @param type    the correlation id
     */
    public static void setMessageType(Message message, String type) {
        try {
            message.setJMSType(type);
        } catch (JMSException e) {
            LOGGER.debug("Error setting the message type: {}", type, e);
        }
    }

    /**
     * Sets the correlation id on the JMS message.
     * <p/>
     * Will ignore exception thrown
     *
     * @param message       the JMS message
     * @param correlationId the correlation id
     */
    public static void setCorrelationId(Message message, String correlationId) {
        try {
            message.setJMSCorrelationID(correlationId);
        } catch (JMSException e) {
            LOGGER.debug("Error setting the correlationId: {}", correlationId, e);
        }
    }

    /**
     * Sets the JMSReplyTo on the message.
     *
     * @param message the message
     * @param replyTo the reply to destination
     */
    public static void setJMSReplyTo(Message message, Destination replyTo) {
        try {
            message.setJMSReplyTo(replyTo);
        } catch (Exception e) {
            LOGGER.debug("Error setting the correlationId: {}", replyTo.toString());
        }
    }

    /**
     * Gets the JMSReplyTo from the message.
     *
     * @param message the message
     * @return the reply to, can be <tt>null</tt>
     */
    public static Destination getJMSReplyTo(Message message) {
        try {
            return message.getJMSReplyTo();
        } catch (Exception e) {
            // ignore due OracleAQ does not support accessing JMSReplyTo
        }
        return null;
    }

    /**
     * Sets the property on the given JMS message.
     *
     * @param jmsMessage the JMS message
     * @param name       name of the property to set
     * @param value      the value
     * @throws JMSException can be thrown
     */
    public static void setProperty(Message jmsMessage, String name, Object value) throws JMSException {
        if (value == null) {
            jmsMessage.setObjectProperty(name, null);
        } else if (value instanceof Byte) {
            jmsMessage.setByteProperty(name, (Byte) value);
        } else if (value instanceof Boolean) {
            jmsMessage.setBooleanProperty(name, (Boolean) value);
        } else if (value instanceof Double) {
            jmsMessage.setDoubleProperty(name, (Double) value);
        } else if (value instanceof Float) {
            jmsMessage.setFloatProperty(name, (Float) value);
        } else if (value instanceof Integer) {
            jmsMessage.setIntProperty(name, (Integer) value);
        } else if (value instanceof Long) {
            jmsMessage.setLongProperty(name, (Long) value);
        } else if (value instanceof Short) {
            jmsMessage.setShortProperty(name, (Short) value);
        } else if (value instanceof String) {
            jmsMessage.setStringProperty(name, (String) value);
        } else {
            // fallback to Object
            jmsMessage.setObjectProperty(name, value);
        }
    }

    public static JmsMessageType discoverMessageTypeFromPayload(final Object payload) {
        JmsMessageType answer;
        // Default is a JMS Message since a body is not required
        if (payload == null) {
            answer = JmsMessageType.Message;
        } else {
            // Something was found in the body so determine
            // what type of message we need to create
            if (byte[].class.isInstance(payload)) {
                answer = JmsMessageType.Bytes;
            } else if (Map.class.isInstance(payload)) {
                answer = JmsMessageType.Map;
            } else if (Collection.class.isInstance(payload)) {
                answer = JmsMessageType.Stream;
            } else if (InputStream.class.isInstance(payload)) {
                answer = JmsMessageType.Bytes;
            } else if (ByteBuffer.class.isInstance(payload)) {
                answer = JmsMessageType.Bytes;
            } else if (File.class.isInstance(payload)) {
                answer = JmsMessageType.Bytes;
            } else if (Reader.class.isInstance(payload)) {
                answer = JmsMessageType.Text;
            } else if (String.class.isInstance(payload)) {
                answer = JmsMessageType.Text;
            } else if (CharBuffer.class.isInstance(payload)) {
                answer = JmsMessageType.Text;
            } else if (char[].class.isInstance(payload)) {
                answer = JmsMessageType.Text;
            } else if (Character.class.isInstance(payload)) {
                answer = JmsMessageType.Text;
            } else if (Serializable.class.isInstance(payload)) {
                answer = JmsMessageType.Object;
            } else {
                answer = JmsMessageType.Message;
            }
        }
        return answer;
    }

    public static JmsMessageType discoverJmsMessageType(Message message) {
        JmsMessageType answer;
        if (message != null) {
            if (BytesMessage.class.isInstance(message)) {
                answer = JmsMessageType.Bytes;
            } else if (MapMessage.class.isInstance(message)) {
                answer = JmsMessageType.Map;
            } else if (TextMessage.class.isInstance(message)) {
                answer = JmsMessageType.Text;
            } else if (StreamMessage.class.isInstance(message)) {
                answer = JmsMessageType.Stream;
            } else if (ObjectMessage.class.isInstance(message)) {
                answer = JmsMessageType.Object;
            } else {
                answer = JmsMessageType.Message;
            }
        } else {
            answer = JmsMessageType.Message;
        }
        return answer;
    }

    private static boolean hasIllegalHeaderKey(String key) {
        return key == null || key.isEmpty() || key.contains(".") || key.contains("-");
    }

}