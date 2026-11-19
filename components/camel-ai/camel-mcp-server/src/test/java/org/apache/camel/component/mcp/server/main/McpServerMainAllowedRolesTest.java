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

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import javax.crypto.KeyGenerator;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code allowedRoles} option of an {@code ai-tool} route over a real MCP call: the Camel Main HTTP server
 * authenticates the caller with a signed JWT, the MCP bridge puts the roles of the token on the tool exchange, and the
 * tool answers the caller who has the role and refuses the one who has not.
 */
class McpServerMainAllowedRolesTest {

    private static final String ISSUER = "https://issuer.camel.example";
    private static final String KEYSTORE_PASSWORD = "changeme";

    @TempDir
    static Path keystoreDir;

    private static AvailablePortFinder.Port portRef;
    private static int port;
    private static Main main;
    private static Vertx vertx;
    private static JWTAuth tokens;

    @BeforeAll
    static void startMain() throws Exception {
        Path keystore = createKeystore();
        portRef = AvailablePortFinder.find();
        port = portRef.getPort();
        main = new Main();
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-tool:order_costs?tags=roles&description=Costs of a production order&allowedRoles=finance")
                        .setBody(constant("costs=42"));
            }
        });
        main.addInitialProperty("camel.server.enabled", "true");
        main.addInitialProperty("camel.server.port", String.valueOf(port));
        main.addInitialProperty("camel.server.authentication-enabled", "true");
        main.addInitialProperty("camel.server.jwt-keystore-type", "pkcs12");
        main.addInitialProperty("camel.server.jwt-keystore-path", keystore.toString());
        main.addInitialProperty("camel.server.jwt-keystore-password", KEYSTORE_PASSWORD);
        main.addInitialProperty("camel.server.jwt-issuer", ISSUER);
        main.addInitialProperty("camel.server.mcp-enabled", "true");
        main.addInitialProperty("camel.server.mcp-tags", "roles");
        main.start();

        vertx = Vertx.vertx();
        tokens = JWTAuth.create(vertx, new JWTAuthOptions(
                new JsonObject().put("keyStore", new JsonObject()
                        .put("type", "pkcs12")
                        .put("path", keystore.toString())
                        .put("password", KEYSTORE_PASSWORD))));
    }

    @AfterAll
    static void stopMain() {
        if (main != null) {
            main.stop();
        }
        if (vertx != null) {
            vertx.close();
        }
        if (portRef != null) {
            portRef.release();
        }
    }

    @Test
    void theToolAnswersACallerWhoHasTheRole() {
        McpSyncClient client = newClient(token("alice", "sales", "finance"));
        try {
            client.initialize();

            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest("order_costs", Map.of()));

            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(result.content().toString()).contains("costs=42");
        } finally {
            client.closeGracefully();
        }
    }

    @Test
    void theToolRefusesACallerWhoDoesNotHaveTheRole() {
        McpSyncClient client = newClient(token("bob", "sales"));
        try {
            client.initialize();

            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest("order_costs", Map.of()));

            assertThat(result.isError()).isEqualTo(Boolean.TRUE);
            // the generic refusal: neither the result of the route nor the roles the tool asks for
            assertThat(result.content().toString())
                    .contains("Access denied: not authorized to call tool 'order_costs'")
                    .doesNotContain("costs=42")
                    .doesNotContain("finance");
        } finally {
            client.closeGracefully();
        }
    }

    /**
     * A keystore with the secret key the server verifies the HS256 signature of a token with, and this test signs with.
     * Vert.x reads the key whose alias is the name of the algorithm.
     */
    private static Path createKeystore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("pkcs12");
        keyStore.load(null, null);
        keyStore.setEntry("HS256", new KeyStore.SecretKeyEntry(KeyGenerator.getInstance("HmacSHA256").generateKey()),
                new KeyStore.PasswordProtection(KEYSTORE_PASSWORD.toCharArray()));
        Path keystore = keystoreDir.resolve("jwt.p12");
        try (OutputStream out = Files.newOutputStream(keystore)) {
            keyStore.store(out, KEYSTORE_PASSWORD.toCharArray());
        }
        return keystore;
    }

    private static String token(String subject, String... roles) {
        return tokens.generateToken(
                new JsonObject().put("sub", subject).put("roles", new JsonArray(List.of(roles))),
                new JWTOptions().setAlgorithm("HS256").setIssuer(ISSUER));
    }

    private static McpSyncClient newClient(String token) {
        return McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .httpRequestCustomizer((builder, method, uri, body, context) -> builder
                        .header("Authorization", "Bearer " + token))
                .build())
                .requestTimeout(Duration.ofSeconds(10))
                .initializationTimeout(Duration.ofSeconds(10))
                .build();
    }
}
