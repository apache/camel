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
package org.apache.camel.impl;

import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.util.EndpointHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.wrapRuntimeCamelException;

/**
 * A {@link EndpointStrategy} which is capable of mocking endpoints.
 * <p/>
 * This strategy will only apply when new endpoints is being created. If you want to apply
 * existing endpoints, you will have to remove them from the {@link org.apache.camel.CamelContext} beforehand.
 *
 * @version 
 */
public class InterceptSendToMockEndpointStrategy implements EndpointStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(InterceptSendToMockEndpointStrategy.class);
    private final String pattern;

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
     * @see EndpointHelper#matchEndpoint(String, String)
     */
    public InterceptSendToMockEndpointStrategy(String pattern) {
        this.pattern = pattern;
    }

    public Endpoint registerEndpoint(String uri, Endpoint endpoint) {
        if (endpoint instanceof InterceptSendToEndpoint) {
            // endpoint already decorated
            return endpoint;
        } else if (endpoint instanceof MockEndpoint) {
            // we should not intercept mock endpoints
            return endpoint;
        } else if (uri == null || pattern == null || EndpointHelper.matchEndpoint(uri, pattern)) {
            // if pattern is null then it mean to match all

            // only proxy if the uri is matched decorate endpoint with our proxy
            // should be false by default
            InterceptSendToEndpoint proxy = new InterceptSendToEndpoint(endpoint, false);

            // create mock endpoint which we will use as interceptor
            // replace :// from scheme to make it easy to lookup the mock endpoint without having double :// in uri
            String key = "mock:" + endpoint.getEndpointKey().replaceFirst("://", ":");
            LOG.info("Adviced endpoint [" + uri + "] with mock endpoint [" + key + "]");

            MockEndpoint mock = endpoint.getCamelContext().getEndpoint(key, MockEndpoint.class);
            Processor producer;
            try {
                producer = mock.createProducer();
            } catch (Exception e) {
                throw wrapRuntimeCamelException(e);
            }

            proxy.setDetour(producer);
            return proxy;
        } else {
            // no proxy so return regular endpoint
            return endpoint;
        }
    }

    @Override
    public String toString() {
        return "InterceptSendToMockEndpointStrategy";
    }

}
