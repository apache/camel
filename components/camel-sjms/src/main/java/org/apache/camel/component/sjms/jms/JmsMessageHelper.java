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

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.jms.DeliveryMode;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;

import org.apache.camel.Exchange;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.trait.message.MessageTrait;
import org.apache.camel.trait.message.RedeliveryTraitPayload;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.util.StringHelper.removeStartingCharacters;

/**
 * Utility class for {@link jakarta.jms.Message}.
 */
public final class JmsMessageHelper {

    private JmsMessageHelper() {
    }

    /**
     * Removes the property from the JMS message.
     *
     * @param  jmsMessage               the JMS message
     * @param  name                     name of the property to remove
     * @return                          the old value of the property or <tt>null</tt> if not exists
     * @throws jakarta.jms.JMSException can be thrown
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
        Map<String, Object> map = new LinkedHashMap<>();
        Enumeration<?> en = jmsMessage.getPropertyNames();
        while (en.hasMoreElements()) {
            String key = (String) en.nextElement();
            if (name.equals(key)) {
                answer = key;
            } else {
                map.put(key, getProperty(jmsMessage, key));
            }
        }

        // redo the properties to keep
        jmsMessage.clearProperties();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            jmsMessage.setObjectProperty(entry.getKey(), entry.getValue());
        }

        return answer;
    }

    /**
     * Tests whether a given property with the name exists
     *
     * @param  jmsMessage   the JMS message
     * @param  name         name of the property to test if exists
     * @return              <tt>true</tt> if the property exists, <tt>false</tt> if not.
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
     * Gets a JMS property
     *
     * @param  jmsMessage   the JMS message
     * @param  name         name of the property to get
     * @return              the property value, or <tt>null</tt> if does not exists
     * @throws JMSException can be thrown
     */
    public static Object getProperty(Message jmsMessage, String name) throws JMSException {
        Object value = jmsMessage.getObjectProperty(name);
        if (value == null) {
            value = jmsMessage.getStringProperty(name);
        }
        return value;
    }

    /**
     * Sets the property on the given JMS message.
     *
     * @param  jmsMessage   the JMS message
     * @param  name         name of the property to set
     * @param  value        the value
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
            // ignore
        }
    }

    /**
     * Whether the destination name has either queue or temp queue prefix.
     *
     * @param  destination the destination
     * @return             <tt>true</tt> if queue or temp-queue prefix, <tt>false</tt> otherwise
     */
    public static boolean isQueuePrefix(String destination) {
        if (ObjectHelper.isEmpty(destination)) {
            return false;
        }

        return destination.startsWith(JmsConstants.QUEUE_PREFIX) || destination.startsWith(JmsConstants.TEMP_QUEUE_PREFIX);
    }

    /**
     * Whether the destination name has either topic or temp topic prefix.
     *
     * @param  destination the destination
     * @return             <tt>true</tt> if topic or temp-topic prefix, <tt>false</tt> otherwise
     */
    public static boolean isTopicPrefix(String destination) {
        if (ObjectHelper.isEmpty(destination)) {
            return false;
        }

        return destination.startsWith(JmsConstants.TOPIC_PREFIX) || destination.startsWith(JmsConstants.TEMP_TOPIC_PREFIX);
    }

    /**
     * Normalizes the destination name.
     * <p/>
     * This ensures the destination name is correct, and we do not create queues as <tt>queue://queue:foo</tt>, which
     * was intended as <tt>queue://foo</tt>.
     *
     * @param  destination the destination
     * @return             the normalized destination
     */
    public static String normalizeDestinationName(String destination) {
        // do not include prefix which is the current behavior when using this method.
        return normalizeDestinationName(destination, false);
    }

    /**
     * Normalizes the destination name.
     * <p/>
     * This ensures the destination name is correct, and we do not create queues as <tt>queue://queue:foo</tt>, which
     * was intended as <tt>queue://foo</tt>.
     *
     * @param  destination   the destination
     * @param  includePrefix whether to include <tt>queue://</tt>, or <tt>topic://</tt> prefix in the normalized
     *                       destination name
     * @return               the normalized destination
     */
    public static String normalizeDestinationName(String destination, boolean includePrefix) {
        if (ObjectHelper.isEmpty(destination)) {
            return destination;
        }
        if (destination.startsWith(JmsConstants.QUEUE_PREFIX)) {
            String s = removeStartingCharacters(destination.substring(JmsConstants.QUEUE_PREFIX.length()), '/');
            if (includePrefix) {
                s = JmsConstants.QUEUE_PREFIX + "//" + s;
            }
            return s;
        } else if (destination.startsWith(JmsConstants.TEMP_QUEUE_PREFIX)) {
            String s = removeStartingCharacters(destination.substring(JmsConstants.TEMP_QUEUE_PREFIX.length()), '/');
            if (includePrefix) {
                s = JmsConstants.TEMP_QUEUE_PREFIX + "//" + s;
            }
            return s;
        } else if (destination.startsWith(JmsConstants.TOPIC_PREFIX)) {
            String s = removeStartingCharacters(destination.substring(JmsConstants.TOPIC_PREFIX.length()), '/');
            if (includePrefix) {
                s = JmsConstants.TOPIC_PREFIX + "//" + s;
            }
            return s;
        } else if (destination.startsWith(JmsConstants.TEMP_TOPIC_PREFIX)) {
            String s = removeStartingCharacters(destination.substring(JmsConstants.TEMP_TOPIC_PREFIX.length()), '/');
            if (includePrefix) {
                s = JmsConstants.TEMP_TOPIC_PREFIX + "//" + s;
            }
            return s;
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
            // ignore due OracleAQ does not support accessing JMSReplyTo
        }
    }

    /**
     * Gets the JMSReplyTo from the message.
     *
     * @param  message the message
     * @return         the reply to, can be <tt>null</tt>
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
     * @param  message the message
     * @return         the type, can be <tt>null</tt>
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
     * Gets the String Properties from the message.
     *
     * @param  message the message
     * @return         the type, can be <tt>null</tt>
     */
    public static String getStringProperty(Message message, String propertyName) {
        try {
            return message.getStringProperty(propertyName);
        } catch (Exception e) {
            // ignore due some broker client does not support accessing StringProperty
        }

        return null;
    }

    /**
     * Gets the JMSRedelivered from the message.
     *
     * @param  message the message
     * @return         <tt>true</tt> if redelivered, <tt>false</tt> if not, <tt>null</tt> if not able to determine
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
     * For a given message, evaluates what is the redelivery state for it and gives the appropriate {@link MessageTrait}
     * for that redelivery state
     *
     * @param  message the message to evalute
     * @return         The appropriate MessageTrait for the redelivery state (one of MessageTrait.UNDEFINED_REDELIVERY,
     *                 MessageTrait.IS_REDELIVERY or MessageTrait.NON_REDELIVERY).
     */
    public static RedeliveryTraitPayload evalRedeliveryMessageTrait(Message message) {
        final Boolean redelivered = JmsMessageHelper.getJMSRedelivered(message);

        if (redelivered == null) {
            return RedeliveryTraitPayload.UNDEFINED_REDELIVERY;
        }

        if (Boolean.TRUE.equals(redelivered)) {
            return RedeliveryTraitPayload.IS_REDELIVERY;
        }

        return RedeliveryTraitPayload.NON_REDELIVERY;
    }

    /**
     * Gets the JMSMessageID from the message.
     *
     * @param  message the message
     * @return         the JMSMessageID, or <tt>null</tt> if not able to get
     */
    public static String getJMSMessageID(Message message) {
        try {
            return message.getJMSMessageID();
        } catch (Exception e) {
            // ignore if JMS broker do not support this
        }

        return null;
    }

    /**
     * Sets the JMSDeliveryMode on the message.
     *
     * @param  exchange                 the exchange
     * @param  message                  the message
     * @param  deliveryMode             the delivery mode, either as a String or integer
     * @throws jakarta.jms.JMSException is thrown if error setting the delivery mode
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

    /**
     * Gets the JMSCorrelationID from the message.
     *
     * @param  message the message
     * @return         the JMSCorrelationID, or <tt>null</tt> if not able to get
     */
    public static String getJMSCorrelationID(Message message) {
        try {
            return message.getJMSCorrelationID();
        } catch (JMSException e) {
            // ignore
        }

        return null;
    }

    /**
     * Gets the JMSCorrelationIDAsBytes from the message.
     *
     * @param  message the message
     * @return         the JMSCorrelationIDAsBytes, or <tt>null</tt> if not able to get
     */
    public static String getJMSCorrelationIDAsBytes(Message message) {
        try {
            return new String(message.getJMSCorrelationIDAsBytes());
        } catch (Exception e) {
            // ignore if JMS broker do not support this
        }

        return null;
    }

    /**
     * Gets the JMSDestination from the message.
     *
     * @param  message the message
     * @return         the JMSDestination, or <tt>null</tt> if not able to get
     */
    public static Destination getJMSDestination(Message message) {
        try {
            return message.getJMSDestination();
        } catch (Exception e) {
            // ignore if JMS broker do not support this
        }

        return null;
    }

}
