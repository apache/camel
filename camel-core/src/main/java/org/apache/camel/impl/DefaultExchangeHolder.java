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

import java.io.File;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.WrappedFile;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holder object for sending an exchange over a remote wire as a serialized object.
 * This is usually configured using the <tt>transferExchange=true</tt> option on the endpoint.
 * <br/>
 * <b>Note:</b> Message body of type {@link File} or {@link WrappedFile} is <b>not</b> supported and
 * a {@link RuntimeExchangeException} is thrown.
 * <br/>
 * As opposed to normal usage where only the body part of the exchange is transferred over the wire,
 * this holder object serializes the following fields over the wire:
 * <ul>
 * <li>exchangeId</li>
 * <li>in body</li>
 * <li>out body</li>
 * <li>fault body </li>
 * <li>exception</li>
 * </ul>
 * <br/>
 * The exchange properties are not propagated by default. However you can specify they should be included
 * by the {@link DefaultExchangeHolder#marshal(Exchange, boolean)} method.
 * <br/>
 * And the following headers is transferred if their values are of primitive types, String or Number based.
 * <ul>
 * <li>in headers</li>
 * <li>out headers</li>
 * <li>fault headers</li>
 * </ul>
 * The body is serialized and stored as serialized bytes. The header and exchange properties only include
 * primitive, String, and Number types (and Exception types for exchange properties). Any other type is skipped.
 * Any message body object that is not serializable will be skipped and Camel will log this at <tt>WARN</tt> level.
 * And any message header values that is not a primitive value will be skipped and Camel will log this at <tt>DEBUG</tt> level.
 *
 * @version 
 */
public class DefaultExchangeHolder implements Serializable {

    private static final long serialVersionUID = 2L;
    private static final Logger LOG = LoggerFactory.getLogger(DefaultExchangeHolder.class);

    private String exchangeId;
    private Object inBody;
    private Object outBody;
    private Boolean inFaultFlag = Boolean.FALSE;
    private Boolean outFaultFlag = Boolean.FALSE;
    private Map<String, Object> inHeaders;
    private Map<String, Object> outHeaders;
    private Map<String, Object> properties;
    private Exception exception;

    /**
     * Creates a payload object with the information from the given exchange.
     *
     * @param exchange the exchange, must <b>not</b> be <tt>null</tt>
     * @return the holder object with information copied form the exchange
     */
    public static DefaultExchangeHolder marshal(Exchange exchange) {
        return marshal(exchange, true, false);
    }

    /**
     * Creates a payload object with the information from the given exchange.
     *
     * @param exchange the exchange, must <b>not</b> be <tt>null</tt>
     * @param includeProperties whether or not to include exchange properties
     * @return the holder object with information copied form the exchange
     */
    public static DefaultExchangeHolder marshal(Exchange exchange, boolean includeProperties) {
        ObjectHelper.notNull(exchange, "exchange");

        // we do not support files
        Object body = exchange.getIn().getBody();
        if (body instanceof WrappedFile || body instanceof File) {
            throw new RuntimeExchangeException("Message body of type " + body.getClass().getCanonicalName() + " is not supported by this marshaller.", exchange);
        }

        DefaultExchangeHolder payload = new DefaultExchangeHolder();

        payload.exchangeId = exchange.getExchangeId();
        payload.inBody = checkSerializableBody("in body", exchange, exchange.getIn().getBody());
        payload.safeSetInHeaders(exchange, false);
        if (exchange.hasOut()) {
            payload.outBody = checkSerializableBody("out body", exchange, exchange.getOut().getBody());
            payload.outFaultFlag = exchange.getOut().isFault();
            payload.safeSetOutHeaders(exchange, false);
        } else {
            payload.inFaultFlag = exchange.getIn().isFault();
        }
        if (includeProperties) {
            payload.safeSetProperties(exchange, false);
        }
        payload.exception = exchange.getException();

        return payload;
    }
    
    /**
     * Creates a payload object with the information from the given exchange.
     *
     * @param exchange the exchange, must <b>not</b> be <tt>null</tt>
     * @param includeProperties whether or not to include exchange properties
     * @param allowSerializedHeaders whether or not to include serialized headers
     * @return the holder object with information copied form the exchange
     */
    public static DefaultExchangeHolder marshal(Exchange exchange, boolean includeProperties, boolean allowSerializedHeaders) {
        ObjectHelper.notNull(exchange, "exchange");

        // we do not support files
        Object body = exchange.getIn().getBody();
        if (body instanceof WrappedFile || body instanceof File) {
            throw new RuntimeExchangeException("Message body of type " + body.getClass().getCanonicalName() + " is not supported by this marshaller.", exchange);
        }

        DefaultExchangeHolder payload = new DefaultExchangeHolder();

        payload.exchangeId = exchange.getExchangeId();
        payload.inBody = checkSerializableBody("in body", exchange, exchange.getIn().getBody());
        payload.safeSetInHeaders(exchange, allowSerializedHeaders);
        if (exchange.hasOut()) {
            payload.outBody = checkSerializableBody("out body", exchange, exchange.getOut().getBody());
            payload.outFaultFlag = exchange.getOut().isFault();
            payload.safeSetOutHeaders(exchange, allowSerializedHeaders);
        } else {
            payload.inFaultFlag = exchange.getIn().isFault();
        }
        if (includeProperties) {
            payload.safeSetProperties(exchange, allowSerializedHeaders);
        }
        payload.exception = exchange.getException();

        return payload;
    }

    /**
     * Transfers the information from the payload to the exchange.
     *
     * @param exchange the exchange to set values from the payload, must <b>not</b> be <tt>null</tt>
     * @param payload  the payload with the values, must <b>not</b> be <tt>null</tt>
     */
    public static void unmarshal(Exchange exchange, DefaultExchangeHolder payload) {
        ObjectHelper.notNull(exchange, "exchange");
        ObjectHelper.notNull(payload, "payload");

        exchange.setExchangeId(payload.exchangeId);
        exchange.getIn().setBody(payload.inBody);
        if (payload.inHeaders != null) {
            exchange.getIn().setHeaders(payload.inHeaders);
        }
        if (payload.inFaultFlag != null) {
            exchange.getIn().setFault(payload.inFaultFlag);
        }
        if (payload.outBody != null) {
            exchange.getOut().setBody(payload.outBody);
            if (payload.outHeaders != null) {
                exchange.getOut().setHeaders(payload.outHeaders);
            }
            if (payload.outFaultFlag != null) {
                exchange.getOut().setFault(payload.outFaultFlag);
            }
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

    private Map<String, Object> safeSetInHeaders(Exchange exchange, boolean allowSerializedHeaders) {
        if (exchange.getIn().hasHeaders()) {
            Map<String, Object> map = checkValidHeaderObjects("in headers", exchange, exchange.getIn().getHeaders(), allowSerializedHeaders);
            if (map != null && !map.isEmpty()) {
                inHeaders = new LinkedHashMap<String, Object>(map);
            }
        }
        return null;
    }

    private Map<String, Object> safeSetOutHeaders(Exchange exchange, boolean allowSerializedHeaders) {
        if (exchange.hasOut() && exchange.getOut().hasHeaders()) {
            Map<String, Object> map = checkValidHeaderObjects("out headers", exchange, exchange.getOut().getHeaders(), allowSerializedHeaders);
            if (map != null && !map.isEmpty()) {
                outHeaders = new LinkedHashMap<String, Object>(map);
            }
        }
        return null;
    }

    private Map<String, Object> safeSetProperties(Exchange exchange, boolean allowSerializedHeaders) {
        if (exchange.hasProperties()) {
            Map<String, Object> map = checkValidExchangePropertyObjects("properties", exchange, exchange.getProperties(), allowSerializedHeaders);
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
            LOG.warn("Exchange {} containing object: {} of type: {} cannot be serialized, it will be excluded by the holder.", type, object, object.getClass().getCanonicalName());
            return null;
        }
    }

    private static Map<String, Object> checkValidHeaderObjects(String type, Exchange exchange, Map<String, Object> map, boolean allowSerializedHeaders) {
        if (map == null) {
            return null;
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {

            // silently skip any values which is null
            if (entry.getValue() == null) {
                continue;
            }

            Object value = getValidHeaderValue(entry.getKey(), entry.getValue(), allowSerializedHeaders);
            if (value != null) {
                Serializable converted = exchange.getContext().getTypeConverter().convertTo(Serializable.class, exchange, value);
                if (converted != null) {
                    result.put(entry.getKey(), converted);
                } else {
                    logCannotSerializeObject(type, entry.getKey(), entry.getValue());
                }
            } else {
                logInvalidHeaderValue(type, entry.getKey(), entry.getValue());
            }
        }

        return result;
    }

    private static Map<String, Object> checkValidExchangePropertyObjects(String type, Exchange exchange, Map<String, Object> map, boolean allowSerializedHeaders) {
        if (map == null) {
            return null;
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {

            // silently skip any values which is null
            if (entry.getValue() == null) {
                continue;
            }

            Object value = getValidExchangePropertyValue(entry.getKey(), entry.getValue(), allowSerializedHeaders);
            if (value != null) {
                Serializable converted = exchange.getContext().getTypeConverter().convertTo(Serializable.class, exchange, value);
                if (converted != null) {
                    result.put(entry.getKey(), converted);
                } else {
                    logCannotSerializeObject(type, entry.getKey(), entry.getValue());
                }
            } else {
                logInvalidExchangePropertyValue(type, entry.getKey(), entry.getValue());
            }
        }

        return result;
    }

    /**
     * We only want to store header values of primitive and String related types.
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
     * We make possible store serialized headers by the boolean field allowSerializedHeaders
     * 
     * @param headerName   the header name
     * @param headerValue  the header value
     * @param allowSerializedHeaders  the header value
     * @return  the value to use, <tt>null</tt> to ignore this header
     */
    protected static Object getValidHeaderValue(String headerName, Object headerValue, boolean allowSerializedHeaders) {
        if (headerValue instanceof String) {
            return headerValue;
        } else if (headerValue instanceof BigInteger) {
            return headerValue;
        } else if (headerValue instanceof BigDecimal) {
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
            return headerValue;
        } else if (allowSerializedHeaders) {
            if (headerValue instanceof Serializable) {
                return headerValue;
            }
        }
        return null;
    }

    /**
     * We only want to store exchange property values of primitive and String related types, and
     * as well any caught exception that Camel routing engine has caught.
     * <p/>
     * This default implementation will allow the same values as {@link #getValidHeaderValue(String, Object, boolean)}
     * and in addition any value of type {@link Throwable}.
     *
     * @param propertyName   the property name
     * @param propertyValue  the property value
     * @return  the value to use, <tt>null</tt> to ignore this header
     */
    protected static Object getValidExchangePropertyValue(String propertyName, Object propertyValue, boolean allowSerializedHeaders) {
        // for exchange properties we also allow exception to be transferred so people can store caught exception
        if (propertyValue instanceof Throwable) {
            return propertyValue;
        }
        return getValidHeaderValue(propertyName, propertyValue, allowSerializedHeaders);
    }

    private static void logCannotSerializeObject(String type, String key, Object value) {
        if (key.startsWith("Camel")) {
            // log Camel at DEBUG level
            if (LOG.isDebugEnabled()) {
                LOG.debug("Exchange {} containing key: {} with object: {} of type: {} cannot be serialized, it will be excluded by the holder.",
                          new Object[]{type, key, value, ObjectHelper.classCanonicalName(value)});
            }
        } else {
            // log regular at WARN level
            LOG.warn("Exchange {} containing key: {} with object: {} of type: {} cannot be serialized, it will be excluded by the holder.",
                     new Object[]{type, key, value, ObjectHelper.classCanonicalName(value)});
        }
    }

    private static void logInvalidHeaderValue(String type, String key, Object value) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Exchange {} containing key: {} with object: {} of type: {} is not valid header type, it will be excluded by the holder.",
                      new Object[]{type, key, value, ObjectHelper.classCanonicalName(value)});
        }
    }

    private static void logInvalidExchangePropertyValue(String type, String key, Object value) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Exchange {} containing key: {} with object: {} of type: {} is not valid exchange property type, it will be excluded by the holder.",
                      new Object[]{type, key, value, ObjectHelper.classCanonicalName(value)});
        }
    }

}
