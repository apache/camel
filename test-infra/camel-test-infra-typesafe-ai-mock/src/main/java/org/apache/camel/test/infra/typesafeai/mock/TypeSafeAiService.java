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
package org.apache.camel.test.infra.typesafeai.mock;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A local System One HTTP mock, or an existing compatible service when its base URL is supplied. Configure
 * {@code TYPESAFE_AI_BASE_URL}, {@code TYPESAFE_AI_API_KEY}, and optionally {@code TYPESAFE_AI_MODEL} to run the same
 * tests against a real API. The Laya equivalents {@code LAYA_BASE_URL} and {@code LAYA_API_KEY} are also accepted.
 *
 * @since 4.23
 */
public class TypeSafeAiService implements BeforeEachCallback, AfterEachCallback {
    private static final Logger LOG = LoggerFactory.getLogger(TypeSafeAiService.class);

    private final String remoteBaseUrl;
    private final String apiKey;
    private final String model;
    private final List<JsonObject> requests = new CopyOnWriteArrayList<>();
    private volatile Function<JsonObject, Map<String, ?>> responder = TypeSafeAiService::defaultAnswers;
    private HttpServer server;
    private ExecutorService executor;

    /**
     * Creates a service using environment configuration, or a local mock when absent.
     *
     * @since 4.23
     */
    public TypeSafeAiService() {
        this(firstConfigured("TYPESAFE_AI_BASE_URL", "LAYA_BASE_URL"),
             firstConfigured("TYPESAFE_AI_API_KEY", "LAYA_API_KEY"),
             firstConfigured("TYPESAFE_AI_MODEL", "LAYA_MODEL"));
    }

    /**
     * Explicit configuration is useful when a test starts its own compatible server.
     *
     * @since 4.23
     */
    public TypeSafeAiService(String baseUrl, String apiKey, String model) {
        remoteBaseUrl = baseUrl;
        if (baseUrl != null && !baseUrl.isBlank() && (apiKey == null || apiKey.isBlank())) {
            throw new IllegalArgumentException("TYPESAFE_AI_API_KEY is required with TYPESAFE_AI_BASE_URL");
        }
        this.apiKey = apiKey == null || apiKey.isBlank() ? "test-key" : apiKey;
        this.model = model == null || model.isBlank() ? "jev-latest" : model;
    }

    /**
     * Returns the URL used by the component.
     *
     * @since 4.23
     */
    public String getBaseUrl() {
        if (isRemote()) {
            return remoteBaseUrl;
        }
        if (server == null) {
            throw new IllegalStateException("TypeSafe AI mock has not started");
        }
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * Returns the configured API key or the local mock's test key.
     *
     * @since 4.23
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * Returns the configured model name.
     *
     * @since 4.23
     */
    public String getModel() {
        return model;
    }

    /**
     * Whether an existing API is used instead of a local mock.
     *
     * @since 4.23
     */
    public boolean isRemote() {
        return remoteBaseUrl != null && !remoteBaseUrl.isBlank();
    }

    /**
     * Returns requests received by the local mock. A remote service is not recorded.
     *
     * @since 4.23
     */
    public List<JsonObject> getRequests() {
        return List.copyOf(requests);
    }

    /**
     * Supplies answer objects keyed by the question names in the incoming request.
     *
     * @since 4.23
     */
    public void setResponder(Function<JsonObject, Map<String, ?>> responder) {
        this.responder = Objects.requireNonNull(responder);
    }

    @Override
    public void beforeEach(ExtensionContext context) throws IOException {
        requests.clear();
        if (isRemote()) {
            return;
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/systemone", this::handle);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        if (server != null) {
            server.stop(1);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equals(exchange.getRequestMethod())) {
                send(exchange, 405, "{}");
                return;
            }
            if (!("Bearer " + apiKey).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                send(exchange, 401, "{}");
                return;
            }
            Object parsed;
            try {
                parsed = Jsoner.deserialize(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            } catch (DeserializationException e) {
                send(exchange, 400, "{}");
                return;
            }
            if (!(parsed instanceof JsonObject request) || !(request.get("questions") instanceof Map<?, ?> questions)
                    || questions.isEmpty() || !(request.get("model") instanceof String)) {
                send(exchange, 400, "{}");
                return;
            }
            requests.add(request);
            Map<String, ?> answers;
            try {
                answers = responder.apply(request);
            } catch (RuntimeException e) {
                LOG.error("TypeSafe AI mock responder failed", e);
                send(exchange, 500, "{}");
                return;
            }
            if (!answers.keySet().equals(questions.keySet())) {
                send(exchange, 500, "{}");
                return;
            }
            send(exchange, 200, Jsoner.serialize(Map.of("model", request.get("model"), "answers", answers,
                    "usage", Map.of("input_tokens", 0, "output_tokens", 0))));
        } finally {
            exchange.close();
        }
    }

    private static void send(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private static Map<String, ?> defaultAnswers(JsonObject request) {
        Map<String, Object> answers = new LinkedHashMap<>();
        Map<?, ?> questions = (Map<?, ?>) request.get("questions");
        questions.forEach((name, definition) -> {
            Map<?, ?> question = (Map<?, ?>) definition;
            Object type = question.get("type");
            if ("noul".equals(type)) {
                answers.put((String) name, Map.of("type", "noul", "noul", 0.9));
            } else if ("choice".equals(type)) {
                Map<?, ?> criteria = (Map<?, ?>) question.get("criteria");
                Map<String, Double> probabilities = new LinkedHashMap<>();
                criteria.keySet().forEach(key -> probabilities.put((String) key, probabilities.isEmpty() ? 1.0 : 0.0));
                answers.put((String) name, Map.of("type", "choice", "choice", probabilities.keySet().iterator().next(),
                        "confidence", 1.0, "probabilities", probabilities));
            } else if ("score".equals(type)) {
                List<?> criteria = (List<?>) question.get("criteria");
                Map<String, Double> probabilities = new LinkedHashMap<>();
                Map<String, Object> legend = new LinkedHashMap<>();
                for (int i = 0; i < criteria.size(); i++) {
                    probabilities.put(Integer.toString(i), i == 0 ? 1.0 : 0.0);
                    legend.put(Integer.toString(i), criteria.get(i));
                }
                answers.put((String) name, Map.of("type", "score", "score", 0, "confidence", 1.0,
                        "probabilities", probabilities, "legend", legend));
            } else {
                throw new IllegalArgumentException("Unsupported question type: " + type);
            }
        });
        return answers;
    }

    private static String firstConfigured(String primary, String fallback) {
        String value = System.getenv(primary);
        if (value == null || value.isBlank()) {
            value = System.getenv(fallback);
        }
        return value;
    }
}
