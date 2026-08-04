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
package org.apache.camel.component.mcp.server.vertx;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.CamelContext;
import org.apache.camel.component.ai.tool.AiToolParameterHelper.ParameterDef;
import org.apache.camel.component.mcp.server.McpServerInfo;
import org.apache.camel.component.mcp.server.McpServerTool;
import org.apache.camel.component.mcp.server.McpToolCallHandler;
import org.apache.camel.component.mcp.server.McpToolCallResult;
import org.apache.camel.component.platform.http.main.MainHttpServer;
import org.apache.camel.component.platform.http.main.ManagementHttpServer;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpRouter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the selectable target server type (CAMEL-24353): the engine can serve the MCP endpoint on the management
 * HTTP server router instead of the main one, and it is driven directly with hand-built tools — no bridge and no
 * ai-tool routes — the way an alternative tool source such as the JBang dev tools (CAMEL-23853) uses it.
 */
class VertxMcpServerEngineTargetServerTypeTest {

    @Test
    void testEngineServesOnManagementServerOnly() throws Exception {
        int mainPort = AvailablePortFinder.getNextAvailable();
        int managementPort = AvailablePortFinder.getNextAvailable();

        CamelContext camelContext = new DefaultCamelContext();
        ManagementHttpServer management = new ManagementHttpServer();
        VertxMcpServerEngine engine = new VertxMcpServerEngine();
        McpSyncClient client = null;
        try {
            MainHttpServer main = new MainHttpServer();
            main.setCamelContext(camelContext);
            main.setHost("0.0.0.0");
            main.setPort(mainPort);
            camelContext.addService(main);
            camelContext.start();

            management.setCamelContext(camelContext);
            management.setHost("0.0.0.0");
            management.setPort(managementPort);
            management.setPath("/");
            management.start();

            engine.setCamelContext(camelContext);
            engine.setTargetServerType(VertxPlatformHttpRouter.SERVER_TYPE_MANAGEMENT);
            engine.initialize(new McpServerInfo("dev-tools", "1.0", "/mcp"));
            engine.start();
            engine.toolAdded(tool("current_pid", "The pid of this process",
                    arguments -> new McpToolCallResult("pid-42", false)));

            client = McpClient.sync(
                    HttpClientStreamableHttpTransport.builder("http://localhost:" + managementPort).build())
                    .requestTimeout(Duration.ofSeconds(10))
                    .initializationTimeout(Duration.ofSeconds(10))
                    .build();
            McpSchema.InitializeResult init = client.initialize();
            assertThat(init.serverInfo().name()).isEqualTo("dev-tools");

            assertThat(client.listTools().tools())
                    .extracting(McpSchema.Tool::name)
                    .contains("current_pid");

            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest("current_pid", Map.of()));
            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(result.content().toString()).contains("pid-42");

            // the main server must not serve the management-targeted MCP endpoint
            HttpResponse<String> onMainServer = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + mainPort + "/mcp"))
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json, text/event-stream")
                            .POST(HttpRequest.BodyPublishers.ofString("{}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(onMainServer.statusCode()).isEqualTo(404);
        } finally {
            if (client != null) {
                client.closeGracefully();
            }
            engine.stop();
            management.stop();
            camelContext.stop();
        }
    }

    @Test
    void testManagementTargetWithoutManagementServerFailsFast() throws Exception {
        int mainPort = AvailablePortFinder.getNextAvailable();

        CamelContext camelContext = new DefaultCamelContext();
        VertxMcpServerEngine engine = new VertxMcpServerEngine();
        try {
            MainHttpServer main = new MainHttpServer();
            main.setCamelContext(camelContext);
            main.setHost("0.0.0.0");
            main.setPort(mainPort);
            camelContext.addService(main);
            camelContext.start();

            engine.setCamelContext(camelContext);
            engine.setTargetServerType(VertxPlatformHttpRouter.SERVER_TYPE_MANAGEMENT);
            engine.initialize(new McpServerInfo("dev-tools", "1.0", "/mcp"));

            // the main server router is present but an explicit management target must never fall back to it
            assertThatThrownBy(engine::start)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("management");
        } finally {
            engine.stop();
            camelContext.stop();
        }
    }

    private static McpServerTool tool(String name, String description, McpToolCallHandler handler) {
        return new McpServerTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public String inputSchemaJson() {
                return null;
            }

            @Override
            public Map<String, ParameterDef> parameters() {
                return Map.of();
            }

            @Override
            public McpToolCallHandler handler() {
                return handler;
            }
        };
    }
}
