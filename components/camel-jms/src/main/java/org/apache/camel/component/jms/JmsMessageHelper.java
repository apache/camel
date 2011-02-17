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

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.jms.JMSException;
import javax.jms.Message;

import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.jms.JmsConfiguration.QUEUE_PREFIX;
import static org.apache.camel.component.jms.JmsConfiguration.TOPIC_PREFIX;
import static org.apache.camel.util.ObjectHelper.removeStartingCharacters;

/**
 * Utility class for {@link javax.jms.Message}.
 *
 * @version 
 */
public final class JmsMessageHelper {

    private JmsMessageHelper() {
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
        Enumeration en = jmsMessage.getPropertyNames();
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
        Enumeration en = jmsMessage.getPropertyNames();
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
     * @param jmsMessage  the JMS message
     * @param name        name of the property to set
     * @param value       the value
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
     * @param message  the JMS message
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
     * Normalizes the destination name, by removing any leading queue or topic prefixes.
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
}
