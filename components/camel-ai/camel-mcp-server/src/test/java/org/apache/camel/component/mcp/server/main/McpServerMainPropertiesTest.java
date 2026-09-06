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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mcp.server.stdio.StdioMcpServerEngine;
import org.apache.camel.main.Main;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the camel-main autowiring (CAMEL-24311): the MCP server starts from {@code camel.server.mcp-*} properties
 * alone — no code and no route for the server itself.
 */
class McpServerMainPropertiesTest {

    @Test
    void testMcpServerStartsFromPropertiesAlone() throws Exception {
        int port = AvailablePortFinder.getNextAvailable();

        Main main = new Main();
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-tool:say_hello?tags=crm&description=Say hello"
                     + "&parameter.name=string&parameter.name.required=true")
                        .setBody(simple("Hello ${header.name}"));

                from("ai-tool:hidden_tool?description=Untagged, not exposed")
                        .setBody(constant("hidden"));
            }
        });
        main.addInitialProperty("camel.server.enabled", "true");
        main.addInitialProperty("camel.server.port", String.valueOf(port));
        main.addInitialProperty("camel.server.mcp-enabled", "true");
        main.addInitialProperty("camel.server.mcp-tags", "crm");
        main.addInitialProperty("camel.server.mcp-server-name", "my-integration-app");
        main.start();

        McpSyncClient client = null;
        try {
            client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port).build())
                    .requestTimeout(Duration.ofSeconds(10))
                    .initializationTimeout(Duration.ofSeconds(10))
                    .build();

            McpSchema.InitializeResult init = client.initialize();
            assertThat(init.serverInfo().name()).isEqualTo("my-integration-app");

            assertThat(client.listTools().tools())
                    .extracting(McpSchema.Tool::name)
                    .contains("say_hello")
                    .doesNotContain("hidden_tool");

            McpSchema.CallToolResult result
                    = client.callTool(new McpSchema.CallToolRequest("say_hello", Map.of("name", "Camel")));
            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(result.content().toString()).contains("Hello Camel");
        } finally {
            if (client != null) {
                client.closeGracefully();
            }
            main.stop();
        }
    }

    @Test
    void testMcpEnabledWithoutHttpServerFailsFastForHttpTransport() {
        Main main = new Main();
        main.addInitialProperty("camel.server.mcp-enabled", "true");
        main.addInitialProperty("camel.server.mcp-transport", "http");
        main.addInitialProperty("camel.server.mcp-tags", "crm");

        try {
            assertThatThrownBy(main::start)
                    .hasStackTraceContaining("Vert.x platform HTTP server");
        } finally {
            main.stop();
        }
    }

    @Test
    void testMcpStdioStartsWithoutHttpServer() throws Exception {
        Main main = new Main();
        main.configure().setStartupSummaryLevel(org.apache.camel.StartupSummaryLevel.Off);
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-tool:say_hello?tags=crm&description=Say hello"
                     + "&parameter.name=string&parameter.name.required=true")
                        .setBody(simple("Hello ${header.name}"));
            }
        });
        main.addInitialProperty("camel.server.mcp-enabled", "true");
        main.addInitialProperty("camel.server.mcp-transport", "stdio");
        main.addInitialProperty("camel.server.mcp-tags", "crm");
        StdioMcpServerEngine engine = new StdioMcpServerEngine();
        engine.setTransportStreams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        main.bind("mcpServerEngine", engine);

        try {
            main.start();
            org.apache.camel.component.mcp.server.McpServerBridge bridge
                    = main.getCamelContext().hasService(org.apache.camel.component.mcp.server.McpServerBridge.class);
            assertThat(bridge).isNotNull();
            assertThat(bridge.getEngine()).isInstanceOf(
                    org.apache.camel.component.mcp.server.stdio.StdioMcpServerEngine.class);
        } finally {
            main.stop();
        }
    }
}
