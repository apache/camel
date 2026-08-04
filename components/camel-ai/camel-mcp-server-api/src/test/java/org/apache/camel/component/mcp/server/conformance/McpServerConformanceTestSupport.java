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
package org.apache.camel.component.mcp.server.conformance;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mcp.server.McpServerBridge;
import org.apache.camel.component.mcp.server.McpServerConfiguration;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Engine conformance kit: the behavioural contract every {@link org.apache.camel.component.mcp.server.McpServerEngine}
 * implementation must satisfy, verified with the official MCP Java SDK client over streamable HTTP.
 * <p>
 * Engine modules extend this class (it is shipped in the camel-mcp-server-api test-jar), install their serving
 * infrastructure in {@link #customizeCamelContext(CamelContext)} and point {@link #mcpServerBaseUrl()} at the running
 * server. The kit owns the ai-tool routes and the {@link McpServerBridge} so tool semantics cannot drift between
 * engines.
 */
public abstract class McpServerConformanceTestSupport extends CamelTestSupport {

    public static final String CONFORMANCE_TAG = "conformance";
    public static final long TOOL_TIMEOUT_MILLIS = 2000;

    protected McpServerBridge bridge;
    private McpSyncClient client;

    /**
     * Base URL of the server under test, without the MCP endpoint path (the SDK client appends {@code /mcp}).
     */
    protected abstract String mcpServerBaseUrl();

    /**
     * Installs the serving infrastructure the engine under test needs (e.g. an HTTP server service). Called before the
     * bridge is added to the context.
     */
    protected void customizeCamelContext(CamelContext camelContext) throws Exception {
    }

    /**
     * Adjusts the bridge configuration; tags and tool timeout are preset by the kit.
     */
    protected void configureBridge(McpServerConfiguration configuration) {
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        customizeCamelContext(camelContext);
        McpServerConfiguration configuration = new McpServerConfiguration();
        configuration.setTags(CONFORMANCE_TAG);
        configuration.setToolTimeout(TOOL_TIMEOUT_MILLIS);
        configureBridge(configuration);
        bridge = new McpServerBridge(configuration);
        camelContext.addService(bridge);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("ai-tool:say_hello?tags=" + CONFORMANCE_TAG + "&description=Say hello"
                     + "&parameter.name=string&parameter.name.description=Who to greet&parameter.name.required=true")
                        .routeId("say-hello-route")
                        .setBody(simple("Hello ${header.name}"));

                from("ai-tool:fail_tool?tags=" + CONFORMANCE_TAG + "&description=Always fails")
                        .routeId("fail-tool-route")
                        .process(e -> {
                            throw new IllegalStateException("secret internal detail");
                        });

                from("ai-tool:slow_tool?tags=" + CONFORMANCE_TAG + "&description=Exceeds the tool timeout")
                        .routeId("slow-tool-route")
                        .delay(TOOL_TIMEOUT_MILLIS * 3)
                        .setBody(constant("done"));

                from("ai-tool:hidden_tool?description=Untagged tool, must not be exposed")
                        .setBody(constant("hidden"));

                from("ai-tool:other_tool?tags=untrusted&description=Not a selected tag, must not be exposed")
                        .setBody(constant("other"));
            }
        };
    }

    protected McpSyncClient client() {
        if (client == null) {
            client = McpClient.sync(HttpClientStreamableHttpTransport.builder(mcpServerBaseUrl()).build())
                    .requestTimeout(Duration.ofSeconds(10))
                    .initializationTimeout(Duration.ofSeconds(10))
                    .build();
            client.initialize();
        }
        return client;
    }

    @AfterEach
    void closeClient() {
        if (client != null) {
            client.closeGracefully();
            client = null;
        }
    }

    @Test
    void testListToolsExposesOnlySelectedTags() {
        List<McpSchema.Tool> tools = client().listTools().tools();

        assertThat(tools).extracting(McpSchema.Tool::name)
                .contains("say_hello", "fail_tool", "slow_tool")
                .doesNotContain("hidden_tool", "other_tool");

        McpSchema.Tool sayHello = tools.stream().filter(t -> "say_hello".equals(t.name())).findFirst().orElseThrow();
        assertThat(sayHello.description()).isEqualTo("Say hello");
        assertThat(sayHello.inputSchema()).containsKey("properties");
        assertThat(sayHello.inputSchema().toString()).contains("name");
    }

    @Test
    void testCallToolSuccess() {
        McpSchema.CallToolResult result
                = client().callTool(new McpSchema.CallToolRequest("say_hello", Map.of("name", "World")));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(textOf(result)).isEqualTo("Hello World");
    }

    @Test
    void testCallToolMissingRequiredArgument() {
        McpSchema.CallToolResult result = client().callTool(new McpSchema.CallToolRequest("say_hello", Map.of()));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(textOf(result)).contains("name");
    }

    @Test
    void testCallToolExecutionErrorIsSanitized() {
        McpSchema.CallToolResult result = client().callTool(new McpSchema.CallToolRequest("fail_tool", Map.of()));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(textOf(result))
                .doesNotContain("secret internal detail")
                .isEqualTo("Tool execution failed");
    }

    @Test
    void testCallToolTimeout() {
        McpSchema.CallToolResult result = client().callTool(new McpSchema.CallToolRequest("slow_tool", Map.of()));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(textOf(result)).contains("timed out");
    }

    @Test
    void testToolsListReflectsRouteStopAndStart() throws Exception {
        assertThat(client().listTools().tools()).extracting(McpSchema.Tool::name).contains("say_hello");

        context.getRouteController().stopRoute("say-hello-route");
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client().listTools().tools())
                .extracting(McpSchema.Tool::name).doesNotContain("say_hello"));

        context.getRouteController().startRoute("say-hello-route");
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client().listTools().tools())
                .extracting(McpSchema.Tool::name).contains("say_hello"));
    }

    protected static String textOf(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(c -> ((McpSchema.TextContent) c).text())
                .collect(Collectors.joining());
    }
}
