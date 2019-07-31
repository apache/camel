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
package org.apache.camel.component.ahc.helper;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.component.ahc.AhcEndpoint;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

/**
 *
 */
public final class AhcHelper {

    private AhcHelper() {
        // Helper class
    }

    /**
     * Writes the given object as response body to the output stream
     *
     * @param stream output stream
     * @param target   object to write
     * @throws java.io.IOException is thrown if error writing
     */
    public static void writeObjectToStream(OutputStream stream, Object target) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(stream);
        oos.writeObject(target);
        oos.flush();
        IOHelper.close(oos);
    }

    /**
     * Deserializes the input stream to a Java object
     *
     * @param is input stream for the Java object
     * @return the java object, or <tt>null</tt> if input stream was <tt>null</tt>
     * @throws ClassNotFoundException is thrown if class not found
     * @throws IOException can be thrown
     */
    public static Object deserializeJavaObjectFromStream(InputStream is) throws ClassNotFoundException, IOException {
        if (is == null) {
            return null;
        }

        Object answer = null;
        ObjectInputStream ois = new ObjectInputStream(is);
        try {
            answer = ois.readObject();
        } finally {
            IOHelper.close(ois);
        }

        return answer;
    }

    public static void setCharsetFromContentType(String contentType, Exchange exchange) {
        if (contentType != null) {
            exchange.setProperty(Exchange.CHARSET_NAME, IOHelper.getCharsetNameFromContentType(contentType));
        }
    }

    /**
     * Creates the URL to invoke.
     *
     * @param exchange the exchange
     * @param endpoint the endpoint
     * @return the URL to invoke
     * @throws java.net.URISyntaxException is thrown if the URL is invalid
     * @throws UnsupportedEncodingException 
     */
    public static String createURL(Exchange exchange, AhcEndpoint endpoint) throws URISyntaxException, UnsupportedEncodingException {
        String url = doCreateURL(exchange, endpoint);
        return URISupport.normalizeUri(url);
    }

    private static String doCreateURL(Exchange exchange, AhcEndpoint endpoint) {
        String uri = null;
        if (!(endpoint.isBridgeEndpoint())) {
            uri = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
        }
        if (uri == null) {
            uri = endpoint.getHttpUri().toASCIIString();
        }

        // resolve placeholders in uri
        try {
            uri = exchange.getContext().resolvePropertyPlaceholders(uri);
        } catch (Exception e) {
            throw new RuntimeExchangeException("Cannot resolve property placeholders with uri: " + uri, exchange, e);
        }

        // append HTTP_PATH to HTTP_URI if it is provided in the header
        String path = exchange.getIn().getHeader(Exchange.HTTP_PATH, String.class);
        if (path != null) {
            if (path.startsWith("/")) {
                URI baseURI;
                String baseURIString = exchange.getIn().getHeader(Exchange.HTTP_BASE_URI, String.class);
                try {
                    if (baseURIString == null) {
                        if (exchange.getFromEndpoint() != null) {
                            baseURIString = exchange.getFromEndpoint().getEndpointUri();
                        } else {
                            // will set a default one for it
                            baseURIString = "/";
                        }
                    }
                    baseURI = new URI(baseURIString);
                    String basePath = baseURI.getPath();
                    if (path.startsWith(basePath)) {
                        path = path.substring(basePath.length());
                        if (path.startsWith("/")) {
                            path = path.substring(1);
                        }
                    } else {
                        throw new RuntimeExchangeException("Cannot analyze the Exchange.HTTP_PATH header, due to: cannot find the right HTTP_BASE_URI", exchange);
                    }
                } catch (Throwable t) {
                    throw new RuntimeExchangeException("Cannot analyze the Exchange.HTTP_PATH header, due to: " + t.getMessage(), exchange, t);
                }
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
    public static URI createURI(Exchange exchange, String url, AhcEndpoint endpoint) throws URISyntaxException {
        URI uri = new URI(url);
        // is a query string provided in the endpoint URI or in a header (header overrules endpoint)
        String queryString = exchange.getIn().getHeader(Exchange.HTTP_QUERY, String.class);
        if (queryString == null) {
            queryString = endpoint.getHttpUri().getRawQuery();
        }
        // We should user the query string from the HTTP_URI header
        if (queryString == null) {
            queryString = uri.getQuery();
        }
        if (queryString != null) {
            // need to encode query string
            queryString = UnsafeUriCharactersEncoder.encodeHttpURI(queryString);
            uri = URISupport.createURIWithQuery(uri, queryString);
        }
        return uri;
    }

}
