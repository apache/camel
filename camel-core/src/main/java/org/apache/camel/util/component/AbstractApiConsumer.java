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

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for API Component Consumers.
 */
public abstract class AbstractApiConsumer<E extends Enum<E> & ApiName, T> extends ScheduledPollConsumer {

    // logger
    protected final Logger log = LoggerFactory.getLogger(getClass());

    // API Endpoint
    protected final AbstractApiEndpoint<E, T> endpoint;

    // helpers
    protected final ApiMethodPropertiesHelper<T> propertiesHelper;
    protected final ApiMethodHelper<? extends ApiMethod> methodHelper;

    // API method to invoke
    protected final ApiMethod method;

    // properties used to invoke
    protected final Map<String, Object> endpointProperties;

    public AbstractApiConsumer(AbstractApiEndpoint<E, T> endpoint, Processor processor) {
        super(endpoint, processor);

        this.endpoint = endpoint;

        // cache helpers
        this.methodHelper = endpoint.getMethodHelper();
        this.propertiesHelper = endpoint.getPropertiesHelper();

        // get endpoint properties in a map
        final HashMap<String, Object> properties = new HashMap<String, Object>();
        propertiesHelper.getEndpointProperties(endpoint.getConfiguration(), properties);
        this.endpointProperties = Collections.unmodifiableMap(properties);

        this.method = findMethod();
    }

    @Override
    public boolean isGreedy() {
        // make this consumer not greedy to avoid making too many calls
        return false;
    }

    private ApiMethod findMethod() {

        ApiMethod result;
        // find one that takes the largest subset of endpoint parameters
        final Set<String> argNames = new HashSet<String>();
        argNames.addAll(propertiesHelper.getEndpointPropertyNames(endpoint.getConfiguration()));

        interceptPropertyNames(argNames);

        final String[] argNamesArray = argNames.toArray(new String[argNames.size()]);
        List<ApiMethod> filteredMethods = ApiMethodHelper.filterMethods(
                endpoint.getCandidates(), ApiMethodHelper.MatchType.SUPER_SET, argNamesArray);

        if (filteredMethods.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("Missing properties for %s/%s, need one or more from %s",
                            endpoint.getApiName().getName(), endpoint.getMethodName(),
                            methodHelper.getMissingProperties(endpoint.getMethodName(), argNames)));
        } else if (filteredMethods.size() == 1) {
            // single match
            result = filteredMethods.get(0);
        } else {
            result = ApiMethodHelper.getHighestPriorityMethod(filteredMethods);
            log.warn("Using highest priority operation {} from operations {}", method, filteredMethods);
        }

        return result;
    }

    @Override
    protected int poll() throws Exception {
        // invoke the consumer method
        final Map<String, Object> args = new HashMap<String, Object>();
        args.putAll(endpointProperties);

        // let the endpoint and the Consumer intercept properties
        endpoint.interceptProperties(args);
        interceptProperties(args);

        try {
            Object result = doInvokeMethod(args);

            // process result according to type
            if (result != null && (result instanceof Collection || result.getClass().isArray())) {
                // create an exchange for every element
                final Object array = getResultAsArray(result);
                final int length = Array.getLength(array);
                for (int i = 0; i < length; i++) {
                    processResult(Array.get(array, i));
                }
                return length;
            } else {
                processResult(result);
                return 1; // number of messages polled
            }
        } catch (Throwable t) {
            throw ObjectHelper.wrapRuntimeCamelException(t);
        }
    }

    /**
     * Intercept property names used to find Consumer method.
     * Used to add any custom/hidden method arguments, which MUST be provided in interceptProperties() override.
     * @param propertyNames argument names.
     */
    @SuppressWarnings("unused")
    protected void interceptPropertyNames(Set<String> propertyNames) {
        // do nothing by default
    }

    /**
     * Intercept method invocation arguments used to find and invoke API method.
     * Can be overridden to add custom/hidden method arguments.
     * @param properties method invocation arguments.
     */
    @SuppressWarnings("unused")
    protected void interceptProperties(Map<String, Object> properties) {
        // do nothing by default
    }

    /**
     * Invoke the API method.
     * This method can be overridden, for example to synchronize API calls for thread-unsafe proxies.
     * Derived class MUST call super.doInvokeMethod() to invoke the API method.
     * @param args method arguments from endpoint parameters.
     * @return method invocation result.
     */
    protected Object doInvokeMethod(Map<String, Object> args) {
        return ApiMethodHelper.invokeMethod(endpoint.getApiProxy(method, args), method, args);
    }

    private void processResult(Object result) throws Exception {
        Exchange exchange = getEndpoint().createExchange();
        exchange.getIn().setBody(result);

        interceptResult(exchange);
        try {
            // send message to next processor in the route
            getProcessor().process(exchange);
        } finally {
            // log exception if an exception occurred and was not handled
            final Exception exception = exchange.getException();
            if (exception != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exception);
            }
        }
    }

    /**
     * Derived classes can do additional result exchange processing, for example, adding custom headers.
     * @param resultExchange result as a Camel exchange.
     */
    @SuppressWarnings("unused")
    protected void interceptResult(Exchange resultExchange) {
        // do nothing by default
    }

    private Object getResultAsArray(Object result) {
        if (result.getClass().isArray()) {
            // no conversion needed
            return result;
        }
        // must be a Collection
        Collection<?> collection = (Collection<?>) result;
        return collection.toArray(new Object[collection.size()]);
    }
}
