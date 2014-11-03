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
package org.apache.camel.component.sjms;

import java.io.ByteArrayOutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
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
import org.apache.camel.component.sjms.jms.DefaultJmsKeyFormatStrategy;
import org.apache.camel.component.sjms.jms.IllegalHeaderException;
import org.apache.camel.component.sjms.jms.JmsConstants;
import org.apache.camel.component.sjms.jms.JmsMessageHeaderType;
import org.apache.camel.component.sjms.jms.JmsMessageHelper;
import org.apache.camel.component.sjms.jms.JmsMessageType;
import org.apache.camel.component.sjms.jms.KeyFormatStrategy;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.apache.camel.component.sjms.SjmsConstants.JMS_MESSAGE_TYPE;
import static org.apache.camel.component.sjms.SjmsConstants.QUEUE_PREFIX;
import static org.apache.camel.component.sjms.SjmsConstants.TOPIC_PREFIX;
import static org.apache.camel.util.ObjectHelper.removeStartingCharacters;

public final class SjmsExchangeMessageHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(SjmsExchangeMessageHelper.class);

    private SjmsExchangeMessageHelper() {
    }

    public static Exchange createExchange(Message message, Endpoint endpoint) {
        Exchange exchange = endpoint.createExchange();
        return populateExchange(message, exchange, false, ((SjmsEndpoint)endpoint).getJmsKeyFormatStrategy());
    }
    
    @Deprecated 
    /**
     * Please use the one which has the parameter of keyFormatStrategy 
     */
    public static Exchange populateExchange(Message message, Exchange exchange, boolean out) {
        return populateExchange(message, exchange, out, new DefaultJmsKeyFormatStrategy()); 
    }

    @SuppressWarnings("unchecked")
    public static Exchange populateExchange(Message message, Exchange exchange, boolean out, KeyFormatStrategy keyFormatStrategy) {
        try {
            SjmsExchangeMessageHelper.setJmsMessageHeaders(message, exchange, out, keyFormatStrategy);
            if (message != null) {
                // convert to JMS Message of the given type

                DefaultMessage bodyMessage = null;
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
                    bodyMessage.setHeader(JMS_MESSAGE_TYPE, JmsMessageType.Bytes);
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
                    bodyMessage.setHeader(JMS_MESSAGE_TYPE, JmsMessageType.Map);
                    bodyMessage.setBody(body);
                    break;
                case Object:
                    ObjectMessage objMsg = (ObjectMessage) message;
                    bodyMessage.setHeader(JMS_MESSAGE_TYPE, JmsMessageType.Object);
                    bodyMessage.setBody(objMsg.getObject());
                    break;
                case Text:
                    TextMessage textMsg = (TextMessage) message;
                    bodyMessage.setHeader(JMS_MESSAGE_TYPE, JmsMessageType.Text);
                    bodyMessage.setBody(textMsg.getText());
                    break;
                case Stream:
                    StreamMessage streamMessage = (StreamMessage) message;
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int next = streamMessage.readByte();
                    while (next > -1) {
                        baos.write(next);
                        next = streamMessage.readByte();
                    }
                    baos.flush();
                    bodyMessage.setHeader(JMS_MESSAGE_TYPE, JmsMessageType.Bytes);
                    bodyMessage.setBody(baos.toByteArray());
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

    /**
     * Removes the property from the JMS message.
     *
     * @param jmsMessage the JMS message
     * @param name       name of the property to remove
     * @return the old value of the property or <tt>null</tt> if not exists
     * @throws JMSException can be thrown
     */
    public static Object removeJmsProperty(Message jmsMessage, String name) throws JMSException {
        // check if the property exists
        if (!jmsMessage.propertyExists(name)) {
            return null;
        }

        Object answer = null;

        // store the properties we want to keep in a temporary map
        // as the JMS API is a bit strict as we are not allowed to
        // clear a single property, but must clear them all and redo
        // the properties
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        Enumeration<?> en = jmsMessage.getPropertyNames();
        while (en.hasMoreElements()) {
            String key = (String) en.nextElement();
            if (name.equals(key)) {
                answer = key;
            } else {
                map.put(key, jmsMessage.getObjectProperty(key));
            }
        }

        // redo the properties to keep
        jmsMessage.clearProperties();
        for (Entry<String, Object> entry : map.entrySet()) {
            jmsMessage.setObjectProperty(entry.getKey(), entry.getValue());
        }

        return answer;
    }

    /**
     * Tests whether a given property with the name exists
     *
     * @param jmsMessage the JMS message
     * @param name       name of the property to test if exists
     * @return <tt>true</tt> if the property exists, <tt>false</tt> if not.
     * @throws JMSException can be thrown
     */
    public static boolean hasProperty(Message jmsMessage, String name) throws JMSException {
        Enumeration<?> en = jmsMessage.getPropertyNames();
        while (en.hasMoreElements()) {
            String key = (String) en.nextElement();
            if (name.equals(key)) {
                return true;
            }
        }
        return false;
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
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Error setting the correlationId: {}", correlationId);
            }
        }
    }

    /**
     * Normalizes the destination name, by removing any leading queue or topic
     * prefixes.
     *
     * @param destination the destination
     * @return the normalized destination
     */
    public static String normalizeDestinationName(String destination) {
        if (ObjectHelper.isEmpty(destination)) {
            return destination;
        }
        if (destination.startsWith(QUEUE_PREFIX)) {
            return removeStartingCharacters(destination.substring(QUEUE_PREFIX.length()), '/');
        } else if (destination.startsWith(TOPIC_PREFIX)) {
            return removeStartingCharacters(destination.substring(TOPIC_PREFIX.length()), '/');
        } else {
            return destination;
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
     * Gets the JMSRedelivered from the message.
     *
     * @param message the message
     * @return <tt>true</tt> if redelivered, <tt>false</tt> if not,
     * <tt>null</tt> if not able to determine
     */
    public static Boolean getJMSRedelivered(Message message) {
        try {
            return message.getJMSRedelivered();
        } catch (Exception e) {
            // ignore if JMS broker do not support this
        }

        return null;
    }

    /**
     * Sets the JMSDeliveryMode on the message.
     *
     * @param exchange     the exchange
     * @param message      the message
     * @param deliveryMode the delivery mode, either as a String or integer
     * @throws javax.jms.JMSException is thrown if error setting the delivery
     *                                mode
     */
    public static void setJMSDeliveryMode(Exchange exchange, Message message, Object deliveryMode) throws JMSException {
        Integer mode = null;

        if (deliveryMode instanceof String) {
            String s = (String) deliveryMode;
            if ("PERSISTENT".equalsIgnoreCase(s)) {
                mode = DeliveryMode.PERSISTENT;
            } else if ("NON_PERSISTENT".equalsIgnoreCase(s)) {
                mode = DeliveryMode.NON_PERSISTENT;
            } else {
                // it may be a number in the String so try that
                Integer value = ExchangeHelper.convertToType(exchange, Integer.class, deliveryMode);
                if (value != null) {
                    mode = value;
                } else {
                    throw new IllegalArgumentException("Unknown delivery mode with value: " + deliveryMode);
                }
            }
        } else {
            // fallback and try to convert to a number
            Integer value = ExchangeHelper.convertToType(exchange, Integer.class, deliveryMode);
            if (value != null) {
                mode = value;
            }
        }

        if (mode != null) {
            message.setJMSDeliveryMode(mode);
            message.setIntProperty(JmsConstants.JMS_DELIVERY_MODE, mode);
        }
    }

    public static Message setJmsMessageHeaders(final Exchange exchange, final Message jmsMessage) throws Exception {
        Map<String, Object> headers = new HashMap<String, Object>(exchange.getIn().getHeaders());
        Set<String> keys = headers.keySet();
        for (String headerName : keys) {
            Object headerValue = headers.get(headerName);
            if (headerName.equalsIgnoreCase("JMSCorrelationID")) {
                jmsMessage.setJMSCorrelationID(ExchangeHelper.convertToType(exchange, String.class, headerValue));
            } else if (headerName.equalsIgnoreCase("JMSReplyTo") && headerValue != null) {
                if (headerValue instanceof String) {
                    // if the value is a String we must normalize it first
                    headerValue = headerValue;
                } else {
                    // TODO write destination converter
                    // Destination replyTo =
                    // ExchangeHelper.convertToType(exchange, Destination.class,
                    // headerValue);
                    // jmsMessage.setJMSReplyTo(replyTo);
                }
            } else if (headerName.equalsIgnoreCase("JMSType")) {
                jmsMessage.setJMSType(ExchangeHelper.convertToType(exchange, String.class, headerValue));
            } else if (headerName.equalsIgnoreCase("JMSPriority")) {
                jmsMessage.setJMSPriority(ExchangeHelper.convertToType(exchange, Integer.class, headerValue));
            } else if (headerName.equalsIgnoreCase("JMSDeliveryMode")) {
                SjmsExchangeMessageHelper.setJMSDeliveryMode(exchange, jmsMessage, headerValue);
            } else if (headerName.equalsIgnoreCase("JMSExpiration")) {
                jmsMessage.setJMSExpiration(ExchangeHelper.convertToType(exchange, Long.class, headerValue));
            } else {
                // The following properties are set by the MessageProducer:
                // JMSDestination
                // The following are set on the underlying JMS provider:
                // JMSMessageID, JMSTimestamp, JMSRedelivered
                // log at trace level to not spam log
                LOGGER.trace("Ignoring JMS header: {} with value: {}", headerName, headerValue);
                if (headerName.equalsIgnoreCase("JMSDestination") || headerName.equalsIgnoreCase("JMSMessageID") || headerName.equalsIgnoreCase("JMSTimestamp")
                        || headerName.equalsIgnoreCase("JMSRedelivered")) {
                    // The following properties are set by the MessageProducer:
                    // JMSDestination
                    // The following are set on the underlying JMS provider:
                    // JMSMessageID, JMSTimestamp, JMSRedelivered
                    // log at trace level to not spam log
                    LOGGER.trace("Ignoring JMS header: {} with value: {}", headerName, headerValue);
                } else {
                    if (!(headerValue instanceof JmsMessageType)) {
                        String encodedName = new DefaultJmsKeyFormatStrategy().encodeKey(headerName);
                        SjmsExchangeMessageHelper.setProperty(jmsMessage, encodedName, headerValue);
                    }
                }
            }
        }
        return jmsMessage;
    }
    
    @Deprecated
    /**
     * Please use the one which has the parameter of keyFormatStrategy
     */
    public static Exchange setJmsMessageHeaders(final Message jmsMessage, final Exchange exchange, boolean out) throws JMSException {
        return setJmsMessageHeaders(jmsMessage, exchange, out, new DefaultJmsKeyFormatStrategy());
    }

    @SuppressWarnings("unchecked")
    public static Exchange setJmsMessageHeaders(final Message jmsMessage, final Exchange exchange, boolean out, KeyFormatStrategy keyFormatStrategy) throws JMSException {
        Map<String, Object> headers = new HashMap<String, Object>();
        if (jmsMessage != null) {
            // lets populate the standard JMS message headers
            try {
                headers.put(JmsMessageHeaderType.JMSCorrelationID.toString(), jmsMessage.getJMSCorrelationID());
                headers.put(JmsMessageHeaderType.JMSDeliveryMode.toString(), jmsMessage.getJMSDeliveryMode());
                headers.put(JmsMessageHeaderType.JMSDestination.toString(), jmsMessage.getJMSDestination());
                headers.put(JmsMessageHeaderType.JMSExpiration.toString(), jmsMessage.getJMSExpiration());
                headers.put(JmsMessageHeaderType.JMSMessageID.toString(), jmsMessage.getJMSMessageID());
                headers.put(JmsMessageHeaderType.JMSPriority.toString(), jmsMessage.getJMSPriority());
                headers.put(JmsMessageHeaderType.JMSRedelivered.toString(), jmsMessage.getJMSRedelivered());
                headers.put(JmsMessageHeaderType.JMSTimestamp.toString(), jmsMessage.getJMSTimestamp());
                headers.put(JmsMessageHeaderType.JMSReplyTo.toString(), SjmsExchangeMessageHelper.getJMSReplyTo(jmsMessage));
                headers.put(JmsMessageHeaderType.JMSType.toString(), SjmsExchangeMessageHelper.getJMSType(jmsMessage));

                // this works around a bug in the ActiveMQ property handling
                headers.put(JmsMessageHeaderType.JMSXGroupID.toString(), jmsMessage.getStringProperty(JmsMessageHeaderType.JMSXGroupID.toString()));
            } catch (JMSException e) {
                throw new RuntimeCamelException(e);
            }

            for (Enumeration<String> enumeration = jmsMessage.getPropertyNames(); enumeration.hasMoreElements();) {
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

    public static Message createMessage(Exchange exchange, Session session, KeyFormatStrategy keyFormatStrategy) throws Exception {
        Message answer = null;
        Object body = null;
        Map<String, Object> bodyHeaders = null;


        if (exchange.getOut().getBody() != null) {
            body = exchange.getOut().getBody();
            bodyHeaders = new HashMap<String, Object>(exchange.getOut().getHeaders());
        } else {
            body = exchange.getIn().getBody();
            bodyHeaders = new HashMap<String, Object>(exchange.getIn().getHeaders());
        }

        answer = JmsMessageHelper.createMessage(session, body, bodyHeaders, keyFormatStrategy);

        return answer;
    }

    private static boolean hasIllegalHeaderKey(String key) {
        if (key == null) {
            return true;
        }
        if (key.equals("")) {
            return true;
        }
        if (key.indexOf(".") > -1) {
            return true;
        }
        if (key.indexOf("-") > -1) {
            return true;
        }
        return false;
    }
}
