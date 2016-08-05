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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for API based Producers
 */
public abstract class AbstractApiProducer<E extends Enum<E> & ApiName, T>
    extends DefaultAsyncProducer implements PropertiesInterceptor, ResultInterceptor {

    // API Endpoint
    protected final AbstractApiEndpoint<E, T> endpoint;

    // properties helper
    protected final ApiMethodPropertiesHelper<T> propertiesHelper;

    // method helper
    protected final ApiMethodHelper<?> methodHelper;

    // logger
    private final transient Logger log = LoggerFactory.getLogger(getClass());

    public AbstractApiProducer(AbstractApiEndpoint<E, T> endpoint, ApiMethodPropertiesHelper<T> propertiesHelper) {
        super(endpoint);
        this.propertiesHelper = propertiesHelper;
        this.endpoint = endpoint;
        this.methodHelper = endpoint.getMethodHelper();
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        // properties for method arguments
        final Map<String, Object> properties = new HashMap<String, Object>();
        properties.putAll(endpoint.getEndpointProperties());
        propertiesHelper.getExchangeProperties(exchange, properties);

        // let the endpoint and the Producer intercept properties
        endpoint.interceptProperties(properties);
        interceptProperties(properties);

        // decide which method to invoke
        final ApiMethod method = findMethod(exchange, properties);
        if (method == null) {
            // synchronous failure
            callback.done(true);
            return true;
        }

        // create a runnable invocation task to be submitted on a background thread pool
        // this way we avoid blocking the current thread for long running methods
        Runnable invocation = new Runnable() {
            @Override
            public void run() {
                try {
                    if (log.isDebugEnabled()) {
                        log.debug("Invoking operation {} with {}", method.getName(), properties.keySet());
                    }

                    Object result = doInvokeMethod(method, properties);

                    // producer returns a single response, even for methods with List return types
                    exchange.getOut().setBody(result);
                    // copy headers
                    exchange.getOut().setHeaders(exchange.getIn().getHeaders());

                    interceptResult(result, exchange);

                } catch (Throwable t) {
                    exchange.setException(ObjectHelper.wrapRuntimeCamelException(t));
                } finally {
                    callback.done(false);
                }
            }
        };

        endpoint.getExecutorService().submit(invocation);
        return false;
    }

    @Override
    public void interceptProperties(Map<String, Object> properties) {
        // do nothing by default
    }

    /**
     * Invoke the API method. Derived classes can override, but MUST call super.doInvokeMethod().
     * @param method API method to invoke.
     * @param properties method arguments from endpoint properties and exchange In headers.
     * @return API method invocation result.
     * @throws RuntimeCamelException on error. Exceptions thrown by API method are wrapped.
     */
    protected Object doInvokeMethod(ApiMethod method, Map<String, Object> properties) throws RuntimeCamelException {
        return ApiMethodHelper.invokeMethod(endpoint.getApiProxy(method, properties), method, properties);
    }

    @Override
    public final Object splitResult(Object result) {
        // producer never splits results
        return result;
    }

    @Override
    public void interceptResult(Object methodResult, Exchange resultExchange) {
        // do nothing by default
    }

    protected ApiMethod findMethod(Exchange exchange, Map<String, Object> properties) {

        ApiMethod method = null;
        final List<ApiMethod> candidates = endpoint.getCandidates();
        if (processInBody(exchange, properties)) {

            // filter candidates based on endpoint and exchange properties
            final Set<String> argNames = properties.keySet();
            final List<ApiMethod> filteredMethods = methodHelper.filterMethods(
                candidates,
                ApiMethodHelper.MatchType.SUPER_SET,
                argNames);

            // get the method to call
            if (filteredMethods.isEmpty()) {
                throw new RuntimeCamelException(String.format("Missing properties for %s, need one or more from %s",
                    endpoint.getMethodName(),
                    methodHelper.getMissingProperties(endpoint.getMethodName(), argNames))
                );
            } else if (filteredMethods.size() == 1) {
                // found an exact match
                method = filteredMethods.get(0);
            } else {
                method = ApiMethodHelper.getHighestPriorityMethod(filteredMethods);
                log.warn("Calling highest priority operation {} from operations {}", method, filteredMethods);
            }
        }

        return method;
    }

    // returns false on exception, which is set in exchange
    private boolean processInBody(Exchange exchange, Map<String, Object> properties) {
        final String inBodyProperty = endpoint.getInBody();
        if (inBodyProperty != null) {

            Object value = exchange.getIn().getBody();
            if (value != null) {
                try {
                    value = endpoint.getCamelContext().getTypeConverter().mandatoryConvertTo(
                        endpoint.getConfiguration().getClass().getDeclaredField(inBodyProperty).getType(),
                        exchange, value);
                } catch (Exception e) {
                    exchange.setException(new RuntimeCamelException(String.format(
                            "Error converting value %s to property %s: %s", value, inBodyProperty, e.getMessage()), e));

                    return false;
                }
            } else {
                // allow null values for inBody only if its a nullable option
                if (!methodHelper.getNullableArguments().contains(inBodyProperty)) {
                    exchange.setException(new NullPointerException(inBodyProperty));

                    return false;
                }
            }

            log.debug("Property [{}] has message body value {}", inBodyProperty, value);
            properties.put(inBodyProperty, value);
        }

        return true;
    }
}
