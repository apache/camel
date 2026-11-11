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
package org.apache.camel.component.mcp.server.main;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.ws.rs.core.Response;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mcp.server.McpToolCallContext;
import org.apache.camel.main.Main;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.keycloak.services.KeycloakService;
import org.apache.camel.test.infra.keycloak.services.KeycloakServiceFactory;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real Keycloak integration tests for CAMEL-26356. The test uses Camel's existing Test Infra Keycloak service and
 * Camel's production OAuth token validator; no test validation factory is bound.
 * <p>
 * Run with {@code mvn -pl components/camel-ai/camel-mcp-server -Dit.test=McpServerMainOAuthKeycloakIT verify}.
 * Testcontainers disables this class automatically when Docker is unavailable.
 */
@DisabledOnOs(architectures = "s390x")
class McpServerMainOAuthKeycloakIT {

    @RegisterExtension
    static KeycloakService keycloakService = KeycloakServiceFactory.createSingletonService();

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final int PORT = AvailablePortFinder.getNextAvailable();
    private static final int MCP_TIMEOUT_SECONDS = 15;
    private static final String REALM = "mcp-oauth-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String CLIENT_ID = "mcp-client";
    private static final String CLIENT_WITHOUT_AUDIENCE_ID = "mcp-client-no-audience";
    private static final String CLIENT_SCP_ONLY_ID = "mcp-client-scp-only";
    private static final String USERNAME = "mcp-alice";
    private static final String PASSWORD = "mcp-test-password";
    private static final String REALM_ROLE = "mcp-operator";
    private static final String MCP_RESOURCE_URI = "https://mcp.example.test/mcp";
    private static final String METADATA_URI
            = "https://mcp.example.test/.well-known/oauth-protected-resource/mcp";
    private static final String INITIALIZE_REQUEST = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",
            "capabilities":{},"clientInfo":{"name":"keycloak-oauth-test","version":"1"}}}
            """;
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");

    private static Keycloak adminClient;
    private static RealmResource realmResource;
    private static String clientSecret;
    private static String clientWithoutAudienceSecret;
    private static String clientScpOnlySecret;
    private static String userId;
    private static String issuer;
    private static Main main;

    @BeforeAll
    static void startKeycloakAndMcpServer() throws Exception {
        awaitKeycloakReady();
        adminClient = keycloakService.getKeycloakAdminClient();
        createRealm();
        realmResource = adminClient.realm(REALM);
        createScopes();
        createClient(CLIENT_ID, true);
        createClient(CLIENT_WITHOUT_AUDIENCE_ID, false);
        createClient(CLIENT_SCP_ONLY_ID, true);
        createRoleAndUser();

        issuer = keycloakService.getKeycloakServerUrl() + "/realms/" + REALM;
        main = newMain(PORT);
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-tool:oauth_identity?tags=oauth&description=Return the authenticated OAuth identity")
                        .process(exchange -> {
                            Object claims = exchange.getProperty(McpToolCallContext.OAUTH_CLAIMS_PROPERTY);
                            exchange.getMessage().setBody(
                                    "subject=" + exchange.getProperty(McpToolCallContext.OAUTH_SUBJECT_PROPERTY)
                                                          + ";principalName="
                                                          + exchange.getProperty(
                                                                  McpToolCallContext.SECURITY_PRINCIPAL_NAME_PROPERTY)
                                                          + ";scopes="
                                                          + exchange.getProperty(McpToolCallContext.OAUTH_SCOPES_PROPERTY)
                                                          + ";claims=" + claims
                                                          + ";roles="
                                                          + exchange.getProperty(
                                                                  McpToolCallContext.SECURITY_PRINCIPAL_ROLES_PROPERTY)
                                                          + ";standardValidationResult="
                                                          + (exchange.getProperty(
                                                                  McpToolCallContext.OAUTH_TOKEN_VALIDATION_RESULT_PROPERTY)
                                                             != null));
                        });
            }
        });
        main.addInitialProperty("camel.server.mcp-oauth-profile", "mcp-test");
        main.addInitialProperty("camel.server.mcp-required-scopes", "mcp:read,mcp:write");
        main.addInitialProperty("camel.server.mcp-resource-uri", MCP_RESOURCE_URI);
        main.addInitialProperty("camel.oauth.mcp-test.base-uri", issuer);
        main.addInitialProperty("camel.oauth.mcp-test.expected-audience", CLIENT_ID);
        main.addInitialProperty("camel.oauth.mcp-test.allowed-jws-algorithms", "RS256");
        // Keycloak Test Infra exposes a local HTTP endpoint. This opt-in is test-only.
        main.addInitialProperty("camel.oauth.mcp-test.allow-insecure-http", "true");
        main.start();
    }

    @AfterAll
    static void stopMcpAndRemoveRealm() {
        if (main != null) {
            main.stop();
        }
        if (adminClient != null) {
            try {
                adminClient.realm(REALM).remove();
            } catch (Exception ignored) {
                // Realm cleanup is best effort; the container is stopped by Camel Test Infra.
            } finally {
                adminClient.close();
            }
        }
    }

    @Test
    void missingBearerTokenReturnsRfc9728Challenge() throws Exception {
        HttpResponse<String> response = post(null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(value -> assertThat(value)
                .contains("resource_metadata=\"" + METADATA_URI + "\"")
                .contains("scope=\"mcp:read mcp:write\""));
        assertThat(response.body()).isEqualTo("Unauthorized");
    }

    @Test
    void invalidTokenReturnsInvalidTokenWithoutLeakingTokenOrConfiguration() throws Exception {
        String invalidToken = "this-is-a-test-token-that-must-not-be-echoed";
        HttpResponse<String> response = post(invalidToken);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(value -> assertThat(value)
                .contains("error=\"invalid_token\"")
                .contains("resource_metadata=\"" + METADATA_URI + "\"")
                .contains("scope=\"mcp:read mcp:write\"")
                .doesNotContain(invalidToken, clientSecret, "error_description"));
        assertThat(response.body()).isEqualTo("Unauthorized")
                .doesNotContain(invalidToken, clientSecret, issuer, "error_description");
    }

    @Test
    void tokenWithTamperedSignatureIsRejected() throws Exception {
        String token = accessToken(CLIENT_ID, clientSecret, "mcp:read mcp:write");
        String tamperedToken = tamperJwtSignature(token);

        HttpResponse<String> response = post(tamperedToken);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate"))
                .hasValueSatisfying(value -> assertThat(value).contains("error=\"invalid_token\""));
    }

    @Test
    void expiredKeycloakJwtIsRejected() throws Exception {
        int originalAccessTokenLifespan = realmResource.toRepresentation().getAccessTokenLifespan();

        try {
            RealmRepresentation shortLivedRealm = realmResource.toRepresentation();
            shortLivedRealm.setAccessTokenLifespan(1);
            realmResource.update(shortLivedRealm);

            String token = accessToken(CLIENT_ID, clientSecret, "mcp:read mcp:write");

            Awaitility.await()
                    .atMost(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() -> {
                        HttpResponse<String> response = post(token);

                        assertThat(response.statusCode()).isEqualTo(401);
                        assertThat(response.headers().firstValue("WWW-Authenticate"))
                                .hasValueSatisfying(
                                        value -> assertThat(value).contains("error=\"invalid_token\""));
                    });
        } finally {
            RealmRepresentation restoredRealm = realmResource.toRepresentation();
            restoredRealm.setAccessTokenLifespan(originalAccessTokenLifespan);
            realmResource.update(restoredRealm);
        }
    }

    @Test
    void validKeycloakJwtPassesMcpAndPropagatesSubjectScopesClaimsAndRoles() throws Exception {
        String token = accessToken(CLIENT_ID, clientSecret, "mcp:read mcp:write");
        McpSyncClient client = newClient(token);
        try {
            client.initialize();
            assertThat(client.listTools().tools())
                    .extracting(McpSchema.Tool::name)
                    .contains("oauth_identity");

            McpSchema.CallToolResult result
                    = client.callTool(new McpSchema.CallToolRequest("oauth_identity", Map.of()));
            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            String content = result.content().toString();
            assertThat(content)
                    .contains("subject=" + userId)
                    .contains("principalName=" + userId)
                    .contains("mcp:read")
                    .contains("mcp:write")
                    .contains("preferred_username=" + USERNAME)
                    .contains("realm_access")
                    .contains(REALM_ROLE)
                    .contains("standardValidationResult=true");
        } finally {
            client.closeGracefully();
        }
    }

    @Test
    void keycloakScpClaimSatisfiesRequiredScopes() throws Exception {
        // Only "openid" is requested. The required MCP scopes are supplied by the scp claim.
        String token = accessToken(CLIENT_SCP_ONLY_ID, clientScpOnlySecret, "openid");
        McpSyncClient client = newClient(token);
        try {
            client.initialize();
            McpSchema.CallToolResult result
                    = client.callTool(new McpSchema.CallToolRequest("oauth_identity", Map.of()));

            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(result.content().toString())
                    .contains("mcp:read")
                    .contains("mcp:write");
        } finally {
            client.closeGracefully();
        }
    }

    @Test
    void missingRequiredScopeIsRejectedByTheMcpHttpBoundary() throws Exception {
        String token = accessToken(CLIENT_ID, clientSecret, "mcp:read");
        HttpResponse<String> response = post(token);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(value -> assertThat(value)
                .contains("error=\"insufficient_scope\"")
                .contains("resource_metadata=\"" + METADATA_URI + "\"")
                .contains("scope=\"mcp:read mcp:write\""));
    }

    @Test
    void tokenWithoutConfiguredAudienceIsRejected() throws Exception {
        String token = accessToken(CLIENT_WITHOUT_AUDIENCE_ID, clientWithoutAudienceSecret, "mcp:read mcp:write");
        HttpResponse<String> response = post(token);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(value -> assertThat(value)
                .contains("error=\"invalid_token\""));
    }

    @Test
    void tokenWithUnexpectedIssuerIsRejected() throws Exception {
        String token = accessToken(CLIENT_ID, clientSecret, "mcp:read mcp:write");
        int wrongIssuerPort = AvailablePortFinder.getNextAvailable();
        Main wrongIssuer = newMain(wrongIssuerPort);
        wrongIssuer.addInitialProperty("camel.server.mcp-oauth-profile", "wrong-issuer");
        wrongIssuer.addInitialProperty("camel.server.mcp-required-scopes", "mcp:read,mcp:write");
        wrongIssuer.addInitialProperty("camel.oauth.wrong-issuer.jwks-endpoint",
                issuer + "/protocol/openid-connect/certs");
        wrongIssuer.addInitialProperty("camel.oauth.wrong-issuer.expected-issuer", issuer + "/not-the-issuer");
        wrongIssuer.addInitialProperty("camel.oauth.wrong-issuer.expected-audience", CLIENT_ID);
        wrongIssuer.addInitialProperty("camel.oauth.wrong-issuer.allow-insecure-http", "true");

        try {
            wrongIssuer.start();
            HttpResponse<String> response = post(wrongIssuerPort, token);
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.headers().firstValue("WWW-Authenticate"))
                    .hasValueSatisfying(value -> assertThat(value).contains("error=\"invalid_token\""));
        } finally {
            wrongIssuer.stop();
        }
    }

    @Test
    void tokenWithDisallowedAlgorithmIsRejected() throws Exception {
        String token = accessToken(CLIENT_ID, clientSecret, "mcp:read mcp:write");
        int wrongAlgorithmPort = AvailablePortFinder.getNextAvailable();
        Main wrongAlgorithm = newMain(wrongAlgorithmPort);
        wrongAlgorithm.addInitialProperty("camel.server.mcp-oauth-profile", "wrong-algorithm");
        wrongAlgorithm.addInitialProperty("camel.server.mcp-required-scopes", "mcp:read,mcp:write");
        wrongAlgorithm.addInitialProperty("camel.oauth.wrong-algorithm.jwks-endpoint",
                issuer + "/protocol/openid-connect/certs");
        wrongAlgorithm.addInitialProperty("camel.oauth.wrong-algorithm.expected-issuer", issuer);
        wrongAlgorithm.addInitialProperty("camel.oauth.wrong-algorithm.expected-audience", CLIENT_ID);
        wrongAlgorithm.addInitialProperty("camel.oauth.wrong-algorithm.allowed-jws-algorithms", "HS256");
        wrongAlgorithm.addInitialProperty("camel.oauth.wrong-algorithm.allow-insecure-http", "true");

        try {
            wrongAlgorithm.start();
            HttpResponse<String> response = post(wrongAlgorithmPort, token);

            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.headers().firstValue("WWW-Authenticate"))
                    .hasValueSatisfying(value -> assertThat(value).contains("error=\"invalid_token\""));
        } finally {
            wrongAlgorithm.stop();
        }
    }

    @Test
    void protectedResourceMetadataIsNotPublishedWithoutOAuthProfile() throws Exception {
        int unprotectedPort = AvailablePortFinder.getNextAvailable();
        Main unprotected = new Main();
        unprotected.addInitialProperty("camel.server.enabled", "true");
        unprotected.addInitialProperty("camel.server.port", String.valueOf(unprotectedPort));
        unprotected.addInitialProperty("camel.server.mcp-enabled", "true");
        unprotected.addInitialProperty("camel.server.mcp-tags", "oauth");

        try {
            unprotected.start();
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + unprotectedPort
                                                                    + "/.well-known/oauth-protected-resource/mcp"))
                    .timeout(Duration.ofSeconds(MCP_TIMEOUT_SECONDS))
                    .GET().build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(404);
        } finally {
            unprotected.stop();
        }
    }

    @Test
    void metadataAdvertisesTheActualKeycloakIssuerAndDoesNotTrustForwardedHeaders() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + PORT
                                                                + "/.well-known/oauth-protected-resource/mcp"))
                .timeout(Duration.ofSeconds(MCP_TIMEOUT_SECONDS))
                .header("X-Forwarded-Host", "attacker.example")
                .header("X-Forwarded-Proto", "http")
                .GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).startsWith("application/json"));
        assertThat(response.body())
                .contains("\"resource\":\"" + MCP_RESOURCE_URI + "\"")
                .contains("\"authorization_servers\":[\"" + issuer + "\"]")
                .contains("\"bearer_methods_supported\":[\"header\"]")
                .contains("\"scopes_supported\":[\"mcp:read\",\"mcp:write\"]")
                .doesNotContain("attacker.example");
    }

    @Test
    void incompleteOAuthProfileFailsDuringStartup() {
        Main invalid = newMain(AvailablePortFinder.getNextAvailable());
        invalid.addInitialProperty("camel.server.mcp-oauth-profile", "incomplete");
        invalid.addInitialProperty("camel.server.mcp-resource-uri", MCP_RESOURCE_URI);
        invalid.addInitialProperty("camel.oauth.incomplete.expected-audience", CLIENT_ID);

        try {
            assertThatThrownBy(invalid::start)
                    .hasStackTraceContaining("No jwks-endpoint, introspection-endpoint, or base-uri");
        } finally {
            invalid.stop();
        }
    }

    @Test
    void unreachableIssuerDiscoveryFailsDuringStartup() {
        Main invalid = newMain(AvailablePortFinder.getNextAvailable());
        invalid.addInitialProperty("camel.server.mcp-oauth-profile", "unreachable");
        invalid.addInitialProperty("camel.server.mcp-resource-uri", MCP_RESOURCE_URI);
        invalid.addInitialProperty("camel.oauth.unreachable.base-uri", "http://127.0.0.1:1/realms/unreachable");
        invalid.addInitialProperty("camel.oauth.unreachable.expected-audience", CLIENT_ID);
        invalid.addInitialProperty("camel.oauth.unreachable.allow-insecure-http", "true");

        try {
            assertThatThrownBy(invalid::start)
                    .hasStackTraceContaining("Failed to discover OIDC endpoints");
        } finally {
            invalid.stop();
        }
    }

    @Test
    void keycloakIntrospectionAcceptsActiveTokenAndRejectsInactiveToken() throws Exception {
        int introspectionPort = AvailablePortFinder.getNextAvailable();
        Main introspection = newMain(introspectionPort);
        introspection.addInitialProperty("camel.server.mcp-oauth-profile", "keycloak-introspection");
        introspection.addInitialProperty("camel.server.mcp-required-scopes", "mcp:read,mcp:write");
        introspection.addInitialProperty("camel.oauth.keycloak-introspection.introspection-endpoint",
                issuer + "/protocol/openid-connect/token/introspect");
        introspection.addInitialProperty("camel.oauth.keycloak-introspection.introspection-client-id", CLIENT_ID);
        introspection.addInitialProperty("camel.oauth.keycloak-introspection.introspection-client-secret", clientSecret);
        introspection.addInitialProperty("camel.oauth.keycloak-introspection.expected-issuer", issuer);
        introspection.addInitialProperty("camel.oauth.keycloak-introspection.expected-audience", CLIENT_ID);
        introspection.addInitialProperty("camel.oauth.keycloak-introspection.allow-insecure-http", "true");

        try {
            introspection.start();

            String activeToken = accessToken(CLIENT_ID, clientSecret, "mcp:read mcp:write");
            HttpResponse<String> activeResponse = post(introspectionPort, activeToken);
            assertThat(activeResponse.statusCode()).isEqualTo(200);

            HttpResponse<String> inactiveResponse = post(introspectionPort, "not-a-valid-keycloak-token");
            assertThat(inactiveResponse.statusCode()).isEqualTo(401);
            assertThat(inactiveResponse.headers().firstValue("WWW-Authenticate"))
                    .hasValueSatisfying(value -> assertThat(value).contains("error=\"invalid_token\""));
        } finally {
            introspection.stop();
        }
    }

    @Test
    void unreachableIntrospectionEndpointReturns503WithoutLeakingTokenOrConfiguration() throws Exception {
        int unavailablePort = AvailablePortFinder.getNextAvailable();
        String introspectionEndpoint = "http://127.0.0.1:" + unavailablePort + "/introspect";
        String token = "opaque-test-token";
        int offlineServerPort = AvailablePortFinder.getNextAvailable();
        Main offline = newMain(offlineServerPort);
        offline.addInitialProperty("camel.server.mcp-oauth-profile", "offline");
        offline.addInitialProperty("camel.server.mcp-resource-uri", MCP_RESOURCE_URI);
        offline.addInitialProperty("camel.oauth.offline.introspection-endpoint", introspectionEndpoint);
        offline.addInitialProperty("camel.oauth.offline.introspection-client-id", "offline-client");
        offline.addInitialProperty("camel.oauth.offline.introspection-client-secret", "offline-secret");
        offline.addInitialProperty("camel.oauth.offline.expected-issuer", "https://issuer.example.test");
        offline.addInitialProperty("camel.oauth.offline.expected-audience", "mcp-server");
        offline.addInitialProperty("camel.oauth.offline.allow-insecure-http", "true");

        try {
            offline.start();
            HttpResponse<String> response = post(offlineServerPort, token);
            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(response.body()).isEqualTo("Service Unavailable")
                    .doesNotContain(token, "offline-secret", introspectionEndpoint);
        } finally {
            offline.stop();
        }
    }

    private static Main newMain(int port) {
        Main result = new Main();
        result.addInitialProperty("camel.server.enabled", "true");
        result.addInitialProperty("camel.server.port", String.valueOf(port));
        result.addInitialProperty("camel.server.mcp-enabled", "true");
        result.addInitialProperty("camel.server.mcp-tags", "oauth");
        result.addInitialProperty("camel.server.mcp-resource-uri", MCP_RESOURCE_URI);
        return result;
    }

    private static void awaitKeycloakReady() {
        Awaitility.await()
                .atMost(Duration.ofMinutes(2))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(
                            keycloakService.getKeycloakServerUrl() + "/realms/master/.well-known/openid-configuration"))
                            .timeout(Duration.ofSeconds(MCP_TIMEOUT_SECONDS))
                            .GET().build();
                    HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                    assertThat(response.statusCode()).isEqualTo(200);
                });
    }

    private static void createRealm() {
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm(REALM);
        realm.setEnabled(true);
        realm.setAccessTokenLifespan(300);

        adminClient.realms().create(realm);
    }

    private static void createScopes() {
        String readId = createOptionalScope("mcp:read");
        String writeId = createOptionalScope("mcp:write");
        // IDs are linked to both clients after each client is created.
        readScopeId = readId;
        writeScopeId = writeId;
    }

    private static String readScopeId;
    private static String writeScopeId;

    private static String createOptionalScope(String name) {
        ClientScopeRepresentation scope = new ClientScopeRepresentation();
        scope.setName(name);
        scope.setProtocol("openid-connect");
        scope.setAttributes(Map.of("include.in.token.scope", "true"));
        try (Response response = realmResource.clientScopes().create(scope)) {
            if (response.getStatus() != 201) {
                throw new IllegalStateException("Unable to create test scope " + name + ": HTTP " + response.getStatus());
            }
            return idFromLocation(response.getHeaderString("Location"));
        }
    }

    private static void createClient(String id, boolean addAudienceMapper) {
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(id);
        client.setEnabled(true);
        client.setPublicClient(false);
        client.setDirectAccessGrantsEnabled(true);
        client.setStandardFlowEnabled(false);
        client.setServiceAccountsEnabled(false);

        try (Response response = realmResource.clients().create(client)) {
            if (response.getStatus() != 201) {
                throw new IllegalStateException("Unable to create MCP OAuth test client: HTTP " + response.getStatus());
            }
            String internalId = idFromLocation(response.getHeaderString("Location"));
            ClientResource resource = realmResource.clients().get(internalId);
            resource.addOptionalClientScope(readScopeId);
            resource.addOptionalClientScope(writeScopeId);
            if (addAudienceMapper) {
                ProtocolMapperRepresentation audienceMapper = new ProtocolMapperRepresentation();
                audienceMapper.setName("mcp-audience");
                audienceMapper.setProtocol("openid-connect");
                audienceMapper.setProtocolMapper("oidc-audience-mapper");
                audienceMapper.setConfig(Map.of(
                        "included.client.audience", CLIENT_ID,
                        "access.token.claim", "true",
                        "id.token.claim", "false"));
                resource.getProtocolMappers().createMapper(audienceMapper);
            }
            if (CLIENT_SCP_ONLY_ID.equals(id)) {
                ProtocolMapperRepresentation scpMapper = new ProtocolMapperRepresentation();
                scpMapper.setName("mcp-scp-claim");
                scpMapper.setProtocol("openid-connect");
                scpMapper.setProtocolMapper("oidc-hardcoded-claim-mapper");
                scpMapper.setConfig(Map.of(
                        "claim.name", "scp",
                        "claim.value", "mcp:read mcp:write",
                        "jsonType.label", "String",
                        "access.token.claim", "true",
                        "id.token.claim", "false",
                        "userinfo.token.claim", "false"));
                resource.getProtocolMappers().createMapper(scpMapper);
            }
            String secret = resource.getSecret().getValue();
            if (CLIENT_ID.equals(id)) {
                clientSecret = secret;
            } else if (CLIENT_WITHOUT_AUDIENCE_ID.equals(id)) {
                clientWithoutAudienceSecret = secret;
            } else if (CLIENT_SCP_ONLY_ID.equals(id)) {
                clientScpOnlySecret = secret;
            }
        }
    }

    private static void createRoleAndUser() {
        RoleRepresentation role = new RoleRepresentation();
        role.setName(REALM_ROLE);
        realmResource.roles().create(role);

        UserRepresentation user = new UserRepresentation();
        user.setUsername(USERNAME);
        user.setFirstName("MCP");
        user.setLastName("Alice");
        user.setEnabled(true);
        user.setEmail(USERNAME + "@example.test");
        user.setEmailVerified(true);
        user.setRequiredActions(List.of());
        try (Response response = realmResource.users().create(user)) {
            if (response.getStatus() != 201) {
                throw new IllegalStateException("Unable to create MCP OAuth test user: HTTP " + response.getStatus());
            }
            userId = idFromLocation(response.getHeaderString("Location"));
        }

        UserResource resource = realmResource.users().get(userId);
        CredentialRepresentation password = new CredentialRepresentation();
        password.setType(CredentialRepresentation.PASSWORD);
        password.setValue(PASSWORD);
        password.setTemporary(false);
        resource.resetPassword(password);
        resource.roles().realmLevel().add(List.of(realmResource.roles().get(REALM_ROLE).toRepresentation()));
    }

    private static String accessToken(String clientId, String secret, String scopes) throws Exception {
        String form = "grant_type=password"
                      + "&client_id=" + encode(clientId)
                      + "&client_secret=" + encode(secret)
                      + "&username=" + encode(USERNAME)
                      + "&password=" + encode(PASSWORD)
                      + "&scope=" + encode(scopes);
        HttpRequest request = HttpRequest.newBuilder(URI.create(issuer + "/protocol/openid-connect/token"))
                .timeout(Duration.ofSeconds(MCP_TIMEOUT_SECONDS))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Keycloak token endpoint returned HTTP " + response.statusCode()
                                            + ": " + response.body());
        }
        Matcher matcher = ACCESS_TOKEN.matcher(response.body());
        if (!matcher.find()) {
            throw new IllegalStateException("Keycloak token response did not contain an access_token");
        }
        return matcher.group(1);
    }

    private static String tamperJwtSignature(String token) {
        String[] segments = token.split("\\.", -1);
        if (segments.length != 3 || segments[2].isEmpty()) {
            throw new IllegalArgumentException("Expected a signed JWT with three segments");
        }

        String signature = segments[2];
        char replacement = signature.charAt(0) == 'A' ? 'B' : 'A';
        segments[2] = replacement + signature.substring(1);

        return String.join(".", segments);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String idFromLocation(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("Keycloak admin response did not contain a Location header");
        }
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private static McpSyncClient newClient(String token) {
        return McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + PORT)
                .httpRequestCustomizer(
                        (builder, method, uri, body, context) -> builder.header("Authorization", "Bearer " + token))
                .build())
                .requestTimeout(Duration.ofSeconds(MCP_TIMEOUT_SECONDS))
                .initializationTimeout(Duration.ofSeconds(MCP_TIMEOUT_SECONDS))
                .build();
    }

    private static HttpResponse<String> post(String token) throws Exception {
        return post(PORT, token);
    }

    private static HttpResponse<String> post(int port, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                .timeout(Duration.ofSeconds(MCP_TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(INITIALIZE_REQUEST));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
