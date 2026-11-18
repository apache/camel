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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.McpSchema.ErrorCodes;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import io.modelcontextprotocol.modern.server.feature.McpChangeBroadcaster;
import io.modelcontextprotocol.modern.server.feature.McpSyncPrompt;
import io.modelcontextprotocol.modern.server.feature.McpSyncPromptRepository;
import io.modelcontextprotocol.modern.server.feature.McpSyncResource;
import io.modelcontextprotocol.modern.server.feature.McpSyncResourceRepository;
import io.modelcontextprotocol.modern.server.feature.McpSyncResourceTemplate;
import io.modelcontextprotocol.modern.server.feature.McpSyncTool;
import io.modelcontextprotocol.modern.server.feature.McpSyncToolRepository;
import io.modelcontextprotocol.modern.server.feature.PromptsPage;
import io.modelcontextprotocol.modern.server.feature.ResourceTemplatesPage;
import io.modelcontextprotocol.modern.server.feature.ResourcesPage;
import io.modelcontextprotocol.modern.server.feature.ServerChange;
import io.modelcontextprotocol.modern.server.feature.ToolsPage;
import io.modelcontextprotocol.util.DefaultMcpUriTemplateManagerFactory;
import io.modelcontextprotocol.util.McpUriTemplateManager;
import org.apache.camel.component.ai.tool.AiToolAnnotations;
import org.apache.camel.component.mcp.server.McpPromptMessage;
import org.apache.camel.component.mcp.server.McpPromptResult;
import org.apache.camel.component.mcp.server.McpResourceReadResult;
import org.apache.camel.component.mcp.server.McpServerPrompt;
import org.apache.camel.component.mcp.server.McpServerResource;
import org.apache.camel.component.mcp.server.McpServerResourceTemplate;
import org.apache.camel.component.mcp.server.McpServerTool;
import org.apache.camel.component.mcp.server.McpToolCallResult;

/**
 * The tools, resources, resource templates and prompts served to clients of MCP protocol version 2026-07-28: the
 * repositories of the MCP SDK's modern server, filled as {@code ai-tool}, {@code ai-resource} and {@code ai-prompt}
 * routes start and stop. Every change is broadcast, so a client listening with {@code subscriptions/listen} is told
 * that a list changed.
 */
final class McpModernCatalog {

    private static final String EMPTY_OBJECT_SCHEMA = """
            {
              "type": "object",
              "properties": {},
              "additionalProperties": false
            }
            """;

    private final McpJsonMapper jsonMapper;
    private final McpChangeBroadcaster changes;
    private final Map<String, McpSyncTool> tools = new ConcurrentHashMap<>();
    private final Map<String, Entry<McpServerResource, McpSchema.Resource>> resources = new ConcurrentHashMap<>();
    private final Map<String, TemplateEntry> templates = new ConcurrentHashMap<>();
    private final Map<String, McpSyncPrompt> prompts = new ConcurrentHashMap<>();
    private final DefaultMcpUriTemplateManagerFactory uriTemplates = new DefaultMcpUriTemplateManagerFactory();

    private record Entry<T, D>(T source, D definition) {
    }

    private record TemplateEntry(
            McpServerResourceTemplate source, McpSyncResourceTemplate definition, McpUriTemplateManager matcher) {
    }

    McpModernCatalog(McpJsonMapper jsonMapper, McpChangeBroadcaster changes) {
        this.jsonMapper = jsonMapper;
        this.changes = changes;
    }

    // ---- tools

    void addTool(McpServerTool tool) {
        tools.put(tool.name(), McpSyncTool.of(buildTool(tool), (ctx, request) -> {
            Map<String, Object> arguments = request.arguments() != null ? request.arguments() : Map.of();
            McpToolCallResult result
                    = tool.handler().call(arguments, VertxMcpServerEngine.toToolCallContext(ctx.transportContext()));
            McpSchema.CallToolResult.Builder builder = McpSchema.CallToolResult.builder()
                    .addContent(McpSchema.TextContent.builder(result.text()).build())
                    .isError(result.isError());
            if (result.structuredContent() != null) {
                builder.structuredContent(result.structuredContent());
            }
            return McpSyncResponse.result(builder.build());
        }));
        changes.broadcast(new ServerChange.ToolsListChanged());
    }

    void removeTool(String name) {
        if (tools.remove(name) != null) {
            changes.broadcast(new ServerChange.ToolsListChanged());
        }
    }

    McpSyncToolRepository toolRepository() {
        return new McpSyncToolRepository() {
            @Override
            public ToolsPage list(McpRequestContext ctx, String cursor) {
                return ToolsPage.of(tools.values().stream().map(McpSyncTool::definition).toList());
            }

            @Override
            public McpSyncTool find(McpRequestContext ctx, String name) {
                return tools.get(name);
            }
        };
    }

    private McpSchema.Tool buildTool(McpServerTool tool) {
        String schema = tool.inputSchemaJson() != null ? tool.inputSchemaJson() : EMPTY_OBJECT_SCHEMA;
        McpSchema.Tool.Builder builder = McpSchema.Tool.builder(tool.name(), jsonMapper, schema)
                .description(tool.description());
        if (tool.outputSchemaJson() != null && !tool.outputSchemaJson().isBlank()) {
            builder.outputSchema(jsonMapper, tool.outputSchemaJson());
        }
        AiToolAnnotations annotations = tool.annotations();
        if (annotations != null) {
            if (annotations.title() != null) {
                builder.title(annotations.title());
            }
            // returnDirect is not part of the 2026-07-28 tool annotations
            if (annotations.readOnlyHint() != null || annotations.destructiveHint() != null
                    || annotations.idempotentHint() != null || annotations.openWorldHint() != null) {
                builder.annotations(McpSchema.ToolAnnotations.builder()
                        .readOnlyHint(annotations.readOnlyHint())
                        .destructiveHint(annotations.destructiveHint())
                        .idempotentHint(annotations.idempotentHint())
                        .openWorldHint(annotations.openWorldHint())
                        .build());
            }
        }
        return builder.build();
    }

    // ---- resources and resource templates

    void addResource(McpServerResource resource) {
        McpSchema.Resource.Builder builder = McpSchema.Resource.builder(resource.uri(), resource.name())
                .description(resource.description())
                .mimeType(resource.mimeType());
        if (resource.title() != null && !resource.title().isBlank()) {
            builder.title(resource.title());
        }
        resources.put(resource.uri(), new Entry<>(resource, builder.build()));
        changes.broadcast(new ServerChange.ResourcesListChanged());
    }

    void removeResource(String uri) {
        if (resources.remove(uri) != null) {
            changes.broadcast(new ServerChange.ResourcesListChanged());
        }
    }

    void addResourceTemplate(McpServerResourceTemplate template) {
        McpSchema.ResourceTemplate.Builder builder = McpSchema.ResourceTemplate
                .builder(template.uriTemplate(), template.name())
                .description(template.description())
                .mimeType(template.mimeType());
        if (template.title() != null && !template.title().isBlank()) {
            builder.title(template.title());
        }
        templates.put(template.uriTemplate(), new TemplateEntry(
                template, McpSyncResourceTemplate.of(builder.build()), uriTemplates.create(template.uriTemplate())));
        changes.broadcast(new ServerChange.ResourcesListChanged());
    }

    void removeResourceTemplate(String uriTemplate) {
        if (templates.remove(uriTemplate) != null) {
            changes.broadcast(new ServerChange.ResourcesListChanged());
        }
    }

    McpSyncResourceRepository resourceRepository() {
        return new McpSyncResourceRepository() {
            @Override
            public ResourcesPage list(McpRequestContext ctx, String cursor) {
                return ResourcesPage.of(resources.values().stream().map(Entry::definition).toList());
            }

            @Override
            public ResourceTemplatesPage listTemplates(McpRequestContext ctx, String cursor) {
                return ResourceTemplatesPage.of(
                        templates.values().stream().map(entry -> entry.definition().definition()).toList());
            }

            @Override
            public McpSyncResource find(McpRequestContext ctx, String uri) {
                return findResource(uri);
            }

            @Override
            public McpSyncResourceTemplate findTemplate(McpRequestContext ctx, String uriTemplate) {
                TemplateEntry template = templates.get(uriTemplate);
                return template != null ? template.definition() : null;
            }
        };
    }

    /**
     * The resource with the URI, or else a resource read through the first template the URI matches.
     */
    private McpSyncResource findResource(String uri) {
        Entry<McpServerResource, McpSchema.Resource> resource = resources.get(uri);
        if (resource != null) {
            McpServerResource source = resource.source();
            return McpSyncResource.of((c, request) -> McpSyncResponse.result(
                    readResult(request.uri(), source.mimeType(), source.handler().read())));
        }
        for (TemplateEntry template : templates.values()) {
            if (template.matcher().matches(uri)) {
                McpServerResourceTemplate source = template.source();
                return McpSyncResource.of((c, request) -> McpSyncResponse.result(
                        readResult(request.uri(), source.mimeType(), source.handler().read(request.uri()))));
            }
        }
        return null;
    }

    private static McpSchema.ReadResourceResult readResult(String uri, String mimeType, McpResourceReadResult result) {
        if (result.isError()) {
            throw new McpException(ErrorCodes.INTERNAL_ERROR, result.errorMessage());
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

    // ---- prompts

    void addPrompt(McpServerPrompt prompt) {
        McpSchema.Prompt.Builder builder = McpSchema.Prompt.builder(prompt.name())
                .description(prompt.description())
                .arguments(prompt.arguments().stream()
                        .map(argument -> McpSchema.PromptArgument.builder(argument.name())
                                .description(argument.description())
                                .required(argument.required())
                                .build())
                        .toList());
        if (prompt.title() != null && !prompt.title().isBlank()) {
            builder.title(prompt.title());
        }
        prompts.put(prompt.name(), McpSyncPrompt.of(builder.build(), (ctx, request) -> {
            Map<String, String> arguments = request.arguments() != null ? request.arguments() : Map.of();
            McpPromptResult result = prompt.handler().get(arguments);
            if (result == null) {
                throw new McpException(ErrorCodes.INTERNAL_ERROR, "Prompt execution failed");
            }
            if (result.isError()) {
                throw new McpException(
                        result.argumentError() ? ErrorCodes.INVALID_PARAMS : ErrorCodes.INTERNAL_ERROR,
                        result.errorMessage());
            }
            List<McpSchema.PromptMessage> messages = result.messages().stream().map(this::toPromptMessage).toList();
            return McpSyncResponse.result(
                    McpSchema.GetPromptResult.builder(messages).description(result.description()).build());
        }));
        changes.broadcast(new ServerChange.PromptsListChanged());
    }

    void removePrompt(String name) {
        if (prompts.remove(name) != null) {
            changes.broadcast(new ServerChange.PromptsListChanged());
        }
    }

    McpSyncPromptRepository promptRepository() {
        return new McpSyncPromptRepository() {
            @Override
            public PromptsPage list(McpRequestContext ctx, String cursor) {
                return PromptsPage.of(prompts.values().stream().map(McpSyncPrompt::definition).toList());
            }

            @Override
            public McpSyncPrompt find(McpRequestContext ctx, String name) {
                return prompts.get(name);
            }
        };
    }

    private McpSchema.PromptMessage toPromptMessage(McpPromptMessage message) {
        McpSchema.Role role;
        try {
            role = McpSchema.Role.valueOf(message.role().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new McpException(ErrorCodes.INTERNAL_ERROR, "Prompt returned an invalid message role");
        }
        Object value = message.content();
        McpSchema.Content content;
        if (value instanceof String text) {
            content = McpSchema.TextContent.builder(text).build();
        } else if (value instanceof McpSchema.Content modern) {
            content = modern;
        } else if (value != null) {
            // a map, or a content block of the MCP SDK's other schema: both have the same JSON form
            try {
                content = jsonMapper.convertValue(value, McpSchema.Content.class);
            } catch (RuntimeException e) {
                throw new McpException(ErrorCodes.INTERNAL_ERROR, "Prompt returned an invalid MCP content block");
            }
        } else {
            throw new McpException(ErrorCodes.INTERNAL_ERROR, "Prompt returned an unsupported content value");
        }
        return McpSchema.PromptMessage.builder(role, content).build();
    }
}
