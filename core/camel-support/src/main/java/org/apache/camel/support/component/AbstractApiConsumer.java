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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.ScheduledPollConsumer;

/**
 * Abstract base class for API Component Consumers.
 */
public abstract class AbstractApiConsumer<E extends Enum<E> & ApiName, T>
        extends ScheduledPollConsumer
        implements PropertyNamesInterceptor, PropertiesInterceptor, ResultInterceptor {

    // API Endpoint
    protected final AbstractApiEndpoint<E, T> endpoint;

    // API method to invoke
    protected final ApiMethod method;

    // split Array or Collection API method results into multiple Exchanges
    private boolean splitResult = true;

    public AbstractApiConsumer(AbstractApiEndpoint<E, T> endpoint, Processor processor) {
        super(endpoint, processor);

        this.endpoint = endpoint;
        this.method = ApiConsumerHelper.findMethod(endpoint, this);
    }

    @Override
    public boolean isGreedy() {
        // make this consumer not greedy to avoid making too many calls
        return false;
    }

    @Override
    protected int poll() throws Exception {
        // invoke the consumer method
        final Map<String, Object> args = new HashMap<>(endpoint.getEndpointProperties());

        // let the endpoint and the Consumer intercept properties
        endpoint.interceptProperties(args);
        interceptProperties(args);

        try {
            Object result = doInvokeMethod(args);

            // okay we have some response so lets mark the consumer as ready
            forceConsumerAsReady();

            return ApiConsumerHelper.getResultsProcessed(this, result, isSplitResult());
        } catch (Exception t) {
            throw RuntimeCamelException.wrapRuntimeCamelException(t);
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
     * Invoke the API method. This method can be overridden, for example to synchronize API calls for thread-unsafe
     * proxies. Derived class MUST call super.doInvokeMethod() to invoke the API method.
     *
     * @param  args method arguments from endpoint parameters.
     * @return      method invocation result.
     */
    protected Object doInvokeMethod(Map<String, Object> args) {
        return ApiMethodHelper.invokeMethod(endpoint.getApiProxy(method, args), method, args);
    }

    @Override
    public Object splitResult(Object result) {
        return result;
    }

    @Override
    public void interceptResult(Object result, Exchange resultExchange) {
        // do nothing by default
    }

    public final boolean isSplitResult() {
        return splitResult;
    }

    public final void setSplitResult(boolean splitResult) {
        this.splitResult = splitResult;
    }
}
