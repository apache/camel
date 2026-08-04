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
import java.util.Base64;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the {@code camel.server.authentication*} handlers of the main HTTP server also protect the MCP
 * endpoint: the MCP routes are registered on the same Vert.x sub-router the authentication handlers are mounted on
 * (default path {@code /*}), and the handlers are installed before the MCP routes, so every {@code /mcp} request passes
 * authentication first.
 */
class McpServerMainAuthenticationTest {

    private static final String INITIALIZE_REQUEST = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05",
            "capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""";

    private static final int PORT = AvailablePortFinder.getNextAvailable();
    private static Main main;

    @BeforeAll
    static void startMain() {
        main = new Main();
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-tool:say_hello?tags=secured&description=Say hello"
                     + "&parameter.name=string&parameter.name.required=true")
                        .setBody(simple("Hello ${header.name}"));
            }
        });
        main.addInitialProperty("camel.server.enabled", "true");
        main.addInitialProperty("camel.server.port", String.valueOf(PORT));
        main.addInitialProperty("camel.server.authentication-enabled", "true");
        main.addInitialProperty("camel.server.basic-properties-file", "mcp-basic-auth.properties");
        main.addInitialProperty("camel.server.mcp-enabled", "true");
        main.addInitialProperty("camel.server.mcp-tags", "secured");
        main.start();
    }

    @AfterAll
    static void stopMain() {
        if (main != null) {
            main.stop();
        }
    }

    @Test
    void testMcpRequestsWithoutCredentialsAreRejected() throws Exception {
        HttpClient http = HttpClient.newHttpClient();

        HttpResponse<String> post = http.send(mcpRequest()
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(INITIALIZE_REQUEST))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(post.statusCode()).isEqualTo(401);

        HttpResponse<String> get = http.send(mcpRequest().GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(get.statusCode()).isEqualTo(401);

        HttpResponse<String> delete = http.send(mcpRequest().DELETE().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(delete.statusCode()).isEqualTo(401);
    }

    @Test
    void testMcpWorksWithBasicCredentials() {
        String credentials = Base64.getEncoder().encodeToString("camel:mcpPass".getBytes(UTF_8));
        McpSyncClient client = null;
        try {
            client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + PORT)
                    .httpRequestCustomizer((builder, method, uri, body, context) -> builder
                            .header("Authorization", "Basic " + credentials))
                    .build())
                    .requestTimeout(Duration.ofSeconds(10))
                    .initializationTimeout(Duration.ofSeconds(10))
                    .build();

            client.initialize();

            assertThat(client.listTools().tools())
                    .extracting(McpSchema.Tool::name)
                    .contains("say_hello");

            McpSchema.CallToolResult result
                    = client.callTool(new McpSchema.CallToolRequest("say_hello", Map.of("name", "Camel")));
            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(result.content().toString()).contains("Hello Camel");
        } finally {
            if (client != null) {
                client.closeGracefully();
            }
        }
    }

    private static HttpRequest.Builder mcpRequest() {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + PORT + "/mcp"))
                .header("Accept", "application/json, text/event-stream");
    }
}
