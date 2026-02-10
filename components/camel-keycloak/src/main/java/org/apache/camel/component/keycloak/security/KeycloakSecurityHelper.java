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

    /**
     * Parses and fully verifies an access token including signature and issuer validation. This is the recommended
     * method for secure token validation.
     *
     * @param  tokenString           the JWT token string
     * @param  publicKey             the public key for signature verification
     * @param  expectedIssuer        the expected issuer URL (e.g., "http://localhost:8080/realms/myrealm")
     * @return                       the verified access token
     * @throws VerificationException if verification fails (invalid signature, wrong issuer, expired, etc.)
     */
    public static AccessToken parseAndVerifyAccessToken(String tokenString, PublicKey publicKey, String expectedIssuer)
            throws VerificationException {
        if (publicKey == null) {
            throw new VerificationException("Public key is required for secure token verification");
        }
        if (expectedIssuer == null || expectedIssuer.isEmpty()) {
            throw new VerificationException("Expected issuer is required for secure token verification");
        }

        TokenVerifier<AccessToken> verifier = TokenVerifier.create(tokenString, AccessToken.class)
                .publicKey(publicKey)
                .withChecks(
                        TokenVerifier.SUBJECT_EXISTS_CHECK,
                        new TokenVerifier.RealmUrlCheck(expectedIssuer));

        AccessToken token = verifier.verify().getToken();

        // Additional explicit issuer check for defense in depth
        String actualIssuer = token.getIssuer();
        if (!expectedIssuer.equals(actualIssuer)) {
            LOG.error("SECURITY: Token issuer mismatch - expected '{}' but got '{}'", expectedIssuer, actualIssuer);
            throw new VerificationException(
                    String.format("Token issuer mismatch: expected '%s' but got '%s'", expectedIssuer, actualIssuer));
        }

        LOG.debug("Token successfully verified for issuer: {}", expectedIssuer);
        return token;
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
        if (permissionsClaim instanceof java.util.Collection<?> permissionsCollection) {
            for (Object perm : permissionsCollection) {
                if (perm instanceof String s) {
                    permissions.add(s);
                }
            }
        }

        // Also check for scope-based permissions
        Object scopesClaim = token.getOtherClaims().get("scope");
        if (scopesClaim instanceof String scopesString) {
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
        if (realmAccess instanceof Map<?, ?> realmAccessMap) {
            Object realmRoles = realmAccessMap.get("roles");
            if (realmRoles instanceof Collection<?> realmRolesCollection) {
                for (Object role : realmRolesCollection) {
                    if (role instanceof String s) {
                        roles.add(s);
                    }
                }
            }
        }

        // Extract client roles from resource_access claim
        Object resourceAccess = introspectionResult.getClaim("resource_access");
        if (resourceAccess instanceof Map<?, ?> resourceAccessMap) {
            Object clientAccess = resourceAccessMap.get(clientId);
            if (clientAccess instanceof Map<?, ?> clientAccessMap) {
                Object clientRoles = clientAccessMap.get("roles");
                if (clientRoles instanceof Collection<?> clientRolesCollection) {
                    for (Object role : clientRolesCollection) {
                        if (role instanceof String s) {
                            roles.add(s);
                        }
                    }
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
        if (permissionsClaim instanceof Collection<?> permissionsCollection) {
            for (Object perm : permissionsCollection) {
                if (perm instanceof String s) {
                    permissions.add(s);
                }
            }
        }

        // Also check for scope-based permissions
        String scope = introspectionResult.getScope();
        if (scope != null && !scope.isEmpty()) {
            permissions.addAll(java.util.Arrays.asList(scope.split(" ")));
        }

        return permissions;
    }
}
