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
package org.apache.camel.util.component;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.ComponentConfiguration;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.EndpointCompleter;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * Abstract base class for API Component Camel {@link org.apache.camel.Component} classes.
 */
public abstract class AbstractApiComponent<E extends Enum<E> & ApiName, T, S extends ApiCollection<E, T>>
        extends UriEndpointComponent implements EndpointCompleter {

    @Metadata(label = "advanced")
    protected T configuration;

    // API collection
    protected final S collection;

    // API name class
    protected final Class<E> apiNameClass;

    public AbstractApiComponent(Class<? extends Endpoint> endpointClass,
                                Class<E> apiNameClass, S collection) {
        super(endpointClass);
        this.collection = collection;
        this.apiNameClass = apiNameClass;
    }

    public AbstractApiComponent(CamelContext context, Class<? extends Endpoint> endpointClass,
                                Class<E> apiNameClass, S collection) {
        super(context, endpointClass);
        this.collection = collection;
        this.apiNameClass = apiNameClass;
    }

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
            throw new CamelException("Invalid URI path [" + remaining
                + "], must be of the format " + collection.getApiNames() + "/<operation-name>");
        }

        try {
            // get API enum from apiName string
            final E apiName = getApiName(apiNameStr);

            final T endpointConfiguration = createEndpointConfiguration(apiName);
            final Endpoint endpoint = createEndpoint(uri, methodName, apiName, endpointConfiguration);

            // set endpoint property inBody
            setProperties(endpoint, parameters);

            // configure endpoint properties and initialize state
            endpoint.configureProperties(parameters);

            return endpoint;
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IllegalArgumentException) {
                throw new CamelException("Invalid URI path prefix [" + remaining
                    + "], must be one of " + collection.getApiNames());
            }
            throw e;
        }
    }

    protected abstract E getApiName(String apiNameStr) throws IllegalArgumentException;

    protected abstract Endpoint createEndpoint(String uri, String methodName, E apiName, T endpointConfiguration);

    protected T createEndpointConfiguration(E name) throws Exception {
        final Map<String, Object> componentProperties = new HashMap<String, Object>();
        // copy component configuration, if set
        if (configuration != null) {
            IntrospectionSupport.getProperties(configuration, componentProperties, null, false);
        }

        // create endpoint configuration with component properties
        final T endpointConfiguration = collection.getEndpointConfiguration(name);
        IntrospectionSupport.setProperties(endpointConfiguration, componentProperties);
        return endpointConfiguration;
    }

    public T getConfiguration() {
        return configuration;
    }

    public void setConfiguration(T configuration) {
        this.configuration = configuration;
    }

    @Override
    public List<String> completeEndpointPath(ComponentConfiguration configuration, String completionText) {
        final List<String> result = new ArrayList<String>();

        final Set<String> apiNames = collection.getApiNames();
        boolean useDefaultName = apiNames.size() == 1 && apiNames.contains("");

        // check if there is an API name present
        completionText = ObjectHelper.isEmpty(completionText) ? "" : completionText;
        final int prefixEnd = completionText.indexOf('/');
        final int pathEnd = completionText.lastIndexOf('?');

        // empty or incomplete API prefix, and no options, add API names or method names if useDefaultName
        final Map<E, ? extends ApiMethodHelper<? extends ApiMethod>> apiHelpers = collection.getApiHelpers();
        if (prefixEnd == -1 && pathEnd == -1) {

            if (useDefaultName) {

                // complete method names for default API
                final Set<Class<? extends ApiMethod>> apiMethods = collection.getApiMethods().keySet();
                final Class<? extends ApiMethod> apiMethod = apiMethods.iterator().next();
                final ApiMethodHelper<? extends ApiMethod> helper = apiHelpers.values().iterator().next();
                getCompletedMethods(result, completionText, apiMethod, helper);
            } else {

                // complete API names
                for (String name : apiNames) {
                    if (!name.isEmpty() || name.startsWith(completionText)) {
                        result.add(name);
                    }
                }
            }

        // path with complete API name prefix, but no options
        } else if (prefixEnd != -1 && pathEnd == -1) {

            // complete method names for specified API
            final E apiName = getApiNameOrNull(completionText.substring(0, prefixEnd));
            if (apiName != null) {
                final ApiMethodHelper<? extends ApiMethod> helper = apiHelpers.get(apiName);
                completionText = completionText.substring(prefixEnd + 1);
                for (Map.Entry<Class<? extends ApiMethod>, E> entry : collection.getApiMethods().entrySet()) {
                    if (entry.getValue().equals(apiName)) {
                        getCompletedMethods(result, completionText, entry.getKey(), helper);
                        break;
                    }
                }
            }

        // complete options
        } else {

            // get last option text
            final int lastParam = completionText.lastIndexOf('&');
            String optionText;
            if (lastParam != -1) {
                optionText = completionText.substring(lastParam + 1);
            } else {
                optionText = completionText.substring(pathEnd);
            }

            String methodName = null;
            ApiMethodHelper<? extends ApiMethod> helper = null;
            if (useDefaultName) {

                // get default endpoint configuration and helper
                methodName = completionText.substring(0, pathEnd);
                helper = apiHelpers.values().iterator().next();
            } else {

                // get API name and method name, if they exist
                final String[] pathElements = completionText.substring(0, pathEnd).split("/");
                if (pathElements.length == 2) {
                    final E apiName = getApiNameOrNull(pathElements[0]);
                    methodName = pathElements[1];
                    helper = collection.getHelper(apiName);
                }
            }
            if (helper != null && !ObjectHelper.isEmpty(methodName)) {
                // get other options from configuration
                Set<String> existingOptions = configuration.getParameters().keySet();
                // get all method options
                try {
                    final List<Object> arguments = helper.getArguments(methodName);
                    final int nArgs = arguments.size();
                    final Set<String> options = new HashSet<String>();
                    for (int i = 1; i < nArgs; i += 2) {
                        options.add((String) arguments.get(i));
                    }
                    options.removeAll(existingOptions);

                    // return matching options
                    for (String option : options) {
                        if (option.startsWith(optionText)) {
                            result.add(option);
                        }
                    }
                } catch (IllegalArgumentException ignore) {
                    // thrown from getArguments() when no matching methods,
                    // return an empty result
                }
            }
        }

        return result;
    }

    // returns null instead of throwing IllegalArgumentException for invalid name
    protected E getApiNameOrNull(String nameStr) {
        try {
            return getApiName(nameStr);
        } catch (IllegalArgumentException ignore) {
            return null;
        }
    }

    protected void getCompletedMethods(List<String> result, String completionText,
                                     Class<? extends ApiMethod> apiMethod, ApiMethodHelper<? extends ApiMethod> helper) {
        // add potential method names
        final ApiMethod[] methods = apiMethod.getEnumConstants();
        for (ApiMethod method : methods) {
            final String name = method.getName();
            if (name.startsWith(completionText)) {
                result.add(name);
            }
        }
        // add potential aliases
        final Map<String, Set<String>> aliases = helper.getAliases();
        for (String alias : aliases.keySet()) {
            if (alias.startsWith(completionText)) {
                result.add(alias);
            }
        }
    }
}
