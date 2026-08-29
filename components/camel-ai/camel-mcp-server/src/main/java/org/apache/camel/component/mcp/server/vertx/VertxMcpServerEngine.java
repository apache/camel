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

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.CamelContext;
import org.apache.camel.component.ai.tool.AiToolAnnotations;
import org.apache.camel.component.mcp.server.McpResourceReadResult;
import org.apache.camel.component.mcp.server.McpServerConstants;
import org.apache.camel.component.mcp.server.McpServerEngine;
import org.apache.camel.component.mcp.server.McpServerIcon;
import org.apache.camel.component.mcp.server.McpServerInfo;
import org.apache.camel.component.mcp.server.McpServerResource;
import org.apache.camel.component.mcp.server.McpServerTool;
import org.apache.camel.component.mcp.server.McpToolCallResult;
import org.apache.camel.component.platform.http.PlatformHttpComponent;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpRouter;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link McpServerEngine} for Camel Main / JBang: serves MCP streamable HTTP through the Vert.x platform HTTP router
 * using the official MCP Java SDK. By default the MCP endpoint is registered on the main HTTP server's router, so it
 * serves on the main server port and inherits its lifecycle, authentication and CORS configuration. Set
 * {@link #setTargetServerType(String)} to {@link VertxPlatformHttpRouter#SERVER_TYPE_MANAGEMENT} to serve on the
 * management HTTP server router instead (e.g. for dev/diagnostics tools that must not be publicly exposed).
 */
@JdkService(McpServerConstants.MCP_SERVER_ENGINE_FACTORY)
public class VertxMcpServerEngine extends ServiceSupport implements McpServerEngine {

    private static final Logger LOG = LoggerFactory.getLogger(VertxMcpServerEngine.class);

    private static final String EMPTY_OBJECT_SCHEMA = """
            {
              "type": "object",
              "properties": {},
              "additionalProperties": false
            }
            """;
    private static final String APPLICATION_JSON = "application/json";

    private CamelContext camelContext;
    private McpServerInfo info;
    private McpJsonMapper jsonMapper;
    private VertxMcpStreamableServerTransportProvider transport;
    private McpSyncServer server;
    private String targetServerType = VertxPlatformHttpRouter.SERVER_TYPE_SERVER;

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

    public String getTargetServerType() {
        return targetServerType;
    }

    /**
     * The server type of the {@link VertxPlatformHttpRouter} to register the MCP endpoint on:
     * {@link VertxPlatformHttpRouter#SERVER_TYPE_SERVER} (default) for the main HTTP server, or
     * {@link VertxPlatformHttpRouter#SERVER_TYPE_MANAGEMENT} for the management HTTP server.
     */
    public void setTargetServerType(String targetServerType) {
        this.targetServerType = targetServerType;
    }

    @Override
    public boolean consumesServingConfiguration() {
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        VertxPlatformHttpRouter router = lookupRouter();
        jsonMapper = McpJsonDefaults.getMapper();
        transport = new VertxMcpStreamableServerTransportProvider(
                jsonMapper, info.path(),
                info.sessionKeepAliveIntervalMs(), info.sessionIdleTtlMs());
        McpServer.SyncSpecification<?> specification = McpServer.sync(transport)
                .serverInfo(buildServerInfo(info))
                // resources(subscribe, listChanged): per-resource subscriptions are not offered, but clients are
                // notified when ai-resource routes start or stop
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).resources(false, true).build())
                .immediateExecution(true);
        if (info.instructions() != null && !info.instructions().isBlank()) {
            specification.instructions(info.instructions());
        }
        server = specification.build();
        // register routes only once the server has set the session factory on the transport
        transport.registerRoutes(router);

        PlatformHttpComponent platformHttpComponent
                = (PlatformHttpComponent) camelContext.hasComponent("platform-http");
        if (platformHttpComponent != null) {
            platformHttpComponent.addHttpEndpoint(info.path(), "GET,POST,DELETE", APPLICATION_JSON,
                    "application/json,text/event-stream", null);
        }
        LOG.info("MCP server '{}' serving tools and resources on path {}", info.serverName(), info.path());
    }

    @Override
    protected void doStop() throws Exception {
        if (transport != null) {
            transport.unregisterRoutes();
        }
        if (server != null) {
            server.closeGracefully();
            server = null;
        }
        PlatformHttpComponent platformHttpComponent
                = (PlatformHttpComponent) camelContext.hasComponent("platform-http");
        if (platformHttpComponent != null && info != null) {
            platformHttpComponent.removeHttpEndpoint(info.path());
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
                    McpSchema.CallToolResult.Builder builder = McpSchema.CallToolResult.builder()
                            .addTextContent(result.text())
                            .isError(result.isError());
                    if (result.structuredContent() != null) {
                        builder.structuredContent(result.structuredContent());
                    }
                    return builder.build();
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

    @Override
    public void resourceAdded(McpServerResource resource) {
        McpSchema.Resource mcpResource = buildMcpResource(resource);
        McpServerFeatures.SyncResourceSpecification spec = new McpServerFeatures.SyncResourceSpecification(
                mcpResource,
                (exchange, request) -> {
                    McpResourceReadResult result = resource.handler().read();
                    if (result.isError()) {
                        throw McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
                                .message(result.errorMessage())
                                .build();
                    }
                    McpSchema.ResourceContents contents;
                    if (result.blob() != null) {
                        contents = new McpSchema.BlobResourceContents(
                                resource.uri(), resource.mimeType(),
                                Base64.getEncoder().encodeToString(result.blob()));
                    } else {
                        contents = new McpSchema.TextResourceContents(
                                resource.uri(), resource.mimeType(), result.text());
                    }
                    return new McpSchema.ReadResourceResult(List.of(contents));
                });
        server.addResource(spec);
        LOG.debug("MCP resource added: {}", resource.uri());
    }

    @Override
    public void resourceRemoved(String resourceUri) {
        try {
            server.removeResource(resourceUri);
            LOG.debug("MCP resource removed: {}", resourceUri);
        } catch (Exception e) {
            LOG.debug("Failed to remove MCP resource {}: {}", resourceUri, e.getMessage());
        }
    }

    @Override
    public boolean supportsResources() {
        return true;
    }

    int sessionCount() {
        return transport != null ? transport.sessionCount() : 0;
    }

    private static McpSchema.Resource buildMcpResource(McpServerResource resource) {
        McpSchema.Resource.Builder builder = McpSchema.Resource.builder()
                .uri(resource.uri())
                .name(resource.name())
                .description(resource.description())
                .mimeType(resource.mimeType());
        if (resource.title() != null && !resource.title().isBlank()) {
            builder.title(resource.title());
        }
        return builder.build();
    }

    private McpSchema.Tool buildMcpTool(McpServerTool tool) {
        String schema = tool.inputSchemaJson() != null ? tool.inputSchemaJson() : EMPTY_OBJECT_SCHEMA;
        McpSchema.Tool.Builder builder = McpSchema.Tool.builder(tool.name(), jsonMapper, schema)
                .description(tool.description());
        if (tool.outputSchemaJson() != null && !tool.outputSchemaJson().isBlank()) {
            builder.outputSchema(jsonMapper, tool.outputSchemaJson());
        }
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
        if (annotations.returnDirect() != null) {
            hintBuilder.returnDirect(annotations.returnDirect());
            hasHints = true;
        }
        if (hasHints) {
            builder.annotations(hintBuilder.build());
        }
    }

    private static McpSchema.Implementation buildServerInfo(McpServerInfo info) {
        McpSchema.Implementation.Builder builder
                = McpSchema.Implementation.builder(info.serverName(), info.version());
        if (info.title() != null && !info.title().isBlank()) {
            builder.title(info.title());
        }
        if (info.description() != null && !info.description().isBlank()) {
            builder.description(info.description());
        }
        if (info.websiteUrl() != null && !info.websiteUrl().isBlank()) {
            builder.websiteUrl(info.websiteUrl());
        }
        if (info.icons() != null && !info.icons().isEmpty()) {
            builder.icons(mapIcons(info.icons()));
        }
        return builder.build();
    }

    private static List<McpSchema.Icon> mapIcons(List<McpServerIcon> icons) {
        return icons.stream().map(VertxMcpServerEngine::mapIcon).toList();
    }

    private static McpSchema.Icon mapIcon(McpServerIcon icon) {
        McpSchema.Icon.Builder builder = McpSchema.Icon.builder(icon.src());
        if (icon.mimeType() != null && !icon.mimeType().isBlank()) {
            builder.mimeType(icon.mimeType());
        }
        if (icon.sizes() != null && !icon.sizes().isEmpty()) {
            builder.sizes(icon.sizes());
        }
        if (icon.theme() != null && !icon.theme().isBlank()) {
            builder.theme(icon.theme());
        }
        return builder.build();
    }

    private VertxPlatformHttpRouter lookupRouter() {
        boolean mainTarget = VertxPlatformHttpRouter.SERVER_TYPE_SERVER.equals(targetServerType);
        Set<VertxPlatformHttpRouter> routers = camelContext.getRegistry().findByType(VertxPlatformHttpRouter.class);
        VertxPlatformHttpRouter router = routers.stream()
                .filter(r -> targetServerType.equals(r.getServerType()))
                .findFirst()
                // a bare VertxPlatformHttpServer may carry no server type; only the default target may fall
                // back to it — an explicit management target must never silently use the public server
                .orElseGet(() -> mainTarget && routers.size() == 1 ? routers.iterator().next() : null);
        if (router == null) {
            if (mainTarget) {
                throw new IllegalStateException(
                        "The MCP server requires the Vert.x platform HTTP server. Enable the Camel main HTTP server "
                                                + "(camel.server.enabled=true with camel-platform-http-main on the classpath) "
                                                + "or add a VertxPlatformHttpServer service to the CamelContext.");
            }
            throw new IllegalStateException(
                    "The MCP server requires the Vert.x platform HTTP server with server type '" + targetServerType
                                            + "' but no such router was found in the registry. Enable the Camel management HTTP server "
                                            + "(camel.management.enabled=true with camel-platform-http-main on the classpath).");
        }
        return router;
    }
}
