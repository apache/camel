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

import java.security.KeyPairGenerator;
import java.security.PublicKey;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
