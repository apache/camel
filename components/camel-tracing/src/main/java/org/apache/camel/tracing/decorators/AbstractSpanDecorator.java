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
package org.apache.camel.tracing.decorators;

import java.util.*;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.tracing.ExtractAdapter;
import org.apache.camel.tracing.InjectAdapter;
import org.apache.camel.tracing.SpanAdapter;
import org.apache.camel.tracing.SpanDecorator;
import org.apache.camel.tracing.SpanKind;
import org.apache.camel.tracing.TagConstants;
import org.apache.camel.tracing.propagation.CamelHeadersExtractAdapter;
import org.apache.camel.tracing.propagation.CamelHeadersInjectAdapter;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

/**
 * An abstract base implementation of the {@link SpanDecorator} interface.
 */
public abstract class AbstractSpanDecorator implements SpanDecorator {

    /**
     * This method removes the scheme, any leading slash characters and options from the supplied URI. This is intended
     * to extract a meaningful name from the URI that can be used in situations, such as the operation name.
     *
     * @param  endpoint The endpoint
     * @return          The stripped value from the URI
     */
    public static String stripSchemeAndOptions(Endpoint endpoint) {
        int start = endpoint.getEndpointUri().indexOf(':');
        start++;
        // Remove any leading '/'
        while (endpoint.getEndpointUri().charAt(start) == '/') {
            start++;
        }
        int end = endpoint.getEndpointUri().indexOf('?');
        return end == -1 ? endpoint.getEndpointUri().substring(start) : endpoint.getEndpointUri().substring(start, end);
    }

    public static Map<String, String> toQueryParameters(String uri) {
        int index = uri.indexOf('?');
        if (index != -1) {
            String queryString = uri.substring(index + 1);
            Map<String, String> map = new HashMap<>();
            for (String param : queryString.split("&")) {
                String[] parts = param.split("=");
                if (parts.length == 2) {
                    map.put(parts[0], parts[1]);
                }
            }
            return map;
        }
        return Collections.emptyMap();
    }

    private static String getComponentName(Endpoint endpoint) {
        String[] splitURI = StringHelper.splitOnCharacter(endpoint.getEndpointUri(), ":", 2);
        if (splitURI.length > 0) {
            return splitURI[0];
        } else {
            return null;
        }
    }

    @Override
    public boolean newSpan() {
        return true;
    }

    @Override
    public String getOperationName(Exchange exchange, Endpoint endpoint) {
        // OpenTracing aims to use low cardinality operation names. Ideally a
        // specific
        // span decorator should be defined for all relevant Camel components
        // that
        // identify a meaningful operation name
        return getComponentName(endpoint);
    }

    @Override
    public void pre(SpanAdapter span, Exchange exchange, Endpoint endpoint) {
        String scheme = getComponentName(endpoint);
        span.setComponent(CAMEL_COMPONENT + scheme);
        span.setTag(TagConstants.URL_SCHEME, scheme);

        // Including the endpoint URI provides access to any options that may
        // have been provided, for subsequent analysis
        String uri = URISupport.sanitizeUri(endpoint.getEndpointUri());
        span.setTag("camel.uri", uri);
        span.setTag(TagConstants.URL_PATH, stripSchemeAndOptions(endpoint));
        String query = URISupport.extractQuery(uri);
        if (query != null) {
            span.setTag(TagConstants.URL_QUERY, query);
        }
    }

    @Override
    public void post(SpanAdapter span, Exchange exchange, Endpoint endpoint) {
        if (exchange.isFailed()) {
            span.setError(true);
            if (exchange.getException() != null) {
                Map<String, String> logEvent = new HashMap<>();
                logEvent.put("event", "error");
                logEvent.put("error.kind", "Exception");
                logEvent.put("message", exchange.getException().getMessage());
                span.log(logEvent);
            }
        }
    }

    @Override
    public SpanKind getInitiatorSpanKind() {
        return SpanKind.SPAN_KIND_CLIENT;
    }

    @Override
    public SpanKind getReceiverSpanKind() {
        return SpanKind.SPAN_KIND_SERVER;
    }

    @Override
    public ExtractAdapter getExtractAdapter(final Map<String, Object> map, boolean encoding) {
        // no encoding supported per default
        return new CamelHeadersExtractAdapter(map);
    }

    @Override
    public InjectAdapter getInjectAdapter(final Map<String, Object> map, boolean encoding) {
        // no encoding supported per default
        return new CamelHeadersInjectAdapter(map);
    }
}
