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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.CamelContext;
import org.apache.camel.component.ai.tool.AiToolAnnotations;
import org.apache.camel.component.mcp.server.McpPromptSdkSupport;
import org.apache.camel.component.mcp.server.McpResourceReadResult;
import org.apache.camel.component.mcp.server.McpServerEngine;
import org.apache.camel.component.mcp.server.McpServerInfo;
import org.apache.camel.component.mcp.server.McpServerPrompt;
import org.apache.camel.component.mcp.server.McpServerResource;
import org.apache.camel.component.mcp.server.McpServerResourceTemplate;
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
    private PrintStream originalOut;
    private final List<McpServerFeatures.SyncToolSpecification> pendingTools = new ArrayList<>();
    private final List<McpServerFeatures.SyncResourceSpecification> pendingResources = new ArrayList<>();
    private final List<McpServerFeatures.SyncResourceTemplateSpecification> pendingResourceTemplates = new ArrayList<>();
    private final List<McpServerFeatures.SyncPromptSpecification> pendingPrompts = new ArrayList<>();

    // Package-private to allow tests to verify pending prompt lifecycle.
    int pendingPromptCount() {
        return pendingPrompts.size();
    }

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
     * The JSON mapper of the MCP SDK, found with the service loader of the application class loader of Camel.
     * <p>
     * The MCP SDK finds the mapper with the thread context class loader. Camel CLI (camel run) loads the downloaded
     * jars, such as mcp-json-jackson3, in the application context class loader of CamelContext, which is the thread
     * context class loader while CamelContext starts, but not while Camel Main configures it and initializes this
     * engine.
     */
    private McpJsonMapper jsonMapper() {
        if (jsonMapper == null) {
            ClassLoader cl = camelContext != null ? camelContext.getApplicationContextClassLoader() : null;
            if (cl == null) {
                cl = StdioMcpServerEngine.class.getClassLoader();
            }
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(cl);
                jsonMapper = McpJsonDefaults.getMapper();
            } finally {
                Thread.currentThread().setContextClassLoader(tccl);
            }
        }
        return jsonMapper;
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
        McpJsonMapper mapper = jsonMapper();
        if (inputStream != null && outputStream != null) {
            transport = new StdioServerTransportProvider(mapper, inputStream, outputStream);
        } else {
            // stdout carries the MCP protocol only: keep it for the transport, and send anything else written to
            // System.out (a stream:out endpoint, a println in a bean, a banner of a library) to stderr
            originalOut = System.out;
            System.setOut(System.err);
            transport = new StdioServerTransportProvider(mapper, new StopOnEndOfInput(System.in), originalOut);
        }
        server = McpServer.sync(transport)
                .serverInfo(info.serverName(), info.version())
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).resources(false, true).prompts(true).build())
                .immediateExecution(true)
                .build();
        for (McpServerFeatures.SyncToolSpecification spec : pendingTools) {
            addTool(spec);
        }
        pendingTools.clear();
        for (McpServerFeatures.SyncResourceSpecification spec : pendingResources) {
            addResource(spec);
        }
        pendingResources.clear();
        for (McpServerFeatures.SyncResourceTemplateSpecification spec : pendingResourceTemplates) {
            addResourceTemplate(spec);
        }
        pendingResourceTemplates.clear();
        for (McpServerFeatures.SyncPromptSpecification spec : pendingPrompts) {
            addPrompt(spec);
        }
        pendingPrompts.clear();
        LOG.info("MCP server '{}' serving ai-tool, ai-resource and ai-prompt routes over stdio", info.serverName());
    }

    @Override
    protected void doStop() throws Exception {
        if (server != null) {
            // closeGracefully() shuts down the MCP sync server and its stdio transport provider
            // (including custom stdin/stdout streams when setTransportStreams was used).
            server.closeGracefully();
            server = null;
        }
        if (originalOut != null) {
            System.setOut(originalOut);
            originalOut = null;
        }
        pendingTools.clear();
        pendingResources.clear();
        pendingResourceTemplates.clear();
        pendingPrompts.clear();
        transport = null;
    }

    private void onEndOfInput() {
        if (camelContext == null || isStoppingOrStopped()) {
            return;
        }
        // the client closed stdin: the MCP stdio transport expects the server to exit then, so stop Camel (which
        // also ends Camel Main), from another thread as this is the thread that reads stdin
        LOG.info("MCP client closed stdin, stopping {}", camelContext.getName());
        Thread thread = new Thread(() -> {
            try {
                camelContext.stop();
            } catch (Exception e) {
                LOG.warn("Failed to stop {} after the MCP client closed stdin: {}", camelContext.getName(), e.getMessage());
            }
        }, "CamelMcpStdioShutdown");
        thread.start();
    }

    /**
     * Calls {@link #onEndOfInput()} when the input reaches its end.
     */
    private final class StopOnEndOfInput extends FilterInputStream {

        private final AtomicBoolean ended = new AtomicBoolean();

        StopOnEndOfInput(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            return checkEnd(super.read());
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return checkEnd(super.read(b, off, len));
        }

        private int checkEnd(int n) {
            if (n == -1 && ended.compareAndSet(false, true)) {
                onEndOfInput();
            }
            return n;
        }
    }

    @Override
    public void toolAdded(McpServerTool tool) {
        McpServerFeatures.SyncToolSpecification spec = buildToolSpecification(tool);
        if (server == null) {
            pendingTools.add(spec);
            LOG.debug("MCP tool queued until stdio server starts: {}", tool.name());
            return;
        }
        addTool(spec);
        LOG.debug("MCP tool added: {}", tool.name());
    }

    private void addTool(McpServerFeatures.SyncToolSpecification spec) {
        try {
            server.addTool(spec);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // an invalid tool (such as its input schema)
            throw e;
        } catch (RuntimeException e) {
            // the tool is registered before the connected client is notified that the tool list changed, and that
            // notification fails when stdin is closed (the client went away); the tool must not fail the route
            LOG.warn("MCP tool {} added, but the client could not be notified: {}", spec.tool().name(), e.getMessage());
        }
    }

    @Override
    public void resourceAdded(McpServerResource resource) {
        McpSchema.Resource.Builder resourceBuilder = McpSchema.Resource.builder()
                .uri(resource.uri())
                .name(resource.name())
                .description(resource.description())
                .mimeType(resource.mimeType());
        if (resource.title() != null && !resource.title().isBlank()) {
            resourceBuilder.title(resource.title());
        }
        McpSchema.Resource mcpResource = resourceBuilder.build();
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
                        contents = new McpSchema.TextResourceContents(resource.uri(), resource.mimeType(), result.text());
                    }
                    return new McpSchema.ReadResourceResult(List.of(contents));
                });
        if (server == null) {
            pendingResources.add(spec);
            LOG.debug("MCP resource queued until stdio server starts: {}", resource.uri());
            return;
        }
        addResource(spec);
    }

    private void addResource(McpServerFeatures.SyncResourceSpecification spec) {
        try {
            server.addResource(spec);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (RuntimeException e) {
            LOG.warn("MCP resource {} added, but the client could not be notified: {}",
                    spec.resource().uri(), e.getMessage());
        }
    }

    @Override
    public void resourceRemoved(String resourceUri) {
        pendingResources.removeIf(spec -> resourceUri.equals(spec.resource().uri()));
        if (server == null) {
            return;
        }
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

    @Override
    public void resourceTemplateAdded(McpServerResourceTemplate resourceTemplate) {
        McpSchema.ResourceTemplate.Builder builder = McpSchema.ResourceTemplate
                .builder(resourceTemplate.uriTemplate(), resourceTemplate.name())
                .description(resourceTemplate.description())
                .mimeType(resourceTemplate.mimeType());
        if (resourceTemplate.title() != null && !resourceTemplate.title().isBlank()) {
            builder.title(resourceTemplate.title());
        }
        McpServerFeatures.SyncResourceTemplateSpecification spec = new McpServerFeatures.SyncResourceTemplateSpecification(
                builder.build(),
                (exchange, request) -> {
                    McpResourceReadResult result = resourceTemplate.handler().read(request.uri());
                    if (result.isError()) {
                        throw McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
                                .message(result.errorMessage())
                                .build();
                    }
                    McpSchema.ResourceContents contents;
                    if (result.blob() != null) {
                        contents = new McpSchema.BlobResourceContents(
                                request.uri(), resourceTemplate.mimeType(),
                                Base64.getEncoder().encodeToString(result.blob()));
                    } else {
                        contents = new McpSchema.TextResourceContents(
                                request.uri(), resourceTemplate.mimeType(),
                                result.text());
                    }
                    return new McpSchema.ReadResourceResult(List.of(contents));
                });
        if (server == null) {
            pendingResourceTemplates.add(spec);
            LOG.debug("MCP resource template queued until stdio server starts: {}", resourceTemplate.uriTemplate());
            return;
        }
        addResourceTemplate(spec);
    }

    private void addResourceTemplate(McpServerFeatures.SyncResourceTemplateSpecification spec) {
        try {
            server.addResourceTemplate(spec);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (RuntimeException e) {
            LOG.warn("MCP resource template {} added, but the client could not be notified: {}",
                    spec.resourceTemplate().uriTemplate(), e.getMessage());
        }
    }

    @Override
    public void resourceTemplateRemoved(String uriTemplate) {
        pendingResourceTemplates.removeIf(spec -> uriTemplate.equals(spec.resourceTemplate().uriTemplate()));
        if (server == null) {
            return;
        }
        try {
            server.removeResourceTemplate(uriTemplate);
            // MCP Java SDK 2.1.0 does not send resources/list_changed when a template is removed.
            // Remove this workaround when the SDK's removeResourceTemplate() sends that notification itself.
            server.notifyResourcesListChanged();
            LOG.debug("MCP resource template removed: {}", uriTemplate);
        } catch (Exception e) {
            LOG.debug("Failed to remove MCP resource template {} or notify client: {}",
                    uriTemplate, e.getMessage());
        }
    }

    @Override
    public boolean supportsResourceTemplates() {
        return true;
    }

    int pendingResourceCount() {
        return pendingResources.size();
    }

    int pendingResourceTemplateCount() {
        return pendingResourceTemplates.size();
    }

    @Override
    public void promptAdded(McpServerPrompt prompt) {
        McpServerFeatures.SyncPromptSpecification spec = McpPromptSdkSupport.buildSpecification(prompt, jsonMapper);
        if (server == null) {
            pendingPrompts.add(spec);
            LOG.debug("MCP prompt queued until stdio server starts: {}", prompt.name());
            return;
        }
        addPrompt(spec);
    }

    private void addPrompt(McpServerFeatures.SyncPromptSpecification spec) {
        try {
            server.addPrompt(spec);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (RuntimeException e) {
            LOG.warn("MCP prompt {} added, but the client could not be notified: {}",
                    spec.prompt().name(), e.getMessage());
        }
    }

    @Override
    public void promptRemoved(String promptName) {
        for (Iterator<McpServerFeatures.SyncPromptSpecification> iterator = pendingPrompts.iterator();
             iterator.hasNext();) {
            if (promptName.equals(iterator.next().prompt().name())) {
                iterator.remove();
            }
        }
        if (server == null) {
            return;
        }
        try {
            server.removePrompt(promptName);
            LOG.debug("MCP prompt removed: {}", promptName);
        } catch (Exception e) {
            LOG.debug("Failed to remove MCP prompt {}: {}", promptName, e.getMessage());
        }
    }

    @Override
    public boolean supportsPrompts() {
        return true;
    }

    @Override
    public void toolRemoved(String toolName) {
        removePendingTool(toolName);
        if (server == null) {
            return;
        }
        try {
            server.removeTool(toolName);
            LOG.debug("MCP tool removed: {}", toolName);
        } catch (Exception e) {
            LOG.debug("Failed to remove MCP tool {}: {}", toolName, e.getMessage());
        }
    }

    private void removePendingTool(String toolName) {
        for (Iterator<McpServerFeatures.SyncToolSpecification> iterator = pendingTools.iterator(); iterator.hasNext();) {
            if (toolName.equals(iterator.next().tool().name())) {
                iterator.remove();
            }
        }
    }

    private McpServerFeatures.SyncToolSpecification buildToolSpecification(McpServerTool tool) {
        McpSchema.Tool mcpTool = buildMcpTool(tool);
        return McpServerFeatures.SyncToolSpecification.builder()
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
    }

    private McpSchema.Tool buildMcpTool(McpServerTool tool) {
        String schema = tool.inputSchemaJson() != null ? tool.inputSchemaJson() : EMPTY_OBJECT_SCHEMA;
        McpSchema.Tool.Builder builder = McpSchema.Tool.builder(tool.name(), jsonMapper(), schema)
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
