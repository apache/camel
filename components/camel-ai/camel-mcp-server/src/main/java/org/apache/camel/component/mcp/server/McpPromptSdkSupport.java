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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Converts the engine-neutral prompt API to the MCP Java SDK representation.
 *
 * @since 4.24
 */
public final class McpPromptSdkSupport {

    private McpPromptSdkSupport() {
    }

    public static McpServerFeatures.SyncPromptSpecification buildSpecification(
            McpServerPrompt prompt, McpJsonMapper jsonMapper) {
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

        return new McpServerFeatures.SyncPromptSpecification(builder.build(), (exchange, request) -> {
            Map<String, String> arguments = new LinkedHashMap<>();
            if (request.arguments() != null) {
                request.arguments().forEach((name, value) -> {
                    if (!(value instanceof String stringValue)) {
                        throw McpError.builder(McpSchema.ErrorCodes.INVALID_PARAMS)
                                .message("Prompt arguments must be strings")
                                .build();
                    }
                    arguments.put(name, stringValue);
                });
            }
            McpPromptResult result = prompt.handler().get(arguments);
            if (result == null) {
                throw McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
                        .message("Prompt execution failed")
                        .build();
            }
            if (result.isError()) {
                int code = result.argumentError()
                        ? McpSchema.ErrorCodes.INVALID_PARAMS : McpSchema.ErrorCodes.INTERNAL_ERROR;
                throw McpError.builder(code).message(result.errorMessage()).build();
            }
            List<McpSchema.PromptMessage> messages = result.messages().stream()
                    .map(message -> toPromptMessage(message, jsonMapper))
                    .toList();
            return McpSchema.GetPromptResult.builder(messages).description(result.description()).build();
        });
    }

    private static McpSchema.PromptMessage toPromptMessage(McpPromptMessage message, McpJsonMapper jsonMapper) {
        McpSchema.Role role;
        try {
            role = McpSchema.Role.valueOf(message.role().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
                    .message("Prompt returned an invalid message role")
                    .build();
        }

        Object value = message.content();
        McpSchema.Content content;
        if (value instanceof String text) {
            content = McpSchema.TextContent.builder(text).build();
        } else if (value instanceof McpSchema.Content mcpContent) {
            content = mcpContent;
        } else if (value instanceof Map<?, ?> map) {
            try {
                content = jsonMapper.convertValue(map, McpSchema.Content.class);
            } catch (RuntimeException e) {
                throw McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
                        .message("Prompt returned an invalid MCP content block")
                        .build();
            }
        } else {
            throw McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
                    .message("Prompt returned an unsupported content value")
                    .build();
        }
        return McpSchema.PromptMessage.builder(role, content).build();
    }
}
