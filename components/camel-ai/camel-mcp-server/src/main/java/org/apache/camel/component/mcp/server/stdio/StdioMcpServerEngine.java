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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.CamelContext;
import org.apache.camel.component.ai.tool.AiToolAnnotations;
import org.apache.camel.component.mcp.server.McpServerEngine;
import org.apache.camel.component.mcp.server.McpServerInfo;
import org.apache.camel.component.mcp.server.McpServerTool;
import org.apache.camel.component.mcp.server.McpToolCallResult;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link McpServerEngine} for Camel Main / JBang: serves MCP over the process stdio transport using the official MCP
 * Java SDK. Intended for IDE and local agent integration where the parent process launches {@code camel run} as a child
 * and speaks JSON-RPC on stdin/stdout. In this mode stdout must carry only MCP protocol frames — callers should route
 * logging and startup banners to stderr.
 *
 * @since 4.23
 */
public class StdioMcpServerEngine extends ServiceSupport implements McpServerEngine {

    private static final Logger LOG = LoggerFactory.getLogger(StdioMcpServerEngine.class);

    private static final String EMPTY_OBJECT_SCHEMA = """
            {
              "type": "object",
              "properties": {},
              "additionalProperties": false
            }
            """;

    private CamelContext camelContext;
    private McpServerInfo info;
    private McpJsonMapper jsonMapper;
    private StdioServerTransportProvider transport;
    private McpSyncServer server;
    private InputStream inputStream;
    private OutputStream outputStream;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void initialize(McpServerInfo info) {
        this.info = info;
    }

    /**
     * Optional test hook: when both streams are set, the engine uses them instead of {@link System#in} and
     * {@link System#out}.
     */
    public void setTransportStreams(InputStream inputStream, OutputStream outputStream) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    @Override
    public boolean consumesServingConfiguration() {
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        jsonMapper = McpJsonDefaults.getMapper();
        if (inputStream != null && outputStream != null) {
            transport = new StdioServerTransportProvider(jsonMapper, inputStream, outputStream);
        } else {
            transport = new StdioServerTransportProvider(jsonMapper);
        }
        server = McpServer.sync(transport)
                .serverInfo(info.serverName(), info.version())
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .immediateExecution(true)
                .build();
        LOG.info("MCP server '{}' serving ai-tool routes over stdio", info.serverName());
    }

    @Override
    protected void doStop() throws Exception {
        if (server != null) {
            server.closeGracefully();
            server = null;
        }
        transport = null;
    }

    @Override
    public void toolAdded(McpServerTool tool) {
        McpSchema.Tool mcpTool = buildMcpTool(tool);
        McpServerFeatures.SyncToolSpecification spec = McpServerFeatures.SyncToolSpecification.builder()
                .tool(mcpTool)
                .callHandler((exchange, request) -> {
                    Map<String, Object> arguments = request.arguments() != null ? request.arguments() : Map.of();
                    McpToolCallResult result = tool.handler().call(arguments);
                    return McpSchema.CallToolResult.builder()
                            .addTextContent(result.text())
                            .isError(result.isError())
                            .build();
                })
                .build();
        server.addTool(spec);
        LOG.debug("MCP tool added: {}", tool.name());
    }

    @Override
    public void toolRemoved(String toolName) {
        try {
            server.removeTool(toolName);
            LOG.debug("MCP tool removed: {}", toolName);
        } catch (Exception e) {
            LOG.debug("Failed to remove MCP tool {}: {}", toolName, e.getMessage());
        }
    }

    private McpSchema.Tool buildMcpTool(McpServerTool tool) {
        String schema = tool.inputSchemaJson() != null ? tool.inputSchemaJson() : EMPTY_OBJECT_SCHEMA;
        McpSchema.Tool.Builder builder = McpSchema.Tool.builder(tool.name(), jsonMapper, schema)
                .description(tool.description());
        applyAnnotations(builder, tool.annotations());
        return builder.build();
    }

    private static void applyAnnotations(McpSchema.Tool.Builder builder, AiToolAnnotations annotations) {
        if (annotations == null) {
            return;
        }
        if (annotations.title() != null) {
            builder.title(annotations.title());
        }
        McpSchema.ToolAnnotations.Builder hintBuilder = McpSchema.ToolAnnotations.builder();
        boolean hasHints = false;
        if (annotations.readOnlyHint() != null) {
            hintBuilder.readOnlyHint(annotations.readOnlyHint());
            hasHints = true;
        }
        if (annotations.destructiveHint() != null) {
            hintBuilder.destructiveHint(annotations.destructiveHint());
            hasHints = true;
        }
        if (annotations.idempotentHint() != null) {
            hintBuilder.idempotentHint(annotations.idempotentHint());
            hasHints = true;
        }
        if (annotations.openWorldHint() != null) {
            hintBuilder.openWorldHint(annotations.openWorldHint());
            hasHints = true;
        }
        if (hasHints) {
            builder.annotations(hintBuilder.build());
        }
    }
}
