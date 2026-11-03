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
package org.apache.camel.component.azure.functions.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP client for invoking Azure Functions.
 */
public class FunctionsInvocationClient {

    private static final Logger LOG = LoggerFactory.getLogger(FunctionsInvocationClient.class);
    private static final String CONTENT_TYPE = "Content-Type";

    private final String functionKey;
    private final String hostKey;
    private final HttpClient httpClient;
    private final int readTimeout;

    public FunctionsInvocationClient(String functionKey, String hostKey,
                                     int connectionTimeout, int readTimeout) {
        this.functionKey = functionKey;
        this.hostKey = hostKey;
        this.readTimeout = readTimeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectionTimeout))
                .build();
    }

    /**
     * Invokes an Azure Function via HTTP.
     *
     * @param  url                  the function URL
     * @param  method               the HTTP method
     * @param  body                 the request body (can be null): a String, sent as UTF-8, or a byte[], sent as it is
     * @param  headers              additional headers to include
     * @return                      the invocation response
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the request is interrupted
     */
    public FunctionInvocationResponse invoke(String url, String method, Object body, Map<String, String> headers)
            throws IOException, InterruptedException {

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(readTimeout));

        // Add function key header if present
        String authKey = functionKey != null ? functionKey : hostKey;
        if (authKey != null) {
            requestBuilder.header("x-functions-key", authKey);
        }

        // Add custom headers
        if (headers != null) {
            headers.forEach(requestBuilder::header);
        }

        byte[] payload = toBytes(body);

        // Default content type when a body is sent and the headers do not set one
        if (payload != null && !hasHeader(headers, CONTENT_TYPE)) {
            requestBuilder.header(CONTENT_TYPE, "application/json");
        }

        // Set method and body
        HttpRequest.BodyPublisher bodyPublisher = payload != null
                ? HttpRequest.BodyPublishers.ofByteArray(payload)
                : HttpRequest.BodyPublishers.noBody();

        switch (method.toUpperCase()) {
            case "GET":
                requestBuilder.GET();
                break;
            case "POST":
                requestBuilder.POST(bodyPublisher);
                break;
            case "PUT":
                requestBuilder.PUT(bodyPublisher);
                break;
            case "DELETE":
                requestBuilder.DELETE();
                break;
            default:
                requestBuilder.method(method, bodyPublisher);
        }

        LOG.debug("Invoking Azure Function: {} {}", method, url);

        HttpResponse<String> response = httpClient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString());

        LOG.debug("Response status: {}", response.statusCode());

        return new FunctionInvocationResponse(
                response.statusCode(),
                response.body(),
                response.headers().map());
    }

    private static byte[] toBytes(Object body) {
        if (body == null) {
            return null;
        } else if (body instanceof byte[] bytes) {
            return bytes;
        } else if (body instanceof String string) {
            return string.getBytes(StandardCharsets.UTF_8);
        }
        // the producer converts a stream, a file and any other body to bytes with the type converter of Camel
        throw new IllegalArgumentException(
                "Cannot send a body of type " + body.getClass().getName() + ": send a String or a byte[]");
    }

    private static boolean hasHeader(Map<String, String> headers, String name) {
        // HTTP header names are case-insensitive
        return headers != null && headers.keySet().stream().anyMatch(name::equalsIgnoreCase);
    }

    /**
     * Response from Azure Function invocation.
     */
    public static class FunctionInvocationResponse {
        private final int statusCode;
        private final String body;
        private final Map<String, List<String>> headers;

        public FunctionInvocationResponse(int statusCode, String body, Map<String, List<String>> headers) {
            this.statusCode = statusCode;
            this.body = body;
            this.headers = headers;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getBody() {
            return body;
        }

        public Map<String, List<String>> getHeaders() {
            return headers;
        }
    }
}
