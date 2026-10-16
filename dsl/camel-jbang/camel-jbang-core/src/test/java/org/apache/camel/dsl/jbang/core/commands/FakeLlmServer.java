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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * A local server that plays an LLM for the tests of the AI commands: Ollama (its models, and the answer of the model,
 * whole or streamed in two parts) and an OpenAI-compatible chat completion. It keeps the last question it answered.
 */
final class FakeLlmServer implements AutoCloseable {

    private final HttpServer server;
    private volatile String answer;
    private volatile JsonObject lastRequest;

    FakeLlmServer(String answer) throws IOException {
        this.answer = answer;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/tags", ex -> reply(ex, "{\"models\":[{\"name\":\"test\",\"size\":1000}]}"));
        server.createContext("/api/ps", ex -> reply(ex, "{\"models\":[]}"));
        server.createContext("/api/show", ex -> reply(ex, "{\"model_info\":{}}"));
        server.createContext("/api/generate", this::generate);
        server.createContext("/v1/models", ex -> reply(ex, "{\"data\":[{\"id\":\"test\"}]}"));
        server.createContext("/v1/chat/completions", this::chat);
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * The last question the server answered, as the client sent it.
     */
    JsonObject lastRequest() {
        return lastRequest;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void generate(HttpExchange exchange) throws IOException {
        JsonObject request = read(exchange);
        if (request.getBooleanOrDefault("stream", false)) {
            // the answer in two parts, a JSON document per line
            int half = answer.length() / 2;
            String first = new JsonObject(Map.of("response", answer.substring(0, half), "done", false)).toJson();
            String last = new JsonObject(Map.of("response", answer.substring(half), "done", true)).toJson();
            reply(exchange, first + "\n" + last + "\n");
        } else {
            reply(exchange, new JsonObject(Map.of("response", answer, "done", true)).toJson());
        }
    }

    private void chat(HttpExchange exchange) throws IOException {
        read(exchange);
        JsonObject message = new JsonObject(Map.of("role", "assistant", "content", answer));
        JsonObject choice = new JsonObject(Map.of("index", 0, "message", message));
        reply(exchange, new JsonObject(Map.of("choices", new JsonArray(List.of(choice)))).toJson());
    }

    private JsonObject read(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject request = Jsoner.deserialize(body, new JsonObject());
        lastRequest = request;
        return request;
    }

    private static void reply(HttpExchange exchange, String body) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
