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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Component;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for API Component Endpoints.
 */
public abstract class AbstractApiEndpoint<E extends ApiName, T> extends DefaultEndpoint {

    // logger
    protected final Logger log = LoggerFactory.getLogger(getClass());

    // API name
    protected final E apiName;

    // API method name
    protected final String methodName;

    // API method helper
    protected final ApiMethodHelper<? extends ApiMethod> methodHelper;

    // endpoint configuration
    @UriParam
    protected final T configuration;

    // property name for Exchange 'In' message body
    @UriParam
    protected String inBody;

    // candidate methods based on method name and endpoint configuration
    private List<ApiMethod> candidates;

    public AbstractApiEndpoint(String endpointUri, Component component,
                               E apiName, String methodName, ApiMethodHelper<? extends ApiMethod> methodHelper, T endpointConfiguration) {
        super(endpointUri, component);

        this.apiName = apiName;
        this.methodName = methodName;
        this.methodHelper = methodHelper;
        this.configuration = endpointConfiguration;
    }

    public boolean isSingleton() {
        return true;
    }

    /**
     * Returns generated helper that extends {@link ApiMethodPropertiesHelper} to work with API properties.
     * @return properties helper.
     */
    protected abstract ApiMethodPropertiesHelper<T> getPropertiesHelper();

    @Override
    public void configureProperties(Map<String, Object> options) {
        super.configureProperties(options);

        // set configuration properties first
        try {
            T configuration = getConfiguration();
            EndpointHelper.setReferenceProperties(getCamelContext(), configuration, options);
            EndpointHelper.setProperties(getCamelContext(), configuration, options);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }

        // validate and initialize state
        initState();

        afterConfigureProperties();
    }

    /**
     * Initialize proxies, create server connections, etc. after endpoint properties have been configured.
     */
    protected abstract void afterConfigureProperties();

    /**
     * Initialize endpoint state, including endpoint arguments, find candidate methods, etc.
     */
    protected void initState() {

        // get endpoint property names
        final Set<String> arguments = new HashSet<String>();
        arguments.addAll(getPropertiesHelper().getEndpointPropertyNames(getConfiguration()));

        // add inBody argument for producers
        if (inBody != null) {
            arguments.add(inBody);
        }

        interceptPropertyNames(arguments);

        final String[] argNames = arguments.toArray(new String[arguments.size()]);

        // create a list of candidate methods
        candidates = new ArrayList<ApiMethod>();
        candidates.addAll(methodHelper.getCandidateMethods(methodName, argNames));

        // error if there are no candidates
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("No matching method for %s/%s, with arguments %s",
                            apiName.getName(), methodName, arguments));
        }

        // log missing/extra properties for debugging
        if (log.isDebugEnabled()) {
            final Set<String> missing = methodHelper.getMissingProperties(methodName, arguments);
            if (!missing.isEmpty()) {
                log.debug("Method {} could use one or more properties from {}", methodName, missing);
            }
        }
    }

    /**
     * Intercept property names used to find Consumer and Producer methods.
     * Used to add any custom/hidden method arguments, which MUST be provided in interceptProperties() override
     * either in Endpoint, or Consumer and Producer.
     * @param propertyNames argument names.
     */
    @SuppressWarnings("unused")
    protected void interceptPropertyNames(Set<String> propertyNames) {
        // do nothing by default
    }

    /**
     * Intercept method invocation arguments used to find and invoke API method. Called by Consumer and Producer.
     * Must be overridden if also overriding interceptPropertyName() to add custom/hidden method properties.
     * @param properties method invocation arguments.
     */
    @SuppressWarnings("unused")
    protected void interceptProperties(Map<String, Object> properties) {
        // do nothing by default
    }

    /**
     * Returns endpoint configuration object.
     * One of the generated *EndpointConfiguration classes that extends component configuration class.
     *
     * @return endpoint configuration object
     */
    public T getConfiguration() {
        return configuration;
    }

    /**
     * Returns API name.
     * @return apiName property.
     */
    public E getApiName() {
        return apiName;
    }

    /**
     * Returns method name.
     * @return methodName property.
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * Returns method helper.
     * @return methodHelper property.
     */
    public ApiMethodHelper<? extends ApiMethod> getMethodHelper() {
        return methodHelper;
    }

    /**
     * Returns candidate methods for this endpoint.
     * @return list of candidate methods.
     */
    public List<ApiMethod> getCandidates() {
        return Collections.unmodifiableList(candidates);
    }

    /**
     * Returns name of parameter passed in the exchange In Body.
     * @return inBody property.
     */
    public String getInBody() {
        return inBody;
    }

    /**
     * Sets the name of a parameter to be passed in the exchange In Body.
     * @param inBody parameter name
     * @throws IllegalArgumentException for invalid parameter name.
     */
    public void setInBody(String inBody) throws IllegalArgumentException {
        // validate property name
        ObjectHelper.notNull(inBody, "inBody");
        if (!getPropertiesHelper().getValidEndpointProperties(getConfiguration()).contains(inBody)) {
            throw new IllegalArgumentException("Unknown property " + inBody);
        }
        this.inBody = inBody;
    }

    /**
     * Returns an instance of an API Proxy based on apiName, method and args.
     * Called by {@link AbstractApiConsumer} or {@link AbstractApiProducer}.
     *
     * @param method method about to be invoked
     * @param args method arguments
     * @return a Java object that implements the method to be invoked.
     * @see AbstractApiProducer
     * @see AbstractApiConsumer
     */
    public abstract Object getApiProxy(ApiMethod method, Map<String, Object> args);
}
