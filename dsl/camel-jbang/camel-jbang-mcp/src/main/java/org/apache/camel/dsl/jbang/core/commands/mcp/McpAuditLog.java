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

import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.interceptor.InvocationContext;

import io.quarkiverse.mcp.server.ToolArg;
import org.jboss.logging.Logger;

/**
 * Structured audit logger for MCP tool invocations. Logs tool name, arguments, outcome, and duration to a dedicated
 * logger category ({@code camel.mcp.audit}) for easy filtering and forwarding to external systems.
 */
@ApplicationScoped
public class McpAuditLog {

    private static final Logger LOG = Logger.getLogger("camel.mcp.audit");

    /**
     * Log a successful tool invocation.
     */
    public void logToolCall(String toolName, InvocationContext ctx, long durationMs) {
        if (!LOG.isInfoEnabled()) {
            return;
        }
        Map<String, String> args = extractArgs(ctx);
        LOG.infof("TOOL_CALL tool=%s args=%s outcome=SUCCESS duration=%dms", toolName, args, durationMs);
    }

    /**
     * Log a failed tool invocation.
     */
    public void logToolError(String toolName, InvocationContext ctx, Throwable error, long durationMs) {
        Map<String, String> args = extractArgs(ctx);
        LOG.warnf("TOOL_CALL tool=%s args=%s outcome=ERROR error=\"%s\" duration=%dms",
                toolName, args, error.getMessage(), durationMs);
    }

    /**
     * Log a tool invocation that was blocked by the access control policy.
     */
    public void logAccessDenied(String toolName, McpAccessLevel configured, boolean readOnly, boolean destructive) {
        LOG.warnf("TOOL_DENIED tool=%s accessLevel=%s readOnlyHint=%s destructiveHint=%s",
                toolName, configured, readOnly, destructive);
    }

    private Map<String, String> extractArgs(InvocationContext ctx) {
        Map<String, String> args = new LinkedHashMap<>();
        Parameter[] params = ctx.getMethod().getParameters();
        Object[] values = ctx.getParameters();
        for (int i = 0; i < params.length; i++) {
            String name = params[i].getName();
            ToolArg toolArg = params[i].getAnnotation(ToolArg.class);
            if (toolArg != null) {
                String desc = toolArg.description();
                if (desc != null && !desc.isEmpty()) {
                    int end = Math.min(desc.length(), 30);
                    name = desc.substring(0, end).replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
                }
            }
            String value = values[i] != null ? truncate(values[i].toString(), 100) : "null";
            args.put(name, value);
        }
        return args;
    }

    private static String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen) + "...";
    }
}
