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
package org.apache.camel.util;

import java.util.Map;
import java.util.HashMap;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.InvalidTypeException;
import org.apache.camel.Message;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.NoSuchPropertyException;
import org.apache.camel.CamelContext;

/**
 * Some helper methods for working with {@link Exchange} objects
 * 
 * @version $Revision$
 */
public class ExchangeHelper {

    /**
     * Utility classes should not have a public constructor.
     */
    private ExchangeHelper() {        
    }

    /**
     * Extracts the exchange property of the given name and type; if it is not present then the
     * default value will be used
     *
     * @param exchange the message exchange
     * @param propertyName the name of the property on the exchange
     * @param type the expected type of the property
     * @param defaultValue the default value to be used if the property name does not exist or could not be
     * converted to the given type
     * @return the property value as the given type or the defaultValue if it could not be found or converted
     */
    public static <T> T getExchangeProperty(Exchange exchange, String propertyName, Class<T> type, T defaultValue) {
        T answer = exchange.getProperty(propertyName, type);
        if (answer == null) {
            return defaultValue;
        }
        return answer;
    }


    /**
     * Attempts to resolve the endpoint for the given value
     * 
     * @param exchange the message exchange being processed
     * @param value the value which can be an {@link Endpoint} or an object
     *                which provides a String representation of an endpoint via
     *                {@link #toString()}
     * 
     * @return the endpoint
     * @throws NoSuchEndpointException if the endpoint cannot be resolved
     */
    @SuppressWarnings({"unchecked" })
    public static <E extends Exchange> Endpoint<E> resolveEndpoint(E exchange, Object value)
        throws NoSuchEndpointException {
        Endpoint<E> endpoint;
        if (value instanceof Endpoint) {
            endpoint = (Endpoint<E>)value;
        } else {
            String uri = value.toString();
            endpoint = CamelContextHelper.getMandatoryEndpoint(exchange.getContext(), uri);
        }
        return endpoint;
    }

    public static <T> T getMandatoryProperty(Exchange exchange, String propertyName, Class<T> type)
        throws NoSuchPropertyException {
        T answer = exchange.getProperty(propertyName, type);
        if (answer == null) {
            throw new NoSuchPropertyException(exchange, propertyName, type);
        }
        return answer;
    }

    /**
     * Returns the mandatory inbound message body of the correct type or throws
     * an exception if it is not present
     */
    public static Object getMandatoryInBody(Exchange exchange) throws InvalidPayloadException {
        Object answer = exchange.getIn().getBody();
        if (answer == null) {
            throw new InvalidPayloadException(exchange, Object.class);
        }
        return answer;
    }

    /**
     * Returns the mandatory inbound message body of the correct type or throws
     * an exception if it is not present
     */
    public static <T> T getMandatoryInBody(Exchange exchange, Class<T> type) throws InvalidPayloadException {
        T answer = exchange.getIn().getBody(type);
        if (answer == null) {
            throw new InvalidPayloadException(exchange, type);
        }
        return answer;
    }

    /**
     * Returns the mandatory outbound message body of the correct type or throws
     * an exception if it is not present
     */
    public static Object getMandatoryOutBody(Exchange exchange) throws InvalidPayloadException {
        Object answer = exchange.getOut().getBody();
        if (answer == null) {
            throw new InvalidPayloadException(exchange, Object.class);
        }
        return answer;
    }

    /**
     * Returns the mandatory outbound message body of the correct type or throws
     * an exception if it is not present
     */
    public static <T> T getMandatoryOutBody(Exchange exchange, Class<T> type) throws InvalidPayloadException {
        T answer = exchange.getOut().getBody(type);
        if (answer == null) {
            throw new InvalidPayloadException(exchange, type);
        }
        return answer;
    }

    /**
     * Converts the value to the given expected type or throws an exception
     */
    public static <T> T convertToMandatoryType(Exchange exchange, Class<T> type, Object value)
        throws InvalidTypeException {
        T answer = convertToType(exchange, type, value);
        if (answer == null) {
            throw new InvalidTypeException(exchange, value, type);
        }
        return answer;
    }

    /**
     * Converts the value to the given expected type returning null if it could
     * not be converted
     */
    public static <T> T convertToType(Exchange exchange, Class<T> type, Object value) {
        return exchange.getContext().getTypeConverter().convertTo(type, value);
    }

    /**
     * Copies the results of a message exchange from the source exchange to the result exchange
     * which will copy the out and fault message contents and the exception
     *
     * @param result the result exchange which will have the output and error state added
     * @param source the source exchange which is not modified
     */
    public static void copyResults(Exchange result, Exchange source) {
        if (result != source) {
            result.setException(source.getException());
            Message fault = source.getFault(false);
            if (fault != null) {
                result.getFault(true).copyFrom(fault);
            }

            Message out = source.getOut(false);
            if (out != null) {
                result.getOut(true).copyFrom(out);
            }
        }
    }

    /**
     * Returns true if the given exchange pattern (if defined) can support IN messagea
     *
     * @param exchange the exchange to interrogate
     * @return true if the exchange is defined as an {@link ExchangePattern} which supports
     * IN messages
     */
    public static boolean isInCapable(Exchange exchange) {
        ExchangePattern pattern = exchange.getPattern();
        return pattern != null && pattern.isInCapable();
    }

    /**
     * Returns true if the given exchange pattern (if defined) can support OUT messagea
     *
     * @param exchange the exchange to interrogate
     * @return true if the exchange is defined as an {@link ExchangePattern} which supports
     * OUT messages
     */
    public static boolean isOutCapable(Exchange exchange) {
        ExchangePattern pattern = exchange.getPattern();
        return pattern != null && pattern.isOutCapable();
    }

    /**
     * Creates a new instance of the given type from the injector
     */
    public static <T> T newInstance(Exchange exchange, Class<T> type) {
        return exchange.getContext().getInjector().newInstance(type);
    }

    /**
     * Creates a Map of the variables which are made available to a script or template
     *
     * @param exchange the exchange to make available
     * @return a Map populated with the require dvariables
     */
    public static Map createVariableMap(Exchange exchange) {
        Map answer = new HashMap();
        populateVariableMap(exchange, answer);
        return answer;
    }

    /**
     * Populates the Map with the variables which are made available to a script or template
     *
     * @param exchange the exchange to make available
     * @param map      the map to populate
     * @return a Map populated with the require dvariables
     */
    public static void populateVariableMap(Exchange exchange, Map map) {
        map.put("exchange", exchange);
        Message in = exchange.getIn();
        map.put("in", in);
        map.put("request", in);
        map.put("headers", in.getHeaders());
        map.put("body", in.getBody())
                ;
        if (isOutCapable(exchange)) {
            Message out = exchange.getOut(true);
            map.put("out", out);
            map.put("response", out);
        }
        map.put("camelContext", exchange.getContext());
    }

    /**
     * Returns the MIME content type on the input message or null if one is not defined
     */
    public static String getContentType(Exchange exchange) {
       return exchange.getIn().getHeader("Content-Type", String.class);
    }
}
