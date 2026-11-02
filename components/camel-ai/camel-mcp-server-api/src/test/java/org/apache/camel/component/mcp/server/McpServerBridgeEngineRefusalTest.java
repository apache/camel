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

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * An engine refusing a tool or resource, like a runtime MCP server already serving the name or uri: the bridge does not
 * track it, so a later route can use the name or uri, and stopping the refused route removes nothing. The bridge does
 * not track a tool the engine failed to add with another exception either.
 */
class McpServerBridgeEngineRefusalTest extends CamelTestSupport {

    private static final String REFUSED_TOOL = "taken_tool";
    private static final String REFUSED_URI = "camel:///taken";
    private static final String FAILING_TOOL = "failing_tool";
    private static final String FAILING_URI = "camel:///failing";

    private final RefusingMcpServerEngine engine = new RefusingMcpServerEngine(REFUSED_TOOL, REFUSED_URI);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.getRegistry().bind("mcpServerEngine", engine);
        McpServerConfiguration configuration = new McpServerConfiguration();
        configuration.setTags("crm,notify");
        camelContext.addService(new McpServerBridge(configuration));
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("ai-tool:" + REFUSED_TOOL + "?tags=crm&description=Refused tool")
                        .routeId("refused-tool-route")
                        .setBody(constant("refused"));

                from("ai-tool:free_tool?tags=crm&description=Accepted tool")
                        .routeId("accepted-tool-route")
                        .setBody(constant("accepted"));

                from("ai-resource:taken_resource?resourceUri=" + REFUSED_URI + "&tags=crm")
                        .routeId("refused-resource-route")
                        .setBody(constant("refused"));

                from("ai-resource:free_resource?resourceUri=camel:///free&tags=crm")
                        .routeId("accepted-resource-route")
                        .setBody(constant("accepted"));
            }
        };
    }

    @Test
    void testRefusedToolAndResourceAreNotPublishedAndTheirRoutesKeepRunning() {
        // the bridge publishes the startup routes when it starts: a refusal must not fail the CamelContext start
        assertThat(engine.tools()).containsOnlyKeys("free_tool");
        assertThat(engine.resources()).containsOnlyKeys("camel:///free");
        assertThat(context.getRouteController().getRouteStatus("refused-tool-route")).isEqualTo(ServiceStatus.Started);
        assertThat(context.getRouteController().getRouteStatus("refused-resource-route"))
                .isEqualTo(ServiceStatus.Started);
    }

    @Test
    void testOtherEngineExceptionOnToolFailsTheStart() throws Exception {
        // the bridge publishes the startup routes when it starts: an exception that is not a refusal fails the start
        try (DefaultCamelContext failingContext = new DefaultCamelContext()) {
            failingContext.getRegistry().bind("mcpServerEngine", new RefusingMcpServerEngine());
            McpServerConfiguration configuration = new McpServerConfiguration();
            configuration.setTags("crm");
            failingContext.addService(new McpServerBridge(configuration));
            failingContext.addRoutes(new RouteBuilder() {
                public void configure() {
                    from("ai-tool:" + FAILING_TOOL + "?tags=crm&description=Failing tool")
                            .setBody(constant("failing"));
                }
            });

            assertThatThrownBy(failingContext::start)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("engine failure");
        }
    }

    @Test
    void testOtherEngineExceptionOnResourceFailsTheStart() throws Exception {
        // the bridge publishes the startup routes when it starts: an exception that is not a refusal fails the start
        try (DefaultCamelContext failingContext = new DefaultCamelContext()) {
            failingContext.getRegistry().bind("mcpServerEngine", new RefusingMcpServerEngine());
            McpServerConfiguration configuration = new McpServerConfiguration();
            configuration.setTags("crm");
            failingContext.addService(new McpServerBridge(configuration));
            failingContext.addRoutes(new RouteBuilder() {
                public void configure() {
                    from("ai-resource:failing_resource?resourceUri=" + FAILING_URI + "&tags=crm")
                            .setBody(constant("failing"));
                }
            });

            assertThatThrownBy(failingContext::start)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("engine failure");
        }
    }

    @Test
    void testStoppingRouteOfToolTheEngineFailedToAddRemovesNothing() throws Exception {
        // a route added after the bridge started: the registry only logs the engine exception and the route runs, but
        // the bridge did not track the tool, so stopping the route does not ask the engine to remove it
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("ai-tool:" + FAILING_TOOL + "?tags=crm&description=Failing tool")
                        .routeId("failing-tool-route")
                        .setBody(constant("failing"));
            }
        });
        assertThat(context.getRouteController().getRouteStatus("failing-tool-route")).isEqualTo(ServiceStatus.Started);

        context.getRouteController().stopRoute("failing-tool-route");

        assertThat(engine.removed()).isEmpty();
    }

    @Test
    void testLaterToolRouteWithRefusedNameIsPublished() throws Exception {
        // the runtime no longer serves the name: a later route using it is not refused as a clash with the refused one
        engine.accept(REFUSED_TOOL);
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("ai-tool:" + REFUSED_TOOL + "?tags=notify&description=Later tool")
                        .setBody(constant("later"));
            }
        });

        assertThat(engine.tools()).containsKey(REFUSED_TOOL);
    }

    @Test
    void testLaterResourceRouteWithRefusedUriIsPublished() throws Exception {
        // the runtime no longer serves the uri: a later route using it is not refused as a clash with the refused one
        engine.accept(REFUSED_URI);
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("ai-resource:later_resource?resourceUri=" + REFUSED_URI + "&tags=notify")
                        .setBody(constant("later"));
            }
        });

        assertThat(engine.resources()).containsKey(REFUSED_URI);
    }

    @Test
    void testStoppingRefusedToolRouteRemovesNothing() throws Exception {
        context.getRouteController().stopRoute("refused-tool-route");
        context.getRouteController().stopRoute("accepted-tool-route");

        assertThat(engine.removed()).containsExactly("free_tool");
    }

    @Test
    void testStoppingRefusedResourceRouteRemovesNothing() throws Exception {
        context.getRouteController().stopRoute("refused-resource-route");
        context.getRouteController().stopRoute("accepted-resource-route");

        assertThat(engine.removedResources()).containsExactly("camel:///free");
    }

    /**
     * Refuses the given tool names and resource uris, like a runtime MCP server whose application already serves them,
     * and fails on {@code FAILING_TOOL} and {@code FAILING_URI} with an exception that is not a refusal.
     */
    private static final class RefusingMcpServerEngine extends RecordingMcpServerEngine {

        private final Set<String> refused = ConcurrentHashMap.newKeySet();

        RefusingMcpServerEngine(String... refused) {
            this.refused.addAll(List.of(refused));
        }

        void accept(String nameOrUri) {
            refused.remove(nameOrUri);
        }

        @Override
        public void toolAdded(McpServerTool tool) {
            if (FAILING_TOOL.equals(tool.name())) {
                throw new IllegalStateException("engine failure");
            }
            if (refused.contains(tool.name())) {
                throw new McpServerRefusedException("the name is used by an application tool");
            }
            super.toolAdded(tool);
        }

        @Override
        public void resourceAdded(McpServerResource resource) {
            if (FAILING_URI.equals(resource.uri())) {
                throw new IllegalStateException("engine failure");
            }
            if (refused.contains(resource.uri())) {
                throw new McpServerRefusedException("the uri is used by an application resource");
            }
            super.resourceAdded(resource);
        }
    }
}
