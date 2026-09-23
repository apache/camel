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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/** HTTP transport shared by the endpoint's producer and predicates. */
final class TypeSafeAiClient implements AutoCloseable {
    private final HttpClient http;
    private final URI uri;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final int maxConcurrentRequests;
    private final Set<CompletableFuture<HttpResponse<String>>> pending = new HashSet<>();
    private int activeRequests;
    private boolean closed;

    TypeSafeAiClient(TypeSafeAiConfiguration configuration) {
        timeout = Duration.ofMillis(configuration.getRequestTimeout());
        maxConcurrentRequests = configuration.getMaxConcurrentRequests();
        apiKey = configuration.getApiKey();
        model = configuration.getModel();
        String baseUrl = configuration.getBaseUrl();
        uri = URI.create((baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl) + "/v1/systemone");
        http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    JsonObject evaluate(Map<String, Object> input) throws Exception {
        synchronized (this) {
            if (closed) {
                throw new IllegalStateException("TypeSafe AI client is stopped");
            }
            if (activeRequests >= maxConcurrentRequests) {
                throw new RejectedExecutionException(
                        "TypeSafe AI maxConcurrentRequests limit reached: " + maxConcurrentRequests);
            }
            activeRequests++;
        }
        try {
            return send(input);
        } finally {
            synchronized (this) {
                activeRequests--;
            }
        }
    }

    private JsonObject send(Map<String, Object> input) throws Exception {
        JsonObject request = TypeSafeAiJson.request(input, model);
        HttpRequest httpRequest = HttpRequest.newBuilder(uri).timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Jsoner.serialize(request), StandardCharsets.UTF_8)).build();
        CompletableFuture<HttpResponse<String>> future;
        synchronized (this) {
            if (closed) {
                throw new IllegalStateException("TypeSafe AI client is stopped");
            }
            future = http.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            pending.add(future);
        }
        try {
            // Keep the transport future itself: cancelling a derived future need not cancel the HTTP transfer.
            HttpResponse<String> response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new TypeSafeAiHttpException(
                        response.statusCode(),
                        response.headers().firstValue("x-typesafe-request-id").orElse(null),
                        response.headers().firstValue("Retry-After").orElse(null));
            }
            return TypeSafeAiJson.response(response.body(), request);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw e;
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof HttpTimeoutException cause) {
                TimeoutException timeoutException = new TimeoutException("TypeSafe AI request timed out");
                timeoutException.initCause(cause);
                throw timeoutException;
            }
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        } finally {
            synchronized (this) {
                pending.remove(future);
            }
        }
    }

    @Override
    public void close() throws Exception {
        Set<CompletableFuture<HttpResponse<String>>> active;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            active = new HashSet<>(pending);
        }
        active.forEach(future -> future.cancel(true));
        // HttpClient implements AutoCloseable on Java 21+. On Java 17 cancellation releases the active transfers.
        if (http instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }
}
