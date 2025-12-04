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

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Dispatcher that routes incoming requests to the appropriate request handler.
 */
public class OpenAIMockServerHandler implements HttpHandler {
    private final RequestHandler requestHandler;

    public OpenAIMockServerHandler(List<MockExpectation> expectations, ObjectMapper objectMapper) {
        this.requestHandler = new RequestHandler(expectations, objectMapper);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            try {
                byte[] response = requestHandler.handleRequest(exchange).getBytes();
                if (exchange.getResponseCode() == -1) {
                    exchange.sendResponseHeaders(200, response.length);
                }
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
