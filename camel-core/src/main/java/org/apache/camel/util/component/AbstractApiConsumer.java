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
public abstract class AbstractApiConsumer extends ScheduledPollConsumer {

    // logger
    protected final Logger log = LoggerFactory.getLogger(getClass());

    // API Endpoint
    protected final AbstractApiEndpoint endpoint;

    // helpers
    protected final ApiMethodPropertiesHelper propertiesHelper;
    protected final ApiMethodHelper methodHelper;

    // API method to invoke
    protected final Enum<? extends ApiMethod> method;

    // properties used to invoke
    protected final Map<String, Object> endpointProperties;

    public AbstractApiConsumer(AbstractApiEndpoint endpoint, Processor processor) {
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

    @SuppressWarnings("unchecked")
    private Enum<? extends ApiMethod> findMethod() {

        Enum<? extends ApiMethod> result;
        // find one that takes the largest subset of endpoint parameters
        final Set<String> argNames = new HashSet<String>();
        argNames.addAll(propertiesHelper.getEndpointPropertyNames(endpoint.getConfiguration()));

        interceptArgumentNames(argNames);

        final String[] argNamesArray = argNames.toArray(new String[argNames.size()]);
        List<Enum<? extends ApiMethod>> filteredMethods = methodHelper.filterMethods(
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
            result = methodHelper.getHighestPriorityMethod(filteredMethods);
            log.warn("Using highest priority operation {} from operations {}", method, filteredMethods);
        }
        return result;
    }

    /**
     * Intercept argument names used to find consumer method.
     * Used to add any custom/hidden method arguments, which MUST be provided in getMethodArguments() override.
     * @param argNames argument names.
     */
    protected void interceptArgumentNames(Set<String> argNames) {
        // do nothing by default
    }

    @Override
    protected int poll() throws Exception {
        // invoke the consumer method
        final Map<String, Object> args = getMethodArguments();
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
     * Invoke the API method.
     * This method can be overridden, for example to synchronize API calls for thread-unsafe proxies.
     * Derived class MUST call super.doInvokeMethod() to invoke the API method.
     * @param args method arguments from endpoint parameters.
     * @return method invocation result.
     */
    @SuppressWarnings("unchecked")
    protected Object doInvokeMethod(Map<String, Object> args) {
        return methodHelper.invokeMethod(endpoint.getApiProxy(), method, args);
    }

    private void processResult(Object result) throws Exception {
        Exchange exchange = getEndpoint().createExchange();
        exchange.getIn().setBody(result);

        doProcessResult(exchange);
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
    protected void doProcessResult(Exchange resultExchange) {
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

    /**
     * Return method arguments to use in doInvokeMethod().
     * Derived classes can override it to add custom arguments.
     * Overriding method MUST first call super.getMethodArguments() to get endpoint properties.
     * @return
     */
    protected Map<String, Object> getMethodArguments() {
        Map<String, Object> arguments = new HashMap<String, Object>();
        arguments.putAll(endpointProperties);
        return arguments;
    }
}
