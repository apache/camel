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
package org.apache.camel.component.aws.xray.decorators;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.xray.entities.Entity;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.aws.xray.SegmentDecorator;
import org.apache.camel.util.URISupport;

/**
 * An abstract base implementation of the {@link SegmentDecorator} interface.
 */
public abstract class AbstractSegmentDecorator implements SegmentDecorator {

    @Override
    public boolean newSegment() {
        return true;
    }

    @Override
    public String getOperationName(Exchange exchange, Endpoint endpoint) {
        URI uri = URI.create(endpoint.getEndpointUri());
        return uri.getScheme() + ":" + uri.getRawAuthority();
    }

    @Override
    public void pre(Entity segment, Exchange exchange, Endpoint endpoint) {
        segment.putMetadata("component", CAMEL_COMPONENT + URI.create(endpoint.getEndpointUri()).getScheme());
        segment.putMetadata("camel.uri", URISupport.sanitizeUri(endpoint.getEndpointUri()));
    }

    @Override
    public void post(Entity segment, Exchange exchange, Endpoint endpoint) {
        if (exchange.isFailed()) {
            segment.setFault(true);
            if (exchange.getException() != null) {
                segment.addException(exchange.getException());
            }
        }
    }

    /**
     * This method removes the scheme, any leading slash characters and options from the supplied URI. This is intended
     * to extract a meaningful name from the URI that can be used in situations, such as the operation name.
     *
     * @param endpoint The endpoint
     * @return The stripped value from the URI
     */
    public static String stripSchemeAndOptions(Endpoint endpoint) {
        int start = endpoint.getEndpointUri().indexOf(":");
        start++;
        // Remove any leading '/'
        while (endpoint.getEndpointUri().charAt(start) == '/') {
            start++;
        }
        int end = endpoint.getEndpointUri().indexOf("?");
        return end == -1 ? endpoint.getEndpointUri().substring(start) : endpoint.getEndpointUri().substring(start, end);
    }

    /**
     * Extracts any parameters passed in the given URI as a key-value representation.
     *
     * @param uri The URI to extract passed parameters from
     * @return A {@link Map} representation of the contained parameters of the provided URI
     */
    public static Map<String,  String> toQueryParameters(String uri) {
        int index = uri.indexOf("?");
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
}
