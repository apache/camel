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

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.GroupsResource;
import org.keycloak.admin.client.resource.OrganizationResource;
import org.keycloak.admin.client.resource.OrganizationsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A pojoRequest body of another type is converted or fails, and the Response of a create or delete is checked and
 * closed.
 */
public class KeycloakProducerPojoRequestAndResponseTest extends CamelTestSupport {

    @BindToRegistry("keycloakClient")
    private Keycloak keycloakClient = Mockito.mock(Keycloak.class);

    private RealmResource realmResource = Mockito.mock(RealmResource.class);
    private UsersResource usersResource = Mockito.mock(UsersResource.class);
    private UserResource userResource = Mockito.mock(UserResource.class);
    private GroupsResource groupsResource = Mockito.mock(GroupsResource.class);
    private OrganizationsResource organizationsResource = Mockito.mock(OrganizationsResource.class);
    private OrganizationResource organizationResource = Mockito.mock(OrganizationResource.class);

    @BeforeEach
    void setupMocks() {
        when(keycloakClient.realm(anyString())).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get(anyString())).thenReturn(userResource);
        when(realmResource.groups()).thenReturn(groupsResource);
        when(realmResource.organizations()).thenReturn(organizationsResource);
        when(organizationsResource.get(anyString())).thenReturn(organizationResource);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createUserPojo")
                        .to("keycloak:test?keycloakClient=#keycloakClient&operation=createUser&pojoRequest=true");
                from("direct:updateUserPojo")
                        .to("keycloak:test?keycloakClient=#keycloakClient&operation=updateUser&pojoRequest=true");
                from("direct:createGroup")
                        .to("keycloak:test?keycloakClient=#keycloakClient&operation=createGroup");
                from("direct:deleteUser")
                        .to("keycloak:test?keycloakClient=#keycloakClient&operation=deleteUser");
                from("direct:deleteOrganization")
                        .to("keycloak:test?keycloakClient=#keycloakClient&operation=deleteOrganization");
                from("direct:bulkCreateUsers")
                        .to("keycloak:test?keycloakClient=#keycloakClient&operation=bulkCreateUsers");
                from("direct:bulkUpdateUsers")
                        .to("keycloak:test?keycloakClient=#keycloakClient&operation=bulkUpdateUsers");
                from("direct:bulkDeleteUsers")
                        .to("keycloak:test?keycloakClient=#keycloakClient&operation=bulkDeleteUsers");
            }
        };
    }

    private static Response response(int status) {
        Response response = Mockito.mock(Response.class);
        when(response.getStatus()).thenReturn(status);
        return response;
    }

    @Test
    public void testCreateUserPojoFromJsonText() {
        Response created = response(201);
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(created);

        Exchange out = template.request("direct:createUserPojo", e -> {
            e.getIn().setHeader(KeycloakConstants.REALM_NAME, "testRealm");
            e.getIn().setBody("{\"username\":\"alice\",\"email\":\"alice@example.com\",\"enabled\":true}");
        });

        assertNull(out.getException());
        ArgumentCaptor<UserRepresentation> captor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(usersResource).create(captor.capture());
        assertEquals("alice", captor.getValue().getUsername());
        assertEquals("alice@example.com", captor.getValue().getEmail());
        assertSame(created, out.getMessage().getBody());
        verify(created).close();
    }

    @Test
    public void testUpdateUserPojoFromMap() {
        Exchange out = template.request("direct:updateUserPojo", e -> {
            e.getIn().setHeader(KeycloakConstants.REALM_NAME, "testRealm");
            e.getIn().setHeader(KeycloakConstants.USER_ID, "user-1");
            e.getIn().setBody(Map.of("firstName", "Alice", "lastName", "Smith"));
        });

        assertNull(out.getException());
        ArgumentCaptor<UserRepresentation> captor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(userResource).update(captor.capture());
        assertEquals("Alice", captor.getValue().getFirstName());
        assertEquals("Smith", captor.getValue().getLastName());
        assertEquals("User updated successfully", out.getMessage().getBody());
    }

    @Test
    public void testCreateUserPojoWrongBodyFails() {
        Exchange out = template.request("direct:createUserPojo", e -> {
            e.getIn().setHeader(KeycloakConstants.REALM_NAME, "testRealm");
            e.getIn().setBody(42);
        });

        InvalidPayloadException cause = assertInstanceOf(InvalidPayloadException.class, out.getException());
        assertEquals(UserRepresentation.class, cause.getType());
        verify(usersResource, never()).create(any(UserRepresentation.class));
    }

    @Test
    public void testCreateUserPojoInvalidJsonFails() {
        Exchange out = template.request("direct:createUserPojo", e -> {
            e.getIn().setHeader(KeycloakConstants.REALM_NAME, "testRealm");
            e.getIn().setBody("not json");
        });

        InvalidPayloadException cause = assertInstanceOf(InvalidPayloadException.class, out.getException());
        assertEquals(UserRepresentation.class, cause.getType());
        assertNotNull(cause.getCause());
        verify(usersResource, never()).create(any(UserRepresentation.class));
    }

    @Test
    public void testCreateUserConflictFails() {
        Response conflict = response(409);
        when(conflict.hasEntity()).thenReturn(true);
        when(conflict.readEntity(String.class)).thenReturn("{\"errorMessage\":\"User exists with same username\"}");
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(conflict);

        Exchange out = template.request("direct:createUserPojo", e -> {
            e.getIn().setHeader(KeycloakConstants.REALM_NAME, "testRealm");
            e.getIn().setBody(new UserRepresentation());
        });

        WebApplicationException cause = assertInstanceOf(WebApplicationException.class, out.getException());
        assertEquals(409, cause.getResponse().getStatus());
        assertTrue(cause.getMessage().contains("createUser failed with status 409"), cause.getMessage());
        assertTrue(cause.getMessage().contains("User exists with same username"), cause.getMessage());
        verify(conflict).close();
    }

    @Test
    public void testCreateGroupConflictFails() {
        Response conflict = response(409);
        when(groupsResource.add(any(GroupRepresentation.class))).thenReturn(conflict);

        Exchange out = template.request("direct:createGroup", e -> {
            e.getIn().setHeader(KeycloakConstants.REALM_NAME, "testRealm");
            e.getIn().setHeader(KeycloakConstants.GROUP_NAME, "admins");
        });

        WebApplicationException cause = assertInstanceOf(WebApplicationException.class, out.getException());
        assertEquals(409, cause.getResponse().getStatus());
        verify(conflict).close();
    }

    @Test
    public void testDeleteUserNotFoundFails() {
        Response notFound = response(404);
        when(usersResource.delete("missing")).thenReturn(notFound);

        Exchange out = template.request("direct:deleteUser", e -> {
            e.getIn().setHeader(KeycloakConstants.REALM_NAME, "testRealm");
            e.getIn().setHeader(KeycloakConstants.USER_ID, "missing");
        });

        WebApplicationException cause = assertInstanceOf(WebApplicationException.class, out.getException());
        assertEquals(404, cause.getResponse().getStatus());
        verify(notFound).close();
    }

    @Test
    public void testDeleteUserClosesResponse() {
        Response noContent = response(204);
        when(usersResource.delete("user-1")).thenReturn(noContent);

        Exchange out = template.request("direct:deleteUser", e -> {
            e.getIn().setHeader(KeycloakConstants.REALM_NAME, "testRealm");
            e.getIn().setHeader(KeycloakConstants.USER_ID, "user-1");
        });

        assertNull(out.getException());
        assertSame(noContent, out.getMessage().getBody());
        verify(noContent).close();
    }

    @Test
    public void testDeleteOrganizationNotFoundFails() {
        Response notFound = response(404);
        when(organizationResource.delete()).thenReturn(notFound);

        Exchange out = template.request("direct:deleteOrganization", e -> {
            e.getIn().setHeader(KeycloakConstants.REALM_NAME, "testRealm");
            e.getIn().setHeader(KeycloakConstants.ORGANIZATION_ID, "missing");
        });

        WebApplicationException cause = assertInstanceOf(WebApplicationException.class, out.getException());
        assertEquals(404, cause.getResponse().getStatus());
        verify(notFound).close();
    }

    @Test
    public void testBulkCreateUsersFromListOfMap() {
        Response created = response(201);
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(created);

        Exchange out = template.request("direct:bulkCreateUsers", e -> {
            e.getIn().setHeader(KeycloakConstants.REALM_NAME, "testRealm");
            e.getIn().setBody(List.of(Map.of("username", "alice"), "{\"username\":\"bob\"}"));
        });

        assertNull(out.getException());
        ArgumentCaptor<UserRepresentation> captor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(usersResource, times(2)).create(captor.capture());
        assertEquals("alice", captor.getAllValues().get(0).getUsername());
        assertEquals("bob", captor.getAllValues().get(1).getUsername());
        verify(created, times(2)).close();

        Map<?, ?> summary = out.getMessage().getBody(Map.class);
        assertEquals(2, summary.get("success"));
        assertEquals(0, summary.get("failed"));
    }

    @Test
    public void testBulkCreateUsersWrongElementFails() {
        Exchange out = template.request("direct:bulkCreateUsers", e -> {
            e.getIn().setHeader(KeycloakConstants.REALM_NAME, "testRealm");
            e.getIn().setBody(List.of(Map.of("username", "alice"), 42));
        });

        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, out.getException());
        assertTrue(cause.getMessage().contains("Element 1 of the list must be a UserRepresentation"), cause.getMessage());
        verify(usersResource, never()).create(any(UserRepresentation.class));
    }

    @Test
    public void testBulkCreateUsersConflictIsFailed() {
        Response created = response(201);
        Response conflict = response(409);
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(created, conflict);

        Exchange out = template.request("direct:bulkCreateUsers", e -> {
            e.getIn().setHeader(KeycloakConstants.REALM_NAME, "testRealm");
            e.getIn().setHeader(KeycloakConstants.CONTINUE_ON_ERROR, true);
            e.getIn().setBody(List.of(Map.of("username", "alice"), Map.of("username", "bob")));
        });

        assertNull(out.getException());
        Map<?, ?> summary = out.getMessage().getBody(Map.class);
        assertEquals(1, summary.get("success"));
        assertEquals(1, summary.get("failed"));
        List<?> results = (List<?>) summary.get("results");
        Map<?, ?> second = (Map<?, ?>) results.get(1);
        assertEquals("failed", second.get("status"));
        assertEquals(409, second.get("statusCode"));
        verify(created).close();
        verify(conflict).close();
    }

    @Test
    public void testBulkUpdateUsersFromListOfMap() {
        Exchange out = template.request("direct:bulkUpdateUsers", e -> {
            e.getIn().setHeader(KeycloakConstants.REALM_NAME, "testRealm");
            e.getIn().setBody(List.of(Map.of("id", "user-1", "firstName", "Alice")));
        });

        assertNull(out.getException());
        ArgumentCaptor<UserRepresentation> captor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(userResource).update(captor.capture());
        assertEquals("Alice", captor.getValue().getFirstName());
    }

    @Test
    public void testBulkDeleteUsersNotFoundIsFailed() {
        Response noContent = response(204);
        Response notFound = response(404);
        when(usersResource.delete("user-1")).thenReturn(noContent);
        when(usersResource.delete("missing")).thenReturn(notFound);

        Exchange out = template.request("direct:bulkDeleteUsers", e -> {
            e.getIn().setHeader(KeycloakConstants.REALM_NAME, "testRealm");
            e.getIn().setHeader(KeycloakConstants.CONTINUE_ON_ERROR, true);
            e.getIn().setBody(List.of("user-1", "missing"));
        });

        assertNull(out.getException());
        Map<?, ?> summary = out.getMessage().getBody(Map.class);
        assertEquals(1, summary.get("success"));
        assertEquals(1, summary.get("failed"));
        verify(noContent).close();
        verify(notFound).close();
    }
}
