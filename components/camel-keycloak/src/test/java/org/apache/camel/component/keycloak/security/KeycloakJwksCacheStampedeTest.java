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
package org.apache.camel.component.keycloak.security;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.representations.AccessToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Tests verifying that {@link KeycloakPublicKeyResolver} and {@link KeycloakSecurityProcessor} rate limit remote JWKS
 * refreshes for unknown key IDs, preventing cache stampede.
 */
class KeycloakJwksCacheStampedeTest {

    private HttpServer server;
    private int serverPort;
    private AtomicInteger jwksRequestCount;
    private KeyPair keyPair;
    private String jwksResponse;
    private CamelContext context;

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.start();

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        keyPair = keyGen.generateKeyPair();
        RSAPublicKey rsaPub = (RSAPublicKey) keyPair.getPublic();

        jwksResponse = "{\"keys\":[" + String.format(
                "{\"kty\":\"RSA\",\"use\":\"sig\",\"kid\":\"valid-key-1\",\"n\":\"%s\",\"e\":\"%s\"}",
                b64url(rsaPub.getModulus()), b64url(rsaPub.getPublicExponent())) + "]}";

        jwksRequestCount = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/realms/test-realm/protocol/openid-connect/certs", httpExchange -> {
            jwksRequestCount.incrementAndGet();
            byte[] bytes = jwksResponse.getBytes(StandardCharsets.UTF_8);
            httpExchange.getResponseHeaders().set("Content-Type", "application/json");
            httpExchange.sendResponseHeaders(200, bytes.length);
            httpExchange.getResponseBody().write(bytes);
            httpExchange.close();
        });
        server.start();
        serverPort = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (context != null) {
            context.stop();
        }
    }

    private static String b64url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] unsigned = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, unsigned, 0, unsigned.length);
            bytes = unsigned;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Test
    void testDirectResolverRateLimitsJwksRefreshOnUnknownKid() throws Exception {
        String serverUrl = "http://127.0.0.1:" + serverPort;
        KeycloakPublicKeyResolver resolver = new KeycloakPublicKeyResolver(serverUrl, "test-realm");

        // 1. Initial request with known kid loads the cache (1 network request)
        assertThat(resolver.getPublicKey("valid-key-1")).isNotNull();
        assertThat(jwksRequestCount.get()).isEqualTo(1);

        // 2. Subsequent requests for known kid hit cache (no network requests)
        assertThat(resolver.getPublicKey("valid-key-1")).isNotNull();
        assertThat(jwksRequestCount.get()).isEqualTo(1);

        // 3. Requests with unknown kids within the cooldown window do NOT trigger remote JWKS refreshes
        int attackAttempts = 5;
        for (int i = 0; i < attackAttempts; i++) {
            final String unknownKid = "random-attacker-kid-" + i;
            assertThatThrownBy(() -> resolver.getPublicKey(unknownKid))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("No public key found in Keycloak JWKS for kid: " + unknownKid);
        }

        // Outbound request count must still be 1 (stampede prevented)
        assertThat(jwksRequestCount.get())
                .as("Unknown kids within cooldown window did not trigger remote JWKS fetches")
                .isEqualTo(1);
    }

    @Test
    void testSecurityProcessorRateLimitsJwksRefreshWithForgedTokens() throws Exception {
        String serverUrl = "http://127.0.0.1:" + serverPort;
        KeycloakSecurityPolicy policy = new KeycloakSecurityPolicy();
        policy.setServerUrl(serverUrl);
        policy.setRealm("test-realm");
        policy.setClientId("test-client");
        policy.setClientSecret("test-secret");
        policy.setAutoFetchPublicKey(true);
        policy.beforeWrap(null, null);

        KeycloakSecurityProcessor processor = new KeycloakSecurityProcessor(e -> {
        }, policy);

        // 1. Legitimate request with a valid key: loads the cache (1 network request)
        AccessToken validToken = new AccessToken();
        validToken.issuer(serverUrl + "/realms/test-realm");
        validToken.subject("legitimate-user");
        validToken.exp(System.currentTimeMillis() / 1000 + 3600);
        String validJwt = new JWSBuilder()
                .type("JWT")
                .kid("valid-key-1")
                .jsonContent(validToken)
                .rsa256(keyPair.getPrivate());

        Exchange validExchange = new DefaultExchange(context);
        validExchange.getIn().setHeader("Authorization", "Bearer " + validJwt);
        processor.process(validExchange);
        assertThat(jwksRequestCount.get()).isEqualTo(1);

        // Subsequent valid requests hit the cache (0 additional network requests)
        Exchange validExchange2 = new DefaultExchange(context);
        validExchange2.getIn().setHeader("Authorization", "Bearer " + validJwt);
        processor.process(validExchange2);
        assertThat(jwksRequestCount.get()).isEqualTo(1);

        // 2. Incoming forged tokens with unknown kid headers within the cooldown window
        int forgedTokensCount = 5;
        for (int i = 0; i < forgedTokensCount; i++) {
            AccessToken token = new AccessToken();
            token.issuer(serverUrl + "/realms/test-realm");
            token.subject("attacker");
            token.exp(System.currentTimeMillis() / 1000 + 3600);

            String forgedJwt = new JWSBuilder()
                    .type("JWT")
                    .kid("forged-random-kid-" + i)
                    .jsonContent(token)
                    .rsa256(keyPair.getPrivate());

            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setHeader("Authorization", "Bearer " + forgedJwt);

            assertThatThrownBy(() -> processor.process(exchange))
                    .isInstanceOf(CamelAuthorizationException.class);
        }

        // Network request count must remain 1
        assertThat(jwksRequestCount.get())
                .as("Forged tokens with unknown kids did not force repeated remote JWKS fetches")
                .isEqualTo(1);
    }

    @Test
    void testJwksRefreshAllowedAfterCooldown() throws Exception {
        String serverUrl = "http://127.0.0.1:" + serverPort;
        // Set a short cooldown of 100ms for testing key rotation
        KeycloakPublicKeyResolver resolver = new KeycloakPublicKeyResolver(serverUrl, "test-realm", 300_000, 100);

        // Initial request loads valid key
        assertThat(resolver.getPublicKey("valid-key-1")).isNotNull();
        assertThat(jwksRequestCount.get()).isEqualTo(1);

        // Immediate unknown kid is rejected without refresh
        assertThatThrownBy(() -> resolver.getPublicKey("unknown-key-1"))
                .isInstanceOf(IOException.class);
        assertThat(jwksRequestCount.get()).isEqualTo(1);

        // Wait for cooldown to elapse using Awaitility (no Thread.sleep)
        long startTime = System.currentTimeMillis();
        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> System.currentTimeMillis() - startTime >= 150);

        // After cooldown, an unknown kid triggers refresh to check for rotated keys
        assertThatThrownBy(() -> resolver.getPublicKey("unknown-key-1"))
                .isInstanceOf(IOException.class);
        assertThat(jwksRequestCount.get()).isEqualTo(2);
    }

    @Test
    void testParseJwksPreservesExistingKeysOnInvalidResponse() throws Exception {
        String serverUrl = "http://127.0.0.1:" + serverPort;
        KeycloakPublicKeyResolver resolver = new KeycloakPublicKeyResolver(serverUrl, "test-realm");

        // Load valid keys
        assertThat(resolver.getPublicKey("valid-key-1")).isNotNull();

        // Simulate invalid/empty JWKS response
        assertThatThrownBy(() -> resolver.parseJwks("{\"keys\":[]}"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("No keys found in JWKS response");

        // Existing key is still available in cache
        assertThat(resolver.selectKey("valid-key-1")).isNotNull();
    }
}
