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
package org.apache.camel.builder.endpoint;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Expression;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.SimpleBuilder;
import org.apache.camel.spi.NormalizedEndpointUri;
import org.apache.camel.support.NormalizedUri;
import org.apache.camel.util.URISupport;

public class AbstractEndpointBuilder {

    protected final String scheme;
    protected final String path;
    protected final Map<String, Object> properties = new LinkedHashMap<>();
    protected final Map<String, Map<String, Object>> multivalues = new HashMap<>();
    private volatile Endpoint resolvedEndpoint;

    public AbstractEndpointBuilder(String scheme, String path) {
        this.scheme = scheme;
        this.path = path;
    }

    public Endpoint resolve(CamelContext context) throws NoSuchEndpointException {
        if (resolvedEndpoint != null) {
            return resolvedEndpoint;
        }

        Map<String, Object> remaining = new LinkedHashMap<>();
        // we should not bind complex objects to registry as we create the endpoint via the properties as-is
        NormalizedEndpointUri uri = computeUri(remaining, context, false, true);
        ExtendedCamelContext ecc = (ExtendedCamelContext) context;
        Endpoint endpoint = ecc.getEndpoint(uri, properties);
        if (endpoint == null) {
            throw new NoSuchEndpointException(uri.getUri());
        }

        resolvedEndpoint = endpoint;
        return endpoint;
    }

    public <T extends Endpoint> T resolve(CamelContext context, Class<T> endpointType) throws NoSuchEndpointException {
        Endpoint answer = resolve(context);
        return endpointType.cast(answer);
    }

    public String getUri() {
        return computeUri(new LinkedHashMap<>(), null, false, true).getUri();
    }

    protected NormalizedUri computeUri(
            Map<String, Object> remaining, CamelContext camelContext, boolean bindToRegistry, boolean encode) {
        NormalizedUri answer;

        // sort parameters so it can be regarded as normalized
        Map<String, Object> params = new TreeMap<>();
        // compute from properties and multivalues
        computeProperties(remaining, camelContext, bindToRegistry, params, properties);
        for (Map<String, Object> map : multivalues.values()) {
            computeProperties(remaining, camelContext, bindToRegistry, params, map);
        }
        if (!remaining.isEmpty()) {
            params.put("hash", Integer.toHexString(remaining.hashCode()));
        }

        // ensure property placeholders is also resolved on scheme and path
        String targetScheme = scheme;
        String targetPath = path;
        if (camelContext != null) {
            targetScheme = camelContext.resolvePropertyPlaceholders(targetScheme);
            targetPath = camelContext.resolvePropertyPlaceholders(targetPath);
        }

        if (params.isEmpty()) {
            answer = new NormalizedUri(targetScheme + "://" + targetPath);
        } else {
            try {
                // build query string from parameters
                String query = URISupport.createQueryString(params, encode);
                if (targetPath.contains("?")) {
                    answer = new NormalizedUri(targetScheme + "://" + targetPath + "&" + query);
                } else {
                    answer = new NormalizedUri(targetScheme + "://" + targetPath + "?" + query);
                }
            } catch (URISyntaxException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        return answer;
    }

    private static void computeProperties(
            Map<String, Object> remaining, CamelContext camelContext, boolean bindToRegistry,
            Map<String, Object> params, Map<String, Object> properties) {
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (val instanceof String) {
                String text = val.toString();
                if (camelContext != null) {
                    text = camelContext.resolvePropertyPlaceholders(text);
                }
                params.put(key, text);
            } else if (val instanceof Number || val instanceof Boolean || val instanceof Enum<?>) {
                params.put(key, val.toString());
            } else if (camelContext != null && bindToRegistry) {
                String hash = Integer.toHexString(val.hashCode());
                params.put(key, "#" + hash);
                camelContext.getRegistry().bind(hash, val);
            } else {
                remaining.put(key, val);
            }
        }
    }

    @Override
    public String toString() {
        return getUri();
    }

    public void doSetProperty(String key, Object value) {
        this.properties.put(key, value);
    }

    public void doSetMultiValueProperty(String name, String key, Object value) {
        Map<String, Object> map = multivalues.computeIfAbsent(name, k -> new LinkedHashMap<>());
        map.put(key, value);
    }

    public void doSetMultiValueProperties(String name, String prefix, Map<String, Object> values) {
        values.forEach((k, v) -> {
            doSetMultiValueProperty(name, prefix + k, v);
        });
    }

    public Expression expr() {
        return SimpleBuilder.simple(getUri());
    }

    public Expression expr(CamelContext camelContext) {
        // need to bind complex properties so we can return an uri that includes these parameters too
        // do not encode computed uri as we want to preserve simple expressions, as this is used
        // by ToDynamic which builds the uri string un-encoded for simple language parser to be able to parse
        NormalizedEndpointUri uri = computeUri(new LinkedHashMap<>(), camelContext, true, false);
        return SimpleBuilder.simple(uri.getUri());
    }

}
