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
package org.apache.camel.component.mcp.server;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ai.resource.AiResourceRegistry;
import org.apache.camel.component.ai.resource.AiResourceSpec;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerBridgeResourceTest extends CamelTestSupport {

    private static final byte[] PDF_BYTES = { 0x25, 0x50, 0x44, 0x46 };

    private final RecordingMcpServerEngine engine = new RecordingMcpServerEngine();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.getRegistry().bind("mcpServerEngine", engine);
        McpServerConfiguration configuration = new McpServerConfiguration();
        configuration.setTags("crm,notify");
        configuration.setResourceTimeout(500);
        camelContext.addService(new McpServerBridge(configuration));
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("ai-resource:app_config?resourceUri=camel:///config/app.json&tags=crm"
                     + "&description=Application configuration&mimeType=application/json"
                     + "&title=Application configuration")
                        .routeId("app-config-route")
                        .setBody(constant("{\"env\":\"test\"}"));

                from("ai-resource:latest_report?resourceUri=camel:///reports/latest.pdf&tags=notify,crm"
                     + "&description=Latest report&mimeType=application/pdf")
                        .routeId("latest-report-route")
                        .setBody(constant(PDF_BYTES));

                from("ai-resource:boom?resourceUri=camel:///boom&tags=crm&description=Always fails")
                        .routeId("boom-resource-route")
                        .process(e -> {
                            throw new IllegalStateException("secret internal detail");
                        });

                from("ai-resource:slow?resourceUri=camel:///slow&tags=crm&description=Too slow")
                        .routeId("slow-resource-route")
                        .delay(5000)
                        .setBody(constant("done"));

                from("ai-resource:hidden?resourceUri=camel:///hidden&description=Untagged resource")
                        .setBody(constant("hidden"));

                from("ai-resource:other?resourceUri=camel:///other&tags=untrusted&description=Other tag")
                        .setBody(constant("other"));
            }
        };
    }

    @Test
    void testPublishesOnlySelectedTags() {
        assertThat(engine.resources())
                .containsKeys("camel:///config/app.json", "camel:///reports/latest.pdf", "camel:///boom",
                        "camel:///slow")
                .doesNotContainKeys("camel:///hidden", "camel:///other");

        McpServerResource resource = engine.resources().get("camel:///config/app.json");
        assertThat(resource.name()).isEqualTo("app_config");
        assertThat(resource.description()).isEqualTo("Application configuration");
        assertThat(resource.mimeType()).isEqualTo("application/json");
        assertThat(resource.title()).isEqualTo("Application configuration");
    }

    @Test
    void testReadTextualResource() {
        McpResourceReadResult result = engine.resources().get("camel:///config/app.json").handler().read();

        assertThat(result.isError()).isFalse();
        assertThat(result.text()).isEqualTo("{\"env\":\"test\"}");
        assertThat(result.blob()).isNull();
    }

    @Test
    void testReadBinaryResource() {
        McpResourceReadResult result = engine.resources().get("camel:///reports/latest.pdf").handler().read();

        assertThat(result.isError()).isFalse();
        assertThat(result.blob()).isEqualTo(PDF_BYTES);
        assertThat(result.text()).isNull();
    }

    @Test
    void testReadErrorIsSanitized() {
        McpResourceReadResult result = engine.resources().get("camel:///boom").handler().read();

        assertThat(result.isError()).isTrue();
        assertThat(result.errorMessage())
                .isEqualTo("Resource read failed")
                .doesNotContain("secret internal detail");
    }

    @Test
    void testReadTimeout() {
        McpResourceReadResult result = engine.resources().get("camel:///slow").handler().read();

        assertThat(result.isError()).isTrue();
        assertThat(result.errorMessage()).isEqualTo("Resource read timed out");
    }

    @Test
    void testResourceListReflectsRouteStopAndStart() throws Exception {
        context.getRouteController().stopRoute("app-config-route");
        assertThat(engine.resources()).doesNotContainKey("camel:///config/app.json");
        assertThat(engine.removedResources()).contains("camel:///config/app.json");

        context.getRouteController().startRoute("app-config-route");
        assertThat(engine.resources()).containsKey("camel:///config/app.json");
    }

    @Test
    void testResourceStaysWhileAnotherSelectedTagKeepsIt() {
        // latest_report is tagged notify and crm; both are selected, so losing one tag must not unpublish it
        AiResourceRegistry registry = AiResourceRegistry.getOrCreate(context);
        AiResourceSpec spec = registry.getResourcesByTag("notify").stream()
                .filter(r -> "camel:///reports/latest.pdf".equals(r.getUri()))
                .findFirst()
                .orElseThrow();

        registry.remove("notify", spec);

        assertThat(engine.resources())
                .as("The resource is still selected through its other tag")
                .containsKey("camel:///reports/latest.pdf");

        registry.remove("crm", spec);

        assertThat(engine.resources())
                .as("The resource is unpublished once no selected tag keeps it")
                .doesNotContainKey("camel:///reports/latest.pdf");
    }
}
