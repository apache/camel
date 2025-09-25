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

import java.util.Set;

import org.junit.jupiter.api.Test;
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
}
