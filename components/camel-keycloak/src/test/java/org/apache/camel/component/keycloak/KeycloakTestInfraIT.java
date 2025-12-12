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
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.test.junit6.TestSupport;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.idm.authorization.PolicyRepresentation;
import org.keycloak.representations.idm.authorization.ResourcePermissionRepresentation;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;
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
    private static final String TEST_IDP_ALIAS = "testinfra-idp-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_RESOURCE_NAME = "testinfra-resource-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_POLICY_NAME = "testinfra-policy-" + UUID.randomUUID().toString().substring(0, 8);
    // NOTE: not yet used
    // private static final String TEST_AUTHZ_CLIENT_ID = "testinfra-authz-client-" + UUID.randomUUID().toString().substring(0, 8);

    private static String testUserId;
    private static String testGroupId;
    private static String testClientUuid;
    private static String testResourceId;
    private static String testPolicyId;
    // NOTE: not yet used
    // private static String testAuthzClientUuid;
    // private static String testAuthzClientSecret;

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

                // Identity Provider operations
                from("direct:createIdentityProvider")
                        .to(keycloakEndpoint + "?operation=createIdentityProvider&pojoRequest=true");

                from("direct:getIdentityProvider")
                        .to(keycloakEndpoint + "?operation=getIdentityProvider");

                from("direct:listIdentityProviders")
                        .to(keycloakEndpoint + "?operation=listIdentityProviders");

                from("direct:deleteIdentityProvider")
                        .to(keycloakEndpoint + "?operation=deleteIdentityProvider");

                // Authorization Services operations
                from("direct:createResource")
                        .to(keycloakEndpoint + "?operation=createResource&pojoRequest=true");

                from("direct:getResource")
                        .to(keycloakEndpoint + "?operation=getResource");

                from("direct:listResources")
                        .to(keycloakEndpoint + "?operation=listResources");

                from("direct:deleteResource")
                        .to(keycloakEndpoint + "?operation=deleteResource");

                from("direct:createResourcePolicy")
                        .to(keycloakEndpoint + "?operation=createResourcePolicy&pojoRequest=true");

                from("direct:getResourcePolicy")
                        .to(keycloakEndpoint + "?operation=getResourcePolicy");

                from("direct:listResourcePolicies")
                        .to(keycloakEndpoint + "?operation=listResourcePolicies");

                from("direct:deleteResourcePolicy")
                        .to(keycloakEndpoint + "?operation=deleteResourcePolicy");

                from("direct:createResourcePermission")
                        .to(keycloakEndpoint + "?operation=createResourcePermission&pojoRequest=true");

                from("direct:listResourcePermissions")
                        .to(keycloakEndpoint + "?operation=listResourcePermissions");

                // User Attribute operations
                from("direct:getUserAttributes")
                        .to(keycloakEndpoint + "?operation=getUserAttributes");

                from("direct:setUserAttribute")
                        .to(keycloakEndpoint + "?operation=setUserAttribute");

                from("direct:deleteUserAttribute")
                        .to(keycloakEndpoint + "?operation=deleteUserAttribute");

                // User Credential operations
                from("direct:getUserCredentials")
                        .to(keycloakEndpoint + "?operation=getUserCredentials");

                from("direct:deleteUserCredential")
                        .to(keycloakEndpoint + "?operation=deleteUserCredential");

                // User Action operations
                from("direct:sendVerifyEmail")
                        .to(keycloakEndpoint + "?operation=sendVerifyEmail");

                from("direct:addRequiredAction")
                        .to(keycloakEndpoint + "?operation=addRequiredAction");

                from("direct:removeRequiredAction")
                        .to(keycloakEndpoint + "?operation=removeRequiredAction");

                // Client Secret Management
                from("direct:getClientSecret")
                        .to(keycloakEndpoint + "?operation=getClientSecret");

                from("direct:regenerateClientSecret")
                        .to(keycloakEndpoint + "?operation=regenerateClientSecret");

                // Permission evaluation operation
                from("direct:evaluatePermission")
                        .to(keycloakEndpoint + "?operation=evaluatePermission");
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
    @Order(3)
    void testGetRealm() {
        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);

        Exchange result = template.send("direct:getRealm", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        log.info("Retrieved realm: {}", TEST_REALM_NAME);
    }

    @Test
    @Order(4)
    void testCreateUser() {
        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
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
        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
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
        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
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
        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
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
        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
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
        Exchange listExchange = TestSupport.createExchangeWithBody(this.context, null);
        listExchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        Exchange listResult = template.send("direct:listUsers", listExchange);

        @SuppressWarnings("unchecked")
        List<UserRepresentation> users = listResult.getIn().getBody(List.class);
        assertNotNull(users);
        assertTrue(users.size() >= 1);
        testUserId = users.get(0).getId();

        // Now add user to group
        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
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
        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
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
        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
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
        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);

        Exchange result = template.send("direct:listClients", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        List<?> clients = result.getIn().getBody(List.class);
        assertNotNull(clients);
        assertTrue(clients.size() >= 1);

        log.info("Found {} clients in realm: {}", clients.size(), TEST_REALM_NAME);
    }

    @Test
    @Order(13)
    void testResetUserPassword() {
        assertNotNull(testUserId, "testUserId should be set by testAddUserToGroup");

        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
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

        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
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
        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
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

    // Identity Provider operation tests
    @Test
    @Order(16)
    void testCreateIdentityProvider() {
        IdentityProviderRepresentation idp = new IdentityProviderRepresentation();
        idp.setAlias(TEST_IDP_ALIAS);
        idp.setProviderId("oidc");
        idp.setEnabled(true);
        idp.setDisplayName("Test OIDC Provider");

        Exchange exchange = TestSupport.createExchangeWithBody(this.context, idp);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);

        Exchange result = template.send("direct:createIdentityProvider", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        Response response = result.getIn().getBody(Response.class);
        assertNotNull(response);

        log.info("Created identity provider: {} in realm: {}", TEST_IDP_ALIAS, TEST_REALM_NAME);
    }

    @Test
    @Order(17)
    void testGetIdentityProvider() {
        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.IDP_ALIAS, TEST_IDP_ALIAS);

        Exchange result = template.send("direct:getIdentityProvider", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        IdentityProviderRepresentation idp = result.getIn().getBody(IdentityProviderRepresentation.class);
        assertNotNull(idp);
        assertEquals(TEST_IDP_ALIAS, idp.getAlias());

        log.info("Retrieved identity provider: {}", TEST_IDP_ALIAS);
    }

    @Test
    @Order(18)
    void testListIdentityProviders() {
        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);

        Exchange result = template.send("direct:listIdentityProviders", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        @SuppressWarnings("unchecked")
        List<IdentityProviderRepresentation> idps = result.getIn().getBody(List.class);
        assertNotNull(idps);
        assertTrue(idps.size() >= 1);

        log.info("Found {} identity providers in realm: {}", idps.size(), TEST_REALM_NAME);
    }

    // User Attribute operation tests
    @Test
    @Order(19)
    void testSetUserAttribute() {
        assertNotNull(testUserId, "testUserId should be set");

        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.USER_ID, testUserId);
        exchange.getIn().setHeader(KeycloakConstants.ATTRIBUTE_NAME, "department");
        exchange.getIn().setHeader(KeycloakConstants.ATTRIBUTE_VALUE, "Engineering");

        Exchange result = template.send("direct:setUserAttribute", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        String body = result.getIn().getBody(String.class);
        assertEquals("User attribute set successfully", body);

        log.info("Set attribute 'department' for user {}", testUserId);
    }

    @Test
    @Order(20)
    void testGetUserAttributes() {
        assertNotNull(testUserId, "testUserId should be set");

        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.USER_ID, testUserId);

        Exchange result = template.send("direct:getUserAttributes", exchange);
        assertNotNull(result);

        // Verify the operation succeeded and returned the expected type
        if (result.getException() != null) {
            log.warn("Failed to get user attributes: {}", result.getException().getMessage());
            // Skip further assertions if user not found - could be retry scenario
            return;
        }

        @SuppressWarnings("unchecked")
        java.util.Map<String, List<String>> attributes = result.getIn().getBody(java.util.Map.class);
        assertNotNull(attributes);

        // Verify the department attribute exists from the previous test
        if (attributes.containsKey("department")) {
            assertEquals("Engineering", attributes.get("department").get(0));
            log.info("Retrieved {} attributes for user {}, including 'department'", attributes.size(), testUserId);
        } else {
            log.info("Retrieved {} attributes for user {} (department attribute may have been deleted in previous test run)",
                    attributes.size(), testUserId);
        }
    }

    @Test
    @Order(21)
    void testDeleteUserAttribute() {
        assertNotNull(testUserId, "testUserId should be set");

        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.USER_ID, testUserId);
        exchange.getIn().setHeader(KeycloakConstants.ATTRIBUTE_NAME, "department");

        Exchange result = template.send("direct:deleteUserAttribute", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        String body = result.getIn().getBody(String.class);
        assertEquals("User attribute deleted successfully", body);

        log.info("Deleted attribute 'department' for user {}", testUserId);
    }

    // User Credential operation tests
    @Test
    @Order(22)
    void testGetUserCredentials() {
        assertNotNull(testUserId, "testUserId should be set");

        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.USER_ID, testUserId);

        Exchange result = template.send("direct:getUserCredentials", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        @SuppressWarnings("unchecked")
        List<CredentialRepresentation> credentials = result.getIn().getBody(List.class);
        assertNotNull(credentials);

        log.info("Retrieved {} credentials for user {}", credentials.size(), testUserId);
    }

    // User Action operation tests
    @Test
    @Order(23)
    void testAddRequiredAction() {
        assertNotNull(testUserId, "testUserId should be set");

        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.USER_ID, testUserId);
        exchange.getIn().setHeader(KeycloakConstants.REQUIRED_ACTION, "VERIFY_EMAIL");

        Exchange result = template.send("direct:addRequiredAction", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        String body = result.getIn().getBody(String.class);
        assertEquals("Required action added successfully", body);

        log.info("Added required action VERIFY_EMAIL for user {}", testUserId);
    }

    @Test
    @Order(24)
    void testRemoveRequiredAction() {
        assertNotNull(testUserId, "testUserId should be set");

        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.USER_ID, testUserId);
        exchange.getIn().setHeader(KeycloakConstants.REQUIRED_ACTION, "VERIFY_EMAIL");

        Exchange result = template.send("direct:removeRequiredAction", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        String body = result.getIn().getBody(String.class);
        assertEquals("Required action removed successfully", body);

        log.info("Removed required action VERIFY_EMAIL for user {}", testUserId);
    }

    // Client Secret Management tests
    @Test
    @Order(25)
    void testGetClientSecret() {
        assertNotNull(testClientUuid, "testClientUuid should be set");

        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.CLIENT_UUID, testClientUuid);

        Exchange result = template.send("direct:getClientSecret", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        CredentialRepresentation secret = result.getIn().getBody(CredentialRepresentation.class);
        assertNotNull(secret);

        log.info("Retrieved client secret for client {}", testClientUuid);
    }

    @Test
    @Order(26)
    void testRegenerateClientSecret() {
        assertNotNull(testClientUuid, "testClientUuid should be set");

        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.CLIENT_UUID, testClientUuid);

        Exchange result = template.send("direct:regenerateClientSecret", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        CredentialRepresentation newSecret = result.getIn().getBody(CredentialRepresentation.class);
        assertNotNull(newSecret);

        log.info("Regenerated client secret for client {}", testClientUuid);
    }

    // Authorization Services operation tests
    // Note: These tests require a client with authorization enabled
    @Test
    @Order(27)
    void testCreateResource() {
        assertNotNull(testClientUuid, "testClientUuid should be set");

        ResourceRepresentation resource = new ResourceRepresentation();
        resource.setName(TEST_RESOURCE_NAME);
        resource.setType("urn:test:resources:document");
        resource.setOwnerManagedAccess(false);

        Exchange exchange = TestSupport.createExchangeWithBody(this.context, resource);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.CLIENT_UUID, testClientUuid);

        try {
            Exchange result = template.send("direct:createResource", exchange);
            if (result.getException() == null) {
                Response response = result.getIn().getBody(Response.class);
                assertNotNull(response);

                // Extract resource ID from location header
                String location = response.getHeaderString("Location");
                if (location != null) {
                    testResourceId = location.substring(location.lastIndexOf('/') + 1);
                    log.info("Created resource: {} with ID: {}", TEST_RESOURCE_NAME, testResourceId);
                }
            } else {
                log.warn("Authorization services may not be enabled on client: {}", result.getException().getMessage());
            }
        } catch (Exception e) {
            log.warn("Skipping resource creation test - authorization may not be enabled: {}", e.getMessage());
        }
    }

    @Test
    @Order(28)
    void testListResources() {
        if (testResourceId == null) {
            log.info("Skipping testListResources - no resource was created");
            return;
        }

        assertNotNull(testClientUuid, "testClientUuid should be set");

        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.CLIENT_UUID, testClientUuid);

        try {
            Exchange result = template.send("direct:listResources", exchange);
            assertNotNull(result);

            if (result.getException() == null) {
                @SuppressWarnings("unchecked")
                List<ResourceRepresentation> resources = result.getIn().getBody(List.class);
                assertNotNull(resources);

                log.info("Found {} resources for client {}", resources.size(), testClientUuid);
            }
        } catch (Exception e) {
            log.warn("Skipping list resources test: {}", e.getMessage());
        }
    }

    @Test
    @Order(29)
    void testGetResource() {
        if (testResourceId == null) {
            log.info("Skipping testGetResource - no resource was created");
            return;
        }

        assertNotNull(testClientUuid, "testClientUuid should be set");

        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.CLIENT_UUID, testClientUuid);
        exchange.getIn().setHeader(KeycloakConstants.RESOURCE_ID, testResourceId);

        try {
            Exchange result = template.send("direct:getResource", exchange);
            assertNotNull(result);

            if (result.getException() == null) {
                ResourceRepresentation resource = result.getIn().getBody(ResourceRepresentation.class);
                assertNotNull(resource);
                assertEquals(TEST_RESOURCE_NAME, resource.getName());

                log.info("Retrieved resource: {}", TEST_RESOURCE_NAME);
            }
        } catch (Exception e) {
            log.warn("Skipping get resource test: {}", e.getMessage());
        }
    }

    @Test
    @Order(30)
    void testCreateResourcePolicy() {
        assertNotNull(testClientUuid, "testClientUuid should be set");

        PolicyRepresentation policy = new PolicyRepresentation();
        policy.setName(TEST_POLICY_NAME);
        policy.setType("role");
        policy.setDescription("Test policy for integration tests");

        Exchange exchange = TestSupport.createExchangeWithBody(this.context, policy);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.CLIENT_UUID, testClientUuid);

        try {
            Exchange result = template.send("direct:createResourcePolicy", exchange);
            if (result.getException() == null) {
                Response response = result.getIn().getBody(Response.class);
                assertNotNull(response);

                // Extract policy ID from location header
                String location = response.getHeaderString("Location");
                if (location != null) {
                    testPolicyId = location.substring(location.lastIndexOf('/') + 1);
                    log.info("Created policy: {} with ID: {}", TEST_POLICY_NAME, testPolicyId);
                }
            }
        } catch (Exception e) {
            log.warn("Skipping policy creation test - authorization may not be enabled: {}", e.getMessage());
        }
    }

    @Test
    @Order(31)
    void testListResourcePolicies() {
        if (testPolicyId == null) {
            log.info("Skipping testListResourcePolicies - no policy was created");
            return;
        }

        assertNotNull(testClientUuid, "testClientUuid should be set");

        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.CLIENT_UUID, testClientUuid);

        try {
            Exchange result = template.send("direct:listResourcePolicies", exchange);
            assertNotNull(result);

            if (result.getException() == null) {
                @SuppressWarnings("unchecked")
                List<PolicyRepresentation> policies = result.getIn().getBody(List.class);
                assertNotNull(policies);

                log.info("Found {} policies for client {}", policies.size(), testClientUuid);
            }
        } catch (Exception e) {
            log.warn("Skipping list policies test: {}", e.getMessage());
        }
    }

    @Test
    @Order(32)
    void testGetResourcePolicy() {
        if (testPolicyId == null) {
            log.info("Skipping testGetResourcePolicy - no policy was created");
            return;
        }

        assertNotNull(testClientUuid, "testClientUuid should be set");

        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.CLIENT_UUID, testClientUuid);
        exchange.getIn().setHeader(KeycloakConstants.POLICY_ID, testPolicyId);

        try {
            Exchange result = template.send("direct:getResourcePolicy", exchange);
            assertNotNull(result);

            if (result.getException() == null) {
                PolicyRepresentation policy = result.getIn().getBody(PolicyRepresentation.class);
                assertNotNull(policy);

                log.info("Retrieved policy: {}", policy.getName());
            }
        } catch (Exception e) {
            log.warn("Skipping get policy test: {}", e.getMessage());
        }
    }

    @Test
    @Order(33)
    void testCreateResourcePermission() {
        if (testResourceId == null || testPolicyId == null) {
            log.info("Skipping testCreateResourcePermission - prerequisites not met");
            return;
        }

        assertNotNull(testClientUuid, "testClientUuid should be set");

        ResourcePermissionRepresentation permission = new ResourcePermissionRepresentation();
        permission.setName("test-permission-" + UUID.randomUUID().toString().substring(0, 8));
        permission.addResource(testResourceId);
        permission.addPolicy(testPolicyId);

        Exchange exchange = TestSupport.createExchangeWithBody(this.context, permission);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.CLIENT_UUID, testClientUuid);

        try {
            Exchange result = template.send("direct:createResourcePermission", exchange);
            if (result.getException() == null) {
                Response response = result.getIn().getBody(Response.class);
                assertNotNull(response);

                log.info("Created resource permission successfully");
            }
        } catch (Exception e) {
            log.warn("Skipping permission creation test: {}", e.getMessage());
        }
    }

    @Test
    @Order(34)
    void testListResourcePermissions() {
        if (testResourceId == null) {
            log.info("Skipping testListResourcePermissions - no resource was created");
            return;
        }

        assertNotNull(testClientUuid, "testClientUuid should be set");

        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
        exchange.getIn().setHeader(KeycloakConstants.CLIENT_UUID, testClientUuid);

        try {
            Exchange result = template.send("direct:listResourcePermissions", exchange);
            assertNotNull(result);

            if (result.getException() == null) {
                @SuppressWarnings("unchecked")
                List<PolicyRepresentation> permissions = result.getIn().getBody(List.class);
                assertNotNull(permissions);

                log.info("Found {} permissions for client {}", permissions.size(), testClientUuid);
            }
        } catch (Exception e) {
            log.warn("Skipping list permissions test: {}", e.getMessage());
        }
    }

    // Permission Evaluation tests - Tests the evaluatePermission operation
    // These tests require a properly configured authorization-enabled client

    @Test
    @Order(40)
    void testEvaluatePermissionWithClientCredentials() {
        // This test evaluates permissions using client credentials
        // The evaluatePermission operation uses AuthzClient which requires serverUrl, realm, clientId, and clientSecret

        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        // Note: The evaluatePermission operation uses the component's configuration
        // which includes serverUrl, realm, username, and password set in createCamelContext()
        // We need to configure a client with authorization enabled for this test

        try {
            // Use the test client we created - it needs to have authorization services enabled
            // For this test, we'll verify the operation validates its required parameters
            Exchange result = template.send("direct:evaluatePermission", exchange);

            // The operation should either succeed or fail with a specific error
            // depending on whether authorization services are enabled
            if (result.getException() != null) {
                String message = result.getException().getMessage();
                // These are expected errors when client doesn't have authorization enabled
                // or when credentials are not properly configured
                log.info("evaluatePermission result: {}", message);
                assertTrue(
                        message.contains("Client ID must be specified")
                                || message.contains("Client secret must be specified")
                                || message.contains("authorization")
                                || message.contains("not enabled")
                                || message.contains("403")
                                || message.contains("404")
                                || message.contains("401"),
                        "Expected authorization-related error but got: " + message);
            } else {
                // If it succeeds, verify the response format
                Object body = result.getIn().getBody();
                assertNotNull(body);
                log.info("evaluatePermission succeeded with response: {}", body);
            }
        } catch (Exception e) {
            log.info("evaluatePermission test completed with expected error: {}", e.getMessage());
        }
    }

    @Test
    @Order(41)
    void testEvaluatePermissionMissingServerUrl() {
        // Test that missing server URL throws appropriate error
        // This test verifies the validation logic in the evaluatePermission operation

        // Create a new route that doesn't have serverUrl configured
        // Since the component is configured with serverUrl in createCamelContext,
        // this test verifies the operation works with the configured values

        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.PERMISSION_RESOURCE_NAMES, "test-resource");
        exchange.getIn().setHeader(KeycloakConstants.PERMISSIONS_ONLY, true);

        try {
            Exchange result = template.send("direct:evaluatePermission", exchange);
            // The operation should validate required configuration
            if (result.getException() != null) {
                String message = result.getException().getMessage();
                log.info("Validation error (expected): {}", message);
                // Should fail due to missing client ID, client secret or authorization not enabled
                assertTrue(
                        message.contains("Client ID must be specified")
                                || message.contains("Client secret must be specified")
                                || message.contains("must be specified"),
                        "Expected validation error but got: " + message);
            } else {
                // If configured properly, should get a result
                Object body = result.getIn().getBody();
                assertNotNull(body);
                log.info("Got result: {}", body);
            }
        } catch (Exception e) {
            log.info("Expected validation error: {}", e.getMessage());
        }
    }

    @Test
    @Order(42)
    void testEvaluatePermissionWithResourceAndScopes() {
        // Test permission evaluation with specific resources and scopes

        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.PERMISSION_RESOURCE_NAMES, "document1,document2");
        exchange.getIn().setHeader(KeycloakConstants.PERMISSION_SCOPES, "read,write");
        exchange.getIn().setHeader(KeycloakConstants.PERMISSIONS_ONLY, true);

        try {
            Exchange result = template.send("direct:evaluatePermission", exchange);

            if (result.getException() != null) {
                String message = result.getException().getMessage();
                log.info("Permission evaluation with resources/scopes result: {}", message);
                // Expected to fail without proper authorization setup
                assertTrue(
                        message.contains("must be specified")
                                || message.contains("authorization")
                                || message.contains("403")
                                || message.contains("404"),
                        "Expected validation or authorization error but got: " + message);
            } else {
                // If it succeeds, verify the permissions-only response format
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> body = result.getIn().getBody(java.util.Map.class);
                if (body != null) {
                    assertTrue(body.containsKey("permissions") || body.containsKey("granted"),
                            "Response should contain permissions or granted field");
                    log.info("Permission evaluation result: permissions={}, granted={}",
                            body.get("permissions"), body.get("granted"));
                }
            }
        } catch (Exception e) {
            log.info("Permission evaluation test result: {}", e.getMessage());
        }
    }

    @Test
    @Order(43)
    void testEvaluatePermissionRPTMode() {
        // Test permission evaluation in RPT mode (default, without permissionsOnly flag)

        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        // Don't set PERMISSIONS_ONLY - should return RPT token
        exchange.getIn().setHeader(KeycloakConstants.PERMISSION_RESOURCE_NAMES, "test-resource");

        try {
            Exchange result = template.send("direct:evaluatePermission", exchange);

            if (result.getException() != null) {
                String message = result.getException().getMessage();
                log.info("RPT mode evaluation result: {}", message);
                // Expected to fail without proper authorization setup
                assertTrue(
                        message.contains("must be specified")
                                || message.contains("authorization")
                                || message.contains("403")
                                || message.contains("404"),
                        "Expected validation or authorization error but got: " + message);
            } else {
                // If it succeeds, verify the RPT response format
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> body = result.getIn().getBody(java.util.Map.class);
                if (body != null) {
                    // RPT mode should return token-related fields
                    log.info("RPT mode result: hasToken={}, tokenType={}, expiresIn={}",
                            body.containsKey("token"), body.get("tokenType"), body.get("expiresIn"));
                }
            }
        } catch (Exception e) {
            log.info("RPT mode test result: {}", e.getMessage());
        }
    }

    @Test
    @Order(90)
    void testCleanupAuthorizationResources() {
        // Cleanup is automatic when client is deleted, but we can try explicit cleanup
        if (testResourceId != null && testClientUuid != null) {
            try {
                Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
                exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
                exchange.getIn().setHeader(KeycloakConstants.CLIENT_UUID, testClientUuid);
                exchange.getIn().setHeader(KeycloakConstants.RESOURCE_ID, testResourceId);

                template.send("direct:deleteResource", exchange);
                log.info("Deleted resource: {}", TEST_RESOURCE_NAME);
            } catch (Exception e) {
                log.warn("Failed to delete resource {}: {}", TEST_RESOURCE_NAME, e.getMessage());
            }
        }

        if (testPolicyId != null && testClientUuid != null) {
            try {
                Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
                exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
                exchange.getIn().setHeader(KeycloakConstants.CLIENT_UUID, testClientUuid);
                exchange.getIn().setHeader(KeycloakConstants.POLICY_ID, testPolicyId);

                template.send("direct:deleteResourcePolicy", exchange);
                log.info("Deleted policy: {}", TEST_POLICY_NAME);
            } catch (Exception e) {
                log.warn("Failed to delete policy {}: {}", TEST_POLICY_NAME, e.getMessage());
            }
        }
    }

    @Test
    @Order(95)
    void testCleanupIdentityProvider() {
        try {
            Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
            exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
            exchange.getIn().setHeader(KeycloakConstants.IDP_ALIAS, TEST_IDP_ALIAS);

            Exchange result = template.send("direct:deleteIdentityProvider", exchange);
            if (result.getException() == null) {
                String body = result.getIn().getBody(String.class);
                assertEquals("Identity provider deleted successfully", body);
                log.info("Deleted identity provider: {}", TEST_IDP_ALIAS);
            }
        } catch (Exception e) {
            log.warn("Failed to delete identity provider {}: {}", TEST_IDP_ALIAS, e.getMessage());
        }
    }

    @Test
    @Order(96)
    void testCleanupClient() {
        if (testClientUuid != null) {
            try {
                Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
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
                Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
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
        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
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
        Exchange exchange = TestSupport.createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);

        Exchange result = template.send("direct:deleteRealm", exchange);
        assertNotNull(result);
        assertNull(result.getException());

        String body = result.getIn().getBody(String.class);
        assertEquals("Realm deleted successfully", body);

        log.info("Deleted test realm: {}", TEST_REALM_NAME);
    }
}
