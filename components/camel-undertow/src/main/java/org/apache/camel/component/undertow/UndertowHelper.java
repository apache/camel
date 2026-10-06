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
package org.apache.camel.component.undertow;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Map;

import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.apache.camel.Exchange;
import org.apache.camel.util.CollectionHelper;
import org.apache.camel.util.IOHelper;
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
     * @param  exchange the exchange
     * @param  endpoint the endpoint
     * @return          the URL to invoke
     */
    public static String createURL(Exchange exchange, UndertowEndpoint endpoint) {
        // rest producer may provide an override url to be used which we should discard if using (hence the remove)
        String uri = (String) exchange.getIn().removeHeader(Exchange.REST_HTTP_URI);
        if (uri == null) {
            uri = endpoint.getHttpURI().toASCIIString();
        }

        // NOTE: property placeholders are resolved at build time on the endpoint uri written in the route,
        // never on the message-supplied override headers (see CAMEL-24282 / CAMEL-24418)

        // append HTTP_PATH to HTTP_URI if it is provided in the header
        String path = exchange.getIn().getHeader(UndertowConstants.HTTP_PATH, String.class);
        // NOW the HTTP_PATH is just related path, we don't need to trim it
        if (path != null) {
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (!path.isEmpty()) {
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
     * @param  exchange the exchange
     * @param  url      the url to invoke
     * @param  endpoint the endpoint
     * @return          the URI to invoke
     */
    public static URI createURI(Exchange exchange, String url, UndertowEndpoint endpoint) throws URISyntaxException {
        URI uri = new URI(url);
        // rest producer may provide an override query string to be used which we should discard if using (hence the remove)
        String queryString = (String) exchange.getIn().removeHeader(Exchange.REST_HTTP_QUERY);
        // is a query string provided in the endpoint URI or in a header (header overrules endpoint)
        if (queryString == null) {
            queryString = exchange.getIn().getHeader(UndertowConstants.HTTP_QUERY, String.class);
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
        CollectionHelper.appendEntry(headers, key, value);
    }

    /**
     * Creates the HttpMethod to use to call the remote server, often either its GET or POST.
     */
    public static HttpString createMethod(Exchange exchange, UndertowEndpoint endpoint, boolean hasPayload)
            throws URISyntaxException {
        // is a query string provided in the endpoint URI or in a header (header
        // overrules endpoint)
        String queryString = exchange.getIn().getHeader(UndertowConstants.HTTP_QUERY, String.class);
        // We need also check the HTTP_URI header query part
        String uriString = exchange.getIn().getHeader(UndertowConstants.HTTP_URI, String.class);
        // NOTE: property placeholders are resolved at build time on the endpoint uri written in the route,
        // never on this header value, which carries message content (see CAMEL-24282 / CAMEL-24418)
        if (uriString != null) {
            URI uri = new URI(uriString);
            queryString = uri.getQuery();
        }
        if (queryString == null) {
            queryString = endpoint.getHttpURI().getRawQuery();
        }

        // compute what method to use either GET or POST
        HttpString answer;
        String m = exchange.getIn().getHeader(UndertowConstants.HTTP_METHOD, String.class);
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
                URI.create(UnsafeUriCharactersEncoder.encodeHttpURI(httpURI)));
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
                        httpURI.getFragment());
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
        } else {
            return httpURI;
        }
    }

    /**
     * Encodes a String body to send over HTTP in the charset that the Content-Type of the message declares, so that the
     * bytes match the header.
     *
     * @param  body        the String body
     * @param  contentType the Content-Type of the request or response being sent, may be <tt>null</tt>
     * @return             the encoded body, or <tt>null</tt> when the Content-Type declares no charset or one that is
     *                     not supported, in which case the body is converted as before (UTF-8 by default)
     */
    public static ByteBuffer toByteBuffer(String body, String contentType) {
        String name = getCharsetFromContentType(contentType);
        if (name == null) {
            return null;
        }
        try {
            return ByteBuffer.wrap(body.getBytes(Charset.forName(name)));
        } catch (IllegalArgumentException e) {
            // unknown or unsupported charset in the Content-Type
            return null;
        }
    }

    /**
     * Gets the charset parameter of a Content-Type. The parameter name is case-insensitive (RFC 9110), as in the other
     * HTTP components. Unlike {@link IOHelper#getCharsetNameFromContentType(String)}, there is no default: a
     * Content-Type without a charset gives <tt>null</tt>.
     *
     * @param  contentType the Content-Type, may be <tt>null</tt>
     * @return             the charset name, or <tt>null</tt> when the Content-Type declares no charset
     */
    public static String getCharsetFromContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        for (String parameter : contentType.split(";")) {
            parameter = parameter.trim();
            if (parameter.regionMatches(true, 0, "charset=", 0, 8)) {
                String name = IOHelper.normalizeCharset(parameter.substring(8));
                return ObjectHelper.isEmpty(name) ? null : name;
            }
        }
        return null;
    }

}
