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
package org.apache.camel.component.keycloak;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.ws.rs.core.Response;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.test.junit6.TestSupport;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Keycloak producer operations using external Keycloak instance.
 *
 * Must be manually tested. Provide your own Keycloak instance and configuration using: -Dmanual.keycloak.test=true
 * -Dkeycloak.server.url=http://localhost:8080 -Dkeycloak.realm=master -Dkeycloak.username=admin
 * -Dkeycloak.password=admin
 *
 * This test requires admin privileges to create/delete realms, users, and roles. IMPORTANT: Authentication is always
 * done against the master realm (where admin user exists) but operations are performed on target realms specified in
 * message headers.
 *
 * Example Docker command to start Keycloak: docker run -p 8080:8080 -e KEYCLOAK_ADMIN=admin -e
 * KEYCLOAK_ADMIN_PASSWORD=admin \ quay.io/keycloak/keycloak:latest start-dev
 */
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "manual.keycloak.test", matches = "true",
                                 disabledReason = "Manual test - set -Dmanual.keycloak.test=true to enable"),
        @EnabledIfSystemProperty(named = "keycloak.server.url", matches = ".*",
                                 disabledReason = "Keycloak server URL not provided"),
        @EnabledIfSystemProperty(named = "keycloak.realm", matches = ".*",
                                 disabledReason = "Keycloak realm not provided"),
        @EnabledIfSystemProperty(named = "keycloak.username", matches = ".*",
                                 disabledReason = "Keycloak username not provided"),
        @EnabledIfSystemProperty(named = "keycloak.password", matches = ".*",
                                 disabledReason = "Keycloak password not provided")
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KeycloakProducerIT extends CamelTestSupport {

    private static final Logger log = LoggerFactory.getLogger(KeycloakProducerIT.class);

    private static String keycloakUrl;
    private static String realm;
    private static String username;
    private static String password;

    // Test data - use unique names to avoid conflicts
    private static final String TEST_REALM_NAME = "test-realm-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_USER_NAME = "test-user-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_ROLE_NAME = "test-role-" + UUID.randomUUID().toString().substring(0, 8);

    static {
        // Load configuration from system properties
        keycloakUrl = System.getProperty("keycloak.server.url");
        realm = System.getProperty("keycloak.realm");
        username = System.getProperty("keycloak.username");
        password = System.getProperty("keycloak.password");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // For admin operations, always authenticate against master realm
                // but operations will be performed on the target realm specified in headers
                String keycloakEndpoint = String.format(
                        "keycloak:admin?serverUrl=%s&realm=master&username=%s&password=%s",
                        keycloakUrl, username, password);

                // Realm operations
                from("direct:createRealm")
                        .to(keycloakEndpoint + "&operation=createRealm");

                from("direct:getRealm")
                        .to(keycloakEndpoint + "&operation=getRealm");

                from("direct:deleteRealm")
                        .to(keycloakEndpoint + "&operation=deleteRealm");

                // User operations
                from("direct:createUser")
                        .to(keycloakEndpoint + "&operation=createUser");

                from("direct:getUser")
                        .to(keycloakEndpoint + "&operation=getUser");

                from("direct:listUsers")
                        .to(keycloakEndpoint + "&operation=listUsers");

                from("direct:deleteUser")
                        .to(keycloakEndpoint + "&operation=deleteUser");

                // Role operations
                from("direct:createRole")
                        .to(keycloakEndpoint + "&operation=createRole");

                from("direct:getRole")
                        .to(keycloakEndpoint + "&operation=getRole");

                from("direct:listRoles")
                        .to(keycloakEndpoint + "&operation=listRoles");

                from("direct:deleteRole")
                        .to(keycloakEndpoint + "&operation=deleteRole");

                // User-Role operations
                from("direct:assignRoleToUser")
                        .to(keycloakEndpoint + "&operation=assignRoleToUser");

                from("direct:removeRoleFromUser")
                        .to(keycloakEndpoint + "&operation=removeRoleFromUser");

                // POJO-based operations
                from("direct:createRealmPojo")
                        .to(keycloakEndpoint + "&operation=createRealm&pojoRequest=true");

                from("direct:createUserPojo")
                        .to(keycloakEndpoint + "&operation=createUser&pojoRequest=true");

                from("direct:createRolePojo")
                        .to(keycloakEndpoint + "&operation=createRole&pojoRequest=true");
            }
        };
    }

    @Test
    @Order(1)
    void testKeycloakProducerConfiguration() {
        // Verify that the configuration is properly set up
        assertNotNull(keycloakUrl);
        assertNotNull(realm);
        assertNotNull(username);
        assertNotNull(password);
        assertTrue(keycloakUrl.startsWith("http://") || keycloakUrl.startsWith("https://"));
        log.info("Testing Keycloak at: {} with realm: {}", keycloakUrl, realm);
    }

    @Test
    @Order(2)
    void testCreateRealmWithHeaders() {
        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);

        Exchange result = template.send("direct:createRealm", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        String body = result.getIn().getBody(String.class);
        assertEquals("Realm created successfully", body);

        log.info("Created realm: {}", TEST_REALM_NAME);
    }

    @Test
    @Order(4)
    void testGetRealm() {
        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);

        Exchange result = template.send("direct:getRealm", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        RealmRepresentation realmRep = result.getIn().getBody(RealmRepresentation.class);
        assertNotNull(realmRep);
        assertEquals(TEST_REALM_NAME, realmRep.getRealm());
        assertTrue(realmRep.isEnabled());

        log.info("Retrieved realm: {} - enabled: {}", realmRep.getRealm(), realmRep.isEnabled());
    }

    @Test
    @Order(5)
    void testCreateUserWithHeaders() {
        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.USERNAME, TEST_USER_NAME);
        exchange.getIn().setHeader(KeycloakConstants.USER_EMAIL, TEST_USER_NAME + "@test.com");
        exchange.getIn().setHeader(KeycloakConstants.USER_FIRST_NAME, "Test");
        exchange.getIn().setHeader(KeycloakConstants.USER_LAST_NAME, "User");

        Exchange result = template.send("direct:createUser", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        // The result should be a Response object
        Object body = result.getIn().getBody();
        assertNotNull(body);
        assertTrue(body instanceof Response);

        log.info("Created user: {} in realm: {}", TEST_USER_NAME, TEST_REALM_NAME);
    }

    @Test
    @Order(6)
    void testCreateUserWithPojo() {
        String pojoUserName = TEST_USER_NAME + "-pojo";

        UserRepresentation user = new UserRepresentation();
        user.setUsername(pojoUserName);
        user.setEmail(pojoUserName + "@test.com");
        user.setFirstName("Test");
        user.setLastName("User POJO");
        user.setEnabled(true);

        Exchange exchange = TestSupport.createExchangeWithBody(this.context, user);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);

        Exchange result = template.send("direct:createUserPojo", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        Object body = result.getIn().getBody();
        assertNotNull(body);
        assertTrue(body instanceof Response);

        log.info("Created user via POJO: {} in realm: {}", pojoUserName, TEST_REALM_NAME);
    }

    @Test
    @Order(7)
    void testListUsers() {
        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);

        Exchange result = template.send("direct:listUsers", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        @SuppressWarnings("unchecked")
        List<UserRepresentation> users = result.getIn().getBody(List.class);
        assertNotNull(users);
        assertTrue(users.size() >= 2); // At least our two test users

        log.info("Found {} users in realm: {}", users.size(), TEST_REALM_NAME);
    }

    @Test
    @Order(8)
    void testCreateRoleWithHeaders() {
        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.ROLE_NAME, TEST_ROLE_NAME);
        exchange.getIn().setHeader(KeycloakConstants.ROLE_DESCRIPTION, "Test role for integration testing");

        Exchange result = template.send("direct:createRole", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        String body = result.getIn().getBody(String.class);
        assertEquals("Role created successfully", body);

        log.info("Created role: {} in realm: {}", TEST_ROLE_NAME, TEST_REALM_NAME);
    }

    @Test
    @Order(9)
    void testCreateRoleWithPojo() {
        String pojoRoleName = TEST_ROLE_NAME + "-pojo";

        RoleRepresentation role = new RoleRepresentation();
        role.setName(pojoRoleName);
        role.setDescription("Test role created via POJO");

        Exchange exchange = TestSupport.createExchangeWithBody(this.context, role);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);

        Exchange result = template.send("direct:createRolePojo", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        String body = result.getIn().getBody(String.class);
        assertEquals("Role created successfully", body);

        log.info("Created role via POJO: {} in realm: {}", pojoRoleName, TEST_REALM_NAME);
    }

    @Test
    @Order(10)
    void testGetRole() {
        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.ROLE_NAME, TEST_ROLE_NAME);

        Exchange result = template.send("direct:getRole", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        RoleRepresentation roleRep = result.getIn().getBody(RoleRepresentation.class);
        assertNotNull(roleRep);
        assertEquals(TEST_ROLE_NAME, roleRep.getName());
        assertEquals("Test role for integration testing", roleRep.getDescription());

        log.info("Retrieved role: {} - description: {}", roleRep.getName(), roleRep.getDescription());
    }

    @Test
    @Order(11)
    void testListRoles() {
        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);

        Exchange result = template.send("direct:listRoles", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        @SuppressWarnings("unchecked")
        List<RoleRepresentation> roles = result.getIn().getBody(List.class);
        assertNotNull(roles);
        assertTrue(roles.size() >= 2); // At least our test roles + default roles

        log.info("Found {} roles in realm: {}", roles.size(), TEST_REALM_NAME);
    }

    @Test
    @Order(12)
    void testErrorHandling() {
        // Test with missing realm name
        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            template.sendBody("direct:createUser", null);
        });
        assertTrue(ex.getCause().getMessage().contains("Realm name must be specified"));

        // Test with non-existent realm
        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, "non-existent-realm");
        exchange.getIn().setHeader(KeycloakConstants.USERNAME, "testuser");

        log.info("Error handling tests completed successfully");
    }

    @Test
    @Order(98)
    void testCleanupRoles() {
        // Delete test roles
        String[] rolesToDelete = { TEST_ROLE_NAME, TEST_ROLE_NAME + "-pojo" };

        for (String roleName : rolesToDelete) {
            try {
                Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
                exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
                exchange.getIn().setHeader(KeycloakConstants.ROLE_NAME, roleName);

                Exchange result = template.send("direct:deleteRole", exchange);
                if (result.getException() == null) {
                    String body = result.getIn().getBody(String.class);
                    assertEquals("Role deleted successfully", body);
                    log.info("Deleted role: {}", roleName);
                }
            } catch (Exception e) {
                log.warn("Failed to delete role {}: {}", roleName, e.getMessage());
            }
        }
    }

    @Test
    @Order(99)
    void testCleanupRealm() {
        // Delete the test realm (this will also delete all users and roles in it)
        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);

        Exchange result = template.send("direct:deleteRealm", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        String body = result.getIn().getBody(String.class);
        assertEquals("Realm deleted successfully", body);

        log.info("Deleted test realm: {}", TEST_REALM_NAME);
    }

    @Test
    @Order(100)
    void testVerifyRealmDeleted() {
        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            // Verify that the realm was actually deleted
            Map<String, Object> headers = new HashMap<>();
            headers.put(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
            template.requestBodyAndHeaders("direct:getRealm", null, headers);
        });

        assertNotNull(ex.getCause());

        log.info("Verified that test realm was deleted");
    }
}
