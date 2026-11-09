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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mcp.server.McpServerBridge;
import org.apache.camel.component.mcp.server.McpServerConfiguration;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Engine conformance kit for MCP prompts, verified with the official MCP Java SDK client over streamable HTTP.
 */
public abstract class McpServerPromptConformanceTestSupport extends CamelTestSupport {

    public static final String CONFORMANCE_TAG = "prompt-conformance";
    private static final long PROMPT_TIMEOUT_MILLIS = 2000;

    protected McpServerBridge bridge;
    private McpSyncClient client;
    private final CountDownLatch promptsChanged = new CountDownLatch(1);
    private final AtomicReference<List<McpSchema.Prompt>> changedPromptList = new AtomicReference<>();

    protected abstract String mcpServerBaseUrl();

    protected void customizeCamelContext(CamelContext camelContext) throws Exception {
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        customizeCamelContext(camelContext);
        McpServerConfiguration configuration = new McpServerConfiguration();
        configuration.setTags(CONFORMANCE_TAG);
        configuration.setPromptTimeout(PROMPT_TIMEOUT_MILLIS);
        bridge = new McpServerBridge(configuration);
        camelContext.addService(bridge);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-prompt:greeting?tags=" + CONFORMANCE_TAG
                     + "&description=Say hello&title=Greeting"
                     + "&argument.name.description=Who to greet&argument.name.required=true")
                        .routeId("greeting-prompt-route")
                        .setBody(simple("Hello ${header.name}"));

                from("ai-prompt:map_prompt?tags=" + CONFORMANCE_TAG + "&description=Return a content block")
                        .routeId("map-prompt-route")
                        .setBody(constant(List.of(Map.of(
                                "role", "assistant", "content", Map.of("type", "text", "text", "Map content block")))));

                from("ai-prompt:hidden_prompt?description=Untagged prompt")
                        .setBody(constant("hidden"));

                from("ai-prompt:other_prompt?tags=untrusted&description=Unselected prompt")
                        .setBody(constant("other"));
            }
        };
    }

    protected McpSyncClient client() {
        if (client == null) {
            client = McpClient.sync(HttpClientStreamableHttpTransport.builder(mcpServerBaseUrl()).build())
                    .requestTimeout(Duration.ofSeconds(10))
                    .initializationTimeout(Duration.ofSeconds(10))
                    .promptsChangeConsumer(prompts -> {
                        changedPromptList.set(prompts);
                        promptsChanged.countDown();
                    })
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
    void testListPromptsIncludesTitleAndArguments() {
        List<McpSchema.Prompt> prompts = client().listPrompts().prompts();

        assertThat(prompts).extracting(McpSchema.Prompt::name)
                .contains("greeting", "map_prompt")
                .doesNotContain("hidden_prompt", "other_prompt");

        McpSchema.Prompt greeting = prompts.stream()
                .filter(prompt -> "greeting".equals(prompt.name()))
                .findFirst().orElseThrow();
        assertThat(greeting.title()).isEqualTo("Greeting");
        assertThat(greeting.description()).isEqualTo("Say hello");
        assertThat(greeting.arguments()).hasSize(1);
        assertThat(greeting.arguments().get(0).name()).isEqualTo("name");
        assertThat(greeting.arguments().get(0).required()).isTrue();
    }

    @Test
    void testGetPromptWithArguments() {
        McpSchema.GetPromptResult result = client().getPrompt(
                new McpSchema.GetPromptRequest("greeting", Map.of("name", "World")));

        assertThat(result.messages()).hasSize(1);
        assertThat(result.messages().get(0).role()).isEqualTo(McpSchema.Role.USER);
        assertThat(result.messages().get(0).content()).isInstanceOf(McpSchema.TextContent.class);
        assertThat(((McpSchema.TextContent) result.messages().get(0).content()).text()).isEqualTo("Hello World");
    }

    @Test
    void testMissingRequiredPromptArgumentReturnsInvalidParams() {
        assertInvalidPromptArguments(new McpSchema.GetPromptRequest("greeting", Map.of()));
    }

    @Test
    void testNonStringPromptArgumentReturnsInvalidParams() {
        assertInvalidPromptArguments(new McpSchema.GetPromptRequest(
                "greeting", Map.<String, Object> of("name", 123)));
    }

    @Test
    void testMapContentBlockIsConvertedBySdkAdapter() {
        McpSchema.GetPromptResult result = client().getPrompt(
                new McpSchema.GetPromptRequest("map_prompt", Map.of()));

        assertThat(result.messages()).hasSize(1);
        assertThat(result.messages().get(0).role()).isEqualTo(McpSchema.Role.ASSISTANT);
        assertThat(result.messages().get(0).content()).isInstanceOf(McpSchema.TextContent.class);
        assertThat(((McpSchema.TextContent) result.messages().get(0).content()).text()).isEqualTo("Map content block");
    }

    @Test
    void testPromptsListChangedWhenRouteStops() throws Exception {
        client();
        context.getRouteController().stopRoute("greeting-prompt-route");

        assertThat(promptsChanged.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(changedPromptList.get()).extracting(McpSchema.Prompt::name).doesNotContain("greeting");
    }

    private void assertInvalidPromptArguments(McpSchema.GetPromptRequest request) {
        assertThatThrownBy(() -> client().getPrompt(request))
                .isInstanceOf(McpError.class)
                .satisfies(error -> assertThat(((McpError) error).getJsonRpcError().code())
                        .isEqualTo(McpSchema.ErrorCodes.INVALID_PARAMS));
    }
}
