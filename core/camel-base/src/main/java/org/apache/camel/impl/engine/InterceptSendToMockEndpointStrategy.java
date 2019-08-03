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
package org.apache.camel.impl.engine;

import org.apache.camel.Endpoint;
import org.apache.camel.Producer;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.RuntimeCamelException.wrapRuntimeCamelException;

/**
 * A {@link EndpointStrategy} which is capable of mocking endpoints.
 * <p/>
 * This strategy will only apply when new endpoints are being created. If you want to replace
 * existing endpoints, you will have to remove them from the {@link org.apache.camel.CamelContext} beforehand.
 */
public class InterceptSendToMockEndpointStrategy implements EndpointStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(InterceptSendToMockEndpointStrategy.class);
    private final String pattern;
    private boolean skip;

    /**
     * Mock all endpoints.
     */
    public InterceptSendToMockEndpointStrategy() {
        this(null);
    }

    /**
     * Mock endpoints based on the given pattern.
     *
     * @param pattern the pattern.
     * @see EndpointHelper#matchEndpoint(org.apache.camel.CamelContext, String, String)
     */
    public InterceptSendToMockEndpointStrategy(String pattern) {
        this(pattern, false);
    }

    /**
     * Mock endpoints based on the given pattern.
     *
     * @param pattern the pattern.
     * @param skip <tt>true</tt> to skip sending after the detour to the original endpoint
     * @see EndpointHelper#matchEndpoint(org.apache.camel.CamelContext, String, String)
     */
    public InterceptSendToMockEndpointStrategy(String pattern, boolean skip) {
        this.pattern = pattern;
        this.skip = skip;
    }

    @Override
    public Endpoint registerEndpoint(String uri, Endpoint endpoint) {
        if (endpoint instanceof DefaultInterceptSendToEndpoint) {
            // endpoint already decorated
            return endpoint;
        } else if (endpoint.getEndpointUri().startsWith("mock:")) {
            // we should not intercept mock endpoints
            return endpoint;
        } else if (matchPattern(uri, endpoint, pattern)) {
            // if pattern is null then it mean to match all

            // only proxy if the uri is matched decorate endpoint with our proxy
            // should be false by default
            DefaultInterceptSendToEndpoint proxy = new DefaultInterceptSendToEndpoint(endpoint, skip);

            // create mock endpoint which we will use as interceptor
            // replace :// from scheme to make it easy to lookup the mock endpoint without having double :// in uri
            String key = "mock:" + endpoint.getEndpointKey().replaceFirst("://", ":");
            // strip off parameters as well
            if (key.contains("?")) {
                key = StringHelper.before(key, "?");
            }
            LOG.info("Adviced endpoint [{}] with mock endpoint [{}]", uri, key);

            Endpoint mock = endpoint.getCamelContext().getEndpoint(key, Endpoint.class);
            Producer producer;
            try {
                producer = mock.createProducer();
            } catch (Exception e) {
                throw wrapRuntimeCamelException(e);
            }

            // allow custom logic
            producer = onInterceptEndpoint(uri, endpoint, mock, producer);
            proxy.setBefore(producer);

            return proxy;
        } else {
            // no proxy so return regular endpoint
            return endpoint;
        }
    }

    /**
     * Does the pattern match the endpoint?
     *
     * @param uri          the uri
     * @param endpoint     the endpoint
     * @param pattern      the pattern
     * @return <tt>true</tt> to match and therefore intercept, <tt>false</tt> if not matched and should not intercept
     */
    protected boolean matchPattern(String uri, Endpoint endpoint, String pattern) {
        return uri == null || pattern == null || EndpointHelper.matchEndpoint(endpoint.getCamelContext(), uri, pattern);
    }

    /**
     * Callback when an endpoint was intercepted with the given mock endpoint
     *
     * @param uri          the uri
     * @param endpoint     the endpoint
     * @param mockEndpoint the mocked endpoint
     * @param mockProducer the mock producer
     * @return the mock producer
     */
    protected Producer onInterceptEndpoint(String uri, Endpoint endpoint, Endpoint mockEndpoint, Producer mockProducer) {
        return mockProducer;
    }

    @Override
    public String toString() {
        return "InterceptSendToMockEndpointStrategy";
    }

}
