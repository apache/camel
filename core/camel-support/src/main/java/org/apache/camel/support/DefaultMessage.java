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
package org.apache.camel.support;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.spi.HeadersMapFactory;

/**
 * The default implementation of {@link org.apache.camel.Message}
 * <p/>
 * This implementation uses a {@link org.apache.camel.util.CaseInsensitiveMap} storing the headers. This allows us to be
 * able to lookup headers using case insensitive keys, making it easier for end users as they do not have to be worried
 * about using exact keys. See more details at {@link org.apache.camel.util.CaseInsensitiveMap}. The implementation of
 * the map can be configured by the {@link HeadersMapFactory} which can be set on the {@link CamelContext}. The default
 * implementation uses the {@link org.apache.camel.util.CaseInsensitiveMap CaseInsensitiveMap}.
 */
public class DefaultMessage extends MessageSupport {
    private Map<String, Object> headers;

    public DefaultMessage(Exchange exchange) {
        setExchange(exchange);
        if (exchange != null) {
            setCamelContext(exchange.getContext());
        }
    }

    public DefaultMessage(CamelContext camelContext) {
        this.camelContext = (ExtendedCamelContext) camelContext;
        this.typeConverter = camelContext.getTypeConverter();
    }

    @Override
    public Object getHeader(String name) {
        if (headers == null) {
            // force creating headers
            headers = createHeaders();
        }

        if (!headers.isEmpty()) {
            return headers.get(name);
        } else {
            return null;
        }
    }

    @Override
    public Object getHeader(String name, Object defaultValue) {
        Object answer = null;

        if (headers == null) {
            // force creating headers
            headers = createHeaders();
        }

        if (!headers.isEmpty()) {
            answer = headers.get(name);
        }
        return answer != null ? answer : defaultValue;
    }

    @Override
    public Object getHeader(String name, Supplier<Object> defaultValueSupplier) {
        Object answer = null;

        if (headers == null) {
            // force creating headers
            headers = createHeaders();
        }

        if (!headers.isEmpty()) {
            answer = headers.get(name);
        }
        return answer != null ? answer : defaultValueSupplier.get();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getHeader(String name, Class<T> type) {
        Object value = null;

        if (headers == null) {
            // force creating headers
            headers = createHeaders();
        }

        if (!headers.isEmpty()) {
            value = headers.get(name);
        }
        if (value == null) {
            // lets avoid NullPointerException when converting to boolean for null values
            if (boolean.class == type) {
                return (T) Boolean.FALSE;
            }
            return null;
        }

        // eager same instance type test to avoid the overhead of invoking the type converter
        // if already same type
        if (type.isInstance(value)) {
            return (T) value;
        }

        Exchange e = getExchange();
        if (e != null) {
            return typeConverter.convertTo(type, e, value);
        } else {
            return typeConverter.convertTo(type, value);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getHeader(String name, Object defaultValue, Class<T> type) {
        Object value = null;

        if (headers == null) {
            // force creating headers
            headers = createHeaders();
        }

        if (!headers.isEmpty()) {
            value = headers.get(name);
        }
        if (value == null) {
            value = defaultValue;
        }
        if (value == null) {
            // lets avoid NullPointerException when converting to boolean for null values
            if (boolean.class == type) {
                return (T) Boolean.FALSE;
            }
            return null;
        }

        // eager same instance type test to avoid the overhead of invoking the type converter
        // if already same type
        if (type.isInstance(value)) {
            return (T) value;
        }

        Exchange e = getExchange();
        if (e != null) {
            return typeConverter.convertTo(type, e, value);
        } else {
            return typeConverter.convertTo(type, value);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getHeader(String name, Supplier<Object> defaultValueSupplier, Class<T> type) {
        Object value = getHeader(name, defaultValueSupplier);
        if (value == null) {
            // lets avoid NullPointerException when converting to boolean for null values
            if (boolean.class == type) {
                return (T) Boolean.FALSE;
            }
            return null;
        }

        // eager same instance type test to avoid the overhead of invoking the type converter
        // if already same type
        if (type.isInstance(value)) {
            return type.cast(value);
        }

        Exchange e = getExchange();
        if (e != null) {
            return typeConverter.convertTo(type, e, value);
        } else {
            return typeConverter.convertTo(type, value);
        }
    }

    @Override
    public void setHeader(String name, Object value) {
        if (headers == null) {
            headers = createHeaders();
        }
        headers.put(name, value);
    }

    @Override
    public Object removeHeader(String name) {
        if (headers == null) {
            // force creating headers
            headers = createHeaders();
        }
        if (headers.isEmpty()) {
            return null;
        }
        return headers.remove(name);
    }

    @Override
    public boolean removeHeaders(String pattern) {
        return removeHeaders(pattern, (String[]) null);
    }

    @Override
    public boolean removeHeaders(String pattern, String... excludePatterns) {
        if (headers == null) {
            // force creating headers
            headers = createHeaders();
        }
        if (headers.isEmpty()) {
            return false;
        }

        boolean matches = false;
        // must use a set to store the keys to remove as we cannot walk using entrySet and remove at the same time
        // due concurrent modification error
        Set<String> toRemove = new HashSet<>();
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            String key = entry.getKey();
            if (PatternHelper.matchPattern(key, pattern)) {
                if (excludePatterns != null && PatternHelper.isExcludePatternMatch(key, excludePatterns)) {
                    continue;
                }
                matches = true;
                toRemove.add(entry.getKey());
            }
        }
        for (String key : toRemove) {
            headers.remove(key);
        }

        return matches;
    }

    @Override
    public Map<String, Object> getHeaders() {
        if (headers == null) {
            headers = createHeaders();
        }
        return headers;
    }

    @Override
    public void setHeaders(Map<String, Object> headers) {
        HeadersMapFactory factory = camelContext.getHeadersMapFactory();
        if (factory != null) {
            if (factory.isInstanceOf(headers)) {
                this.headers = headers;
            } else {
                // create a new map
                this.headers = camelContext.getHeadersMapFactory().newMap(headers);
            }
        } else {
            // should not really happen but some tests rely on using camel context that is not started
            this.headers = new HashMap<>(headers);
        }
    }

    @Override
    public boolean hasHeaders() {
        if (headers == null) {
            // force creating headers
            headers = createHeaders();
        }
        return !headers.isEmpty();
    }

    @Override
    public DefaultMessage newInstance() {
        return new DefaultMessage(camelContext);
    }

    /**
     * A factory method to lazily create the headers to make it easy to create efficient Message implementations which
     * only construct and populate the Map on demand
     *
     * @return return a newly constructed Map possibly containing headers from the underlying inbound transport
     */
    protected Map<String, Object> createHeaders() {
        Map<String, Object> map;

        HeadersMapFactory factory = camelContext.getHeadersMapFactory();
        if (factory != null) {
            map = factory.newMap();
        } else {
            // should not really happen but some tests rely on using camel context that is not started
            map = new HashMap<>();
        }
        populateInitialHeaders(map);
        return map;
    }

    /**
     * A strategy method populate the initial set of headers on an inbound message from an underlying binding
     *
     * @param map is the empty header map to populate
     */
    protected void populateInitialHeaders(Map<String, Object> map) {
        // do nothing by default
    }

    /**
     * A strategy for component specific messages to determine whether the message is redelivered or not.
     * <p/>
     * <b>Important: </b> It is not always possible to determine if the transacted is a redelivery or not, and therefore
     * <tt>null</tt> is returned. Such an example would be a JDBC message. However JMS brokers provides details if a
     * transacted message is redelivered.
     *
     * @return <tt>true</tt> if redelivered, <tt>false</tt> if not, <tt>null</tt> if not able to determine
     */
    protected Boolean isTransactedRedelivered() {
        // return null by default
        return null;
    }

    /**
     * Returns true if the headers have been mutated in some way
     */
    protected boolean hasPopulatedHeaders() {
        return headers != null;
    }

}
