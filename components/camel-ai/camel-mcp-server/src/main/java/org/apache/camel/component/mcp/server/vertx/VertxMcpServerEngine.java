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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.modern.server.feature.McpChangeFeed;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.vertx.ext.auth.User;
import org.apache.camel.CamelContext;
import org.apache.camel.component.ai.tool.AiToolAnnotations;
import org.apache.camel.component.mcp.server.McpPromptSdkSupport;
import org.apache.camel.component.mcp.server.McpProtocolEra;
import org.apache.camel.component.mcp.server.McpResourceReadResult;
import org.apache.camel.component.mcp.server.McpServerConstants;
import org.apache.camel.component.mcp.server.McpServerEngine;
import org.apache.camel.component.mcp.server.McpServerIcon;
import org.apache.camel.component.mcp.server.McpServerInfo;
import org.apache.camel.component.mcp.server.McpServerPrompt;
import org.apache.camel.component.mcp.server.McpServerRefusedException;
import org.apache.camel.component.mcp.server.McpServerResource;
import org.apache.camel.component.mcp.server.McpServerResourceTemplate;
import org.apache.camel.component.mcp.server.McpServerTool;
import org.apache.camel.component.mcp.server.McpToolCallContext;
import org.apache.camel.component.mcp.server.McpToolCallResult;
import org.apache.camel.component.platform.http.PlatformHttpComponent;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpRouter;
import org.apache.camel.http.base.OAuthHttpSecuritySupport;
import org.apache.camel.spi.OAuthTokenValidationConfig;
import org.apache.camel.spi.OAuthTokenValidationConfigResolver;
import org.apache.camel.spi.OAuthTokenValidationResult;
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
 * <p>
 * Every request on the MCP endpoint that carries an {@code Origin} header must come from an allowed origin
 * ({@link McpServerInfo#allowedOrigins()}, loopback origins by default), or it is rejected with HTTP 403 before it
 * reaches the MCP server. Requests without an {@code Origin} header (MCP clients running as a process) are served. When
 * allowed hosts are configured ({@link McpServerInfo#allowedHosts()}), every request must also carry a {@code Host}
 * header from that list, or it is rejected with HTTP 421 in the same way.
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

    /**
     * Key under which {@link VertxMcpStreamableServerTransportProvider} stashes the authenticated caller principal in
     * the MCP transport context, so the tool, resource and prompt handlers read it back via
     * {@code exchange.transportContext()}.
     */
    static final String TRANSPORT_PRINCIPAL_KEY = "CamelMcpTransportPrincipal";
    static final String TRANSPORT_OAUTH_RESULT_KEY = "CamelMcpOAuthTokenValidationResult";

    private CamelContext camelContext;
    private McpServerInfo info;
    private McpJsonMapper jsonMapper;
    private VertxMcpStreamableServerTransportProvider transport;
    // the servers this engine runs, depending on the protocol era and the stateless option; null when not served
    private McpSyncServer server;
    private McpStatelessSyncServer statelessServer;
    private McpModernCatalog modernCatalog;
    private VertxMcpModernTransport modernTransport;
    private Duration modernKeepAliveInterval = VertxMcpModernTransport.DEFAULT_KEEP_ALIVE_INTERVAL;
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
    public boolean supportsOAuthConfiguration() {
        return true;
    }

    @Override
    public boolean supportsProtocolEraConfiguration() {
        return true;
    }

    /**
     * The interval of the keep-alive comments on a stream answering a request of protocol version 2026-07-28, such as a
     * {@code subscriptions/listen} stream; {@code null} or zero for none. Defaults to 30 seconds.
     */
    void setModernKeepAliveInterval(Duration modernKeepAliveInterval) {
        this.modernKeepAliveInterval = modernKeepAliveInterval;
    }

    @Override
    protected void doStart() throws Exception {
        VertxPlatformHttpRouter router = lookupRouter();
        validateOAuthResourceMetadataPath(router);
        jsonMapper = McpJsonDefaults.getMapper();
        OAuthHttpSecuritySupport oauthSecurity
                = OAuthHttpSecuritySupport.create(camelContext, info.oauthProfile());
        OAuthTokenValidationConfig oauthConfig = info.oauthProfile() != null && !info.oauthProfile().isBlank()
                ? OAuthTokenValidationConfigResolver.resolveProfileConfig(camelContext, info.oauthProfile())
                : null;
        String authorizationServerIssuer = oauthConfig != null ? oauthConfig.getExpectedIssuer() : null;
        boolean allowInsecureAuthorizationServer = oauthConfig != null && oauthConfig.isAllowInsecureHttp();
        transport = new VertxMcpStreamableServerTransportProvider(
                jsonMapper, info.path(),
                info.sessionKeepAliveIntervalMs(), info.sessionIdleTtlMs(), info.allowedOrigins(),
                camelContext, oauthSecurity, info.requiredScopes(), info.resourceUri(),
                authorizationServerIssuer, allowInsecureAuthorizationServer, info.allowedHosts());
        McpProtocolEra era = info.protocolEra();
        if (info.sessions()) {
            McpServer.SyncSpecification<?> specification = McpServer.sync(transport)
                    .serverInfo(buildServerInfo(info))
                    // resources(subscribe, listChanged): per-resource subscriptions are not offered, but clients are
                    // notified when ai-resource routes start or stop
                    .capabilities(McpSchema.ServerCapabilities.builder().tools(true).resources(false, true).prompts(true)
                            .build())
                    .immediateExecution(true);
            if (info.instructions() != null && !info.instructions().isBlank()) {
                specification.instructions(info.instructions());
            }
            server = specification.build();
        }
        VertxMcpStatelessServerTransport statelessTransport = null;
        if (era.servesLegacy() && info.stateless()) {
            statelessTransport = new VertxMcpStatelessServerTransport(jsonMapper);
            McpServer.StatelessSyncSpecification specification = McpServer.sync(statelessTransport)
                    .serverInfo(buildServerInfo(info))
                    // without a session the server cannot notify clients, so no list advertises listChanged
                    .capabilities(McpSchema.ServerCapabilities.builder().tools(false).resources(false, false)
                            .prompts(false).build())
                    .immediateExecution(true);
            if (info.instructions() != null && !info.instructions().isBlank()) {
                specification.instructions(info.instructions());
            }
            statelessServer = specification.build();
        }
        if (era.servesModern()) {
            McpChangeFeed changes = new McpChangeFeed();
            modernCatalog = new McpModernCatalog(jsonMapper, changes);
            modernTransport = VertxMcpModernTransport.create(info, modernCatalog, changes, jsonMapper,
                    modernKeepAliveInterval);
        }
        transport.serveProtocols(era, info.sessions(), statelessTransport, modernTransport);
        // register routes only once the servers are built (the session server sets the session factory)
        transport.registerRoutes(router);

        PlatformHttpComponent platformHttpComponent
                = (PlatformHttpComponent) camelContext.hasComponent("platform-http");
        if (platformHttpComponent != null) {
            platformHttpComponent.addHttpEndpoint(info.path(), info.sessions() ? "GET,POST,DELETE" : "POST",
                    APPLICATION_JSON, "application/json,text/event-stream", null);
        }
        LOG.info("MCP server '{}' serving tools, resources and prompts on path {} (protocol era {}{})",
                info.serverName(), info.path(), era.value(), info.stateless() ? ", stateless" : "");
    }

    /**
     * RFC 9728 protected-resource metadata for a path-based resource must be discoverable from the origin root. The
     * selected platform-http router is mounted below the server's context path, so a non-root context path would also
     * prefix the well-known metadata URL and make discovery fail.
     */
    private void validateOAuthResourceMetadataPath(VertxPlatformHttpRouter router) {
        if (info.oauthProfile() == null || info.oauthProfile().isBlank()) {
            return;
        }
        String contextPath = router.getServer().getPath();
        if (!"/".equals(contextPath)) {
            throw new IllegalArgumentException(
                    "OAuth-protected MCP requires camel.server.path='/' because RFC 9728 protected-resource metadata "
                                               + "must be available at the origin root; configure the public path prefix "
                                               + "with camel.server.mcp-path instead");
        }
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
        if (statelessServer != null) {
            statelessServer.closeGracefully();
            statelessServer = null;
        }
        if (modernTransport != null) {
            modernTransport.closeGracefully();
            modernTransport = null;
            modernCatalog = null;
        }
        PlatformHttpComponent platformHttpComponent
                = (PlatformHttpComponent) camelContext.hasComponent("platform-http");
        if (platformHttpComponent != null && info != null) {
            platformHttpComponent.removeHttpEndpoint(info.path(), null);
        }
        transport = null;
    }

    @Override
    public void toolAdded(McpServerTool tool) {
        McpSchema.Tool mcpTool = buildValidMcpTool(tool);
        if (server != null) {
            server.addTool(McpServerFeatures.SyncToolSpecification.builder()
                    .tool(mcpTool)
                    .callHandler((exchange, request) -> callTool(tool, exchange.transportContext(), request))
                    .build());
        }
        if (statelessServer != null) {
            statelessServer.addTool(McpStatelessServerFeatures.SyncToolSpecification.builder()
                    .tool(mcpTool)
                    .callHandler((transportContext, request) -> callTool(tool, transportContext, request))
                    .build());
        }
        if (modernCatalog != null) {
            modernCatalog.addTool(tool);
        }
        LOG.debug("MCP tool added: {}", tool.name());
    }

    private static McpSchema.CallToolResult callTool(
            McpServerTool tool, McpTransportContext transportContext, McpSchema.CallToolRequest request) {
        Map<String, Object> arguments = request.arguments() != null ? request.arguments() : Map.of();
        McpToolCallResult result = tool.handler().call(arguments, toToolCallContext(transportContext));
        McpSchema.CallToolResult.Builder builder = McpSchema.CallToolResult.builder()
                .addTextContent(result.text())
                .isError(result.isError());
        if (result.structuredContent() != null) {
            builder.structuredContent(result.structuredContent());
        }
        if (result.meta() != null) {
            builder.meta(result.meta());
        }
        return builder.build();
    }

    /**
     * The tool-call context of the caller whose principal and OAuth validation result the transport stored in the MCP
     * transport context.
     */
    static McpToolCallContext toToolCallContext(McpTransportContext transportContext) {
        if (transportContext == null) {
            return McpToolCallContext.EMPTY;
        }
        Object principal = transportContext.get(TRANSPORT_PRINCIPAL_KEY);
        OAuthTokenValidationResult oauthResult
                = transportContext.get(TRANSPORT_OAUTH_RESULT_KEY) instanceof OAuthTokenValidationResult value
                        ? value : null;
        return toToolCallContext(principal, oauthResult);
    }

    /**
     * Builds the tool-call context from the transport principal, deriving a runtime-neutral name and roles when it is a
     * Vert.x {@link User} so a tool route or its authorization policy can authorize on a stable identity without
     * depending on Vert.x. A non-{@code User} principal is still carried as the raw object, and {@code null} yields the
     * empty context.
     */
    private static McpToolCallContext toToolCallContext(
            Object principal, OAuthTokenValidationResult oauthResult) {
        String oauthSubject = oauthResult != null ? oauthResult.getSubject() : null;
        List<String> oauthScopes = oauthResult != null ? oauthResult.getScopes() : null;
        Map<String, Object> oauthClaims = oauthResult != null ? oauthResult.getClaims() : null;

        if (principal instanceof User user) {
            String userName = VertxMcpPrincipal.name(user);
            List<String> roles = VertxMcpPrincipal.roles(user);
            if ((roles == null || roles.isEmpty()) && oauthResult != null) {
                roles = oauthRoles(oauthResult);
            }
            return new McpToolCallContext(
                    user,
                    userName != null ? userName : oauthSubject,
                    roles,
                    oauthSubject,
                    oauthScopes,
                    oauthClaims,
                    oauthResult);
        }

        if (principal != null || oauthResult != null) {
            return new McpToolCallContext(
                    principal != null ? principal : oauthResult,
                    oauthSubject,
                    oauthResult != null ? oauthRoles(oauthResult) : null,
                    oauthSubject,
                    oauthScopes,
                    oauthClaims,
                    oauthResult);
        }

        return McpToolCallContext.EMPTY;
    }

    /**
     * Extracts role names from common OAuth claim shapes without assuming claim types. The scopes remain separate from
     * roles in McpToolCallContext.
     */
    private static List<String> oauthRoles(OAuthTokenValidationResult result) {
        Map<String, Object> claims = result.getClaims();
        Object roleValue = claims.get("roles");

        if (!(roleValue instanceof Collection<?>)) {
            Object realmAccess = claims.get("realm_access");
            if (realmAccess instanceof Map<?, ?> realmMap) {
                roleValue = realmMap.get("roles");
            }
        }

        if (!(roleValue instanceof Collection<?>)) {
            roleValue = claims.get("groups");
        }

        if (!(roleValue instanceof Collection<?> values)) {
            return null;
        }

        List<String> roles = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof String role && !role.isBlank()) {
                roles.add(role);
            }
        }
        return roles.isEmpty() ? null : List.copyOf(roles);
    }

    @Override
    public void toolRemoved(String toolName) {
        try {
            if (server != null) {
                server.removeTool(toolName);
            }
            if (statelessServer != null) {
                statelessServer.removeTool(toolName);
            }
            if (modernCatalog != null) {
                modernCatalog.removeTool(toolName);
            }
            LOG.debug("MCP tool removed: {}", toolName);
        } catch (Exception e) {
            LOG.debug("Failed to remove MCP tool {}: {}", toolName, e.getMessage());
        }
    }

    @Override
    public void resourceAdded(McpServerResource resource) {
        McpSchema.Resource mcpResource = buildMcpResource(resource);
        if (server != null) {
            server.addResource(new McpServerFeatures.SyncResourceSpecification(
                    mcpResource,
                    (exchange, request) -> readResult(resource.uri(), resource.mimeType(),
                            resource.handler().read(toToolCallContext(exchange.transportContext())))));
        }
        if (statelessServer != null) {
            statelessServer.addResource(new McpStatelessServerFeatures.SyncResourceSpecification(
                    mcpResource,
                    (transportContext, request) -> readResult(resource.uri(), resource.mimeType(),
                            resource.handler().read(toToolCallContext(transportContext)))));
        }
        if (modernCatalog != null) {
            modernCatalog.addResource(resource);
        }
        LOG.debug("MCP resource added: {}", resource.uri());
    }

    private static McpSchema.ReadResourceResult readResult(String uri, String mimeType, McpResourceReadResult result) {
        if (result.isError()) {
            throw McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
                    .message(result.errorMessage())
                    .build();
        }
        McpSchema.ResourceContents contents;
        if (result.blob() != null) {
            contents = McpSchema.BlobResourceContents.builder(uri, Base64.getEncoder().encodeToString(result.blob()))
                    .mimeType(mimeType)
                    .build();
        } else {
            contents = McpSchema.TextResourceContents.builder(uri, result.text()).mimeType(mimeType).build();
        }
        return McpSchema.ReadResourceResult.builder(List.of(contents)).build();
    }

    @Override
    public void resourceRemoved(String resourceUri) {
        try {
            if (server != null) {
                server.removeResource(resourceUri);
            }
            if (statelessServer != null) {
                statelessServer.removeResource(resourceUri);
            }
            if (modernCatalog != null) {
                modernCatalog.removeResource(resourceUri);
            }
            LOG.debug("MCP resource removed: {}", resourceUri);
        } catch (Exception e) {
            LOG.debug("Failed to remove MCP resource {}: {}", resourceUri, e.getMessage());
        }
    }

    @Override
    public boolean supportsResources() {
        return true;
    }

    @Override
    public void resourceTemplateAdded(McpServerResourceTemplate resourceTemplate) {
        McpSchema.ResourceTemplate.Builder builder = McpSchema.ResourceTemplate
                .builder(resourceTemplate.uriTemplate(), resourceTemplate.name())
                .description(resourceTemplate.description())
                .mimeType(resourceTemplate.mimeType());
        if (resourceTemplate.title() != null && !resourceTemplate.title().isBlank()) {
            builder.title(resourceTemplate.title());
        }
        McpSchema.ResourceTemplate mcpTemplate = builder.build();
        if (server != null) {
            server.addResourceTemplate(new McpServerFeatures.SyncResourceTemplateSpecification(
                    mcpTemplate,
                    (exchange, request) -> readResult(request.uri(), resourceTemplate.mimeType(),
                            resourceTemplate.handler().read(request.uri(),
                                    toToolCallContext(exchange.transportContext())))));
        }
        if (statelessServer != null) {
            statelessServer.addResourceTemplate(new McpStatelessServerFeatures.SyncResourceTemplateSpecification(
                    mcpTemplate,
                    (transportContext, request) -> readResult(request.uri(), resourceTemplate.mimeType(),
                            resourceTemplate.handler().read(request.uri(), toToolCallContext(transportContext)))));
        }
        if (modernCatalog != null) {
            modernCatalog.addResourceTemplate(resourceTemplate);
        }
        LOG.debug("MCP resource template added: {}", resourceTemplate.uriTemplate());
    }

    @Override
    public void resourceTemplateRemoved(String uriTemplate) {
        try {
            if (server != null) {
                // the SDK notifies resources/list_changed itself
                server.removeResourceTemplate(uriTemplate);
            }
            if (statelessServer != null) {
                statelessServer.removeResourceTemplate(uriTemplate);
            }
            if (modernCatalog != null) {
                modernCatalog.removeResourceTemplate(uriTemplate);
            }
            LOG.debug("MCP resource template removed: {}", uriTemplate);
        } catch (Exception e) {
            LOG.debug("Failed to remove MCP resource template {}: {}", uriTemplate, e.getMessage());
        }
    }

    @Override
    public boolean supportsResourceTemplates() {
        return true;
    }

    @Override
    public void promptAdded(McpServerPrompt prompt) {
        if (server != null) {
            server.addPrompt(McpPromptSdkSupport.buildSpecification(
                    prompt, jsonMapper, VertxMcpServerEngine::toToolCallContext));
        }
        if (statelessServer != null) {
            statelessServer.addPrompt(McpPromptSdkSupport.buildStatelessSpecification(
                    prompt, jsonMapper, VertxMcpServerEngine::toToolCallContext));
        }
        if (modernCatalog != null) {
            modernCatalog.addPrompt(prompt);
        }
        LOG.debug("MCP prompt added: {}", prompt.name());
    }

    @Override
    public void promptRemoved(String promptName) {
        try {
            if (server != null) {
                server.removePrompt(promptName);
            }
            if (statelessServer != null) {
                statelessServer.removePrompt(promptName);
            }
            if (modernCatalog != null) {
                modernCatalog.removePrompt(promptName);
            }
            LOG.debug("MCP prompt removed: {}", promptName);
        } catch (Exception e) {
            LOG.debug("Failed to remove MCP prompt {}: {}", promptName, e.getMessage());
        }
    }

    @Override
    public boolean supportsPrompts() {
        return true;
    }

    int sessionCount() {
        return transport != null ? transport.sessionCount() : 0;
    }

    private static McpSchema.Resource buildMcpResource(McpServerResource resource) {
        McpSchema.Resource.Builder builder = McpSchema.Resource.builder(resource.uri(), resource.name())
                .description(resource.description())
                .mimeType(resource.mimeType());
        if (resource.title() != null && !resource.title().isBlank()) {
            builder.title(resource.title());
        }
        return builder.build();
    }

    /**
     * Builds the tool, refusing it when its input or output schema is not a valid JSON Schema document. The MCP SDK
     * checks the schemas when a tool is added to a server of the legacy era, but not to the catalog of protocol version
     * 2026-07-28: they are checked once here, with the same validator, before the tool is added to any server, so every
     * era refuses the tool alike and none serves it alone.
     */
    private McpSchema.Tool buildValidMcpTool(McpServerTool tool) {
        try {
            McpSchema.Tool mcpTool = buildMcpTool(tool);
            JsonSchemaValidator validator = McpJsonDefaults.getSchemaValidator();
            validator.assertConforms("Tool '" + tool.name() + "' inputSchema", mcpTool.inputSchema());
            validator.assertConforms("Tool '" + tool.name() + "' outputSchema", mcpTool.outputSchema());
            return mcpTool;
        } catch (IllegalArgumentException e) {
            throw new McpServerRefusedException(
                    "MCP tool '" + tool.name() + "' has an invalid input or output schema: " + e.getMessage(), e);
        }
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
