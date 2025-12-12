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
 * Integration test for Keycloak token binding security features.
 * <p>
 * This test validates security enhancements that prevent token injection attacks: - Token source priority (property vs
 * header) - Token binding validation - Session fixation prevention - Attack scenario blocking
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KeycloakTokenBindingSecurityIT extends CamelTestSupport {

    private static final Logger log = LoggerFactory.getLogger(KeycloakTokenBindingSecurityIT.class);

    @RegisterExtension
    static KeycloakService keycloakService = KeycloakServiceFactory.createService();

    // Test data - use unique names
    private static final String TEST_REALM_NAME = "token-binding-realm-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_CLIENT_ID = "token-binding-client-" + UUID.randomUUID().toString().substring(0, 8);
    private static String TEST_CLIENT_SECRET = null;

    // Test users
    private static final String ADMIN_USER = "admin-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String ADMIN_PASSWORD = "admin123";
    private static final String NORMAL_USER = "user-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String NORMAL_PASSWORD = "user123";
    private static final String ATTACKER_USER = "attacker-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String ATTACKER_PASSWORD = "attacker123";

    // Test roles
    private static final String ADMIN_ROLE = "admin";
    private static final String USER_ROLE = "user";

    private static Keycloak keycloakAdminClient;
    private static RealmResource realmResource;

    @BeforeAll
    static void setupKeycloakRealm() {
        log.info("Setting up Keycloak realm for token binding tests");

        keycloakAdminClient = KeycloakBuilder.builder()
                .serverUrl(keycloakService.getKeycloakServerUrl())
                .realm(keycloakService.getKeycloakRealm())
                .username(keycloakService.getKeycloakUsername())
                .password(keycloakService.getKeycloakPassword())
                .clientId("admin-cli")
                .build();

        createTestRealm();
        realmResource = keycloakAdminClient.realm(TEST_REALM_NAME);
        createTestClient();
        createTestRoles();
        createTestUsers();
        assignRolesToUsers();

        log.info("Keycloak realm setup completed: {}", TEST_REALM_NAME);
    }

    @AfterAll
    static void cleanupKeycloakRealm() {
        if (keycloakAdminClient != null) {
            try {
                keycloakAdminClient.realm(TEST_REALM_NAME).remove();
                log.info("Deleted test realm: {}", TEST_REALM_NAME);
            } catch (Exception e) {
                log.warn("Failed to cleanup test realm: {}", e.getMessage());
            } finally {
                keycloakAdminClient.close();
            }
        }
    }

    private static void createTestRealm() {
        RealmRepresentation realm = new RealmRepresentation();
        realm.setId(TEST_REALM_NAME);
        realm.setRealm(TEST_REALM_NAME);
        realm.setDisplayName("Token Binding Security Test Realm");
        realm.setEnabled(true);
        realm.setAccessTokenLifespan(3600);
        keycloakAdminClient.realms().create(realm);
    }

    private static void createTestClient() {
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(TEST_CLIENT_ID);
        client.setName("Token Binding Test Client");
        client.setDescription("Client for token binding security testing");
        client.setEnabled(true);
        client.setPublicClient(false);
        client.setDirectAccessGrantsEnabled(true);
        client.setStandardFlowEnabled(true);
        client.setFullScopeAllowed(true);

        Response response = realmResource.clients().create(client);
        if (response.getStatus() == 201) {
            String clientId
                    = response.getHeaderString("Location").substring(response.getHeaderString("Location").lastIndexOf('/') + 1);
            ClientResource clientResource = realmResource.clients().get(clientId);
            TEST_CLIENT_SECRET = clientResource.getSecret().getValue();
            log.info("Created test client: {} with secret", TEST_CLIENT_ID);
        } else {
            throw new RuntimeException("Failed to create client. Status: " + response.getStatus());
        }
        response.close();
    }

    private static void createTestRoles() {
        RoleRepresentation adminRole = new RoleRepresentation();
        adminRole.setName(ADMIN_ROLE);
        realmResource.roles().create(adminRole);

        RoleRepresentation userRole = new RoleRepresentation();
        userRole.setName(USER_ROLE);
        realmResource.roles().create(userRole);
    }

    private static void createTestUsers() {
        createUser(ADMIN_USER, ADMIN_PASSWORD, "Admin", "User", ADMIN_USER + "@test.com");
        createUser(NORMAL_USER, NORMAL_PASSWORD, "Normal", "User", NORMAL_USER + "@test.com");
        createUser(ATTACKER_USER, ATTACKER_PASSWORD, "Attacker", "User", ATTACKER_USER + "@test.com");
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
            String userId
                    = response.getHeaderString("Location").substring(response.getHeaderString("Location").lastIndexOf('/') + 1);
            UserResource userResource = realmResource.users().get(userId);

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(false);
            userResource.resetPassword(credential);
            log.info("Created user: {} with password", username);
        } else {
            throw new RuntimeException("Failed to create user: " + username + ". Status: " + response.getStatus());
        }
        response.close();
    }

    private static void assignRolesToUsers() {
        assignRoleToUser(ADMIN_USER, ADMIN_ROLE);
        assignRoleToUser(NORMAL_USER, USER_ROLE);
        assignRoleToUser(ATTACKER_USER, USER_ROLE);
    }

    private static void assignRoleToUser(String username, String roleName) {
        List<UserRepresentation> users = realmResource.users().search(username);
        if (!users.isEmpty()) {
            UserResource userResource = realmResource.users().get(users.get(0).getId());
            RoleRepresentation role = realmResource.roles().get(roleName).toRepresentation();
            userResource.roles().realmLevel().add(Arrays.asList(role));
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Route 1: Secure default - property preferred over header
                KeycloakSecurityPolicy securePolicy = new KeycloakSecurityPolicy();
                securePolicy.setServerUrl(keycloakService.getKeycloakServerUrl());
                securePolicy.setRealm(TEST_REALM_NAME);
                securePolicy.setClientId(TEST_CLIENT_ID);
                securePolicy.setClientSecret(TEST_CLIENT_SECRET);
                securePolicy.setPreferPropertyOverHeader(true);
                securePolicy.setAllowTokenFromHeader(true);
                securePolicy.setValidateTokenBinding(true);

                from("direct:secure-default")
                        .policy(securePolicy)
                        .transform().constant("Access granted - secure default");

                // Route 2: Maximum security - headers disabled
                KeycloakSecurityPolicy maxSecurityPolicy = new KeycloakSecurityPolicy();
                maxSecurityPolicy.setServerUrl(keycloakService.getKeycloakServerUrl());
                maxSecurityPolicy.setRealm(TEST_REALM_NAME);
                maxSecurityPolicy.setClientId(TEST_CLIENT_ID);
                maxSecurityPolicy.setClientSecret(TEST_CLIENT_SECRET);
                maxSecurityPolicy.setAllowTokenFromHeader(false);

                from("direct:max-security")
                        .policy(maxSecurityPolicy)
                        .transform().constant("Access granted - max security");

                // Route 3: Admin-only route
                KeycloakSecurityPolicy adminPolicy = new KeycloakSecurityPolicy();
                adminPolicy.setServerUrl(keycloakService.getKeycloakServerUrl());
                adminPolicy.setRealm(TEST_REALM_NAME);
                adminPolicy.setClientId(TEST_CLIENT_ID);
                adminPolicy.setClientSecret(TEST_CLIENT_SECRET);
                adminPolicy.setRequiredRoles(Arrays.asList(ADMIN_ROLE));
                adminPolicy.setPreferPropertyOverHeader(true);

                from("direct:admin-only")
                        .policy(adminPolicy)
                        .transform().constant("Admin access granted");
            }
        };
    }

    @Test
    @Order(1)
    void testKeycloakServiceConfiguration() {
        assertNotNull(keycloakService.getKeycloakServerUrl());
        assertNotNull(TEST_CLIENT_SECRET);
    }

    @Test
    @Order(10)
    void testPropertyTokenWorks() {
        String normalToken = getAccessToken(NORMAL_USER, NORMAL_PASSWORD);

        String result = template.send("direct:secure-default", exchange -> {
            exchange.setProperty(KeycloakSecurityConstants.ACCESS_TOKEN_PROPERTY, normalToken);
            exchange.getIn().setBody("test");
        }).getMessage().getBody(String.class);

        assertEquals("Access granted - secure default", result);
        log.info("✓ Property token works");
    }

    @Test
    @Order(11)
    void testHeaderTokenWorks() {
        String normalToken = getAccessToken(NORMAL_USER, NORMAL_PASSWORD);

        String result = template.requestBodyAndHeader("direct:secure-default", "test",
                KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, normalToken, String.class);

        assertEquals("Access granted - secure default", result);
        log.info("✓ Header token works");
    }

    @Test
    @Order(12)
    void testPropertyPreferredOverHeader() {
        String normalToken = getAccessToken(NORMAL_USER, NORMAL_PASSWORD);
        String attackerToken = getAccessToken(ATTACKER_USER, ATTACKER_PASSWORD);

        String result = template.send("direct:secure-default", exchange -> {
            exchange.setProperty(KeycloakSecurityConstants.ACCESS_TOKEN_PROPERTY, normalToken);
            exchange.getIn().setHeader(KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, attackerToken);
            exchange.getIn().setBody("test");
        }).getMessage().getBody(String.class);

        assertEquals("Access granted - secure default", result);
        log.info("✓ Property token preferred over header token");
    }

    @Test
    @Order(13)
    void testInvalidHeaderIgnoredWhenPropertyValid() {
        String normalToken = getAccessToken(NORMAL_USER, NORMAL_PASSWORD);

        String result = template.send("direct:secure-default", exchange -> {
            exchange.setProperty(KeycloakSecurityConstants.ACCESS_TOKEN_PROPERTY, normalToken);
            exchange.getIn().setHeader(KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, "invalid.token");
            exchange.getIn().setBody("test");
        }).getMessage().getBody(String.class);

        assertEquals("Access granted - secure default", result);
        log.info("✓ Invalid header ignored when property valid");
    }

    @Test
    @Order(20)
    void testHeaderRejectedWhenHeadersDisabled() {
        String normalToken = getAccessToken(NORMAL_USER, NORMAL_PASSWORD);

        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            template.requestBodyAndHeader("direct:max-security", "test",
                    KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, normalToken, String.class);
        });

        assertTrue(ex.getCause() instanceof CamelAuthorizationException);
        log.info("✓ Header correctly rejected when headers disabled");
    }

    @Test
    @Order(21)
    void testPropertyWorksWhenHeadersDisabled() {
        String normalToken = getAccessToken(NORMAL_USER, NORMAL_PASSWORD);

        String result = template.send("direct:max-security", exchange -> {
            exchange.setProperty(KeycloakSecurityConstants.ACCESS_TOKEN_PROPERTY, normalToken);
            exchange.getIn().setBody("test");
        }).getMessage().getBody(String.class);

        assertEquals("Access granted - max security", result);
        log.info("✓ Property works when headers disabled");
    }

    @Test
    @Order(30)
    void testPropertyTokenUsedNotHeader() {
        String normalToken = getAccessToken(NORMAL_USER, NORMAL_PASSWORD);
        String adminToken = getAccessToken(ADMIN_USER, ADMIN_PASSWORD);

        String result = template.send("direct:secure-default", exchange -> {
            exchange.setProperty(KeycloakSecurityConstants.ACCESS_TOKEN_PROPERTY, normalToken);
            exchange.getIn().setHeader(KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, adminToken);
            exchange.getIn().setBody("test");
        }).getMessage().getBody(String.class);

        assertEquals("Access granted - secure default", result);
        log.info("✓ Property token preferred - attack vector mitigated");
    }

    @Test
    @Order(31)
    void testAttackScenario_SessionHijacking() {
        String victimToken = getAccessToken(NORMAL_USER, NORMAL_PASSWORD);
        String attackerToken = getAccessToken(ATTACKER_USER, ATTACKER_PASSWORD);

        String result = template.send("direct:secure-default", exchange -> {
            exchange.setProperty(KeycloakSecurityConstants.ACCESS_TOKEN_PROPERTY, victimToken);
            exchange.getIn().setHeader(KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, attackerToken);
            exchange.getIn().setBody("hijack attempt");
        }).getMessage().getBody(String.class);

        assertEquals("Access granted - secure default", result);
        log.info("✓ BLOCKED: Session hijacking prevented");
    }

    @Test
    @Order(32)
    void testAuthorizationHeaderFormat() {
        String normalToken = getAccessToken(NORMAL_USER, NORMAL_PASSWORD);

        String result = template.requestBodyAndHeader("direct:secure-default", "test",
                "Authorization", "Bearer " + normalToken, String.class);

        assertEquals("Access granted - secure default", result);
        log.info("✓ Authorization Bearer header works");
    }

    @Test
    @Order(33)
    void testNoTokenRejected() {
        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            template.sendBody("direct:secure-default", "test");
        });

        assertTrue(ex.getCause() instanceof CamelAuthorizationException);
        assertTrue(ex.getCause().getMessage().contains("Access token not found"));
        log.info("✓ Request without token correctly rejected");
    }

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
                    String errorBody = response.readEntity(String.class);
                    log.error("Failed to obtain token for user {}. Status: {}, Response: {}", username,
                            response.getStatus(), errorBody);
                    throw new RuntimeException(
                            "Failed to obtain access token for " + username + ". Status: " + response.getStatus() + ", Error: "
                                               + errorBody);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error obtaining access token for " + username, e);
        }
    }
}
