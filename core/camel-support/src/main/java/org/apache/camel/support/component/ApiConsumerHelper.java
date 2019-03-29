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

import java.lang.reflect.Array;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for API Consumers.
 */
public final class ApiConsumerHelper {

    private static final Logger LOG = LoggerFactory.getLogger(ApiConsumerHelper.class);

    private ApiConsumerHelper() {
    }

    /**
     * Utility method to find matching API Method for supplied endpoint's configuration properties.
     * @param endpoint endpoint for configuration properties.
     * @param propertyNamesInterceptor names interceptor for adapting property names, usually the consumer class itself.
     * @param <E> ApiName enumeration.
     * @param <T> Component configuration class.
     * @return matching ApiMethod.
     */
    public static <E extends Enum<E> & ApiName, T> ApiMethod findMethod(
        AbstractApiEndpoint<E, T> endpoint, PropertyNamesInterceptor propertyNamesInterceptor) {

        ApiMethod result;
        // find one that takes the largest subset of endpoint parameters
        final Set<String> argNames = new HashSet<>(endpoint.getEndpointPropertyNames());
        propertyNamesInterceptor.interceptPropertyNames(argNames);

        List<ApiMethod> filteredMethods = endpoint.methodHelper.filterMethods(
                endpoint.getCandidates(), ApiMethodHelper.MatchType.SUPER_SET, argNames);

        if (filteredMethods.isEmpty()) {
            ApiMethodHelper<? extends ApiMethod> methodHelper = endpoint.getMethodHelper();
            throw new IllegalArgumentException(
                    String.format("Missing properties for %s/%s, need one or more from %s",
                            endpoint.getApiName().getName(), endpoint.getMethodName(),
                            methodHelper.getMissingProperties(endpoint.getMethodName(), argNames)));
        } else if (filteredMethods.size() == 1) {
            // single match
            result = filteredMethods.get(0);
        } else {
            result = ApiMethodHelper.getHighestPriorityMethod(filteredMethods);
            LOG.warn(String.format("Using highest priority operation %s from operations %s for endpoint %s",
                result, filteredMethods, endpoint.getEndpointUri()));
        }

        return result;
    }

    /**
     * Utility method for Consumers to process API method invocation result.
     * @param consumer Consumer that wants to process results.
     * @param result result of API method invocation.
     * @param splitResult true if the Consumer wants to split result using {@link org.apache.camel.support.component.ResultInterceptor#splitResult(Object)} method.
     * @param <T> Consumer class that extends DefaultConsumer and implements {@link org.apache.camel.support.component.ResultInterceptor}.
     * @return number of result exchanges processed.
     * @throws Exception on error.
     */
    public static <T extends DefaultConsumer & ResultInterceptor> int getResultsProcessed(
        T consumer, Object result, boolean splitResult) throws Exception {

        // process result according to type
        if (result != null && splitResult) {
            // try to split the result
            final Object results = consumer.splitResult(result);

            if (results != null) {
                if (results instanceof List) {
                    // Optimized for lists
                    final List<?> list = (List<?>)results;
                    final int size = list.size();

                    // access elements by position rather than with iterator to
                    // reduce garbage
                    for (int i = 0; i < size; i++) {
                        processResult(consumer, result, list.get(i));
                    }

                    return size;
                } else if (results instanceof Iterable) {
                    // Optimized for iterable
                    int size = 0;
                    for (Object singleResult : (Iterable<?>)results) {
                        processResult(consumer, result, singleResult);
                        size++;
                    }

                    return size;
                } else if (results.getClass().isArray()) {
                    // Optimized for array
                    final int size = Array.getLength(results);
                    for (int i = 0; i < size; i++) {
                        processResult(consumer, result, Array.get(results, i));
                    }

                    return size;
                }
            }
        }

        processResult(consumer, result, result);
        return 1; // number of messages polled
    }

    private static <T extends DefaultConsumer & ResultInterceptor> void processResult(T consumer, Object methodResult, Object result)
        throws Exception {

        Exchange exchange = consumer.getEndpoint().createExchange();
        exchange.getIn().setBody(result);

        consumer.interceptResult(methodResult, exchange);
        try {
            // send message to next processor in the route
            consumer.getProcessor().process(exchange);
        } finally {
            // log exception if an exception occurred and was not handled
            final Exception exception = exchange.getException();
            if (exception != null) {
                consumer.getExceptionHandler().handleException("Error processing exchange", exchange, exception);
            }
        }
    }
}
