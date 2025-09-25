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

import java.util.Map;

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
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Keycloak security policy using external Keycloak instance.
 *
 * Must be manually tested. Provide your own Keycloak instance and configuration using:
 * -Dkeycloak.server.url=http://localhost:8080 -Dkeycloak.realm=test-realm -Dkeycloak.client.id=test-client
 * -Dkeycloak.client.secret=test-secret
 *
 * Example test users that should exist in the realm: - myuser (password: pippo123, role: admin-role) - test-user
 * (password: user123, role: user) - reader-user (password: reader123, role: reader)
 */
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "keycloak.server.url", matches = ".*",
                                 disabledReason = "Keycloak server URL not provided"),
        @EnabledIfSystemProperty(named = "keycloak.realm", matches = ".*", disabledReason = "Keycloak realm not provided"),
        @EnabledIfSystemProperty(named = "keycloak.client.id", matches = ".*",
                                 disabledReason = "Keycloak client ID not provided"),
        @EnabledIfSystemProperty(named = "keycloak.client.secret", matches = ".*",
                                 disabledReason = "Keycloak client secret not provided")
})
public class KeycloakSecurityIT extends CamelTestSupport {

    private static String keycloakUrl;
    private static String realm;
    private static String clientId;
    private static String clientSecret;

    static {
        // Load configuration from system properties
        keycloakUrl = System.getProperty("keycloak.server.url");
        realm = System.getProperty("keycloak.realm");
        clientId = System.getProperty("keycloak.client.id");
        clientSecret = System.getProperty("keycloak.client.secret");
    }

    @Test
    void testKeycloakSecurityPolicyWithValidAdminToken() {
        // Test with valid admin token - assumes myuser exists with admin-role
        String adminToken = getAccessToken("myuser", "pippo123");
        assertNotNull(adminToken);

        String result = template.requestBodyAndHeader("direct:admin-only", "test message",
                KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, adminToken, String.class);
        assertEquals("Admin access granted", result);
    }

    @Test
    void testKeycloakSecurityPolicyWithValidUserToken() {
        // Test with valid user token - assumes test-user exists with user role
        String userToken = getAccessToken("test-user", "user123");
        assertNotNull(userToken);

        String result = template.requestBodyAndHeader("direct:user-access", "test message",
                KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, userToken, String.class);
        assertEquals("User access granted", result);
    }

    @Test
    void testKeycloakSecurityPolicyWithAuthorizationHeader() {
        // Test using Authorization header format
        String adminToken = getAccessToken("myuser", "pippo123");
        assertNotNull(adminToken);

        String result = template.requestBodyAndHeader("direct:protected", "test message",
                "Authorization", "Bearer " + adminToken, String.class);
        assertEquals("Access granted", result);
    }

    @Test
    void testKeycloakSecurityPolicyConfiguration() {
        // Verify that the configuration is properly set up
        assertNotNull(keycloakUrl);
        assertNotNull(realm);
        assertNotNull(clientId);
        assertTrue(keycloakUrl.startsWith("http://") || keycloakUrl.startsWith("https://"));
    }

    @Test
    void testKeycloakSecurityPolicyWithoutToken() {
        // Test that requests without tokens are rejected
        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            template.sendBody("direct:protected", "test message");
        });
        assertTrue(ex.getCause() instanceof CamelAuthorizationException);
    }

    @Test
    void testKeycloakSecurityPolicyWithInvalidToken() {
        // Test that requests with invalid tokens are rejected
        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            template.sendBodyAndHeader("direct:protected", "test message",
                    KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, "invalid-token");
        });
        assertTrue(ex.getCause() instanceof CamelAuthorizationException);
    }

    @Test
    void testKeycloakSecurityPolicyUserCannotAccessAdminRoute() {
        // Test that user token cannot access admin-only routes
        String userToken = getAccessToken("test-user", "user123");
        assertNotNull(userToken);

        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            template.requestBodyAndHeader("direct:admin-only", "test message",
                    KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, userToken, String.class);
        });
        assertTrue(ex.getCause() instanceof CamelAuthorizationException);
    }

    @Test
    void testKeycloakSecurityPolicyReaderUserCannotAccessUserRoute() {
        // Test that reader user cannot access user routes (assumes reader-user exists with reader role)
        String readerToken = getAccessToken("reader-user", "reader123");
        assertNotNull(readerToken);

        // This should fail because userPolicy requires "user" OR "default-roles-test-realm" roles
        // and reader-user only has "reader" role
        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            template.requestBodyAndHeader("direct:user-access", "test message",
                    KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, readerToken, String.class);
        });
        assertTrue(ex.getCause() instanceof CamelAuthorizationException);
    }

    /**
     * Helper method to obtain access token from Keycloak using resource owner password flow.
     */
    private String getAccessToken(String username, String password) {
        try (Client client = ClientBuilder.newClient()) {
            String tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";

            Form form = new Form()
                    .param("grant_type", "password")
                    .param("client_id", clientId)
                    .param("client_secret", clientSecret)
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
                    throw new RuntimeException("Failed to obtain access token. Status: " + response.getStatus());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error obtaining access token", e);
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Create Keycloak security policy using external Keycloak configuration
                KeycloakSecurityPolicy keycloakPolicy = new KeycloakSecurityPolicy();
                keycloakPolicy.setServerUrl(keycloakUrl);
                keycloakPolicy.setRealm(realm);
                keycloakPolicy.setClientId(clientId);
                keycloakPolicy.setClientSecret(clientSecret);

                // Configure different policies for different access levels
                KeycloakSecurityPolicy adminPolicy = new KeycloakSecurityPolicy();
                adminPolicy.setServerUrl(keycloakUrl);
                adminPolicy.setRealm(realm);
                adminPolicy.setClientId(clientId);
                adminPolicy.setClientSecret(clientSecret);
                adminPolicy.setRequiredRoles(java.util.Arrays.asList("admin-role"));

                KeycloakSecurityPolicy userPolicy = new KeycloakSecurityPolicy();
                userPolicy.setServerUrl(keycloakUrl);
                userPolicy.setRealm(realm);
                userPolicy.setClientId(clientId);
                userPolicy.setClientSecret(clientSecret);
                userPolicy.setRequiredRoles(java.util.Arrays.asList("user", "default-roles-test-realm"));
                userPolicy.setAllRolesRequired(false); // ANY role

                // Protected routes
                from("direct:protected")
                        .policy(keycloakPolicy)
                        .transform().constant("Access granted")
                        .to("mock:result");

                from("direct:admin-only")
                        .policy(adminPolicy)
                        .transform().constant("Admin access granted")
                        .to("mock:admin-result");

                from("direct:user-access")
                        .policy(userPolicy)
                        .transform().constant("User access granted")
                        .to("mock:user-result");
            }
        };
    }
}
