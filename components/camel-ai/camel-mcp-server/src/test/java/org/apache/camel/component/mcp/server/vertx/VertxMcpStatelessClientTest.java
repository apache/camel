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

import java.time.Duration;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.CamelContext;
import org.apache.camel.component.ai.tool.AiToolParameterHelper.ParameterDef;
import org.apache.camel.component.mcp.server.McpProtocolEra;
import org.apache.camel.component.mcp.server.McpServerInfo;
import org.apache.camel.component.mcp.server.McpServerTool;
import org.apache.camel.component.mcp.server.McpToolCallHandler;
import org.apache.camel.component.mcp.server.McpToolCallResult;
import org.apache.camel.component.platform.http.main.MainHttpServer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the MCP endpoint with the MCP Java SDK client of the protocol versions that open with {@code initialize} when
 * the stateless option is on (CAMEL-27145): the client gets no session, and keeps calling tools across a restart of the
 * application, as when Camel JBang reloads it or a request reaches another instance.
 */
class VertxMcpStatelessClientTest {

    private int port;
    private CamelContext camelContext;
    private VertxMcpServerEngine engine;
    private McpSyncClient client;

    @AfterEach
    void stop() {
        if (client != null) {
            client.closeGracefully();
        }
        stopServer();
    }

    @ParameterizedTest
    @EnumSource(value = McpProtocolEra.class, names = { "LEGACY", "DUAL" })
    void clientKeepsCallingToolsAcrossARestart(McpProtocolEra era) throws Exception {
        port = AvailablePortFinder.getNextAvailable();
        startServer(era, true, "first");

        client = newClient();
        client.initialize();
        assertThat(callPing()).isEqualTo("pong from first");
        assertThat(engine.sessionCount()).isZero();

        // the application restarts: no session survives, and none is needed
        stopServer();
        startServer(era, true, "second");

        assertThat(callPing()).isEqualTo("pong from second");
        assertThat(client.listTools().tools()).extracting(McpSchema.Tool::name).containsExactly("ping");
    }

    @Test
    void withoutTheStatelessOptionTheClientGetsASession() throws Exception {
        port = AvailablePortFinder.getNextAvailable();
        startServer(McpProtocolEra.DUAL, false, "only");

        client = newClient();
        client.initialize();

        assertThat(callPing()).isEqualTo("pong from only");
        assertThat(engine.sessionCount()).isEqualTo(1);
    }

    private McpSyncClient newClient() {
        return McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port).build())
                .requestTimeout(Duration.ofSeconds(10))
                .initializationTimeout(Duration.ofSeconds(10))
                .build();
    }

    private String callPing() {
        McpSchema.CallToolResult result = client.callTool(
                McpSchema.CallToolRequest.builder("ping").arguments(Map.of()).build());
        assertThat(result.isError()).isFalse();
        return ((McpSchema.TextContent) result.content().get(0)).text();
    }

    private void startServer(McpProtocolEra era, boolean stateless, String instance) throws Exception {
        camelContext = new DefaultCamelContext();
        MainHttpServer main = new MainHttpServer();
        main.setCamelContext(camelContext);
        main.setHost("0.0.0.0");
        main.setPort(port);
        camelContext.addService(main);
        camelContext.start();

        engine = new VertxMcpServerEngine();
        engine.setCamelContext(camelContext);
        engine.initialize(new McpServerInfo(
                "stateless", "1.0", "/mcp", 0, 0, null, null, null, null, null, null, null, null, null, null,
                era, stateless));
        engine.start();
        engine.toolAdded(tool(arguments -> new McpToolCallResult("pong from " + instance, false)));
    }

    private void stopServer() {
        if (engine != null) {
            engine.stop();
            engine = null;
        }
        if (camelContext != null) {
            camelContext.stop();
            camelContext = null;
        }
    }

    private static McpServerTool tool(McpToolCallHandler handler) {
        return new McpServerTool() {
            @Override
            public String name() {
                return "ping";
            }

            @Override
            public String description() {
                return "Answers pong";
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
