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

import java.lang.reflect.Field;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.camel.CamelContext;
import org.apache.camel.ComponentConfiguration;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ReflectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A component implementation for endpoints which are annotated with UriEndpoint to describe
 * their configurable parameters via annotations
 *
 * @deprecated use {@link DefaultComponent}
 */
@Deprecated
public abstract class UriEndpointComponent extends DefaultComponent {
    private static final Logger LOG = LoggerFactory.getLogger(UriEndpointComponent.class);

    private Class<? extends Endpoint> endpointClass;
    private SortedMap<String, ParameterConfiguration> parameterConfigurationMap;

    public UriEndpointComponent(Class<? extends Endpoint> endpointClass) {
        this.endpointClass = endpointClass;
    }

    public UriEndpointComponent(CamelContext context, Class<? extends Endpoint> endpointClass) {
        super(context);
        this.endpointClass = endpointClass;
    }

    /**
     * To use a specific endpoint class, instead of what has been provided by the constructors.
     *
     * @param endpointClass the endpoint class to use
     */
    public void setEndpointClass(Class<? extends Endpoint> endpointClass) {
        this.endpointClass = endpointClass;
    }

    @Override
    public ComponentConfiguration createComponentConfiguration() {
        return new UriComponentConfiguration(this);
    }

    /**
     * Returns a newly created sorted map, indexed by name of all the parameter configurations
     * of the given endpoint class using introspection for the various annotations like
     * {@link org.apache.camel.spi.UriEndpoint}, {@link org.apache.camel.spi.UriParam}, {@link org.apache.camel.spi.UriParams}
     */
    public static SortedMap<String, ParameterConfiguration> createParameterConfigurationMap(
            Class<? extends Endpoint> endpointClass) {
        SortedMap<String, ParameterConfiguration> answer = new TreeMap<String, ParameterConfiguration>();
        populateParameterConfigurationMap(answer, endpointClass, "");
        return answer;
    }

    protected static void populateParameterConfigurationMap(
            final SortedMap<String, ParameterConfiguration> parameterMap, Class<?> aClass,
            final String prefix) {
        ReflectionHelper.doWithFields(aClass, new ReflectionHelper.FieldCallback() {
            @Override
            public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
                UriParam uriParam = field.getAnnotation(UriParam.class);
                if (uriParam != null) {
                    String name = uriParam.name();
                    if (ObjectHelper.isEmpty(name)) {
                        name = field.getName();
                    }
                    String propertyName = prefix + name;

                    // is the parameter a nested configuration object
                    Class<?> fieldType = field.getType();
                    UriParams uriParams = fieldType.getAnnotation(UriParams.class);
                    if (uriParams != null) {
                        String nestedPrefix = uriParams.prefix();
                        if (nestedPrefix == null) {
                            nestedPrefix = "";
                        }
                        nestedPrefix = (prefix + nestedPrefix).trim();
                        populateParameterConfigurationMap(parameterMap, fieldType, nestedPrefix);
                    } else {
                        if (parameterMap.containsKey(propertyName)) {
                            LOG.warn("Duplicate property name {} defined on field {}", propertyName, field);
                        } else {
                            parameterMap.put(propertyName,
                                    ParameterConfiguration.newInstance(propertyName, field, uriParam));
                        }
                    }
                }
            }
        });
    }

    public Class<? extends Endpoint> getEndpointClass() {
        return endpointClass;
    }

    /**
     * Returns the sorted map of all the URI query parameter names to their {@link ParameterConfiguration} objects
     */
    public SortedMap<String, ParameterConfiguration> getParameterConfigurationMap() {
        if (parameterConfigurationMap == null) {
            parameterConfigurationMap = createParameterConfigurationMap(getEndpointClass());
        }
        return new TreeMap<String, ParameterConfiguration>(parameterConfigurationMap);
    }

}
