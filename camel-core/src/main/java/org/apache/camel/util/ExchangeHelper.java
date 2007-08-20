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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.InvalidTypeException;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.NoSuchPropertyException;

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
    public static <T> T getMandatoryInBody(Exchange exchange, Class<T> type) throws InvalidPayloadException {
        T answer = exchange.getIn().getBody(type);
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
}
