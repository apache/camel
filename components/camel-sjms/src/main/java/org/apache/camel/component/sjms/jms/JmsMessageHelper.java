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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
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

import org.apache.camel.TypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for {@link javax.jms.Message}.
 */
public final class JmsMessageHelper {

    /**
     * Set by the publishing Client
     */
    public static final String JMS_CORRELATION_ID = "JMSCorrelationID";
    /**
     * Set on the send or publish event
     */
    public static final String JMS_DELIVERY_MODE = "JMSDeliveryMode";
    /**
     * Set on the send or publish event
     */
    public static final String JMS_DESTINATION = "JMSDestination";
    /**
     * Set on the send or publish event
     */
    public static final String JMS_EXPIRATION = "JMSExpiration";
    /**
     * Set on the send or publish event
     */
    public static final String JMS_MESSAGE_ID = "JMSMessageID";
    /**
     * Set on the send or publish event
     */
    public static final String JMS_PRIORITY = "JMSPriority";
    /**
     * A redelivery flag set by the JMS provider
     */
    public static final String JMS_REDELIVERED = "JMSRedelivered";
    /**
     * The JMS Reply To {@link Destination} set by the publishing Client
     */
    public static final String JMS_REPLY_TO = "JMSReplyTo";
    /**
     * Set on the send or publish event
     */
    public static final String JMS_TIMESTAMP = "JMSTimestamp";
    /**
     * Set by the publishing Client
     */
    public static final String JMS_TYPE = "JMSType";

    private static final Logger LOGGER = LoggerFactory.getLogger(JmsMessageHelper.class);

    private JmsMessageHelper() {
    }

    //@SuppressWarnings("unchecked")
    public static Message createMessage(Session session, Object payload, Map<String, Object> messageHeaders, KeyFormatStrategy keyFormatStrategy, TypeConverter typeConverter) throws Exception {
        Message answer = null;
        JmsMessageType messageType = JmsMessageHelper.discoverMessageTypeFromPayload(payload);
        try {

            switch (messageType) {
            case Bytes:
                BytesMessage bytesMessage = session.createBytesMessage();
                byte[] bytesToWrite = typeConverter.convertTo(byte[].class, payload);
                bytesMessage.writeBytes(bytesToWrite);
                answer = bytesMessage;
                break;
            case Map:
                MapMessage mapMessage = session.createMapMessage();
                Map<String, Object> objMap = (Map<String, Object>) payload;
                Set<String> keys = objMap.keySet();
                for (String key : keys) {
                    Object value = objMap.get(key);
                    mapMessage.setObject(key, value);
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
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                InputStream is = typeConverter.convertTo(InputStream.class, payload);
                int reads = is.read();
                while (reads != -1) {
                    baos.write(reads);
                    reads = is.read();
                }
                BytesMessage bytesStreamMessage = session.createBytesMessage();
                bytesStreamMessage.writeBytes(baos.toByteArray());
                baos.close();
                is.close();
                answer = bytesStreamMessage;
                break;
            default:
                break;
            }
        } catch (Exception e) {
            LOGGER.error("Error creating a message of type: {}", messageType, e);
            throw e;
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
     *                          format keys in a JMS 1.1 compliant manner. If null the
     *                          {@link DefaultJmsKeyFormatStrategy} will be used.
     * @return {@link Message}
     */
    public static Message setJmsMessageHeaders(final Message jmsMessage, Map<String, Object> messageHeaders, KeyFormatStrategy keyFormatStrategy) throws IllegalHeaderException {
        // Support for the null keyFormatStrategy
        KeyFormatStrategy localKeyFormatStrategy = null;
        if (keyFormatStrategy == null) {
            localKeyFormatStrategy = new DefaultJmsKeyFormatStrategy();
        } else {
            localKeyFormatStrategy = keyFormatStrategy;
        }

        Map<String, Object> headers = new HashMap<String, Object>(messageHeaders);
        Set<String> keys = headers.keySet();
        for (String headerName : keys) {
            Object headerValue = headers.get(headerName);

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
                if (headerName.equalsIgnoreCase(JMS_DESTINATION) || headerName.equalsIgnoreCase(JMS_MESSAGE_ID) || headerName.equalsIgnoreCase("JMSTimestamp")
                        || headerName.equalsIgnoreCase("JMSRedelivered")) {
                    // The following properties are set by the
                    // MessageProducer:
                    // JMSDestination
                    // The following are set on the underlying JMS provider:
                    // JMSMessageID, JMSTimestamp, JMSRedelivered
                    // log at trace level to not spam log
                    LOGGER.trace("Ignoring JMS header: {} with value: {}", headerName, headerValue);
                } else {
                    if (!(headerValue instanceof JmsMessageType)) {
                        String encodedName = localKeyFormatStrategy.encodeKey(headerName);
                        try {
                            JmsMessageHelper.setProperty(jmsMessage, encodedName, headerValue);
                        } catch (JMSException e) {
                            throw new IllegalHeaderException("Failed to set the header " + encodedName + " header. Cause: " + e.getLocalizedMessage(), e);
                        }
                    }
                }
                // }
            }
        }
        return jmsMessage;
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
            if ("PERSISTENT".equalsIgnoreCase(s)) {
                mode = DeliveryMode.PERSISTENT;
            } else if ("NON_PERSISTENT".equalsIgnoreCase(s)) {
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
     * Sets the property on the given JMS message.
     *
     * @param jmsMessage the JMS message
     * @param name       name of the property to set
     * @param value      the value
     * @throws JMSException can be thrown
     */
    public static void setProperty(Message jmsMessage, String name, Object value) throws JMSException {
        if (value == null) {
            return;
        }
        if (value instanceof Byte) {
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
        JmsMessageType answer = null;
        // Default is a JMS Message since a body is not required
        if (payload == null) {
            answer = JmsMessageType.Message;
        } else {
            // Something was found in the body so determine
            // what type of message we need to create
            if (Byte[].class.isInstance(payload)) {
                answer = JmsMessageType.Bytes;
            } else if (Collection.class.isInstance(payload)) {
                answer = JmsMessageType.Map;
            } else if (InputStream.class.isInstance(payload)) {
                answer = JmsMessageType.Bytes;
            } else if (ByteBuffer.class.isInstance(payload)) {
                answer = JmsMessageType.Bytes;
            } else if (File.class.isInstance(payload)) {
                answer = JmsMessageType.Bytes;
            } else if (Reader.class.isInstance(payload)) {
                answer = JmsMessageType.Bytes;
            } else if (String.class.isInstance(payload)) {
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
        JmsMessageType answer = null;
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
}
