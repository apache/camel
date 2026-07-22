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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolInputGuardrail;
import io.quarkiverse.mcp.server.ToolManager;
import io.vertx.core.json.JsonObject;

/**
 * Global input guardrail that enforces authorization, input sanitization, and audit logging before every MCP tool call.
 * <p>
 * This is a defense-in-depth layer — even if {@link McpAccessFilter} is bypassed, this guardrail blocks unauthorized
 * tool execution.
 */
@ApplicationScoped
public class McpAccessGuardrail implements ToolInputGuardrail {

    @Inject
    McpSecurityConfig config;

    @Inject
    McpAuditLogger auditLogger;

    @Override
    public void apply(ToolInputContext ctx) {
        if (!config.isEnabled()) {
            return;
        }

        String toolName = ctx.getTool().name();
        String connectionId = ctx.getConnection().id();
        String clientName = resolveClientName(ctx);
        McpSecurityConfig.AccessLevel level = config.getAccessLevel();

        // Authorization check
        boolean readOnlyHint = ctx.getTool().annotations()
                .map(ToolManager.ToolAnnotations::readOnlyHint).orElse(true);
        boolean destructiveHint = ctx.getTool().annotations()
                .map(ToolManager.ToolAnnotations::destructiveHint).orElse(false);

        if (!level.permits(readOnlyHint, destructiveHint)) {
            String requiredLevel = requiredLevel(readOnlyHint, destructiveHint);
            if (config.isAuditEnabled()) {
                auditLogger.logAccessDenied(toolName, connectionId, clientName, requiredLevel, level.name());
            }
            throw new ToolCallException(
                    "Access denied: tool '" + toolName + "' requires '" + requiredLevel
                                        + "' access level, current level is '" + level.name().toLowerCase().replace("_", "-")
                                        + "'",
                    null);
        }

        // Input sanitization
        sanitizeArguments(ctx);

        // Audit logging
        if (config.isAuditEnabled()) {
            String arguments = null;
            if (config.isAuditIncludeArguments()) {
                JsonObject args = ctx.getArguments();
                arguments = args != null ? truncate(args.encode(), 1000) : null;
            }
            auditLogger.logToolCall(toolName, connectionId, clientName, level.name(), arguments);
        }
    }

    private void sanitizeArguments(ToolInputContext ctx) {
        JsonObject args = ctx.getArguments();
        if (args == null || args.isEmpty()) {
            return;
        }

        int maxLen = config.getMaxArgumentLength();
        boolean modified = false;
        JsonObject sanitized = args.copy();

        for (String key : sanitized.fieldNames()) {
            Object value = sanitized.getValue(key);
            if (value instanceof String s) {
                String clean = stripControlChars(s);
                if (clean.length() > maxLen) {
                    clean = clean.substring(0, maxLen);
                }
                if (!clean.equals(s)) {
                    sanitized.put(key, clean);
                    modified = true;
                }
            }
        }

        if (modified) {
            ctx.setArguments(sanitized);
        }
    }

    static String stripControlChars(String input) {
        if (input == null) {
            return null;
        }
        StringBuilder sb = null;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') {
                if (sb == null) {
                    sb = new StringBuilder(input.length());
                    sb.append(input, 0, i);
                }
            } else if (sb != null) {
                sb.append(c);
            }
        }
        return sb != null ? sb.toString() : input;
    }

    private static String resolveClientName(ToolInputContext ctx) {
        try {
            return ctx.getConnection().initialRequest().implementation().name();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String requiredLevel(boolean readOnlyHint, boolean destructiveHint) {
        if (destructiveHint) {
            return "admin";
        }
        if (!readOnlyHint) {
            return "read-write";
        }
        return "read-only";
    }

    private static String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen) + "...(truncated)";
    }
}
