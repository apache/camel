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

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.ExtendedPropertyConfigurerGetter;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.PropertyBindingSupport;

/**
 * Abstract base class for API Component Camel {@link org.apache.camel.Component} classes.
 */
public abstract class AbstractApiComponent<E extends Enum<E> & ApiName, T, S extends ApiCollection<E, T>>
        extends DefaultComponent {

    @Metadata(label = "advanced", description = "Component configuration")
    protected T configuration;

    // API collection
    protected final S collection;

    // API name class
    protected final Class<E> apiNameClass;

    /**
     * Deprecated constructor for AbstractApiComponent.
     *
     * @deprecated               Use {@link AbstractApiComponent#AbstractApiComponent(Class, ApiCollection)}
     * @param      endpointClass This is deprecated. Do not use
     * @param      apiNameClass  The API name class
     * @param      collection    The collection of API methods
     */
    @Deprecated
    public AbstractApiComponent(Class<? extends Endpoint> endpointClass, Class<E> apiNameClass, S collection) {
        this(apiNameClass, collection);
    }

    /**
     * Deprecated constructor for AbstractApiComponent.
     *
     * @deprecated               Use
     *                           {@link AbstractApiComponent#AbstractApiComponent(CamelContext, Class, ApiCollection)}
     *                           instead
     * @param      context       The CamelContext
     * @param      endpointClass This is deprecated. Do not use
     * @param      apiNameClass  The API name class
     * @param      collection    The collection of API methods
     */
    @Deprecated
    public AbstractApiComponent(CamelContext context, Class<? extends Endpoint> endpointClass, Class<E> apiNameClass,
                                S collection) {
        this(context, apiNameClass, collection);
    }

    /**
     * Creates a new AbstractApiComponent
     *
     * @param apiNameClass The API name class
     * @param collection   The collection of API methods
     */
    protected AbstractApiComponent(Class<E> apiNameClass, S collection) {
        this.collection = collection;
        this.apiNameClass = apiNameClass;
    }

    /**
     * Creates a new AbstractApiComponent
     *
     * @param context      The CamelContext
     * @param apiNameClass The API name class
     * @param collection   The collection of API methods
     */
    protected AbstractApiComponent(CamelContext context, Class<E> apiNameClass, S collection) {
        super(context);
        this.collection = collection;
        this.apiNameClass = apiNameClass;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // split remaining path to get API name and method
        final String[] pathElements = remaining.split("/");
        String apiNameStr;
        String methodName;
        switch (pathElements.length) {
            case 1:
                apiNameStr = "";
                methodName = pathElements[0];
                break;
            case 2:
                apiNameStr = pathElements[0];
                methodName = pathElements[1];
                break;
            default:
                throw new CamelException(
                        "Invalid URI path [" + remaining
                                         + "], must be of the format " + collection.getApiNames() + "/<operation-name>");
        }

        try {
            // get API enum from apiName string
            final E apiName = getApiName(apiNameStr);

            final T endpointConfiguration = createEndpointConfiguration(apiName);
            final Endpoint endpoint = createEndpoint(uri, methodName, apiName, endpointConfiguration);

            // configure endpoint properties and initialize state
            setProperties(endpoint, parameters);

            afterPropertiesSet(endpointConfiguration);

            return endpoint;
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IllegalArgumentException) {
                throw new CamelException(
                        "Invalid URI path prefix [" + remaining
                                         + "], must be one of " + collection.getApiNames());
            }
            throw e;
        }
    }

    protected void afterPropertiesSet(T endpointConfiguration) {
        // NO-OP
    }

    protected abstract E getApiName(String apiNameStr);

    protected abstract Endpoint createEndpoint(String uri, String methodName, E apiName, T endpointConfiguration);

    protected T createEndpointConfiguration(E name) throws Exception {
        final Map<String, Object> componentProperties = new HashMap<>();
        // copy component configuration, if set
        if (configuration != null) {
            PropertyConfigurer configurer = PluginHelper.getConfigurerResolver(getCamelContext())
                    .resolvePropertyConfigurer(configuration.getClass().getName(), getCamelContext());
            // use reflection free configurer (if possible)
            if (configurer instanceof ExtendedPropertyConfigurerGetter) {
                ExtendedPropertyConfigurerGetter getter = (ExtendedPropertyConfigurerGetter) configurer;
                for (String key : getter.getAllOptions(configuration).keySet()) {
                    Object value = getter.getOptionValue(configuration, key, true);
                    if (value != null) {
                        componentProperties.put(key, value);
                    }
                }
            } else {
                PluginHelper.getBeanIntrospection(getCamelContext()).getProperties(configuration,
                        componentProperties, null, false);
            }
        }

        // create endpoint configuration with component properties
        final T endpointConfiguration = collection.getEndpointConfiguration(name);
        PropertyConfigurer configurer = PluginHelper.getConfigurerResolver(getCamelContext())
                .resolvePropertyConfigurer(endpointConfiguration.getClass().getName(), getCamelContext());
        PropertyBindingSupport.build()
                .withConfigurer(configurer)
                .withIgnoreCase(true)
                .bind(getCamelContext(), endpointConfiguration, componentProperties);
        return endpointConfiguration;
    }

    public T getConfiguration() {
        return configuration;
    }

    public void setConfiguration(T configuration) {
        this.configuration = configuration;
    }

}
