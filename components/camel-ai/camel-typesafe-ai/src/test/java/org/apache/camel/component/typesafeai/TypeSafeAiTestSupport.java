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
package org.apache.camel.component.typesafeai;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

abstract class TypeSafeAiTestSupport {
    CamelContext context;
    ProducerTemplate template;
    HttpServer server;
    ExecutorService serverExecutor;
    final ConcurrentLinkedQueue<JsonObject> requests = new ConcurrentLinkedQueue<>();
    final ConcurrentLinkedQueue<String> authorization = new ConcurrentLinkedQueue<>();
    final CountDownLatch release = new CountDownLatch(1);
    volatile Function<JsonObject, String> respond = request -> noulResponse(0.9);
    volatile int status = 200;
    volatile boolean holdHeaders;
    volatile boolean holdBody;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.createContext("/v1/systemone", this::handle);
        server.start();
        context = new DefaultCamelContext();
        TypeSafeAiComponent component = new TypeSafeAiComponent();
        component.getConfiguration().setApiKey("test-key");
        component.getConfiguration().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        component.getConfiguration().setModel("jev-1.13.0");
        context.addComponent("typesafe-ai", component);
        context.start();
        template = context.createProducerTemplate();
    }

    @AfterEach
    void stop() throws Exception {
        release.countDown();
        if (template != null) {
            template.stop();
        }
        if (context != null) {
            context.stop();
        }
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            JsonObject request = (JsonObject) Jsoner.deserialize(
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requests.add(request);
            authorization.add(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = respond.apply(request).getBytes(StandardCharsets.UTF_8);
            if (holdHeaders) {
                release.await(10, TimeUnit.SECONDS);
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Retry-After", "2");
            exchange.getResponseHeaders().set("Location", "/redirected");
            exchange.sendResponseHeaders(status, body.length);
            if (holdBody) {
                exchange.getResponseBody().write(body, 0, 1);
                exchange.getResponseBody().flush();
                release.await(10, TimeUnit.SECONDS);
                exchange.getResponseBody().write(body, 1, body.length - 1);
            } else {
                exchange.getResponseBody().write(body);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // A timed-out client cancels its request and closes the connection.
            if (!holdHeaders && !holdBody) {
                throw new IOException(e);
            }
        } finally {
            exchange.close();
        }
    }

    static Map<String, Object> noulQuestion(Object instructions) {
        return Map.of("type", "noul", "instructions", instructions);
    }

    static Map<String, Object> request(Object state) {
        return Map.of("state", state, "questions", Map.of("predicate", noulQuestion("Is a refund requested?")));
    }

    static Map<String, Object> mixedRequest(Object state) {
        return Map.of("state", state, "questions", mixedQuestions());
    }

    Predicate predicate(String endpoint, Expression state, String question, double threshold) {
        return context.resolveLanguage("typesafe-ai").createPredicate(question,
                new Object[] { endpoint, threshold, null, null, state });
    }

    static String noulResponse(double probability) {
        return result(Map.of("predicate", Map.of("type", "noul", "noul", probability)));
    }

    static String result(Map<String, ?> answers) {
        return Jsoner.serialize(Map.of("model", "jev-1.13.0", "answers", answers,
                "usage", Map.of("input_tokens", 100, "output_tokens", 20)));
    }

    static Map<String, Object> mixedQuestions() {
        return Map.of("refund", Map.of("type", "noul", "instructions", Map.of("question", "Is a refund requested?"),
                "criteria", Map.of("true", List.of("Explicit request", "Return money"), "false", "No request")),
                "department", Map.of("type", "choice", "instructions", "Which department?",
                        "criteria", Map.of("billing", "Refunds", "technical", "Faults", "other", "Anything else")),
                "urgency",
                Map.of("type", "score", "instructions", "How urgent?", "criteria", List.of("Routine", "Urgent", "Critical")));
    }

    static String mixedResponse() {
        return result(Map.of("refund", Map.of("type", "noul", "noul", 0.9),
                "department", Map.of("type", "choice", "choice", "billing", "confidence", 0.8,
                        "probabilities", Map.of("billing", 0.9, "technical", 0.1, "other", 0)),
                "urgency", Map.of("type", "score", "score", 1.2, "confidence", 0.7,
                        "probabilities", Map.of("0", 0, "1", 0.8, "2", 0.2),
                        "legend", Map.of("0", "Routine", "1", "Urgent", "2", "Critical"))));
    }
}
