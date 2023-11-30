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
package org.apache.camel.component.vertx.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

public final class VertxHttpHelper {

    private VertxHttpHelper() {
        // Utility class
    }

    /**
     * Resolves a HTTP URI query string from the given exchange message headers
     */
    public static String resolveQueryString(Exchange exchange) throws URISyntaxException {
        Message message = exchange.getMessage();
        String queryString = (String) message.removeHeader(Exchange.REST_HTTP_QUERY);
        if (ObjectHelper.isEmpty(queryString)) {
            queryString = message.getHeader(VertxHttpConstants.HTTP_QUERY, String.class);
        }

        String uriString = message.getHeader(VertxHttpConstants.HTTP_URI, String.class);
        uriString = exchange.getContext().resolvePropertyPlaceholders(uriString);

        if (uriString != null) {
            uriString = UnsafeUriCharactersEncoder.encodeHttpURI(uriString);
            URI uri = new URI(uriString);
            queryString = uri.getQuery();
        }

        return queryString;
    }

    /**
     * Resolves a HTTP URI and path string from the given exchange message headers
     */
    public static URI resolveHttpURI(Exchange exchange, VertxHttpEndpoint endpoint) throws URISyntaxException {
        Message message = exchange.getMessage();
        String uri = (String) message.removeHeader(Exchange.REST_HTTP_URI);

        if (ObjectHelper.isEmpty(uri)) {
            uri = message.getHeader(VertxHttpConstants.HTTP_URI, String.class);
        }

        if (uri == null) {
            uri = endpoint.getConfiguration().getHttpUri().toASCIIString();
        }

        // Resolve property placeholders that may be present in the URI
        uri = exchange.getContext().resolvePropertyPlaceholders(uri);

        // Append HTTP_PATH header value if is present
        String path = message.getHeader(VertxHttpConstants.HTTP_PATH, String.class);
        if (ObjectHelper.isNotEmpty(path)) {
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

        uri = UnsafeUriCharactersEncoder.encodeHttpURI(uri);

        return new URI(uri);
    }

    /**
     * Writes the given target object to an {@link ObjectOutputStream}
     */
    public static void writeObjectToStream(OutputStream stream, Object target) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(stream);
        try {
            oos.writeObject(target);
            oos.flush();
        } finally {
            IOHelper.close(oos);
        }
    }

    /**
     * Deserializes an object from the given {@link InputStream}
     */
    public static Object deserializeJavaObjectFromStream(InputStream is) throws ClassNotFoundException, IOException {
        if (is == null) {
            return null;
        }

        Object answer;
        ObjectInputStream ois = new ObjectInputStream(is);
        try {
            answer = ois.readObject();
        } finally {
            IOHelper.close(ois);
        }

        return answer;
    }

    /**
     * Retrieves the charset from the exchange Content-Type header, or falls back to the CamelCharsetName exchange
     * property when not available
     */
    public static String getCharsetFromExchange(Exchange exchange) {
        String charset = null;
        if (exchange != null) {
            String contentType = exchange.getMessage().getHeader(VertxHttpConstants.CONTENT_TYPE, String.class);
            charset = IOHelper.getCharsetNameFromContentType(contentType);
            if (ObjectHelper.isEmpty(charset)) {
                charset = exchange.getProperty(ExchangePropertyKey.CHARSET_NAME, String.class);
            }
        }
        return charset;
    }
}
