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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.keycloak.security.cache.TokenCache;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.representations.AccessToken;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link KeycloakSecurityProcessor} authenticates the access token even when the policy does not require
 * any roles or permissions, so an invalid or unverifiable token is rejected instead of being accepted.
 */
class KeycloakSecurityProcessorTest {

    private CamelContext context;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    private Exchange bearer(String token) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("Authorization", "Bearer " + token);
        return exchange;
    }

    @Test
    void testInvalidTokenRejectedWithoutRolesOrPermissionsLocalJwt() throws Exception {
        // Documented "Basic Setup": no required roles, no required permissions.
        KeycloakSecurityPolicy policy = new KeycloakSecurityPolicy();
        policy.setServerUrl("http://localhost:8080");
        policy.setRealm("test-realm");
        policy.setClientId("test-client");
        policy.setClientSecret("test-secret");
        // Configure a key so local JWT verification can run fully offline.
        policy.setAutoFetchPublicKey(false);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        PublicKey publicKey = generator.generateKeyPair().getPublic();
        policy.setPublicKey(publicKey);

        AtomicBoolean routeReached = new AtomicBoolean(false);
        KeycloakSecurityProcessor processor = new KeycloakSecurityProcessor(e -> routeReached.set(true), policy);

        assertThrows(CamelAuthorizationException.class, () -> processor.process(bearer("x")));
        assertFalse(routeReached.get(), "Route body must not be reached for an unverified token");
    }

    @Test
    void testInactiveTokenRejectedWithoutRolesOrPermissionsIntrospection() throws Exception {
        // An introspector that reports the token as inactive, evaluated offline.
        KeycloakTokenIntrospector introspector = new KeycloakTokenIntrospector(
                "http://localhost:8080", "test-realm", "test-client", "test-secret", (TokenCache) null) {
            @Override
            public IntrospectionResult introspect(String token) {
                return new IntrospectionResult(Map.<String, Object> of("active", false));
            }
        };

        // Basic Setup (no roles/permissions) with introspection enabled.
        KeycloakSecurityPolicy policy = new KeycloakSecurityPolicy() {
            @Override
            public boolean isUseTokenIntrospection() {
                return true;
            }

            @Override
            public KeycloakTokenIntrospector getTokenIntrospector() {
                return introspector;
            }
        };
        policy.setServerUrl("http://localhost:8080");
        policy.setRealm("test-realm");
        policy.setClientId("test-client");
        policy.setClientSecret("test-secret");

        AtomicBoolean routeReached = new AtomicBoolean(false);
        KeycloakSecurityProcessor processor = new KeycloakSecurityProcessor(e -> routeReached.set(true), policy);

        assertThrows(CamelAuthorizationException.class, () -> processor.process(bearer("x")));
        assertFalse(routeReached.get(), "Route body must not be reached for an inactive token");
    }

    @Test
    void testTokenMissingExpectedAudienceRejectedLocalJwt() throws Exception {
        String issuer = "http://localhost:8080/realms/test-realm";

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        KeycloakSecurityPolicy policy = new KeycloakSecurityPolicy();
        policy.setServerUrl("http://localhost:8080");
        policy.setRealm("test-realm");
        policy.setClientId("test-client");
        policy.setClientSecret("test-secret");
        policy.setAutoFetchPublicKey(false);
        policy.setPublicKey(keyPair.getPublic());
        policy.setExpectedAudience("expected-client");

        AccessToken token = new AccessToken();
        token.issuer(issuer);
        token.subject("user-123");
        token.exp(System.currentTimeMillis() / 1000 + 3600);
        // No audience on the token

        String signed = new JWSBuilder().type("JWT").jsonContent(token).rsa256(keyPair.getPrivate());

        AtomicBoolean routeReached = new AtomicBoolean(false);
        KeycloakSecurityProcessor processor = new KeycloakSecurityProcessor(e -> routeReached.set(true), policy);

        assertThrows(CamelAuthorizationException.class, () -> processor.process(bearer(signed)));
        assertFalse(routeReached.get(), "Route body must not be reached for a token missing the expected audience");
    }

    @Test
    void testTokenWithMatchingAudienceAcceptedLocalJwt() throws Exception {
        String issuer = "http://localhost:8080/realms/test-realm";

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        KeycloakSecurityPolicy policy = new KeycloakSecurityPolicy();
        policy.setServerUrl("http://localhost:8080");
        policy.setRealm("test-realm");
        policy.setClientId("test-client");
        policy.setClientSecret("test-secret");
        policy.setAutoFetchPublicKey(false);
        policy.setPublicKey(keyPair.getPublic());
        policy.setExpectedAudience("expected-client");

        AccessToken token = new AccessToken();
        token.issuer(issuer);
        token.subject("user-123");
        token.exp(System.currentTimeMillis() / 1000 + 3600);
        token.audience("expected-client");

        String signed = new JWSBuilder().type("JWT").jsonContent(token).rsa256(keyPair.getPrivate());

        AtomicBoolean routeReached = new AtomicBoolean(false);
        KeycloakSecurityProcessor processor = new KeycloakSecurityProcessor(e -> routeReached.set(true), policy);

        processor.process(bearer(signed));
        assertTrue(routeReached.get(), "Route body must be reached for a token with the expected audience");
    }

    @Test
    void testTokenMissingExpectedAudienceRejectedIntrospection() throws Exception {
        KeycloakTokenIntrospector introspector = new KeycloakTokenIntrospector(
                "http://localhost:8080", "test-realm", "test-client", "test-secret", (TokenCache) null) {
            @Override
            public IntrospectionResult introspect(String token) {
                // Active but no "aud" claim at all
                return new IntrospectionResult(Map.<String, Object> of("active", true));
            }
        };

        KeycloakSecurityPolicy policy = new KeycloakSecurityPolicy() {
            @Override
            public boolean isUseTokenIntrospection() {
                return true;
            }

            @Override
            public KeycloakTokenIntrospector getTokenIntrospector() {
                return introspector;
            }
        };
        policy.setServerUrl("http://localhost:8080");
        policy.setRealm("test-realm");
        policy.setClientId("test-client");
        policy.setClientSecret("test-secret");
        policy.setValidateIssuer(false);
        policy.setExpectedAudience("expected-client");

        AtomicBoolean routeReached = new AtomicBoolean(false);
        KeycloakSecurityProcessor processor = new KeycloakSecurityProcessor(e -> routeReached.set(true), policy);

        assertThrows(CamelAuthorizationException.class, () -> processor.process(bearer("x")));
        assertFalse(routeReached.get(), "Route body must not be reached for a token missing the expected audience");
    }

    @Test
    void testTokenWithMatchingAudienceAcceptedIntrospection() throws Exception {
        KeycloakTokenIntrospector introspector = new KeycloakTokenIntrospector(
                "http://localhost:8080", "test-realm", "test-client", "test-secret", (TokenCache) null) {
            @Override
            public IntrospectionResult introspect(String token) {
                return new IntrospectionResult(
                        Map.of("active", true, "aud", List.of("expected-client", "other-client")));
            }
        };

        KeycloakSecurityPolicy policy = new KeycloakSecurityPolicy() {
            @Override
            public boolean isUseTokenIntrospection() {
                return true;
            }

            @Override
            public KeycloakTokenIntrospector getTokenIntrospector() {
                return introspector;
            }
        };
        policy.setServerUrl("http://localhost:8080");
        policy.setRealm("test-realm");
        policy.setClientId("test-client");
        policy.setClientSecret("test-secret");
        policy.setValidateIssuer(false);
        policy.setExpectedAudience("expected-client");

        AtomicBoolean routeReached = new AtomicBoolean(false);
        KeycloakSecurityProcessor processor = new KeycloakSecurityProcessor(e -> routeReached.set(true), policy);

        processor.process(bearer("x"));
        assertTrue(routeReached.get(), "Route body must be reached for a token with the expected audience");
    }

    @Test
    void testTokenMissingOneOfMultipleExpectedAudiencesRejectedIntrospection() throws Exception {
        KeycloakTokenIntrospector introspector = new KeycloakTokenIntrospector(
                "http://localhost:8080", "test-realm", "test-client", "test-secret", (TokenCache) null) {
            @Override
            public IntrospectionResult introspect(String token) {
                // Token only has one of the two expected audiences
                return new IntrospectionResult(Map.of("active", true, "aud", "expected-client"));
            }
        };

        KeycloakSecurityPolicy policy = new KeycloakSecurityPolicy() {
            @Override
            public boolean isUseTokenIntrospection() {
                return true;
            }

            @Override
            public KeycloakTokenIntrospector getTokenIntrospector() {
                return introspector;
            }
        };
        policy.setServerUrl("http://localhost:8080");
        policy.setRealm("test-realm");
        policy.setClientId("test-client");
        policy.setClientSecret("test-secret");
        policy.setValidateIssuer(false);
        policy.setExpectedAudience("expected-client,other-required-client");

        AtomicBoolean routeReached = new AtomicBoolean(false);
        KeycloakSecurityProcessor processor = new KeycloakSecurityProcessor(e -> routeReached.set(true), policy);

        assertThrows(CamelAuthorizationException.class, () -> processor.process(bearer("x")));
        assertFalse(routeReached.get(),
                "Route body must not be reached when the token has only some of the expected audiences");
    }

    @Test
    void testTokenMissingExpectedAudienceRejectedWithRequiredRolesIntrospection() throws Exception {
        // Token would satisfy the required role, but is missing the expected audience: the audience check
        // inside validateRoles() must still reject it before the role check ever runs.
        KeycloakTokenIntrospector introspector = new KeycloakTokenIntrospector(
                "http://localhost:8080", "test-realm", "test-client", "test-secret", (TokenCache) null) {
            @Override
            public IntrospectionResult introspect(String token) {
                return new IntrospectionResult(
                        Map.of("active", true, "realm_access", Map.of("roles", List.of("admin"))));
            }
        };

        KeycloakSecurityPolicy policy = new KeycloakSecurityPolicy() {
            @Override
            public boolean isUseTokenIntrospection() {
                return true;
            }

            @Override
            public KeycloakTokenIntrospector getTokenIntrospector() {
                return introspector;
            }
        };
        policy.setServerUrl("http://localhost:8080");
        policy.setRealm("test-realm");
        policy.setClientId("test-client");
        policy.setClientSecret("test-secret");
        policy.setValidateIssuer(false);
        policy.setExpectedAudience("expected-client");
        policy.setRequiredRoles("admin");

        AtomicBoolean routeReached = new AtomicBoolean(false);
        KeycloakSecurityProcessor processor = new KeycloakSecurityProcessor(e -> routeReached.set(true), policy);

        assertThrows(CamelAuthorizationException.class, () -> processor.process(bearer("x")));
        assertFalse(routeReached.get(),
                "Route body must not be reached when the token has the required role but not the expected audience");
    }

    @Test
    void testTokenMissingExpectedAudienceRejectedWithRequiredPermissionsIntrospection() throws Exception {
        // Token would satisfy the required permission, but is missing the expected audience: the audience check
        // inside validatePermissions() must still reject it before the permission check ever runs.
        KeycloakTokenIntrospector introspector = new KeycloakTokenIntrospector(
                "http://localhost:8080", "test-realm", "test-client", "test-secret", (TokenCache) null) {
            @Override
            public IntrospectionResult introspect(String token) {
                return new IntrospectionResult(Map.of("active", true, "scope", "read"));
            }
        };

        KeycloakSecurityPolicy policy = new KeycloakSecurityPolicy() {
            @Override
            public boolean isUseTokenIntrospection() {
                return true;
            }

            @Override
            public KeycloakTokenIntrospector getTokenIntrospector() {
                return introspector;
            }
        };
        policy.setServerUrl("http://localhost:8080");
        policy.setRealm("test-realm");
        policy.setClientId("test-client");
        policy.setClientSecret("test-secret");
        policy.setValidateIssuer(false);
        policy.setExpectedAudience("expected-client");
        policy.setRequiredPermissions("read");

        AtomicBoolean routeReached = new AtomicBoolean(false);
        KeycloakSecurityProcessor processor = new KeycloakSecurityProcessor(e -> routeReached.set(true), policy);

        assertThrows(CamelAuthorizationException.class, () -> processor.process(bearer("x")));
        assertFalse(routeReached.get(),
                "Route body must not be reached when the token has the required permission but not the expected audience");
    }
}
