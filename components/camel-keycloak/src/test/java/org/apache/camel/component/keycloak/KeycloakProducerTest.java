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
import java.util.Map;

import jakarta.ws.rs.core.Response;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.GroupsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RealmsResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class KeycloakProducerTest extends CamelTestSupport {

    @BindToRegistry("keycloakClient")
    private Keycloak keycloakClient = Mockito.mock(Keycloak.class);

    private RealmsResource realmsResource = Mockito.mock(RealmsResource.class);
    private RealmResource realmResource = Mockito.mock(RealmResource.class);
    private UsersResource usersResource = Mockito.mock(UsersResource.class);
    private UserResource userResource = Mockito.mock(UserResource.class);
    private RolesResource rolesResource = Mockito.mock(RolesResource.class);
    private GroupsResource groupsResource = Mockito.mock(GroupsResource.class);
    private ClientsResource clientsResource = Mockito.mock(ClientsResource.class);
    private RoleMappingResource roleMappingResource = Mockito.mock(RoleMappingResource.class);
    private RoleScopeResource roleScopeResource = Mockito.mock(RoleScopeResource.class);
    private Response response = Mockito.mock(Response.class);

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:createRealm")
                        .to("keycloak:test?keycloakClient=#keycloakClient&operation=createRealm")
                        .to("mock:result");

                from("direct:createUser")
                        .to("keycloak:test?keycloakClient=#keycloakClient&operation=createUser")
                        .to("mock:result");

                from("direct:createRole")
                        .to("keycloak:test?keycloakClient=#keycloakClient&operation=createRole")
                        .to("mock:result");

                from("direct:listUsers")
                        .to("keycloak:test?keycloakClient=#keycloakClient&operation=listUsers")
                        .to("mock:result");

                from("direct:createGroup")
                        .to("keycloak:test?keycloakClient=#keycloakClient&operation=createGroup")
                        .to("mock:result");

                from("direct:listGroups")
                        .to("keycloak:test?keycloakClient=#keycloakClient&operation=listGroups")
                        .to("mock:result");

                from("direct:addUserToGroup")
                        .to("keycloak:test?keycloakClient=#keycloakClient&operation=addUserToGroup")
                        .to("mock:result");

                from("direct:createClient")
                        .to("keycloak:test?keycloakClient=#keycloakClient&operation=createClient")
                        .to("mock:result");

                from("direct:listClients")
                        .to("keycloak:test?keycloakClient=#keycloakClient&operation=listClients")
                        .to("mock:result");

                from("direct:resetUserPassword")
                        .to("keycloak:test?keycloakClient=#keycloakClient&operation=resetUserPassword")
                        .to("mock:result");

                from("direct:getUserRoles")
                        .to("keycloak:test?keycloakClient=#keycloakClient&operation=getUserRoles")
                        .to("mock:result");

                from("direct:searchUsers")
                        .to("keycloak:test?keycloakClient=#keycloakClient&operation=searchUsers")
                        .to("mock:result");

                from("direct:evaluatePermission")
                        .to("keycloak:test?keycloakClient=#keycloakClient&operation=evaluatePermission")
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testCreateRealm() throws Exception {
        when(keycloakClient.realms()).thenReturn(realmsResource);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:createRealm", null, KeycloakConstants.REALM_NAME, "testRealm");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testCreateUser() throws Exception {
        when(keycloakClient.realm(anyString())).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeaders("direct:createUser", null, Map.of(
                KeycloakConstants.REALM_NAME, "testRealm",
                KeycloakConstants.USERNAME, "testUser",
                KeycloakConstants.USER_EMAIL, "test@example.com"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testCreateRole() throws Exception {
        when(keycloakClient.realm(anyString())).thenReturn(realmResource);
        when(realmResource.roles()).thenReturn(rolesResource);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeaders("direct:createRole", null, Map.of(
                KeycloakConstants.REALM_NAME, "testRealm",
                KeycloakConstants.ROLE_NAME, "testRole",
                KeycloakConstants.ROLE_DESCRIPTION, "Test Role Description"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testListUsers() throws Exception {
        when(keycloakClient.realm(anyString())).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.list()).thenReturn(List.of(new UserRepresentation()));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:listUsers", null, KeycloakConstants.REALM_NAME, "testRealm");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMissingRealmName() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        try {
            template.sendBody("direct:createRealm", null);
        } catch (Exception e) {
            assertTrue(e.getCause().getMessage().contains("Realm name must be specified"));
        }
    }

    @Test
    public void testMissingUserName() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        try {
            template.sendBodyAndHeader("direct:createUser", null, KeycloakConstants.REALM_NAME, "testRealm");
        } catch (Exception e) {
            assertTrue(e.getCause().getMessage().contains("Username must be specified"));
        }
    }

    @Test
    public void testCreateGroup() throws Exception {
        when(keycloakClient.realm(anyString())).thenReturn(realmResource);
        when(realmResource.groups()).thenReturn(groupsResource);
        when(groupsResource.add(any(GroupRepresentation.class))).thenReturn(response);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeaders("direct:createGroup", null, Map.of(
                KeycloakConstants.REALM_NAME, "testRealm",
                KeycloakConstants.GROUP_NAME, "testGroup"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testListGroups() throws Exception {
        when(keycloakClient.realm(anyString())).thenReturn(realmResource);
        when(realmResource.groups()).thenReturn(groupsResource);
        when(groupsResource.groups()).thenReturn(List.of(new GroupRepresentation()));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:listGroups", null, KeycloakConstants.REALM_NAME, "testRealm");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testAddUserToGroup() throws Exception {
        when(keycloakClient.realm(anyString())).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get(anyString())).thenReturn(userResource);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeaders("direct:addUserToGroup", null, Map.of(
                KeycloakConstants.REALM_NAME, "testRealm",
                KeycloakConstants.USER_ID, "userId123",
                KeycloakConstants.GROUP_ID, "groupId123"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testCreateClient() throws Exception {
        when(keycloakClient.realm(anyString())).thenReturn(realmResource);
        when(realmResource.clients()).thenReturn(clientsResource);
        when(clientsResource.create(any(ClientRepresentation.class))).thenReturn(response);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeaders("direct:createClient", null, Map.of(
                KeycloakConstants.REALM_NAME, "testRealm",
                KeycloakConstants.CLIENT_ID, "testClient"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testListClients() throws Exception {
        when(keycloakClient.realm(anyString())).thenReturn(realmResource);
        when(realmResource.clients()).thenReturn(clientsResource);
        when(clientsResource.findAll()).thenReturn(List.of(new ClientRepresentation()));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:listClients", null, KeycloakConstants.REALM_NAME, "testRealm");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testResetUserPassword() throws Exception {
        when(keycloakClient.realm(anyString())).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get(anyString())).thenReturn(userResource);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeaders("direct:resetUserPassword", null, Map.of(
                KeycloakConstants.REALM_NAME, "testRealm",
                KeycloakConstants.USER_ID, "userId123",
                KeycloakConstants.USER_PASSWORD, "newPassword123",
                KeycloakConstants.PASSWORD_TEMPORARY, false));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testGetUserRoles() throws Exception {
        when(keycloakClient.realm(anyString())).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get(anyString())).thenReturn(userResource);
        when(userResource.roles()).thenReturn(roleMappingResource);
        when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);
        when(roleScopeResource.listAll()).thenReturn(List.of(new RoleRepresentation()));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeaders("direct:getUserRoles", null, Map.of(
                KeycloakConstants.REALM_NAME, "testRealm",
                KeycloakConstants.USER_ID, "userId123"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testSearchUsers() throws Exception {
        when(keycloakClient.realm(anyString())).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.search(anyString())).thenReturn(List.of(new UserRepresentation()));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeaders("direct:searchUsers", null, Map.of(
                KeycloakConstants.REALM_NAME, "testRealm",
                KeycloakConstants.SEARCH_QUERY, "testUser"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testEvaluatePermissionMissingServerUrl() throws Exception {
        // This test verifies that evaluatePermission requires serverUrl
        try {
            template.sendBodyAndHeaders("direct:evaluatePermission", null, Map.of(
                    KeycloakConstants.REALM_NAME, "testRealm",
                    KeycloakConstants.PERMISSION_RESOURCE_NAMES, "resource1"));
        } catch (Exception e) {
            assertTrue(e.getCause().getMessage().contains("Server URL must be specified"));
        }
    }
}
