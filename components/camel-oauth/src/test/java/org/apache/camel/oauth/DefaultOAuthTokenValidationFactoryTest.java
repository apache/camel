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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.JsonObject;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.OAuthTokenValidationConfig;
import org.apache.camel.spi.OAuthTokenValidationFactory;
import org.apache.camel.spi.OAuthTokenValidationResult;
import org.apache.camel.spi.OAuthTokenValidationResult.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultOAuthTokenValidationFactoryTest {

    private static final String JWKS_ENDPOINT = "https://test-factory.example.com/.well-known/jwks.json";
    private static final String KID = "factory-test-key";
    private static final String ISSUER = "https://idp.example.com";
    private static final String AUDIENCE = "test-client";

    private RSAKey rsaKey;
    private DefaultOAuthTokenValidationFactory factory;

    @BeforeEach
    void setUp() throws Exception {
        rsaKey = new RSAKeyGenerator(2048)
                .keyID(KID)
                .generate();
        JWKSet jwkSet = new JWKSet(rsaKey.toPublicJWK());
        JwksCache.instance().put(JWKS_ENDPOINT, jwkSet);
        DefaultOAuthTokenValidationFactory.clearDiscoveryCache();
        factory = new DefaultOAuthTokenValidationFactory();
    }

    @AfterEach
    void tearDown() {
        JwksCache.instance().clear();
        DefaultOAuthTokenValidationFactory.clearDiscoveryCache();
    }

    @Test
    void validateJwtWithExplicitConfig() throws Exception {
        String token = createJwt(ISSUER, AUDIENCE, futureDate(300));

        OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                .setJwksEndpoint(JWKS_ENDPOINT)
                .setExpectedIssuer(ISSUER)
                .setExpectedAudience(AUDIENCE);

        OAuthTokenValidationResult result = factory.validateToken(config, token);

        assertTrue(result.isValid());
        assertEquals(ISSUER, result.getIssuer());
        assertTrue(result.getAudience().contains(AUDIENCE));
    }

    @Test
    void validateExpiredJwtWithExplicitConfig() throws Exception {
        String token = createJwt(ISSUER, AUDIENCE, pastDate(60));

        OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                .setJwksEndpoint(JWKS_ENDPOINT)
                .setExpectedIssuer(ISSUER)
                .setExpectedAudience(AUDIENCE);

        OAuthTokenValidationResult result = factory.validateToken(config, token);

        assertFalse(result.isValid());
        assertTrue(result.getError().contains("expired"));
    }

    @Test
    void jwtWithoutJwksEndpointThrows() {
        String token = createJwt(ISSUER, AUDIENCE, futureDate(300));

        OAuthTokenValidationConfig config = new OAuthTokenValidationConfig();

        assertThrows(IllegalArgumentException.class,
                () -> factory.validateToken(config, token));
    }

    @Test
    void opaqueTokenWithoutIntrospectionEndpointThrows() {
        OAuthTokenValidationConfig config = new OAuthTokenValidationConfig();

        assertThrows(IllegalArgumentException.class,
                () -> factory.validateToken(config, "opaque-token-no-dots"));
    }

    @Test
    void opaqueTokenWithoutClientCredentialsThrows() {
        OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                .setIntrospectionEndpoint("https://example.com/introspect");

        assertThrows(IllegalArgumentException.class,
                () -> factory.validateToken(config, "opaque-token-no-dots"));
    }

    @Test
    void missingExpectedAudienceRejectedByDefault() {
        OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                .setJwksEndpoint(JWKS_ENDPOINT)
                .setExpectedIssuer(ISSUER);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> factory.validateConfiguration(config));

        assertTrue(exception.getMessage().contains("expected-audience"));
    }

    @Test
    void missingExpectedIssuerRejectedByDefault() {
        OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                .setJwksEndpoint(JWKS_ENDPOINT)
                .setExpectedAudience(AUDIENCE);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> factory.validateConfiguration(config));

        assertTrue(exception.getMessage().contains("expected-issuer"));
    }

    @Test
    void plainHttpEndpointRejectedByDefault() {
        OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                .setJwksEndpoint("http://127.0.0.1/jwks")
                .setExpectedIssuer(ISSUER)
                .setExpectedAudience(AUDIENCE);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> factory.validateConfiguration(config));

        assertTrue(exception.getMessage().contains("HTTPS"));
    }

    @Test
    void uppercasePlainHttpEndpointRejectedByDefault() {
        OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                .setJwksEndpoint("HTTP://127.0.0.1/jwks")
                .setExpectedIssuer(ISSUER)
                .setExpectedAudience(AUDIENCE);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> factory.validateConfiguration(config));

        assertTrue(exception.getMessage().contains("HTTPS"));
    }

    @Test
    void unsupportedEndpointSchemeRejected() {
        OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                .setJwksEndpoint("file:///tmp/jwks.json")
                .setExpectedIssuer(ISSUER)
                .setExpectedAudience(AUDIENCE);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> factory.validateConfiguration(config));

        assertTrue(exception.getMessage().contains("HTTP or HTTPS"));
    }

    @Test
    void missingIssuerAudienceAndHttpCanBeExplicitlyAllowed() {
        OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                .setJwksEndpoint("http://127.0.0.1/jwks")
                .setAllowMissingAudience(true)
                .setAllowMissingIssuer(true)
                .setAllowInsecureHttp(true);

        factory.validateConfiguration(config);
    }

    @Test
    void profileResolutionFromContext() throws Exception {
        String token = createJwt(ISSUER, AUDIENCE, futureDate(300));

        try (CamelContext context = new DefaultCamelContext()) {
            PropertiesComponent pc = new PropertiesComponent();
            pc.addInitialProperty("camel.oauth.myprofile.jwks-endpoint", JWKS_ENDPOINT);
            pc.addInitialProperty("camel.oauth.myprofile.expected-issuer", ISSUER);
            pc.addInitialProperty("camel.oauth.myprofile.expected-audience", AUDIENCE);
            context.setPropertiesComponent(pc);
            context.start();

            OAuthTokenValidationResult result = factory.validateToken(context, "myprofile", token);

            assertTrue(result.isValid());
            assertEquals(ISSUER, result.getIssuer());
        }
    }

    @Test
    void defaultProfileResolutionFromContext() throws Exception {
        String token = createJwt(ISSUER, AUDIENCE, futureDate(300));

        try (CamelContext context = new DefaultCamelContext()) {
            PropertiesComponent pc = new PropertiesComponent();
            pc.addInitialProperty("camel.oauth.jwks-endpoint", JWKS_ENDPOINT);
            pc.addInitialProperty("camel.oauth.expected-issuer", ISSUER);
            pc.addInitialProperty("camel.oauth.expected-audience", AUDIENCE);
            context.setPropertiesComponent(pc);
            context.start();

            OAuthTokenValidationResult result = factory.validateToken(context, token);

            assertTrue(result.isValid());
        }
    }

    @Test
    void expectedAudienceProfileAcceptsCommaSeparatedValues() throws Exception {
        String token = createJwt(ISSUER, AUDIENCE, futureDate(300));

        try (CamelContext context = new DefaultCamelContext()) {
            PropertiesComponent pc = new PropertiesComponent();
            pc.addInitialProperty("camel.oauth.myprofile.jwks-endpoint", JWKS_ENDPOINT);
            pc.addInitialProperty("camel.oauth.myprofile.expected-issuer", ISSUER);
            pc.addInitialProperty("camel.oauth.myprofile.expected-audience", "other-api, " + AUDIENCE);
            context.setPropertiesComponent(pc);
            context.start();

            OAuthTokenValidationResult result = factory.validateToken(context, "myprofile", token);

            assertTrue(result.isValid());
        }
    }

    @Test
    void expectedTokenTypeResolvesFromProfile() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            PropertiesComponent pc = new PropertiesComponent();
            pc.addInitialProperty("camel.oauth.myprofile.jwks-endpoint", JWKS_ENDPOINT);
            pc.addInitialProperty("camel.oauth.myprofile.expected-issuer", ISSUER);
            pc.addInitialProperty("camel.oauth.myprofile.expected-audience", AUDIENCE);
            pc.addInitialProperty("camel.oauth.myprofile.expected-token-type", "at+jwt");
            context.setPropertiesComponent(pc);
            context.start();

            OAuthTokenValidationConfig config = OAuthTokenValidationFactory.resolveProfileConfig(context, "myprofile");

            assertEquals("at+jwt", config.getExpectedTokenType());
        }
    }

    @Test
    void introspectionClientIdFallsBackToClientId() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = startIntrospectionServer(
                requests, new AtomicReference<>(), authorization, new AtomicReference<>(),
                """
                        {"active":true,"sub":"opaque-user","iss":"%s","aud":"%s","exp":%d}
                        """
                        .formatted(ISSUER, AUDIENCE, System.currentTimeMillis() / 1000L + 300));
        try (CamelContext context = new DefaultCamelContext()) {
            PropertiesComponent pc = new PropertiesComponent();
            pc.addInitialProperty("camel.oauth.myprofile.introspection-endpoint",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/introspect");
            pc.addInitialProperty("camel.oauth.myprofile.expected-issuer", ISSUER);
            pc.addInitialProperty("camel.oauth.myprofile.expected-audience", AUDIENCE);
            pc.addInitialProperty("camel.oauth.myprofile.client-id", "fallback-id");
            pc.addInitialProperty("camel.oauth.myprofile.client-secret", "fallback-secret");
            pc.addInitialProperty("camel.oauth.myprofile.allow-insecure-http", "true");
            context.setPropertiesComponent(pc);
            context.start();

            OAuthTokenValidationResult result = factory.validateToken(context, "myprofile", "opaque-token");

            assertTrue(result.isValid());
            assertEquals(1, requests.get());
            assertEquals("Basic " + Base64.getEncoder()
                    .encodeToString("fallback-id:fallback-secret".getBytes(StandardCharsets.UTF_8)), authorization.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void clockSkewFromProfile() throws Exception {
        String token = createJwt(ISSUER, AUDIENCE, pastDate(3));

        try (CamelContext context = new DefaultCamelContext()) {
            PropertiesComponent pc = new PropertiesComponent();
            pc.addInitialProperty("camel.oauth.myprofile.jwks-endpoint", JWKS_ENDPOINT);
            pc.addInitialProperty("camel.oauth.myprofile.expected-issuer", ISSUER);
            pc.addInitialProperty("camel.oauth.myprofile.expected-audience", AUDIENCE);
            pc.addInitialProperty("camel.oauth.myprofile.clock-skew-seconds", "10");
            context.setPropertiesComponent(pc);
            context.start();

            OAuthTokenValidationResult result = factory.validateToken(context, "myprofile", token);

            assertTrue(result.isValid());
        }
    }

    @Test
    void timeoutConfigFromProfile() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        HttpServer server = startBlockingIntrospectionServer(release);
        try (CamelContext context = new DefaultCamelContext()) {
            PropertiesComponent pc = new PropertiesComponent();
            pc.addInitialProperty("camel.oauth.myprofile.introspection-endpoint",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/introspect");
            pc.addInitialProperty("camel.oauth.myprofile.introspection-client-id", "client");
            pc.addInitialProperty("camel.oauth.myprofile.introspection-client-secret", "secret");
            pc.addInitialProperty("camel.oauth.myprofile.allow-missing-audience", "true");
            pc.addInitialProperty("camel.oauth.myprofile.allow-missing-issuer", "true");
            pc.addInitialProperty("camel.oauth.myprofile.allow-insecure-http", "true");
            pc.addInitialProperty("camel.oauth.myprofile.connect-timeout-seconds", "1");
            pc.addInitialProperty("camel.oauth.myprofile.read-timeout-seconds", "1");
            context.setPropertiesComponent(pc);
            context.start();

            assertTimeoutPreemptively(Duration.ofSeconds(5),
                    () -> assertThrows(OAuthException.class,
                            () -> factory.validateToken(context, "myprofile", "opaque-token")));
        } finally {
            release.countDown();
            server.stop(0);
        }
    }

    @Test
    void introspectionUsesConfiguredReadTimeout() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        HttpServer server = startBlockingIntrospectionServer(release);
        try {
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setIntrospectionEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect")
                    .setIntrospectionClientId("client")
                    .setIntrospectionClientSecret("secret")
                    .setAllowMissingAudience(true)
                    .setAllowMissingIssuer(true)
                    .setAllowInsecureHttp(true)
                    .setConnectTimeoutSeconds(1)
                    .setReadTimeoutSeconds(1);

            assertTimeoutPreemptively(Duration.ofSeconds(5),
                    () -> assertThrows(OAuthException.class,
                            () -> factory.validateToken(config, "opaque-token-no-dots")));
        } finally {
            release.countDown();
            server.stop(0);
        }
    }

    @Test
    void introspectionOnlyProfileAcceptsOpaqueTokenWithDots() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = startIntrospectionServer(requests, method, authorization, requestBody,
                """
                        {"active":true,"sub":"opaque-user","iss":"%s","aud":["%s"],"scope":"read write","exp":%d,"custom":"value"}
                        """
                        .formatted(ISSUER, AUDIENCE, System.currentTimeMillis() / 1000L + 300));
        try {
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setIntrospectionEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect")
                    .setIntrospectionClientId("client")
                    .setIntrospectionClientSecret("secret")
                    .setAllowMissingAudience(true)
                    .setAllowMissingIssuer(true)
                    .setAllowInsecureHttp(true)
                    .setExpectedIssuer(ISSUER)
                    .setExpectedAudience(AUDIENCE);

            OAuthTokenValidationResult result = factory.validateToken(config, "abc.def.ghi");

            assertTrue(result.isValid());
            assertEquals("opaque-user", result.getSubject());
            assertEquals(ISSUER, result.getIssuer());
            assertEquals(List.of(AUDIENCE), result.getAudience());
            assertEquals(List.of("read", "write"), result.getScopes());
            assertEquals("opaque-user", result.getName());
            assertTrue(result.hasScope("read"));
            assertFalse(result.hasScope("admin"));
            assertEquals("value", result.getClaims().get("custom"));
            assertEquals("value", result.getAttributes().get("custom"));
            assertEquals("value", result.getAttribute("custom", String.class));
            assertEquals("value", result.getClaim("custom", String.class));
            assertEquals(1, requests.get());
            assertEquals("POST", method.get());
            assertEquals("Basic " + Base64.getEncoder()
                    .encodeToString("client:secret".getBytes(StandardCharsets.UTF_8)), authorization.get());
            assertTrue(requestBody.get().contains("token=abc.def.ghi"));
            assertTrue(requestBody.get().contains("token_type_hint=access_token"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mixedProfileIntrospectsOpaqueTokenWithDots() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = startIntrospectionServer(requests, new AtomicReference<>(), new AtomicReference<>(),
                new AtomicReference<>(),
                """
                        {"active":true,"sub":"opaque-user","iss":"%s","aud":"%s","exp":%d}
                        """
                        .formatted(ISSUER, AUDIENCE, System.currentTimeMillis() / 1000L + 300));
        try {
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setJwksEndpoint(JWKS_ENDPOINT)
                    .setIntrospectionEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect")
                    .setIntrospectionClientId("client")
                    .setIntrospectionClientSecret("secret")
                    .setAllowMissingAudience(true)
                    .setAllowMissingIssuer(true)
                    .setAllowInsecureHttp(true)
                    .setExpectedIssuer(ISSUER)
                    .setExpectedAudience(AUDIENCE);

            OAuthTokenValidationResult result = factory.validateToken(config, "abc.def.ghi");

            assertTrue(result.isValid());
            assertEquals("opaque-user", result.getSubject());
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mixedProfileValidSignedJwtDoesNotCallIntrospection() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = startIntrospectionServer(requests, new AtomicReference<>(), new AtomicReference<>(),
                new AtomicReference<>(), "{\"active\":false}");
        try {
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setJwksEndpoint(JWKS_ENDPOINT)
                    .setIntrospectionEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect")
                    .setIntrospectionClientId("client")
                    .setIntrospectionClientSecret("secret")
                    .setAllowInsecureHttp(true)
                    .setExpectedIssuer(ISSUER)
                    .setExpectedAudience(AUDIENCE);

            OAuthTokenValidationResult result = factory.validateToken(config, createJwt(ISSUER, AUDIENCE, futureDate(300)));

            assertTrue(result.isValid());
            assertEquals(0, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mixedProfilePlainJwtIsIntrospectedAsOpaqueToken() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = startIntrospectionServer(requests, new AtomicReference<>(), new AtomicReference<>(),
                new AtomicReference<>(),
                """
                        {"active":true,"sub":"plain-jwt-token","iss":"%s","aud":"%s","exp":%d}
                        """
                        .formatted(ISSUER, AUDIENCE, System.currentTimeMillis() / 1000L + 300));
        try {
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setJwksEndpoint(JWKS_ENDPOINT)
                    .setIntrospectionEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect")
                    .setIntrospectionClientId("client")
                    .setIntrospectionClientSecret("secret")
                    .setAllowInsecureHttp(true)
                    .setExpectedIssuer(ISSUER)
                    .setExpectedAudience(AUDIENCE);

            PlainJWT plainJWT = new PlainJWT(
                    new JWTClaimsSet.Builder()
                            .subject("plain-jwt-token")
                            .issuer(ISSUER)
                            .audience(AUDIENCE)
                            .expirationTime(futureDate(300))
                            .build());

            OAuthTokenValidationResult result = factory.validateToken(config, plainJWT.serialize());

            assertTrue(result.isValid());
            assertEquals("plain-jwt-token", result.getSubject());
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void introspectionActiveFalseReturnsInvalid() throws Exception {
        HttpServer server = startIntrospectionServer(new AtomicInteger(), new AtomicReference<>(), new AtomicReference<>(),
                new AtomicReference<>(), "{\"active\":false}");
        try {
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setIntrospectionEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect")
                    .setIntrospectionClientId("client")
                    .setIntrospectionClientSecret("secret")
                    .setAllowMissingAudience(true)
                    .setAllowMissingIssuer(true)
                    .setAllowInsecureHttp(true);

            OAuthTokenValidationResult result = factory.validateToken(config, "opaque-token-no-dots");

            assertFalse(result.isValid());
            assertEquals(ErrorCode.INACTIVE_TOKEN, result.getErrorCode());
            assertTrue(result.getError().contains("not active"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void introspectionBasicAuthFormEncodesCredentials() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = startIntrospectionServer(new AtomicInteger(), new AtomicReference<>(), authorization,
                new AtomicReference<>(), "{\"active\":false}");
        try {
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setIntrospectionEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect")
                    .setIntrospectionClientId("client:id")
                    .setIntrospectionClientSecret("secret+value")
                    .setAllowMissingAudience(true)
                    .setAllowMissingIssuer(true)
                    .setAllowInsecureHttp(true);

            OAuthTokenValidationResult result = factory.validateToken(config, "opaque-token-no-dots");

            assertFalse(result.isValid());
            assertEquals("Basic " + Base64.getEncoder()
                    .encodeToString("client%3Aid:secret%2Bvalue".getBytes(StandardCharsets.UTF_8)), authorization.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void introspectionNonSuccessResponsesThrowOAuthException() throws Exception {
        for (int statusCode : List.of(400, 401, 500, 503, 204)) {
            HttpServer server = startIntrospectionServer(new AtomicInteger(), new AtomicReference<>(), new AtomicReference<>(),
                    new AtomicReference<>(), statusCode, statusCode == 204 ? "" : "{\"active\":true}");
            try {
                OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                        .setIntrospectionEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect")
                        .setIntrospectionClientId("client")
                        .setIntrospectionClientSecret("secret")
                        .setAllowMissingAudience(true)
                        .setAllowMissingIssuer(true)
                        .setAllowInsecureHttp(true);

                OAuthException exception = assertThrows(OAuthException.class,
                        () -> factory.validateToken(config, "opaque-token-no-dots"));

                assertTrue(exception.getMessage().contains("Failed to introspect token"));
            } finally {
                server.stop(0);
            }
        }
    }

    @Test
    void introspectionEmptySuccessResponseThrowsOAuthException() throws Exception {
        HttpServer server = startIntrospectionServer(new AtomicInteger(), new AtomicReference<>(), new AtomicReference<>(),
                new AtomicReference<>(), "");
        try {
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setIntrospectionEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect")
                    .setIntrospectionClientId("client")
                    .setIntrospectionClientSecret("secret")
                    .setAllowMissingAudience(true)
                    .setAllowMissingIssuer(true)
                    .setAllowInsecureHttp(true);

            assertThrows(OAuthException.class, () -> factory.validateToken(config, "opaque-token-no-dots"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void introspectionNonJsonOrNonObjectSuccessResponseThrowsOAuthException() throws Exception {
        for (String response : List.of("not-json", "[]")) {
            HttpServer server = startIntrospectionServer(new AtomicInteger(), new AtomicReference<>(), new AtomicReference<>(),
                    new AtomicReference<>(), response);
            try {
                OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                        .setIntrospectionEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect")
                        .setIntrospectionClientId("client")
                        .setIntrospectionClientSecret("secret")
                        .setAllowMissingAudience(true)
                        .setAllowMissingIssuer(true)
                        .setAllowInsecureHttp(true);

                assertThrows(OAuthException.class, () -> factory.validateToken(config, "opaque-token-no-dots"));
            } finally {
                server.stop(0);
            }
        }
    }

    @Test
    void introspectionMissingActiveThrowsOAuthException() throws Exception {
        HttpServer server = startIntrospectionServer(new AtomicInteger(), new AtomicReference<>(), new AtomicReference<>(),
                new AtomicReference<>(), "{\"sub\":\"user\"}");
        try {
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setIntrospectionEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect")
                    .setIntrospectionClientId("client")
                    .setIntrospectionClientSecret("secret")
                    .setAllowMissingAudience(true)
                    .setAllowMissingIssuer(true)
                    .setAllowInsecureHttp(true);

            assertThrows(OAuthException.class, () -> factory.validateToken(config, "opaque-token-no-dots"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void introspectionStringActiveThrowsOAuthException() throws Exception {
        HttpServer server = startIntrospectionServer(new AtomicInteger(), new AtomicReference<>(), new AtomicReference<>(),
                new AtomicReference<>(), "{\"active\":\"true\"}");
        try {
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setIntrospectionEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect")
                    .setIntrospectionClientId("client")
                    .setIntrospectionClientSecret("secret")
                    .setAllowMissingAudience(true)
                    .setAllowMissingIssuer(true)
                    .setAllowInsecureHttp(true);

            assertThrows(OAuthException.class, () -> factory.validateToken(config, "opaque-token-no-dots"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void introspectionNumericActiveThrowsOAuthException() throws Exception {
        HttpServer server = startIntrospectionServer(new AtomicInteger(), new AtomicReference<>(), new AtomicReference<>(),
                new AtomicReference<>(), "{\"active\":1}");
        try {
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setIntrospectionEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect")
                    .setIntrospectionClientId("client")
                    .setIntrospectionClientSecret("secret")
                    .setAllowMissingAudience(true)
                    .setAllowMissingIssuer(true)
                    .setAllowInsecureHttp(true);

            assertThrows(OAuthException.class, () -> factory.validateToken(config, "opaque-token-no-dots"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void introspectionExpZeroReturnsInvalidThroughValidationPath() throws Exception {
        HttpServer server = startIntrospectionServer(new AtomicInteger(), new AtomicReference<>(), new AtomicReference<>(),
                new AtomicReference<>(), "{\"active\":true,\"exp\":0}");
        try {
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setIntrospectionEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect")
                    .setIntrospectionClientId("client")
                    .setIntrospectionClientSecret("secret")
                    .setAllowMissingAudience(true)
                    .setAllowMissingIssuer(true)
                    .setAllowInsecureHttp(true);

            OAuthTokenValidationResult result = factory.validateToken(config, "opaque-token-no-dots");

            assertFalse(result.isValid());
            assertEquals(ErrorCode.EXPIRED_TOKEN, result.getErrorCode());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void introspectionExpiredExpReturnsInvalidThroughValidationPath() throws Exception {
        long exp = System.currentTimeMillis() / 1000L - 60;
        HttpServer server = startIntrospectionServer(new AtomicInteger(), new AtomicReference<>(), new AtomicReference<>(),
                new AtomicReference<>(), "{\"active\":true,\"exp\":" + exp + "}");
        try {
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setIntrospectionEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect")
                    .setIntrospectionClientId("client")
                    .setIntrospectionClientSecret("secret")
                    .setAllowMissingAudience(true)
                    .setAllowMissingIssuer(true)
                    .setAllowInsecureHttp(true);

            OAuthTokenValidationResult result = factory.validateToken(config, "opaque-token-no-dots");

            assertFalse(result.isValid());
            assertEquals(ErrorCode.EXPIRED_TOKEN, result.getErrorCode());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void introspectionFutureNbfReturnsInvalidThroughValidationPath() throws Exception {
        long nbf = System.currentTimeMillis() / 1000L + 300;
        HttpServer server = startIntrospectionServer(new AtomicInteger(), new AtomicReference<>(), new AtomicReference<>(),
                new AtomicReference<>(), "{\"active\":true,\"nbf\":" + nbf + "}");
        try {
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setIntrospectionEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect")
                    .setIntrospectionClientId("client")
                    .setIntrospectionClientSecret("secret")
                    .setAllowMissingAudience(true)
                    .setAllowMissingIssuer(true)
                    .setAllowInsecureHttp(true);

            OAuthTokenValidationResult result = factory.validateToken(config, "opaque-token-no-dots");

            assertFalse(result.isValid());
            assertEquals(ErrorCode.NOT_YET_VALID, result.getErrorCode());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void introspectionStringTemporalClaimThrowsOAuthException() throws Exception {
        HttpServer server = startIntrospectionServer(new AtomicInteger(), new AtomicReference<>(), new AtomicReference<>(),
                new AtomicReference<>(), "{\"active\":true,\"exp\":\"123\"}");
        try {
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setIntrospectionEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect")
                    .setIntrospectionClientId("client")
                    .setIntrospectionClientSecret("secret")
                    .setAllowMissingAudience(true)
                    .setAllowMissingIssuer(true)
                    .setAllowInsecureHttp(true);

            assertThrows(OAuthException.class, () -> factory.validateToken(config, "opaque-token-no-dots"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void introspectionMalformedAudienceThrowsOAuthException() throws Exception {
        HttpServer server = startIntrospectionServer(new AtomicInteger(), new AtomicReference<>(), new AtomicReference<>(),
                new AtomicReference<>(), "{\"active\":true,\"aud\":[\"api\",1]}");
        try {
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setIntrospectionEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect")
                    .setIntrospectionClientId("client")
                    .setIntrospectionClientSecret("secret")
                    .setAllowMissingAudience(true)
                    .setAllowMissingIssuer(true)
                    .setAllowInsecureHttp(true);

            assertThrows(OAuthException.class, () -> factory.validateToken(config, "opaque-token-no-dots"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void introspectionWrongIssuerReturnsInvalid() throws Exception {
        HttpServer server = startIntrospectionServer(new AtomicInteger(), new AtomicReference<>(), new AtomicReference<>(),
                new AtomicReference<>(), "{\"active\":true,\"iss\":\"https://wrong.example.com\"}");
        try {
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setIntrospectionEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect")
                    .setIntrospectionClientId("client")
                    .setIntrospectionClientSecret("secret")
                    .setAllowMissingAudience(true)
                    .setAllowMissingIssuer(true)
                    .setAllowInsecureHttp(true)
                    .setExpectedIssuer(ISSUER);

            OAuthTokenValidationResult result = factory.validateToken(config, "opaque-token-no-dots");

            assertFalse(result.isValid());
            assertEquals(ErrorCode.INVALID_ISSUER, result.getErrorCode());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void introspectionWrongAudienceReturnsInvalid() throws Exception {
        HttpServer server = startIntrospectionServer(new AtomicInteger(), new AtomicReference<>(), new AtomicReference<>(),
                new AtomicReference<>(), "{\"active\":true,\"aud\":\"other-api\"}");
        try {
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setIntrospectionEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect")
                    .setIntrospectionClientId("client")
                    .setIntrospectionClientSecret("secret")
                    .setAllowMissingAudience(true)
                    .setAllowMissingIssuer(true)
                    .setAllowInsecureHttp(true)
                    .setExpectedAudience(AUDIENCE);

            OAuthTokenValidationResult result = factory.validateToken(config, "opaque-token-no-dots");

            assertFalse(result.isValid());
            assertEquals(ErrorCode.INVALID_AUDIENCE, result.getErrorCode());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void introspectionOversizedResponseThrowsOAuthException() throws Exception {
        String oversizedResponse = " ".repeat(65 * 1024);
        HttpServer server = startIntrospectionServer(new AtomicInteger(), new AtomicReference<>(), new AtomicReference<>(),
                new AtomicReference<>(), oversizedResponse);
        try {
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setIntrospectionEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect")
                    .setIntrospectionClientId("client")
                    .setIntrospectionClientSecret("secret")
                    .setAllowMissingAudience(true)
                    .setAllowMissingIssuer(true)
                    .setAllowInsecureHttp(true);

            assertThrows(OAuthException.class, () -> factory.validateToken(config, "opaque-token-no-dots"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void oidcDiscoveryResolvesJwksEndpointAndIssuerForJwt() throws Exception {
        AtomicInteger discoveryRequests = new AtomicInteger();
        AtomicInteger jwksRequests = new AtomicInteger();
        AtomicReference<JWKSet> serverJwkSet = new AtomicReference<>(new JWKSet(rsaKey.toPublicJWK()));
        HttpServer jwksServer = startJwksServer(serverJwkSet, jwksRequests);
        HttpServer discoveryServer = startDiscoveryServer(discoveryRequests,
                """
                        {"issuer":"%s","jwks_uri":"%s"}
                        """.formatted(ISSUER, localJwksEndpoint(jwksServer)));
        try {
            String token = createJwt(ISSUER, AUDIENCE, futureDate(300));
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setOidcDiscoveryUrl("http://127.0.0.1:" + discoveryServer.getAddress().getPort()
                                         + "/.well-known/openid-configuration")
                    .setExpectedIssuer(ISSUER)
                    .setAllowInsecureHttp(true)
                    .setExpectedAudience(AUDIENCE);

            OAuthTokenValidationResult result = factory.validateToken(config, token);

            assertTrue(result.isValid());
            assertEquals(ISSUER, result.getIssuer());
            assertEquals(1, discoveryRequests.get());
            assertEquals(1, jwksRequests.get());
        } finally {
            discoveryServer.stop(0);
            jwksServer.stop(0);
        }
    }

    @Test
    void oidcDiscoveryDoesNotRequireIntrospectionCredentialsForJwtOnly() throws Exception {
        AtomicInteger discoveryRequests = new AtomicInteger();
        AtomicInteger jwksRequests = new AtomicInteger();
        AtomicReference<JWKSet> serverJwkSet = new AtomicReference<>(new JWKSet(rsaKey.toPublicJWK()));
        HttpServer jwksServer = startJwksServer(serverJwkSet, jwksRequests);
        HttpServer discoveryServer = startDiscoveryServer(discoveryRequests,
                """
                        {"issuer":"%s","jwks_uri":"%s","introspection_endpoint":"http://127.0.0.1:1/introspect"}
                        """.formatted(ISSUER, localJwksEndpoint(jwksServer)));
        try {
            String token = createJwt(ISSUER, AUDIENCE, futureDate(300));
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setOidcDiscoveryUrl("http://127.0.0.1:" + discoveryServer.getAddress().getPort()
                                         + "/.well-known/openid-configuration")
                    .setExpectedIssuer(ISSUER)
                    .setAllowInsecureHttp(true)
                    .setExpectedAudience(AUDIENCE);

            OAuthTokenValidationResult result = factory.validateToken(config, token);

            assertTrue(result.isValid());
            assertEquals(1, discoveryRequests.get());
            assertEquals(1, jwksRequests.get());
        } finally {
            discoveryServer.stop(0);
            jwksServer.stop(0);
        }
    }

    @Test
    void oidcDiscoverySuccessIsSingleFlight() throws Exception {
        int callers = 8;
        CountDownLatch releaseDiscovery = new CountDownLatch(1);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger discoveryRequests = new AtomicInteger();
        AtomicInteger jwksRequests = new AtomicInteger();
        AtomicReference<JWKSet> serverJwkSet = new AtomicReference<>(new JWKSet(rsaKey.toPublicJWK()));
        HttpServer jwksServer = startJwksServer(serverJwkSet, jwksRequests);
        HttpServer discoveryServer = startBlockingDiscoveryServer(discoveryRequests, releaseDiscovery,
                """
                        {"issuer":"%s","jwks_uri":"%s"}
                        """.formatted(ISSUER, localJwksEndpoint(jwksServer)));
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        try {
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setOidcDiscoveryUrl("http://127.0.0.1:" + discoveryServer.getAddress().getPort()
                                         + "/.well-known/openid-configuration")
                    .setExpectedIssuer(ISSUER)
                    .setExpectedAudience(AUDIENCE)
                    .setAllowInsecureHttp(true);
            String token = createJwt(ISSUER, AUDIENCE, futureDate(300));

            List<Future<OAuthTokenValidationResult>> futures = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                futures.add(executor.submit(() -> {
                    start.await(30, TimeUnit.SECONDS);
                    return factory.validateToken(config, token);
                }));
            }

            start.countDown();
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(1, discoveryRequests.get()));
            await().during(Duration.ofMillis(250)).atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> assertEquals(1, discoveryRequests.get()));
            releaseDiscovery.countDown();

            for (Future<OAuthTokenValidationResult> future : futures) {
                assertTrue(future.get(5, TimeUnit.SECONDS).isValid());
            }
            assertEquals(1, discoveryRequests.get());
        } finally {
            releaseDiscovery.countDown();
            executor.shutdownNow();
            discoveryServer.stop(0);
            jwksServer.stop(0);
        }
    }

    @Test
    void oidcDiscoveryIsCached() throws Exception {
        AtomicInteger discoveryRequests = new AtomicInteger();
        AtomicInteger jwksRequests = new AtomicInteger();
        AtomicReference<JWKSet> serverJwkSet = new AtomicReference<>(new JWKSet(rsaKey.toPublicJWK()));
        HttpServer jwksServer = startJwksServer(serverJwkSet, jwksRequests);
        HttpServer discoveryServer = startDiscoveryServer(discoveryRequests,
                """
                        {"issuer":"%s","jwks_uri":"%s"}
                        """.formatted(ISSUER, localJwksEndpoint(jwksServer)));
        try {
            String discoveryUrl = "http://127.0.0.1:" + discoveryServer.getAddress().getPort()
                                  + "/.well-known/openid-configuration";
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setOidcDiscoveryUrl(discoveryUrl)
                    .setExpectedIssuer(ISSUER)
                    .setAllowInsecureHttp(true)
                    .setExpectedAudience(AUDIENCE);

            assertTrue(factory.validateToken(config, createJwt(ISSUER, AUDIENCE, futureDate(300))).isValid());
            assertTrue(factory.validateToken(config, createJwt(ISSUER, AUDIENCE, futureDate(300))).isValid());

            assertEquals(1, discoveryRequests.get());
        } finally {
            discoveryServer.stop(0);
            jwksServer.stop(0);
        }
    }

    @Test
    void oidcDiscoveryRefreshesAfterCacheTtl() throws Exception {
        AtomicInteger discoveryRequests = new AtomicInteger();
        AtomicInteger jwksRequests = new AtomicInteger();
        AtomicReference<JWKSet> serverJwkSet = new AtomicReference<>(new JWKSet(rsaKey.toPublicJWK()));
        HttpServer jwksServer = startJwksServer(serverJwkSet, jwksRequests);
        HttpServer discoveryServer = startDiscoveryServer(discoveryRequests,
                """
                        {"issuer":"%s","jwks_uri":"%s"}
                        """.formatted(ISSUER, localJwksEndpoint(jwksServer)));
        try {
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setOidcDiscoveryUrl("http://127.0.0.1:" + discoveryServer.getAddress().getPort()
                                         + "/.well-known/openid-configuration")
                    .setOidcDiscoveryCacheTtlSeconds(1)
                    .setExpectedIssuer(ISSUER)
                    .setExpectedAudience(AUDIENCE)
                    .setAllowInsecureHttp(true);

            assertTrue(factory.validateToken(config, createJwt(ISSUER, AUDIENCE, futureDate(300))).isValid());
            assertEquals(1, discoveryRequests.get());

            await().atMost(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
                assertTrue(factory.validateToken(config, createJwt(ISSUER, AUDIENCE, futureDate(300))).isValid());
                assertEquals(2, discoveryRequests.get());
            });
        } finally {
            discoveryServer.stop(0);
            jwksServer.stop(0);
        }
    }

    @Test
    void oidcDiscoveryFailureIsRateLimited() throws Exception {
        AtomicInteger discoveryRequests = new AtomicInteger();
        HttpServer discoveryServer = startDiscoveryServer(discoveryRequests, "not-json");
        try {
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setOidcDiscoveryUrl("http://127.0.0.1:" + discoveryServer.getAddress().getPort()
                                         + "/.well-known/openid-configuration")
                    .setExpectedIssuer(ISSUER)
                    .setExpectedAudience(AUDIENCE)
                    .setAllowInsecureHttp(true);

            assertThrows(OAuthException.class, () -> factory.validateConfiguration(config));
            assertEquals(1, discoveryRequests.get());

            OAuthException exception = assertThrows(OAuthException.class, () -> factory.validateConfiguration(config));
            assertTrue(exception.getMessage().contains("attempted recently"));
            assertEquals(1, discoveryRequests.get());
        } finally {
            discoveryServer.stop(0);
        }
    }

    @Test
    void oidcDiscoveryPlainHttpRejectedBeforeRequest() throws Exception {
        AtomicInteger discoveryRequests = new AtomicInteger();
        HttpServer discoveryServer = startDiscoveryServer(discoveryRequests, "{}");
        try {
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setOidcDiscoveryUrl("http://127.0.0.1:" + discoveryServer.getAddress().getPort()
                                         + "/.well-known/openid-configuration")
                    .setExpectedIssuer(ISSUER)
                    .setExpectedAudience(AUDIENCE);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> factory.validateConfiguration(config));

            assertTrue(exception.getMessage().contains("HTTPS"));
            assertEquals(0, discoveryRequests.get());
        } finally {
            discoveryServer.stop(0);
        }
    }

    @Test
    void oidcDiscoveryResolvesIntrospectionEndpointForOpaqueToken() throws Exception {
        AtomicInteger discoveryRequests = new AtomicInteger();
        AtomicInteger introspectionRequests = new AtomicInteger();
        HttpServer introspectionServer = startIntrospectionServer(
                introspectionRequests, new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>(),
                """
                        {"active":true,"sub":"opaque-user","iss":"%s","aud":"%s","exp":%d}
                        """
                        .formatted(ISSUER, AUDIENCE, System.currentTimeMillis() / 1000L + 300));
        HttpServer discoveryServer = startDiscoveryServer(discoveryRequests,
                """
                        {"issuer":"%s","introspection_endpoint":"%s"}
                        """.formatted(ISSUER,
                        "http://127.0.0.1:" + introspectionServer.getAddress().getPort() + "/introspect"));
        try {
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setOidcDiscoveryUrl("http://127.0.0.1:" + discoveryServer.getAddress().getPort()
                                         + "/.well-known/openid-configuration")
                    .setIntrospectionClientId("client")
                    .setIntrospectionClientSecret("secret")
                    .setExpectedIssuer(ISSUER)
                    .setExpectedAudience(AUDIENCE)
                    .setAllowInsecureHttp(true);

            OAuthTokenValidationResult result = factory.validateToken(config, "opaque-token-no-dots");

            assertTrue(result.isValid());
            assertEquals("opaque-user", result.getSubject());
            assertEquals(1, discoveryRequests.get());
            assertEquals(1, introspectionRequests.get());
        } finally {
            discoveryServer.stop(0);
            introspectionServer.stop(0);
        }
    }

    @Test
    void baseUriDiscoveryResolvesJwksEndpointAndIssuerForJwt() throws Exception {
        AtomicInteger discoveryRequests = new AtomicInteger();
        AtomicInteger jwksRequests = new AtomicInteger();
        AtomicReference<JWKSet> serverJwkSet = new AtomicReference<>(new JWKSet(rsaKey.toPublicJWK()));
        HttpServer jwksServer = startJwksServer(serverJwkSet, jwksRequests);
        HttpServer discoveryServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            String baseUri = "http://127.0.0.1:" + discoveryServer.getAddress().getPort();
            discoveryServer.createContext("/.well-known/openid-configuration", exchange -> {
                discoveryRequests.incrementAndGet();
                sendResponse(exchange,
                        """
                                {"issuer":"%s","jwks_uri":"%s"}
                                """.formatted(baseUri, localJwksEndpoint(jwksServer)));
            });
            discoveryServer.start();

            try (CamelContext context = new DefaultCamelContext()) {
                PropertiesComponent pc = new PropertiesComponent();
                pc.addInitialProperty("camel.oauth.myprofile.base-uri", baseUri);
                pc.addInitialProperty("camel.oauth.myprofile.expected-audience", AUDIENCE);
                pc.addInitialProperty("camel.oauth.myprofile.allow-insecure-http", "true");
                context.setPropertiesComponent(pc);
                context.start();

                OAuthTokenValidationResult result
                        = factory.validateToken(context, "myprofile", createJwt(baseUri, AUDIENCE, futureDate(300)));

                assertTrue(result.isValid());
                assertEquals(baseUri, result.getIssuer());
                assertEquals(1, discoveryRequests.get());
                assertEquals(1, jwksRequests.get());
            }
        } finally {
            discoveryServer.stop(0);
            jwksServer.stop(0);
        }
    }

    @Test
    void baseUriDiscoveryIssuerMismatchThrows() throws Exception {
        AtomicInteger discoveryRequests = new AtomicInteger();
        AtomicInteger jwksRequests = new AtomicInteger();
        AtomicReference<JWKSet> serverJwkSet = new AtomicReference<>(new JWKSet(rsaKey.toPublicJWK()));
        HttpServer jwksServer = startJwksServer(serverJwkSet, jwksRequests);
        HttpServer discoveryServer = startDiscoveryServer(discoveryRequests,
                """
                        {"issuer":"https://issuer-from-discovery.example.com","jwks_uri":"%s"}
                        """.formatted(localJwksEndpoint(jwksServer)));
        try (CamelContext context = new DefaultCamelContext()) {
            String baseUri = "http://127.0.0.1:" + discoveryServer.getAddress().getPort();
            PropertiesComponent pc = new PropertiesComponent();
            pc.addInitialProperty("camel.oauth.myprofile.base-uri", baseUri);
            pc.addInitialProperty("camel.oauth.myprofile.expected-audience", AUDIENCE);
            pc.addInitialProperty("camel.oauth.myprofile.allow-insecure-http", "true");
            context.setPropertiesComponent(pc);
            context.start();

            String token = createJwt("https://issuer-from-discovery.example.com", AUDIENCE, futureDate(300));
            OAuthException exception = assertThrows(OAuthException.class,
                    () -> factory.validateToken(context, "myprofile", token));

            assertTrue(exception.getMessage().contains("OIDC discovery issuer mismatch"));
            assertTrue(exception.getMessage().contains(baseUri));
        } finally {
            discoveryServer.stop(0);
            jwksServer.stop(0);
        }
    }

    @Test
    void nonStandardDiscoveryUrlWithoutExpectedIssuerThrows() throws Exception {
        AtomicInteger discoveryRequests = new AtomicInteger();
        HttpServer discoveryServer = startDiscoveryServer("/custom-discovery", discoveryRequests,
                """
                        {"issuer":"%s","jwks_uri":"https://idp.example.com/jwks"}
                        """.formatted(ISSUER));
        try {
            OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                    .setOidcDiscoveryUrl("http://127.0.0.1:" + discoveryServer.getAddress().getPort() + "/custom-discovery")
                    .setExpectedAudience(AUDIENCE)
                    .setAllowInsecureHttp(true);

            OAuthException exception = assertThrows(OAuthException.class, () -> factory.validateConfiguration(config));

            assertTrue(exception.getMessage().contains("non-standard discovery URL"));
            assertEquals(1, discoveryRequests.get());
        } finally {
            discoveryServer.stop(0);
        }
    }

    @Test
    void validationResultDefensivelyCopiesCollections() {
        List<String> audience = new ArrayList<>(List.of("api"));
        List<String> scopes = new ArrayList<>(List.of("read"));
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "user");
        claims.put("email", "user@example.com");

        OAuthTokenValidationResult result = OAuthTokenValidationResult.valid("user", ISSUER, audience, scopes, claims, 1);

        audience.add("other-api");
        scopes.add("write");
        claims.put("sub", "other-user");

        assertEquals(List.of("api"), result.getAudience());
        assertEquals(List.of("read"), result.getScopes());
        assertEquals("user", result.getName());
        assertTrue(result.hasScope("read"));
        assertFalse(result.hasScope("write"));
        assertEquals("user", result.getClaims().get("sub"));
        assertEquals("user@example.com", result.getAttributes().get("email"));
        assertEquals("user@example.com", result.getAttribute("email"));
        assertEquals("user@example.com", result.getAttribute("email", String.class));
        assertEquals("user@example.com", result.getClaim("email"));
        assertEquals("user@example.com", result.getClaim("email", String.class));
        assertNull(result.getAttribute("missing", String.class));
        assertThrows(UnsupportedOperationException.class, () -> result.getAudience().add("new-api"));
        assertThrows(UnsupportedOperationException.class, () -> result.getClaims().put("other", "value"));
        assertThrows(UnsupportedOperationException.class, () -> result.getAttributes().put("other", "value"));
        assertThrows(ClassCastException.class, () -> result.getClaim("email", Integer.class));
        assertThrows(NullPointerException.class, () -> OAuthTokenValidationResult.invalid(null, "error"));
        assertThrows(NullPointerException.class, () -> OAuthTokenValidationResult.invalid(ErrorCode.INVALID_TOKEN, null));
    }

    @Test
    void configDefensivelyCopiesAllowedJwsAlgorithms() {
        Set<String> algorithms = new LinkedHashSet<>(List.of(" RS256 ", "ES256"));
        Set<String> audiences = new LinkedHashSet<>(List.of(" other-api ", AUDIENCE));
        OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                .setAllowedJwsAlgorithms(algorithms)
                .setExpectedAudiences(audiences)
                .setExpectedTokenType("at+jwt");

        algorithms.clear();
        audiences.clear();

        assertEquals(Set.of("RS256", "ES256"), config.getAllowedJwsAlgorithms());
        assertEquals(Set.of("other-api", AUDIENCE), config.getExpectedAudiences());
        assertThrows(UnsupportedOperationException.class, () -> config.getAllowedJwsAlgorithms().add("PS256"));
        assertThrows(UnsupportedOperationException.class, () -> config.getExpectedAudiences().add("new-api"));

        OAuthTokenValidationConfig copy = config.copy();
        config.setExpectedTokenType("JWT");
        assertEquals("at+jwt", copy.getExpectedTokenType());
        assertEquals(Set.of("RS256", "ES256"), copy.getAllowedJwsAlgorithms());
        assertEquals(Set.of("other-api", AUDIENCE), copy.getExpectedAudiences());
    }

    @Test
    void configRejectsInvalidNumericValues() {
        OAuthTokenValidationConfig config = new OAuthTokenValidationConfig();

        assertThrows(IllegalArgumentException.class, () -> config.setClockSkewSeconds(-1));
        assertThrows(IllegalArgumentException.class, () -> config.setJwksCacheTtlSeconds(0));
        assertThrows(IllegalArgumentException.class, () -> config.setOidcDiscoveryCacheTtlSeconds(0));
        assertThrows(IllegalArgumentException.class, () -> config.setConnectTimeoutSeconds(0));
        assertThrows(IllegalArgumentException.class, () -> config.setReadTimeoutSeconds(0));
    }

    @Test
    void configNormalizesOptionalStringsAndRejectsBlankCredentials() {
        OAuthTokenValidationConfig config = new OAuthTokenValidationConfig()
                .setJwksEndpoint(" https://idp.example.com/jwks ")
                .setIntrospectionEndpoint(" ")
                .setExpectedIssuer(" https://issuer.example.com ")
                .setOidcDiscoveryUrl(" ");

        assertEquals("https://idp.example.com/jwks", config.getJwksEndpoint());
        assertNull(config.getIntrospectionEndpoint());
        assertEquals("https://issuer.example.com", config.getExpectedIssuer());
        assertNull(config.getOidcDiscoveryUrl());
        assertThrows(IllegalArgumentException.class, () -> config.setIntrospectionClientId(" "));
        assertThrows(IllegalArgumentException.class, () -> config.setIntrospectionClientSecret("\t"));
    }

    @Test
    void allowedJwsAlgorithmsFromProfileAreTrimmed() throws Exception {
        String token = createJwt(ISSUER, AUDIENCE, futureDate(300));

        try (CamelContext context = new DefaultCamelContext()) {
            PropertiesComponent pc = new PropertiesComponent();
            pc.addInitialProperty("camel.oauth.myprofile.jwks-endpoint", JWKS_ENDPOINT);
            pc.addInitialProperty("camel.oauth.myprofile.expected-issuer", ISSUER);
            pc.addInitialProperty("camel.oauth.myprofile.expected-audience", AUDIENCE);
            pc.addInitialProperty("camel.oauth.myprofile.allowed-jws-algorithms", " ES256, RS256 ");
            context.setPropertiesComponent(pc);
            context.start();

            OAuthTokenValidationResult result = factory.validateToken(context, "myprofile", token);

            assertTrue(result.isValid());
        }
    }

    @Test
    void introspectionExpiredExpReturnsInvalid() {
        JsonObject json = new JsonObject();
        json.addProperty("active", true);
        json.addProperty("exp", System.currentTimeMillis() / 1000L - 60);

        OAuthTokenValidationResult result
                = DefaultOAuthTokenValidationFactory.checkIntrospectionTemporalClaims(json, 0);

        assertNotNull(result);
        assertFalse(result.isValid());
        assertEquals(ErrorCode.EXPIRED_TOKEN, result.getErrorCode());
        assertTrue(result.getError().contains("expired"));
    }

    @Test
    void introspectionFutureNbfReturnsInvalid() {
        JsonObject json = new JsonObject();
        json.addProperty("active", true);
        json.addProperty("nbf", System.currentTimeMillis() / 1000L + 300);

        OAuthTokenValidationResult result
                = DefaultOAuthTokenValidationFactory.checkIntrospectionTemporalClaims(json, 0);

        assertNotNull(result);
        assertFalse(result.isValid());
        assertEquals(ErrorCode.NOT_YET_VALID, result.getErrorCode());
        assertTrue(result.getError().contains("not yet valid"));
    }

    @Test
    void introspectionExpZeroReturnsInvalid() {
        JsonObject json = new JsonObject();
        json.addProperty("active", true);
        json.addProperty("exp", 0);

        OAuthTokenValidationResult result
                = DefaultOAuthTokenValidationFactory.checkIntrospectionTemporalClaims(json, 0);

        assertNotNull(result);
        assertFalse(result.isValid());
        assertEquals(ErrorCode.EXPIRED_TOKEN, result.getErrorCode());
    }

    @Test
    void introspectionStringNbfThrowsOAuthException() {
        JsonObject json = new JsonObject();
        json.addProperty("active", true);
        json.addProperty("nbf", "123");

        assertThrows(OAuthException.class,
                () -> DefaultOAuthTokenValidationFactory.checkIntrospectionTemporalClaims(json, 0));
    }

    @Test
    void introspectionValidTemporalClaimsReturnsNull() {
        JsonObject json = new JsonObject();
        json.addProperty("active", true);
        json.addProperty("exp", System.currentTimeMillis() / 1000L + 300);
        json.addProperty("nbf", System.currentTimeMillis() / 1000L - 60);

        OAuthTokenValidationResult result
                = DefaultOAuthTokenValidationFactory.checkIntrospectionTemporalClaims(json, 0);

        assertNull(result);
    }

    @Test
    void introspectionExpiredExpWithClockSkewPasses() {
        JsonObject json = new JsonObject();
        json.addProperty("active", true);
        json.addProperty("exp", System.currentTimeMillis() / 1000L - 1);

        OAuthTokenValidationResult result
                = DefaultOAuthTokenValidationFactory.checkIntrospectionTemporalClaims(json, 60);

        assertNull(result);
    }

    @Test
    void introspectionFutureNbfWithinClockSkewPasses() {
        JsonObject json = new JsonObject();
        json.addProperty("active", true);
        json.addProperty("nbf", System.currentTimeMillis() / 1000L + 1);

        OAuthTokenValidationResult result
                = DefaultOAuthTokenValidationFactory.checkIntrospectionTemporalClaims(json, 60);

        assertNull(result);
    }

    private String createJwt(String issuer, String audience, Date expiration) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("test-user")
                    .issuer(issuer)
                    .audience(audience)
                    .expirationTime(expiration)
                    .issueTime(new Date())
                    .build();
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(KID)
                    .build();
            SignedJWT signedJWT = new SignedJWT(header, claims);
            signedJWT.sign(new RSASSASigner(rsaKey));
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Date futureDate(int secondsFromNow) {
        return new Date(System.currentTimeMillis() + secondsFromNow * 1000L);
    }

    private static Date pastDate(int secondsAgo) {
        return new Date(System.currentTimeMillis() - secondsAgo * 1000L);
    }

    private static HttpServer startBlockingIntrospectionServer(CountDownLatch release) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/introspect", exchange -> {
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    private static HttpServer startIntrospectionServer(
            AtomicInteger requests, AtomicReference<String> method, AtomicReference<String> authorization,
            AtomicReference<String> requestBody, String response)
            throws Exception {
        return startIntrospectionServer(requests, method, authorization, requestBody, 200, response);
    }

    private static HttpServer startIntrospectionServer(
            AtomicInteger requests, AtomicReference<String> method, AtomicReference<String> authorization,
            AtomicReference<String> requestBody, int statusCode, String response)
            throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/introspect", exchange -> {
            requests.incrementAndGet();
            method.set(exchange.getRequestMethod());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(readRequestBody(exchange));
            sendResponse(exchange, statusCode, response);
        });
        server.start();
        return server;
    }

    private static HttpServer startDiscoveryServer(AtomicInteger requests, String response) throws Exception {
        return startDiscoveryServer("/.well-known/openid-configuration", requests, response);
    }

    private static HttpServer startDiscoveryServer(String path, AtomicInteger requests, String response) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            requests.incrementAndGet();
            sendResponse(exchange, response);
        });
        server.start();
        return server;
    }

    private static HttpServer startBlockingDiscoveryServer(
            AtomicInteger requests, CountDownLatch release, String response)
            throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/.well-known/openid-configuration", exchange -> {
            requests.incrementAndGet();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            sendResponse(exchange, response);
        });
        server.start();
        return server;
    }

    private static HttpServer startJwksServer(AtomicReference<JWKSet> jwkSet, AtomicInteger requests) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            requests.incrementAndGet();
            sendResponse(exchange, jwkSet.get().toString());
        });
        server.start();
        return server;
    }

    private static String localJwksEndpoint(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks";
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void sendResponse(HttpExchange exchange, String response) throws IOException {
        sendResponse(exchange, 200, response);
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] body = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        if (statusCode == 204) {
            exchange.sendResponseHeaders(statusCode, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(statusCode, body.length);
        try (var outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }
}
