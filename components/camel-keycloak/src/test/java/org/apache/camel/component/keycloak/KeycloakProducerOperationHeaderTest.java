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

import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers when the CamelKeycloakOperation header may choose the operation of the producer: only when the endpoint sets
 * allowOperationHeader=true, as the operation decides what the endpoint does with its admin client.
 */
class KeycloakProducerOperationHeaderTest extends CamelTestSupport {

    private static final String REALM = "my-realm";
    private static final String USER_ID = "user-1";
    private static final String GET_USER = "keycloak:admin?keycloakClient=#keycloakClient&operation=getUser";

    @BindToRegistry("keycloakClient")
    private final Keycloak keycloakClient = mock(Keycloak.class);

    private final RealmResource realmResource = mock(RealmResource.class);
    private final UsersResource usersResource = mock(UsersResource.class);
    private final UserResource userResource = mock(UserResource.class);
    private final UserRepresentation user = new UserRepresentation();

    @BeforeEach
    void stubTheAdminClient() {
        when(keycloakClient.realm(REALM)).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get(USER_ID)).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(user);
    }

    @Test
    void theOperationHeaderIsIgnoredByDefault() {
        Exchange out = template.request(GET_USER, e -> {
            e.getIn().setHeader(KeycloakConstants.REALM_NAME, REALM);
            e.getIn().setHeader(KeycloakConstants.USER_ID, USER_ID);
            e.getIn().setHeader(KeycloakConstants.OPERATION, KeycloakOperations.deleteRealm);
        });

        // the endpoint stays a getUser endpoint: the user is read, and the realm is not deleted
        assertNull(out.getException());
        assertSame(user, out.getMessage().getBody());
        verify(realmResource, never()).remove();
    }

    @Test
    void theOperationHeaderAsTextIsIgnoredByDefault() {
        Exchange out = template.request(GET_USER, e -> {
            e.getIn().setHeader(KeycloakConstants.REALM_NAME, REALM);
            e.getIn().setHeader(KeycloakConstants.USER_ID, USER_ID);
            e.getIn().setHeader(KeycloakConstants.OPERATION, "deleteRealm");
        });

        assertNull(out.getException());
        assertSame(user, out.getMessage().getBody());
        verify(realmResource, never()).remove();
    }

    @Test
    void anEndpointWithoutAnOperationDoesNotTakeItFromTheHeaderByDefault() {
        Exchange out = template.request("keycloak:admin?keycloakClient=#keycloakClient", e -> {
            e.getIn().setHeader(KeycloakConstants.REALM_NAME, REALM);
            e.getIn().setHeader(KeycloakConstants.OPERATION, KeycloakOperations.deleteRealm);
        });

        IllegalArgumentException error = assertInstanceOf(IllegalArgumentException.class, out.getException());
        assertTrue(error.getMessage().contains("allowOperationHeader=true"), error.getMessage());
        verify(realmResource, never()).remove();
    }

    @Test
    void theOperationHeaderChoosesTheOperationWhenTheEndpointAllowsIt() {
        Exchange out = template.request(GET_USER + "&allowOperationHeader=true", e -> {
            e.getIn().setHeader(KeycloakConstants.REALM_NAME, REALM);
            e.getIn().setHeader(KeycloakConstants.OPERATION, "deleteRealm");
        });

        assertNull(out.getException());
        assertEquals("Realm deleted successfully", out.getMessage().getBody());
        verify(realmResource).remove();
    }

    @Test
    void withoutTheHeaderAnEndpointThatAllowsItRunsItsOperation() {
        Exchange out = template.request(GET_USER + "&allowOperationHeader=true", e -> {
            e.getIn().setHeader(KeycloakConstants.REALM_NAME, REALM);
            e.getIn().setHeader(KeycloakConstants.USER_ID, USER_ID);
        });

        assertNull(out.getException());
        assertSame(user, out.getMessage().getBody());
        verify(realmResource, never()).remove();
    }

    @Test
    void theOptionCanBeSetOnTheComponent() {
        // as camel.component.keycloak.allowOperationHeader=true does
        PropertyBindingSupport.setPropertiesOnTarget(context, context.getComponent("keycloak"),
                Map.of("allowOperationHeader", "true"));

        // a new endpoint takes the option from the configuration of the component
        Exchange out = template.request("keycloak:other?keycloakClient=#keycloakClient&operation=getUser", e -> {
            e.getIn().setHeader(KeycloakConstants.REALM_NAME, REALM);
            e.getIn().setHeader(KeycloakConstants.OPERATION, KeycloakOperations.deleteRealm);
        });

        assertNull(out.getException());
        assertEquals("Realm deleted successfully", out.getMessage().getBody());
        verify(realmResource).remove();
    }
}
