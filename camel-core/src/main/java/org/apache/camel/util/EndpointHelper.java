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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.camel.CamelContext;
import org.apache.camel.DelegateEndpoint;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.Route;
import org.apache.camel.spi.BrowsableEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.JsonSchemaHelper.getPropertyDefaultValue;
import static org.apache.camel.util.JsonSchemaHelper.getPropertyPrefix;
import static org.apache.camel.util.JsonSchemaHelper.isPropertyMultiValue;
import static org.apache.camel.util.JsonSchemaHelper.isPropertyRequired;
import static org.apache.camel.util.ObjectHelper.after;

/**
 * Some helper methods for working with {@link Endpoint} instances
 */
public final class EndpointHelper {

    private static final Logger LOG = LoggerFactory.getLogger(EndpointHelper.class);
    private static final AtomicLong ENDPOINT_COUNTER = new AtomicLong(0);
    private static final Pattern SYNTAX_PATTERN = Pattern.compile("(\\w+)");

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
                LOG.warn("Failed to stop PollingConsumer: " + consumer + ". This example is ignored.", e);
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
     * The endpoint will first resolve property placeholders using {@link CamelContext#resolvePropertyPlaceholders(String)}.
     * <p/>
     * The match rules are applied in this order:
     * <ul>
     * <li>exact match, returns true</li>
     * <li>wildcard match (pattern ends with a * and the uri starts with the pattern), returns true</li>
     * <li>regular expression match, returns true</li>
     * <li>otherwise returns false</li>
     * </ul>
     *
     * @param context the Camel context, if <tt>null</tt> then property placeholder resolution is skipped.
     * @param uri     the endpoint uri
     * @param pattern a pattern to match
     * @return <tt>true</tt> if match, <tt>false</tt> otherwise.
     */
    public static boolean matchEndpoint(CamelContext context, String uri, String pattern) {
        if (context != null) {
            try {
                uri = context.resolvePropertyPlaceholders(uri);
            } catch (Exception e) {
                throw new ResolveEndpointFailedException(uri, e);
            }
        }

        // normalize uri so we can do endpoint hits with minor mistakes and parameters is not in the same order
        try {
            uri = URISupport.normalizeUri(uri);
        } catch (Exception e) {
            throw new ResolveEndpointFailedException(uri, e);
        }

        // we need to test with and without scheme separators (//)
        if (uri.contains("://")) {
            // try without :// also
            String scheme = ObjectHelper.before(uri, "://");
            String path = after(uri, "://");
            if (matchPattern(scheme + ":" + path, pattern)) {
                return true;
            }
        } else {
            // try with :// also
            String scheme = ObjectHelper.before(uri, ":");
            String path = after(uri, ":");
            if (matchPattern(scheme + "://" + path, pattern)) {
                return true;
            }
        }

        // and fallback to test with the uri as is
        return matchPattern(uri, pattern);
    }

    /**
     * Matches the endpoint with the given pattern.
     *
     * @see #matchEndpoint(org.apache.camel.CamelContext, String, String)
     * @deprecated use {@link #matchEndpoint(org.apache.camel.CamelContext, String, String)} instead.
     */
    @Deprecated
    public static boolean matchEndpoint(String uri, String pattern) {
        return matchEndpoint(null, uri, pattern);
    }

    /**
     * Matches the name with the given pattern.
     * <p/>
     * The match rules are applied in this order:
     * <ul>
     * <li>exact match, returns true</li>
     * <li>wildcard match (pattern ends with a * and the name starts with the pattern), returns true</li>
     * <li>regular expression match, returns true</li>
     * <li>otherwise returns false</li>
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
     * <li>wildcard match (pattern ends with a * and the name starts with the pattern), returns true</li>
     * <li>otherwise returns false</li>
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
     * <li>regular expression match, returns true</li>
     * <li>otherwise returns false</li>
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
                boolean hit = IntrospectionSupport.setProperty(context, context.getTypeConverter(), bean, name, null, value, true);
                if (hit) {
                    // must remove as its a valid option and we could configure it
                    it.remove();
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
     * <code>mandatory</code> is <code>false</code>).
     * @throws IllegalArgumentException if object was not found in registry and
     *                                  <code>mandatory</code> is <code>true</code>.
     */
    public static <T> T resolveReferenceParameter(CamelContext context, String value, Class<T> type, boolean mandatory) {
        String valueNoHash = StringHelper.replaceAll(value, "#", "");
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
     * <li>a comma-separated list of references to beans of type T</li>
     * <li>a single reference to a bean type T</li>
     * <li>a single reference to a bean of type java.util.List</li>
     * </ul>
     *
     * @param context     Camel context to use for lookup.
     * @param value       reference parameter value.
     * @param elementType result list element type.
     * @return list of lookup results, will always return a list.
     * @throws IllegalArgumentException if any referenced object was not found in registry.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> List<T> resolveReferenceListParameter(CamelContext context, String value, Class<T> elementType) {
        if (value == null) {
            return new ArrayList<T>();
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
            List<T> result = new ArrayList<T>(elements.size());
            for (String element : elements) {
                result.add(resolveReferenceParameter(context, element.trim(), elementType));
            }
            return result;
        }
    }

    /**
     * Resolves a parameter, by doing a reference lookup if the parameter is a reference, and converting
     * the parameter to the given type.
     *
     * @param <T>     type of object to convert the parameter value as.
     * @param context Camel context to use for lookup.
     * @param value   parameter or reference parameter value.
     * @param type    type of object to lookup.
     * @return lookup result if it was a reference parameter, or the value converted to the given type
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
     * @deprecated use {@link #resolveParameter(org.apache.camel.CamelContext, String, Class)}
     */
    @Deprecated
    public static <T> T resloveStringParameter(CamelContext context, String value, Class<T> type) {
        return resolveParameter(context, value, type);
    }

    /**
     * Gets the route id for the given endpoint in which there is a consumer listening.
     *
     * @param endpoint the endpoint
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
     * A helper method for Endpoint implementations to create new Ids for Endpoints which also implement
     * {@link org.apache.camel.spi.HasId}
     */
    public static String createEndpointId() {
        return "endpoint" + ENDPOINT_COUNTER.incrementAndGet();
    }

    /**
     * Lookup the id the given endpoint has been enlisted with in the {@link org.apache.camel.spi.Registry}.
     *
     * @param endpoint the endpoint
     * @return the endpoint id, or <tt>null</tt> if not found
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
     * Browses the {@link BrowsableEndpoint} within the given range, and returns the messages as a XML payload.
     *
     * @param endpoint    the browsable endpoint
     * @param fromIndex   from range
     * @param toIndex     to range
     * @param includeBody whether to include the message body in the XML payload
     * @return XML payload with the messages
     * @throws IllegalArgumentException if the from and to range is invalid
     * @see MessageHelper#dumpAsXml(org.apache.camel.Message)
     */
    public static String browseRangeMessagesAsXml(BrowsableEndpoint endpoint, Integer fromIndex, Integer toIndex, Boolean includeBody) {
        if (fromIndex == null) {
            fromIndex = 0;
        }
        if (toIndex == null) {
            toIndex = Integer.MAX_VALUE;
        }
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException("From index cannot be larger than to index, was: " + fromIndex + " > " + toIndex);
        }

        List<Exchange> exchanges = endpoint.getExchanges();
        if (exchanges.size() == 0) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<messages>");
        for (int i = fromIndex; i < exchanges.size() && i <= toIndex; i++) {
            Exchange exchange = exchanges.get(i);
            Message msg = exchange.hasOut() ? exchange.getOut() : exchange.getIn();
            String xml = MessageHelper.dumpAsXml(msg, includeBody);
            sb.append("\n").append(xml);
        }
        sb.append("\n</messages>");
        return sb.toString();
    }

    /**
     * Attempts to resolve if the url has an <tt>exchangePattern</tt> option configured
     *
     * @param url the url
     * @return the exchange pattern, or <tt>null</tt> if the url has no <tt>exchangePattern</tt> configured.
     * @throws URISyntaxException is thrown if uri is invalid
     */
    public static ExchangePattern resolveExchangePatternFromUrl(String url) throws URISyntaxException {
        int idx = url.indexOf("?");
        if (idx > 0) {
            url = url.substring(idx + 1);
        }
        Map<String, Object> parameters = URISupport.parseQuery(url, true);
        String pattern = (String) parameters.get("exchangePattern");
        if (pattern != null) {
            return ExchangePattern.asEnum(pattern);
        }
        return null;
    }

    /**
     * Parses the endpoint uri and builds a map of documentation information for each option which is extracted
     * from the component json documentation
     *
     * @param camelContext the Camel context
     * @param uri          the endpoint uri
     * @return a map for each option in the uri with the corresponding information from the json
     * @throws Exception is thrown in case of error
     */
    // CHECKSTYLE:OFF
    public static Map<String, Object> endpointProperties(CamelContext camelContext, String uri) throws Exception {
        // NOTICE: This logic is similar to org.apache.camel.util.EndpointHelper#endpointProperties
        // as the catalog also offers similar functionality (without having camel-core on classpath)

        // need to normalize uri first

        // parse the uri
        URI u = normalizeUri(uri);
        String scheme = u.getScheme();

        String json = camelContext.getComponentParameterJsonSchema(u.getScheme());
        if (json == null) {
            throw new IllegalArgumentException("Cannot find endpoint with scheme " + scheme);
        }

        // grab the syntax
        String syntax = null;
        String alternativeSyntax = null;
        List<Map<String, String>> rows = JsonSchemaHelper.parseJsonSchema("component", json, false);
        for (Map<String, String> row : rows) {
            if (row.containsKey("syntax")) {
                syntax = row.get("syntax");
            }
            if (row.containsKey("alternativeSyntax")) {
                alternativeSyntax = row.get("alternativeSyntax");
            }
        }
        if (syntax == null) {
            throw new IllegalArgumentException("Endpoint with scheme " + scheme + " has no syntax defined in the json schema");
        }

        // only if we support alternative syntax, and the uri contains the username and password in the authority
        // part of the uri, then we would need some special logic to capture that information and strip those
        // details from the uri, so we can continue parsing the uri using the normal syntax
        Map<String, String> userInfoOptions = new LinkedHashMap<String, String>();
        if (alternativeSyntax != null && alternativeSyntax.contains("@")) {
            // clip the scheme from the syntax
            alternativeSyntax = after(alternativeSyntax, ":");
            // trim so only userinfo
            int idx = alternativeSyntax.indexOf("@");
            String fields = alternativeSyntax.substring(0, idx);
            String[] names = fields.split(":");

            // grab authority part and grab username and/or password
            String authority = u.getAuthority();
            if (authority != null && authority.contains("@")) {
                String username = null;
                String password = null;

                // grab unserinfo part before @
                String userInfo = authority.substring(0, authority.indexOf("@"));
                String[] parts = userInfo.split(":");
                if (parts.length == 2) {
                    username = parts[0];
                    password = parts[1];
                } else {
                    // only username
                    username = userInfo;
                }

                // remember the username and/or password which we add later to the options
                if (names.length == 2) {
                    userInfoOptions.put(names[0], username);
                    if (password != null) {
                        // password is optional
                        userInfoOptions.put(names[1], password);
                    }
                }
            }
        }

        // clip the scheme from the syntax
        syntax = after(syntax, ":");
        // clip the scheme from the uri
        uri = after(uri, ":");
        String uriPath = stripQuery(uri);

        // strip user info from uri path
        if (!userInfoOptions.isEmpty()) {
            int idx = uriPath.indexOf('@');
            if (idx > -1) {
                uriPath = uriPath.substring(idx + 1);
            }
        }

        // strip double slash in the start
        if (uriPath != null && uriPath.startsWith("//")) {
            uriPath = uriPath.substring(2);
        }

        // parse the syntax and find the names of each option
        Matcher matcher = SYNTAX_PATTERN.matcher(syntax);
        List<String> word = new ArrayList<String>();
        while (matcher.find()) {
            String s = matcher.group(1);
            if (!scheme.equals(s)) {
                word.add(s);
            }
        }
        // parse the syntax and find each token between each option
        String[] tokens = SYNTAX_PATTERN.split(syntax);

        // find the position where each option start/end
        List<String> word2 = new ArrayList<String>();
        int prev = 0;
        int prevPath = 0;

        // special for activemq/jms where the enum for destinationType causes a token issue as it includes a colon
        // for 'temp:queue' and 'temp:topic' values
        if ("activemq".equals(scheme) || "jms".equals("scheme")) {
            if (uriPath.startsWith("temp:")) {
                prevPath = 5;
            }
        }

        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }

            // special for some tokens where :// can be used also, eg http://foo
            int idx = -1;
            int len = 0;
            if (":".equals(token)) {
                idx = uriPath.indexOf("://", prevPath);
                len = 3;
            }
            if (idx == -1) {
                idx = uriPath.indexOf(token, prevPath);
                len = token.length();
            }

            if (idx > 0) {
                String option = uriPath.substring(prev, idx);
                word2.add(option);
                prev = idx + len;
                prevPath = prev;
            }
        }
        // special for last or if we did not add anyone
        if (prev > 0 || word2.isEmpty()) {
            String option = uriPath.substring(prev);
            word2.add(option);
        }

        rows = JsonSchemaHelper.parseJsonSchema("properties", json, true);

        boolean defaultValueAdded = false;

        // now parse the uri to know which part isw what
        Map<String, String> options = new LinkedHashMap<String, String>();

        // include the username and password from the userinfo section
        if (!userInfoOptions.isEmpty()) {
            options.putAll(userInfoOptions);
        }

        // word contains the syntax path elements
        Iterator<String> it = word2.iterator();
        for (int i = 0; i < word.size(); i++) {
            String key = word.get(i);

            boolean allOptions = word.size() == word2.size();
            boolean required = isPropertyRequired(rows, key);
            String defaultValue = getPropertyDefaultValue(rows, key);

            // we have all options so no problem
            if (allOptions) {
                String value = it.next();
                options.put(key, value);
            } else {
                // we have a little problem as we do not not have all options
                if (!required) {
                    String value = null;

                    boolean last = i == word.size() - 1;
                    if (last) {
                        // if its the last value then use it instead of the default value
                        value = it.hasNext() ? it.next() : null;
                        if (value != null) {
                            options.put(key, value);
                        } else {
                            value = defaultValue;
                        }
                    }
                    if (value != null) {
                        options.put(key, value);
                        defaultValueAdded = true;
                    }
                } else {
                    String value = it.hasNext() ? it.next() : null;
                    if (value != null) {
                        options.put(key, value);
                    }
                }
            }
        }

        Map<String, Object> answer = new LinkedHashMap<String, Object>();

        // remove all options which are using default values and are not required
        for (Map.Entry<String, String> entry : options.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (defaultValueAdded) {
                boolean required = isPropertyRequired(rows, key);
                String defaultValue = getPropertyDefaultValue(rows, key);

                if (!required && defaultValue != null) {
                    if (defaultValue.equals(value)) {
                        continue;
                    }
                }
            }

            // we should keep this in the answer
            answer.put(key, value);
        }

        // now parse the uri parameters
        Map<String, Object> parameters = URISupport.parseParameters(u);

        // and covert the values to String so its JMX friendly
        while (!parameters.isEmpty()) {
            Map.Entry<String, Object> entry = parameters.entrySet().iterator().next();
            String key = entry.getKey();
            String value = entry.getValue() != null ? entry.getValue().toString() : "";

            boolean multiValued = isPropertyMultiValue(rows, key);
            if (multiValued) {
                String prefix = getPropertyPrefix(rows, key);
                // extra all the multi valued options
                Map<String, Object> values = URISupport.extractProperties(parameters, prefix);
                // build a string with the extra multi valued options with the prefix and & as separator
                CollectionStringBuffer csb = new CollectionStringBuffer("&");
                for (Map.Entry<String, Object> multi : values.entrySet()) {
                    String line = prefix + multi.getKey() + "=" + (multi.getValue() != null ? multi.getValue().toString() : "");
                    csb.append(line);
                }
                // append the extra multi-values to the existing (which contains the first multi value)
                if (!csb.isEmpty()) {
                    value = value + "&" + csb.toString();
                }
            }

            answer.put(key, value);
            // remove the parameter as we run in a while loop until no more parameters
            parameters.remove(key);
        }

        return answer;
    }
    // CHECKSTYLE:ON

    /**
     * Normalizes the URI so unsafe characters is encoded
     *
     * @param uri the input uri
     * @return as URI instance
     * @throws URISyntaxException is thrown if syntax error in the input uri
     */
    private static URI normalizeUri(String uri) throws URISyntaxException {
        return new URI(UnsafeUriCharactersEncoder.encode(uri, true));
    }

    /**
     * Strips the query parameters from the uri
     *
     * @param uri the uri
     * @return the uri without the query parameter
     */
    private static String stripQuery(String uri) {
        int idx = uri.indexOf('?');
        if (idx > -1) {
            uri = uri.substring(0, idx);
        }
        return uri;
    }

}
