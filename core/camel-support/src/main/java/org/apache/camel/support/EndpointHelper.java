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

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.CamelContext;
import org.apache.camel.DelegateEndpoint;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.Route;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.StringHelper.after;

/**
 * Some helper methods for working with {@link Endpoint} instances
 */
public final class EndpointHelper {

    private static final Logger LOG = LoggerFactory.getLogger(EndpointHelper.class);
    private static final AtomicLong ENDPOINT_COUNTER = new AtomicLong();

    private EndpointHelper() {
        //Utility Class
    }

    /**
     * Resolves the endpoint uri that may have property placeholders (supports optional property placeholders).
     *
     * @param  camelContext the camel context
     * @param  uri          the endpoint uri
     * @return              returns endpoint uri with property placeholders resolved
     */
    public static String resolveEndpointUriPropertyPlaceholders(CamelContext camelContext, String uri) {
        // the uri may have optional property placeholders which is not possible to resolve
        // so we keep the unresolved in the uri, which we then afterwards will remove
        // which is a little complex depending on the placeholder is from context-path or query parameters
        // in the uri string
        try {
            uri = camelContext.getCamelContextExtension().resolvePropertyPlaceholders(uri, true);
            if (uri == null || uri.isEmpty()) {
                return uri;
            }
            String prefix = PropertiesComponent.PREFIX_OPTIONAL_TOKEN;
            if (uri.contains(prefix)) {
                String unresolved = uri;
                uri = doResolveEndpointUriOptionalPropertyPlaceholders(unresolved);
                LOG.trace("Unresolved optional placeholders removed from uri: {} -> {}", unresolved, uri);
            }
            LOG.trace("Resolved property placeholders with uri: {}", uri);
        } catch (Exception e) {
            throw new ResolveEndpointFailedException(uri, e);
        }
        return uri;
    }

    private static String doResolveEndpointUriOptionalPropertyPlaceholders(String uri) throws URISyntaxException {
        String prefix = PropertiesComponent.PREFIX_OPTIONAL_TOKEN;

        // find query position which is the first question mark that is not part of the optional token prefix
        int pos = 0;
        for (int i = 0; i < uri.length(); i++) {
            char ch = uri.charAt(i);
            if (ch == '?') {
                // ensure that its not part of property prefix
                if (i > 2) {
                    char ch1 = uri.charAt(i - 1);
                    char ch2 = uri.charAt(i - 2);
                    if (ch1 != '{' && ch2 != '{') {
                        pos = i;
                        break;
                    }
                } else {
                    pos = i;
                    break;
                }
            }
        }
        String base = pos > 0 ? uri.substring(0, pos) : uri;
        String query = pos > 0 ? uri.substring(pos + 1) : null;

        // the base (context path) should remove all unresolved property placeholders
        // which is done by replacing all begin...end tokens with an empty string
        String pattern = "\\{\\{?.*}}";
        base = base.replaceAll(pattern, "");

        // the query parameters needs to be rebuild by removing the unresolved key=value pairs
        if (query != null && query.contains(prefix)) {
            Map<String, Object> params = URISupport.parseQuery(query);
            Map<String, Object> keep = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(prefix)) {
                    continue;
                }
                Object value = entry.getValue();
                if (value instanceof String) {
                    String s = value.toString();
                    if (s.startsWith(prefix)) {
                        continue;
                    }
                    // okay the value may use a resource loader with a scheme prefix
                    int dot = s.indexOf(':');
                    if (dot > 0 && dot < s.length() - 1) {
                        s = s.substring(dot + 1);
                        if (s.startsWith(prefix)) {
                            continue;
                        }
                    }
                }
                keep.put(key, value);
            }
            // rebuild query
            query = URISupport.createQueryString(keep);
        }

        // assemble uri as answer
        uri = query != null && !query.isEmpty() ? base + "?" + query : base;
        return uri;
    }

    /**
     * Normalize uri so we can do endpoint hits with minor mistakes and parameters is not in the same order.
     *
     * @param  uri                            the uri
     * @return                                normalized uri
     * @throws ResolveEndpointFailedException if uri cannot be normalized
     */
    public static String normalizeEndpointUri(String uri) {
        try {
            uri = URISupport.normalizeUri(uri);
        } catch (Exception e) {
            throw new ResolveEndpointFailedException(uri, e);
        }
        return uri;
    }

    /**
     * Creates a {@link PollingConsumer} and polls all pending messages on the endpoint and invokes the given
     * {@link Processor} to process each {@link Exchange} and then closes down the consumer and throws any exceptions
     * thrown.
     */
    public static void pollEndpoint(Endpoint endpoint, Processor processor, long timeout) throws Exception {
        PollingConsumer consumer = endpoint.createPollingConsumer();
        try {
            ServiceHelper.startService(consumer);

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
                ServiceHelper.stopAndShutdownService(consumer);
            } catch (Exception e) {
                LOG.warn("Failed to stop PollingConsumer: {}. This example is ignored.", consumer, e);
            }
        }
    }

    /**
     * Creates a {@link PollingConsumer} and polls all pending messages on the endpoint and invokes the given
     * {@link Processor} to process each {@link Exchange} and then closes down the consumer and throws any exceptions
     * thrown.
     */
    public static void pollEndpoint(Endpoint endpoint, Processor processor) throws Exception {
        pollEndpoint(endpoint, processor, 1000L);
    }

    /**
     * Matches the endpoint with the given pattern.
     * <p/>
     * The endpoint will first resolve property placeholders using
     * {@link #resolveEndpointUriPropertyPlaceholders(CamelContext, String)}
     * <p/>
     * The match rules are applied in this order:
     * <ul>
     * <li>exact match, returns true</li>
     * <li>wildcard match (pattern ends with a * and the uri starts with the pattern), returns true</li>
     * <li>regular expression match, returns true</li>
     * <li>exact match with uri normalization of the pattern if possible, returns true</li>
     * <li>otherwise returns false</li>
     * </ul>
     *
     * @param  context the Camel context, if <tt>null</tt> then property placeholder resolution is skipped.
     * @param  uri     the endpoint uri
     * @param  pattern a pattern to match
     * @return         <tt>true</tt> if match, <tt>false</tt> otherwise.
     */
    public static boolean matchEndpoint(CamelContext context, String uri, String pattern) {
        if (context != null) {
            try {
                uri = resolveEndpointUriPropertyPlaceholders(context, uri);
            } catch (Exception e) {
                throw new ResolveEndpointFailedException(uri, e);
            }
        }

        // normalize uri so we can do endpoint hits with minor mistakes and parameters is not in the same order
        uri = normalizeEndpointUri(uri);

        // we need to test with and without scheme separators (//)
        boolean match = PatternHelper.matchPattern(toggleUriSchemeSeparators(uri), pattern);
        match |= PatternHelper.matchPattern(uri, pattern);
        if (!match && pattern != null && pattern.contains("?")) {
            // try normalizing the pattern as a uri for exact matching, so parameters are ordered the same as in the endpoint uri
            try {
                pattern = URISupport.normalizeUri(pattern);
                // try both with and without scheme separators (//)
                match = toggleUriSchemeSeparators(uri).equalsIgnoreCase(pattern);
                return match || uri.equalsIgnoreCase(pattern);
            } catch (URISyntaxException e) {
                //Can't normalize and original match failed
                return false;
            } catch (Exception e) {
                throw new ResolveEndpointFailedException(uri, e);
            }
        }
        return match;
    }

    /**
     * Toggles // separators in the given uri. If the uri does not contain ://, the slashes are added, otherwise they
     * are removed.
     *
     * @param  normalizedUri The uri to add/remove separators in
     * @return               The uri with separators added or removed
     */
    private static String toggleUriSchemeSeparators(String normalizedUri) {
        if (normalizedUri.contains("://")) {
            String scheme = StringHelper.before(normalizedUri, "://");
            String path = after(normalizedUri, "://");
            return scheme + ":" + path;
        } else {
            String scheme = StringHelper.before(normalizedUri, ":");
            String path = after(normalizedUri, ":");
            return scheme + "://" + path;
        }
    }

    /**
     * Is the given parameter a reference parameter (starting with a # char)
     *
     * @param  parameter the parameter
     * @return           <tt>true</tt> if its a reference parameter
     */
    public static boolean isReferenceParameter(String parameter) {
        return parameter != null && parameter.trim().startsWith("#") && parameter.trim().length() > 1;
    }

    /**
     * Resolves a reference parameter by making a lookup in the registry.
     *
     * @param  <T>                      type of object to lookup.
     * @param  context                  Camel context to use for lookup.
     * @param  value                    reference parameter value.
     * @param  type                     type of object to lookup.
     * @return                          lookup result.
     * @throws IllegalArgumentException if referenced object was not found in registry.
     */
    public static <T> T resolveReferenceParameter(CamelContext context, String value, Class<T> type) {
        return resolveReferenceParameter(context, value, type, true);
    }

    /**
     * Resolves a reference parameter by making a lookup in the registry.
     *
     * @param  <T>                 type of object to lookup.
     * @param  context             Camel context to use for lookup.
     * @param  value               reference parameter value.
     * @param  type                type of object to lookup.
     * @return                     lookup result (or <code>null</code> only if <code>mandatory</code> is
     *                             <code>false</code>).
     * @throws NoSuchBeanException if object was not found in registry and <code>mandatory</code> is <code>true</code>.
     */
    public static <T> T resolveReferenceParameter(CamelContext context, String value, Class<T> type, boolean mandatory) {
        Object answer = null;
        if (value.startsWith("#class:")) {
            try {
                answer = createBean(context, value, type);
            } catch (Exception e) {
                throw new NoSuchBeanException(value, e);
            }
        } else if (value.startsWith("#type:")) {
            try {
                value = value.substring(6);
                Class<?> clazz = context.getClassResolver().resolveMandatoryClass(value);
                Set<?> set = context.getRegistry().findByType(clazz);
                if (set.size() == 1) {
                    answer = set.iterator().next();
                } else if (set.size() > 1) {
                    throw new NoSuchBeanException(
                            value, "Found " + set.size() + " beans of type: " + clazz + ". Only 1 bean instance is supported.");
                }
            } catch (ClassNotFoundException e) {
                throw new NoSuchBeanException(value, e);
            }
        } else {
            value = value.replace("#bean:", "");
            value = value.replace("#", "");
            // lookup first with type
            answer = CamelContextHelper.lookup(context, value, type);
            if (answer == null) {
                // fallback to lookup by name
                answer = CamelContextHelper.lookup(context, value);
            }
        }

        if (mandatory && answer == null) {
            if (type != null) {
                throw new NoSuchBeanException(value, type.getTypeName());
            } else {
                throw new NoSuchBeanException(value);
            }
        }
        if (answer != null) {
            if (mandatory) {
                answer = CamelContextHelper.convertTo(context, type, answer);
            } else {
                answer = CamelContextHelper.tryConvertTo(context, type, answer);
            }
        }
        return (T) answer;
    }

    private static <T> T createBean(CamelContext camelContext, String name, Class<T> type) throws Exception {
        Object answer;

        // if there is a factory method then the class/bean should be created in a different way
        String className;
        String factoryMethod = null;
        String parameters = null;
        className = name.substring(7);
        if (className.endsWith(")") && className.indexOf('(') != -1) {
            parameters = StringHelper.after(className, "(");
            parameters = parameters.substring(0, parameters.length() - 1); // clip last )
            className = StringHelper.before(className, "(");
        }
        if (className != null && className.indexOf('#') != -1) {
            factoryMethod = StringHelper.after(className, "#");
            className = StringHelper.before(className, "#");
        }
        Class<?> clazz = camelContext.getClassResolver().resolveMandatoryClass(className);

        if (factoryMethod != null && parameters != null) {
            answer = PropertyBindingSupport.newInstanceFactoryParameters(camelContext, clazz, factoryMethod, parameters);
        } else if (factoryMethod != null) {
            answer = camelContext.getInjector().newInstance(type, factoryMethod);
        } else if (parameters != null) {
            answer = PropertyBindingSupport.newInstanceConstructorParameters(camelContext, clazz, parameters);
        } else {
            answer = camelContext.getInjector().newInstance(clazz);
        }
        if (answer == null) {
            throw new IllegalStateException("Cannot create bean: " + name);
        }
        return type.cast(answer);
    }

    /**
     * Resolves a reference list parameter by making lookups in the registry. The parameter value must be one of the
     * following:
     * <ul>
     * <li>a comma-separated list of references to beans of type T</li>
     * <li>a single reference to a bean type T</li>
     * <li>a single reference to a bean of type java.util.List</li>
     * </ul>
     *
     * @param  context                  Camel context to use for lookup.
     * @param  value                    reference parameter value.
     * @param  elementType              result list element type.
     * @return                          list of lookup results, will always return a list.
     * @throws IllegalArgumentException if any referenced object was not found in registry.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> List<T> resolveReferenceListParameter(CamelContext context, String value, Class<T> elementType) {
        if (value == null) {
            return new ArrayList<>();
        }
        List<String> elements = Arrays.asList(value.split(","));
        if (elements.size() == 1) {
            Object bean = resolveReferenceParameter(context, elements.get(0).trim(), Object.class);
            if (bean instanceof List) {
                // The bean is a list
                return (List) bean;
            } else {
                // The bean is a list element
                List<T> singleElementList = new ArrayList<>();
                singleElementList.add(elementType.cast(bean));
                return singleElementList;
            }
        } else { // more than one list element
            List<T> result = new ArrayList<>(elements.size());
            for (String element : elements) {
                result.add(resolveReferenceParameter(context, element.trim(), elementType));
            }
            return result;
        }
    }

    /**
     * Resolves a parameter, by doing a reference lookup if the parameter is a reference, and converting the parameter
     * to the given type.
     *
     * @param  <T>                      type of object to convert the parameter value as.
     * @param  context                  Camel context to use for lookup.
     * @param  value                    parameter or reference parameter value.
     * @param  type                     type of object to lookup.
     * @return                          lookup result if it was a reference parameter, or the value converted to the
     *                                  given type
     * @throws IllegalArgumentException if referenced object was not found in registry.
     */
    public static <T> T resolveParameter(CamelContext context, String value, Class<T> type) {
        T result;
        if (EndpointHelper.isReferenceParameter(value)) {
            result = EndpointHelper.resolveReferenceParameter(context, value, type);
        } else {
            result = context.getTypeConverter().convertTo(type, value);
        }
        return result;
    }

    /**
     * Gets the route id for the given endpoint in which there is a consumer listening.
     *
     * @param  endpoint the endpoint
     * @return          the route id, or <tt>null</tt> if none found
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
     * A helper method for Endpoint implementations to create new Ids for Endpoints which also implement
     * {@link org.apache.camel.spi.HasId}
     */
    public static String createEndpointId() {
        return "endpoint" + ENDPOINT_COUNTER.incrementAndGet();
    }

    /**
     * Lookup the id the given endpoint has been enlisted with in the {@link org.apache.camel.spi.Registry}.
     *
     * @param  endpoint the endpoint
     * @return          the endpoint id, or <tt>null</tt> if not found
     */
    public static String lookupEndpointRegistryId(Endpoint endpoint) {
        if (endpoint == null || endpoint.getCamelContext() == null) {
            return null;
        }

        // it may be a delegate endpoint, which we need to match as well
        Endpoint delegate = null;
        if (endpoint instanceof DelegateEndpoint) {
            delegate = ((DelegateEndpoint) endpoint).getEndpoint();
        }

        Map<String, Endpoint> map = endpoint.getCamelContext().getRegistry().findByTypeWithName(Endpoint.class);
        for (Map.Entry<String, Endpoint> entry : map.entrySet()) {
            if (entry.getValue().equals(endpoint) || entry.getValue().equals(delegate)) {
                return entry.getKey();
            }
        }

        // not found
        return null;
    }

    /**
     * Attempts to resolve if the url has an <tt>exchangePattern</tt> option configured
     *
     * @param  url the url
     * @return     the exchange pattern, or <tt>null</tt> if the url has no <tt>exchangePattern</tt> configured.
     */
    public static ExchangePattern resolveExchangePatternFromUrl(String url) {
        // optimize to use simple string contains check
        if (url.contains("exchangePattern=InOnly")) {
            return ExchangePattern.InOnly;
        } else if (url.contains("exchangePattern=InOut")) {
            return ExchangePattern.InOut;
        }
        return null;
    }

}
