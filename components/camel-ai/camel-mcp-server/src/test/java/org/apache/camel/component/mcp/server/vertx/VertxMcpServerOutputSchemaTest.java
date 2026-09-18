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
import org.apache.camel.component.mcp.server.McpServerInfo;
import org.apache.camel.component.mcp.server.McpServerTool;
import org.apache.camel.component.mcp.server.McpToolCallHandler;
import org.apache.camel.component.mcp.server.McpToolCallResult;
import org.apache.camel.component.platform.http.main.MainHttpServer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VertxMcpServerOutputSchemaTest {

    private static final String OUTPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "temperature": { "type": "number" },
                "unit": { "type": "string" }
              },
              "required": ["temperature", "unit"]
            }
            """;

    @Test
    void testEnginePublishesOutputSchemaAndStructuredContent() throws Exception {
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
            engine.initialize(new McpServerInfo("output-schema", "1.0", "/mcp"));
            engine.start();

            JsonObject structured = new JsonObject();
            structured.put("temperature", 21.5);
            structured.put("unit", "celsius");
            engine.toolAdded(tool("getWeather", "Get weather", OUTPUT_SCHEMA,
                    arguments -> new McpToolCallResult(structured.toJson(), false, structured)));

            client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port).build())
                    .requestTimeout(Duration.ofSeconds(10))
                    .initializationTimeout(Duration.ofSeconds(10))
                    .build();
            client.initialize();

            List<McpSchema.Tool> tools = client.listTools().tools();
            McpSchema.Tool weather = tools.stream().filter(t -> "getWeather".equals(t.name())).findFirst().orElseThrow();

            assertThat(weather.outputSchema()).isNotNull();
            assertThat(weather.outputSchema()).containsKey("properties");

            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest("getWeather", Map.of()));

            assertThat(result.isError()).isFalse();
            assertThat(result.structuredContent()).isNotNull();
            assertThat(result.structuredContent()).isInstanceOf(Map.class);
            assertThat(((Map<?, ?>) result.structuredContent()).get("temperature")).isEqualTo(21.5);
        } finally {
            if (client != null) {
                client.closeGracefully();
            }
            engine.stop();
            camelContext.stop();
        }
    }

    private static McpServerTool tool(
            String name, String description, String outputSchemaJson, McpToolCallHandler handler) {
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
            public Map<String, org.apache.camel.component.ai.tool.AiToolParameterHelper.ParameterDef> parameters() {
                return Map.of();
            }

            @Override
            public McpToolCallHandler handler() {
                return handler;
            }

            @Override
            public String outputSchemaJson() {
                return outputSchemaJson;
            }
        };
    }
}
