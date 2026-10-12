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

import java.util.List;
import java.util.Optional;

import org.apache.camel.Message;

/**
 * Reads a header of the HTTP request a message was created from.
 * <p>
 * HTTP consumers also map query and form parameters into the message headers, so a message header is not necessarily
 * the request header of that name. Credentials are therefore read from the HTTP request itself whenever the message
 * carries it: platform-http on Vert.x, and the servlet based consumers. The message types are matched by name, and a
 * transport specific class is only loaded for a message of its own type, so neither transport has to be on the
 * classpath.
 */
final class HttpRequestHeaders {

    private static final String VERTX_HTTP_MESSAGE = "org.apache.camel.component.platform.http.vertx.HttpMessage";
    private static final String SERVLET_HTTP_MESSAGE = "org.apache.camel.http.common.HttpMessage";

    private HttpRequestHeaders() {
    }

    /**
     * The values of the named header of the HTTP request that the message carries.
     *
     * @param  message the message
     * @param  name    the header name, matched ignoring case
     * @return         the header values, possibly none, or empty when the message carries no HTTP request that can be
     *                 read, in which case the message headers are all there is
     */
    static Optional<List<String>> values(Message message, String name) {
        for (Class<?> type = message.getClass(); type != null; type = type.getSuperclass()) {
            if (VERTX_HTTP_MESSAGE.equals(type.getName())) {
                return VertxHttpRequestHeaders.values(message, name);
            }
            if (SERVLET_HTTP_MESSAGE.equals(type.getName())) {
                return ServletHttpRequestHeaders.values(message, name);
            }
        }
        return Optional.empty();
    }
}
