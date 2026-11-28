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
package org.apache.camel.component.stub;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.seda.BlockingQueueFactory;
import org.apache.camel.component.seda.SedaComponent;
import org.apache.camel.spi.EndpointRegistry;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.NormalizedUri;

/**
 * The stub component is for stubbing out endpoints while developing or testing.
 *
 * Allows you to easily stub out a middleware transport by prefixing the URI with "stub:" which is handy for testing out
 * routes, or isolating bits of middleware.
 */
@org.apache.camel.spi.annotations.Component("stub")
public class StubComponent extends SedaComponent {

    @Metadata(label = "advanced",
              description = "Whether to also register each stub endpoint in the endpoint registry under its URI without the"
                            + " stub: prefix, so that looking up kafka:cheese returns the endpoint of stub:kafka:cheese.")
    private boolean shadow;
    @Metadata(label = "advanced",
              description = "The pattern that selects which stub endpoints shadow is applied to. It is matched against the"
                            + " whole endpoint URI including the stub: prefix, as an exact URI, a wildcard with a trailing *"
                            + " (such as stub:kafka*), or a regular expression. If not set, shadow applies to all stub"
                            + " endpoints.")
    private String shadowPattern;

    // guards making room in a queue without consumers, as the endpoints of a queue may have several producers
    private final Lock noConsumerLock = new ReentrantLock();

    public StubComponent() {
    }

    @Override
    protected void validateURI(String uri, String path, Map<String, Object> parameters) {
        // Don't validate so we can stub any URI
    }

    @Override
    protected void validateParameters(String uri, Map<String, Object> parameters, String optionPrefix) {
        // Don't validate so we can stub any URI
    }

    protected void setProperties(Endpoint endpoint, Map<String, Object> parameters) throws Exception {
        // Don't configure extra properties
    }

    @Override
    protected StubEndpoint createEndpoint(
            String endpointUri, Component component, BlockingQueueFactory<Exchange> queueFactory, int concurrentConsumers) {
        return new StubEndpoint(endpointUri, component, queueFactory, concurrentConsumers);
    }

    @Override
    protected StubEndpoint createEndpoint(
            String endpointUri, Component component, BlockingQueue<Exchange> queue, int concurrentConsumers) {
        return new StubEndpoint(endpointUri, component, queue, concurrentConsumers);
    }

    /**
     * Strategy to resolve the shadow uri to use for the stub endpoints
     */
    protected String resolveShadowUri(String uri) {
        if (uri.startsWith("stub://")) {
            uri = uri.substring(7);
        } else if (uri.startsWith("stub:")) {
            uri = uri.substring(5);
        }
        return uri;
    }

    public boolean isShadow() {
        return shadow;
    }

    public void setShadow(boolean shadow) {
        this.shadow = shadow;
    }

    public String getShadowPattern() {
        return shadowPattern;
    }

    public void setShadowPattern(String shadowPattern) {
        this.shadowPattern = shadowPattern;
    }

    Lock getNoConsumerLock() {
        return noConsumerLock;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (shadow) {
            final EndpointRegistry registry = getCamelContext().getEndpointRegistry();
            getCamelContext().getCamelContextExtension().registerEndpointCallback((uri, endpoint) -> {
                boolean match = shadowPattern == null || EndpointHelper.matchEndpoint(getCamelContext(), uri, shadowPattern);
                if (match) {
                    String shadowUri = resolveShadowUri(uri);
                    if (!uri.equals(shadowUri)) {
                        NormalizedUri nuri = NormalizedUri.newNormalizedUri(shadowUri, false);
                        // put the shadow uri directly into the endpoint registry,
                        // so we can lookup the stubbed endpoint using its actual uri
                        registry.put(nuri, endpoint);
                    }
                }
                return endpoint;
            });
        }
    }
}
