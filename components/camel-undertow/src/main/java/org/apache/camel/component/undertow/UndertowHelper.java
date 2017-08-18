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
package org.apache.camel.component.undertow;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

/**
 * Helper class for useful methods used all over the component
 */
public final class UndertowHelper {

    private UndertowHelper() {
    }

    /**
     * Creates the URL to invoke.
     *
     * @param exchange the exchange
     * @param endpoint the endpoint
     * @return the URL to invoke
     */
    public static String createURL(Exchange exchange, UndertowEndpoint endpoint) {
        // rest producer may provide an override url to be used which we should discard if using (hence the remove)
        String uri = (String) exchange.getIn().removeHeader(Exchange.REST_HTTP_URI);
        if (uri == null) {
            uri = endpoint.getHttpURI().toASCIIString();
        }

        // resolve placeholders in uri
        try {
            uri = exchange.getContext().resolvePropertyPlaceholders(uri);
        } catch (Exception e) {
            throw new RuntimeExchangeException("Cannot resolve property placeholders with uri: " + uri, exchange, e);
        }

        // append HTTP_PATH to HTTP_URI if it is provided in the header
        String path = exchange.getIn().getHeader(Exchange.HTTP_PATH, String.class);
        // NOW the HTTP_PATH is just related path, we don't need to trim it
        if (path != null) {
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.length() > 0) {
                // make sure that there is exactly one "/" between HTTP_URI and
                // HTTP_PATH
                if (!uri.endsWith("/")) {
                    uri = uri + "/";
                }
                uri = uri.concat(path);
            }
        }

        // ensure uri is encoded to be valid
        uri = UnsafeUriCharactersEncoder.encodeHttpURI(uri);

        return uri;
    }

    /**
     * Creates the URI to invoke.
     *
     * @param exchange the exchange
     * @param url      the url to invoke
     * @param endpoint the endpoint
     * @return the URI to invoke
     */
    public static URI createURI(Exchange exchange, String url, UndertowEndpoint endpoint) throws URISyntaxException {
        URI uri = new URI(url);
        // rest producer may provide an override query string to be used which we should discard if using (hence the remove)
        String queryString = (String) exchange.getIn().removeHeader(Exchange.REST_HTTP_QUERY);
        // is a query string provided in the endpoint URI or in a header (header overrules endpoint)
        if (queryString == null) {
            queryString = exchange.getIn().getHeader(Exchange.HTTP_QUERY, String.class);
        }
        if (queryString == null) {
            queryString = endpoint.getHttpURI().getRawQuery();
        }
        // We should user the query string from the HTTP_URI header
        if (queryString == null) {
            queryString = uri.getRawQuery();
        }
        if (queryString != null) {
            // need to encode query string
            queryString = UnsafeUriCharactersEncoder.encodeHttpURI(queryString);
            uri = URISupport.createURIWithQuery(uri, queryString);
        }
        return uri;
    }

    public static void appendHeader(Map<String, Object> headers, String key, Object value) {
        if (headers.containsKey(key)) {
            Object existing = headers.get(key);
            List<Object> list;
            if (existing instanceof List) {
                list = (List<Object>) existing;
            } else {
                list = new ArrayList<Object>();
                list.add(existing);
            }
            list.add(value);
            value = list;
        }

        headers.put(key, value);
    }

    /**
     * Creates the HttpMethod to use to call the remote server, often either its GET or POST.
     */
    public static HttpString createMethod(Exchange exchange, UndertowEndpoint endpoint, boolean hasPayload) throws URISyntaxException {
        // is a query string provided in the endpoint URI or in a header (header
        // overrules endpoint)
        String queryString = exchange.getIn().getHeader(Exchange.HTTP_QUERY, String.class);
        // We need also check the HTTP_URI header query part
        String uriString = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
        // resolve placeholders in uriString
        try {
            uriString = exchange.getContext().resolvePropertyPlaceholders(uriString);
        } catch (Exception e) {
            throw new RuntimeExchangeException("Cannot resolve property placeholders with uri: " + uriString, exchange, e);
        }
        if (uriString != null) {
            URI uri = new URI(uriString);
            queryString = uri.getQuery();
        }
        if (queryString == null) {
            queryString = endpoint.getHttpURI().getRawQuery();
        }

        // compute what method to use either GET or POST
        HttpString answer;
        String m = exchange.getIn().getHeader(Exchange.HTTP_METHOD, String.class);
        if (m != null) {
            // always use what end-user provides in a header
            // must be in upper case
            m = m.toUpperCase();
            answer = new HttpString(m);
        } else if (queryString != null) {
            // if a query string is provided then use GET
            answer = Methods.GET;
        } else {
            // fallback to POST if we have payload, otherwise GET
            answer = hasPayload ? Methods.POST : Methods.GET;
        }

        return answer;
    }

    public static URI makeHttpURI(String httpURI) {
        return makeHttpURI(
            URI.create(UnsafeUriCharactersEncoder.encodeHttpURI(httpURI))
        );
    }

    public static URI makeHttpURI(URI httpURI) {
        if (ObjectHelper.isEmpty(httpURI.getPath())) {
            try {
                return new URI(
                    httpURI.getScheme(),
                    httpURI.getUserInfo(),
                    httpURI.getHost(),
                    httpURI.getPort(),
                    "/",
                    httpURI.getQuery(),
                    httpURI.getFragment()
                );
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
        } else {
            return httpURI;
        }
    }

}
