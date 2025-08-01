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
package org.apache.camel.component.http.helper;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.StreamCache;
import org.apache.camel.component.http.HttpConstants;
import org.apache.camel.component.http.HttpEndpoint;
import org.apache.camel.component.http.HttpMethods;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

public final class HttpMethodHelper {

    private HttpMethodHelper() {
        // Helper class
    }

    /**
     * Creates the HttpMethod to use to call the remote server, often either its GET or POST
     */
    public static HttpMethods createMethod(Exchange exchange, HttpEndpoint endpoint) throws URISyntaxException {
        // is a query string provided in the endpoint URI or in a header (header
        // overrules endpoint)
        String queryString = null;
        String uriString = null;
        if (!endpoint.isSkipControlHeaders()) {
            queryString = exchange.getIn().getHeader(HttpConstants.HTTP_QUERY, String.class);
            uriString = exchange.getIn().getHeader(HttpConstants.HTTP_URI, String.class);
        }
        if (uriString != null) {
            // resolve placeholders in uriString
            try {
                uriString = exchange.getContext().resolvePropertyPlaceholders(uriString);
            } catch (Exception e) {
                throw new RuntimeExchangeException("Cannot resolve property placeholders with uri: " + uriString, exchange, e);
            }
            // in case the URI string contains unsafe characters
            uriString = UnsafeUriCharactersEncoder.encodeHttpURI(uriString);
            URI uri = new URI(uriString);
            queryString = uri.getQuery();
        }
        if (queryString == null) {
            queryString = endpoint.getHttpUri().getRawQuery();
        }

        // compute what method to use either GET or POST
        HttpMethods answer;
        if (endpoint.getHttpMethod() != null) {
            // endpoint configured take precedence
            answer = HttpMethods.valueOf(endpoint.getHttpMethod().name());
        } else {
            // compute what method to use either GET or POST (header take precedence)
            HttpMethods m = null;
            if (!endpoint.isSkipControlHeaders()) {
                m = exchange.getIn().getHeader(HttpConstants.HTTP_METHOD, HttpMethods.class);
            }
            if (m != null) {
                // always use what end-user provides in a header
                answer = m;
            } else if (queryString != null) {
                // if a query string is provided then use GET
                answer = HttpMethods.GET;
            } else {
                // fallback to POST if we have payload, otherwise GET
                Object body = exchange.getMessage().getBody();
                if (body instanceof StreamCache sc) {
                    long len = sc.length();
                    answer = len > 0 ? HttpMethods.POST : HttpMethods.GET;
                } else {
                    answer = body != null ? HttpMethods.POST : HttpMethods.GET;
                }
            }
        }

        return answer;
    }

}
