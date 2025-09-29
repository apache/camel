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

import java.security.PublicKey;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.keycloak.TokenVerifier;
import org.keycloak.common.VerificationException;
import org.keycloak.representations.AccessToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KeycloakSecurityHelper {
    private static final Logger LOG = LoggerFactory.getLogger(KeycloakSecurityHelper.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private KeycloakSecurityHelper() {
        // Utility class
    }

    public static AccessToken parseAccessToken(String tokenString) throws VerificationException {
        return parseAccessToken(tokenString, null);
    }

    public static AccessToken parseAccessToken(String tokenString, PublicKey publicKey) throws VerificationException {
        if (publicKey != null) {
            return TokenVerifier.create(tokenString, AccessToken.class)
                    .publicKey(publicKey)
                    .verify()
                    .getToken();
        } else {
            return TokenVerifier.create(tokenString, AccessToken.class).getToken();
        }
    }

    public static Set<String> extractRoles(AccessToken token, String realm, String clientId) {
        Set<String> roles = new HashSet<>();

        // Extract realm roles
        if (token.getRealmAccess() != null && token.getRealmAccess().getRoles() != null) {
            roles.addAll(token.getRealmAccess().getRoles());
        }

        // Extract client roles
        if (token.getResourceAccess() != null) {
            AccessToken.Access clientAccess = token.getResourceAccess().get(clientId);
            if (clientAccess != null && clientAccess.getRoles() != null) {
                roles.addAll(clientAccess.getRoles());
            }
        }

        return roles;
    }

    public static String extractUsername(AccessToken token) {
        String username = token.getPreferredUsername();
        if (username == null) {
            username = token.getName();
        }
        if (username == null) {
            username = token.getSubject();
        }
        return username;
    }

    public static String extractEmail(AccessToken token) {
        return token.getEmail();
    }

    public static Map<String, Object> extractUserInfo(AccessToken token) {
        try {
            String tokenJson = OBJECT_MAPPER.writeValueAsString(token);
            JsonNode tokenNode = OBJECT_MAPPER.readTree(tokenJson);
            return OBJECT_MAPPER.convertValue(tokenNode, Map.class);
        } catch (Exception e) {
            LOG.warn("Failed to extract user info from token", e);
            return Map.of(
                    "sub", token.getSubject() != null ? token.getSubject() : "",
                    "preferred_username", token.getPreferredUsername() != null ? token.getPreferredUsername() : "",
                    "email", token.getEmail() != null ? token.getEmail() : "");
        }
    }

    public static boolean isTokenExpired(AccessToken token) {
        if (token.getExp() == null) {
            return false;
        }
        long currentTime = System.currentTimeMillis() / 1000;
        return token.getExp() < currentTime;
    }

    public static boolean isTokenActive(AccessToken token) {
        if (isTokenExpired(token)) {
            return false;
        }

        // Check if token is not yet valid
        if (token.getNbf() != null) {
            long currentTime = System.currentTimeMillis() / 1000;
            return token.getNbf() <= currentTime;
        }

        return true;
    }

    public static Set<String> extractPermissions(AccessToken token) {
        Set<String> permissions = new HashSet<>();

        // Extract permissions from custom claims (primary approach for simple setups)
        Object permissionsClaim = token.getOtherClaims().get("permissions");
        if (permissionsClaim instanceof java.util.Collection<?>) {
            @SuppressWarnings("unchecked")
            java.util.Collection<String> permissionsCollection = (java.util.Collection<String>) permissionsClaim;
            permissions.addAll(permissionsCollection);
        }

        // Also check for scope-based permissions
        Object scopesClaim = token.getOtherClaims().get("scope");
        if (scopesClaim instanceof String) {
            String scopesString = (String) scopesClaim;
            if (!scopesString.isEmpty()) {
                permissions.addAll(java.util.Arrays.asList(scopesString.split(" ")));
            }
        }

        return permissions;
    }
}
