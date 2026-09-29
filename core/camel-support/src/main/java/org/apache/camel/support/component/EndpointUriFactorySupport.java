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

import java.util.Map;
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
        // skip the scheme
        int idx = uri.indexOf(name, uri.indexOf(':') + 1);
        while (idx != -1) {
            int end = idx + name.length();
            boolean start = idx == 0 || !Character.isLetterOrDigit(uri.charAt(idx - 1));
            boolean stop = end == uri.length() || !Character.isLetterOrDigit(uri.charAt(end));
            if (start && stop) {
                return idx;
            }
            idx = uri.indexOf(name, idx + 1);
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

    protected String buildQueryParameters(String uri, Map<String, Object> parameters, boolean encode) {

        // we want sorted parameters
        Map<String, Object> map = new TreeMap<>(parameters);

        // automatic use RAW(value) for secret options
        for (String secretParameter : secretPropertyNames()) {
            Object val = map.get(secretParameter);
            if (val instanceof String answer) {
                if (!answer.startsWith("#") && !answer.startsWith("RAW(")) {
                    map.put(secretParameter, "RAW(" + val + ")");
                }
            }
        }

        // flatten all multiValue=true maps into parameters with prefix
        for (var multi : multiValuePrefixes().entrySet()) {
            Object val = map.get(multi.getKey());
            String prefix = multi.getValue();
            if (val instanceof Map<?, ?> m) {
                for (var k : m.keySet()) {
                    // each entry in map becomes a new option with the prefix key
                    String key = prefix + k;
                    val = m.get(k);
                    if (val != null) {
                        map.put(key, val);
                    }
                }
                map.remove(multi.getKey());
            }
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
