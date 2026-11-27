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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.client.McpClient;
import io.modelcontextprotocol.modern.client.McpSyncClient;
import io.modelcontextprotocol.modern.client.SubscriptionEvent;
import io.modelcontextprotocol.modern.client.transport.HttpClientMcpTransport;
import org.apache.camel.CamelContext;
import org.apache.camel.component.ai.tool.AiToolParameterHelper.ParameterDef;
import org.apache.camel.component.mcp.server.McpPromptArgument;
import org.apache.camel.component.mcp.server.McpPromptHandler;
import org.apache.camel.component.mcp.server.McpPromptMessage;
import org.apache.camel.component.mcp.server.McpPromptResult;
import org.apache.camel.component.mcp.server.McpProtocolEra;
import org.apache.camel.component.mcp.server.McpResourceReadHandler;
import org.apache.camel.component.mcp.server.McpResourceReadResult;
import org.apache.camel.component.mcp.server.McpResourceTemplateReadHandler;
import org.apache.camel.component.mcp.server.McpServerConstants;
import org.apache.camel.component.mcp.server.McpServerInfo;
import org.apache.camel.component.mcp.server.McpServerPrompt;
import org.apache.camel.component.mcp.server.McpServerResource;
import org.apache.camel.component.mcp.server.McpServerResourceTemplate;
import org.apache.camel.component.mcp.server.McpServerTool;
import org.apache.camel.component.mcp.server.McpToolCallHandler;
import org.apache.camel.component.mcp.server.McpToolCallResult;
import org.apache.camel.component.platform.http.main.MainHttpServer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Drives the MCP endpoint with the MCP Java SDK client for protocol version 2026-07-28 (CAMEL-27145): the client has no
 * handshake and no session, and finds tools, resources, resource templates and prompts as a client of a legacy version
 * does.
 */
class VertxMcpModernClientTest {

    private CamelContext camelContext;
    private VertxMcpServerEngine engine;
    private McpSyncClient client;

    @BeforeEach
    void start() throws Exception {
        int port = AvailablePortFinder.getNextAvailable();
        camelContext = new DefaultCamelContext();
        MainHttpServer main = new MainHttpServer();
        main.setCamelContext(camelContext);
        main.setHost("0.0.0.0");
        main.setPort(port);
        camelContext.addService(main);
        camelContext.start();

        engine = new VertxMcpServerEngine();
        engine.setCamelContext(camelContext);
        engine.setModernKeepAliveInterval(null);
        engine.initialize(new McpServerInfo(
                "modern-client", "1.0", "/mcp", 0, 0, "Modern client", null, null, "Ask the weather", null, null, null,
                null, null, null, McpProtocolEra.DUAL, false));
        engine.start();
        engine.toolAdded(tool("weather", arguments -> new McpToolCallResult(
                "sunny in " + arguments.get("city"), false, Map.of("sky", "sunny"))));
        engine.resourceAdded(resource("camel://readme", () -> McpResourceReadResult.text("read me")));
        engine.resourceAdded(resource("camel://logo", () -> McpResourceReadResult.blob(
                "logo".getBytes(StandardCharsets.UTF_8))));
        engine.resourceTemplateAdded(template("camel://orders/{id}",
                uri -> McpResourceReadResult.text("order " + uri.substring(uri.lastIndexOf('/') + 1))));
        engine.promptAdded(prompt("greet", arguments -> McpPromptResult.success("A greeting",
                List.of(new McpPromptMessage("user", "Hello " + arguments.get("name"))))));

        client = McpClient.sync(HttpClientMcpTransport.builder("http://localhost:" + port + "/mcp").build())
                .clientInfo(McpSchema.Implementation.builder("test", "1").build())
                .build();
    }

    @AfterEach
    void stop() {
        // the client holds no connection or session to close
        if (engine != null) {
            engine.stop();
        }
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    @Test
    void discoverNamesTheServer() {
        McpSchema.DiscoverResult discover = client.discover();

        assertThat(discover.supportedVersions()).containsExactly("2026-07-28");
        assertThat(discover.meta()).extractingByKey(McpSchema.MetaKeys.SERVER_INFO)
                .isInstanceOfSatisfying(Map.class, serverInfo -> assertThat(serverInfo)
                        .containsEntry("name", "modern-client")
                        .containsEntry("title", "Modern client"));
        assertThat(discover.instructions()).isEqualTo("Ask the weather");
        assertThat(discover.capabilities().tools()).isNotNull();
        assertThat(discover.capabilities().resources()).isNotNull();
        assertThat(discover.capabilities().prompts()).isNotNull();
        assertThat(engine.sessionCount()).isZero();
    }

    @Test
    void callsATool() {
        assertThat(client.tools().list().tools()).extracting(McpSchema.Tool::name).containsExactly("weather");

        McpSchema.CallToolResult result = client.tool("weather").arguments(Map.of("city", "Rome")).call();

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).singleElement().isInstanceOfSatisfying(McpSchema.TextContent.class,
                text -> assertThat(text.text()).isEqualTo("sunny in Rome"));
        assertThat(result.structuredContent()).isEqualTo(Map.of("sky", "sunny"));
        assertThat(engine.sessionCount()).isZero();
    }

    @Test
    void aToolResultCarriesItsMeta() {
        engine.toolAdded(tool("guarded", arguments -> new McpToolCallResult(
                "Access denied", true, null,
                Map.of(McpServerConstants.META_AUTHORIZATION, McpServerConstants.META_AUTHORIZATION_DENIED))));

        McpSchema.CallToolResult result = client.tool("guarded").arguments(Map.of("city", "Rome")).call();

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).singleElement().isInstanceOfSatisfying(McpSchema.TextContent.class,
                text -> assertThat(text.text()).isEqualTo("Access denied"));
        // next to the entry the server adds to every result of this protocol version
        assertThat(result.meta())
                .containsEntry(McpServerConstants.META_AUTHORIZATION, McpServerConstants.META_AUTHORIZATION_DENIED)
                .containsKey(McpSchema.MetaKeys.SERVER_INFO);
    }

    @Test
    void readsResourcesAndTemplates() {
        assertThat(client.resources().list().resources()).extracting(McpSchema.Resource::uri)
                .containsExactlyInAnyOrder("camel://readme", "camel://logo");
        assertThat(client.resourceTemplates().list().resourceTemplates())
                .extracting(McpSchema.ResourceTemplate::uriTemplate).containsExactly("camel://orders/{id}");

        assertThat(client.resource("camel://readme").read().contents()).singleElement()
                .isInstanceOfSatisfying(McpSchema.TextResourceContents.class,
                        text -> assertThat(text.text()).isEqualTo("read me"));
        assertThat(client.resource("camel://logo").read().contents()).singleElement()
                .isInstanceOfSatisfying(McpSchema.BlobResourceContents.class,
                        blob -> assertThat(blob.blob()).isEqualTo("bG9nbw=="));
        assertThat(client.resource("camel://orders/42").read().contents()).singleElement()
                .isInstanceOfSatisfying(McpSchema.TextResourceContents.class,
                        text -> assertThat(text.text()).isEqualTo("order 42"));
    }

    @Test
    void getsAPrompt() {
        assertThat(client.prompts().list().prompts()).extracting(McpSchema.Prompt::name).containsExactly("greet");

        McpSchema.GetPromptResult result = client.prompt("greet").arguments(Map.of("name", "Camel")).get();

        assertThat(result.description()).isEqualTo("A greeting");
        assertThat(result.messages()).singleElement().satisfies(message -> {
            assertThat(message.role()).isEqualTo(McpSchema.Role.USER);
            assertThat(message.content()).isInstanceOfSatisfying(McpSchema.TextContent.class,
                    text -> assertThat(text.text()).isEqualTo("Hello Camel"));
        });
    }

    @Test
    void listenerIsToldWhenToolsChange() {
        List<SubscriptionEvent> events = new CopyOnWriteArrayList<>();
        try (McpSyncClient.Listening listening = client.listen().toolsChanged().open(events::add)) {
            await().atMost(10, TimeUnit.SECONDS)
                    .until(() -> events.stream().anyMatch(SubscriptionEvent.Subscribed.class::isInstance));

            engine.toolRemoved("weather");

            await().atMost(10, TimeUnit.SECONDS)
                    .until(() -> events.stream().anyMatch(SubscriptionEvent.ToolsListChanged.class::isInstance));
        }
        assertThat(client.tools().list().tools()).isEmpty();
    }

    private static McpServerTool tool(String name, McpToolCallHandler handler) {
        return new McpServerTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "The weather of a city";
            }

            @Override
            public String inputSchemaJson() {
                return """
                        {"type":"object","properties":{"city":{"type":"string"}},"required":["city"]}""";
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

    private static McpServerResource resource(String uri, McpResourceReadHandler handler) {
        return new McpServerResource() {
            @Override
            public String uri() {
                return uri;
            }

            @Override
            public String name() {
                return uri.substring(uri.lastIndexOf('/') + 1);
            }

            @Override
            public String description() {
                return "A resource";
            }

            @Override
            public String mimeType() {
                return "text/plain";
            }

            @Override
            public McpResourceReadHandler handler() {
                return handler;
            }
        };
    }

    private static McpServerResourceTemplate template(String uriTemplate, McpResourceTemplateReadHandler handler) {
        return new McpServerResourceTemplate() {
            @Override
            public String uriTemplate() {
                return uriTemplate;
            }

            @Override
            public String name() {
                return "orders";
            }

            @Override
            public String description() {
                return "An order";
            }

            @Override
            public String mimeType() {
                return "text/plain";
            }

            @Override
            public McpResourceTemplateReadHandler handler() {
                return handler;
            }
        };
    }

    private static McpServerPrompt prompt(String name, McpPromptHandler handler) {
        return new McpServerPrompt() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "Greets someone";
            }

            @Override
            public String title() {
                return null;
            }

            @Override
            public List<McpPromptArgument> arguments() {
                return List.of(new McpPromptArgument("name", "Who to greet", true));
            }

            @Override
            public McpPromptHandler handler() {
                return handler;
            }
        };
    }
}
