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
package org.apache.camel.support.component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.EndpointUriFactory;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

/**
 * Base class used by Camel Package Maven Plugin when it generates source code for fast endpoint uri factory via
 * {@link EndpointUriFactory}.
 */
public abstract class EndpointUriFactorySupport implements EndpointUriFactory {

    protected CamelContext camelContext;

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    private static int indexOfPathParameter(String uri, String name) {
        // the last match after the scheme: the path parameters are built in the order of the syntax, so the values
        // of those before this one are before its name, and may contain it as a word ({{name}}) (CAMEL-25383)
        int scheme = uri.indexOf(':');
        int idx = uri.lastIndexOf(name);
        while (idx > scheme) {
            int end = idx + name.length();
            boolean start = idx == 0 || !Character.isLetterOrDigit(uri.charAt(idx - 1));
            boolean stop = end == uri.length() || !Character.isLetterOrDigit(uri.charAt(end));
            if (start && stop) {
                return idx;
            }
            idx = uri.lastIndexOf(name, idx - 1);
        }
        return -1;
    }

    protected String buildPathParameter(
            String syntax, String uri, String name, Object defaultValue, boolean required,
            Map<String, Object> parameters) {
        Object obj = parameters.remove(name);
        if (ObjectHelper.isEmpty(obj) && defaultValue != null && required) {
            obj = camelContext.getTypeConverter().convertTo(String.class, defaultValue);
        }
        if (ObjectHelper.isEmpty(obj) && required) {
            throw new IllegalArgumentException(
                    "Option " + name + " is required when creating endpoint uri with syntax " + syntax);
        }
        // the name of the path parameter in the syntax (and not such as the scheme or part of another name)
        int pos = indexOfPathParameter(uri, name);
        if (ObjectHelper.isNotEmpty(obj)) {
            String str = camelContext.getTypeConverter().convertTo(String.class, obj);
            if (pos != -1) {
                uri = uri.substring(0, pos) + str + uri.substring(pos + name.length());
            }
        } else {
            // the option is optional, and we have no default or value for it, so we need to
            // remove it from the syntax
            if (pos != -1) {
                // remove from syntax
                uri = uri.substring(0, pos) + uri.substring(pos + name.length());
                pos = pos - 1;
                // remove the separator char
                char ch = uri.charAt(pos);
                if (!Character.isLetterOrDigit(ch)) {
                    uri = uri.substring(0, pos) + uri.substring(pos + 1);
                }
            }
        }

        return uri;
    }

    /**
     * A copy of the parameters to build the uri from: in their order when they have one (a {@link LinkedHashMap} such
     * as the parameters of a route in the YAML DSL, which then keep the order they are written in), otherwise sorted.
     */
    protected static Map<String, Object> copyParameters(Map<String, Object> parameters) {
        if (isOrdered(parameters)) {
            return new LinkedHashMap<>(parameters);
        }
        return new TreeMap<>(parameters);
    }

    private static boolean isOrdered(Map<?, ?> map) {
        return map instanceof LinkedHashMap || map instanceof SortedMap;
    }

    protected String buildQueryParameters(String uri, Map<String, Object> parameters, boolean encode) {

        Map<String, Object> map = copyParameters(parameters);

        // automatic use RAW(value) for secret options
        for (String secretParameter : secretPropertyNames()) {
            Object val = map.get(secretParameter);
            if (val instanceof String answer) {
                if (!answer.startsWith("#") && !answer.startsWith("RAW(")) {
                    map.put(secretParameter, "RAW(" + val + ")");
                }
            }
        }

        // flatten all multiValue=true maps into parameters with prefix, where the map option is
        Map<String, String> prefixes = multiValuePrefixes();
        if (!prefixes.isEmpty()) {
            Map<String, Object> flat = map instanceof SortedMap ? new TreeMap<>() : new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : map.entrySet()) {
                String prefix = prefixes.get(e.getKey());
                if (prefix != null && e.getValue() instanceof Map<?, ?> m) {
                    // each entry in map becomes a new option with the prefix key
                    Map<String, Object> options = isOrdered(m) ? new LinkedHashMap<>() : new TreeMap<>();
                    m.forEach((k, v) -> {
                        if (v != null) {
                            options.put(prefix + k, v);
                        }
                    });
                    flat.putAll(options);
                } else {
                    flat.put(e.getKey(), e.getValue());
                }
            }
            map = flat;
        }

        String query = URISupport.createQueryString(map, encode);
        if (ObjectHelper.isNotEmpty(query)) {
            // there may be a ? sign in the context path then use & instead
            // (this is not correct but lets deal with this as the camel-catalog handled this)
            boolean questionMark = uri.indexOf('?') != -1;
            if (questionMark) {
                uri = uri + "&" + query;
            } else {
                uri = uri + "?" + query;
            }
        }
        return uri;
    }

}
