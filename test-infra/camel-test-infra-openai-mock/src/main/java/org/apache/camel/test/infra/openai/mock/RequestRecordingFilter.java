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
package org.apache.camel.test.infra.openai.mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

/**
 * Records every request before it reaches the request handlers. The body is read once and handed on as a fresh stream,
 * so the handlers read it exactly as before.
 */
final class RequestRecordingFilter extends Filter {

    private final List<RecordedRequest> requests;

    RequestRecordingFilter(List<RecordedRequest> requests) {
        this.requests = requests;
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        byte[] body;
        try (InputStream is = exchange.getRequestBody()) {
            body = is.readAllBytes();
        }
        requests.add(new RecordedRequest(
                exchange.getRequestMethod(), exchange.getRequestURI().getPath(), new String(body, StandardCharsets.UTF_8)));
        exchange.setStreams(new ByteArrayInputStream(body), exchange.getResponseBody());
        chain.doFilter(exchange);
    }

    @Override
    public String description() {
        return "Records the requests received by the OpenAI mock";
    }
}
