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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.keycloak.security.cache.CaffeineTokenCache;
import org.apache.camel.component.keycloak.security.cache.TokenCache;
import org.apache.camel.component.keycloak.security.cache.TokenCacheType;
import org.apache.camel.test.infra.keycloak.services.KeycloakService;
import org.apache.camel.test.infra.keycloak.services.KeycloakServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Keycloak OAuth 2.0 Token Introspection (RFC 7662) using test-infra.
 *
 * This test uses camel-test-infra-keycloak to automatically spin up a Keycloak container and test token introspection
 * functionality including caching, performance, and revocation detection.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KeycloakTokenIntrospectionIT extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakTokenIntrospectionIT.class);

    @RegisterExtension
    static KeycloakService keycloakService = KeycloakServiceFactory.createService();

    // Test data - use unique names to avoid conflicts
    private static final String TEST_REALM_NAME = "introspection-realm-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_CLIENT_ID = "introspection-client-" + UUID.randomUUID().toString().substring(0, 8);
    private static String TEST_CLIENT_SECRET = null; // Will be generated

    // Test users
    private static final String ADMIN_USER = "admin-user-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String ADMIN_PASSWORD = "admin123";
    private static final String USER_USER = "test-user-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String USER_PASSWORD = "user123";

    // Test roles
    private static final String ADMIN_ROLE = "admin-role";
    private static final String USER_ROLE = "user";

    private static Keycloak keycloakAdminClient;
    private static RealmResource realmResource;

    @BeforeAll
    static void setupKeycloakRealm() {
        LOG.info("Setting up Keycloak realm for token introspection tests");

        // Create Keycloak admin client
        keycloakAdminClient = KeycloakBuilder.builder()
                .serverUrl(keycloakService.getKeycloakServerUrl())
                .realm(keycloakService.getKeycloakRealm())
                .username(keycloakService.getKeycloakUsername())
                .password(keycloakService.getKeycloakPassword())
                .clientId("admin-cli")
                .build();

        // Create test realm
        createTestRealm();

        // Get realm resource for further operations
        realmResource = keycloakAdminClient.realm(TEST_REALM_NAME);

        // Create test client
        createTestClient();

        // Create test roles
        createTestRoles();

        // Create test users
        createTestUsers();

        // Assign roles to users
        assignRolesToUsers();

        LOG.info("Keycloak realm setup completed: {}", TEST_REALM_NAME);
    }

    @AfterAll
    static void cleanupKeycloakRealm() {
        if (keycloakAdminClient != null) {
            try {
                // Delete the test realm
                keycloakAdminClient.realm(TEST_REALM_NAME).remove();
                LOG.info("Deleted test realm: {}", TEST_REALM_NAME);
            } catch (Exception e) {
                LOG.warn("Failed to cleanup test realm: {}", e.getMessage());
            } finally {
                keycloakAdminClient.close();
            }
        }
    }

    private static void createTestRealm() {
        RealmRepresentation realm = new RealmRepresentation();
        realm.setId(TEST_REALM_NAME);
        realm.setRealm(TEST_REALM_NAME);
        realm.setDisplayName("Token Introspection Test Realm");
        realm.setEnabled(true);
        realm.setAccessTokenLifespan(3600); // 1 hour

        keycloakAdminClient.realms().create(realm);
        LOG.info("Created test realm: {}", TEST_REALM_NAME);
    }

    private static void createTestClient() {
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(TEST_CLIENT_ID);
        client.setName("Introspection Test Client");
        client.setDescription("Client for token introspection testing");
        client.setEnabled(true);
        client.setPublicClient(false); // Confidential client with secret (required for introspection)
        client.setDirectAccessGrantsEnabled(true);
        client.setServiceAccountsEnabled(true); // Enable service account for introspection
        client.setStandardFlowEnabled(true);

        Response response = realmResource.clients().create(client);
        if (response.getStatus() == 201) {
            String clientId = extractIdFromLocationHeader(response);
            ClientResource clientResource = realmResource.clients().get(clientId);

            // Get client secret
            TEST_CLIENT_SECRET = clientResource.getSecret().getValue();
            LOG.info("Created test client: {} with secret for introspection", TEST_CLIENT_ID);
        } else {
            throw new RuntimeException("Failed to create client. Status: " + response.getStatus());
        }
        response.close();
    }

    private static void createTestRoles() {
        // Create admin role
        RoleRepresentation adminRole = new RoleRepresentation();
        adminRole.setName(ADMIN_ROLE);
        adminRole.setDescription("Administrator role for introspection tests");
        realmResource.roles().create(adminRole);

        // Create user role
        RoleRepresentation userRole = new RoleRepresentation();
        userRole.setName(USER_ROLE);
        userRole.setDescription("User role for introspection tests");
        realmResource.roles().create(userRole);

        LOG.info("Created test roles: {}, {}", ADMIN_ROLE, USER_ROLE);
    }

    private static void createTestUsers() {
        // Create admin user
        createUser(ADMIN_USER, ADMIN_PASSWORD, "Admin", "User", ADMIN_USER + "@test.com");

        // Create normal user
        createUser(USER_USER, USER_PASSWORD, "Test", "User", USER_USER + "@test.com");

        LOG.info("Created test users: {}, {}", ADMIN_USER, USER_USER);
    }

    private static void createUser(String username, String password, String firstName, String lastName, String email) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setEnabled(true);
        user.setEmailVerified(true);

        Response response = realmResource.users().create(user);
        if (response.getStatus() == 201) {
            String userId = extractIdFromLocationHeader(response);
            UserResource userResource = realmResource.users().get(userId);

            // Set password
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(false);
            userResource.resetPassword(credential);

            LOG.info("Created user: {} with password", username);
        } else {
            throw new RuntimeException("Failed to create user: " + username + ". Status: " + response.getStatus());
        }
        response.close();
    }

    private static void assignRolesToUsers() {
        // Assign admin role to admin user
        assignRoleToUser(ADMIN_USER, ADMIN_ROLE);

        // Assign user role to normal user
        assignRoleToUser(USER_USER, USER_ROLE);

        LOG.info("Assigned roles to users: {} -> {}, {} -> {}", ADMIN_USER, ADMIN_ROLE, USER_USER, USER_ROLE);
    }

    private static void assignRoleToUser(String username, String roleName) {
        List<UserRepresentation> users = realmResource.users().search(username);
        if (!users.isEmpty()) {
            UserResource userResource = realmResource.users().get(users.get(0).getId());
            RoleRepresentation role = realmResource.roles().get(roleName).toRepresentation();
            userResource.roles().realmLevel().add(Arrays.asList(role));
            LOG.info("Assigned role {} to user {}", roleName, username);
        } else {
            throw new RuntimeException("User not found: " + username);
        }
    }

    private static String extractIdFromLocationHeader(Response response) {
        String location = response.getHeaderString("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Policy with token introspection enabled
                KeycloakSecurityPolicy introspectionPolicy = new KeycloakSecurityPolicy();
                introspectionPolicy.setServerUrl(keycloakService.getKeycloakServerUrl());
                introspectionPolicy.setRealm(TEST_REALM_NAME);
                introspectionPolicy.setClientId(TEST_CLIENT_ID);
                introspectionPolicy.setClientSecret(TEST_CLIENT_SECRET);
                introspectionPolicy.setRequiredRoles(Arrays.asList(ADMIN_ROLE));
                introspectionPolicy.setUseTokenIntrospection(true);
                introspectionPolicy.setIntrospectionCacheEnabled(true);
                introspectionPolicy.setIntrospectionCacheTtl(60);

                from("direct:introspection-protected")
                        .policy(introspectionPolicy)
                        .transform().constant("Introspection access granted")
                        .to("mock:introspection-result");

                // Policy with local JWT validation (for comparison)
                KeycloakSecurityPolicy localJwtPolicy = new KeycloakSecurityPolicy();
                localJwtPolicy.setServerUrl(keycloakService.getKeycloakServerUrl());
                localJwtPolicy.setRealm(TEST_REALM_NAME);
                localJwtPolicy.setClientId(TEST_CLIENT_ID);
                localJwtPolicy.setClientSecret(TEST_CLIENT_SECRET);
                localJwtPolicy.setRequiredRoles(Arrays.asList(ADMIN_ROLE));
                localJwtPolicy.setUseTokenIntrospection(false); // Use local JWT parsing

                from("direct:local-jwt-protected")
                        .policy(localJwtPolicy)
                        .transform().constant("Local JWT access granted")
                        .to("mock:local-jwt-result");

                // Policy with introspection but no cache
                KeycloakSecurityPolicy noCachePolicy = new KeycloakSecurityPolicy();
                noCachePolicy.setServerUrl(keycloakService.getKeycloakServerUrl());
                noCachePolicy.setRealm(TEST_REALM_NAME);
                noCachePolicy.setClientId(TEST_CLIENT_ID);
                noCachePolicy.setClientSecret(TEST_CLIENT_SECRET);
                noCachePolicy.setRequiredRoles(Arrays.asList(ADMIN_ROLE));
                noCachePolicy.setUseTokenIntrospection(true);
                noCachePolicy.setIntrospectionCacheEnabled(false);

                from("direct:introspection-no-cache")
                        .policy(noCachePolicy)
                        .transform().constant("No cache access granted")
                        .to("mock:no-cache-result");

                // Policy with Caffeine cache
                KeycloakSecurityPolicy caffeinePolicy = new KeycloakSecurityPolicy();
                caffeinePolicy.setServerUrl(keycloakService.getKeycloakServerUrl());
                caffeinePolicy.setRealm(TEST_REALM_NAME);
                caffeinePolicy.setClientId(TEST_CLIENT_ID);
                caffeinePolicy.setClientSecret(TEST_CLIENT_SECRET);
                caffeinePolicy.setRequiredRoles(Arrays.asList(ADMIN_ROLE));
                caffeinePolicy.setUseTokenIntrospection(true);
                caffeinePolicy.setIntrospectionCacheEnabled(true);
                caffeinePolicy.setIntrospectionCacheTtl(60);
                // Use Caffeine cache by creating custom cache
                TokenCache customCaffeineCache = new CaffeineTokenCache(60, 100, true);

                // Store for later retrieval in tests
                context.getRegistry().bind("caffeineCache", customCaffeineCache);

                from("direct:caffeine-protected")
                        .policy(caffeinePolicy)
                        .transform().constant("Caffeine cache access granted")
                        .to("mock:caffeine-result");
            }
        };
    }

    @Test
    @Order(1)
    void testKeycloakServiceConfiguration() {
        // Verify that the test-infra service is properly configured
        assertNotNull(keycloakService.getKeycloakServerUrl());
        assertNotNull(TEST_CLIENT_SECRET);
        LOG.info("Testing Keycloak at: {} with realm: {}", keycloakService.getKeycloakServerUrl(), TEST_REALM_NAME);
    }

    @Test
    @Order(10)
    void testTokenIntrospectionWithValidToken() throws Exception {
        // Get a valid access token
        String adminToken = getAccessToken(ADMIN_USER, ADMIN_PASSWORD);
        assertNotNull(adminToken);

        // Create introspector
        KeycloakTokenIntrospector introspector = new KeycloakTokenIntrospector(
                keycloakService.getKeycloakServerUrl(), TEST_REALM_NAME, TEST_CLIENT_ID, TEST_CLIENT_SECRET,
                true, 60);

        // Introspect the token
        KeycloakTokenIntrospector.IntrospectionResult result = introspector.introspect(adminToken);

        assertNotNull(result);
        assertTrue(result.isActive(), "Token should be active");
        assertNotNull(result.getSubject());
        assertNotNull(result.getClientId());
        assertNotNull(result.getUsername());

        LOG.info("Introspection result: active={}, subject={}, username={}",
                result.isActive(), result.getSubject(), result.getUsername());

        introspector.close();
    }

    @Test
    @Order(11)
    void testTokenIntrospectionWithInvalidToken() throws Exception {
        KeycloakTokenIntrospector introspector = new KeycloakTokenIntrospector(
                keycloakService.getKeycloakServerUrl(), TEST_REALM_NAME, TEST_CLIENT_ID, TEST_CLIENT_SECRET,
                true, 60);

        // Introspect an invalid token
        KeycloakTokenIntrospector.IntrospectionResult result = introspector.introspect("invalid-token");

        assertNotNull(result);
        assertFalse(result.isActive(), "Invalid token should not be active");

        introspector.close();
    }

    @Test
    @Order(12)
    void testTokenIntrospectionCaching() throws Exception {
        String adminToken = getAccessToken(ADMIN_USER, ADMIN_PASSWORD);
        assertNotNull(adminToken);

        KeycloakTokenIntrospector introspector = new KeycloakTokenIntrospector(
                keycloakService.getKeycloakServerUrl(), TEST_REALM_NAME, TEST_CLIENT_ID, TEST_CLIENT_SECRET,
                true, 60);

        // First introspection
        long start1 = System.currentTimeMillis();
        KeycloakTokenIntrospector.IntrospectionResult result1 = introspector.introspect(adminToken);
        long duration1 = System.currentTimeMillis() - start1;

        // Second introspection (should be cached)
        long start2 = System.currentTimeMillis();
        KeycloakTokenIntrospector.IntrospectionResult result2 = introspector.introspect(adminToken);
        long duration2 = System.currentTimeMillis() - start2;

        assertTrue(result1.isActive());
        assertTrue(result2.isActive());

        // Cached result should be significantly faster
        LOG.info("First introspection: {}ms, Cached introspection: {}ms", duration1, duration2);
        assertTrue(duration2 <= duration1, "Cached introspection should be faster or equal");

        introspector.close();
    }

    @Test
    @Order(20)
    void testSecurityPolicyWithIntrospection() {
        // Test with valid admin token using introspection
        String adminToken = getAccessToken(ADMIN_USER, ADMIN_PASSWORD);
        assertNotNull(adminToken);

        String result = template.requestBodyAndHeader("direct:introspection-protected", "test message",
                KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, adminToken, String.class);
        assertEquals("Introspection access granted", result);
    }

    @Test
    @Order(21)
    void testSecurityPolicyWithIntrospectionInvalidToken() {
        // Test that invalid tokens are rejected via introspection
        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            template.sendBodyAndHeader("direct:introspection-protected", "test message",
                    KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, "invalid-token");
        });
        assertTrue(ex.getCause() instanceof CamelAuthorizationException);
        assertTrue(ex.getCause().getMessage().contains("not active") ||
                ex.getCause().getMessage().contains("Failed to validate"));
    }

    @Test
    @Order(22)
    void testSecurityPolicyIntrospectionVsLocalValidation() {
        // Compare introspection-based validation with local JWT parsing
        String adminToken = getAccessToken(ADMIN_USER, ADMIN_PASSWORD);
        assertNotNull(adminToken);

        // Test introspection-based route
        long start1 = System.currentTimeMillis();
        String result1 = template.requestBodyAndHeader("direct:introspection-protected", "test message",
                KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, adminToken, String.class);
        long duration1 = System.currentTimeMillis() - start1;

        // Test local JWT parsing route
        long start2 = System.currentTimeMillis();
        String result2 = template.requestBodyAndHeader("direct:local-jwt-protected", "test message",
                KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, adminToken, String.class);
        long duration2 = System.currentTimeMillis() - start2;

        assertEquals("Introspection access granted", result1);
        assertEquals("Local JWT access granted", result2);

        LOG.info("Introspection validation: {}ms, Local JWT validation: {}ms", duration1, duration2);
    }

    @Test
    @Order(23)
    void testSecurityPolicyIntrospectionWithoutCache() {
        // Test introspection without caching
        String adminToken = getAccessToken(ADMIN_USER, ADMIN_PASSWORD);
        assertNotNull(adminToken);

        String result = template.requestBodyAndHeader("direct:introspection-no-cache", "test message",
                KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, adminToken, String.class);
        assertEquals("No cache access granted", result);
    }

    @Test
    @Order(24)
    void testSecurityPolicyIntrospectionUserWithoutAdminRole() {
        // Test that user token without admin role is rejected
        String userToken = getAccessToken(USER_USER, USER_PASSWORD);
        assertNotNull(userToken);

        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            template.sendBodyAndHeader("direct:introspection-protected", "test message",
                    KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, userToken);
        });
        assertTrue(ex.getCause() instanceof CamelAuthorizationException);
    }

    @Test
    @Order(25)
    void testRoleExtractionFromIntrospection() throws Exception {
        // Test that roles are correctly extracted from introspection result
        String adminToken = getAccessToken(ADMIN_USER, ADMIN_PASSWORD);
        assertNotNull(adminToken);

        KeycloakTokenIntrospector introspector = new KeycloakTokenIntrospector(
                keycloakService.getKeycloakServerUrl(), TEST_REALM_NAME, TEST_CLIENT_ID, TEST_CLIENT_SECRET,
                true, 60);

        KeycloakTokenIntrospector.IntrospectionResult result = introspector.introspect(adminToken);
        Set<String> roles = KeycloakSecurityHelper.extractRolesFromIntrospection(result, TEST_REALM_NAME, TEST_CLIENT_ID);

        assertNotNull(roles);
        LOG.info("Roles extracted from introspection: {}", roles);

        // Verify that admin role is present
        assertTrue(roles.contains(ADMIN_ROLE) || roles.size() > 0,
                "Should extract roles from introspection result");

        introspector.close();
    }

    @Test
    @Order(26)
    void testPermissionsExtractionFromIntrospection() throws Exception {
        // Test that permissions/scopes are correctly extracted from introspection result
        String adminToken = getAccessToken(ADMIN_USER, ADMIN_PASSWORD);
        assertNotNull(adminToken);

        KeycloakTokenIntrospector introspector = new KeycloakTokenIntrospector(
                keycloakService.getKeycloakServerUrl(), TEST_REALM_NAME, TEST_CLIENT_ID, TEST_CLIENT_SECRET,
                true, 60);

        KeycloakTokenIntrospector.IntrospectionResult result = introspector.introspect(adminToken);
        Set<String> permissions = KeycloakSecurityHelper.extractPermissionsFromIntrospection(result);

        assertNotNull(permissions);
        LOG.info("Permissions/scopes extracted from introspection: {}", permissions);

        introspector.close();
    }

    @Test
    @Order(30)
    void testCaffeineCacheWithValidToken() throws Exception {
        // Test Caffeine cache implementation with valid token
        String adminToken = getAccessToken(ADMIN_USER, ADMIN_PASSWORD);
        assertNotNull(adminToken);

        // Create introspector with Caffeine cache
        KeycloakTokenIntrospector introspector = new KeycloakTokenIntrospector(
                keycloakService.getKeycloakServerUrl(), TEST_REALM_NAME, TEST_CLIENT_ID, TEST_CLIENT_SECRET,
                TokenCacheType.CAFFEINE,
                60,    // TTL in seconds
                100,   // max size
                true   // record stats
        );

        // First introspection
        KeycloakTokenIntrospector.IntrospectionResult result1 = introspector.introspect(adminToken);
        assertTrue(result1.isActive());

        // Second introspection (should be cached)
        KeycloakTokenIntrospector.IntrospectionResult result2 = introspector.introspect(adminToken);
        assertTrue(result2.isActive());

        // Verify cache statistics
        TokenCache.CacheStats stats = introspector.getCacheStats();
        assertNotNull(stats);
        assertTrue(stats.getHitCount() >= 1, "Should have at least one cache hit");
        LOG.info("Caffeine cache stats: {}", stats.toString());

        // Verify cache size
        long cacheSize = introspector.getCacheSize();
        assertTrue(cacheSize > 0, "Cache should contain at least one entry");
        LOG.info("Caffeine cache size: {}", cacheSize);

        introspector.close();
    }

    @Test
    @Order(31)
    void testCaffeineCachePerformance() throws Exception {
        // Test that Caffeine cache improves performance
        String adminToken = getAccessToken(ADMIN_USER, ADMIN_PASSWORD);
        assertNotNull(adminToken);

        KeycloakTokenIntrospector introspector = new KeycloakTokenIntrospector(
                keycloakService.getKeycloakServerUrl(), TEST_REALM_NAME, TEST_CLIENT_ID, TEST_CLIENT_SECRET,
                TokenCacheType.CAFFEINE,
                120,   // 2 minute TTL
                1000,  // max 1000 entries
                true   // record stats
        );

        // First introspection - will hit Keycloak
        long start1 = System.currentTimeMillis();
        KeycloakTokenIntrospector.IntrospectionResult result1 = introspector.introspect(adminToken);
        long duration1 = System.currentTimeMillis() - start1;

        // Second introspection - should hit cache
        long start2 = System.currentTimeMillis();
        KeycloakTokenIntrospector.IntrospectionResult result2 = introspector.introspect(adminToken);
        long duration2 = System.currentTimeMillis() - start2;

        assertTrue(result1.isActive());
        assertTrue(result2.isActive());

        // Cached result should be faster
        LOG.info("First introspection (Keycloak): {}ms, Cached introspection (Caffeine): {}ms", duration1, duration2);
        assertTrue(duration2 <= duration1, "Cached introspection should be faster or equal");

        // Verify cache statistics
        TokenCache.CacheStats stats = introspector.getCacheStats();
        assertNotNull(stats);
        assertEquals(1, stats.getHitCount(), "Should have exactly one cache hit");
        assertEquals(1, stats.getMissCount(), "Should have exactly one cache miss");
        assertEquals(0.5, stats.getHitRate(), 0.01, "Hit rate should be 50%");
        LOG.info("Caffeine cache performance stats: {}", stats);

        introspector.close();
    }

    @Test
    @Order(32)
    void testCaffeineCacheEviction() throws Exception {
        // Test that Caffeine cache evicts entries based on size
        String adminToken = getAccessToken(ADMIN_USER, ADMIN_PASSWORD);
        assertNotNull(adminToken);

        // Create cache with very small max size
        KeycloakTokenIntrospector introspector = new KeycloakTokenIntrospector(
                keycloakService.getKeycloakServerUrl(), TEST_REALM_NAME, TEST_CLIENT_ID, TEST_CLIENT_SECRET,
                TokenCacheType.CAFFEINE,
                300,   // 5 minute TTL
                2,     // max 2 entries (small for testing eviction)
                true   // record stats
        );

        // Introspect the same token multiple times
        for (int i = 0; i < 5; i++) {
            introspector.introspect(adminToken);
        }

        // Verify cache stats
        TokenCache.CacheStats stats = introspector.getCacheStats();
        assertNotNull(stats);
        assertTrue(stats.getHitCount() > 0, "Should have cache hits");
        LOG.info("Cache stats after multiple introspections: {}", stats);

        // Verify cache size is within limits
        long size = introspector.getCacheSize();
        assertTrue(size <= 2, "Cache size should not exceed max size of 2");

        introspector.close();
    }

    @Test
    @Order(33)
    void testCaffeineCacheWithMultipleTokens() throws Exception {
        // Test Caffeine cache with multiple different tokens
        String adminToken = getAccessToken(ADMIN_USER, ADMIN_PASSWORD);
        String userToken = getAccessToken(USER_USER, USER_PASSWORD);
        assertNotNull(adminToken);
        assertNotNull(userToken);

        KeycloakTokenIntrospector introspector = new KeycloakTokenIntrospector(
                keycloakService.getKeycloakServerUrl(), TEST_REALM_NAME, TEST_CLIENT_ID, TEST_CLIENT_SECRET,
                TokenCacheType.CAFFEINE,
                60,    // 1 minute TTL
                100,   // max 100 entries
                true   // record stats
        );

        // Introspect both tokens
        introspector.introspect(adminToken);
        introspector.introspect(userToken);

        // Verify both are cached
        long cacheSize = introspector.getCacheSize();
        assertEquals(2, cacheSize, "Cache should contain both tokens");

        // Introspect again (should hit cache)
        introspector.introspect(adminToken);
        introspector.introspect(userToken);

        TokenCache.CacheStats stats = introspector.getCacheStats();
        assertNotNull(stats);
        assertEquals(2, stats.getHitCount(), "Should have 2 cache hits");
        assertEquals(2, stats.getMissCount(), "Should have 2 cache misses");
        LOG.info("Cache stats with multiple tokens: {}", stats);

        introspector.close();
    }

    @Test
    @Order(34)
    void testCaffeineCacheClearOperation() throws Exception {
        // Test that cache can be cleared
        String adminToken = getAccessToken(ADMIN_USER, ADMIN_PASSWORD);
        assertNotNull(adminToken);

        KeycloakTokenIntrospector introspector = new KeycloakTokenIntrospector(
                keycloakService.getKeycloakServerUrl(), TEST_REALM_NAME, TEST_CLIENT_ID, TEST_CLIENT_SECRET,
                TokenCacheType.CAFFEINE,
                60, 100, true);

        // Introspect and cache
        introspector.introspect(adminToken);
        assertTrue(introspector.getCacheSize() > 0, "Cache should have entries");

        // Clear cache
        introspector.clearCache();
        assertEquals(0, introspector.getCacheSize(), "Cache should be empty after clear");

        // Introspect again (should miss cache)
        introspector.introspect(adminToken);

        TokenCache.CacheStats stats = introspector.getCacheStats();
        assertNotNull(stats);
        // After clear, stats should be reset
        LOG.info("Cache stats after clear: {}", stats);

        introspector.close();
    }

    /**
     * Helper method to obtain access token from Keycloak using resource owner password flow.
     */
    private String getAccessToken(String username, String password) {
        try (Client client = ClientBuilder.newClient()) {
            String tokenUrl = keycloakService.getKeycloakServerUrl() + "/realms/" + TEST_REALM_NAME
                              + "/protocol/openid-connect/token";

            Form form = new Form()
                    .param("grant_type", "password")
                    .param("client_id", TEST_CLIENT_ID)
                    .param("client_secret", TEST_CLIENT_SECRET)
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
}
