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

import jakarta.ws.rs.core.Response;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.FederatedIdentityRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the federated identity (identity provider link) operations.
 */
class KeycloakProducerFederatedIdentityTest extends CamelTestSupport {

    @BindToRegistry("keycloakClient")
    private Keycloak keycloakClient = Mockito.mock(Keycloak.class);

    private RealmResource realmResource = Mockito.mock(RealmResource.class);
    private UsersResource usersResource = Mockito.mock(UsersResource.class);
    private UserResource userResource = Mockito.mock(UserResource.class);
    private Response response = Mockito.mock(Response.class);

    private void stubUserLookup() {
        when(keycloakClient.realm(anyString())).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get(anyString())).thenReturn(userResource);
    }

    private Map<String, Object> headers(String... keyValues) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, "testRealm");
        headers.put(KeycloakConstants.USER_ID, "user-1");
        for (int i = 0; i < keyValues.length; i += 2) {
            headers.put(keyValues[i], keyValues[i + 1]);
        }
        return headers;
    }

    @Test
    void testAddFederatedIdentity() throws Exception {
        stubUserLookup();
        when(response.getStatus()).thenReturn(204);
        when(userResource.addFederatedIdentity(anyString(), any(FederatedIdentityRepresentation.class)))
                .thenReturn(response);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeaders("direct:addFederatedIdentity", null,
                headers(KeycloakConstants.IDENTITY_PROVIDER, "google",
                        KeycloakConstants.FEDERATED_USER_ID, "google-123",
                        KeycloakConstants.FEDERATED_USERNAME, "jane@example.com"));

        MockEndpoint.assertIsSatisfied(context);

        ArgumentCaptor<FederatedIdentityRepresentation> captor
                = ArgumentCaptor.forClass(FederatedIdentityRepresentation.class);
        verify(userResource).addFederatedIdentity(eq("google"), captor.capture());

        FederatedIdentityRepresentation sent = captor.getValue();
        assertThat(sent.getIdentityProvider()).isEqualTo("google");
        assertThat(sent.getUserId()).isEqualTo("google-123");
        assertThat(sent.getUserName()).isEqualTo("jane@example.com");
    }

    @Test
    void testAddFederatedIdentityFallsBackToUserIdAsUsername() throws Exception {
        stubUserLookup();
        when(response.getStatus()).thenReturn(204);
        when(userResource.addFederatedIdentity(anyString(), any(FederatedIdentityRepresentation.class)))
                .thenReturn(response);

        template.sendBodyAndHeaders("direct:addFederatedIdentity", null,
                headers(KeycloakConstants.IDENTITY_PROVIDER, "github",
                        KeycloakConstants.FEDERATED_USER_ID, "gh-42"));

        ArgumentCaptor<FederatedIdentityRepresentation> captor
                = ArgumentCaptor.forClass(FederatedIdentityRepresentation.class);
        verify(userResource).addFederatedIdentity(eq("github"), captor.capture());

        // no username supplied, so the external user id is used rather than leaving the link with a blank username
        assertThat(captor.getValue().getUserName()).isEqualTo("gh-42");
    }

    @Test
    void testAddFederatedIdentityFailsOnErrorStatus() throws Exception {
        stubUserLookup();
        when(response.getStatus()).thenReturn(409);
        when(userResource.addFederatedIdentity(anyString(), any(FederatedIdentityRepresentation.class)))
                .thenReturn(response);

        assertThatThrownBy(() -> template.sendBodyAndHeaders("direct:addFederatedIdentity", null,
                headers(KeycloakConstants.IDENTITY_PROVIDER, "google",
                        KeycloakConstants.FEDERATED_USER_ID, "google-123")))
                .cause()
                .hasMessageContaining("409");
    }

    @Test
    void testRemoveFederatedIdentity() throws Exception {
        stubUserLookup();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeaders("direct:removeFederatedIdentity", null,
                headers(KeycloakConstants.IDENTITY_PROVIDER, "google"));

        MockEndpoint.assertIsSatisfied(context);
        verify(userResource).removeFederatedIdentity("google");
    }

    @Test
    void testGetFederatedIdentities() throws Exception {
        stubUserLookup();

        FederatedIdentityRepresentation link = new FederatedIdentityRepresentation();
        link.setIdentityProvider("google");
        link.setUserId("google-123");
        link.setUserName("jane@example.com");
        when(userResource.getFederatedIdentity()).thenReturn(List.of(link));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeaders("direct:getFederatedIdentities", null, headers());

        MockEndpoint.assertIsSatisfied(context);

        List<?> body = mock.getExchanges().get(0).getMessage().getBody(List.class);
        assertThat(body).hasSize(1);
        assertThat(((FederatedIdentityRepresentation) body.get(0)).getIdentityProvider()).isEqualTo("google");
    }

    @Test
    void testMissingIdentityProvider() throws Exception {
        assertThatThrownBy(() -> template.sendBodyAndHeaders("direct:addFederatedIdentity", null,
                headers(KeycloakConstants.FEDERATED_USER_ID, "google-123")))
                .cause()
                .hasMessageContaining("Identity provider alias must be specified");
    }

    @Test
    void testMissingFederatedUserId() throws Exception {
        assertThatThrownBy(() -> template.sendBodyAndHeaders("direct:addFederatedIdentity", null,
                headers(KeycloakConstants.IDENTITY_PROVIDER, "google")))
                .cause()
                .hasMessageContaining("Federated user ID must be specified");
    }

    @Test
    void testMissingUserId() throws Exception {
        assertThatThrownBy(() -> template.sendBodyAndHeader("direct:getFederatedIdentities", null,
                KeycloakConstants.REALM_NAME, "testRealm"))
                .cause()
                .hasMessageContaining("User ID must be specified");
    }

    @Test
    void testMissingRealmName() throws Exception {
        assertThatThrownBy(() -> template.sendBodyAndHeader("direct:getFederatedIdentities", null,
                KeycloakConstants.USER_ID, "user-1"))
                .cause()
                .hasMessageContaining("Realm name must be specified");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:addFederatedIdentity")
                        .to("keycloak:test?keycloakClient=#keycloakClient&operation=addFederatedIdentity")
                        .to("mock:result");

                from("direct:removeFederatedIdentity")
                        .to("keycloak:test?keycloakClient=#keycloakClient&operation=removeFederatedIdentity")
                        .to("mock:result");

                from("direct:getFederatedIdentities")
                        .to("keycloak:test?keycloakClient=#keycloakClient&operation=getFederatedIdentities")
                        .to("mock:result");
            }
        };
    }
}
