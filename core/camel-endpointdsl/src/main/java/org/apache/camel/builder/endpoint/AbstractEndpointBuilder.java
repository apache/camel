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

import javax.xml.bind.annotation.XmlTransient;

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

@XmlTransient
public class AbstractEndpointBuilder {

    protected final String scheme;
    protected final String path;
    protected final Map<String, Object> properties = new LinkedHashMap<>();

    public AbstractEndpointBuilder(String scheme, String path) {
        this.scheme = scheme;
        this.path = path;
    }

    public Endpoint resolve(CamelContext context) throws NoSuchEndpointException {
        Map<String, Object> remaining = new HashMap<>();
        // we should not bind complex objects to registry as we create the endpoint via the properties as-is
        NormalizedEndpointUri uri = computeUri(remaining, context, false);
        ExtendedCamelContext ecc = (ExtendedCamelContext) context;
        Endpoint endpoint = ecc.getEndpoint(uri, properties);
        if (endpoint == null) {
            throw new NoSuchEndpointException(uri.getUri());
        }
        return endpoint;
    }

    public String getUri() {
        return computeUri(new HashMap<>(), null, false).getUri();
    }

    protected NormalizedUri computeUri(Map<String, Object> remaining, CamelContext camelContext, boolean bindToRegistry) {
        NormalizedUri answer;

        // sort parameters so it can be regarded as normalized
        Map<String, Object> params = new TreeMap<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (val instanceof String || val instanceof Number || val instanceof Boolean || val instanceof Enum<?>) {
                params.put(key, val.toString());
            } else if (camelContext != null && bindToRegistry) {
                String hash = Integer.toHexString(val.hashCode());
                params.put(key, "#" + hash);
                camelContext.getRegistry().bind(hash, val);
            } else {
                remaining.put(key, val);
            }
        }
        if (!remaining.isEmpty()) {
            params.put("hash", Integer.toHexString(remaining.hashCode()));
        }
        if (params.isEmpty()) {
            answer = new NormalizedUri(scheme + ":" + path);
        } else {
            try {
                String query = URISupport.createQueryString(params);
                answer = new NormalizedUri(scheme + ":" + path + "?" + query);
            } catch (URISyntaxException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        return answer;
    }

    @Override
    public String toString() {
        return getUri();
    }

    public void doSetProperty(String key, Object value) {
        this.properties.put(key, value);
    }

    public Expression expr() {
        return SimpleBuilder.simple(getUri());
    }

    public Expression expr(CamelContext camelContext) {
        // need to bind complex properties so we can return an uri that includes these parameters too
        NormalizedEndpointUri uri = computeUri(new HashMap<>(), camelContext, true);
        return SimpleBuilder.simple(uri.getUri());
    }

}
