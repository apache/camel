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
package org.apache.camel.oauth;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.camel.Exchange;
import org.apache.camel.Message;

/**
 * Reads a header of the HTTP request a message was created from.
 * <p>
 * HTTP consumers also map query and form parameters into the message headers, so a message header is not necessarily
 * the request header of that name. Credentials are therefore read from the HTTP request itself whenever the message
 * carries it: platform-http on Vert.x, the servlet based consumers and camel-netty-http. The message types are matched
 * by name, and a transport specific class is only loaded for a message of its own type, so none of these transports has
 * to be on the classpath.
 * <p>
 * Any other message falls back to the message headers, leaving out a value that also appears in the query string of the
 * request: the consumer may have mapped it from a query parameter, and a credential does not belong in a url anyway
 * (RFC 6750 section 5.3).
 */
final class HttpRequestHeaders {

    private static final String VERTX_HTTP_MESSAGE = "org.apache.camel.component.platform.http.vertx.HttpMessage";
    private static final String SERVLET_HTTP_MESSAGE = "org.apache.camel.http.common.HttpMessage";
    private static final String NETTY_HTTP_MESSAGE = "org.apache.camel.component.netty.http.NettyHttpMessage";

    private HttpRequestHeaders() {
    }

    /**
     * The values of the named header of the HTTP request that the message comes from.
     *
     * @param  message the message
     * @param  name    the header name, matched ignoring case
     * @return         the header values, possibly none
     */
    static List<String> values(Message message, String name) {
        return requestHeaderValues(message, name).orElseGet(() -> messageHeaderValues(message, name));
    }

    private static Optional<List<String>> requestHeaderValues(Message message, String name) {
        for (Class<?> type = message.getClass(); type != null; type = type.getSuperclass()) {
            if (VERTX_HTTP_MESSAGE.equals(type.getName())) {
                return VertxHttpRequestHeaders.values(message, name);
            }
            if (SERVLET_HTTP_MESSAGE.equals(type.getName())) {
                return ServletHttpRequestHeaders.values(message, name);
            }
            if (NETTY_HTTP_MESSAGE.equals(type.getName())) {
                return NettyHttpRequestHeaders.values(message, name);
            }
        }
        return Optional.empty();
    }

    private static List<String> messageHeaderValues(Message message, String name) {
        List<String> values = new ArrayList<>();
        Object header = message.getHeader(name);
        if (header instanceof Iterable<?> iterable) {
            iterable.forEach(value -> {
                if (value != null) {
                    values.add(value.toString());
                }
            });
        } else if (header != null) {
            values.add(header.toString());
        }

        String query = message.getHeader(Exchange.HTTP_RAW_QUERY, String.class);
        if (query == null) {
            query = message.getHeader(Exchange.HTTP_QUERY, String.class);
        }
        if (query != null && !query.isEmpty() && !values.isEmpty()) {
            String rawQuery = query;
            String decodedQuery = decode(rawQuery);
            // a query string that cannot be decoded cannot be checked either, so none of the values is used
            values.removeIf(value -> decodedQuery == null || rawQuery.contains(value) || decodedQuery.contains(value));
        }
        return values;
    }

    private static String decode(String query) {
        try {
            return URLDecoder.decode(query, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
