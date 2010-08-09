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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.PatternSyntaxException;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.Route;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Some helper methods for working with {@link Endpoint} instances
 *
 * @version $Revision$
 */
public final class EndpointHelper {

    private static final transient Log LOG = LogFactory.getLog(EndpointHelper.class);
    private static final AtomicLong endpointCounter = new AtomicLong(0);

    private EndpointHelper() {
        //Utility Class
    }

    /**
     * Creates a {@link PollingConsumer} and polls all pending messages on the endpoint
     * and invokes the given {@link Processor} to process each {@link Exchange} and then closes
     * down the consumer and throws any exceptions thrown.
     */
    public static void pollEndpoint(Endpoint endpoint, Processor processor, long timeout) throws Exception {
        PollingConsumer consumer = endpoint.createPollingConsumer();
        try {
            consumer.start();

            while (true) {
                Exchange exchange = consumer.receive(timeout);
                if (exchange == null) {
                    break;
                } else {
                    processor.process(exchange);
                }
            }
        } finally {
            try {
                consumer.stop();
            } catch (Exception e) {
                LOG.warn("Failed to stop PollingConsumer: " + e, e);
            }
        }
    }

    /**
     * Creates a {@link PollingConsumer} and polls all pending messages on the
     * endpoint and invokes the given {@link Processor} to process each
     * {@link Exchange} and then closes down the consumer and throws any
     * exceptions thrown.
     */
    public static void pollEndpoint(Endpoint endpoint, Processor processor) throws Exception {
        pollEndpoint(endpoint, processor, 1000L);
    }

    /**
     * Matches the endpoint with the given pattern.
     * <p/>
     * The match rules are applied in this order:
     * <ul>
     *   <li>exact match, returns true</li>
     *   <li>wildcard match (pattern ends with a * and the uri starts with the pattern), returns true</li>
     *   <li>regular expression match, returns true</li>
     *   <li>otherwise returns false</li>
     * </ul>
     *
     * @param uri     the endpoint uri
     * @param pattern a pattern to match
     * @return <tt>true</tt> if match, <tt>false</tt> otherwise.
     */
    public static boolean matchEndpoint(String uri, String pattern) {
        // normalize uri so we can do endpoint hits with minor mistakes and parameters is not in the same order
        try {
            uri = URISupport.normalizeUri(uri);
        } catch (Exception e) {
            throw new ResolveEndpointFailedException(uri, e);
        }

        // we need to test with and without scheme separators (//)
        if (uri.indexOf("://") != -1) {
            // try without :// also
            String scheme = ObjectHelper.before(uri, "://");
            String path = ObjectHelper.after(uri, "://");
            if (matchPattern(scheme + ":" + path, pattern)) {
                return true;
            }
        } else {
            // try with :// also
            String scheme = ObjectHelper.before(uri, ":");
            String path = ObjectHelper.after(uri, ":");
            if (matchPattern(scheme + "://" + path, pattern)) {
                return true;
            }
        }

        // and fallback to test with the uri as is
        return matchPattern(uri, pattern);
    }

    /**
     * Matches the name with the given pattern.
     * <p/>
     * The match rules are applied in this order:
     * <ul>
     *   <li>exact match, returns true</li>
     *   <li>wildcard match (pattern ends with a * and the name starts with the pattern), returns true</li>
     *   <li>regular expression match, returns true</li>
     *   <li>otherwise returns false</li>
     * </ul>
     *
     * @param name    the name
     * @param pattern a pattern to match
     * @return <tt>true</tt> if match, <tt>false</tt> otherwise.
     */
    public static boolean matchPattern(String name, String pattern) {
        if (name == null || pattern == null) {
            return false;
        }

        if (name.equals(pattern)) {
            // exact match
            return true;
        }

        if (matchWildcard(name, pattern)) {
            return true;
        }

        if (matchRegex(name, pattern)) {
            return true;
        }

        // no match
        return false;
    }

    /**
     * Matches the name with the given pattern.
     * <p/>
     * The match rules are applied in this order:
     * <ul>
     *   <li>wildcard match (pattern ends with a * and the name starts with the pattern), returns true</li>
     *   <li>otherwise returns false</li>
     * </ul>
     *
     * @param name    the name
     * @param pattern a pattern to match
     * @return <tt>true</tt> if match, <tt>false</tt> otherwise.
     */
    private static boolean matchWildcard(String name, String pattern) {
        // we have wildcard support in that hence you can match with: file* to match any file endpoints
        if (pattern.endsWith("*") && name.startsWith(pattern.substring(0, pattern.length() - 1))) {
            return true;
        }
        return false;
    }

    /**
     * Matches the name with the given pattern.
     * <p/>
     * The match rules are applied in this order:
     * <ul>
     *   <li>regular expression match, returns true</li>
     *   <li>otherwise returns false</li>
     * </ul>
     *
     * @param name    the name
     * @param pattern a pattern to match
     * @return <tt>true</tt> if match, <tt>false</tt> otherwise.
     */
    private static boolean matchRegex(String name, String pattern) {
        // match by regular expression
        try {
            if (name.matches(pattern)) {
                return true;
            }
        } catch (PatternSyntaxException e) {
            // ignore
        }
        return false;
    }

    /**
     * Sets the regular properties on the given bean
     *
     * @param context    the camel context
     * @param bean       the bean
     * @param parameters parameters
     * @throws Exception is thrown if setting property fails
     */
    public static void setProperties(CamelContext context, Object bean, Map<String, Object> parameters) throws Exception {
        IntrospectionSupport.setProperties(context.getTypeConverter(), bean, parameters);
    }

    /**
     * Sets the reference properties on the given bean
     * <p/>
     * This is convention over configuration, setting all reference parameters (using {@link #isReferenceParameter(String)}
     * by looking it up in registry and setting it on the bean if possible.
     *
     * @param context    the camel context
     * @param bean       the bean
     * @param parameters parameters
     * @throws Exception is thrown if setting property fails
     */
    public static void setReferenceProperties(CamelContext context, Object bean, Map<String, Object> parameters) throws Exception {
        Iterator<Map.Entry<String, Object>> it = parameters.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> entry = it.next();
            String name = entry.getKey();
            Object v = entry.getValue();
            String value = v != null ? v.toString() : null;
            if (value != null && isReferenceParameter(value)) {
                // For backwards-compatibility reasons, no mandatory lookup is done here
                Object ref = resolveReferenceParameter(context, value, Object.class, false);
                if (ref != null) {
                    boolean hit = IntrospectionSupport.setProperty(context.getTypeConverter(), bean, name, ref);
                    if (hit) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Configured property: " + name + " on bean: " + bean + " with value: " + ref);
                        }
                        // must remove as its a valid option and we could configure it
                        it.remove();
                    }
                }
            }
        }
    }

    /**
     * Is the given parameter a reference parameter (starting with a # char)
     *
     * @param parameter the parameter
     * @return <tt>true</tt> if its a reference parameter
     */
    public static boolean isReferenceParameter(String parameter) {
        return parameter != null && parameter.trim().startsWith("#");
    }

    /**
     * Resolves a reference parameter by making a lookup in the registry.
     *
     * @param <T>     type of object to lookup.
     * @param context Camel context to use for lookup.
     * @param value   reference parameter value.
     * @param type    type of object to lookup.
     * @return lookup result.
     * @throws IllegalArgumentException if referenced object was not found in registry.
     */
    public static <T> T resolveReferenceParameter(CamelContext context, String value, Class<T> type) {
        return resolveReferenceParameter(context, value, type, true);
    }

    /**
     * Resolves a reference parameter by making a lookup in the registry.
     *
     * @param <T>     type of object to lookup.
     * @param context Camel context to use for lookup.
     * @param value   reference parameter value.
     * @param type    type of object to lookup.
     * @return lookup result (or <code>null</code> only if
     *         <code>mandatory</code> is <code>false</code>).
     * @throws IllegalArgumentException if object was not found in registry and
     *                                  <code>mandatory</code> is <code>true</code>.
     */
    public static <T> T resolveReferenceParameter(CamelContext context, String value, Class<T> type, boolean mandatory) {
        String valueNoHash = value.replaceAll("#", "");
        if (mandatory) {
            return CamelContextHelper.mandatoryLookup(context, valueNoHash, type);
        } else {
            return CamelContextHelper.lookup(context, valueNoHash, type);
        }
    }

    /**
     * Resolves a reference list parameter by making lookups in the registry.
     * The parameter value must be one of the following:
     * <ul>
     *   <li>a comma-separated list of references to beans of type T</li>
     *   <li>a single reference to a bean type T</li>
     *   <li>a single reference to a bean of type java.util.List</li>
     * </ul>
     *
     * @param context     Camel context to use for lookup.
     * @param value       reference parameter value.
     * @param elementType result list element type.
     * @return list of lookup results.
     * @throws IllegalArgumentException if any referenced object was not found in registry.
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> resolveReferenceListParameter(CamelContext context, String value, Class<T> elementType) {
        if (value == null) {
            return Collections.emptyList();
        }
        List<String> elements = Arrays.asList(value.split(","));
        if (elements.size() == 1) {
            Object bean = resolveReferenceParameter(context, elements.get(0).trim(), Object.class);
            if (bean instanceof List) {
                // The bean is a list
                return (List) bean;
            } else {
                // The bean is a list element
                return Arrays.asList(elementType.cast(bean));
            }
        } else { // more than one list element
            ArrayList<T> result = new ArrayList<T>(elements.size());
            for (String element : elements) {
                result.add(resolveReferenceParameter(context, element.trim(), elementType));
            }
            return result;
        }
    }

    /**
     * Gets the route id for the given endpoint in which there is a consumer listening.
     *
     * @param endpoint  the endpoint
     * @return the route id, or <tt>null</tt> if none found
     */
    public static String getRouteIdFromEndpoint(Endpoint endpoint) {
        if (endpoint == null || endpoint.getCamelContext() == null) {
            return null;
        }

        List<Route> routes = endpoint.getCamelContext().getRoutes();
        for (Route route : routes) {
            if (route.getEndpoint().equals(endpoint)
                    || route.getEndpoint().getEndpointKey().equals(endpoint.getEndpointKey())) {
                return route.getId();
            }
        }
        return null;
    }

    /**
     * A helper method for Endpoint implementations to create new Ids for Endpoints which also implement {@link HasId}
     */
    public static String createEndpointId() {
        return "endpoint" + endpointCounter.incrementAndGet();
    }
}
