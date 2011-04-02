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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.NoSuchEndpointException;

import static org.apache.camel.util.ObjectHelper.isEmpty;
import static org.apache.camel.util.ObjectHelper.isNotEmpty;
import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * A number of helper methods
 *
 * @version 
 */
public final class CamelContextHelper {

    /**
     * Utility classes should not have a public constructor.
     */
    private CamelContextHelper() {
    }

    /**
     * Returns the mandatory endpoint for the given URI or the
     * {@link org.apache.camel.NoSuchEndpointException} is thrown
     */
    public static Endpoint getMandatoryEndpoint(CamelContext camelContext, String uri)
        throws NoSuchEndpointException {
        Endpoint endpoint = camelContext.getEndpoint(uri);
        if (endpoint == null) {
            throw new NoSuchEndpointException(uri);
        } else {
            return endpoint;
        }
    }

    /**
     * Returns the mandatory endpoint for the given URI and type or the
     * {@link org.apache.camel.NoSuchEndpointException} is thrown
     */
    public static <T extends Endpoint> T getMandatoryEndpoint(CamelContext camelContext, String uri, Class<T> type) {
        Endpoint endpoint = getMandatoryEndpoint(camelContext, uri);
        return ObjectHelper.cast(type, endpoint);
    }

    /**
     * Converts the given value to the requested type
     */
    public static <T> T convertTo(CamelContext context, Class<T> type, Object value) {
        notNull(context, "camelContext");
        return context.getTypeConverter().convertTo(type, value);
    }

    /**
     * Converts the given value to the specified type throwing an {@link IllegalArgumentException}
     * if the value could not be converted to a non null value
     */
    public static <T> T mandatoryConvertTo(CamelContext context, Class<T> type, Object value) {
        T answer = convertTo(context, type, value);
        if (answer == null) {
            throw new IllegalArgumentException("Value " + value + " converted to " + type.getName() + " cannot be null");
        }
        return answer;
    }

    /**
     * Creates a new instance of the given type using the {@link org.apache.camel.spi.Injector} on the given
     * {@link CamelContext}
     */
    public static <T> T newInstance(CamelContext context, Class<T> beanType) {
        return context.getInjector().newInstance(beanType);
    }

    /**
     * Look up the given named bean in the {@link org.apache.camel.spi.Registry} on the
     * {@link CamelContext}
     */
    public static Object lookup(CamelContext context, String name) {
        return context.getRegistry().lookup(name);
    }

    /**
     * Look up the given named bean of the given type in the {@link org.apache.camel.spi.Registry} on the
     * {@link CamelContext}
     */
    public static <T> T lookup(CamelContext context, String name, Class<T> beanType) {
        return context.getRegistry().lookup(name, beanType);
    }

    /**
     * Look up the given named bean in the {@link org.apache.camel.spi.Registry} on the
     * {@link CamelContext} or throws IllegalArgumentException if not found.
     */
    public static Object mandatoryLookup(CamelContext context, String name) {
        Object answer = lookup(context, name);
        notNull(answer, "registry entry called " + name);
        return answer;
    }

    /**
     * Look up the given named bean of the given type in the {@link org.apache.camel.spi.Registry} on the
     * {@link CamelContext} or throws IllegalArgumentException if not found.
     */
    public static <T> T mandatoryLookup(CamelContext context, String name, Class<T> beanType) {
        T answer = lookup(context, name, beanType);
        notNull(answer, "registry entry called " + name + " of type " + beanType.getName());
        return answer;
    }

    /**
     * Evaluates the @EndpointInject annotation using the given context
     */
    public static Endpoint getEndpointInjection(CamelContext camelContext, String uri, String ref, String injectionPointName, boolean mandatory) {
        if (ObjectHelper.isNotEmpty(uri) && ObjectHelper.isNotEmpty(ref)) {
            throw new IllegalArgumentException("Both uri and name is provided, only either one is allowed: uri=" + uri + ", ref=" + ref);
        }

        Endpoint endpoint;
        if (isNotEmpty(uri)) {
            endpoint = camelContext.getEndpoint(uri);
        } else {
            // if a ref is given then it should be possible to lookup
            // otherwise we do not catch situations where there is a typo etc
            if (isNotEmpty(ref)) {
                endpoint = mandatoryLookup(camelContext, ref, Endpoint.class);
            } else {
                if (isEmpty(ref)) {
                    ref = injectionPointName;
                }
                if (mandatory) {
                    endpoint = mandatoryLookup(camelContext, ref, Endpoint.class);
                } else {
                    endpoint = lookup(camelContext, ref, Endpoint.class);
                }
            }
        }
        return endpoint;
    }

    /**
     * Gets the maximum cache pool size.
     * <p/>
     * Will use the property set on CamelContext with the key {@link Exchange#MAXIMUM_CACHE_POOL_SIZE}.
     * If no property has been set, then it will fallback to return a size of 1000.
     *
     * @param camelContext the camel context
     * @return the maximum cache size
     * @throws IllegalArgumentException is thrown if the property is illegal
     */
    public static int getMaximumCachePoolSize(CamelContext camelContext) throws IllegalArgumentException {
        if (camelContext != null) {
            String s = camelContext.getProperties().get(Exchange.MAXIMUM_CACHE_POOL_SIZE);
            if (s != null) {
                try {
                    // we cannot use Camel type converters as they may not be ready this early
                    Integer size = Integer.valueOf(s);
                    if (size == null || size <= 0) {
                        throw new IllegalArgumentException("Property " + Exchange.MAXIMUM_CACHE_POOL_SIZE + " must be a positive number, was: " + s);
                    }
                    return size;
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Property " + Exchange.MAXIMUM_CACHE_POOL_SIZE + " must be a positive number, was: " + s, e);
                }
            }
        }

        // 1000 is the default fallback
        return 1000;
    }

    /**
     * Gets the maximum endpoint cache size.
     * <p/>
     * Will use the property set on CamelContext with the key {@link Exchange#MAXIMUM_ENDPOINT_CACHE_SIZE}.
     * If no property has been set, then it will fallback to return a size of 1000.
     *
     * @param camelContext the camel context
     * @return the maximum cache size
     * @throws IllegalArgumentException is thrown if the property is illegal
     */
    public static int getMaximumEndpointCacheSize(CamelContext camelContext) throws IllegalArgumentException {
        if (camelContext != null) {
            String s = camelContext.getProperties().get(Exchange.MAXIMUM_ENDPOINT_CACHE_SIZE);
            if (s != null) {
                // we cannot use Camel type converters as they may not be ready this early
                try {
                    Integer size = Integer.valueOf(s);
                    if (size == null || size <= 0) {
                        throw new IllegalArgumentException("Property " + Exchange.MAXIMUM_ENDPOINT_CACHE_SIZE + " must be a positive number, was: " + s);
                    }
                    return size;
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Property " + Exchange.MAXIMUM_ENDPOINT_CACHE_SIZE + " must be a positive number, was: " + s, e);
                }
            }
        }

        // 1000 is the default fallback
        return 1000;
    }

    /**
     * Parses the given text and handling property placeholders as well
     *
     * @param camelContext the camel context
     * @param text  the text
     * @return the parsed text, or <tt>null</tt> if the text was <tt>null</tt>
     * @throws Exception is thrown if illegal argument
     */
    public static String parseText(CamelContext camelContext, String text) throws Exception {
        // ensure we support property placeholders
        return camelContext.resolvePropertyPlaceholders(text);
    }

    /**
     * Parses the given text and converts it to an Integer and handling property placeholders as well
     *
     * @param camelContext the camel context
     * @param text  the text
     * @return the integer vale, or <tt>null</tt> if the text was <tt>null</tt>
     * @throws Exception is thrown if illegal argument or type conversion not possible
     */
    public static Integer parseInteger(CamelContext camelContext, String text) throws Exception {
        // ensure we support property placeholders
        String s = camelContext.resolvePropertyPlaceholders(text);
        if (s != null) {
            try {
                return camelContext.getTypeConverter().mandatoryConvertTo(Integer.class, s);
            } catch (NumberFormatException e) {
                if (s.equals(text)) {
                    throw new IllegalArgumentException("Error parsing [" + s + "] as an Integer.", e);
                } else {
                    throw new IllegalArgumentException("Error parsing [" + s + "] from property " + text + " as an Integer.", e);
                }
            }
        }
        return null;
    }

    /**
     * Parses the given text and converts it to an Long and handling property placeholders as well
     *
     * @param camelContext the camel context
     * @param text  the text
     * @return the long vale, or <tt>null</tt> if the text was <tt>null</tt>
     * @throws Exception is thrown if illegal argument or type conversion not possible
     */
    public static Long parseLong(CamelContext camelContext, String text) throws Exception {
        // ensure we support property placeholders
        String s = camelContext.resolvePropertyPlaceholders(text);
        if (s != null) {
            try {
                return camelContext.getTypeConverter().mandatoryConvertTo(Long.class, s);
            } catch (NumberFormatException e) {
                if (s.equals(text)) {
                    throw new IllegalArgumentException("Error parsing [" + s + "] as a Long.", e);
                } else {
                    throw new IllegalArgumentException("Error parsing [" + s + "] from property " + text + " as a Long.", e);
                }
            }
        }
        return null;
    }

    /**
     * Parses the given text and converts it to a Double and handling property placeholders as well
     *
     * @param camelContext the camel context
     * @param text  the text
     * @return the double vale, or <tt>null</tt> if the text was <tt>null</tt>
     * @throws Exception is thrown if illegal argument or type conversion not possible
     */
    public static Double parseDouble(CamelContext camelContext, String text) throws Exception {
        // ensure we support property placeholders
        String s = camelContext.resolvePropertyPlaceholders(text);
        if (s != null) {
            try {
                return camelContext.getTypeConverter().mandatoryConvertTo(Double.class, s);
            } catch (NumberFormatException e) {
                if (s.equals(text)) {
                    throw new IllegalArgumentException("Error parsing [" + s + "] as an Integer.", e);
                } else {
                    throw new IllegalArgumentException("Error parsing [" + s + "] from property " + text + " as an Integer.", e);
                }
            }
        }
        return null;
    }

    /**
     * Parses the given text and converts it to an Boolean and handling property placeholders as well
     *
     * @param camelContext the camel context
     * @param text  the text
     * @return the boolean vale, or <tt>null</tt> if the text was <tt>null</tt>
     * @throws Exception is thrown if illegal argument or type conversion not possible
     */
    public static Boolean parseBoolean(CamelContext camelContext, String text) throws Exception {
        // ensure we support property placeholders
        String s = camelContext.resolvePropertyPlaceholders(text);
        if (s != null) {
            s = s.trim().toLowerCase();
            if (s.equals("true") || s.equals("false")) {
                return camelContext.getTypeConverter().mandatoryConvertTo(Boolean.class, s);
            } else {
                if (s.equals(text)) {
                    throw new IllegalArgumentException("Error parsing [" + s + "] as a Boolean.");
                } else {
                    throw new IllegalArgumentException("Error parsing [" + s + "] from property " + text + " as a Boolean.");
                }
            }
        }
        return null;
    }

}
