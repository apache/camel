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

import jakarta.ws.rs.core.Response;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RealmsResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UsersResource;
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
    private RolesResource rolesResource = Mockito.mock(RolesResource.class);
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

        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, "testRealm");
        exchange.getIn().setHeader(KeycloakConstants.USERNAME, "testUser");
        exchange.getIn().setHeader(KeycloakConstants.USER_EMAIL, "test@example.com");

        template.send("direct:createUser", exchange);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testCreateRole() throws Exception {
        when(keycloakClient.realm(anyString())).thenReturn(realmResource);
        when(realmResource.roles()).thenReturn(rolesResource);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, "testRealm");
        exchange.getIn().setHeader(KeycloakConstants.ROLE_NAME, "testRole");
        exchange.getIn().setHeader(KeycloakConstants.ROLE_DESCRIPTION, "Test Role Description");

        template.send("direct:createRole", exchange);

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
}
