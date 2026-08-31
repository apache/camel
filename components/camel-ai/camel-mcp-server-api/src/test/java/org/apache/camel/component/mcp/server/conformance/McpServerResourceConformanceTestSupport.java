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
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Engine conformance kit for MCP resources: the behavioural contract an
 * {@link org.apache.camel.component.mcp.server.McpServerEngine} implementation must satisfy once it serves resources,
 * verified with the official MCP Java SDK client over streamable HTTP.
 * <p>
 * This is a separate kit from {@link McpServerConformanceTestSupport} on purpose: resource support is opt-in on the
 * SPI, so an engine that serves tools only stays conformant without changes. Engines extend this class once
 * {@link org.apache.camel.component.mcp.server.McpServerEngine#supportsResources()} returns true.
 */
public abstract class McpServerResourceConformanceTestSupport extends CamelTestSupport {

    public static final String CONFORMANCE_TAG = "conformance";
    public static final long RESOURCE_TIMEOUT_MILLIS = 2000;
    public static final byte[] BINARY_CONTENT = { 0x25, 0x50, 0x44, 0x46 };

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
     * Adjusts the bridge configuration; tags and resource timeout are preset by the kit.
     */
    protected void configureBridge(McpServerConfiguration configuration) {
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        customizeCamelContext(camelContext);
        McpServerConfiguration configuration = new McpServerConfiguration();
        configuration.setTags(CONFORMANCE_TAG);
        configuration.setResourceTimeout(RESOURCE_TIMEOUT_MILLIS);
        configureBridge(configuration);
        bridge = new McpServerBridge(configuration);
        camelContext.addService(bridge);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("ai-resource:app_config?resourceUri=camel:///config/app.json&tags=" + CONFORMANCE_TAG
                     + "&description=Application configuration&mimeType=application/json")
                        .routeId("app-config-route")
                        .setBody(constant("{\"env\":\"test\"}"));

                from("ai-resource:latest_report?resourceUri=camel:///reports/latest.pdf&tags=" + CONFORMANCE_TAG
                     + "&description=Latest report&mimeType=application/pdf")
                        .routeId("latest-report-route")
                        .setBody(constant(BINARY_CONTENT));

                from("ai-resource:fail_resource?resourceUri=camel:///fail&tags=" + CONFORMANCE_TAG
                     + "&description=Always fails")
                        .routeId("fail-resource-route")
                        .process(e -> {
                            throw new IllegalStateException("secret internal detail");
                        });

                from("ai-resource:slow_resource?resourceUri=camel:///slow&tags=" + CONFORMANCE_TAG
                     + "&description=Exceeds the resource timeout")
                        .routeId("slow-resource-route")
                        .delay(RESOURCE_TIMEOUT_MILLIS * 3)
                        .setBody(constant("done"));

                from("ai-resource:hidden_resource?resourceUri=camel:///hidden"
                     + "&description=Untagged resource, must not be exposed")
                        .setBody(constant("hidden"));

                from("ai-resource:other_resource?resourceUri=camel:///other&tags=untrusted"
                     + "&description=Not a selected tag, must not be exposed")
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
    void testServerAdvertisesResourcesCapability() {
        assertThat(client().getServerCapabilities().resources())
                .as("An engine serving resources must advertise the capability")
                .isNotNull();
    }

    @Test
    void testListResourcesExposesOnlySelectedTags() {
        List<McpSchema.Resource> resources = client().listResources().resources();

        assertThat(resources).extracting(McpSchema.Resource::uri)
                .contains("camel:///config/app.json", "camel:///reports/latest.pdf", "camel:///fail", "camel:///slow")
                .doesNotContain("camel:///hidden", "camel:///other");

        McpSchema.Resource config = resources.stream()
                .filter(r -> "camel:///config/app.json".equals(r.uri()))
                .findFirst()
                .orElseThrow();
        assertThat(config.name()).isEqualTo("app_config");
        assertThat(config.description()).isEqualTo("Application configuration");
        assertThat(config.mimeType()).isEqualTo("application/json");
    }

    @Test
    void testReadTextResource() {
        McpSchema.ReadResourceResult result
                = client().readResource(new McpSchema.ReadResourceRequest("camel:///config/app.json"));

        assertThat(result.contents()).hasSize(1);
        McpSchema.TextResourceContents contents = (McpSchema.TextResourceContents) result.contents().get(0);
        assertThat(contents.uri()).isEqualTo("camel:///config/app.json");
        assertThat(contents.mimeType()).isEqualTo("application/json");
        assertThat(contents.text()).isEqualTo("{\"env\":\"test\"}");
    }

    @Test
    void testReadBinaryResource() {
        McpSchema.ReadResourceResult result
                = client().readResource(new McpSchema.ReadResourceRequest("camel:///reports/latest.pdf"));

        assertThat(result.contents()).hasSize(1);
        McpSchema.BlobResourceContents contents = (McpSchema.BlobResourceContents) result.contents().get(0);
        assertThat(contents.mimeType()).isEqualTo("application/pdf");
        assertThat(Base64.getDecoder().decode(contents.blob())).isEqualTo(BINARY_CONTENT);
    }

    @Test
    void testReadResourceErrorIsSanitized() {
        assertThatThrownBy(() -> client().readResource(new McpSchema.ReadResourceRequest("camel:///fail")))
                .hasMessageContaining("Resource read failed")
                .hasMessageNotContaining("secret internal detail");
    }

    @Test
    void testReadResourceTimeout() {
        assertThatThrownBy(() -> client().readResource(new McpSchema.ReadResourceRequest("camel:///slow")))
                .hasMessageContaining("timed out");
    }

    @Test
    void testResourcesListReflectsRouteStopAndStart() throws Exception {
        assertThat(client().listResources().resources())
                .extracting(McpSchema.Resource::uri).contains("camel:///config/app.json");

        context.getRouteController().stopRoute("app-config-route");
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client().listResources().resources())
                .extracting(McpSchema.Resource::uri).doesNotContain("camel:///config/app.json"));

        context.getRouteController().startRoute("app-config-route");
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client().listResources().resources())
                .extracting(McpSchema.Resource::uri).contains("camel:///config/app.json"));
    }
}
