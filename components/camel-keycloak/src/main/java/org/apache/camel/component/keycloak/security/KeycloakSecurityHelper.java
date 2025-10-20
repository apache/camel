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

    /**
     * Validates a token using OAuth 2.0 token introspection.
     *
     * @param  token             the access token to validate
     * @param  tokenIntrospector the introspector to use
     * @return                   the introspection result
     * @throws IOException       if introspection fails
     */
    public static KeycloakTokenIntrospector.IntrospectionResult introspectToken(
            String token, KeycloakTokenIntrospector tokenIntrospector)
            throws IOException {
        return tokenIntrospector.introspect(token);
    }

    /**
     * Extracts roles from an introspection result.
     *
     * @param  introspectionResult the introspection result
     * @param  realm               the realm name
     * @param  clientId            the client ID
     * @return                     set of roles
     */
    public static Set<String> extractRolesFromIntrospection(
            KeycloakTokenIntrospector.IntrospectionResult introspectionResult,
            String realm, String clientId) {
        Set<String> roles = new HashSet<>();

        // Extract roles from realm_access claim
        Object realmAccess = introspectionResult.getClaim("realm_access");
        if (realmAccess instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> realmAccessMap = (Map<String, Object>) realmAccess;
            Object realmRoles = realmAccessMap.get("roles");
            if (realmRoles instanceof Collection) {
                @SuppressWarnings("unchecked")
                Collection<String> realmRolesCollection = (Collection<String>) realmRoles;
                roles.addAll(realmRolesCollection);
            }
        }

        // Extract client roles from resource_access claim
        Object resourceAccess = introspectionResult.getClaim("resource_access");
        if (resourceAccess instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resourceAccessMap = (Map<String, Object>) resourceAccess;
            Object clientAccess = resourceAccessMap.get(clientId);
            if (clientAccess instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> clientAccessMap = (Map<String, Object>) clientAccess;
                Object clientRoles = clientAccessMap.get("roles");
                if (clientRoles instanceof Collection) {
                    @SuppressWarnings("unchecked")
                    Collection<String> clientRolesCollection = (Collection<String>) clientRoles;
                    roles.addAll(clientRolesCollection);
                }
            }
        }

        return roles;
    }

    /**
     * Extracts permissions from an introspection result.
     *
     * @param  introspectionResult the introspection result
     * @return                     set of permissions
     */
    public static Set<String> extractPermissionsFromIntrospection(
            KeycloakTokenIntrospector.IntrospectionResult introspectionResult) {
        Set<String> permissions = new HashSet<>();

        // Extract permissions from custom claims
        Object permissionsClaim = introspectionResult.getClaim("permissions");
        if (permissionsClaim instanceof Collection) {
            @SuppressWarnings("unchecked")
            Collection<String> permissionsCollection = (Collection<String>) permissionsClaim;
            permissions.addAll(permissionsCollection);
        }

        // Also check for scope-based permissions
        String scope = introspectionResult.getScope();
        if (scope != null && !scope.isEmpty()) {
            permissions.addAll(java.util.Arrays.asList(scope.split(" ")));
        }

        return permissions;
    }
}
