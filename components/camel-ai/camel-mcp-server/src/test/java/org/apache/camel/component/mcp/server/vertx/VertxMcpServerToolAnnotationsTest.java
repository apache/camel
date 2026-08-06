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
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.CamelContext;
import org.apache.camel.component.ai.tool.AiToolAnnotations;
import org.apache.camel.component.ai.tool.AiToolConfiguration;
import org.apache.camel.component.ai.tool.AiToolParameterHelper.ParameterDef;
import org.apache.camel.component.mcp.server.McpServerInfo;
import org.apache.camel.component.mcp.server.McpServerTool;
import org.apache.camel.component.mcp.server.McpToolCallHandler;
import org.apache.camel.component.mcp.server.McpToolCallResult;
import org.apache.camel.component.platform.http.main.MainHttpServer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VertxMcpServerToolAnnotationsTest {

    @Test
    void testEnginePublishesTitleOnlyHint() throws Exception {
        int port = AvailablePortFinder.getNextAvailable();
        CamelContext camelContext = new DefaultCamelContext();
        VertxMcpServerEngine engine = new VertxMcpServerEngine();
        McpSyncClient client = null;
        try {
            MainHttpServer main = new MainHttpServer();
            main.setCamelContext(camelContext);
            main.setHost("0.0.0.0");
            main.setPort(port);
            camelContext.addService(main);
            camelContext.start();

            engine.setCamelContext(camelContext);
            engine.initialize(new McpServerInfo("tool-hints", "1.0", "/mcp"));
            engine.start();

            AiToolConfiguration configuration = new AiToolConfiguration();
            configuration.setTitle("Lookup customer");
            AiToolAnnotations annotations = AiToolAnnotations.fromConfiguration(configuration);

            engine.toolAdded(tool("lookup", "Lookup a customer", annotations,
                    arguments -> new McpToolCallResult("ok", false)));

            client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port).build())
                    .requestTimeout(Duration.ofSeconds(10))
                    .initializationTimeout(Duration.ofSeconds(10))
                    .build();
            client.initialize();

            List<McpSchema.Tool> tools = client.listTools().tools();
            McpSchema.Tool lookup = tools.stream().filter(t -> "lookup".equals(t.name())).findFirst().orElseThrow();

            assertThat(lookup.title()).isEqualTo("Lookup customer");
            assertThat(lookup.annotations()).isNull();
        } finally {
            if (client != null) {
                client.closeGracefully();
            }
            engine.stop();
            camelContext.stop();
        }
    }

    @Test
    void testEnginePublishesPartialBooleanHints() throws Exception {
        int port = AvailablePortFinder.getNextAvailable();
        CamelContext camelContext = new DefaultCamelContext();
        VertxMcpServerEngine engine = new VertxMcpServerEngine();
        McpSyncClient client = null;
        try {
            MainHttpServer main = new MainHttpServer();
            main.setCamelContext(camelContext);
            main.setHost("0.0.0.0");
            main.setPort(port);
            camelContext.addService(main);
            camelContext.start();

            engine.setCamelContext(camelContext);
            engine.initialize(new McpServerInfo("tool-hints", "1.0", "/mcp"));
            engine.start();

            AiToolConfiguration configuration = new AiToolConfiguration();
            configuration.setDestructiveHint(true);
            AiToolAnnotations annotations = AiToolAnnotations.fromConfiguration(configuration);

            engine.toolAdded(tool("delete", "Delete resource", annotations,
                    arguments -> new McpToolCallResult("ok", false)));

            client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port).build())
                    .requestTimeout(Duration.ofSeconds(10))
                    .initializationTimeout(Duration.ofSeconds(10))
                    .build();
            client.initialize();

            List<McpSchema.Tool> tools = client.listTools().tools();
            McpSchema.Tool delete = tools.stream().filter(t -> "delete".equals(t.name())).findFirst().orElseThrow();

            assertThat(delete.title()).isNull();
            assertThat(delete.annotations()).isNotNull();
            assertThat(delete.annotations().destructiveHint()).isTrue();
            assertThat(delete.annotations().readOnlyHint()).isNull();
        } finally {
            if (client != null) {
                client.closeGracefully();
            }
            engine.stop();
            camelContext.stop();
        }
    }

    @Test
    void testEnginePublishesToolAnnotationHints() throws Exception {
        int port = AvailablePortFinder.getNextAvailable();
        CamelContext camelContext = new DefaultCamelContext();
        VertxMcpServerEngine engine = new VertxMcpServerEngine();
        McpSyncClient client = null;
        try {
            MainHttpServer main = new MainHttpServer();
            main.setCamelContext(camelContext);
            main.setHost("0.0.0.0");
            main.setPort(port);
            camelContext.addService(main);
            camelContext.start();

            engine.setCamelContext(camelContext);
            engine.initialize(new McpServerInfo("tool-hints", "1.0", "/mcp"));
            engine.start();

            AiToolConfiguration configuration = new AiToolConfiguration();
            configuration.setTitle("Read only tool");
            configuration.setReadOnlyHint(true);
            configuration.setDestructiveHint(false);
            configuration.setOpenWorldHint(false);
            AiToolAnnotations annotations = AiToolAnnotations.fromConfiguration(configuration);

            engine.toolAdded(tool("read_only", "Read-only lookup", annotations,
                    arguments -> new McpToolCallResult("ok", false)));

            client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port).build())
                    .requestTimeout(Duration.ofSeconds(10))
                    .initializationTimeout(Duration.ofSeconds(10))
                    .build();
            client.initialize();

            List<McpSchema.Tool> tools = client.listTools().tools();
            McpSchema.Tool readOnly = tools.stream().filter(t -> "read_only".equals(t.name())).findFirst().orElseThrow();

            assertThat(readOnly.title()).isEqualTo("Read only tool");
            assertThat(readOnly.annotations()).isNotNull();
            assertThat(readOnly.annotations().readOnlyHint()).isTrue();
            assertThat(readOnly.annotations().destructiveHint()).isFalse();
            assertThat(readOnly.annotations().openWorldHint()).isFalse();
        } finally {
            if (client != null) {
                client.closeGracefully();
            }
            engine.stop();
            camelContext.stop();
        }
    }

    private static McpServerTool tool(
            String name, String description, AiToolAnnotations annotations, McpToolCallHandler handler) {
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

            @Override
            public AiToolAnnotations annotations() {
                return annotations;
            }
        };
    }
}
