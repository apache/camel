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

import java.time.Duration;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies CAMEL-24399: additional MCP server metadata is advertised on initialize.
 */
class McpServerMainMetadataTest {

    @Test
    void testMcpServerMetadataFromProperties() throws Exception {
        int port = AvailablePortFinder.getNextAvailable();

        Main main = new Main();
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-tool:say_hello?tags=crm&description=Say hello")
                        .setBody(constant("Hello"));
            }
        });
        main.addInitialProperty("camel.server.enabled", "true");
        main.addInitialProperty("camel.server.port", String.valueOf(port));
        main.addInitialProperty("camel.server.mcp-enabled", "true");
        main.addInitialProperty("camel.server.mcp-tags", "crm");
        main.addInitialProperty("camel.server.mcp-server-name", "wanaku-camel-mcp");
        main.addInitialProperty("camel.server.mcp-server-title", "Wanaku Camel MCP");
        main.addInitialProperty("camel.server.mcp-server-description", "Integration tools for Wanaku");
        main.addInitialProperty("camel.server.mcp-server-website-url", "https://camel.apache.org/");
        main.addInitialProperty("camel.server.mcp-instructions", "Use these tools to operate the integration.");
        main.addInitialProperty("camel.server.mcp-server-icons", """
                [{"src":"https://example.com/icon.png","mimeType":"image/png","sizes":["48x48"],"theme":"light"}]
                """);
        main.start();

        McpSyncClient client = null;
        try {
            client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port).build())
                    .requestTimeout(Duration.ofSeconds(10))
                    .initializationTimeout(Duration.ofSeconds(10))
                    .build();

            McpSchema.InitializeResult init = client.initialize();
            McpSchema.Implementation serverInfo = init.serverInfo();

            assertThat(serverInfo.name()).isEqualTo("wanaku-camel-mcp");
            assertThat(serverInfo.title()).isEqualTo("Wanaku Camel MCP");
            assertThat(serverInfo.description()).isEqualTo("Integration tools for Wanaku");
            assertThat(serverInfo.websiteUrl()).isEqualTo("https://camel.apache.org/");
            assertThat(serverInfo.version()).isNotBlank();
            assertThat(serverInfo.icons()).hasSize(1);
            assertThat(serverInfo.icons().get(0).src()).isEqualTo("https://example.com/icon.png");
            assertThat(serverInfo.icons().get(0).mimeType()).isEqualTo("image/png");
            assertThat(serverInfo.icons().get(0).sizes()).containsExactly("48x48");
            assertThat(serverInfo.icons().get(0).theme()).isEqualTo("light");
            assertThat(init.instructions()).isEqualTo("Use these tools to operate the integration.");

            assertThat(client.listTools().tools())
                    .extracting(McpSchema.Tool::name)
                    .contains("say_hello");
            assertThat(client.callTool(new McpSchema.CallToolRequest("say_hello", Map.of())).isError())
                    .isNotEqualTo(Boolean.TRUE);
        } finally {
            if (client != null) {
                client.closeGracefully();
            }
            main.stop();
        }
    }
}
