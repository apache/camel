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
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.catalog.RuntimeCamelCatalog;
import org.apache.camel.spi.EndpointUriFactory;
import org.apache.camel.spi.SendDynamicAware;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.URISupport;

/**
 * Support class for {@link SendDynamicAware} implementations.
 */
public abstract class SendDynamicAwareSupport extends ServiceSupport implements SendDynamicAware {

    private CamelContext camelContext;
    private Set<String> knownProperties;
    private RuntimeCamelCatalog catalog;
    private String scheme;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    @Override
    public String getScheme() {
        return scheme;
    }

    @Override
    protected void doInit() throws Exception {
        if (isOnlyDynamicQueryParameters()) {
            // optimize to eager load the list of known properties
            EndpointUriFactory factory = getCamelContext().adapt(ExtendedCamelContext.class).getEndpointUriFactory(getScheme());
            if (factory == null) {
                throw new IllegalStateException("Cannot find EndpointUriFactory for component: " + getScheme());
            }
            knownProperties = factory.propertyNames();
        } else {
            catalog = getCamelContext().adapt(ExtendedCamelContext.class).getRuntimeCamelCatalog();
        }
    }

    public Map<String, Object> endpointProperties(Exchange exchange, String uri) throws Exception {
        Map<String, Object> properties;
        if (isOnlyDynamicQueryParameters()) {
            // optimize as we know its only query parameters that can be dynamic
            Map<String, Object> map = URISupport.parseQuery(URISupport.extractQuery(uri));
            if (map != null && !map.isEmpty() && isLenientProperties()) {
                // okay so only add the known properties as they are the non lenient properties
                properties = new LinkedHashMap<>();
                map.forEach((k, v) -> {
                    if (knownProperties.contains(k)) {
                        properties.put(k, v);
                    }
                });
            } else {
                properties = map;
            }
        } else {
            properties = new LinkedHashMap<>(catalog.endpointProperties(uri));
        }
        return properties;
    }

    public Map<String, Object> endpointLenientProperties(Exchange exchange, String uri) throws Exception {
        Map<String, Object> properties;
        if (isOnlyDynamicQueryParameters()) {
            // optimize as we know its only query parameters that can be dynamic
            Map<String, Object> map = URISupport.parseQuery(URISupport.extractQuery(uri));
            if (map != null && !map.isEmpty()) {
                properties = new LinkedHashMap<>();
                map.forEach((k, v) -> {
                    if (!knownProperties.contains(k)) {
                        properties.put(k, v.toString());
                    }
                });
            } else {
                properties = map;
            }
        } else {
            properties = new LinkedHashMap<>(catalog.endpointLenientProperties(uri));
        }
        return properties;
    }

    public String asEndpointUri(Exchange exchange, String uri, Map<String, Object> properties) throws Exception {
        if (isOnlyDynamicQueryParameters()) {
            String answer;
            String query = URISupport.createQueryString(properties, false);
            int pos = uri.indexOf('?');
            if (pos != -1) {
                answer = uri.substring(0, pos) + "?" + query;
            } else {
                answer = uri + "?" + query;
            }
            return answer;
        } else {
            return catalog.asEndpointUri(getScheme(), new LinkedHashMap(properties), false);
        }
    }
}
