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

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KeycloakTokenIntrospectorTest {

    @Test
    public void testIntrospectionResultActive() {
        Map<String, Object> claims = Map.of(
                "active", true,
                "sub", "user123",
                "username", "testuser",
                "client_id", "test-client",
                "scope", "openid profile email",
                "exp", 1234567890L,
                "iat", 1234567800L);

        KeycloakTokenIntrospector.IntrospectionResult result = new KeycloakTokenIntrospector.IntrospectionResult(claims);

        assertTrue(result.isActive());
        assertEquals("user123", result.getSubject());
        assertEquals("testuser", result.getUsername());
        assertEquals("test-client", result.getClientId());
        assertEquals("openid profile email", result.getScope());
        assertEquals(1234567890L, result.getExpiration());
        assertEquals(1234567800L, result.getIssuedAt());
    }

    @Test
    public void testIntrospectionResultInactive() {
        Map<String, Object> claims = Map.of("active", false);

        KeycloakTokenIntrospector.IntrospectionResult result = new KeycloakTokenIntrospector.IntrospectionResult(claims);

        assertFalse(result.isActive());
        assertNull(result.getSubject());
        assertNull(result.getUsername());
    }

    @Test
    public void testExtractRolesFromIntrospection() {
        Map<String, Object> claims = Map.of(
                "active", true,
                "realm_access", Map.of("roles", java.util.List.of("admin", "user")),
                "resource_access", Map.of(
                        "test-client", Map.of("roles", java.util.List.of("client-admin"))));

        KeycloakTokenIntrospector.IntrospectionResult result = new KeycloakTokenIntrospector.IntrospectionResult(claims);

        Set<String> roles = KeycloakSecurityHelper.extractRolesFromIntrospection(result, "test-realm", "test-client");

        assertNotNull(roles);
        assertEquals(3, roles.size());
        assertTrue(roles.contains("admin"));
        assertTrue(roles.contains("user"));
        assertTrue(roles.contains("client-admin"));
    }

    @Test
    public void testExtractPermissionsFromIntrospection() {
        Map<String, Object> claims = Map.of(
                "active", true,
                "scope", "read:documents write:documents",
                "permissions", java.util.List.of("admin:all"));

        KeycloakTokenIntrospector.IntrospectionResult result = new KeycloakTokenIntrospector.IntrospectionResult(claims);

        Set<String> permissions = KeycloakSecurityHelper.extractPermissionsFromIntrospection(result);

        assertNotNull(permissions);
        assertEquals(3, permissions.size());
        assertTrue(permissions.contains("read:documents"));
        assertTrue(permissions.contains("write:documents"));
        assertTrue(permissions.contains("admin:all"));
    }

    @Test
    public void testGetAllClaims() {
        Map<String, Object> claims = Map.of(
                "active", true,
                "sub", "user123",
                "custom_claim", "custom_value");

        KeycloakTokenIntrospector.IntrospectionResult result = new KeycloakTokenIntrospector.IntrospectionResult(claims);

        Map<String, Object> allClaims = result.getAllClaims();
        assertNotNull(allClaims);
        assertEquals(3, allClaims.size());
        assertEquals("custom_value", allClaims.get("custom_claim"));
    }

    @Test
    public void testGetClaim() {
        Map<String, Object> claims = Map.of(
                "active", true,
                "custom_claim", "custom_value");

        KeycloakTokenIntrospector.IntrospectionResult result = new KeycloakTokenIntrospector.IntrospectionResult(claims);

        assertEquals("custom_value", result.getClaim("custom_claim"));
        assertNull(result.getClaim("nonexistent_claim"));
    }

    @Test
    public void testTokenType() {
        Map<String, Object> claims = Map.of(
                "active", true,
                "token_type", "Bearer");

        KeycloakTokenIntrospector.IntrospectionResult result = new KeycloakTokenIntrospector.IntrospectionResult(claims);

        assertEquals("Bearer", result.getTokenType());
    }
}
