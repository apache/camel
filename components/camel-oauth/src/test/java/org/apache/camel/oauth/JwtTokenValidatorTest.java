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
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyOperation;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.spi.OAuthTokenValidationResult;
import org.apache.camel.spi.OAuthTokenValidationResult.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenValidatorTest {

    private static final String TEST_ENDPOINT = "https://test.example.com/.well-known/jwks.json";
    private static final String KID = "test-key-1";
    private static final String ISSUER = "https://idp.example.com";
    private static final String AUDIENCE = "my-client";

    private RSAKey rsaKey;
    private JWKSet jwkSet;

    @BeforeEach
    void setUp() throws Exception {
        rsaKey = new RSAKeyGenerator(2048)
                .keyID(KID)
                .generate();
        jwkSet = new JWKSet(rsaKey.toPublicJWK());
        JwksCache.instance().put(TEST_ENDPOINT, jwkSet);
    }

    @AfterEach
    void tearDown() {
        JwksCache.instance().clear();
    }

    @Test
    void validJwt() throws Exception {
        String token = createToken(ISSUER, AUDIENCE, futureDate(300), null);

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);

        assertTrue(result.isValid());
        assertNotNull(result.getSubject());
        assertEquals(ISSUER, result.getIssuer());
        assertTrue(result.getAudience().contains(AUDIENCE));
    }

    @Test
    void validJwtWithScopes() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user1")
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .expirationTime(futureDate(300))
                .issueTime(new Date())
                .claim("scope", "read write admin")
                .build();
        String token = signClaims(claims);

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);

        assertTrue(result.isValid());
        assertEquals(3, result.getScopes().size());
        assertTrue(result.getScopes().contains("read"));
        assertTrue(result.getScopes().contains("write"));
        assertTrue(result.getScopes().contains("admin"));
    }

    @Test
    void expiredJwt() throws Exception {
        String token = createToken(ISSUER, AUDIENCE, pastDate(60), null);

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);

        assertFalse(result.isValid());
        assertTrue(result.getError().contains("expired"));
    }

    @Test
    void expiredJwtWithClockSkewTolerance() throws Exception {
        String token = createToken(ISSUER, AUDIENCE, pastDate(1), null);

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, ISSUER, Set.of(AUDIENCE), 60, true, 5000, 10000, null);

        assertTrue(result.isValid());
    }

    @Test
    void notYetValidJwt() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user1")
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .expirationTime(futureDate(600))
                .notBeforeTime(futureDate(300))
                .build();
        String token = signClaims(claims);

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);

        assertFalse(result.isValid());
        assertTrue(result.getError().contains("not yet valid"));
    }

    @Test
    void wrongIssuer() throws Exception {
        String token = createToken("https://wrong-issuer.com", AUDIENCE, futureDate(300), null);

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);

        assertFalse(result.isValid());
        assertTrue(result.getError().contains("issuer"));
    }

    @Test
    void wrongAudience() throws Exception {
        String token = createToken(ISSUER, "other-client", futureDate(300), null);

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);

        assertFalse(result.isValid());
        assertTrue(result.getError().contains("audience"));
    }

    @Test
    void multipleAudiencesAcceptedWhenOneMatches() throws Exception {
        String token = createToken(ISSUER, List.of("other-client", AUDIENCE), futureDate(300), null);

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);

        assertTrue(result.isValid());
        assertTrue(result.getAudience().contains(AUDIENCE));
    }

    @Test
    void multipleAudiencesRejectedWhenNoneMatches() throws Exception {
        String token = createToken(ISSUER, List.of("other-client", "third-client"), futureDate(300), null);

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);

        assertFalse(result.isValid());
        assertEquals(ErrorCode.INVALID_AUDIENCE, result.getErrorCode());
    }

    @Test
    void issuerCheckSkippedWhenNull() throws Exception {
        String token = createToken("any-issuer", AUDIENCE, futureDate(300), null);

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, null, Set.of(AUDIENCE), 0, true, 5000, 10000, null);

        assertTrue(result.isValid());
    }

    @Test
    void audienceCheckSkippedWhenNull() throws Exception {
        String token = createToken(ISSUER, "any-audience", futureDate(300), null);

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, ISSUER, null, 0, true, 5000, 10000, null);

        assertTrue(result.isValid());
    }

    @Test
    void invalidSignature() throws Exception {
        RSAKey otherKey = new RSAKeyGenerator(2048)
                .keyID(KID)
                .generate();
        JWKSet otherJwkSet = new JWKSet(otherKey.toPublicJWK());
        JwksCache.instance().put(TEST_ENDPOINT, otherJwkSet);

        String token = createToken(ISSUER, AUDIENCE, futureDate(300), null);

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);

        assertFalse(result.isValid());
        assertTrue(result.getError().contains("signature"));
    }

    @Test
    void jwkWithEncryptionUseRejected() throws Exception {
        RSAKey encryptionKey = new RSAKey.Builder(rsaKey.toRSAPublicKey())
                .keyID(KID)
                .keyUse(KeyUse.ENCRYPTION)
                .build();
        JwksCache.instance().put(TEST_ENDPOINT, new JWKSet(encryptionKey));

        String token = createToken(ISSUER, AUDIENCE, futureDate(300), null);

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);

        assertFalse(result.isValid());
        assertTrue(result.getError().contains("No matching key"));
    }

    @Test
    void jwkWithoutVerifyOperationRejected() throws Exception {
        RSAKey signingOnlyKey = new RSAKey.Builder(rsaKey.toRSAPublicKey())
                .keyID(KID)
                .keyOperations(Set.of(KeyOperation.SIGN))
                .build();
        JwksCache.instance().put(TEST_ENDPOINT, new JWKSet(signingOnlyKey));

        String token = createToken(ISSUER, AUDIENCE, futureDate(300), null);

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);

        assertFalse(result.isValid());
        assertTrue(result.getError().contains("No matching key"));
    }

    @Test
    void jwkAlgorithmMismatchRejected() throws Exception {
        RSAKey wrongAlgorithmKey = new RSAKey.Builder(rsaKey.toRSAPublicKey())
                .keyID(KID)
                .algorithm(JWSAlgorithm.RS512)
                .build();
        JwksCache.instance().put(TEST_ENDPOINT, new JWKSet(wrongAlgorithmKey));

        String token = createToken(ISSUER, AUDIENCE, futureDate(300), null);

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);

        assertFalse(result.isValid());
        assertTrue(result.getError().contains("No matching key"));
    }

    @Test
    void unknownKidReturnsInvalid() throws Exception {
        RSAKey otherKey = new RSAKeyGenerator(2048)
                .keyID("unknown-kid")
                .generate();
        JWKSet otherJwkSet = new JWKSet(otherKey.toPublicJWK());
        JwksCache.instance().put(TEST_ENDPOINT, otherJwkSet);

        String token = createToken(ISSUER, AUDIENCE, futureDate(300), null);

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);

        assertFalse(result.isValid());
        assertTrue(result.getError().contains("No matching key"));
    }

    @Test
    void unknownKidForcedRefreshIsRateLimited() throws Exception {
        RSAKey otherKey = new RSAKeyGenerator(2048)
                .keyID("other-kid")
                .generate();
        JWKSet otherJwkSet = new JWKSet(otherKey.toPublicJWK());

        AtomicInteger requests = new AtomicInteger();
        AtomicReference<JWKSet> serverJwkSet = new AtomicReference<>(otherJwkSet);
        HttpServer server = startJwksServer(serverJwkSet, requests);
        try {
            String endpoint = jwksEndpoint(server);
            AtomicLong now = new AtomicLong(31_000L);
            JwksCache.instance().setCurrentTimeMillisSupplier(now::get);
            JwksCache.instance().put(endpoint, otherJwkSet, 0L);

            String token = createToken(ISSUER, AUDIENCE, futureDate(300), null);

            OAuthTokenValidationResult first = JwtTokenValidator.validate(
                    token, endpoint, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);
            OAuthTokenValidationResult second = JwtTokenValidator.validate(
                    token, endpoint, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);

            assertFalse(first.isValid());
            assertFalse(second.isValid());
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unknownKidFailedForcedRefreshIsRateLimited() throws Exception {
        RSAKey otherKey = new RSAKeyGenerator(2048)
                .keyID("other-kid")
                .generate();
        JWKSet otherJwkSet = new JWKSet(otherKey.toPublicJWK());

        AtomicInteger requests = new AtomicInteger();
        HttpServer server = startMalformedJwksServer(requests);
        try {
            String endpoint = jwksEndpoint(server);
            AtomicLong now = new AtomicLong(31_000L);
            JwksCache.instance().setCurrentTimeMillisSupplier(now::get);
            JwksCache.instance().put(endpoint, otherJwkSet, 0L);

            String token = createToken(ISSUER, AUDIENCE, futureDate(300), null);

            OAuthTokenValidationResult first = JwtTokenValidator.validate(
                    token, endpoint, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);
            OAuthTokenValidationResult second = JwtTokenValidator.validate(
                    token, endpoint, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);

            assertFalse(first.isValid());
            assertFalse(second.isValid());
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void expiredJwksRefreshFailureServesStaleKeys() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = startMalformedJwksServer(requests);
        try {
            String endpoint = jwksEndpoint(server);
            AtomicLong now = new AtomicLong(31_000L);
            JwksCache.instance().setCurrentTimeMillisSupplier(now::get);
            JwksCache.instance().put(endpoint, jwkSet, 0L);

            String token = createToken(ISSUER, AUDIENCE, futureDate(300), null);

            // serve-stale-on-error: the refresh fails, but the expired cached keys keep validating tokens
            OAuthTokenValidationResult first = JwtTokenValidator.validate(
                    token, endpoint, 1, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);
            // while the failure is rate-limited, stale keys are served without contacting the endpoint again
            OAuthTokenValidationResult second = JwtTokenValidator.validate(
                    token, endpoint, 1, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);

            assertTrue(first.isValid());
            assertTrue(second.isValid());
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void coldJwksRefreshFailureIsRateLimited() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = startMalformedJwksServer(requests);
        try {
            String endpoint = jwksEndpoint(server);
            AtomicLong now = new AtomicLong(1_000L);
            JwksCache.instance().setCurrentTimeMillisSupplier(now::get);

            String token = createToken(ISSUER, AUDIENCE, futureDate(300), null);

            assertThrows(OAuthException.class,
                    () -> JwtTokenValidator.validate(
                            token, endpoint, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null));
            OAuthException second = assertThrows(OAuthException.class,
                    () -> JwtTokenValidator.validate(
                            token, endpoint, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null));

            assertTrue(second.getMessage().contains("attempted recently"));
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void jwksFetchForOneEndpointDoesNotBlockCollidingEndpoint() throws Exception {
        CountDownLatch blockingRequest = new CountDownLatch(1);
        CountDownLatch releaseBlocking = new CountDownLatch(1);
        AtomicInteger normalRequests = new AtomicInteger();
        ExecutorService serverExecutor = Executors.newFixedThreadPool(2);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(serverExecutor);
        server.createContext("/jwks", exchange -> {
            if ("Aa".equals(exchange.getRequestURI().getQuery())) {
                blockingRequest.countDown();
                try {
                    releaseBlocking.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
            } else {
                normalRequests.incrementAndGet();
            }
            sendResponse(exchange, jwkSet.toString());
        });
        server.start();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            String endpointPrefix = "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks?";
            String blockingEndpoint = endpointPrefix + "Aa";
            String normalEndpoint = endpointPrefix + "BB";
            assertEquals(blockingEndpoint.hashCode(), normalEndpoint.hashCode());

            String token = createToken(ISSUER, AUDIENCE, futureDate(300), null);
            Future<OAuthTokenValidationResult> blockedFetch = executor.submit(
                    () -> JwtTokenValidator.validate(
                            token, blockingEndpoint, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null));

            assertTrue(blockingRequest.await(5, TimeUnit.SECONDS));
            OAuthTokenValidationResult result = assertTimeoutPreemptively(Duration.ofSeconds(2),
                    () -> JwtTokenValidator.validate(
                            token, normalEndpoint, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null));
            assertTrue(result.isValid());
            assertEquals(1, normalRequests.get());

            releaseBlocking.countDown();
            assertTrue(blockedFetch.get(5, TimeUnit.SECONDS).isValid());
        } finally {
            releaseBlocking.countDown();
            executor.shutdownNow();
            server.stop(0);
            serverExecutor.shutdownNow();
        }
    }

    @Test
    void normalJwksRefreshFailureAllowsRecoveryAfterCooldown() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> response = new AtomicReference<>("not-json");
        HttpServer server = startRawJwksServer(response, requests);
        try {
            String endpoint = jwksEndpoint(server);
            AtomicLong now = new AtomicLong(31_000L);
            JwksCache.instance().setCurrentTimeMillisSupplier(now::get);
            JwksCache.instance().put(endpoint, jwkSet, 0L);

            String token = createToken(ISSUER, AUDIENCE, futureDate(300), null);

            // the failed refresh serves stale cached keys instead of failing the request
            OAuthTokenValidationResult stale = JwtTokenValidator.validate(
                    token, endpoint, 1, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);
            assertTrue(stale.isValid());
            assertEquals(1, requests.get());

            // after the cooldown the refresh is re-attempted and succeeds against the recovered endpoint
            response.set(jwkSet.toString());
            now.addAndGet(31_000L);
            OAuthTokenValidationResult result = JwtTokenValidator.validate(
                    token, endpoint, 1, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);

            assertTrue(result.isValid());
            assertEquals(2, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void normalTtlRefreshIgnoresRecentForcedRefreshAttempt() throws Exception {
        RSAKey otherKey = new RSAKeyGenerator(2048)
                .keyID("other-kid")
                .generate();
        JWKSet otherJwkSet = new JWKSet(otherKey.toPublicJWK());

        AtomicInteger requests = new AtomicInteger();
        AtomicReference<JWKSet> serverJwkSet = new AtomicReference<>(otherJwkSet);
        HttpServer server = startJwksServer(serverJwkSet, requests);
        try {
            String endpoint = jwksEndpoint(server);
            AtomicLong now = new AtomicLong(31_000L);
            JwksCache.instance().setCurrentTimeMillisSupplier(now::get);
            JwksCache.instance().put(endpoint, otherJwkSet, 0L);

            String token = createToken(ISSUER, AUDIENCE, futureDate(300), null);

            OAuthTokenValidationResult forcedRefresh = JwtTokenValidator.validate(
                    token, endpoint, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);
            assertFalse(forcedRefresh.isValid());
            assertEquals(1, requests.get());

            serverJwkSet.set(jwkSet);
            now.addAndGet(2_000L);
            OAuthTokenValidationResult ttlRefresh = JwtTokenValidator.validate(
                    token, endpoint, 1, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);

            assertTrue(ttlRefresh.isValid());
            assertEquals(2, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unknownKidRefreshAllowsKeyRotationAfterCooldown() throws Exception {
        RSAKey otherKey = new RSAKeyGenerator(2048)
                .keyID("other-kid")
                .generate();
        JWKSet otherJwkSet = new JWKSet(otherKey.toPublicJWK());

        AtomicInteger requests = new AtomicInteger();
        AtomicReference<JWKSet> serverJwkSet = new AtomicReference<>(otherJwkSet);
        HttpServer server = startJwksServer(serverJwkSet, requests);
        try {
            String endpoint = jwksEndpoint(server);
            AtomicLong now = new AtomicLong(1_000L);
            JwksCache.instance().setCurrentTimeMillisSupplier(now::get);
            JwksCache.instance().put(endpoint, otherJwkSet, now.get());

            String token = createToken(ISSUER, AUDIENCE, futureDate(300), null);

            OAuthTokenValidationResult first = JwtTokenValidator.validate(
                    token, endpoint, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);
            assertFalse(first.isValid());
            assertEquals(1, requests.get());

            serverJwkSet.set(jwkSet);
            OAuthTokenValidationResult suppressed = JwtTokenValidator.validate(
                    token, endpoint, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);
            assertFalse(suppressed.isValid());
            assertEquals(1, requests.get());

            now.addAndGet(31_000L);
            OAuthTokenValidationResult refreshed = JwtTokenValidator.validate(
                    token, endpoint, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);

            assertTrue(refreshed.isValid());
            assertEquals(2, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void nonJwtTokenReturnsInvalid() {
        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                "not-a-jwt-token", TEST_ENDPOINT, 600, null, null, 0, true, 5000, 10000, null);

        assertFalse(result.isValid());
        assertTrue(result.getError().contains("not a valid JWT"));
    }

    @Test
    void claimsExtractedCorrectly() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user123")
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .expirationTime(futureDate(300))
                .issueTime(new Date())
                .claim("email", "user@example.com")
                .claim("custom", "value")
                .build();
        String token = signClaims(claims);

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, null, null, 0, true, 5000, 10000, null);

        assertTrue(result.isValid());
        assertEquals("user123", result.getSubject());
        assertEquals("user@example.com", result.getClaims().get("email"));
        assertEquals("value", result.getClaims().get("custom"));
    }

    @Test
    void jwtWithoutExpRejectedByDefault() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user1")
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .issueTime(new Date())
                .build();
        String token = signClaims(claims);

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);

        assertFalse(result.isValid());
        assertTrue(result.getError().contains("no expiration"));
    }

    @Test
    void jwtWithoutExpAcceptedWhenNotRequired() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user1")
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .issueTime(new Date())
                .build();
        String token = signClaims(claims);

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, ISSUER, Set.of(AUDIENCE), 0, false, 5000, 10000, null);

        assertTrue(result.isValid());
    }

    @Test
    void disallowedAlgorithmRejected() throws Exception {
        String token = createToken(ISSUER, AUDIENCE, futureDate(300), null);

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000,
                Set.of("ES256"));

        assertFalse(result.isValid());
        assertTrue(result.getError().contains("not in the allowed set"));
    }

    @Test
    void allowedAlgorithmAccepted() throws Exception {
        String token = createToken(ISSUER, AUDIENCE, futureDate(300), null);

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000,
                Set.of("RS256", "ES256"));

        assertTrue(result.isValid());
    }

    @Test
    void nullAllowlistAcceptsAnyAlgorithm() throws Exception {
        String token = createToken(ISSUER, AUDIENCE, futureDate(300), null);

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000,
                null);

        assertTrue(result.isValid());
    }

    @Test
    void symmetricAlgorithmExplicitlyRejected() throws Exception {
        String token = createHmacToken();

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000,
                Set.of("HS256"));

        assertFalse(result.isValid());
        assertEquals(ErrorCode.UNSUPPORTED_ALGORITHM, result.getErrorCode());
        assertTrue(result.getError().contains("Symmetric JWT algorithm"));
    }

    @Test
    void expectedTokenTypeAccepted() throws Exception {
        JWTClaimsSet claims = createClaims(ISSUER, List.of(AUDIENCE), futureDate(300), null);
        String token = signClaimsWithType(claims, "at+jwt");

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null,
                "at+jwt");

        assertTrue(result.isValid());
    }

    @Test
    void expectedTokenTypeRejectedWhenDifferent() throws Exception {
        JWTClaimsSet claims = createClaims(ISSUER, List.of(AUDIENCE), futureDate(300), null);
        String token = signClaimsWithType(claims, "JWT");

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null,
                "at+jwt");

        assertFalse(result.isValid());
        assertEquals(ErrorCode.INVALID_TOKEN, result.getErrorCode());
        assertTrue(result.getError().contains("Invalid JWT type"));
    }

    @Test
    void ecJwtAccepted() throws Exception {
        ECKey ecKey = new ECKeyGenerator(Curve.P_256)
                .keyID("ec-key")
                .generate();
        JwksCache.instance().put(TEST_ENDPOINT, new JWKSet(ecKey.toPublicJWK()));

        JWTClaimsSet claims = createClaims(ISSUER, List.of(AUDIENCE), futureDate(300), null);
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID("ec-key").build(), claims);
        signedJWT.sign(new ECDSASigner(ecKey));

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                signedJWT.serialize(), TEST_ENDPOINT, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);

        assertTrue(result.isValid());
    }

    @Test
    void jwtWithoutKidAcceptedWhenSingleEligibleKey() throws Exception {
        JWTClaimsSet claims = createClaims(ISSUER, List.of(AUDIENCE), futureDate(300), null);
        String token = signClaimsWithoutKid(claims);

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                token, TEST_ENDPOINT, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);

        assertTrue(result.isValid());
    }

    @Test
    void jwtWithoutKidRejectedWhenMultipleEligibleKeys() throws Exception {
        RSAKey otherKey = new RSAKeyGenerator(2048)
                .keyID("other-key")
                .generate();
        JWKSet multipleKeys = new JWKSet(List.of(rsaKey.toPublicJWK(), otherKey.toPublicJWK()));
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<JWKSet> serverJwkSet = new AtomicReference<>(multipleKeys);
        HttpServer server = startJwksServer(serverJwkSet, requests);
        try {
            String endpoint = jwksEndpoint(server);
            JwksCache.instance().put(endpoint, multipleKeys);
            JWTClaimsSet claims = createClaims(ISSUER, List.of(AUDIENCE), futureDate(300), null);
            String token = signClaimsWithoutKid(claims);

            OAuthTokenValidationResult result = JwtTokenValidator.validate(
                    token, endpoint, 600, ISSUER, Set.of(AUDIENCE), 0, true, 1000, 1000, null);

            assertFalse(result.isValid());
            assertEquals(ErrorCode.NO_MATCHING_KEY, result.getErrorCode());
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void algNoneTokenRejected() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("attacker")
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .expirationTime(futureDate(300))
                .build();
        PlainJWT plainJWT = new PlainJWT(claims);
        String unsignedToken = plainJWT.serialize();

        OAuthTokenValidationResult result = JwtTokenValidator.validate(
                unsignedToken, TEST_ENDPOINT, 600, ISSUER, Set.of(AUDIENCE), 0, true, 5000, 10000, null);

        assertFalse(result.isValid());
        assertTrue(result.getError().contains("not a valid JWT"));
    }

    @Test
    void isJwtHandlesAlgNoneVariants() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("x").build();
        PlainJWT plainJWT = new PlainJWT(claims);
        String plainJwtToken = plainJWT.serialize();

        // PlainJWT serializes as 3 segments with empty signature (header.payload.)
        // isJwt returns true (3 segments), but SignedJWT.parse() will reject it in the validator
        assertTrue(DefaultOAuthTokenValidationFactory.isJwt(plainJwtToken));

        // Manual 2-segment token (without trailing dot) — isJwt should return false
        String twoSegment = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJ4In0";
        assertFalse(DefaultOAuthTokenValidationFactory.isJwt(twoSegment));
    }

    @Test
    void isJwtDetection() {
        assertTrue(DefaultOAuthTokenValidationFactory.isJwt("a.b.c"));
        assertTrue(DefaultOAuthTokenValidationFactory.isJwt("eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyMSJ9.signature"));
        assertFalse(DefaultOAuthTokenValidationFactory.isJwt("opaque-token-no-dots"));
        assertFalse(DefaultOAuthTokenValidationFactory.isJwt("only.one.dot.too.many"));
        assertFalse(DefaultOAuthTokenValidationFactory.isJwt("one.dot"));
        assertFalse(DefaultOAuthTokenValidationFactory.isJwt(""));
    }

    @Test
    void isSignedJwtRequiresParseableSignedJwt() throws Exception {
        assertTrue(DefaultOAuthTokenValidationFactory.isSignedJwt(createToken(ISSUER, AUDIENCE, futureDate(300), null)));
        assertFalse(DefaultOAuthTokenValidationFactory.isSignedJwt("abc.def.ghi"));
        assertFalse(DefaultOAuthTokenValidationFactory.isSignedJwt("opaque-token-no-dots"));
    }

    private String createToken(String issuer, String audience, Date expiration, Date notBefore) throws Exception {
        return createToken(issuer, List.of(audience), expiration, notBefore);
    }

    private String createToken(String issuer, List<String> audience, Date expiration, Date notBefore) throws Exception {
        return signClaims(createClaims(issuer, audience, expiration, notBefore));
    }

    private static JWTClaimsSet createClaims(String issuer, List<String> audience, Date expiration, Date notBefore) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject("user-" + UUID.randomUUID().toString().substring(0, 8))
                .issuer(issuer)
                .audience(audience)
                .expirationTime(expiration)
                .issueTime(new Date());
        if (notBefore != null) {
            builder.notBeforeTime(notBefore);
        }
        return builder.build();
    }

    private String signClaims(JWTClaimsSet claims) throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(KID)
                .build();
        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(new RSASSASigner(rsaKey));
        return signedJWT.serialize();
    }

    private String signClaimsWithoutKid(JWTClaimsSet claims) throws Exception {
        SignedJWT signedJWT = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims);
        signedJWT.sign(new RSASSASigner(rsaKey));
        return signedJWT.serialize();
    }

    private String signClaimsWithType(JWTClaimsSet claims, String type) throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(KID)
                .type(new JOSEObjectType(type))
                .build();
        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(new RSASSASigner(rsaKey));
        return signedJWT.serialize();
    }

    private String createHmacToken() throws Exception {
        JWTClaimsSet claims = createClaims(ISSUER, List.of(AUDIENCE), futureDate(300), null);
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(KID).build(), claims);
        signedJWT.sign(new MACSigner("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)));
        return signedJWT.serialize();
    }

    private static Date futureDate(int secondsFromNow) {
        return new Date(System.currentTimeMillis() + secondsFromNow * 1000L);
    }

    private static Date pastDate(int secondsAgo) {
        return new Date(System.currentTimeMillis() - secondsAgo * 1000L);
    }

    private static HttpServer startJwksServer(AtomicReference<JWKSet> jwkSet, AtomicInteger requests) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            requests.incrementAndGet();
            byte[] body = jwkSet.get().toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        return server;
    }

    private static HttpServer startMalformedJwksServer(AtomicInteger requests) throws Exception {
        return startRawJwksServer(new AtomicReference<>("not-json"), requests);
    }

    private static HttpServer startRawJwksServer(AtomicReference<String> response, AtomicInteger requests) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            requests.incrementAndGet();
            byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        return server;
    }

    private static void sendResponse(HttpExchange exchange, String response) throws IOException {
        byte[] body = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (var outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private static String jwksEndpoint(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks";
    }
}
