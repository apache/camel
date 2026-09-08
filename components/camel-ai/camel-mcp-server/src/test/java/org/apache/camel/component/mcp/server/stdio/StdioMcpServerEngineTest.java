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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mcp.server.McpServerBridge;
import org.apache.camel.component.mcp.server.McpServerConfiguration;
import org.apache.camel.component.mcp.server.McpServerTool;
import org.apache.camel.component.mcp.server.McpToolCallHandler;
import org.apache.camel.component.mcp.server.McpToolCallResult;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class StdioMcpServerEngineTest extends CamelTestSupport {

    private McpServerBridge bridge;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        StdioMcpServerEngine engine = new StdioMcpServerEngine();
        engine.setTransportStreams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        camelContext.getRegistry().bind("mcpServerEngine", engine);
        McpServerConfiguration configuration = new McpServerConfiguration();
        configuration.setTags("stdio");
        configuration.setToolTimeout(5000);
        bridge = new McpServerBridge(configuration);
        camelContext.addService(bridge);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-tool:say_hello?tags=stdio&description=Say hello"
                     + "&parameter.name=string&parameter.name.required=true")
                        .setBody(simple("Hello ${header.name}"));
            }
        };
    }

    @Test
    void bridgeResolvesStdioEngineWithoutHttpServer() {
        assertThat(bridge.getEngine()).isInstanceOf(StdioMcpServerEngine.class);
        assertThat(bridge.isStarted()).isTrue();
        assertThat(bridge.getEngine().consumesServingConfiguration()).isTrue();
    }

    @Test
    void toolAddedBeforeStartIsQueuedWithoutNpe() {
        StdioMcpServerEngine engine = new StdioMcpServerEngine();
        engine.initialize(new org.apache.camel.component.mcp.server.McpServerInfo(
                "test", "1.0", "/mcp", 0, 0, null, null, null, null, null));

        assertThatCode(() -> engine.toolAdded(sampleTool("queued_tool", "Queued before start")))
                .doesNotThrowAnyException();
        assertThatCode(() -> engine.toolRemoved("queued_tool")).doesNotThrowAnyException();
    }

    @Test
    void toolRemovedBeforeStartDoesNotThrow() {
        StdioMcpServerEngine engine = new StdioMcpServerEngine();
        engine.initialize(new org.apache.camel.component.mcp.server.McpServerInfo(
                "test", "1.0", "/mcp", 0, 0, null, null, null, null, null));

        engine.toolAdded(sampleTool("ephemeral", "Removed before start"));
        assertThatCode(() -> engine.toolRemoved("ephemeral")).doesNotThrowAnyException();
    }

    private static McpServerTool sampleTool(String name, String description) {
        McpToolCallHandler handler = arguments -> new McpToolCallResult("ok", false);
        return new McpServerTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public String inputSchemaJson() {
                return null;
            }

            @Override
            public java.util.Map<String, org.apache.camel.component.ai.tool.AiToolParameterHelper.ParameterDef> parameters() {
                return java.util.Map.of();
            }

            @Override
            public McpToolCallHandler handler() {
                return handler;
            }

            @Override
            public org.apache.camel.component.ai.tool.AiToolAnnotations annotations() {
                return null;
            }

            @Override
            public String outputSchemaJson() {
                return null;
            }
        };
    }
}
