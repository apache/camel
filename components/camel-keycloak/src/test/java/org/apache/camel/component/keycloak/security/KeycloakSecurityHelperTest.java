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
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.keycloak.common.VerificationException;
import org.keycloak.representations.AccessToken;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class KeycloakSecurityHelperTest {

    @Test
    void testExtractRoles() {
        AccessToken token = Mockito.mock(AccessToken.class);
        AccessToken.Access realmAccess = Mockito.mock(AccessToken.Access.class);
        AccessToken.Access clientAccess = Mockito.mock(AccessToken.Access.class);

        when(token.getRealmAccess()).thenReturn(realmAccess);
        when(realmAccess.getRoles()).thenReturn(Set.of("realm-admin", "user"));

        when(token.getResourceAccess()).thenReturn(java.util.Map.of("test-client", clientAccess));
        when(clientAccess.getRoles()).thenReturn(Set.of("client-admin"));

        Set<String> roles = KeycloakSecurityHelper.extractRoles(token, "test-realm", "test-client");

        assertEquals(3, roles.size());
        assertTrue(roles.contains("realm-admin"));
        assertTrue(roles.contains("user"));
        assertTrue(roles.contains("client-admin"));
    }

    @Test
    void testExtractUsername() {
        AccessToken token = Mockito.mock(AccessToken.class);
        when(token.getPreferredUsername()).thenReturn("testuser");

        String username = KeycloakSecurityHelper.extractUsername(token);
        assertEquals("testuser", username);
    }

    @Test
    void testExtractUsernameFallback() {
        AccessToken token = Mockito.mock(AccessToken.class);
        when(token.getPreferredUsername()).thenReturn(null);
        when(token.getName()).thenReturn("Test User");

        String username = KeycloakSecurityHelper.extractUsername(token);
        assertEquals("Test User", username);
    }

    @Test
    void testExtractUsernameSubjectFallback() {
        AccessToken token = Mockito.mock(AccessToken.class);
        when(token.getPreferredUsername()).thenReturn(null);
        when(token.getName()).thenReturn(null);
        when(token.getSubject()).thenReturn("subject-123");

        String username = KeycloakSecurityHelper.extractUsername(token);
        assertEquals("subject-123", username);
    }

    @Test
    void testExtractEmail() {
        AccessToken token = Mockito.mock(AccessToken.class);
        when(token.getEmail()).thenReturn("test@example.com");

        String email = KeycloakSecurityHelper.extractEmail(token);
        assertEquals("test@example.com", email);
    }

    @Test
    void testIsTokenExpired() {
        AccessToken token = Mockito.mock(AccessToken.class);

        // Test with future expiry
        long futureTime = (System.currentTimeMillis() / 1000) + 3600; // 1 hour from now
        when(token.getExp()).thenReturn(futureTime);
        assertFalse(KeycloakSecurityHelper.isTokenExpired(token));

        // Test with past expiry
        long pastTime = (System.currentTimeMillis() / 1000) - 3600; // 1 hour ago
        when(token.getExp()).thenReturn(pastTime);
        assertTrue(KeycloakSecurityHelper.isTokenExpired(token));

        // Test with no expiry
        when(token.getExp()).thenReturn(null);
        assertFalse(KeycloakSecurityHelper.isTokenExpired(token));
    }

    @Test
    void testIsTokenActive() {
        AccessToken token = Mockito.mock(AccessToken.class);

        // Test active token
        long futureTime = (System.currentTimeMillis() / 1000) + 3600; // 1 hour from now
        when(token.getExp()).thenReturn(futureTime);
        when(token.getNbf()).thenReturn(null);
        assertTrue(KeycloakSecurityHelper.isTokenActive(token));

        // Test expired token
        long pastTime = (System.currentTimeMillis() / 1000) - 3600; // 1 hour ago
        when(token.getExp()).thenReturn(pastTime);
        assertFalse(KeycloakSecurityHelper.isTokenActive(token));

        // Test token not yet valid
        long futureNbf = (System.currentTimeMillis() / 1000) + 3600; // 1 hour from now
        when(token.getExp()).thenReturn(futureTime);
        when(token.getNbf()).thenReturn(futureNbf);
        assertFalse(KeycloakSecurityHelper.isTokenActive(token));
    }

    @Test
    void testParseAndVerifyAccessTokenWithInvalidToken() throws Exception {
        // Test that verification fails with invalid token
        String invalidToken = "invalid.jwt.token";
        String expectedIssuer = "http://localhost:8080/realms/test";

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();

        assertThrows(VerificationException.class, () -> {
            KeycloakSecurityHelper.parseAndVerifyAccessToken(invalidToken, publicKey, expectedIssuer);
        });
    }

    @Test
    void testParseAndVerifyAccessTokenWithNullKey() {
        String invalidToken = "invalid.jwt.token";
        String expectedIssuer = "http://localhost:8080/realms/test";

        // Should throw exception with null key
        assertThrows(VerificationException.class, () -> {
            KeycloakSecurityHelper.parseAndVerifyAccessToken(invalidToken, null, expectedIssuer);
        });
    }

    @Test
    void testParseAndVerifyAccessTokenWithNullIssuer() throws Exception {
        String invalidToken = "invalid.jwt.token";

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();

        // Should throw exception with null issuer
        assertThrows(VerificationException.class, () -> {
            KeycloakSecurityHelper.parseAndVerifyAccessToken(invalidToken, publicKey, null);
        });
    }

    @Test
    void testExtractPermissions() {
        AccessToken token = Mockito.mock(AccessToken.class);

        // Mock authorization (simple approach without specific permission structure)
        AccessToken.Authorization authorization = Mockito.mock(AccessToken.Authorization.class);
        when(token.getAuthorization()).thenReturn(authorization);

        // Test permissions extraction from custom claims
        java.util.Map<String, Object> otherClaims = new java.util.HashMap<>();
        otherClaims.put("permissions", java.util.Arrays.asList("read:documents", "write:documents", "admin:users"));

        when(token.getOtherClaims()).thenReturn(otherClaims);

        java.util.Set<String> permissions = KeycloakSecurityHelper.extractPermissions(token);

        assertEquals(3, permissions.size());
        assertTrue(permissions.contains("read:documents"));
        assertTrue(permissions.contains("write:documents"));
        assertTrue(permissions.contains("admin:users"));
    }

    @Test
    void testExtractPermissionsFromCustomClaims() {
        AccessToken token = Mockito.mock(AccessToken.class);

        // Mock other claims with permissions
        java.util.Map<String, Object> otherClaims = new java.util.HashMap<>();
        otherClaims.put("permissions", java.util.Arrays.asList("read:files", "write:files", "delete:files"));

        when(token.getOtherClaims()).thenReturn(otherClaims);
        when(token.getAuthorization()).thenReturn(null);

        java.util.Set<String> permissions = KeycloakSecurityHelper.extractPermissions(token);

        assertEquals(3, permissions.size());
        assertTrue(permissions.contains("read:files"));
        assertTrue(permissions.contains("write:files"));
        assertTrue(permissions.contains("delete:files"));
    }

    @Test
    void testExtractPermissionsFromScopes() {
        AccessToken token = Mockito.mock(AccessToken.class);

        // Mock other claims with scope-based permissions
        java.util.Map<String, Object> otherClaims = new java.util.HashMap<>();
        otherClaims.put("scope", "read write admin");

        when(token.getOtherClaims()).thenReturn(otherClaims);
        when(token.getAuthorization()).thenReturn(null);

        java.util.Set<String> permissions = KeycloakSecurityHelper.extractPermissions(token);

        assertEquals(3, permissions.size());
        assertTrue(permissions.contains("read"));
        assertTrue(permissions.contains("write"));
        assertTrue(permissions.contains("admin"));
    }

    @Test
    void testExtractPermissionsEmpty() {
        AccessToken token = Mockito.mock(AccessToken.class);
        when(token.getAuthorization()).thenReturn(null);
        when(token.getOtherClaims()).thenReturn(java.util.Map.of());

        java.util.Set<String> permissions = KeycloakSecurityHelper.extractPermissions(token);

        assertTrue(permissions.isEmpty());
    }

}
