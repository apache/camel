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

import java.util.Map;

import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.client.McpClient;
import io.modelcontextprotocol.modern.client.McpSyncClient;
import io.modelcontextprotocol.modern.client.transport.HttpClientMcpTransport;
import org.apache.camel.CamelContext;
import org.apache.camel.component.ai.tool.AiToolParameterHelper.ParameterDef;
import org.apache.camel.component.mcp.server.McpServerInfo;
import org.apache.camel.component.mcp.server.McpServerTool;
import org.apache.camel.component.mcp.server.McpToolCallHandler;
import org.apache.camel.component.mcp.server.McpToolCallResult;
import org.apache.camel.component.platform.http.main.MainHttpServer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A client of protocol version 2026-07-28 has no session (CAMEL-27145), so with the default protocol era it keeps
 * calling tools across a restart of the application, as when Camel JBang reloads it or a request reaches another
 * instance.
 */
class VertxMcpRestartTest {

    private int port;
    private CamelContext camelContext;
    private VertxMcpServerEngine engine;

    @AfterEach
    void stop() {
        stopServer();
    }

    @Test
    void modernClientKeepsCallingToolsAcrossARestart() throws Exception {
        port = AvailablePortFinder.getNextAvailable();
        startServer("first");
        McpSyncClient client = McpClient
                .sync(HttpClientMcpTransport.builder("http://localhost:" + port + "/mcp").build())
                .clientInfo(McpSchema.Implementation.builder("test", "1").build())
                .build();

        assertThat(callPing(client)).isEqualTo("pong from first");
        assertThat(engine.sessionCount()).isZero();

        // the application restarts: no session survives, and none is needed
        stopServer();
        startServer("second");

        assertThat(callPing(client)).isEqualTo("pong from second");
        assertThat(engine.sessionCount()).isZero();
    }

    private static String callPing(McpSyncClient client) {
        McpSchema.CallToolResult result = client.tool("ping").arguments(Map.of()).call();
        assertThat(result.isError()).isFalse();
        return ((McpSchema.TextContent) result.content().get(0)).text();
    }

    private void startServer(String instance) throws Exception {
        camelContext = new DefaultCamelContext();
        MainHttpServer main = new MainHttpServer();
        main.setCamelContext(camelContext);
        main.setHost("0.0.0.0");
        main.setPort(port);
        camelContext.addService(main);
        camelContext.start();

        engine = new VertxMcpServerEngine();
        engine.setCamelContext(camelContext);
        engine.initialize(new McpServerInfo("restart", "1.0", "/mcp"));
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
