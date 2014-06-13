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
import java.util.concurrent.ExecutorService;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for API based Producers
 */
public abstract class AbstractApiProducer extends DefaultAsyncProducer {

    // thread pool executor
    protected static ExecutorService executorService;

    // API Endpoint
    protected final AbstractApiEndpoint endpoint;

    // properties helper
    protected final ApiMethodPropertiesHelper propertiesHelper;

    // method helper
    protected final ApiMethodHelper methodHelper;

    // logger
    private final transient Logger log = LoggerFactory.getLogger(getClass());

    public AbstractApiProducer(AbstractApiEndpoint endpoint, ApiMethodPropertiesHelper propertiesHelper) {
        super(endpoint);
        this.propertiesHelper = propertiesHelper;
        this.endpoint = endpoint;
        this.methodHelper = endpoint.getMethodHelper();
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        // properties for method arguments
        final Map<String, Object> properties = new HashMap<String, Object>();
        propertiesHelper.getEndpointProperties(endpoint.getConfiguration(), properties);
        propertiesHelper.getExchangeProperties(exchange, properties);

        interceptProperties(properties);

        // decide which method to invoke
        final Enum<? extends ApiMethod> method = findMethod(exchange, properties);
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
                        log.debug("Invoking operation {} with {}", ((ApiMethod) method).getName(), properties.keySet());
                    }

                    Object result = doInvokeMethod(method, properties);

                    // producer returns a single response, even for methods with List return types
                    exchange.getOut().setBody(result);
                    // copy headers
                    exchange.getOut().setHeaders(exchange.getIn().getHeaders());

                    doProcessResult(exchange);

                } catch (Throwable t) {
                    exchange.setException(ObjectHelper.wrapRuntimeCamelException(t));
                } finally {
                    callback.done(false);
                }
            }
        };

        getExecutorService(getEndpoint().getCamelContext()).submit(invocation);
        return false;
    }

    /**
     * Intercept method invocation arguments used to find and invoke API method.
     * Can be overridden to add custom method properties.
     * @param properties method invocation arguments.
     */
    @SuppressWarnings("unused")
    protected void interceptProperties(Map<String, Object> properties) {
        // do nothing by default
    }

    /**
     * Invoke the API method. Derived classes can override, but MUST call super.doInvokeMethod().
     * @param method API method to invoke.
     * @param properties method arguments from endpoint properties and exchange In headers.
     * @return API method invocation result.
     * @throws RuntimeCamelException on error. Exceptions thrown by API method are wrapped.
     */
    @SuppressWarnings("unchecked")
    protected Object doInvokeMethod(Enum<? extends ApiMethod> method, Map<String, Object> properties) throws RuntimeCamelException {
        return methodHelper.invokeMethod(endpoint.getApiProxy(), method, properties);
    }

    /**
     * Do additional result processing, for example, add custom headers, etc.
     * @param resultExchange API method result as exchange.
     */
    @SuppressWarnings("unused")
    protected void doProcessResult(Exchange resultExchange) {
        // do nothing by default
    }

    @SuppressWarnings("unchecked")
    private Enum<? extends ApiMethod> findMethod(Exchange exchange, Map<String, Object> properties) {

        Enum<? extends ApiMethod> method = null;
        final List<Enum<? extends ApiMethod>> candidates = endpoint.getCandidates();
        if (processInBody(exchange, properties)) {

            // filter candidates based on endpoint and exchange properties
            final Set<String> argNames = properties.keySet();
            final List<Enum<? extends ApiMethod>> filteredMethods = methodHelper.filterMethods(candidates,
                    ApiMethodHelper.MatchType.SUPER_SET,
                    argNames.toArray(new String[argNames.size()]));

            // get the method to call
            if (filteredMethods.isEmpty()) {
                final Set<String> missing = methodHelper.getMissingProperties(endpoint.getMethodName(), argNames);
                throw new RuntimeCamelException(String.format("Missing properties for %s, need one or more from %s",
                        endpoint.getMethodName(), missing));
            } else if (filteredMethods.size() == 1) {
                // found an exact match
                method = filteredMethods.get(0);
            } else {
                method = methodHelper.getHighestPriorityMethod(filteredMethods);
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
            try {
                value = getEndpoint().getCamelContext().getTypeConverter().mandatoryConvertTo(
                        endpoint.getConfiguration().getClass().getDeclaredField(inBodyProperty).getType(),
                        exchange, value);
            } catch (Exception e) {
                exchange.setException(new RuntimeCamelException(String.format(
                        "Error converting value %s to property %s: %s", value, inBodyProperty, e.getMessage()), e));

                return false;
            }

            log.debug("Property [{}] has message body value {}", inBodyProperty, value);
            properties.put(inBodyProperty, value);
        }

        return true;
    }

    private synchronized ExecutorService getExecutorService(CamelContext context) {
        // CamelContext will shutdown thread pool when it shutdown so we can
        // lazy create it on demand
        // but in case of hot-deploy or the likes we need to be able to
        // re-create it (its a shared static instance)
        if (executorService == null || executorService.isTerminated() || executorService.isShutdown()) {
            final ExecutorServiceManager manager = context.getExecutorServiceManager();

            // try to lookup a pool first based on profile
            final String threadProfileName = getThreadProfileName();
            ThreadPoolProfile poolProfile = manager.getThreadPoolProfile(
                    threadProfileName);
            if (poolProfile == null) {
                poolProfile = manager.getDefaultThreadPoolProfile();
            }

            // create a new pool using the custom or default profile
            executorService = manager.newScheduledThreadPool(getClass(),
                    threadProfileName, poolProfile);
        }

        return executorService;
    }

    /**
     * Returns Thread profile name. Generated as a constant THREAD_PROFILE_NAME in *Constants.
     * @return thread profile name to use.
     */
    protected abstract String getThreadProfileName();
}
