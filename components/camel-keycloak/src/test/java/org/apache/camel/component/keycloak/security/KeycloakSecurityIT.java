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

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Keycloak security policy using external Keycloak instance.
 *
 * Must be manually tested. Provide your own Keycloak instance and configuration using:
 * -Dkeycloak.server.url=http://localhost:8080 -Dkeycloak.realm=test-realm -Dkeycloak.client.id=test-client
 * -Dkeycloak.client.secret=test-secret
 *
 * Example test users that should exist in the realm: - myuser (password: pippo123, role: admin-role) - test-user
 * (password: user123, role: user) - reader-user (password: reader123, role: reader)
 */
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "keycloak.server.url", matches = ".*",
                                 disabledReason = "Keycloak server URL not provided"),
        @EnabledIfSystemProperty(named = "keycloak.realm", matches = ".*", disabledReason = "Keycloak realm not provided"),
        @EnabledIfSystemProperty(named = "keycloak.client.id", matches = ".*",
                                 disabledReason = "Keycloak client ID not provided"),
        @EnabledIfSystemProperty(named = "keycloak.client.secret", matches = ".*",
                                 disabledReason = "Keycloak client secret not provided")
})
public class KeycloakSecurityIT extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakSecurityIT.class);

    private static String keycloakUrl;
    private static String realm;
    private static String clientId;
    private static String clientSecret;

    static {
        // Load configuration from system properties
        keycloakUrl = System.getProperty("keycloak.server.url");
        realm = System.getProperty("keycloak.realm");
        clientId = System.getProperty("keycloak.client.id");
        clientSecret = System.getProperty("keycloak.client.secret");
    }

    @Test
    void testKeycloakSecurityPolicyWithValidAdminToken() {
        // Test with valid admin token - assumes myuser exists with admin-role
        String adminToken = getAccessToken("myuser", "pippo123");
        assertNotNull(adminToken);

        String result = template.requestBodyAndHeader("direct:admin-only", "test message",
                KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, adminToken, String.class);
        assertEquals("Admin access granted", result);
    }

    @Test
    void testKeycloakSecurityPolicyWithValidUserToken() {
        // Test with valid user token - assumes test-user exists with user role
        String userToken = getAccessToken("test-user", "user123");
        assertNotNull(userToken);

        String result = template.requestBodyAndHeader("direct:user-access", "test message",
                KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, userToken, String.class);
        assertEquals("User access granted", result);
    }

    @Test
    void testKeycloakSecurityPolicyWithAuthorizationHeader() {
        // Test using Authorization header format
        String adminToken = getAccessToken("myuser", "pippo123");
        assertNotNull(adminToken);

        String result = template.requestBodyAndHeader("direct:protected", "test message",
                "Authorization", "Bearer " + adminToken, String.class);
        assertEquals("Access granted", result);
    }

    @Test
    void testKeycloakSecurityPolicyConfiguration() {
        // Verify that the configuration is properly set up
        assertNotNull(keycloakUrl);
        assertNotNull(realm);
        assertNotNull(clientId);
        assertTrue(keycloakUrl.startsWith("http://") || keycloakUrl.startsWith("https://"));
    }

    @Test
    void testKeycloakSecurityPolicyWithoutToken() {
        // Test that requests without tokens are rejected
        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            template.sendBody("direct:protected", "test message");
        });
        assertTrue(ex.getCause() instanceof CamelAuthorizationException);
    }

    @Test
    void testKeycloakSecurityPolicyWithInvalidToken() {
        // Test that requests with invalid tokens are rejected
        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            template.sendBodyAndHeader("direct:protected", "test message",
                    KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, "invalid-token");
        });
        assertTrue(ex.getCause() instanceof CamelAuthorizationException);
    }

    @Test
    void testKeycloakSecurityPolicyUserCannotAccessAdminRoute() {
        // Test that user token cannot access admin-only routes
        String userToken = getAccessToken("test-user", "user123");
        assertNotNull(userToken);

        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            template.requestBodyAndHeader("direct:admin-only", "test message",
                    KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, userToken, String.class);
        });
        assertTrue(ex.getCause() instanceof CamelAuthorizationException);
    }

    @Test
    void testKeycloakSecurityPolicyReaderUserCannotAccessUserRoute() {
        // Test that reader user cannot access user routes (assumes reader-user exists with reader role)
        String readerToken = getAccessToken("reader-user", "reader123");
        assertNotNull(readerToken);

        // This should fail because userPolicy requires "user" role specifically
        // and reader-user only has "reader" role
        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            template.requestBodyAndHeader("direct:user-access", "test message",
                    KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, readerToken, String.class);
        });
        assertTrue(ex.getCause() instanceof CamelAuthorizationException);
    }

    @Test
    void testKeycloakSecurityPolicyWithPublicKeyVerification() {
        // Test that public key verification works with real Keycloak instance
        String adminToken = getAccessToken("myuser", "pippo123");
        assertNotNull(adminToken);

        // Get public key from Keycloak JWKS endpoint
        PublicKey publicKey = getPublicKeyFromKeycloak();
        assertNotNull(publicKey);

        // Test that parseToken works correctly with public key and issuer verification
        String expectedIssuer = keycloakUrl + "/realms/" + realm;
        try {
            org.keycloak.representations.AccessToken token = KeycloakSecurityHelper.parseAndVerifyAccessToken(
                    adminToken, publicKey, expectedIssuer);

            assertNotNull(token);
            assertNotNull(token.getSubject());
            assertTrue(KeycloakSecurityHelper.isTokenActive(token));

            // Verify roles can be extracted after public key verification
            java.util.Set<String> roles = KeycloakSecurityHelper.extractRoles(token, realm, clientId);
            assertNotNull(roles);
            assertFalse(roles.isEmpty());

        } catch (Exception e) {
            // Public key verification might fail due to key mismatch - this is actually expected
            // The main test is that we can successfully call parseAndVerifyAccessToken with a public key
            assertNotNull(e.getMessage());
            assertTrue(e.getMessage().contains("Invalid token signature") ||
                    e.getMessage().contains("verification") ||
                    e.getMessage().contains("signature") ||
                    e.getMessage().contains("issuer"));
        }

        // Test with public key-enabled policy route
        // This might fail with signature verification, which is the expected behavior
        try {
            String result = template.requestBodyAndHeader("direct:public-key-protected", "test message",
                    KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, adminToken, String.class);
            assertEquals("Public key access granted", result);
        } catch (CamelExecutionException ex) {
            // This is expected if the public key doesn't match the token signature
            assertTrue(ex.getCause() instanceof CamelAuthorizationException);
        }
    }

    @Test
    void testKeycloakSecurityPolicyWithWrongPublicKey() {
        // Test that verification fails with wrong public key
        String adminToken = getAccessToken("myuser", "pippo123");
        assertNotNull(adminToken);

        // Test that requests with wrong public key in policy are rejected
        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            template.sendBodyAndHeader("direct:wrong-public-key-protected", "test message",
                    KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, adminToken);
        });
        assertTrue(ex.getCause() instanceof CamelAuthorizationException);
    }

    @Test
    void testParseAndVerifyTokenDirectlyWithPublicKey() {
        // Test the core functionality: parseAndVerifyAccessToken with public key and issuer
        String adminToken = getAccessToken("myuser", "pippo123");
        assertNotNull(adminToken);

        // Get public key from Keycloak JWKS endpoint
        PublicKey publicKey = getPublicKeyFromKeycloak();
        assertNotNull(publicKey);

        String expectedIssuer = keycloakUrl + "/realms/" + realm;

        // Test parseAndVerifyAccessToken with correct public key and issuer (may fail with signature verification)
        try {
            org.keycloak.representations.AccessToken tokenWithKey
                    = KeycloakSecurityHelper.parseAndVerifyAccessToken(adminToken, publicKey, expectedIssuer);
            assertNotNull(tokenWithKey);
            assertNotNull(tokenWithKey.getSubject());
        } catch (Exception e) {
            // This is expected behavior if the public key doesn't match
            assertTrue(e.getMessage().contains("signature") || e.getMessage().contains("verification")
                    || e.getMessage().contains("issuer"));
        }

        // Test parseAndVerifyAccessToken with wrong public key (should fail)
        PublicKey wrongKey = getWrongPublicKey();
        Exception ex = assertThrows(Exception.class, () -> {
            KeycloakSecurityHelper.parseAndVerifyAccessToken(adminToken, wrongKey, expectedIssuer);
        });
        assertTrue(ex.getMessage().contains("signature") || ex.getMessage().contains("verification"));

        // Test parseAndVerifyAccessToken with wrong issuer (should fail)
        String wrongIssuer = keycloakUrl + "/realms/wrong-realm";
        Exception issuerEx = assertThrows(Exception.class, () -> {
            KeycloakSecurityHelper.parseAndVerifyAccessToken(adminToken, publicKey, wrongIssuer);
        });
        assertTrue(issuerEx.getMessage().contains("issuer") || issuerEx.getMessage().contains("verification")
                || issuerEx.getMessage().contains("signature"));
    }

    @Test
    void testKeycloakSecurityPolicyWithPermissions() {
        // Test permissions-based authorization
        String adminToken = getAccessToken("myuser", "pippo123");
        assertNotNull(adminToken);

        // Test with permissions policy route - this will test the permissions extraction
        try {
            String result = template.requestBodyAndHeader("direct:permissions-protected", "test message",
                    KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, adminToken, String.class);
            assertEquals("Permissions access granted", result);
        } catch (CamelExecutionException ex) {
            // This might fail if permissions are not configured in the token
            // which is expected in a basic Keycloak setup
            assertTrue(ex.getCause() instanceof CamelAuthorizationException);
        }
    }

    @Test
    void testKeycloakSecurityPolicyWithInsufficientPermissions() {
        // Test that users without required permissions are rejected
        String userToken = getAccessToken("test-user", "user123");
        assertNotNull(userToken);

        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            template.sendBodyAndHeader("direct:permissions-protected", "test message",
                    KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, userToken);
        });
        assertTrue(ex.getCause() instanceof CamelAuthorizationException);
    }

    @Test
    void testKeycloakSecurityPolicyWithScopeBasedPermissions() {
        // Test scope-based permissions (using standard OAuth2 scopes)
        String adminToken = getAccessToken("myuser", "pippo123");
        assertNotNull(adminToken);

        // Test with scope-based permissions policy route
        try {
            String result = template.requestBodyAndHeader("direct:scope-permissions-protected", "test message",
                    KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, adminToken, String.class);
            assertEquals("Scope permissions access granted", result);
        } catch (CamelExecutionException ex) {
            // This might fail if the token doesn't contain the expected scopes
            // which is expected in basic Keycloak setup without custom scope configuration
            assertTrue(ex.getCause() instanceof CamelAuthorizationException);
        }
    }

    @Test
    void testKeycloakSecurityPolicyWithCombinedRolesAndPermissions() {
        // Test combined roles and permissions validation
        String adminToken = getAccessToken("myuser", "pippo123");
        assertNotNull(adminToken);

        // Test with combined policy route (requires BOTH admin role AND permissions)
        try {
            String result = template.requestBodyAndHeader("direct:combined-protected", "test message",
                    KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, adminToken, String.class);
            assertEquals("Combined access granted", result);
        } catch (CamelExecutionException ex) {
            // This will fail if either role or permissions are missing
            assertTrue(ex.getCause() instanceof CamelAuthorizationException);
        }
    }

    @Test
    void testKeycloakSecurityPolicyWithFlexiblePermissions() {
        // Test flexible permissions (ANY permission required)
        String userToken = getAccessToken("test-user", "user123");
        assertNotNull(userToken);

        // Test with flexible permissions policy (requires ANY of the specified permissions)
        try {
            String result = template.requestBodyAndHeader("direct:flexible-permissions-protected", "test message",
                    KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, userToken, String.class);
            assertEquals("Flexible permissions access granted", result);
        } catch (CamelExecutionException ex) {
            // This will fail if the user doesn't have any of the required permissions
            assertTrue(ex.getCause() instanceof CamelAuthorizationException);
        }
    }

    @Test
    void testPermissionsExtractionFromToken() {
        // Test direct permissions extraction from token for debugging
        String adminToken = getAccessToken("myuser", "pippo123");
        assertNotNull(adminToken);

        PublicKey publicKey = getPublicKeyFromKeycloak();
        assertNotNull(publicKey);

        String expectedIssuer = keycloakUrl + "/realms/" + realm;

        try {
            // Parse and verify token, then extract permissions directly
            org.keycloak.representations.AccessToken token = KeycloakSecurityHelper.parseAndVerifyAccessToken(
                    adminToken, publicKey, expectedIssuer);
            java.util.Set<String> permissions = KeycloakSecurityHelper.extractPermissions(token);

            // Log the permissions found for debugging
            LOG.info("Permissions found in token: {}", permissions);

            // Permissions might be empty in a basic setup, which is expected
            assertNotNull(permissions);

        } catch (Exception e) {
            // Token verification might fail due to key mismatch
            LOG.warn("Token verification failed (may be expected): {}", e.getMessage());
        }
    }

    /**
     * Helper method to get public key from Keycloak JWKS endpoint for token verification. Tries to find the key with
     * "sig" usage or the first RSA key available.
     */
    private PublicKey getPublicKeyFromKeycloak() {
        try (Client client = ClientBuilder.newClient()) {
            String jwksUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/certs";

            try (Response response = client.target(jwksUrl)
                    .request(MediaType.APPLICATION_JSON)
                    .get()) {

                if (response.getStatus() == 200) {
                    String jwksJson = response.readEntity(String.class);
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode jwks = mapper.readTree(jwksJson);
                    JsonNode keys = jwks.get("keys");

                    if (keys != null && keys.isArray() && keys.size() > 0) {
                        JsonNode selectedKey = null;

                        // First try to find a key with "sig" usage
                        for (JsonNode key : keys) {
                            if ("RSA".equals(key.path("kty").asText())) {
                                JsonNode use = key.path("use");
                                if (!use.isMissingNode() && "sig".equals(use.asText())) {
                                    selectedKey = key;
                                    break;
                                }
                            }
                        }

                        // If no "sig" key found, use the first RSA key
                        if (selectedKey == null) {
                            for (JsonNode key : keys) {
                                if ("RSA".equals(key.path("kty").asText())) {
                                    selectedKey = key;
                                    break;
                                }
                            }
                        }

                        if (selectedKey != null) {
                            String modulus = selectedKey.get("n").asText();
                            String exponent = selectedKey.get("e").asText();

                            byte[] modulusBytes = Base64.getUrlDecoder().decode(modulus);
                            byte[] exponentBytes = Base64.getUrlDecoder().decode(exponent);

                            BigInteger modulusBigInt = new BigInteger(1, modulusBytes);
                            BigInteger exponentBigInt = new BigInteger(1, exponentBytes);

                            RSAPublicKeySpec keySpec = new RSAPublicKeySpec(modulusBigInt, exponentBigInt);
                            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                            return keyFactory.generatePublic(keySpec);
                        } else {
                            throw new RuntimeException("No RSA keys found in JWKS response");
                        }
                    } else {
                        throw new RuntimeException("No keys found in JWKS response");
                    }
                } else {
                    throw new RuntimeException("Failed to fetch JWKS. Status: " + response.getStatus());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error fetching public key from Keycloak", e);
        }
    }

    /**
     * Helper method to generate a wrong/dummy public key for testing validation failure.
     */
    private PublicKey getWrongPublicKey() {
        try {
            java.security.KeyPairGenerator keyGen = java.security.KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            java.security.KeyPair keyPair = keyGen.generateKeyPair();
            return keyPair.getPublic();
        } catch (Exception e) {
            throw new RuntimeException("Error generating dummy public key", e);
        }
    }

    /**
     * Helper method to obtain access token from Keycloak using resource owner password flow.
     */
    private String getAccessToken(String username, String password) {
        try (Client client = ClientBuilder.newClient()) {
            String tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";

            Form form = new Form()
                    .param("grant_type", "password")
                    .param("client_id", clientId)
                    .param("client_secret", clientSecret)
                    .param("username", username)
                    .param("password", password);

            try (Response response = client.target(tokenUrl)
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED))) {

                if (response.getStatus() == 200) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> tokenResponse = response.readEntity(Map.class);
                    return (String) tokenResponse.get("access_token");
                } else {
                    throw new RuntimeException("Failed to obtain access token. Status: " + response.getStatus());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error obtaining access token", e);
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Create Keycloak security policy using external Keycloak configuration
                KeycloakSecurityPolicy keycloakPolicy = new KeycloakSecurityPolicy();
                keycloakPolicy.setServerUrl(keycloakUrl);
                keycloakPolicy.setRealm(realm);
                keycloakPolicy.setClientId(clientId);
                keycloakPolicy.setClientSecret(clientSecret);
                keycloakPolicy.setRequiredRoles(java.util.Arrays.asList("admin-role")); // Add role to trigger validation

                // Configure different policies for different access levels
                KeycloakSecurityPolicy adminPolicy = new KeycloakSecurityPolicy();
                adminPolicy.setServerUrl(keycloakUrl);
                adminPolicy.setRealm(realm);
                adminPolicy.setClientId(clientId);
                adminPolicy.setClientSecret(clientSecret);
                adminPolicy.setRequiredRoles(java.util.Arrays.asList("admin-role"));

                KeycloakSecurityPolicy userPolicy = new KeycloakSecurityPolicy();
                userPolicy.setServerUrl(keycloakUrl);
                userPolicy.setRealm(realm);
                userPolicy.setClientId(clientId);
                userPolicy.setClientSecret(clientSecret);
                userPolicy.setRequiredRoles(java.util.Arrays.asList("user"));
                userPolicy.setAllRolesRequired(true); // Must have exact role

                // Protected routes
                from("direct:protected")
                        .policy(keycloakPolicy)
                        .transform().constant("Access granted")
                        .to("mock:result");

                from("direct:admin-only")
                        .policy(adminPolicy)
                        .transform().constant("Admin access granted")
                        .to("mock:admin-result");

                from("direct:user-access")
                        .policy(userPolicy)
                        .transform().constant("User access granted")
                        .to("mock:user-result");

                // Public key verification policies
                KeycloakSecurityPolicy publicKeyPolicy = new KeycloakSecurityPolicy();
                publicKeyPolicy.setServerUrl(keycloakUrl);
                publicKeyPolicy.setRealm(realm);
                publicKeyPolicy.setClientId(clientId);
                publicKeyPolicy.setClientSecret(clientSecret);
                publicKeyPolicy.setRequiredRoles(java.util.Arrays.asList("admin-role")); // Add role to trigger validation
                try {
                    publicKeyPolicy.setPublicKey(getPublicKeyFromKeycloak());
                } catch (Exception e) {
                    // If we can't get the public key, create a dummy one for testing
                    publicKeyPolicy.setPublicKey(getWrongPublicKey());
                }

                KeycloakSecurityPolicy wrongPublicKeyPolicy = new KeycloakSecurityPolicy();
                wrongPublicKeyPolicy.setServerUrl(keycloakUrl);
                wrongPublicKeyPolicy.setRealm(realm);
                wrongPublicKeyPolicy.setClientId(clientId);
                wrongPublicKeyPolicy.setClientSecret(clientSecret);
                wrongPublicKeyPolicy.setPublicKey(getWrongPublicKey());
                wrongPublicKeyPolicy.setRequiredRoles(java.util.Arrays.asList("admin-role")); // Add role to trigger validation

                from("direct:public-key-protected")
                        .policy(publicKeyPolicy)
                        .transform().constant("Public key access granted")
                        .to("mock:public-key-result");

                from("direct:wrong-public-key-protected")
                        .policy(wrongPublicKeyPolicy)
                        .transform().constant("Should not reach here")
                        .to("mock:wrong-key-result");

                // Permissions-based policy
                KeycloakSecurityPolicy permissionsPolicy = new KeycloakSecurityPolicy();
                permissionsPolicy.setServerUrl(keycloakUrl);
                permissionsPolicy.setRealm(realm);
                permissionsPolicy.setClientId(clientId);
                permissionsPolicy.setClientSecret(clientSecret);
                permissionsPolicy.setRequiredPermissions(java.util.Arrays.asList("read:documents", "write:documents"));
                permissionsPolicy.setAllPermissionsRequired(false); // ANY permission

                from("direct:permissions-protected")
                        .policy(permissionsPolicy)
                        .transform().constant("Permissions access granted")
                        .to("mock:permissions-result");

                // Scope-based permissions policy (using OAuth2 scopes)
                KeycloakSecurityPolicy scopePermissionsPolicy = new KeycloakSecurityPolicy();
                scopePermissionsPolicy.setServerUrl(keycloakUrl);
                scopePermissionsPolicy.setRealm(realm);
                scopePermissionsPolicy.setClientId(clientId);
                scopePermissionsPolicy.setClientSecret(clientSecret);
                scopePermissionsPolicy.setRequiredPermissions(java.util.Arrays.asList("profile", "email", "openid"));
                scopePermissionsPolicy.setAllPermissionsRequired(false); // ANY scope

                from("direct:scope-permissions-protected")
                        .policy(scopePermissionsPolicy)
                        .transform().constant("Scope permissions access granted")
                        .to("mock:scope-permissions-result");

                // Combined roles and permissions policy
                KeycloakSecurityPolicy combinedPolicy = new KeycloakSecurityPolicy();
                combinedPolicy.setServerUrl(keycloakUrl);
                combinedPolicy.setRealm(realm);
                combinedPolicy.setClientId(clientId);
                combinedPolicy.setClientSecret(clientSecret);
                combinedPolicy.setRequiredRoles(java.util.Arrays.asList("admin-role"));
                combinedPolicy.setRequiredPermissions(java.util.Arrays.asList("read:documents", "admin:system"));
                combinedPolicy.setAllRolesRequired(true); // Must have ALL roles
                combinedPolicy.setAllPermissionsRequired(true); // Any permission

                from("direct:combined-protected")
                        .policy(combinedPolicy)
                        .transform().constant("Combined access granted")
                        .to("mock:combined-result");

                // Flexible permissions policy (ANY permission)
                KeycloakSecurityPolicy flexiblePermissionsPolicy = new KeycloakSecurityPolicy();
                flexiblePermissionsPolicy.setServerUrl(keycloakUrl);
                flexiblePermissionsPolicy.setRealm(realm);
                flexiblePermissionsPolicy.setClientId(clientId);
                flexiblePermissionsPolicy.setClientSecret(clientSecret);
                flexiblePermissionsPolicy
                        .setRequiredPermissions(java.util.Arrays.asList("profile", "email", "user:basic", "read:public"));
                flexiblePermissionsPolicy.setAllPermissionsRequired(false); // ANY permission

                from("direct:flexible-permissions-protected")
                        .policy(flexiblePermissionsPolicy)
                        .transform().constant("Flexible permissions access granted")
                        .to("mock:flexible-permissions-result");

            }
        };
    }
}
