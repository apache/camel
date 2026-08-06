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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerBridgeTest extends CamelTestSupport {

    private final RecordingMcpServerEngine engine = new RecordingMcpServerEngine();
    private McpServerBridge bridge;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        // a registry bean of type McpServerEngine wins over FactoryFinder discovery
        camelContext.getRegistry().bind("mcpServerEngine", engine);
        McpServerConfiguration configuration = new McpServerConfiguration();
        configuration.setTags("crm,notify");
        configuration.setToolTimeout(500);
        bridge = new McpServerBridge(configuration);
        camelContext.addService(bridge);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("ai-tool:query_db?tags=crm&description=Query the customer database"
                     + "&parameter.customerId=string&parameter.customerId.required=true"
                     + "&readOnlyHint=true&title=Query database")
                        .routeId("query-db-route")
                        .setBody(simple("customer-${header.customerId}"));

                from("ai-tool:delete_order?tags=crm&description=Delete an order"
                     + "&destructiveHint=true&idempotentHint=false")
                        .routeId("delete-order-route")
                        .setBody(constant("deleted"));

                from("ai-tool:send_email?tags=notify,crm&description=Send an email")
                        .routeId("send-email-route")
                        .setBody(constant("sent"));

                from("ai-tool:boom?tags=crm&description=Always fails")
                        .routeId("boom-route")
                        .process(e -> {
                            throw new IllegalStateException("secret internal detail");
                        });

                from("ai-tool:slow?tags=crm&description=Too slow")
                        .routeId("slow-route")
                        .delay(5000)
                        .setBody(constant("done"));

                from("ai-tool:hidden_tool?description=Untagged tool")
                        .setBody(constant("hidden"));

                from("ai-tool:other_tool?tags=untrusted&description=Other tag")
                        .setBody(constant("other"));
            }
        };
    }

    @Test
    void testPublishesOnlySelectedTags() {
        assertThat(engine.tools())
                .containsKeys("query_db", "send_email", "boom", "slow")
                .doesNotContainKeys("hidden_tool", "other_tool");
        assertThat(engine.info().serverName()).isEqualTo(context.getName());

        McpServerTool tool = engine.tools().get("query_db");
        assertThat(tool.description()).isEqualTo("Query the customer database");
        assertThat(tool.inputSchemaJson()).contains("customerId");
        assertThat(tool.parameters()).containsKey("customerId");
    }

    @Test
    void testToolAnnotationsPassedThroughBridge() {
        McpServerTool readTool = engine.tools().get("query_db");
        assertThat(readTool.annotations()).isNotNull();
        assertThat(readTool.annotations().title()).isEqualTo("Query database");
        assertThat(readTool.annotations().readOnlyHint()).isTrue();

        McpServerTool destructiveTool = engine.tools().get("delete_order");
        assertThat(destructiveTool.annotations()).isNotNull();
        assertThat(destructiveTool.annotations().destructiveHint()).isTrue();
        assertThat(destructiveTool.annotations().idempotentHint()).isFalse();
    }

    @Test
    void testCallToolSuccess() {
        McpToolCallResult result = engine.tools().get("query_db").handler().call(Map.of("customerId", "42"));

        assertThat(result.isError()).isFalse();
        assertThat(result.text()).isEqualTo("customer-42");
    }

    @Test
    void testCallToolMissingRequiredArgument() {
        McpToolCallResult result = engine.tools().get("query_db").handler().call(Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(result.text()).contains("customerId");
    }

    @Test
    void testCallToolExecutionErrorIsSanitized() {
        McpToolCallResult result = engine.tools().get("boom").handler().call(Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(result.text())
                .doesNotContain("secret internal detail")
                .isEqualTo("Tool execution failed");
    }

    @Test
    void testCallToolTimeout() {
        McpToolCallResult result = engine.tools().get("slow").handler().call(Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(result.text()).contains("timed out");
    }

    @Test
    void testToolRemovedAndReAddedOnRouteLifecycle() throws Exception {
        context.getRouteController().stopRoute("query-db-route");
        assertThat(engine.tools()).doesNotContainKey("query_db");
        assertThat(engine.removed()).contains("query_db");

        context.getRouteController().startRoute("query-db-route");
        assertThat(engine.tools()).containsKey("query_db");
    }

    @Test
    void testMultiTagToolRemovedOnceWhenRouteStops() throws Exception {
        // send_email is registered under two selected tags: stopping the route fires two deregistration
        // events but must remove the published tool exactly once
        context.getRouteController().stopRoute("send-email-route");

        assertThat(engine.tools()).doesNotContainKey("send_email");
        assertThat(engine.removed()).containsOnlyOnce("send_email");
    }

    @Test
    void testNameCollisionIsRefused() throws Exception {
        McpServerTool published = engine.tools().get("query_db");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("ai-tool:query_db?tags=notify&description=Colliding tool")
                        .routeId("colliding-route")
                        .setBody(constant("other"));
            }
        });

        // the colliding tool is refused: the originally published tool stays
        assertThat(engine.tools().get("query_db")).isSameAs(published);

        // and removing the colliding route does not remove the published tool
        context.getRouteController().stopRoute("colliding-route");
        assertThat(engine.tools()).containsKey("query_db");
    }
}
