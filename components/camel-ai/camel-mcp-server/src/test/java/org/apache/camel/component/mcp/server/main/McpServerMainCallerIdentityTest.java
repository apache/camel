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
package org.apache.camel.component.mcp.server.main;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.modern.client.transport.HttpClientMcpTransport;
import io.modelcontextprotocol.modern.client.transport.McpHttpRequestCustomizer;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mcp.server.McpToolCallContext;
import org.apache.camel.component.platform.http.main.MainHttpServer;
import org.apache.camel.main.Main;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * An authenticated MCP caller reaches {@code ai-resource} (fixed and templated) and {@code ai-prompt} routes as it
 * reaches {@code ai-tool} routes (CAMEL-26360): the caller identity is stamped as exchange properties on a request of
 * each protocol era, with and without sessions, and an anonymous request stamps nothing.
 */
class McpServerMainCallerIdentityTest {

    private static final String CALLER = "name=camel,principal=true";
    private static final String NO_CALLER = "name=null,principal=false";
    private static final String CREDENTIALS = Base64.getEncoder().encodeToString("camel:mcpPass".getBytes(UTF_8));

    @Test
    void legacySessionAndModernRequestsCarryTheCaller() {
        Main main = startMain(true, false);
        try {
            assertLegacyClientSees(port(main), true, CALLER);
            assertModernClientSees(port(main), true, CALLER);
        } finally {
            main.stop();
        }
    }

    @Test
    void statelessLegacyRequestsCarryTheCaller() {
        Main main = startMain(true, true);
        try {
            assertLegacyClientSees(port(main), true, CALLER);
        } finally {
            main.stop();
        }
    }

    @Test
    void anonymousRequestsCarryNoCaller() {
        Main main = startMain(false, false);
        try {
            assertLegacyClientSees(port(main), false, NO_CALLER);
            assertModernClientSees(port(main), false, NO_CALLER);
        } finally {
            main.stop();
        }
    }

    private static void assertLegacyClientSees(int port, boolean authenticated, String expected) {
        HttpClientStreamableHttpTransport.Builder transport
                = HttpClientStreamableHttpTransport.builder("http://localhost:" + port);
        if (authenticated) {
            transport.httpRequestCustomizer(
                    (builder, method, uri, body, context) -> builder.header("Authorization", "Basic " + CREDENTIALS));
        }
        McpSyncClient client = McpClient.sync(transport.build())
                .requestTimeout(Duration.ofSeconds(10))
                .initializationTimeout(Duration.ofSeconds(10))
                .build();
        try {
            client.initialize();

            assertThat(client.readResource(new McpSchema.ReadResourceRequest("camel:///whoami")).contents())
                    .singleElement().isInstanceOfSatisfying(McpSchema.TextResourceContents.class,
                            text -> assertThat(text.text()).isEqualTo(expected));
            assertThat(client.readResource(new McpSchema.ReadResourceRequest("crm://customers/42")).contents())
                    .singleElement().isInstanceOfSatisfying(McpSchema.TextResourceContents.class,
                            text -> assertThat(text.text()).isEqualTo(expected));
            assertThat(client.getPrompt(new McpSchema.GetPromptRequest("who_prompt", Map.of())).messages())
                    .singleElement().satisfies(message -> assertThat(message.content())
                            .isInstanceOfSatisfying(McpSchema.TextContent.class,
                                    text -> assertThat(text.text()).isEqualTo(expected)));
        } finally {
            client.closeGracefully();
        }
    }

    private static void assertModernClientSees(int port, boolean authenticated, String expected) {
        HttpClientMcpTransport.Builder transport = HttpClientMcpTransport.builder("http://localhost:" + port + "/mcp");
        if (authenticated) {
            transport.requestCustomizer(McpHttpRequestCustomizer.fromSync(
                    (request, headers, context) -> headers.set("Authorization", "Basic " + CREDENTIALS)));
        }
        io.modelcontextprotocol.modern.client.McpSyncClient client = io.modelcontextprotocol.modern.client.McpClient
                .sync(transport.build())
                .clientInfo(io.modelcontextprotocol.modern.McpSchema.Implementation.builder("test", "1").build())
                .build();

        assertThat(client.resource("camel:///whoami").read().contents()).singleElement()
                .isInstanceOfSatisfying(io.modelcontextprotocol.modern.McpSchema.TextResourceContents.class,
                        text -> assertThat(text.text()).isEqualTo(expected));
        assertThat(client.resource("crm://customers/42").read().contents()).singleElement()
                .isInstanceOfSatisfying(io.modelcontextprotocol.modern.McpSchema.TextResourceContents.class,
                        text -> assertThat(text.text()).isEqualTo(expected));
        assertThat(client.prompt("who_prompt").arguments(Map.of()).get().messages()).singleElement()
                .satisfies(message -> assertThat(message.content())
                        .isInstanceOfSatisfying(io.modelcontextprotocol.modern.McpSchema.TextContent.class,
                                text -> assertThat(text.text()).isEqualTo(expected)));
    }

    private static Main startMain(boolean authenticated, boolean stateless) {
        Main main = new Main();
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-resource:who_resource?resourceUri=camel:///whoami&tags=secured&mimeType=text/plain")
                        .process(McpServerMainCallerIdentityTest::who);
                from("ai-resource:who_template?uriTemplate=crm://customers/{id}&tags=secured&mimeType=text/plain")
                        .process(McpServerMainCallerIdentityTest::who);
                from("ai-prompt:who_prompt?tags=secured")
                        .process(McpServerMainCallerIdentityTest::who);
            }
        });
        main.addInitialProperty("camel.server.enabled", "true");
        // port 0: the OS picks a free port, read back from the started server
        main.addInitialProperty("camel.server.port", "0");
        main.addInitialProperty("camel.server.mcp-enabled", "true");
        main.addInitialProperty("camel.server.mcp-tags", "secured");
        if (authenticated) {
            main.addInitialProperty("camel.server.authentication-enabled", "true");
            main.addInitialProperty("camel.server.basic-properties-file", "mcp-basic-auth.properties");
        }
        if (stateless) {
            main.addInitialProperty("camel.server.mcp-stateless", "true");
        }
        main.start();
        return main;
    }

    private static int port(Main main) {
        return main.getCamelContext().hasService(MainHttpServer.class).getPort();
    }

    private static void who(Exchange exchange) {
        Object name = exchange.getProperty(McpToolCallContext.SECURITY_PRINCIPAL_NAME_PROPERTY);
        Object principal = exchange.getProperty(McpToolCallContext.SECURITY_PRINCIPAL_PROPERTY);
        exchange.getMessage().setBody("name=" + name + ",principal=" + (principal != null));
    }
}
