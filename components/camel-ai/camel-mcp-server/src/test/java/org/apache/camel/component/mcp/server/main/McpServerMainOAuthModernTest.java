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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mcp.server.McpToolCallContext;
import org.apache.camel.main.Main;
import org.apache.camel.spi.OAuthTokenValidationFactory;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OAuth on the requests of protocol version 2026-07-28 (CAMEL-27145): every request is authenticated on its own, before
 * its protocol version is looked at, and the caller identity reaches the tool route. As such a request creates no
 * session to own, a token without a subject is served, while a session still requires one.
 */
class McpServerMainOAuthModernTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final String MODERN_CALL = """
            {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"oauth_identity","arguments":{},
            "_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28",
            "io.modelcontextprotocol/clientCapabilities":{}}}}""";

    private static final String LEGACY_INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25",
            "capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""";

    private static AvailablePortFinder.Port portRef;
    private static Main main;

    @BeforeAll
    static void startMain() throws Exception {
        portRef = AvailablePortFinder.find();
        int port = portRef.getPort();
        main = new Main();
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-tool:oauth_identity?tags=oauth&description=Return the authenticated OAuth identity")
                        .process(exchange -> exchange.getMessage().setBody(
                                "subject=" + exchange.getProperty(McpToolCallContext.OAUTH_SUBJECT_PROPERTY)));
            }
        });
        main.bind(OAuthTokenValidationFactory.FACTORY, new McpServerMainOAuthTest.TestOAuthTokenValidationFactory());
        main.addInitialProperty("camel.server.enabled", "true");
        main.addInitialProperty("camel.server.port", String.valueOf(port));
        main.addInitialProperty("camel.server.mcp-enabled", "true");
        main.addInitialProperty("camel.server.mcp-tags", "oauth");
        main.addInitialProperty("camel.server.mcp-oauth-profile", "mcp-test");
        main.addInitialProperty("camel.server.mcp-required-scopes", "mcp:read,mcp:write");
        main.addInitialProperty("camel.server.mcp-resource-uri", "https://localhost:" + port + "/mcp");
        main.addInitialProperty("camel.oauth.mcp-test.base-uri", "https://id.example.test/realms/camel");
        main.addInitialProperty("camel.oauth.mcp-test.expected-audience", "mcp-server");
        main.start();
    }

    @AfterAll
    static void stopMain() {
        if (main != null) {
            main.stop();
        }
        if (portRef != null) {
            portRef.release();
        }
    }

    @Test
    void modernRequestWithoutTokenIsRejected() throws Exception {
        HttpResponse<String> response = postModern(null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(
                challenge -> assertThat(challenge).contains("resource_metadata="));
    }

    @Test
    void modernRequestWithTooFewScopesIsRejected() throws Exception {
        assertThat(postModern("insufficient").statusCode()).isEqualTo(403);
    }

    @Test
    void callerIdentityReachesTheToolOnAModernRequest() throws Exception {
        HttpResponse<String> response = postModern("valid");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("subject=alice");
    }

    @Test
    void tokenWithoutSubjectIsServedWhenNoSessionIsCreated() throws Exception {
        assertThat(postModern("no-sub").statusCode()).isEqualTo(200);

        // a session must be owned by a subject
        HttpResponse<String> initialize = post("no-sub", LEGACY_INITIALIZE, null);
        assertThat(initialize.statusCode()).isEqualTo(403);
        assertThat(initialize.headers().firstValue("mcp-session-id")).isEmpty();
    }

    private static HttpResponse<String> postModern(String token) throws Exception {
        return post(token, MODERN_CALL, "oauth_identity");
    }

    private static HttpResponse<String> post(String token, String body, String toolName) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + portRef.getPort() + "/mcp"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (toolName != null) {
            builder.header("MCP-Protocol-Version", "2026-07-28")
                    .header("Mcp-Method", "tools/call")
                    .header("Mcp-Name", toolName);
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
