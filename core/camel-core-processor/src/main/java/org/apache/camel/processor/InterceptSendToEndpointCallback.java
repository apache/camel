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
package org.apache.camel.processor;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.spi.InterceptSendToEndpoint;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.URISupport;

/**
 * Endpoint strategy used by intercept send to endpoint.
 */
public class InterceptSendToEndpointCallback implements EndpointStrategy {

    private final CamelContext camelContext;
    private final Processor before;
    private final Processor after;
    private final String matchURI;
    private final boolean skip;

    public InterceptSendToEndpointCallback(CamelContext camelContext, Processor before, Processor after, String matchURI,
                                           boolean skip) {
        this.camelContext = camelContext;
        this.before = before;
        this.after = after;
        this.matchURI = matchURI;
        this.skip = skip;
    }

    public Endpoint registerEndpoint(String uri, Endpoint endpoint) {
        if (endpoint instanceof InterceptSendToEndpoint) {
            // endpoint already decorated
            return endpoint;
        } else if (matchURI == null || matchPattern(uri, matchURI)) {
            // only proxy if the uri is matched decorate endpoint with
            // our proxy should be false by default
            return PluginHelper.getInterceptEndpointFactory(camelContext)
                    .createInterceptSendToEndpoint(camelContext, endpoint, skip, before, after);
        } else {
            // no proxy so return regular endpoint
            return endpoint;
        }
    }

    /**
     * Does the uri match the pattern.
     *
     * @param  uri     the uri
     * @param  pattern the pattern, which can be an endpoint uri as well
     * @return         <tt>true</tt> if matched and we should intercept, <tt>false</tt> if not matched, and not
     *                 intercept.
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
