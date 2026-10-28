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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.http.common.HttpMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads request headers from a message of the servlet based HTTP consumers. Only loaded for a message of such a type,
 * see {@link HttpRequestHeaders}.
 */
final class ServletHttpRequestHeaders {

    private static final Logger LOG = LoggerFactory.getLogger(ServletHttpRequestHeaders.class);

    private ServletHttpRequestHeaders() {
    }

    /**
     * Reads the headers of the request of a camel-http-common {@link HttpMessage} (camel-servlet, camel-jetty).
     */
    static Optional<List<String>> values(Message message, String name) {
        return values(((HttpMessage) message).getRequest(), name);
    }

    /**
     * Reads the headers of the request of a platform-http message on Spring Boot. The request comes from the
     * {@code getRequest()} method of the message, which camel-oauth does not depend on, or else from the
     * {@link Exchange#HTTP_SERVLET_REQUEST} header that the message also sets, but that a route may remove. Without a
     * request no value is read at all, rather than the message headers, which also hold the form parameters.
     */
    static Optional<List<String>> valuesOfSpringBootMessage(Message message, String name) {
        var request = springBootRequest(message);
        if (request == null) {
            LOG.debug("No HTTP request on the platform-http message, so no {} header is read", name);
            return Optional.of(List.of());
        }
        return Optional.of(values(request, name).orElse(List.of()));
    }

    private static HttpServletRequest springBootRequest(Message message) {
        try {
            if (message.getClass().getMethod("getRequest").invoke(message) instanceof HttpServletRequest request) {
                return request;
            }
        } catch (ReflectiveOperationException | SecurityException e) {
            LOG.debug("Cannot call getRequest() on {}, reading the {} header instead", message.getClass().getName(),
                    Exchange.HTTP_SERVLET_REQUEST, e);
        }
        return message.getHeader(Exchange.HTTP_SERVLET_REQUEST) instanceof HttpServletRequest request ? request : null;
    }

    private static Optional<List<String>> values(HttpServletRequest request, String name) {
        // a container that does not allow access to the headers returns null
        var values = request != null ? request.getHeaders(name) : null;
        return values != null ? Optional.of(Collections.list(values)) : Optional.empty();
    }
}
