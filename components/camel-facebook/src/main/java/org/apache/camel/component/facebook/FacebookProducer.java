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
package org.apache.camel.component.facebook;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import facebook4j.Facebook;
import facebook4j.json.DataObjectFactory;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.facebook.config.FacebookEndpointConfiguration;
import org.apache.camel.component.facebook.data.FacebookMethodsType;
import org.apache.camel.component.facebook.data.FacebookMethodsTypeHelper;
import org.apache.camel.component.facebook.data.FacebookPropertiesHelper;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.DefaultAsyncProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.facebook.data.FacebookMethodsTypeHelper.MatchType;
import static org.apache.camel.component.facebook.data.FacebookMethodsTypeHelper.filterMethods;
import static org.apache.camel.component.facebook.data.FacebookMethodsTypeHelper.getMissingProperties;
import static org.apache.camel.component.facebook.data.FacebookPropertiesHelper.getEndpointProperties;
import static org.apache.camel.component.facebook.data.FacebookPropertiesHelper.getExchangeProperties;

/**
 * The Facebook producer.
 */
public class FacebookProducer extends DefaultAsyncProducer {
    private static final transient Logger LOG = LoggerFactory.getLogger(FacebookProducer.class);

    // thread pool executor
    private static ExecutorService executorService;

    private FacebookEndpoint endpoint;

    public FacebookProducer(FacebookEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        // properties for method arguments
        final Map<String, Object> properties = new HashMap<>();

        getExchangeProperties(exchange, properties);
        FacebookPropertiesHelper.configureReadingProperties(endpoint.getConfiguration(), properties);
        getEndpointProperties(endpoint.getCamelContext(), endpoint.getConfiguration(), properties);

        // decide which method to invoke
        final FacebookMethodsType method = findMethod(exchange, properties);
        if (method == null) {
            // synchronous failure
            callback.done(true);
            return true;
        }

        // create a runnable invocation task to be submitted on a background thread pool
        // this way we avoid blocking the current thread for long running operations
        Runnable invocation = new Runnable() {
            @Override
            public void run() {
                try {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Invoking method {} with {}", method.getName(), properties.keySet());
                    }

                    // also check whether we need to get Raw JSON
                    Object result;
                    String rawJSON = null;
                    if (endpoint.getConfiguration().getJsonStoreEnabled() == null
                        || !endpoint.getConfiguration().getJsonStoreEnabled()) {
                        result = FacebookMethodsTypeHelper.invokeMethod(
                            endpoint.getConfiguration().getFacebook(), method, properties);
                    } else {
                        final Facebook facebook = endpoint.getConfiguration().getFacebook();
                        // lock out the underlying Facebook object from other threads
                        synchronized (facebook) {
                            result = FacebookMethodsTypeHelper.invokeMethod(
                                facebook, method, properties);
                            rawJSON = DataObjectFactory.getRawJSON(result);
                        }
                    }

                    // producer returns a single response, even for methods with List return types
                    exchange.getMessage().setBody(result);
                    // copy headers
                    exchange.getMessage().setHeaders(exchange.getIn().getHeaders());
                    if (rawJSON != null) {
                        exchange.getMessage().setHeader(FacebookConstants.FACEBOOK_PROPERTY_PREFIX + "rawJSON",
                            rawJSON);
                    }

                } catch (Throwable t) {
                    exchange.setException(RuntimeCamelException.wrapRuntimeCamelException(t));
                } finally {
                    callback.done(false);
                }
            }
        };

        getExecutorService(getEndpoint().getCamelContext()).submit(invocation);
        return false;
    }

    private boolean hasReadingParameters(Map<String, Object> properties) {
        for (String parameterName : properties.keySet()) {
            if (parameterName.startsWith(FacebookConstants.READING_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    private FacebookMethodsType findMethod(Exchange exchange, Map<String, Object> properties) {

        FacebookMethodsType method = null;
        final List<FacebookMethodsType> candidates = endpoint.getCandidates();
        if (processInBody(exchange, properties)) {

            // filter candidates based on endpoint and exchange properties
            final Set<String> argNames = properties.keySet();
            final List<FacebookMethodsType> filteredMethods = filterMethods(candidates, MatchType.SUPER_SET,
                argNames.toArray(new String[argNames.size()]));

            // get the method to call
            if (filteredMethods.isEmpty()) {
                final Set<String> missing = getMissingProperties(endpoint.getMethod(),
                    endpoint.getNameStyle(), argNames);
                throw new RuntimeCamelException(String.format("Missing properties for %s, need one or more from %s",
                        endpoint.getMethod(), missing));
            } else if (filteredMethods.size() == 1) {
                // found an exact match
                method = filteredMethods.get(0);
            } else {
                method = FacebookMethodsTypeHelper.getHighestPriorityMethod(filteredMethods);
                LOG.warn("Calling highest priority method {} from methods {}", method, filteredMethods);
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
                    FacebookEndpointConfiguration.class.getDeclaredField(inBodyProperty).getType(),
                    exchange, value);
            } catch (Exception e) {
                exchange.setException(new RuntimeCamelException(String.format(
                    "Error converting value %s to property %s: %s", value, inBodyProperty, e.getMessage()), e));

                return false;
            }

            LOG.debug("Property [{}] has message body value {}", inBodyProperty, value);
            properties.put(inBodyProperty, value);
        }

        return true;
    }

    protected static synchronized ExecutorService getExecutorService(CamelContext context) {
        // CamelContext will shutdown thread pool when it shutdown so we can
        // lazy create it on demand
        // but in case of hot-deploy or the likes we need to be able to
        // re-create it (its a shared static instance)
        if (executorService == null || executorService.isTerminated() || executorService.isShutdown()) {
            final ExecutorServiceManager manager = context.getExecutorServiceManager();

            // try to lookup a pool first based on profile
            ThreadPoolProfile poolProfile = manager.getThreadPoolProfile(
                FacebookConstants.FACEBOOK_THREAD_PROFILE_NAME);
            if (poolProfile == null) {
                poolProfile = manager.getDefaultThreadPoolProfile();
            }

            // create a new pool using the custom or default profile
            executorService = manager.newScheduledThreadPool(FacebookProducer.class,
                FacebookConstants.FACEBOOK_THREAD_PROFILE_NAME, poolProfile);
        }

        return executorService;
    }

}
