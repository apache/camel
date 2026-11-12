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
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mcp.server.McpPromptArgument;
import org.apache.camel.component.mcp.server.McpPromptHandler;
import org.apache.camel.component.mcp.server.McpPromptMessage;
import org.apache.camel.component.mcp.server.McpPromptResult;
import org.apache.camel.component.mcp.server.McpResourceReadResult;
import org.apache.camel.component.mcp.server.McpServerBridge;
import org.apache.camel.component.mcp.server.McpServerConfiguration;
import org.apache.camel.component.mcp.server.McpServerInfo;
import org.apache.camel.component.mcp.server.McpServerPrompt;
import org.apache.camel.component.mcp.server.McpServerResource;
import org.apache.camel.component.mcp.server.McpServerResourceTemplate;
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
        engine.initialize(new McpServerInfo(
                "test", "1.0", "/mcp", 0, 0, null, null, null, null, null));

        assertThatCode(() -> engine.toolAdded(sampleTool("queued_tool", "Queued before start")))
                .doesNotThrowAnyException();
        assertThatCode(() -> engine.toolRemoved("queued_tool")).doesNotThrowAnyException();
    }

    @Test
    void resourceAddedBeforeStartIsQueuedAndCanBeRemoved() {
        StdioMcpServerEngine engine = new StdioMcpServerEngine();
        engine.initialize(new McpServerInfo(
                "test", "1.0", "/mcp", 0, 0, null, null, null, null, null));

        engine.resourceAdded(sampleResource("camel:///queued"));
        assertThat(engine.pendingResourceCount()).isEqualTo(1);
        engine.resourceRemoved("camel:///queued");
        assertThat(engine.pendingResourceCount()).isZero();
    }

    @Test
    void resourceTemplateAddedBeforeStartIsQueuedAndCanBeRemoved() {
        StdioMcpServerEngine engine = new StdioMcpServerEngine();
        engine.initialize(new McpServerInfo(
                "test", "1.0", "/mcp", 0, 0, null, null, null, null, null));

        engine.resourceTemplateAdded(sampleResourceTemplate("camel:///queued/{id}"));
        assertThat(engine.pendingResourceTemplateCount()).isEqualTo(1);
        engine.resourceTemplateRemoved("camel:///queued/{id}");
        assertThat(engine.pendingResourceTemplateCount()).isZero();
    }

    @Test
    void resourceTemplateAddedBeforeStartIsFlushedWhenServerStarts() throws Exception {
        StdioMcpServerEngine engine = new StdioMcpServerEngine();
        engine.initialize(new McpServerInfo(
                "test", "1.0", "/mcp", 0, 0, null, null, null, null, null));
        engine.setTransportStreams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        engine.resourceTemplateAdded(sampleResourceTemplate("camel:///queued/{id}"));

        assertThat(engine.pendingResourceTemplateCount()).isEqualTo(1);
        try {
            assertThatCode(engine::start).doesNotThrowAnyException();
            assertThat(engine.isStarted()).isTrue();
            assertThat(engine.pendingResourceTemplateCount()).isZero();
        } finally {
            engine.stop();
        }
    }

    @Test
    void promptAddedBeforeStartIsFlushedWhenServerStarts() throws Exception {
        StdioMcpServerEngine engine = new StdioMcpServerEngine();
        engine.initialize(new McpServerInfo(
                "test", "1.0", "/mcp", 0, 0, null, null, null, null, null));
        engine.setTransportStreams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        engine.promptAdded(samplePrompt("queued_prompt", "Queued before start"));

        assertThat(engine.pendingPromptCount()).isEqualTo(1);

        try {
            assertThatCode(engine::start).doesNotThrowAnyException();
            assertThat(engine.isStarted()).isTrue();
            assertThat(engine.pendingPromptCount()).isZero();
        } finally {
            engine.stop();
        }
    }

    @Test
    void promptRemovedBeforeStartRemovesItFromPendingQueue() {
        StdioMcpServerEngine engine = new StdioMcpServerEngine();
        engine.initialize(new McpServerInfo(
                "test", "1.0", "/mcp", 0, 0, null, null, null, null, null));

        engine.promptAdded(samplePrompt("ephemeral_prompt", "Removed before start"));
        assertThat(engine.pendingPromptCount()).isEqualTo(1);

        assertThatCode(() -> engine.promptRemoved("ephemeral_prompt")).doesNotThrowAnyException();
        assertThat(engine.pendingPromptCount()).isZero();
    }

    @Test
    void toolRemovedBeforeStartDoesNotThrow() {
        StdioMcpServerEngine engine = new StdioMcpServerEngine();
        engine.initialize(new McpServerInfo(
                "test", "1.0", "/mcp", 0, 0, null, null, null, null, null));

        engine.toolAdded(sampleTool("ephemeral", "Removed before start"));
        assertThatCode(() -> engine.toolRemoved("ephemeral")).doesNotThrowAnyException();
    }

    @Test
    void toolAddedAfterStdinClosedDoesNotFail() throws Exception {
        StdioMcpServerEngine engine = new StdioMcpServerEngine();
        engine.initialize(new McpServerInfo(
                "test", "1.0", "/mcp", 0, 0, null, null, null, null, null));
        // an empty stdin is a client that went away: the session closes and notifications can no longer be sent
        engine.setTransportStreams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        engine.start();
        try {
            for (int i = 0; i < 20; i++) {
                String name = "late_tool_" + i;
                assertThatCode(() -> engine.toolAdded(sampleTool(name, "Added after stdin closed")))
                        .doesNotThrowAnyException();
            }
        } finally {
            engine.stop();
        }
    }

    private static McpServerResource sampleResource(String uri) {
        return new McpServerResource() {
            @Override
            public String uri() {
                return uri;
            }

            @Override
            public String name() {
                return "queued";
            }

            @Override
            public String description() {
                return "Queued resource";
            }

            @Override
            public String mimeType() {
                return "text/plain";
            }

            @Override
            public org.apache.camel.component.mcp.server.McpResourceReadHandler handler() {
                return () -> McpResourceReadResult.text("queued");
            }
        };
    }

    private static McpServerResourceTemplate sampleResourceTemplate(String uriTemplate) {
        return new McpServerResourceTemplate() {
            @Override
            public String uriTemplate() {
                return uriTemplate;
            }

            @Override
            public String name() {
                return "queued_template";
            }

            @Override
            public String description() {
                return "Queued resource template";
            }

            @Override
            public String mimeType() {
                return "text/plain";
            }

            @Override
            public org.apache.camel.component.mcp.server.McpResourceTemplateReadHandler handler() {
                return uri -> McpResourceReadResult.text(uri);
            }
        };
    }

    private static McpServerPrompt samplePrompt(String name, String description) {
        return new McpServerPrompt() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public String title() {
                return null;
            }

            @Override
            public List<McpPromptArgument> arguments() {
                return List.of();
            }

            @Override
            public McpPromptHandler handler() {
                return arguments -> McpPromptResult.success(description,
                        List.of(new McpPromptMessage("user", description)));
            }
        };
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
