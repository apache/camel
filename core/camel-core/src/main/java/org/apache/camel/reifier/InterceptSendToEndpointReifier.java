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
package org.apache.camel.reifier;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultInterceptSendToEndpoint;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.processor.InterceptEndpointProcessor;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.util.URISupport;

class InterceptSendToEndpointReifier extends ProcessorReifier<InterceptSendToEndpointDefinition> {

    InterceptSendToEndpointReifier(ProcessorDefinition<?> definition) {
        super((InterceptSendToEndpointDefinition) definition);
    }

    @Override
    public Processor createProcessor(final RouteContext routeContext) throws Exception {
        // create the detour
        final Processor detour = this.createChildProcessor(routeContext, true);
        final String matchURI = definition.getUri();

        // register endpoint callback so we can proxy the endpoint
        routeContext.getCamelContext().addRegisterEndpointCallback(new EndpointStrategy() {
            public Endpoint registerEndpoint(String uri, Endpoint endpoint) {
                if (endpoint instanceof DefaultInterceptSendToEndpoint) {
                    // endpoint already decorated
                    return endpoint;
                } else if (matchURI == null || matchPattern(routeContext.getCamelContext(), uri, matchURI)) {
                    // only proxy if the uri is matched decorate endpoint with our proxy
                    // should be false by default
                    boolean skip = definition.getSkipSendToOriginalEndpoint() != null && definition.getSkipSendToOriginalEndpoint();
                    DefaultInterceptSendToEndpoint proxy = new DefaultInterceptSendToEndpoint(endpoint, skip);
                    proxy.setDetour(detour);
                    return proxy;
                } else {
                    // no proxy so return regular endpoint
                    return endpoint;
                }
            }
        });


        // remove the original intercepted route from the outputs as we do not intercept as the regular interceptor
        // instead we use the proxy endpoints producer do the triggering. That is we trigger when someone sends
        // an exchange to the endpoint, see InterceptSendToEndpoint for details.
        RouteDefinition route = (RouteDefinition) routeContext.getRoute();
        List<ProcessorDefinition<?>> outputs = route.getOutputs();
        outputs.remove(this);

        return new InterceptEndpointProcessor(matchURI, detour);
    }

    /**
     * Does the uri match the pattern.
     *
     * @param camelContext the CamelContext
     * @param uri the uri
     * @param pattern the pattern, which can be an endpoint uri as well
     * @return <tt>true</tt> if matched and we should intercept, <tt>false</tt> if not matched, and not intercept.
     */
    protected boolean matchPattern(CamelContext camelContext, String uri, String pattern) {
        // match using the pattern as-is
        boolean match = EndpointHelper.matchEndpoint(camelContext, uri, pattern);
        if (!match) {
            try {
                // the pattern could be an uri, so we need to normalize it before matching again
                pattern = URISupport.normalizeUri(pattern);
                match = EndpointHelper.matchEndpoint(camelContext, uri, pattern);
            } catch (Exception e) {
                // ignore
            }
        }
        return match;
    }

}
