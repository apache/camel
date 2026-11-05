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
package org.apache.camel.component.cyberark.vault.client.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.component.cyberark.vault.client.ConjurClient;
import org.apache.camel.component.cyberark.vault.client.ConjurClientFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A 401 from Conjur makes the client authenticate again and retry once, and only when it has an API key or a password
 * to get a new token. A fake Conjur answers the queued statuses to the secret requests, and then succeeds: a client
 * that retries more than it should gets the secret instead of the expected failure.
 */
class ConjurClientReauthenticationTest {

    private static final String ACCOUNT = "myaccount";
    private static final String SECRET_ID = "db/password";

    private final AtomicInteger logins = new AtomicInteger();
    private final List<String> secretRequestTokens = new CopyOnWriteArrayList<>();
    private final List<String> storedValues = new CopyOnWriteArrayList<>();
    private final Queue<Integer> secretStatuses = new ConcurrentLinkedQueue<>();

    private HttpServer server;
    private String url;

    @BeforeEach
    void startConjur() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/authn/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            respond(exchange, 200, "token-" + logins.incrementAndGet());
        });
        server.createContext("/secrets/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            secretRequestTokens.add(tokenOf(exchange));
            Integer status = secretStatuses.poll();
            if ("POST".equals(exchange.getRequestMethod())) {
                storedValues.add(body);
                respond(exchange, status != null ? status : 201, "");
            } else {
                respond(exchange, status != null ? status : 200, "secret-value");
            }
        });
        server.start();
        url = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopConjur() {
        server.stop(0);
    }

    @Test
    void retrieveSecretWithOnlyATokenFailsWithTheUnauthorizedResponse() throws Exception {
        secretStatuses.add(401);
        secretStatuses.add(401);

        try (ConjurClient client = ConjurClientFactory.createWithToken(url, ACCOUNT, "my-token")) {
            RuntimeException e = assertThrows(RuntimeException.class, () -> client.retrieveSecret(SECRET_ID));
            assertTrue(e.getCause().getMessage().contains("HTTP 401"), e.getCause().getMessage());
        }
        assertEquals(List.of("my-token"), secretRequestTokens);
        assertEquals(0, logins.get());
    }

    @Test
    void retrieveSecretAuthenticatesAgainAndRetriesOnce() throws Exception {
        secretStatuses.add(401);

        try (ConjurClient client = ConjurClientFactory.createWithApiKey(url, ACCOUNT, "host/myapp", "my-api-key")) {
            assertEquals("secret-value", client.retrieveSecret(SECRET_ID));
        }
        assertEquals(List.of("token-1", "token-2"), secretRequestTokens);
        assertEquals(2, logins.get());
    }

    @Test
    void retrieveSecretFailsWhenTheNewTokenIsRejectedToo() throws Exception {
        secretStatuses.add(401);
        secretStatuses.add(401);

        try (ConjurClient client = ConjurClientFactory.createWithCredentials(url, ACCOUNT, "admin", "my-password")) {
            RuntimeException e = assertThrows(RuntimeException.class, () -> client.retrieveSecret(SECRET_ID));
            assertTrue(e.getCause().getMessage().contains("HTTP 401"), e.getCause().getMessage());
        }
        assertEquals(List.of("token-1", "token-2"), secretRequestTokens);
        assertEquals(2, logins.get());
    }

    @Test
    void createSecretWithOnlyATokenFailsWithTheUnauthorizedResponse() throws Exception {
        secretStatuses.add(401);
        secretStatuses.add(401);

        try (ConjurClient client = ConjurClientFactory.createWithToken(url, ACCOUNT, "my-token")) {
            RuntimeException e = assertThrows(RuntimeException.class, () -> client.createSecret(SECRET_ID, "new-value"));
            assertTrue(e.getCause().getMessage().contains("HTTP 401"), e.getCause().getMessage());
        }
        assertEquals(List.of("my-token"), secretRequestTokens);
        assertEquals(0, logins.get());
    }

    @Test
    void createSecretAuthenticatesAgainAndRetriesOnce() throws Exception {
        secretStatuses.add(401);

        try (ConjurClient client = ConjurClientFactory.createWithApiKey(url, ACCOUNT, "host/myapp", "my-api-key")) {
            client.createSecret(SECRET_ID, "new-value");
        }
        assertEquals(List.of("token-1", "token-2"), secretRequestTokens);
        assertEquals(List.of("new-value", "new-value"), storedValues);
        assertEquals(2, logins.get());
    }

    @Test
    void createSecretFailsWhenTheNewTokenIsRejectedToo() throws Exception {
        secretStatuses.add(401);
        secretStatuses.add(401);

        try (ConjurClient client = ConjurClientFactory.createWithCredentials(url, ACCOUNT, "admin", "my-password")) {
            RuntimeException e = assertThrows(RuntimeException.class, () -> client.createSecret(SECRET_ID, "new-value"));
            assertTrue(e.getCause().getMessage().contains("HTTP 401"), e.getCause().getMessage());
        }
        assertEquals(List.of("token-1", "token-2"), secretRequestTokens);
        assertEquals(2, logins.get());
    }

    private static String tokenOf(HttpExchange exchange) {
        // Authorization: Token token="<base64 of the access token>"
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        String encoded = authorization.substring("Token token=\"".length(), authorization.length() - 1);
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
