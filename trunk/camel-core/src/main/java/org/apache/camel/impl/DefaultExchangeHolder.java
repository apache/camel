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
package org.apache.camel.impl;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holder object for sending an exchange over a remote wire as a serialized object.
 * This is usually configured using the <tt>transferExchange=true</tt> option on the endpoint.
 * <p/>
 * As opposed to normal usage where only the body part of the exchange is transferred over the wire,
 * this holder object serializes the following fields over the wire:
 * <ul>
 * <li>exchangeId</li>
 * <li>in body</li>
 * <li>out body</li>
 * <li>in headers</li>
 * <li>out headers</li>
 * <li>fault body </li>
 * <li>fault headers</li>
 * <li>exchange properties</li>
 * <li>exception</li>
 * </ul>
 * Any object that is not serializable will be skipped and Camel will log this at WARN level.
 *
 * @version 
 */
public class DefaultExchangeHolder implements Serializable {

    private static final long serialVersionUID = 2L;
    private static final transient Logger LOG = LoggerFactory.getLogger(DefaultExchangeHolder.class);

    private String exchangeId;
    private Object inBody;
    private Object outBody;
    private Boolean outFaultFlag = Boolean.FALSE;
    private Map<String, Object> inHeaders;
    private Map<String, Object> outHeaders;
    private Map<String, Object> properties;
    private Exception exception;

    /**
     * Creates a payload object with the information from the given exchange.
     *
     * @param exchange the exchange
     * @return the holder object with information copied form the exchange
     */
    public static DefaultExchangeHolder marshal(Exchange exchange) {
        return marshal(exchange, true);
    }

    /**
     * Creates a payload object with the information from the given exchange.
     *
     * @param exchange the exchange
     * @param includeProperties whether or not to include exchange properties
     * @return the holder object with information copied form the exchange
     */
    public static DefaultExchangeHolder marshal(Exchange exchange, boolean includeProperties) {
        DefaultExchangeHolder payload = new DefaultExchangeHolder();

        payload.exchangeId = exchange.getExchangeId();
        payload.inBody = checkSerializableBody("in body", exchange, exchange.getIn().getBody());
        payload.safeSetInHeaders(exchange);
        if (exchange.hasOut()) {
            payload.outBody = checkSerializableBody("out body", exchange, exchange.getOut().getBody());
            payload.outFaultFlag = exchange.getOut().isFault();
            payload.safeSetOutHeaders(exchange);
        }
        if (includeProperties) {
            payload.safeSetProperties(exchange);
        }
        payload.exception = exchange.getException();

        return payload;
    }

    /**
     * Transfers the information from the payload to the exchange.
     *
     * @param exchange the exchange to set values from the payload
     * @param payload  the payload with the values
     */
    public static void unmarshal(Exchange exchange, DefaultExchangeHolder payload) {
        exchange.setExchangeId(payload.exchangeId);
        exchange.getIn().setBody(payload.inBody);
        if (payload.inHeaders != null) {
            exchange.getIn().setHeaders(payload.inHeaders);
        }
        if (payload.outBody != null) {
            exchange.getOut().setBody(payload.outBody);
            if (payload.outHeaders != null) {
                exchange.getOut().setHeaders(payload.outHeaders);
            }
            exchange.getOut().setFault(payload.outFaultFlag.booleanValue());
        }
        if (payload.properties != null) {
            for (String key : payload.properties.keySet()) {
                exchange.setProperty(key, payload.properties.get(key));
            }
        }
        exchange.setException(payload.exception);
    }

    /**
     * Adds a property to the payload.
     * <p/>
     * This can be done in special situations where additional information must be added which was not provided
     * from the source.
     *
     * @param payload the serialized payload
     * @param key the property key to add
     * @param property the property value to add
     */
    public static void addProperty(DefaultExchangeHolder payload, String key, Serializable property) {
        if (key == null || property == null) {
            return;
        }
        if (payload.properties == null) {
            payload.properties = new LinkedHashMap<String, Object>();
        }
        payload.properties.put(key, property);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("DefaultExchangeHolder[exchangeId=").append(exchangeId);
        sb.append("inBody=").append(inBody).append(", outBody=").append(outBody);
        sb.append(", inHeaders=").append(inHeaders).append(", outHeaders=").append(outHeaders);
        sb.append(", properties=").append(properties).append(", exception=").append(exception);
        return sb.append(']').toString();
    }

    private Map<String, Object> safeSetInHeaders(Exchange exchange) {
        if (exchange.getIn().hasHeaders()) {
            Map<String, Object> map = checkMapSerializableObjects("in headers", exchange, exchange.getIn().getHeaders());
            if (map != null && !map.isEmpty()) {
                inHeaders = new LinkedHashMap<String, Object>(map);
            }
        }
        return null;
    }

    private Map<String, Object> safeSetOutHeaders(Exchange exchange) {
        if (exchange.hasOut() && exchange.getOut().hasHeaders()) {
            Map<String, Object> map = checkMapSerializableObjects("out headers", exchange, exchange.getOut().getHeaders());
            if (map != null && !map.isEmpty()) {
                outHeaders = new LinkedHashMap<String, Object>(map);
            }
        }
        return null;
    }

    private Map<String, Object> safeSetProperties(Exchange exchange) {
        if (exchange.hasProperties()) {
            Map<String, Object> map = checkMapSerializableObjects("properties", exchange, exchange.getProperties());
            if (map != null && !map.isEmpty()) {
                properties = new LinkedHashMap<String, Object>(map);
            }
        }
        return null;
    }

    private static Object checkSerializableBody(String type, Exchange exchange, Object object) {
        if (object == null) {
            return null;
        }

        Serializable converted = exchange.getContext().getTypeConverter().convertTo(Serializable.class, exchange, object);
        if (converted != null) {
            return converted;
        } else {
            LOG.warn("Exchange " + type + " containing object: " + object + " of type: " + object.getClass().getCanonicalName() + " cannot be serialized, it will be excluded by the holder.");
            return null;
        }
    }

    private static Map<String, Object> checkMapSerializableObjects(String type, Exchange exchange, Map<String, Object> map) {
        if (map == null) {
            return null;
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {

            // silently skip any values which is null
            if (entry.getValue() != null) {
                Serializable converted = exchange.getContext().getTypeConverter().convertTo(Serializable.class, exchange, entry.getValue());

                // if the converter is a map/collection we need to check its content as well
                if (converted instanceof Collection) {
                    Collection valueCol = (Collection) converted;
                    if (!collectionContainsAllSerializableObjects(valueCol, exchange)) {
                        logCannotSerializeObject(type, entry.getKey(), entry.getValue());
                        continue;
                    }
                } else if (converted instanceof Map) {
                    Map valueMap = (Map) converted;
                    if (!mapContainsAllSerializableObjects(valueMap, exchange)) {
                        logCannotSerializeObject(type, entry.getKey(), entry.getValue());
                        continue;
                    }
                }

                if (converted != null) {
                    result.put(entry.getKey(), converted);
                } else {
                    logCannotSerializeObject(type, entry.getKey(), entry.getValue());
                }
            }
        }

        return result;
    }

    private static void logCannotSerializeObject(String type, String key, Object value) {
        if (key.startsWith("Camel")) {
            // log Camel at DEBUG level
            if (LOG.isDebugEnabled()) {
                LOG.debug("Exchange {} containing key: {} with object: {} of type: {} cannot be serialized, it will be excluded by the holder."
                          , new Object[]{type, key, value, ObjectHelper.classCanonicalName(value)});
            }
        } else {
            // log regular at WARN level
            LOG.warn("Exchange {} containing key: {} with object: {} of type: {} cannot be serialized, it will be excluded by the holder."
                     , new Object[]{type, key, value, ObjectHelper.classCanonicalName(value)});
        }
    }

    private static boolean collectionContainsAllSerializableObjects(Collection col, Exchange exchange) {
        for (Object value : col) {
            if (value != null) {
                Serializable converted = exchange.getContext().getTypeConverter().convertTo(Serializable.class, exchange, value);
                if (converted == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean mapContainsAllSerializableObjects(Map map, Exchange exchange) {
        for (Object value : map.values()) {
            if (value != null) {
                Serializable converted = exchange.getContext().getTypeConverter().convertTo(Serializable.class, exchange, value);
                if (converted == null) {
                    return false;
                }
            }
        }
        return true;
    }

}
