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
package org.apache.camel.reifier;

import java.util.List;

import org.apache.camel.Endpoint;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.impl.engine.DefaultInterceptSendToEndpoint;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.processor.InterceptEndpointProcessor;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.util.URISupport;

public class InterceptSendToEndpointReifier extends ProcessorReifier<InterceptSendToEndpointDefinition> {

    public InterceptSendToEndpointReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (InterceptSendToEndpointDefinition) definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        // create the before
        final Processor before = this.createChildProcessor(true);
        // create the after
        Processor afterProcessor = null;
        if (definition.getAfterUri() != null) {
            ToDefinition to = new ToDefinition(parseString(definition.getAfterUri()));
            // at first use custom factory
            if (camelContext.adapt(ExtendedCamelContext.class).getProcessorFactory() != null) {
                afterProcessor = camelContext.adapt(ExtendedCamelContext.class).getProcessorFactory().createProcessor(route, to);
            }
            // fallback to default implementation if factory did not create the
            // processor
            if (afterProcessor == null) {
                afterProcessor = createProcessor(to);
            }
        }
        final Processor after = afterProcessor;
        final String matchURI = parseString(definition.getUri());

        // register endpoint callback so we can proxy the endpoint
        camelContext.adapt(ExtendedCamelContext.class).registerEndpointCallback(new EndpointStrategy() {
            public Endpoint registerEndpoint(String uri, Endpoint endpoint) {
                if (endpoint instanceof DefaultInterceptSendToEndpoint) {
                    // endpoint already decorated
                    return endpoint;
                } else if (matchURI == null || matchPattern(uri, matchURI)) {
                    // only proxy if the uri is matched decorate endpoint with
                    // our proxy
                    // should be false by default
                    boolean skip = parseBoolean(definition.getSkipSendToOriginalEndpoint(), false);
                    DefaultInterceptSendToEndpoint proxy = new DefaultInterceptSendToEndpoint(endpoint, skip);
                    proxy.setBefore(before);
                    proxy.setAfter(after);
                    return proxy;
                } else {
                    // no proxy so return regular endpoint
                    return endpoint;
                }
            }
        });

        // remove the original intercepted route from the outputs as we do not
        // intercept as the regular interceptor
        // instead we use the proxy endpoints producer do the triggering. That
        // is we trigger when someone sends
        // an exchange to the endpoint, see InterceptSendToEndpoint for details.
        RouteDefinition route = (RouteDefinition) this.route.getRoute();
        List<ProcessorDefinition<?>> outputs = route.getOutputs();
        outputs.remove(definition);

        return new InterceptEndpointProcessor(matchURI, before);
    }

    /**
     * Does the uri match the pattern.
     *
     * @param uri the uri
     * @param pattern the pattern, which can be an endpoint uri as well
     * @return <tt>true</tt> if matched and we should intercept, <tt>false</tt>
     *         if not matched, and not intercept.
     */
    protected boolean matchPattern(String uri, String pattern) {
        // match using the pattern as-is
        boolean match = EndpointHelper.matchEndpoint(camelContext, uri, pattern);
        if (!match) {
            try {
                // the pattern could be an uri, so we need to normalize it
                // before matching again
                pattern = URISupport.normalizeUri(pattern);
                match = EndpointHelper.matchEndpoint(camelContext, uri, pattern);
            } catch (Exception e) {
                // ignore
            }
        }
        return match;
    }

}
