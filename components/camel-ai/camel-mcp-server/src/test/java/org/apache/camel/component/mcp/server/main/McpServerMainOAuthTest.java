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

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mcp.server.McpToolCallContext;
import org.apache.camel.main.Main;
import org.apache.camel.spi.OAuthTokenValidationConfig;
import org.apache.camel.spi.OAuthTokenValidationFactory;
import org.apache.camel.spi.OAuthTokenValidationResult;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end tests for the CAMEL-26356 MCP OAuth HTTP boundary and identity propagation. A deterministic in-memory
 * validation factory is used so these tests do not depend on an external IdP.
 */
class McpServerMainOAuthTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final int PORT = AvailablePortFinder.getNextAvailable();
    private static final String RESOURCE_URI = "https://localhost:" + PORT + "/mcp";
    private static final String RESOURCE_METADATA_URI
            = "https://localhost:" + PORT + "/.well-known/oauth-protected-resource/mcp";
    private static final String MISSING_TOKEN_CHALLENGE
            = "Bearer resource_metadata=\"" + RESOURCE_METADATA_URI + "\", scope=\"mcp:read mcp:write\"";
    private static final String INVALID_TOKEN_CHALLENGE
            = "Bearer error=\"invalid_token\", resource_metadata=\"" + RESOURCE_METADATA_URI
              + "\", scope=\"mcp:read mcp:write\"";
    private static final String INITIALIZE_REQUEST = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",
            "capabilities":{},"clientInfo":{"name":"oauth-test","version":"1"}}}
            """;

    private static Main main;

    @BeforeAll
    static void startMain() throws Exception {
        main = new Main();
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-tool:oauth_identity?tags=oauth&description=Return the authenticated OAuth identity")
                        .process(exchange -> exchange.getMessage().setBody(
                                "subject=" + exchange.getProperty(McpToolCallContext.OAUTH_SUBJECT_PROPERTY)
                                                                           + ";scopes="
                                                                           + exchange.getProperty(
                                                                                   McpToolCallContext.OAUTH_SCOPES_PROPERTY)
                                                                           + ";claims="
                                                                           + exchange.getProperty(
                                                                                   McpToolCallContext.OAUTH_CLAIMS_PROPERTY)
                                                                           + ";roles=" + exchange.getProperty(
                                                                                   McpToolCallContext.SECURITY_PRINCIPAL_ROLES_PROPERTY)
                                                                           + ";standardValidationResult="
                                                                           + (exchange.getProperty(
                                                                                   McpToolCallContext.OAUTH_TOKEN_VALIDATION_RESULT_PROPERTY)
                                                                              != null)));
            }
        });

        main.bind(OAuthTokenValidationFactory.FACTORY, new TestOAuthTokenValidationFactory());
        main.addInitialProperty("camel.server.enabled", "true");
        main.addInitialProperty("camel.server.port", String.valueOf(PORT));
        main.addInitialProperty("camel.server.mcp-enabled", "true");
        main.addInitialProperty("camel.server.mcp-tags", "oauth");
        main.addInitialProperty("camel.server.mcp-oauth-profile", "mcp-test");
        main.addInitialProperty("camel.server.mcp-required-scopes", "mcp:read,mcp:write");
        main.addInitialProperty("camel.server.mcp-resource-uri", RESOURCE_URI);
        main.addInitialProperty("camel.oauth.mcp-test.base-uri", "https://id.example.test/realms/camel");
        main.addInitialProperty("camel.oauth.mcp-test.expected-audience", "mcp-server");
        main.start();
    }

    @AfterAll
    static void stopMain() {
        if (main != null) {
            main.stop();
        }
    }

    @Test
    void missingBearerTokenIsRejectedOnPostGetAndDelete() throws Exception {
        HttpResponse<String> post = post(null);
        assertThat(post.statusCode()).isEqualTo(401);
        assertThat(post.headers().firstValue("WWW-Authenticate")).hasValue(MISSING_TOKEN_CHALLENGE);

        HttpResponse<String> get = HTTP.send(
                mcpRequest(null).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(get.statusCode()).isEqualTo(401);
        assertThat(get.headers().firstValue("WWW-Authenticate")).hasValue(MISSING_TOKEN_CHALLENGE);

        HttpResponse<String> delete = HTTP.send(
                mcpRequest(null).DELETE().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(delete.statusCode()).isEqualTo(401);
        assertThat(delete.headers().firstValue("WWW-Authenticate")).hasValue(MISSING_TOKEN_CHALLENGE);
    }

    @Test
    void rejectedLargeRequestsDoNotBlockTheNextRequestOnTheSameConnection() throws Exception {
        byte[] largeBody = ("{\"payload\":\"" + "x".repeat(300 * 1024) + "\"}")
                .getBytes(StandardCharsets.UTF_8);

        assertRejectedLargePostDoesNotBlockConnection("invalid", 401, largeBody);
        assertRejectedLargePostDoesNotBlockConnection("insufficient", 403, largeBody);
        assertRejectedLargePostDoesNotBlockConnection("unavailable", 503, largeBody);
    }

    @Test
    void protectedResourceMetadataIsPublishedAtTheRfc9728Path() throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + PORT
                                                  + "/.well-known/oauth-protected-resource/mcp"))
                        .timeout(Duration.ofSeconds(10))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                value -> assertThat(value).startsWith("application/json"));
        assertThat(response.body())
                .contains("\"resource\":\"" + RESOURCE_URI + "\"")
                .contains("\"authorization_servers\":[\"https://id.example.test/realms/camel\"]")
                .contains("\"bearer_methods_supported\":[\"header\"]")
                .contains("\"scopes_supported\":[\"mcp:read\",\"mcp:write\"]");
    }

    @Test
    void forwardedHeadersCannotChangeTheAdvertisedResourceUri() throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + PORT
                                                  + "/.well-known/oauth-protected-resource/mcp"))
                        .timeout(Duration.ofSeconds(10))
                        .header("X-Forwarded-Host", "attacker.example")
                        .header("X-Forwarded-Proto", "http")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"resource\":\"" + RESOURCE_URI + "\"")
                .doesNotContain("attacker.example");
    }

    @Test
    void invalidBearerTokenReturnsInvalidTokenChallenge() throws Exception {
        HttpResponse<String> response = post("invalid");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate"))
                .hasValue(INVALID_TOKEN_CHALLENGE);
        assertThat(response.body()).doesNotContain("invalid");
    }

    @Test
    void validatorInfrastructureFailureReturnsServiceUnavailable() throws Exception {
        HttpResponse<String> response = post("unavailable");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("Service Unavailable").doesNotContain("unavailable");
    }

    @Test
    void validTokenWithoutAllRequiredScopesReturnsInsufficientScope() throws Exception {
        HttpResponse<String> response = post("insufficient");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("WWW-Authenticate"))
                .hasValueSatisfying(value -> assertThat(value)
                        .contains("error=\"insufficient_scope\"")
                        .contains("resource_metadata=\"" + RESOURCE_METADATA_URI + "\"")
                        .contains("scope=\"mcp:read mcp:write\""));
    }

    @Test
    void rejectsNonRootHttpContextPathWhenOAuthEngineStarts() {
        Main nonRootMain = new Main();
        nonRootMain.addInitialProperty("camel.server.enabled", "true");
        nonRootMain.addInitialProperty("camel.server.port", String.valueOf(AvailablePortFinder.getNextAvailable()));
        nonRootMain.addInitialProperty("camel.server.path", "/api");
        nonRootMain.addInitialProperty("camel.server.mcp-enabled", "true");
        nonRootMain.addInitialProperty("camel.server.mcp-oauth-profile", "mcp-test");
        nonRootMain.addInitialProperty("camel.server.mcp-resource-uri", "https://localhost/mcp");

        try {
            assertThatThrownBy(nonRootMain::start)
                    .hasStackTraceContaining(
                            "RFC 9728 protected-resource metadata must be available at the origin root");
        } finally {
            nonRootMain.stop();
        }
    }

    @Test
    void sessionIsBoundToTheOAuthSubjectAndRequiresASubjectClaim() throws Exception {
        // A valid access token without a subject cannot establish a user-bound stateful session.
        assertThat(post("no-sub").statusCode()).isEqualTo(403);

        HttpResponse<String> initialized = post("valid");
        assertThat(initialized.statusCode()).isEqualTo(200);
        String sessionId = initialized.headers().firstValue("Mcp-Session-Id").orElseThrow();

        // A different subject cannot use the session for POST, GET, or DELETE. Use a syntactically
        // valid notification for POST; the ownership check must reject it before session dispatch.
        HttpResponse<String> postAsBob = HTTP.send(
                mcpRequest("bob")
                        .header("Mcp-Session-Id", sessionId)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(postAsBob.statusCode()).isEqualTo(404);

        HttpResponse<String> getAsBob = HTTP.send(
                mcpRequest("bob").header("Mcp-Session-Id", sessionId).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(getAsBob.statusCode()).isEqualTo(404);

        HttpResponse<String> deleteAsBob = HTTP.send(
                mcpRequest("bob").header("Mcp-Session-Id", sessionId).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(deleteAsBob.statusCode()).isEqualTo(404);

        // The original subject still owns the session and can terminate it.
        HttpResponse<String> deleteAsAlice = HTTP.send(
                mcpRequest("valid").header("Mcp-Session-Id", sessionId).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(deleteAsAlice.statusCode()).isEqualTo(200);
    }

    @Test
    void validBearerTokenWithRequiredScopesCanUseMcpEndpoint() {
        McpSyncClient client = newClient("valid");
        try {
            client.initialize();

            assertThat(client.listTools().tools())
                    .extracting(McpSchema.Tool::name)
                    .contains("oauth_identity");
        } finally {
            client.closeGracefully();
        }
    }

    @Test
    void oauthSubjectScopesClaimsAndRolesReachTheToolRoute() {
        McpSyncClient client = newClient("valid");
        try {
            client.initialize();

            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest("oauth_identity", Map.of()));

            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            String content = result.content().toString();
            assertThat(content)
                    .contains("subject=alice")
                    .contains("scopes=[mcp:read, mcp:write]")
                    .contains("tenant=engineering")
                    .contains("roles=[admin, analyst]")
                    .contains("standardValidationResult=true");
        } finally {
            client.closeGracefully();
        }
    }

    private static void assertRejectedLargePostDoesNotBlockConnection(
            String token, int expectedStatus, byte[] body)
            throws Exception {
        try (Socket socket = new Socket("localhost", PORT)) {
            socket.setSoTimeout(5_000);
            BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
            OutputStream output = socket.getOutputStream();

            String postHeaders = "POST /mcp HTTP/1.1\r\n"
                                 + "Host: localhost:" + PORT + "\r\n"
                                 + "Accept: application/json, text/event-stream\r\n"
                                 + "Authorization: Bearer " + token + "\r\n"
                                 + "Content-Type: application/json\r\n"
                                 + "Content-Length: " + body.length + "\r\n"
                                 + "Connection: keep-alive\r\n\r\n";
            output.write(postHeaders.getBytes(StandardCharsets.US_ASCII));
            output.write(body);
            output.flush();

            assertThat(readHttpResponseStatus(input)).isEqualTo(expectedStatus);

            String getRequest = "GET /mcp HTTP/1.1\r\n"
                                + "Host: localhost:" + PORT + "\r\n"
                                + "Accept: application/json, text/event-stream\r\n"
                                + "Authorization: Bearer " + token + "\r\n"
                                + "Connection: keep-alive\r\n\r\n";
            output.write(getRequest.getBytes(StandardCharsets.US_ASCII));
            output.flush();

            // This request is deliberately sent on the same socket. If the rejected POST
            // left the request paused, the server cannot parse or answer this follow-up.
            assertThat(readHttpResponseStatus(input)).isEqualTo(expectedStatus);
        }
    }

    private static int readHttpResponseStatus(InputStream input) throws IOException {
        String statusLine = readHttpLine(input);
        String[] statusParts = statusLine.split(" ", 3);
        if (statusParts.length < 2) {
            throw new IOException("Invalid HTTP status line: " + statusLine);
        }

        long contentLength = -1;
        boolean chunked = false;
        String header;
        while (!(header = readHttpLine(input)).isEmpty()) {
            int separator = header.indexOf(':');
            if (separator < 0) {
                continue;
            }
            String name = header.substring(0, separator).trim();
            String value = header.substring(separator + 1).trim();
            if ("Content-Length".equalsIgnoreCase(name)) {
                contentLength = Long.parseLong(value);
            } else if ("Transfer-Encoding".equalsIgnoreCase(name)) {
                chunked = value.toLowerCase(java.util.Locale.ROOT).contains("chunked");
            }
        }

        if (chunked) {
            consumeChunkedHttpBody(input);
        } else if (contentLength >= 0) {
            skipHttpBytes(input, contentLength);
        }
        return Integer.parseInt(statusParts[1]);
    }

    private static void consumeChunkedHttpBody(InputStream input) throws IOException {
        while (true) {
            String sizeLine = readHttpLine(input);
            int extension = sizeLine.indexOf(';');
            String size = extension >= 0 ? sizeLine.substring(0, extension) : sizeLine;
            long chunkSize = Long.parseLong(size.trim(), 16);
            if (chunkSize == 0) {
                while (!readHttpLine(input).isEmpty()) {
                    // Consume optional trailer headers.
                }
                return;
            }
            skipHttpBytes(input, chunkSize);
            if (!readHttpLine(input).isEmpty()) {
                throw new IOException("Invalid chunk terminator in HTTP response");
            }
        }
    }

    private static void skipHttpBytes(InputStream input, long count) throws IOException {
        long remaining = count;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped == 0) {
                if (input.read() < 0) {
                    throw new EOFException("Unexpected end of HTTP response body");
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private static String readHttpLine(InputStream input) throws IOException {
        StringBuilder line = new StringBuilder();
        int current;
        while ((current = input.read()) >= 0) {
            if (current == '\r') {
                if (input.read() != '\n') {
                    throw new IOException("Invalid HTTP line ending");
                }
                return line.toString();
            }
            line.append((char) current);
        }
        throw new EOFException("Unexpected end of HTTP response");
    }

    private static McpSyncClient newClient(String token) {
        return McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + PORT)
                .httpRequestCustomizer((builder, method, uri, body, context) -> builder
                        .header("Authorization", "Bearer " + token))
                .build())
                .requestTimeout(Duration.ofSeconds(10))
                .initializationTimeout(Duration.ofSeconds(10))
                .build();
    }

    private static HttpResponse<String> post(String token) throws Exception {
        return HTTP.send(mcpRequest(token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(INITIALIZE_REQUEST))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpRequest.Builder mcpRequest(String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + PORT + "/mcp"))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json, text/event-stream");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private static final class TestOAuthTokenValidationFactory implements OAuthTokenValidationFactory {

        @Override
        public OAuthTokenValidationResult validateToken(OAuthTokenValidationConfig config, String token) {
            if ("invalid".equals(token)) {
                return OAuthTokenValidationResult.invalid("test token rejected");
            }
            if ("unavailable".equals(token)) {
                throw new IllegalStateException("test identity provider unavailable");
            }

            List<String> scopes = "insufficient".equals(token)
                    ? List.of("mcp:read")
                    : List.of("mcp:read", "mcp:write");
            String subject;
            if ("bob".equals(token)) {
                subject = "bob";
            } else if ("no-sub".equals(token)) {
                subject = null;
            } else {
                subject = "alice";
            }
            Map<String, Object> claims = new LinkedHashMap<>();
            if (subject != null) {
                claims.put("sub", subject);
            }
            claims.put("tenant", "engineering");
            claims.put("roles", List.of("admin", "analyst"));
            return OAuthTokenValidationResult.valid(
                    subject,
                    "https://id.example.test/realms/camel",
                    List.of("mcp-server"),
                    scopes,
                    claims,
                    Instant.now().plusSeconds(300).getEpochSecond());
        }

        @Override
        public void validateConfiguration(OAuthTokenValidationConfig config) {
            // The fake validator needs no remote configuration; profile resolution is still exercised by Camel.
        }
    }
}
