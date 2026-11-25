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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.modern.McpSchema.Implementation;
import io.modelcontextprotocol.modern.client.transport.HttpClientMcpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ai.tool.AiToolParameterHelper.ParameterDef;
import org.apache.camel.component.mcp.server.McpServerBridge;
import org.apache.camel.component.mcp.server.McpServerConfiguration;
import org.apache.camel.component.mcp.server.McpServerEngine;
import org.apache.camel.component.mcp.server.McpServerRefusedException;
import org.apache.camel.component.mcp.server.McpServerTool;
import org.apache.camel.component.mcp.server.McpToolCallHandler;
import org.apache.camel.component.mcp.server.McpToolCallResult;
import org.apache.camel.component.platform.http.main.MainHttpServer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.main.Main;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * An {@code ai-tool} route whose input or output schema is a JSON object but not a valid JSON Schema 2020-12 document
 * is refused by every protocol era, whether its route starts after the MCP server or before it: the engine refuses the
 * tool before adding it to the server of any era, so it is not served to any client, its route keeps running, and the
 * other tools are served.
 */
class VertxMcpServerInvalidToolSchemaTest {

    // a JSON object, so Camel accepts it, but "strng" is not a JSON Schema type
    private static final String INVALID_INPUT_SCHEMA = """
            {"type":"object","properties":{"q":{"type":"strng"}}}""";
    private static final String INVALID_OUTPUT_SCHEMA = """
            {"type":"object","properties":{"total":{"type":"numbr"}}}""";

    @TempDir
    Path dir;

    private Path inputSchema;
    private Path outputSchema;

    @BeforeEach
    void writeSchemas() throws Exception {
        inputSchema = Files.writeString(dir.resolve("input-schema.json"), INVALID_INPUT_SCHEMA);
        outputSchema = Files.writeString(dir.resolve("output-schema.json"), INVALID_OUTPUT_SCHEMA);
    }

    @ParameterizedTest(name = "era {0}, stateless {1}")
    @CsvSource({ "legacy, false", "dual, false", "dual, true", "modern, false" })
    void routeStartedAfterTheServer(String era, boolean stateless) throws Exception {
        Main main = new Main();
        main.configure().addRoutesBuilder(routes());
        main.addInitialProperty("camel.server.enabled", "true");
        main.addInitialProperty("camel.server.port", "0");
        main.addInitialProperty("camel.server.mcp-enabled", "true");
        main.addInitialProperty("camel.server.mcp-tags", "crm");
        main.addInitialProperty("camel.server.mcp-protocol-era", era);
        main.addInitialProperty("camel.server.mcp-stateless", String.valueOf(stateless));
        main.start();
        try {
            int port = main.getCamelContext().hasService(MainHttpServer.class).getPort();
            assertServedTools(main.getCamelContext(), port, era);
        } finally {
            main.stop();
        }
    }

    @ParameterizedTest(name = "era {0}")
    @CsvSource({ "legacy", "dual", "modern" })
    void routeStartedBeforeTheServer(String era) throws Exception {
        try (DefaultCamelContext camelContext = new DefaultCamelContext()) {
            MainHttpServer server = new MainHttpServer();
            server.setCamelContext(camelContext);
            server.setHost("0.0.0.0");
            server.setPort(0);
            camelContext.addService(server);
            McpServerConfiguration configuration = new McpServerConfiguration();
            configuration.setTags("crm");
            configuration.setProtocolEra(era);
            McpServerBridge bridge = new McpServerBridge(configuration);
            camelContext.addService(bridge);
            camelContext.addRoutes(routes());

            // the MCP server publishes the tools of the routes started before it when it starts: a refused tool must
            // not fail the start of the CamelContext
            camelContext.start();

            assertThat(bridge.isStarted()).isTrue();
            assertServedTools(camelContext, server.getPort(), era);
        }
    }

    private RouteBuilder routes() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-tool:bad_input?tags=crm&description=Bad input schema&argSchema=file:" + inputSchema)
                        .routeId("bad-input-route")
                        .setBody(constant("ran"));
                from("ai-tool:bad_output?tags=crm&description=Bad output schema&outputSchema=file:" + outputSchema)
                        .routeId("bad-output-route")
                        .setBody(constant("{\"total\":1}"));
                from("ai-tool:good?tags=crm&description=Good tool")
                        .setBody(constant("ok"));
            }
        };
    }

    private static void assertServedTools(CamelContext camelContext, int port, String era) {
        // the engine refuses the tool before adding it to the server of any era
        McpServerEngine engine = camelContext.hasService(McpServerBridge.class).getEngine();
        assertThatThrownBy(() -> engine.toolAdded(tool("direct_bad", INVALID_INPUT_SCHEMA, null)))
                .isInstanceOf(McpServerRefusedException.class)
                .hasMessageContaining("Tool 'direct_bad' inputSchema is not a valid JSON Schema 2020-12 document");
        assertThatThrownBy(() -> engine.toolAdded(tool("direct_bad", null, INVALID_OUTPUT_SCHEMA)))
                .isInstanceOf(McpServerRefusedException.class)
                .hasMessageContaining("Tool 'direct_bad' outputSchema is not a valid JSON Schema 2020-12 document");

        if (!"modern".equals(era)) {
            McpSyncClient legacy = McpClient.sync(
                    HttpClientStreamableHttpTransport.builder("http://localhost:" + port).build())
                    .requestTimeout(Duration.ofSeconds(10))
                    .initializationTimeout(Duration.ofSeconds(10))
                    .build();
            try {
                legacy.initialize();
                List<String> tools = legacy.listTools().tools().stream().map(McpSchema.Tool::name).toList();
                assertThat(tools).as("tools served to a legacy client").containsExactly("good");
            } finally {
                legacy.closeGracefully();
            }
        }
        if (!"legacy".equals(era)) {
            io.modelcontextprotocol.modern.client.McpSyncClient modern = io.modelcontextprotocol.modern.client.McpClient
                    .sync(HttpClientMcpTransport.builder("http://localhost:" + port + "/mcp").build())
                    .clientInfo(Implementation.builder("test", "1").build())
                    .build();
            List<String> tools = modern.listTools().list().tools().stream()
                    .map(io.modelcontextprotocol.modern.McpSchema.Tool::name).toList();
            assertThat(tools).as("tools served to a client of protocol version 2026-07-28").containsExactly("good");
        }
        assertThat(camelContext.getRouteController().getRouteStatus("bad-input-route"))
                .isEqualTo(ServiceStatus.Started);
        assertThat(camelContext.getRouteController().getRouteStatus("bad-output-route"))
                .isEqualTo(ServiceStatus.Started);
    }

    private static McpServerTool tool(String name, String inputSchemaJson, String outputSchemaJson) {
        return new McpServerTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "Invalid schema";
            }

            @Override
            public String inputSchemaJson() {
                return inputSchemaJson;
            }

            @Override
            public Map<String, ParameterDef> parameters() {
                return Map.of();
            }

            @Override
            public McpToolCallHandler handler() {
                return arguments -> new McpToolCallResult("ran", false);
            }

            @Override
            public String outputSchemaJson() {
                return outputSchemaJson;
            }
        };
    }
}
