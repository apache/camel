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

import java.util.List;
import java.util.UUID;

import jakarta.ws.rs.core.Response;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.keycloak.services.KeycloakService;
import org.apache.camel.test.infra.keycloak.services.KeycloakServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Keycloak producer operations using test-infra for container management.
 *
 * This test demonstrates how to use the camel-test-infra-keycloak module to automatically spin up a Keycloak container
 * for testing without manual setup.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KeycloakTestInfraIT extends CamelTestSupport {

    private static final Logger log = LoggerFactory.getLogger(KeycloakTestInfraIT.class);

    @RegisterExtension
    static KeycloakService keycloakService = KeycloakServiceFactory.createService();

    // Test data - use unique names to avoid conflicts
    private static final String TEST_REALM_NAME = "testinfra-realm-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_USER_NAME = "testinfra-user-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_ROLE_NAME = "testinfra-role-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_GROUP_NAME = "testinfra-group-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_CLIENT_ID = "testinfra-client-" + UUID.randomUUID().toString().substring(0, 8);

    private static String testUserId;
    private static String testGroupId;
    private static String testClientUuid;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        KeycloakComponent keycloak = context.getComponent("keycloak", KeycloakComponent.class);
        KeycloakConfiguration conf = new KeycloakConfiguration();
        conf.setServerUrl(keycloakService.getKeycloakServerUrl());
        conf.setRealm(keycloakService.getKeycloakRealm());
        conf.setUsername(keycloakService.getKeycloakUsername());
        conf.setPassword(keycloakService.getKeycloakPassword());
        keycloak.setConfiguration(conf);
        return context;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // The test-infra service automatically sets up the connection parameters
                String keycloakEndpoint = "keycloak:admin";

                // Realm operations
                from("direct:createRealm")
                        .to(keycloakEndpoint + "?operation=createRealm");

                from("direct:getRealm")
                        .to(keycloakEndpoint + "?operation=getRealm");

                from("direct:deleteRealm")
                        .to(keycloakEndpoint + "?operation=deleteRealm");

                // User operations
                from("direct:createUser")
                        .to(keycloakEndpoint + "?operation=createUser");

                from("direct:listUsers")
                        .to(keycloakEndpoint + "?operation=listUsers");

                from("direct:deleteUser")
                        .to(keycloakEndpoint + "?operation=deleteUser");

                // Role operations
                from("direct:createRole")
                        .to(keycloakEndpoint + "?operation=createRole");

                from("direct:getRole")
                        .to(keycloakEndpoint + "?operation=getRole");

                from("direct:deleteRole")
                        .to(keycloakEndpoint + "?operation=deleteRole");

                // Group operations
                from("direct:createGroup")
                        .to(keycloakEndpoint + "?operation=createGroup");

                from("direct:getGroup")
                        .to(keycloakEndpoint + "?operation=getGroup");

                from("direct:listGroups")
                        .to(keycloakEndpoint + "?operation=listGroups");

                from("direct:addUserToGroup")
                        .to(keycloakEndpoint + "?operation=addUserToGroup");

                from("direct:listUserGroups")
                        .to(keycloakEndpoint + "?operation=listUserGroups");

                from("direct:deleteGroup")
                        .to(keycloakEndpoint + "?operation=deleteGroup");

                // Client operations
                from("direct:createClient")
                        .to(keycloakEndpoint + "?operation=createClient");

                from("direct:listClients")
                        .to(keycloakEndpoint + "?operation=listClients");

                from("direct:deleteClient")
                        .to(keycloakEndpoint + "?operation=deleteClient");

                // Password operations
                from("direct:resetUserPassword")
                        .to(keycloakEndpoint + "?operation=resetUserPassword");

                // getUserRoles operation
                from("direct:getUserRoles")
                        .to(keycloakEndpoint + "?operation=getUserRoles");

                // Search users operation
                from("direct:searchUsers")
                        .to(keycloakEndpoint + "?operation=searchUsers");
            }
        };
    }

    @Test
    @Order(1)
    void testKeycloakServiceConfiguration() {
        // Verify that the test-infra service is properly configured
        assertNotNull(keycloakService.getKeycloakServerUrl());
        assertNotNull(keycloakService.getKeycloakRealm());
        assertNotNull(keycloakService.getKeycloakUsername());
        assertNotNull(keycloakService.getKeycloakPassword());
        assertTrue(keycloakService.getKeycloakServerUrl().startsWith("http://"));
        assertEquals("master", keycloakService.getKeycloakRealm());
        assertEquals("admin", keycloakService.getKeycloakUsername());
        assertEquals("admin", keycloakService.getKeycloakPassword());

        log.info("Testing Keycloak at: {} with realm: {}",
                keycloakService.getKeycloakServerUrl(),
                keycloakService.getKeycloakRealm());
    }

    @Test
    @Order(2)
    void testCreateRealm() {
        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);

        Exchange result = template.send("direct:createRealm", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        String body = result.getIn().getBody(String.class);
        assertEquals("Realm created successfully", body);

        log.info("Created realm: {}", TEST_REALM_NAME);
    }

    @Test
    @Order(3)
    void testGetRealm() {
        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);

        Exchange result = template.send("direct:getRealm", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        log.info("Retrieved realm: {}", TEST_REALM_NAME);
    }

    @Test
    @Order(4)
    void testCreateUser() {
        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.USERNAME, TEST_USER_NAME);
        exchange.getIn().setHeader(KeycloakConstants.USER_EMAIL, TEST_USER_NAME + "@testinfra.com");
        exchange.getIn().setHeader(KeycloakConstants.USER_FIRST_NAME, "TestInfra");
        exchange.getIn().setHeader(KeycloakConstants.USER_LAST_NAME, "User");

        Exchange result = template.send("direct:createUser", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        log.info("Created user: {} in realm: {}", TEST_USER_NAME, TEST_REALM_NAME);
    }

    @Test
    @Order(5)
    void testCreateRole() {
        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.ROLE_NAME, TEST_ROLE_NAME);
        exchange.getIn().setHeader(KeycloakConstants.ROLE_DESCRIPTION, "Test role for test-infra demonstration");

        Exchange result = template.send("direct:createRole", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        String body = result.getIn().getBody(String.class);
        assertEquals("Role created successfully", body);

        log.info("Created role: {} in realm: {}", TEST_ROLE_NAME, TEST_REALM_NAME);
    }

    @Test
    @Order(6)
    void testGetRole() {
        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.ROLE_NAME, TEST_ROLE_NAME);

        Exchange result = template.send("direct:getRole", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        log.info("Retrieved role: {} from realm: {}", TEST_ROLE_NAME, TEST_REALM_NAME);
    }

    @Test
    @Order(7)
    void testCreateGroup() {
        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.GROUP_NAME, TEST_GROUP_NAME);

        Exchange result = template.send("direct:createGroup", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        Response response = result.getIn().getBody(Response.class);
        assertNotNull(response);

        // Extract group ID from location header
        String location = response.getHeaderString("Location");
        if (location != null) {
            testGroupId = location.substring(location.lastIndexOf('/') + 1);
            log.info("Created group: {} with ID: {}", TEST_GROUP_NAME, testGroupId);
        }
    }

    @Test
    @Order(8)
    void testListGroups() {
        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);

        Exchange result = template.send("direct:listGroups", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        @SuppressWarnings("unchecked")
        List<GroupRepresentation> groups = result.getIn().getBody(List.class);
        assertNotNull(groups);
        assertTrue(groups.size() >= 1);

        log.info("Found {} groups in realm: {}", groups.size(), TEST_REALM_NAME);
    }

    @Test
    @Order(9)
    void testAddUserToGroup() {
        // First, get the user ID from the created user
        Exchange listExchange = createExchangeWithBody(null);
        listExchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        Exchange listResult = template.send("direct:listUsers", listExchange);

        @SuppressWarnings("unchecked")
        List<UserRepresentation> users = listResult.getIn().getBody(List.class);
        assertNotNull(users);
        assertTrue(users.size() >= 1);
        testUserId = users.get(0).getId();

        // Now add user to group
        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.USER_ID, testUserId);
        exchange.getIn().setHeader(KeycloakConstants.GROUP_ID, testGroupId);

        Exchange result = template.send("direct:addUserToGroup", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        String body = result.getIn().getBody(String.class);
        assertEquals("User added to group successfully", body);

        log.info("Added user {} to group {}", testUserId, testGroupId);
    }

    @Test
    @Order(10)
    void testListUserGroups() {
        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.USER_ID, testUserId);

        Exchange result = template.send("direct:listUserGroups", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        @SuppressWarnings("unchecked")
        List<GroupRepresentation> groups = result.getIn().getBody(List.class);
        assertNotNull(groups);
        assertTrue(groups.size() >= 1);

        log.info("User {} is member of {} groups", testUserId, groups.size());
    }

    @Test
    @Order(11)
    void testCreateClient() {
        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.CLIENT_ID, TEST_CLIENT_ID);

        Exchange result = template.send("direct:createClient", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        Response response = result.getIn().getBody(Response.class);
        assertNotNull(response);

        // Extract client UUID from location header
        String location = response.getHeaderString("Location");
        if (location != null) {
            testClientUuid = location.substring(location.lastIndexOf('/') + 1);
            log.info("Created client: {} with UUID: {}", TEST_CLIENT_ID, testClientUuid);
        }
    }

    @Test
    @Order(12)
    void testListClients() {
        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);

        Exchange result = template.send("direct:listClients", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        @SuppressWarnings("unchecked")
        List<?> clients = result.getIn().getBody(List.class);
        assertNotNull(clients);
        assertTrue(clients.size() >= 1);

        log.info("Found {} clients in realm: {}", clients.size(), TEST_REALM_NAME);
    }

    @Test
    @Order(13)
    void testResetUserPassword() {
        assertNotNull(testUserId, "testUserId should be set by testAddUserToGroup");

        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.USER_ID, testUserId);
        exchange.getIn().setHeader(KeycloakConstants.USER_PASSWORD, "newTestPassword123");
        exchange.getIn().setHeader(KeycloakConstants.PASSWORD_TEMPORARY, false);

        Exchange result = template.send("direct:resetUserPassword", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        String body = result.getIn().getBody(String.class);
        assertEquals("User password reset successfully", body);

        log.info("Reset password for user {}", testUserId);
    }

    @Test
    @Order(14)
    void testGetUserRoles() {
        assertNotNull(testUserId, "testUserId should be set by testAddUserToGroup");

        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.USER_ID, testUserId);

        Exchange result = template.send("direct:getUserRoles", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        @SuppressWarnings("unchecked")
        List<RoleRepresentation> roles = result.getIn().getBody(List.class);
        assertNotNull(roles);

        log.info("User {} has {} realm-level roles", testUserId, roles.size());
    }

    @Test
    @Order(15)
    void testSearchUsers() {
        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.SEARCH_QUERY, TEST_USER_NAME);

        Exchange result = template.send("direct:searchUsers", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        @SuppressWarnings("unchecked")
        List<UserRepresentation> users = result.getIn().getBody(List.class);
        assertNotNull(users);
        assertTrue(users.size() >= 1);

        log.info("Search for '{}' found {} users", TEST_USER_NAME, users.size());
    }

    @Test
    @Order(96)
    void testCleanupClient() {
        if (testClientUuid != null) {
            try {
                Exchange exchange = createExchangeWithBody(null);
                exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
                exchange.getIn().setHeader(KeycloakConstants.CLIENT_UUID, testClientUuid);

                Exchange result = template.send("direct:deleteClient", exchange);
                if (result.getException() == null) {
                    String body = result.getIn().getBody(String.class);
                    assertEquals("Client deleted successfully", body);
                    log.info("Deleted client: {}", TEST_CLIENT_ID);
                }
            } catch (Exception e) {
                log.warn("Failed to delete client {}: {}", TEST_CLIENT_ID, e.getMessage());
            }
        }
    }

    @Test
    @Order(97)
    void testCleanupGroup() {
        if (testGroupId != null) {
            try {
                Exchange exchange = createExchangeWithBody(null);
                exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
                exchange.getIn().setHeader(KeycloakConstants.GROUP_ID, testGroupId);

                Exchange result = template.send("direct:deleteGroup", exchange);
                if (result.getException() == null) {
                    String body = result.getIn().getBody(String.class);
                    assertEquals("Group deleted successfully", body);
                    log.info("Deleted group: {}", TEST_GROUP_NAME);
                }
            } catch (Exception e) {
                log.warn("Failed to delete group {}: {}", TEST_GROUP_NAME, e.getMessage());
            }
        }
    }

    @Test
    @Order(98)
    void testCleanupRole() {
        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.ROLE_NAME, TEST_ROLE_NAME);

        Exchange result = template.send("direct:deleteRole", exchange);
        if (result.getException() == null) {
            String body = result.getIn().getBody(String.class);
            assertEquals("Role deleted successfully", body);
            log.info("Deleted role: {}", TEST_ROLE_NAME);
        }
    }

    @Test
    @Order(99)
    void testCleanupRealm() {
        // Delete the test realm (this will also delete all users and roles in it)
        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);

        Exchange result = template.send("direct:deleteRealm", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        String body = result.getIn().getBody(String.class);
        assertEquals("Realm deleted successfully", body);

        log.info("Deleted test realm: {}", TEST_REALM_NAME);
    }
}
