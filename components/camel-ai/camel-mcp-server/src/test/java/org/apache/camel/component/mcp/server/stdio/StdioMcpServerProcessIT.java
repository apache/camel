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
package org.apache.camel.component.mcp.server.stdio;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end stdio MCP test: the MCP Java SDK client launches {@link McpStdioHarnessMain} as a subprocess (the same
 * shape IDE agents use) and verifies tool listing and execution.
 */
class StdioMcpServerProcessIT {

    private static final String HARNESS_TAG = "harness";

    @Test
    void testStdioMcpServerListsAndCallsTaggedTools() {
        McpSyncClient client = null;
        try {
            client = createClient();
            client.initialize();

            List<McpSchema.Tool> tools = client.listTools().tools();
            assertThat(tools).extracting(McpSchema.Tool::name)
                    .contains("say_hello")
                    .doesNotContain("hidden_tool");

            McpSchema.CallToolResult result
                    = client.callTool(new McpSchema.CallToolRequest("say_hello", Map.of("name", "Camel")));
            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(textOf(result)).isEqualTo("Hello Camel");
        } finally {
            if (client != null) {
                client.closeGracefully();
            }
        }
    }

    @Test
    void testStdioMcpServerSanitizesExecutionErrors() {
        McpSyncClient client = null;
        try {
            client = createClient();
            client.initialize();

            // harness does not expose fail_tool; add a quick inline check via missing required arg
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest("say_hello", Map.of()));
            assertThat(result.isError()).isEqualTo(Boolean.TRUE);
            assertThat(textOf(result)).contains("name");
        } finally {
            if (client != null) {
                client.closeGracefully();
            }
        }
    }

    private static McpSyncClient createClient() {
        List<String> command = harnessCommand();
        ServerParameters parameters = ServerParameters.builder(command.get(0))
                .args(command.subList(1, command.size()))
                .build();
        return McpClient.sync(new StdioClientTransport(parameters, McpJsonDefaults.getMapper()))
                .requestTimeout(Duration.ofSeconds(20))
                .initializationTimeout(Duration.ofSeconds(20))
                .build();
    }

    private static List<String> harnessCommand() {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + "/bin/java";
        String classpath = System.getProperty("java.class.path");
        return List.of(
                javaBin,
                "-Dlog4j2.configurationFile=classpath:log4j2-mcp-stdio.properties",
                "-cp",
                classpath,
                McpStdioHarnessMain.class.getName(),
                HARNESS_TAG);
    }

    private static String textOf(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(c -> ((McpSchema.TextContent) c).text())
                .collect(Collectors.joining());
    }
}
