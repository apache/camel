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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointConfiguration;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.util.IntrospectionSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements {@link EndpointConfiguration} for Endpoint implementations
 * which are annotated with {@link org.apache.camel.spi.UriEndpoint} to use the {@link UriParam} and {@link UriParams} annotations
 * to denote its parameters which can be specified via URI query parameters.
 */
@Deprecated
public class UriEndpointConfiguration implements EndpointConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(UriEndpointConfiguration.class);

    private final CamelContext camelContext;
    private final Endpoint endpoint;
    private String uriText;
    private URI uri;
    private SortedMap<String, ParameterConfiguration> propertyMap;

    public UriEndpointConfiguration(CamelContext camelContext, Endpoint endpoint, String uriText) {
        this.camelContext = camelContext;
        this.endpoint = endpoint;
        this.uriText = uriText;
    }

    @Override
    public URI getURI() {
        if (uri == null) {
            // lazily create the URI which may fail as not all camel uriText are valid URI text
            try {
                uri = new URI(uriText);
            } catch (URISyntaxException e) {
                throw new RuntimeCamelException(e);
            }
        }
        return uri;
    }

    public void setURI(URI uri) {
        this.uriText = null;
        this.uri = uri;
    }

    @Override
    public <T> T getParameter(String name) throws RuntimeCamelException {
        ParameterConfiguration config = getPropertyConfiguration(name);

        // lets try get the property regardless of if this maps to a valid property name
        // then if the introspection fails we will get a valid error otherwise
        // lets raise a warning afterwards that we should update the metadata on the endpoint class
        try {
            @SuppressWarnings("unchecked")
            T answer = (T)IntrospectionSupport.getProperty(endpoint, name);
            if (config == null) {
                warnMissingUriParamOnProperty(name);
            }
            return answer;
        } catch (Exception e) {
            throw new RuntimeCamelException(
                    "Failed to get property '" + name + "' on " + endpoint + " due " + e.getMessage(), e);
        }
    }

    protected void warnMissingUriParamOnProperty(String name) {
        LOG.warn("Using property " + name + " on endpoint " + getEndpointClass().getName()
                + " which does not have a @UriParam annotation! "
                + "Please add the @UriParam annotation to the " + name + " field");
    }

    @Override
    public <T> void setParameter(String name, T value) throws RuntimeCamelException {
        ParameterConfiguration config = getPropertyConfiguration(name);

        // lets try set the property regardless of if this maps to a valid property name
        // then if the injection fails we will get a valid error otherwise
        // lets raise a warning afterwards that we should update the metadata on the endpoint class
        try {
            IntrospectionSupport.setProperty(endpoint, name, value);
        } catch (Exception e) {
            throw new RuntimeCamelException(
                    "Failed to set property '" + name + "' on " + endpoint + " to value " + value + " due "
                            + e.getMessage(), e);
        }
        if (config == null) {
            warnMissingUriParamOnProperty(name);
        }
    }

    @Override
    public String toUriString(UriFormat format) {
        // TODO
        return null;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public Class<? extends Endpoint> getEndpointClass() {
        return endpoint.getClass();
    }

    /**
     * Returns the property configuration for the given property name or null if it does not exist
     */
    public ParameterConfiguration getPropertyConfiguration(String name) {
        return getPropertyConfigurationMap().get(name);
    }

    /**
     * Returns the sorted map of all the property names to their {@link ParameterConfiguration} objects
     */
    public SortedMap<String, ParameterConfiguration> getPropertyConfigurationMap() {
        if (propertyMap == null) {
            propertyMap = UriEndpointComponent.createParameterConfigurationMap(getEndpointClass());
        }
        return new TreeMap<String, ParameterConfiguration>(propertyMap);
    }

}
