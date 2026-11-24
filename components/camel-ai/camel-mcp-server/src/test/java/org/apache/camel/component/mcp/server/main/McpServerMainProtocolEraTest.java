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
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.modern.client.transport.HttpClientMcpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.AvailablePortFinder.Port;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * By default the Camel Main MCP server serves an {@code ai-tool} route to a client of each protocol era (CAMEL-27145),
 * and the protocol era and the stateless option can be configured with Camel Main properties.
 */
class McpServerMainProtocolEraTest {

    @Test
    void byDefaultBothClientsAreServed() throws Exception {
        try (Port portHolder = AvailablePortFinder.find()) {
            int port = portHolder.getPort();
            Main main = main(port);
            main.start();

            McpSyncClient legacy = null;
            try {
                legacy = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port).build())
                        .requestTimeout(Duration.ofSeconds(10))
                        .initializationTimeout(Duration.ofSeconds(10))
                        .build();
                legacy.initialize();
                McpSchema.CallToolResult legacyResult = legacy.callTool(
                        McpSchema.CallToolRequest.builder("say_hello").arguments(Map.of("name", "legacy")).build());
                assertThat(legacyResult.content().toString()).contains("Hello legacy");

                io.modelcontextprotocol.modern.client.McpSyncClient modern
                        = io.modelcontextprotocol.modern.client.McpClient
                                .sync(HttpClientMcpTransport.builder("http://localhost:" + port + "/mcp").build())
                                .clientInfo(io.modelcontextprotocol.modern.McpSchema.Implementation.builder("test", "1")
                                        .build())
                                .build();
                io.modelcontextprotocol.modern.McpSchema.CallToolResult modernResult
                        = modern.tool("say_hello").arguments(Map.of("name", "modern")).call();
                assertThat(modernResult.content().toString()).contains("Hello modern");
            } finally {
                if (legacy != null) {
                    legacy.closeGracefully();
                }
                main.stop();
            }
        }
    }

    @Test
    void statelessFromPropertiesServesTheLegacyClientWithoutSession() throws Exception {
        try (Port portHolder = AvailablePortFinder.find()) {
            int port = portHolder.getPort();
            Main main = main(port);
            main.addInitialProperty("camel.server.mcp-stateless", "true");
            main.start();

            McpSyncClient legacy = null;
            try {
                HttpResponse<String> initialize = HttpClient.newHttpClient().send(HttpRequest
                        .newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json, text/event-stream")
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25",
                                "capabilities":{},"clientInfo":{"name":"test","version":"1"}}}"""))
                        .build(), HttpResponse.BodyHandlers.ofString());
                assertThat(initialize.statusCode()).isEqualTo(200);
                assertThat(initialize.headers().firstValue("mcp-session-id")).isEmpty();

                legacy = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port).build())
                        .requestTimeout(Duration.ofSeconds(10))
                        .initializationTimeout(Duration.ofSeconds(10))
                        .build();
                legacy.initialize();
                McpSchema.CallToolResult result = legacy.callTool(
                        McpSchema.CallToolRequest.builder("say_hello").arguments(Map.of("name", "stateless")).build());
                assertThat(result.content().toString()).contains("Hello stateless");
            } finally {
                if (legacy != null) {
                    legacy.closeGracefully();
                }
                main.stop();
            }
        }
    }

    @Test
    void unknownProtocolEraFailsFast() {
        Main main = main(AvailablePortFinder.getNextAvailable());
        main.addInitialProperty("camel.server.mcp-protocol-era", "stateless");

        try {
            assertThatThrownBy(main::start)
                    .hasStackTraceContaining("Invalid MCP protocol era 'stateless': expected legacy, dual or modern");
        } finally {
            main.stop();
        }
    }

    private static Main main(int port) {
        Main main = new Main();
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-tool:say_hello?tags=crm&description=Say hello"
                     + "&parameter.name=string&parameter.name.required=true")
                        .setBody(simple("Hello ${header.name}"));
            }
        });
        main.addInitialProperty("camel.server.enabled", "true");
        main.addInitialProperty("camel.server.port", String.valueOf(port));
        main.addInitialProperty("camel.server.mcp-enabled", "true");
        main.addInitialProperty("camel.server.mcp-tags", "crm");
        return main;
    }
}
