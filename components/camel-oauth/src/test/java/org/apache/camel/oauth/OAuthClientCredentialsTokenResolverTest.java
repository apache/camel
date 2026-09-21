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
package org.apache.camel.oauth;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.spi.OAuthClientConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OAuthClientCredentialsTokenResolverTest {

    private final List<TokenRequest> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private String endpoint;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // A unique path prevents static cache entries from leaking across tests if a port is reused.
        String path = "/token/" + UUID.randomUUID();
        server.createContext(path, this::handleTokenRequest);
        server.start();
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void identicalConfigurationSharesTokenAcrossResolvers() {
        assertEquals("token-1", new OAuthClientCredentialsTokenResolver().resolveToken(config("read")));
        assertEquals("token-1", new OAuthClientCredentialsTokenResolver().resolveToken(config("read")));
        assertEquals(1, requests.size());
        assertEquals("read", requests.get(0).scope());
    }

    @ParameterizedTest
    @MethodSource("differentScopes")
    void differentScopesUseSeparateTokens(String firstScope, String secondScope) {
        assertSeparateTokens(config(firstScope), config(secondScope));
        assertEquals(firstScope, requests.get(0).scope());
        assertEquals(secondScope, requests.get(1).scope());
    }

    static Stream<Arguments> differentScopes() {
        return Stream.of(
                Arguments.of("read", "write"),
                Arguments.of("write", "read"),
                Arguments.of(null, "read"),
                Arguments.of("read", null),
                Arguments.of("read", "READ"),
                Arguments.of("read write", "write read"));
    }

    @Test
    void differentClientsUseSeparateTokens() {
        assertSeparateTokens(config("read"), config("read").setClientId("other-client"));
        assertEquals("client:secret", requests.get(0).credentials());
        assertEquals("other-client:secret", requests.get(1).credentials());
    }

    @Test
    void differentEndpointsUseSeparateTokens() {
        assertSeparateTokens(config("read"), config("read").setTokenEndpoint(endpoint + "/other"));
        assertEquals(endpoint, requests.get(0).endpoint());
        assertEquals(endpoint + "/other", requests.get(1).endpoint());
    }

    @Test
    void differentSecretsUseSeparateTokens() {
        assertSeparateTokens(config("read"), config("read").setClientSecret("other-secret"));
        assertEquals("client:secret", requests.get(0).credentials());
        assertEquals("client:other-secret", requests.get(1).credentials());
    }

    @Test
    void disabledCacheAlwaysAcquiresTokenWithoutReplacingCachedToken() {
        OAuthClientCredentialsTokenResolver resolver = new OAuthClientCredentialsTokenResolver();
        assertEquals("token-1", resolver.resolveToken(config("read")));
        OAuthClientConfig uncached = config("read").setCacheTokens(false);
        assertEquals("token-2", resolver.resolveToken(uncached));
        assertEquals("token-3", resolver.resolveToken(uncached));
        assertEquals("token-1", resolver.resolveToken(config("read")));
        assertEquals(3, requests.size());
    }

    @Test
    void omittedScopesShareToken() {
        OAuthClientCredentialsTokenResolver resolver = new OAuthClientCredentialsTokenResolver();
        assertEquals("token-1", resolver.resolveToken(config(null)));
        assertEquals("token-1", resolver.resolveToken(config("")));
        assertEquals("token-1", resolver.resolveToken(config(" \t")));
        assertEquals(1, requests.size());
        assertNull(requests.get(0).scope());
    }

    private OAuthClientConfig config(String scope) {
        return new OAuthClientConfig().setTokenEndpoint(endpoint).setClientId("client")
                .setClientSecret("secret").setScope(scope);
    }

    private void assertSeparateTokens(OAuthClientConfig first, OAuthClientConfig second) {
        OAuthClientCredentialsTokenResolver firstResolver = new OAuthClientCredentialsTokenResolver();
        OAuthClientCredentialsTokenResolver secondResolver = new OAuthClientCredentialsTokenResolver();
        assertEquals("token-1", firstResolver.resolveToken(first));
        assertEquals("token-2", secondResolver.resolveToken(second));
        assertEquals("token-1", secondResolver.resolveToken(first));
        assertEquals("token-2", firstResolver.resolveToken(second));
        assertEquals(2, requests.size());
    }

    private void handleTokenRequest(HttpExchange exchange) throws IOException {
        Map<String, String> form = new HashMap<>();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            form.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
        }
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        String credentials = new String(
                Base64.getDecoder().decode(authorization.substring("Basic ".length())),
                StandardCharsets.UTF_8);
        requests.add(new TokenRequest(
                "http://127.0.0.1:" + server.getAddress().getPort() + exchange.getRequestURI(),
                credentials, form.get("scope")));
        byte[] response = ("{\"access_token\":\"token-" + requests.size() + "\",\"expires_in\":3600}")
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        try (var outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }

    private record TokenRequest(String endpoint, String credentials, String scope) {
    }
}
