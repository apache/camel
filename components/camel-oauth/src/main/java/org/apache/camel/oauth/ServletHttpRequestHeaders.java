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

import org.apache.camel.Message;
import org.apache.camel.http.common.HttpMessage;

/**
 * Reads request headers from a message of the servlet based HTTP consumers. Only loaded for a message of that type, see
 * {@link HttpRequestHeaders}.
 */
final class ServletHttpRequestHeaders {

    private ServletHttpRequestHeaders() {
    }

    static Optional<List<String>> values(Message message, String name) {
        var request = ((HttpMessage) message).getRequest();
        // a container that does not allow access to the headers returns null
        var values = request != null ? request.getHeaders(name) : null;
        return values != null ? Optional.of(Collections.list(values)) : Optional.empty();
    }
}
