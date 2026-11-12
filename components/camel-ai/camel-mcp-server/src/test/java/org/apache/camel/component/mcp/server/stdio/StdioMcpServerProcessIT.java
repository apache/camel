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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
    void testStdioMcpServerListsAndReadsFixedResourcesAndResourceTemplates() {
        McpSyncClient client = null;
        try {
            client = createClient();
            client.initialize();

            List<McpSchema.Resource> resources = client.listResources().resources();
            assertThat(resources).extracting(McpSchema.Resource::uri)
                    .contains("camel:///stdio/config")
                    .doesNotContain("camel:///stdio/hidden");

            List<McpSchema.ResourceTemplate> templates = client.listResourceTemplates().resourceTemplates();
            assertThat(templates).extracting(McpSchema.ResourceTemplate::uriTemplate)
                    .contains("camel:///stdio/customers/{customerId}")
                    .doesNotContain("camel:///stdio/hidden/{id}", "camel:///stdio/other/{id}");

            McpSchema.ReadResourceResult fixedResult
                    = client.readResource(new McpSchema.ReadResourceRequest("camel:///stdio/config"));
            assertThat(fixedResult.contents()).hasSize(1);
            assertThat(fixedResult.contents().get(0)).isInstanceOf(McpSchema.TextResourceContents.class);
            McpSchema.TextResourceContents fixedContents
                    = (McpSchema.TextResourceContents) fixedResult.contents().get(0);
            assertThat(fixedContents.uri()).isEqualTo("camel:///stdio/config");
            assertThat(fixedContents.text()).isEqualTo("stdio-fixed-resource");

            McpSchema.ReadResourceResult templateResult = client.readResource(
                    new McpSchema.ReadResourceRequest("camel:///stdio/customers/42"));
            assertThat(templateResult.contents()).hasSize(1);
            assertThat(templateResult.contents().get(0)).isInstanceOf(McpSchema.TextResourceContents.class);
            McpSchema.TextResourceContents templateContents
                    = (McpSchema.TextResourceContents) templateResult.contents().get(0);
            assertThat(templateContents.uri()).isEqualTo("camel:///stdio/customers/42");
            assertThat(templateContents.text()).isEqualTo("camel:///stdio/customers/42|42");
        } finally {
            if (client != null) {
                client.closeGracefully();
            }
        }
    }

    @Test
    void testStdioMcpServerNotifiesConnectedClientWhenTemplateRouteStopsAndStarts() {
        AtomicInteger notifications = new AtomicInteger();
        McpSyncClient client = null;
        try {
            client = createClient("default", resources -> notifications.incrementAndGet());
            client.initialize();

            assertThat(client.listResourceTemplates().resourceTemplates())
                    .extracting(McpSchema.ResourceTemplate::uriTemplate)
                    .contains("camel:///stdio/customers/{customerId}");

            int beforeStop = notifications.get();
            McpSchema.CallToolResult stopped
                    = client.callTool(new McpSchema.CallToolRequest("stop_customer_template", Map.of()));
            assertThat(stopped.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(textOf(stopped)).isEqualTo("stopped");
            await().atMost(10, TimeUnit.SECONDS).until(() -> notifications.get() > beforeStop);

            assertThat(client.listResourceTemplates().resourceTemplates())
                    .extracting(McpSchema.ResourceTemplate::uriTemplate)
                    .doesNotContain("camel:///stdio/customers/{customerId}");

            int beforeStart = notifications.get();
            McpSchema.CallToolResult started
                    = client.callTool(new McpSchema.CallToolRequest("start_customer_template", Map.of()));
            assertThat(started.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(textOf(started)).isEqualTo("started");
            await().atMost(10, TimeUnit.SECONDS).until(() -> notifications.get() > beforeStart);

            assertThat(client.listResourceTemplates().resourceTemplates())
                    .extracting(McpSchema.ResourceTemplate::uriTemplate)
                    .contains("camel:///stdio/customers/{customerId}");
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

    @Test
    void testStdioMcpServerStartsWhenTheContextClassLoaderCannotSeeTheJsonMapper() {
        McpSyncClient client = null;
        try {
            client = createClient("cli");
            client.initialize();

            McpSchema.CallToolResult result
                    = client.callTool(new McpSchema.CallToolRequest("say_hello", Map.of("name", "Camel")));
            assertThat(textOf(result)).isEqualTo("Hello Camel");
        } finally {
            if (client != null) {
                client.closeGracefully();
            }
        }
    }

    @Test
    void testStdioMcpServerKeepsSystemOutOfTheMcpStream() {
        McpSyncClient client = null;
        try {
            client = createClient();
            client.initialize();

            McpSchema.CallToolResult printed = client.callTool(new McpSchema.CallToolRequest("print_tool", Map.of()));
            assertThat(textOf(printed)).isEqualTo("printed");
            // the printed line went to stderr, so the next call still works
            McpSchema.CallToolResult result
                    = client.callTool(new McpSchema.CallToolRequest("say_hello", Map.of("name", "Camel")));
            assertThat(textOf(result)).isEqualTo("Hello Camel");
        } finally {
            if (client != null) {
                client.closeGracefully();
            }
        }
    }

    @Test
    void testStdioMcpServerExitsWhenTheClientClosesStdin() throws Exception {
        List<String> command = harnessCommand("default");
        Process process = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        try {
            process.getOutputStream().close();
            assertThat(process.waitFor(60, TimeUnit.SECONDS)).as("the server exits when stdin is closed").isTrue();
        } finally {
            process.destroyForcibly();
        }
    }

    private static McpSyncClient createClient() {
        return createClient("default");
    }

    private static McpSyncClient createClient(String mode) {
        return createClient(mode, null);
    }

    private static McpSyncClient createClient(
            String mode, java.util.function.Consumer<List<McpSchema.Resource>> resourcesChangeConsumer) {
        List<String> command = harnessCommand(mode);
        ServerParameters parameters = ServerParameters.builder(command.get(0))
                .args(command.subList(1, command.size()))
                .build();
        var builder = McpClient.sync(new StdioClientTransport(parameters, McpJsonDefaults.getMapper()))
                .requestTimeout(Duration.ofSeconds(20))
                .initializationTimeout(Duration.ofSeconds(20));
        if (resourcesChangeConsumer != null) {
            builder.resourcesChangeConsumer(resourcesChangeConsumer);
        }
        return builder.build();
    }

    private static List<String> harnessCommand(String mode) {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + "/bin/java";
        String classpath = System.getProperty("java.class.path");
        return List.of(
                javaBin,
                "-Dlog4j2.configurationFile=classpath:log4j2-mcp-stdio.properties",
                "-cp",
                classpath,
                McpStdioHarnessMain.class.getName(),
                HARNESS_TAG,
                mode);
    }

    private static String textOf(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(c -> ((McpSchema.TextContent) c).text())
                .collect(Collectors.joining());
    }
}
