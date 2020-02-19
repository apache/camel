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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for API Component Endpoints.
 */
public abstract class AbstractApiEndpoint<E extends ApiName, T>
    extends ScheduledPollEndpoint implements PropertyNamesInterceptor, PropertiesInterceptor {

    // thread pool executor with Endpoint Class name as keys
    private static Map<String, ExecutorService> executorServiceMap = new ConcurrentHashMap<>();

    // logger
    protected final Logger log = LoggerFactory.getLogger(getClass());

    // API name
    protected final E apiName;

    // API method name
    protected final String methodName;

    // API method helper
    protected final ApiMethodHelper<? extends ApiMethod> methodHelper;

    // endpoint configuration
    protected final T configuration;

    // property name for Exchange 'In' message body
    @UriParam(description = "Sets the name of a parameter to be passed in the exchange In Body")
    protected String inBody;

    // candidate methods based on method name and endpoint configuration
    private List<ApiMethod> candidates;

    // cached Executor service
    private ExecutorService executorService;

    // cached property names and values
    private Set<String> endpointPropertyNames;
    private Map<String, Object> endpointProperties;

    public AbstractApiEndpoint(String endpointUri, Component component,
                               E apiName, String methodName, ApiMethodHelper<? extends ApiMethod> methodHelper, T endpointConfiguration) {
        super(endpointUri, component);

        this.apiName = apiName;
        this.methodName = methodName;
        this.methodHelper = methodHelper;
        this.configuration = endpointConfiguration;
    }

    /**
     * Returns generated helper that extends {@link ApiMethodPropertiesHelper} to work with API properties.
     * @return properties helper.
     */
    protected abstract ApiMethodPropertiesHelper<T> getPropertiesHelper();

    @Override
    public void configureProperties(Map<String, Object> options) {
        super.configureProperties(options);
        // TODO: this is not very clean as it does not leverage the endpoint
        // TODO: configurer, but the generated configurer currently does not
        // TODO: support configuration inheritance, so only basic options
        // TODO: are supported.  This should be fixed.
        setProperties(getConfiguration(), options);

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
    private void initState() {

        // compute endpoint property names and values
        this.endpointPropertyNames = Collections.unmodifiableSet(
            getPropertiesHelper().getEndpointPropertyNames(getCamelContext(), configuration));
        final HashMap<String, Object> properties = new HashMap<>();
        getPropertiesHelper().getEndpointProperties(getCamelContext(), configuration, properties);
        this.endpointProperties = Collections.unmodifiableMap(properties);

        // get endpoint property names
        final Set<String> arguments = new HashSet<>(endpointPropertyNames);
        // add inBody argument for producers
        if (inBody != null) {
            arguments.add(inBody);
        }

        interceptPropertyNames(arguments);

        // create a list of candidate methods
        candidates = new ArrayList<>();
        candidates.addAll(methodHelper.getCandidateMethods(methodName, arguments));
        candidates = Collections.unmodifiableList(candidates);

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

    @Override
    public void interceptPropertyNames(Set<String> propertyNames) {
        // do nothing by default
    }

    @Override
    public void interceptProperties(Map<String, Object> properties) {
        // do nothing by default
    }

    /**
     * Returns endpoint configuration object.
     * One of the generated EndpointConfiguration classes that extends component configuration class.
     *
     * @return endpoint configuration object
     */
    public final T getConfiguration() {
        return configuration;
    }

    /**
     * Returns API name.
     * @return apiName property.
     */
    public final E getApiName() {
        return apiName;
    }

    /**
     * Returns method name.
     * @return methodName property.
     */
    public final String getMethodName() {
        return methodName;
    }

    /**
     * Returns method helper.
     * @return methodHelper property.
     */
    public final ApiMethodHelper<? extends ApiMethod> getMethodHelper() {
        return methodHelper;
    }

    /**
     * Returns candidate methods for this endpoint.
     * @return list of candidate methods.
     */
    public final List<ApiMethod> getCandidates() {
        return candidates;
    }

    /**
     * Returns name of parameter passed in the exchange In Body.
     * @return inBody property.
     */
    public final String getInBody() {
        return inBody;
    }

    /**
     * Sets the name of a parameter to be passed in the exchange In Body.
     * @param inBody parameter name
     * @throws IllegalArgumentException for invalid parameter name.
     */
    public final void setInBody(String inBody) throws IllegalArgumentException {
        // validate property name
        ObjectHelper.notNull(inBody, "inBody");
        if (!getPropertiesHelper().getValidEndpointProperties(getConfiguration()).contains(inBody)) {
            throw new IllegalArgumentException("Unknown property " + inBody);
        }
        this.inBody = inBody;
    }

    public final Set<String> getEndpointPropertyNames() {
        return endpointPropertyNames;
    }

    public final Map<String, Object> getEndpointProperties() {
        return endpointProperties;
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

    private static ExecutorService getExecutorService(
        Class<? extends AbstractApiEndpoint> endpointClass, CamelContext context, String threadProfileName) {

        // lookup executorService for extending class name
        final String endpointClassName = endpointClass.getName();
        ExecutorService executorService = executorServiceMap.get(endpointClassName);

        // CamelContext will shutdown thread pool when it shutdown so we can
        // lazy create it on demand
        // but in case of hot-deploy or the likes we need to be able to
        // re-create it (its a shared static instance)
        if (executorService == null || executorService.isTerminated() || executorService.isShutdown()) {
            final ExecutorServiceManager manager = context.getExecutorServiceManager();

            // try to lookup a pool first based on profile
            ThreadPoolProfile poolProfile = manager.getThreadPoolProfile(
                threadProfileName);
            if (poolProfile == null) {
                poolProfile = manager.getDefaultThreadPoolProfile();
            }

            // create a new pool using the custom or default profile
            executorService = manager.newScheduledThreadPool(endpointClass, threadProfileName, poolProfile);

            executorServiceMap.put(endpointClassName, executorService);
        }

        return executorService;
    }

    public final ExecutorService getExecutorService() {
        if (this.executorService == null) {
            // synchronize on class to avoid creating duplicate class level executors
            synchronized (getClass()) {
                this.executorService = getExecutorService(getClass(), getCamelContext(), getThreadProfileName());
            }
        }
        return this.executorService;
    }

    /**
     * Returns Thread profile name. Generated as a constant THREAD_PROFILE_NAME in *Constants.
     * @return thread profile name to use.
     */
    protected abstract String getThreadProfileName();
}
