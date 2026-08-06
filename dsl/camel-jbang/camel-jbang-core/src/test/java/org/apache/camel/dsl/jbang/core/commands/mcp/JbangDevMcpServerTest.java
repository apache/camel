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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.time.Duration;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.CamelContext;
import org.apache.camel.component.mcp.server.jbang.JbangDevMcpServer;
import org.apache.camel.component.platform.http.main.MainHttpServer;
import org.apache.camel.component.platform.http.main.ManagementHttpServer;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolRegistry;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JbangDevMcpServerTest {

    @Test
    void exposesToolRegistryOnManagementServer() throws Exception {
        int mainPort = AvailablePortFinder.getNextAvailable();
        int managementPort = AvailablePortFinder.getNextAvailable();

        CamelContext camelContext = new DefaultCamelContext();
        JbangDevMcpServer devMcp = new JbangDevMcpServer();
        McpSyncClient client = null;
        try {
            MainHttpServer main = new MainHttpServer();
            main.setCamelContext(camelContext);
            main.setHost("127.0.0.1");
            main.setPort(mainPort);
            camelContext.addService(main);

            ManagementHttpServer management = new ManagementHttpServer();
            management.setCamelContext(camelContext);
            management.setHost("127.0.0.1");
            management.setPort(managementPort);
            management.setPath("/");
            camelContext.addService(management);

            devMcp.setCamelContext(camelContext);
            devMcp.setPath("/mcp");
            camelContext.addService(devMcp);

            camelContext.start();

            client = McpClient.sync(
                    HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + managementPort).build())
                    .requestTimeout(Duration.ofSeconds(10))
                    .initializationTimeout(Duration.ofSeconds(10))
                    .build();
            McpSchema.InitializeResult init = client.initialize();
            assertThat(init.serverInfo().name()).isEqualTo("camel-jbang-dev-tools");

            assertThat(client.listTools().tools())
                    .extracting(McpSchema.Tool::name)
                    .contains(ToolRegistry.allTools().get(0).name());

            McpSchema.Tool parameterizedTool = client.listTools().tools().stream()
                    .filter(t -> "select_process".equals(t.name()))
                    .findFirst()
                    .orElseThrow();
            assertThat(parameterizedTool.inputSchema()).isNotNull();
            assertThat(parameterizedTool.inputSchema().toString()).contains("name").contains("required");

            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("list_processes", Map.of()));
            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(result.content().toString()).contains("processes");
        } finally {
            if (client != null) {
                client.closeGracefully();
            }
            camelContext.stop();
        }
    }
}
