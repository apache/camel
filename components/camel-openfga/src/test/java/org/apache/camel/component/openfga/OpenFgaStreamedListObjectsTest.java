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
package org.apache.camel.component.openfga;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import dev.openfga.sdk.api.model.ListObjectsRequest;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenFgaStreamedListObjectsTest {
    @Test
    void ownedTransportUsesSdkRequestAuthenticationAndClosesStalledBody() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var release = new java.util.concurrent.CountDownLatch(1);
        AtomicReference<String> payload = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        OpenFgaConfiguration configuration = configuration();
        configuration.setReadTimeout(200);
        configuration.setApiToken("stream-token");
        configuration.setApiUrl("http://127.0.0.1:" + server.getAddress().getPort());
        server.createContext("/stores/" + configuration.getStoreId() + "/streamed-list-objects", http -> {
            try (http) {
                payload.set(new String(http.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                authorization.set(http.getRequestHeaders().getFirst("Authorization"));
                http.getResponseHeaders().add("Content-Type", "application/json");
                http.sendResponseHeaders(200, 0);
                http.getResponseBody().write("{\"result\":{\"object\":\"document:one\"}}\n".getBytes(StandardCharsets.UTF_8));
                http.getResponseBody().flush();
                release.await(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        try (var context = new DefaultCamelContext()) {
            var endpoint = endpoint(context, configuration);
            endpoint.start();
            var producer = new OpenFgaProducer(endpoint);
            producer.start();
            try {
                Exchange exchange = new DefaultExchange(context);
                producer.process(exchange);
                try (var iterator = exchange.getMessage().getBody(OpenFgaObjectIterator.class)) {
                    assertThat(iterator.next()).isEqualTo("document:one");
                    assertThat(authorization.get()).isEqualTo("Bearer stream-token");
                    assertThat(payload.get()).contains("user:anne", "reader", "document");
                    assertThatThrownBy(iterator::hasNext).hasRootCauseInstanceOf(java.net.http.HttpTimeoutException.class);
                    assertThat(iterator.isComplete()).isFalse();
                }
            } finally {
                producer.stop();
                endpoint.stop();
            }
        } finally {
            release.countDown();
            server.stop(0);
        }
    }

    @Test
    void bodyRemainsLazyAfterProcessAndPreservesConfiguredQuery() throws Exception {
        AtomicReference<ListObjectsRequest> request = new AtomicReference<>();
        AtomicInteger opened = new AtomicInteger();
        OpenFgaConfiguration configuration = configuration();
        configuration.setAuthorizationModelId("01HQMVAJXYZ0000000000000001");
        configuration.setConsistency("HIGHER_CONSISTENCY");
        configuration.setConditionContext(Map.of("region", "eu"));
        configuration.setContextualTuples("user:anne,reader,document:one");
        configuration.setStreamingClient((store, query) -> {
            assertThat(store).isEqualTo(configuration.getStoreId());
            opened.incrementAndGet();
            request.set(query);
            return response(new ByteArrayInputStream(
                    "{\"result\":{\"object\":\"document:one\"}}\n"
                            .getBytes(StandardCharsets.UTF_8)));
        });
        try (var context = new DefaultCamelContext()) {
            var endpoint = endpoint(context, configuration);
            endpoint.start();
            var producer = new OpenFgaProducer(endpoint);
            producer.start();
            try {
                Exchange exchange = new DefaultExchange(context);
                producer.process(exchange);
                assertThat(opened).hasValue(0);
                try (OpenFgaObjectIterator iterator = exchange.getMessage().getBody(OpenFgaObjectIterator.class)) {
                    assertThat(iterator.next()).isEqualTo("document:one");
                    assertThat(request.get().getUser()).isEqualTo("user:anne");
                    assertThat(request.get().getRelation()).isEqualTo("reader");
                    assertThat(request.get().getType()).isEqualTo("document");
                    assertThat(request.get().getAuthorizationModelId()).isEqualTo(configuration.getAuthorizationModelId());
                    assertThat(request.get().getConsistency().toString()).isEqualTo("HIGHER_CONSISTENCY");
                    assertThat(request.get().getContext()).isEqualTo(Map.of("region", "eu"));
                    assertThat(request.get().getContextualTuples().getTupleKeys()).hasSize(1);
                    assertThat(iterator.hasNext()).isFalse();
                }
            } finally {
                producer.stop();
                endpoint.stop();
            }
        }
    }

    @Test
    void producerStopClosesActiveResponseWithoutClosingInjectedTransport() throws Exception {
        AtomicInteger bodyClosed = new AtomicInteger();
        AtomicInteger transportClosed = new AtomicInteger();
        InputStream body = new ByteArrayInputStream(
                "{\"result\":{\"object\":\"document:one\"}}\n"
                        .getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() throws IOException {
                bodyClosed.incrementAndGet();
                super.close();
            }
        };
        OpenFgaConfiguration configuration = configuration();
        configuration.setStreamingClient(new OpenFgaStreamingClient() {
            @Override
            public CompletableFuture<HttpResponse<InputStream>> open(String store, ListObjectsRequest request) {
                return response(body);
            }

            @Override
            public void close() {
                transportClosed.incrementAndGet();
            }
        });
        try (var context = new DefaultCamelContext()) {
            var endpoint = endpoint(context, configuration);
            endpoint.start();
            var producer = new OpenFgaProducer(endpoint);
            producer.start();
            Exchange exchange = new DefaultExchange(context);
            producer.process(exchange);
            var iterator = exchange.getMessage().getBody(OpenFgaObjectIterator.class);
            assertThat(iterator.next()).isEqualTo("document:one");
            producer.stop();
            endpoint.stop();
            assertThat(bodyClosed).hasValue(1);
            assertThat(transportClosed).hasValue(0);
            assertThat(iterator.isComplete()).isFalse();
            assertThatThrownBy(iterator::hasNext).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void injectedOpaqueClientRequiresExplicitStreamingTransport() throws Exception {
        OpenFgaConfiguration configuration = configuration();
        configuration.setOpenFgaClient(OpenFgaClientFactory.createClient(configuration, null));
        try (var context = new DefaultCamelContext()) {
            var endpoint = endpoint(context, configuration);
            assertThatThrownBy(endpoint::start).hasMessageContaining("requires streamingClient");
        }
    }

    @Test
    void positiveBoundsAreValidatedAtStartup() throws Exception {
        for (int option = 0; option < 3; option++) {
            OpenFgaConfiguration configuration = configuration();
            if (option == 0) {
                configuration.setStreamMaxResults(0);
            } else if (option == 1) {
                configuration.setStreamMaxLineLength(0);
            } else {
                configuration.setReadTimeout(0);
            }
            try (var context = new DefaultCamelContext()) {
                var endpoint = endpoint(context, configuration);
                assertThatThrownBy(endpoint::start).hasMessageContaining("must be positive");
            }
        }
    }

    private static OpenFgaConfiguration configuration() {
        OpenFgaConfiguration configuration = new OpenFgaConfiguration();
        configuration.setStoreId("01HQMVAJXYZ0000000000000000");
        configuration.setUser("user:anne");
        configuration.setRelation("reader");
        configuration.setType("document");
        return configuration;
    }

    private static OpenFgaEndpoint endpoint(DefaultCamelContext context, OpenFgaConfiguration configuration) {
        var endpoint = new OpenFgaEndpoint("openfga:streamedListObjects", new OpenFgaComponent(context), configuration);
        endpoint.setOperation(OpenFgaOperation.streamedListObjects);
        return endpoint;
    }

    @SuppressWarnings("unchecked")
    private static CompletableFuture<HttpResponse<InputStream>> response(InputStream body) {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.body()).thenReturn(body);
        when(response.statusCode()).thenReturn(200);
        return CompletableFuture.completedFuture(response);
    }
}
