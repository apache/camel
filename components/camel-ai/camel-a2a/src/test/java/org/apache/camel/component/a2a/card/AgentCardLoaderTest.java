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
package org.apache.camel.component.a2a.card;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.PushPromiseHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.component.a2a.model.AgentCard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentCardLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsFromClasspath() throws Exception {
        AgentCardLoader loader = new AgentCardLoader();
        AgentCard card = loader.load("classpath:cards/test-agent-card.json");

        assertThat(card).isNotNull();
        assertThat(card.getName()).isEqualTo("Weather Agent");
        assertThat(card.getDescription()).isEqualTo("Provides weather forecasts");
        assertThat(card.getVersion()).isEqualTo("1.0.0");
        assertThat(card.getProvider()).isNotNull();
        assertThat(card.getProvider().getName()).isEqualTo("ACME Corp");
        assertThat(card.getSkills()).hasSize(1);
        assertThat(card.getSkills().get(0).getId()).isEqualTo("get-forecast");
    }

    @Test
    void returnsEmptyCardForPlainName() throws Exception {
        AgentCardLoader loader = new AgentCardLoader();
        AgentCard card = loader.load("my-agent");

        assertThat(card).isNotNull();
        assertThat(card.getName()).isNull();
    }

    @Test
    void expandsPartialUrl() {
        String expanded = AgentCardLoader.expandWellKnownUrl("https://agent.example.com");
        assertThat(expanded).isEqualTo("https://agent.example.com/.well-known/agent-card.json");
    }

    @Test
    void keepsFullUrl() {
        String url = "https://agent.example.com/.well-known/agent-card.json";
        String expanded = AgentCardLoader.expandWellKnownUrl(url);
        assertThat(expanded).isEqualTo(url);
    }

    @Test
    void appendsWellKnownAfterSlash() {
        String expanded = AgentCardLoader.expandWellKnownUrl("https://agent.example.com/");
        assertThat(expanded).isEqualTo("https://agent.example.com/.well-known/agent-card.json");
    }

    @Test
    void returnsEmptyCardForNull() throws Exception {
        AgentCardLoader loader = new AgentCardLoader();
        AgentCard card = loader.load(null);

        assertThat(card).isNotNull();
        assertThat(card.getName()).isNull();
    }

    @Test
    void returnsEmptyCardForBlank() throws Exception {
        AgentCardLoader loader = new AgentCardLoader();
        AgentCard card = loader.load("   ");

        assertThat(card).isNotNull();
        assertThat(card.getName()).isNull();
    }

    @Test
    void throwsOnMalformedFileCard() throws Exception {
        Path card = tempDir.resolve("card.json");
        Files.writeString(card, "{not-json");

        AgentCardLoader loader = new AgentCardLoader();

        assertThatThrownBy(() -> loader.load("file:" + card))
                .isInstanceOf(Exception.class);
    }

    @Test
    void appliesConfiguredRequestTimeoutToHttpCardRequest() throws Exception {
        RecordingHttpClient client = new RecordingHttpClient();
        Duration timeout = Duration.ofMillis(1234);

        AgentCard card = new AgentCardLoader(client, timeout).load("http://agent.example.com");

        assertThat(card).isNotNull();
        assertThat(client.request().timeout()).contains(timeout);
        assertThat(client.request().uri()).hasToString("http://agent.example.com/.well-known/agent-card.json");
    }

    @Test
    void throwsOnRedirectingHttpCard() throws Exception {
        HttpServer server = startServer(exchange -> {
            exchange.getResponseHeaders().add("Location", "http://example.com/card.json");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        try {
            AgentCardLoader loader = new AgentCardLoader();

            assertThatThrownBy(() -> loader.load(serverUrl(server)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("redirected")
                    .hasMessageContaining("blocked");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void throwsOnMalformedHttpCard() throws Exception {
        HttpServer server = startServer(exchange -> respond(exchange, 200, "{not-json"));
        try {
            AgentCardLoader loader = new AgentCardLoader();

            assertThatThrownBy(() -> loader.load(serverUrl(server)))
                    .isInstanceOf(Exception.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void throwsOnOversizedHttpCard() throws Exception {
        HttpServer server = startServer(exchange -> respond(exchange, 200, "x".repeat(1024 * 1024 + 1)));
        try {
            AgentCardLoader loader = new AgentCardLoader();

            assertThatThrownBy(() -> loader.load(serverUrl(server)))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Agent card response exceeds maximum size");
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer startServer(ThrowingHttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } catch (Exception e) {
                exchange.close();
                if (e instanceof IOException ioException) {
                    throw ioException;
                }
                throw new IOException(e);
            }
        });
        server.start();
        return server;
    }

    private static String serverUrl(HttpServer server) {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ThrowingHttpHandler {
        void handle(HttpExchange exchange) throws Exception;
    }

    private static final class RecordingHttpClient extends HttpClient {
        private HttpRequest request;

        HttpRequest request() {
            return request;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            this.request = request;
            @SuppressWarnings("unchecked")
            T body = (T) new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));
            return new BasicHttpResponse<>(request, body);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, BodyHandler<T> responseBodyHandler,
                PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }

    private record BasicHttpResponse<T>(HttpRequest request, T body) implements HttpResponse<T> {
        @Override
        public int statusCode() {
            return 200;
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (name, value) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }
    }
}
