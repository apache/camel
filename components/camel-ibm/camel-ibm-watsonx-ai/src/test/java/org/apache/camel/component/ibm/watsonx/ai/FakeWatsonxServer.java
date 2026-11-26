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
package org.apache.camel.component.ibm.watsonx.ai;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.watsonx.ai.core.auth.Authenticator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A JDK HttpServer that plays watsonx.ai for the unit tests: it records every request and answers with the responses
 * queued for its path, the last one of a path answering every further request.
 */
final class FakeWatsonxServer implements AutoCloseable {

    static final Authenticator TEST_AUTHENTICATOR = new Authenticator() {
        @Override
        public String token() {
            return "test-token";
        }

        @Override
        public CompletableFuture<String> tokenAsync() {
            return CompletableFuture.completedFuture(token());
        }

        @Override
        public String scheme() {
            return "Bearer";
        }
    };

    private static final ObjectMapper MAPPER = new ObjectMapper();

    record Request(String method, String path, String query, String authorization, String body) {

        JsonNode json() {
            try {
                return MAPPER.readTree(body);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private record Response(int status, String contentType, String body) {
    }

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Deque<Response>> responses = new ConcurrentHashMap<>();

    private FakeWatsonxServer(HttpServer server) {
        this.server = server;
    }

    static FakeWatsonxServer start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        FakeWatsonxServer fake = new FakeWatsonxServer(server);
        server.createContext("/", fake::handle);
        server.start();
        return fake;
    }

    String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    FakeWatsonxServer respond(String path, String json) {
        return respond(path, 200, "application/json", json);
    }

    FakeWatsonxServer respondStream(String path, String events) {
        return respond(path, 200, "text/event-stream", events);
    }

    FakeWatsonxServer respond(String path, int status, String contentType, String body) {
        responses.computeIfAbsent(path, p -> new ArrayDeque<>()).add(new Response(status, contentType, body));
        return this;
    }

    List<Request> requests() {
        return List.copyOf(requests);
    }

    List<Request> requests(String path) {
        return requests.stream().filter(r -> r.path().equals(path)).toList();
    }

    Request lastRequest(String path) {
        List<Request> matching = requests(path);
        if (matching.isEmpty()) {
            throw new AssertionError("No request to " + path + " but to " + requests.stream().map(Request::path).toList());
        }
        return matching.get(matching.size() - 1);
    }

    void reset() {
        requests.clear();
        responses.clear();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requests.add(new Request(
                exchange.getRequestMethod(), path, exchange.getRequestURI().getRawQuery(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));

        Response response;
        Deque<Response> queue = responses.get(path);
        synchronized (this) {
            response = queue == null ? null : queue.size() > 1 ? queue.poll() : queue.peek();
        }
        if (response == null) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", response.contentType());
        exchange.sendResponseHeaders(response.status(), bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
