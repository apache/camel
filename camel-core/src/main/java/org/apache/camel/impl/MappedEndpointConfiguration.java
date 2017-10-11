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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.EndpointConfiguration;
import org.apache.camel.TypeConverter;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

/**
 * Fallback implementation of {@link EndpointConfiguration} used by {@link Component}s
 * that did not yet define a configuration type.
 */
@Deprecated
public final class MappedEndpointConfiguration extends DefaultEndpointConfiguration {
    // TODO: need 2 sets to differentiate between user keys and fixed keys
    private Map<String, Object> params = new LinkedHashMap<String, Object>();

    MappedEndpointConfiguration(CamelContext camelContext) {
        super(camelContext);
    }

    MappedEndpointConfiguration(CamelContext camelContext, String uri) {
        super(camelContext);
        setURI(uri);
    }

    @SuppressWarnings("unchecked")
    public <T> T getParameter(String name) {
        return (T) params.get(name);
    }

    @Override
    public <T> void setParameter(String name, T value) {
        params.put(name, value);
    }
    
    @Override
    public boolean equals(Object other) {
        if (other == null || !(other instanceof MappedEndpointConfiguration)) {
            return false;
        }
        // if all parameters including scheme are the same, the component and uri must be the same too
        return this == other || (this.getClass() == other.getClass() && params.equals(((MappedEndpointConfiguration)other).params));
    }

    @Override
    public int hashCode() {
        return params.hashCode();
    }

    @Override
    protected void parseURI() {
        ConfigurationHelper.populateFromURI(getCamelContext(), this, new ConfigurationHelper.ParameterSetter() {
            @Override
            public <T> void set(CamelContext camelContext, EndpointConfiguration config, String name, T value) {
                if (name != null && value != null) {
                    params.put(name, value);
                }
            }
        });
    }

    @Override
    public String toUriString(UriFormat format) {
        Set<Map.Entry<String, Object>> entries = params.entrySet();
        List<String> queryParams = new ArrayList<String>();
        
        String scheme = null;
        String schemeSpecificPart = null;
        String authority = null;
        String path = null;
        String fragment = null;

        TypeConverter converter = getCamelContext().getTypeConverter();

        // Separate URI values from query parameters
        for (Map.Entry<String, Object> entry : entries) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key.equals(EndpointConfiguration.URI_SCHEME)) {
                scheme = converter.convertTo(String.class, value);
            } else if (key.equals(EndpointConfiguration.URI_SCHEME_SPECIFIC_PART)) {
                schemeSpecificPart = converter.convertTo(String.class, value);
            } else if (key.equals(EndpointConfiguration.URI_AUTHORITY)) {
                authority = converter.convertTo(String.class, value);
            } else if (key.equals(EndpointConfiguration.URI_USER_INFO)) {
                // ignore, part of authority
            } else if (key.equals(EndpointConfiguration.URI_HOST)) {
                // ignore, part of authority
            } else if (key.equals(EndpointConfiguration.URI_PORT)) {
                // ignore, part of authority
            } else if (key.equals(EndpointConfiguration.URI_PATH)) {
                path = converter.convertTo(String.class, value);
            } else if (key.equals(EndpointConfiguration.URI_QUERY)) {
                // ignore, but this should not be the case, may be a good idea to log...
            } else if (key.equals(EndpointConfiguration.URI_FRAGMENT)) {
                fragment = converter.convertTo(String.class, value);
            } else {
                // convert to "param=value" format here, order will be preserved
                if (value instanceof List) {
                    for (Object item : (List<?>)value) {
                        queryParams.add(key + "=" + UnsafeUriCharactersEncoder.encode(item.toString()));
                    }
                } else {
                    queryParams.add(key + "=" + UnsafeUriCharactersEncoder.encode(value.toString()));
                }
            }
        }

        queryParams.sort(null);
        StringBuilder q = new StringBuilder();
        for (String entry : queryParams) {
            q.append(q.length() == 0 ? "" : "&");
            q.append(entry);
        }

        StringBuilder u = new StringBuilder(64);
        if (scheme != null) {
            u.append(scheme); // SHOULD NOT be null
            u.append(":");
        }
        if (authority != null) {
            u.append("//");
            u.append(authority);
            u.append(path);
            if (q.length() > 0) {
                u.append("?");
                u.append(q);
            }
            if (fragment != null) {
                u.append("#");
                u.append(fragment);
            }
        } else {
            // add leading // if not provided
            if (!schemeSpecificPart.startsWith("//")) {
                u.append("//");
            }
            u.append(schemeSpecificPart);
        }
        return u.toString();
    }
}
