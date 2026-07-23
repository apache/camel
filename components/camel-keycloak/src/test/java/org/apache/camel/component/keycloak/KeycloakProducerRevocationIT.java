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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

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
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KeycloakProducerRevocationIT extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakProducerRevocationIT.class);

    @RegisterExtension
    static KeycloakService keycloakService = KeycloakServiceFactory.createService();

    private static final String TEST_REALM_NAME = "revocation-realm-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_CLIENT_ID = "revocation-client-" + UUID.randomUUID().toString().substring(0, 8);
    private static String TEST_CLIENT_SECRET;

    private static final String TEST_USER = "test-user-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_PASSWORD = "password123";

    private static Keycloak keycloakAdminClient;
    private static RealmResource realmResource;

    @BeforeAll
    static void setupKeycloak() {
        keycloakAdminClient = KeycloakBuilder.builder()
                .serverUrl(keycloakService.getKeycloakServerUrl())
                .realm(keycloakService.getKeycloakRealm())
                .username(keycloakService.getKeycloakUsername())
                .password(keycloakService.getKeycloakPassword())
                .clientId("admin-cli")
                .build();

        // Create realm
        RealmRepresentation realm = new RealmRepresentation();
        realm.setId(TEST_REALM_NAME);
        realm.setRealm(TEST_REALM_NAME);
        realm.setDisplayName("Revocation Test Realm");
        realm.setEnabled(true);
        realm.setAccessTokenLifespan(3600);
        keycloakAdminClient.realms().create(realm);
        realmResource = keycloakAdminClient.realm(TEST_REALM_NAME);

        // Create client
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(TEST_CLIENT_ID);
        client.setPublicClient(false);
        client.setDirectAccessGrantsEnabled(true);
        client.setEnabled(true);
        client.setServiceAccountsEnabled(true);
        try (Response response = realmResource.clients().create(client)) {
            assertEquals(201, response.getStatus(), "Failed to create client");
            String location = response.getHeaderString("Location");
            String clientUuid = location.substring(location.lastIndexOf('/') + 1);
            ClientResource clientResource = realmResource.clients().get(clientUuid);
            TEST_CLIENT_SECRET = clientResource.getSecret().getValue();

            // Add audience mapper to include this client in the token audience
            // This is required for Keycloak token introspection
            ProtocolMapperRepresentation audienceMapper = new ProtocolMapperRepresentation();
            audienceMapper.setName("audience-mapper");
            audienceMapper.setProtocol("openid-connect");
            audienceMapper.setProtocolMapper("oidc-audience-mapper");
            Map<String, String> config = new HashMap<>();
            config.put("included.client.audience", TEST_CLIENT_ID);
            config.put("access.token.claim", "true");
            config.put("id.token.claim", "false");
            audienceMapper.setConfig(config);
            clientResource.getProtocolMappers().createMapper(audienceMapper);

            // Assign realm-management roles to the client service account
            UserRepresentation serviceAccount = clientResource.getServiceAccountUser();
            String realmManagementId = realmResource.clients().findByClientId("realm-management").get(0).getId();

            realmResource.users().get(serviceAccount.getId()).roles().clientLevel(realmManagementId)
                    .add(realmResource.clients().get(realmManagementId).roles().list().stream()
                            .filter(r -> r.getName().equals("manage-realm") || r.getName().equals("manage-users"))
                            .collect(java.util.stream.Collectors.toList()));
        }
        // Create user
        UserRepresentation user = new UserRepresentation();
        user.setUsername(TEST_USER);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail(TEST_USER + "@test.com");
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setRequiredActions(java.util.Collections.emptyList());
        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setValue(TEST_PASSWORD);
        cred.setTemporary(false);
        user.setCredentials(Arrays.asList(cred));
        try (Response response = realmResource.users().create(user)) {
            assertEquals(201, response.getStatus(), "Failed to create user");
        }
    }

    @AfterAll
    static void cleanup() {
        if (keycloakAdminClient != null) {
            try {
                keycloakAdminClient.realm(TEST_REALM_NAME).remove();
            } catch (Exception e) {
                // ignore
            } finally {
                keycloakAdminClient.close();
            }
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String endpoint = String.format("keycloak:admin?serverUrl=%s&realm=%s&clientId=%s&clientSecret=%s",
                        keycloakService.getKeycloakServerUrl(), TEST_REALM_NAME, TEST_CLIENT_ID, TEST_CLIENT_SECRET);

                from("direct:introspect")
                        .to(endpoint + "&operation=introspectToken");

                from("direct:revoke")
                        .to(endpoint + "&operation=revokeAccessToken");

                from("direct:logoutAll")
                        .to(endpoint + "&operation=logoutAllUsers");

                from("direct:pushNotBefore")
                        .to(endpoint + "&operation=pushNotBefore");
            }
        };
    }

    @Test
    @Order(1)
    void testIntrospectToken() {
        String token = getAccessToken();
        assertNotNull(token);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.TOKEN, token);

        Map<String, Object> claims = template.requestBodyAndHeaders("direct:introspect", null, headers, Map.class);
        assertNotNull(claims);
        assertEquals(Boolean.TRUE, claims.get("active"));
        assertEquals(TEST_USER, claims.get("preferred_username"));
    }

    @Test
    @Order(2)
    void testRevokeToken() {
        String token = getAccessToken();

        // 1. Verify token is active
        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.TOKEN, token);
        Map<String, Object> claims = template.requestBodyAndHeaders("direct:introspect", null, headers, Map.class);
        assertEquals(Boolean.TRUE, claims.get("active"));

        // 2. Revoke token
        template.sendBodyAndHeaders("direct:revoke", null, headers);

        // 3. Verify token is NO LONGER active
        claims = template.requestBodyAndHeaders("direct:introspect", null, headers, Map.class);
        assertEquals(Boolean.FALSE, claims.get("active"));
    }

    @Test
    @Order(3)
    void testLogoutAllUsers() {
        String token = getAccessToken();

        // 1. Verify token is active
        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.TOKEN, token);
        Map<String, Object> claims = template.requestBodyAndHeaders("direct:introspect", null, headers, Map.class);
        assertEquals(Boolean.TRUE, claims.get("active"));

        // 2. Logout all users
        template.sendBodyAndHeader("direct:logoutAll", null, KeycloakConstants.REALM_NAME, TEST_REALM_NAME);

        // 3. Verify token is NO LONGER active
        claims = template.requestBodyAndHeaders("direct:introspect", null, headers, Map.class);
        assertEquals(Boolean.FALSE, claims.get("active"));
    }

    @Test
    @Order(4)
    void testPushNotBefore() throws Exception {
        int initialNotBefore = realmResource.toRepresentation().getNotBefore();

        // 1. Push not-before
        template.sendBodyAndHeader("direct:pushNotBefore", null, KeycloakConstants.REALM_NAME, TEST_REALM_NAME);

        // 2. Verify notBefore has increased
        int updatedNotBefore = realmResource.toRepresentation().getNotBefore();
        assertTrue(updatedNotBefore >= initialNotBefore);
    }

    private String getAccessToken() {
        try (Client client = ClientBuilder.newClient()) {
            String tokenUrl = keycloakService.getKeycloakServerUrl() + "/realms/" + TEST_REALM_NAME
                              + "/protocol/openid-connect/token";

            Form form = new Form()
                    .param("grant_type", "password")
                    .param("client_id", TEST_CLIENT_ID)
                    .param("client_secret", TEST_CLIENT_SECRET)
                    .param("username", TEST_USER)
                    .param("password", TEST_PASSWORD);

            try (Response response = client.target(tokenUrl)
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED))) {

                if (response.getStatus() == 200) {
                    Map<String, Object> tokenResponse = response.readEntity(Map.class);
                    return (String) tokenResponse.get("access_token");
                } else {
                    LOG.error("Failed to obtain access token. Status: {}, Body: {}",
                            response.getStatus(), response.readEntity(String.class));
                    return null;
                }
            }
        }
    }
}
