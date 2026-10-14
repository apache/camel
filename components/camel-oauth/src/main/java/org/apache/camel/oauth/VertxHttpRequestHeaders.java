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
import org.apache.camel.component.platform.http.vertx.HttpMessage;

/**
 * Reads request headers from a platform-http message on Vert.x. Only loaded for a message of that type, see
 * {@link HttpRequestHeaders}.
 */
final class VertxHttpRequestHeaders {

    private VertxHttpRequestHeaders() {
    }

    static Optional<List<String>> values(Message message, String name) {
        var request = ((HttpMessage) message).getRequest();
        return request != null ? Optional.of(request.headers().getAll(name)) : Optional.empty();
    }
}
