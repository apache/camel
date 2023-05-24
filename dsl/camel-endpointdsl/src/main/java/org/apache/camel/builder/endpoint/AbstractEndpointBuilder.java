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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Expression;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.NormalizedEndpointUri;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.NormalizedUri;
import org.apache.camel.util.URISupport;

public class AbstractEndpointBuilder {

    protected final String scheme;
    protected final String path;
    protected final Map<String, Object> properties = new LinkedHashMap<>();
    protected final Map<String, Map<String, Object>> multiValues = new HashMap<>();
    private volatile Map<String, Object> originalProperties;
    private volatile Language simple;

    public AbstractEndpointBuilder(String scheme, String path) {
        this.scheme = scheme;
        this.path = path;
    }

    public Endpoint resolve(CamelContext context) throws NoSuchEndpointException {
        // properties may contain property placeholder which should be resolved
        if (originalProperties == null) {
            originalProperties = new LinkedHashMap<>(properties);
        } else {
            // reload from original properties before resolving placeholder in case its updated
            properties.putAll(originalProperties);
        }
        resolvePropertyPlaceholders(context, properties);

        Map<String, Object> remaining = new LinkedHashMap<>();
        // we should not bind complex objects to registry as we create the endpoint via the properties as-is
        NormalizedEndpointUri uri = computeUri(remaining, context, false, true);
        Endpoint endpoint = context.getCamelContextExtension().getEndpoint(uri, properties);
        if (endpoint == null) {
            throw new NoSuchEndpointException(uri.getUri());
        }

        return endpoint;
    }

    private static void resolvePropertyPlaceholders(CamelContext context, Map<String, Object> properties) {
        Set<String> toRemove = new HashSet<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                String text = (String) value;
                String changed = context.getCamelContextExtension().resolvePropertyPlaceholders(text, true);
                if (changed.startsWith(PropertiesComponent.PREFIX_OPTIONAL_TOKEN)) {
                    // unresolved then remove it
                    toRemove.add(entry.getKey());
                } else if (!changed.equals(text)) {
                    entry.setValue(changed);
                }
            }
        }
        if (!toRemove.isEmpty()) {
            for (String key : toRemove) {
                properties.remove(key);
            }
        }
    }

    public <T extends Endpoint> T resolve(CamelContext context, Class<T> endpointType) throws NoSuchEndpointException {
        Endpoint answer = resolve(context);
        return endpointType.cast(answer);
    }

    public String getUri() {
        return computeUri(new LinkedHashMap<>(), null, false, true).getUri();
    }

    public String getRawUri() {
        return computeUri(new LinkedHashMap<>(), null, false, false).getUri();
    }

    protected NormalizedUri computeUri(
            Map<String, Object> remaining, CamelContext camelContext, boolean bindToRegistry, boolean encode) {
        NormalizedUri answer;

        // sort parameters so it can be regarded as normalized
        Map<String, Object> params = new TreeMap<>();
        // compute from properties and multi values
        computeProperties(remaining, camelContext, bindToRegistry, params, properties);
        for (Map<String, Object> map : multiValues.values()) {
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
            targetPath = EndpointHelper.resolveEndpointUriPropertyPlaceholders(camelContext, targetPath);
        }

        if (params.isEmpty()) {
            answer = NormalizedUri.newNormalizedUri(targetScheme + "://" + targetPath, true);
        } else {
            // build query string from parameters
            String query = URISupport.createQueryString(params, encode);
            if (targetPath.contains("?")) {
                answer = NormalizedUri.newNormalizedUri(targetScheme + "://" + targetPath + "&" + query, true);
            } else {
                answer = NormalizedUri.newNormalizedUri(targetScheme + "://" + targetPath + "?" + query, true);
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
                params.put(key, val);
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
        Map<String, Object> map = multiValues.computeIfAbsent(name, k -> new LinkedHashMap<>());
        map.put(key, value);
    }

    public void doSetMultiValueProperties(String name, String prefix, Map<String, Object> values) {
        values.forEach((k, v) -> {
            doSetMultiValueProperty(name, prefix + k, v);
        });
    }

    public Expression expr(CamelContext camelContext) {
        // need to bind complex properties so we can return an uri that includes these parameters too
        // do not encode computed uri as we want to preserve simple expressions, as this is used
        // by ToDynamic which builds the uri string un-encoded for simple language parser to be able to parse
        NormalizedEndpointUri uri = computeUri(new LinkedHashMap<>(), camelContext, true, false);
        if (simple == null) {
            simple = camelContext.resolveLanguage("simple");
        }
        return simple.createExpression(uri.getUri());
    }

}
