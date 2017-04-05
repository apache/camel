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

import java.util.Collections;
import java.util.SortedMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.InvalidPropertyException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.IntrospectionSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements {@link org.apache.camel.EndpointConfiguration} for Endpoint implementations
 * which are annotated with {@link org.apache.camel.spi.UriEndpoint}
 * to use the {@link org.apache.camel.spi.UriParam} and {@link org.apache.camel.spi.UriParams} annotations
 * to denote its parameters which can be specified via URI query parameters.
 */
@Deprecated
public class UriComponentConfiguration extends ComponentConfigurationSupport {
    private static final Logger LOG = LoggerFactory.getLogger(UriComponentConfiguration.class);

    private final Class<? extends Endpoint> endpointClass;
    private final SortedMap<String, ParameterConfiguration> parameterConfigurationMap;
    private boolean strictOnParameterNames = true;

    public UriComponentConfiguration(Component component, Class<? extends Endpoint> endpointClass,
                                     SortedMap<String, ParameterConfiguration> parameterConfigurationMap) {
        super(component);
        this.endpointClass = endpointClass;
        this.parameterConfigurationMap = Collections.unmodifiableSortedMap(parameterConfigurationMap);
    }

    public UriComponentConfiguration(Component component, Class<? extends Endpoint> endpointClass) {
        this(component, endpointClass, UriEndpointComponent.createParameterConfigurationMap(endpointClass));
    }

    public UriComponentConfiguration(UriEndpointComponent component) {
        this(component, component.getEndpointClass(), component.getParameterConfigurationMap());
    }

    @Override
    public Object getEndpointParameter(Endpoint endpoint, String name) throws RuntimeCamelException {
        ParameterConfiguration config = getParameterConfiguration(name);

        // lets try get the property regardless of if this maps to a valid property name
        // then if the introspection fails we will get a valid error otherwise
        // lets raise a warning afterwards that we should update the metadata on the endpoint class
        Object answer = null;
        try {
            answer = IntrospectionSupport.getProperty(endpoint, name);
        } catch (Exception e) {
            throw new RuntimeCamelException(
                    "Failed to get property '" + name + "' on " + endpoint + " due " + e.getMessage(), e);
        }
        if (config == null) {
            unknownPropertyName(name);
        }
        return answer;
    }

    @Override
    public void setEndpointParameter(Endpoint endpoint, String name, Object value) throws RuntimeCamelException {
        ParameterConfiguration config = getParameterConfiguration(name);

        // lets try set the property regardless of if this maps to a valid property name
        // then if the injection fails we will get a valid error otherwise
        // lets raise a warning afterwards that we should update the metadata on the endpoint class
        try {
            IntrospectionSupport.setProperty(endpoint, name, value);
        } catch (Exception e) {
            throw new RuntimeCamelException("Failed to set property '" + name + "' on " + endpoint + " to value "
                    + value + " due " + e.getMessage(), e);
        }
        if (config == null) {
            unknownPropertyName(name);
        }
    }

    public CamelContext getCamelContext() {
        return component.getCamelContext();
    }

    public Class<? extends Endpoint> getEndpointClass() {
        return endpointClass;
    }

    public boolean isStrictOnParameterNames() {
        return strictOnParameterNames;
    }

    /**
     * Strict mode is enabled by default but if disabled then invalid parameter names
     * will not result in exceptions but we will just log warnings about their use
     *
     * @param strictOnParameterNames whether to throw exceptions if invalid
     *                               parameter names are used or not
     */
    public void setStrictOnParameterNames(boolean strictOnParameterNames) {
        this.strictOnParameterNames = strictOnParameterNames;
    }

    @Override
    public SortedMap<String, ParameterConfiguration> getParameterConfigurationMap() {
        return parameterConfigurationMap;
    }

    @Override
    protected void validatePropertyName(String name) {
        ParameterConfiguration parameterConfiguration = getParameterConfiguration(name);
        if (parameterConfiguration == null) {
            unknownPropertyName(name);
        }
    }

    @Override
    protected Object validatePropertyValue(String name, Object value) {
        ParameterConfiguration parameterConfiguration = getParameterConfiguration(name);
        if (parameterConfiguration == null) {
            unknownPropertyName(name);
            return value;
        } else {
            Class<?> parameterType = parameterConfiguration.getParameterType();
            return getCamelContext().getTypeConverter().convertTo(parameterType, value);
        }
    }

    protected void unknownPropertyName(String name) {
        if (isStrictOnParameterNames()) {
            throw new InvalidPropertyException(this, name, endpointClass);
        } else {
            LOG.warn("Using parameter " + name + " on endpoint " + getEndpointClass().getName()
                    + " which does not have a @UriParam annotation! "
                    + "Please add the @UriParam annotation to the " + name + " field");
        }
    }

}
