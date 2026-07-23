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

import java.lang.reflect.Method;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import io.quarkiverse.mcp.server.Tool;

/**
 * CDI interceptor for the MCP security execution layer.
 * <p>
 * Intercepts {@code @Tool}-annotated methods on classes marked with {@link McpSecured} to provide input sanitization,
 * audit logging, and secret redaction. Authorization is handled by {@link McpAccessFilter} which is a global
 * {@code ToolFilter}.
 * <p>
 * This replaces the ToolInputGuardrail/ToolOutputGuardrail approach since those require per-tool
 * {@code @ToolGuardrails} annotations and are not auto-discovered as global CDI beans.
 */
@McpSecured
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class McpSecurityInterceptor {

    @Inject
    McpSecurityConfig config;

    @Inject
    McpAuditLogger auditLogger;

    @Inject
    McpSecretRedactor redactor;

    @AroundInvoke
    Object intercept(InvocationContext ctx) throws Exception {
        if (!config.isEnabled()) {
            return ctx.proceed();
        }

        Method method = ctx.getMethod();
        if (!method.isAnnotationPresent(Tool.class)) {
            return ctx.proceed();
        }

        String toolName = method.getName();

        // Input sanitization
        sanitizeParameters(ctx);

        // TODO: CDI interceptors do not have access to MCP connection context (connectionId, clientName).
        // These will be populated once global guardrail support is available in Quarkus MCP Server.

        // Audit: log tool call
        if (config.isAuditEnabled()) {
            String arguments = null;
            if (config.isAuditIncludeArguments()) {
                arguments = summarizeParameters(ctx);
            }
            auditLogger.logToolCall(toolName, "", "", config.getAccessLevel().name(), arguments);
        }

        long start = System.nanoTime();
        boolean isError = false;
        boolean wasRedacted = false;
        try {
            Object result = ctx.proceed();

            // Secret redaction on string results
            if (config.isRedactionEnabled() && result instanceof String s) {
                if (redactor.containsSecret(s)) {
                    result = redactor.redact(s);
                    wasRedacted = true;
                }
            }

            return result;
        } catch (Exception e) {
            isError = true;
            throw e;
        } finally {
            if (config.isAuditEnabled()) {
                long durationMs = (System.nanoTime() - start) / 1_000_000;
                auditLogger.logToolResult(toolName, "", isError, wasRedacted, durationMs);
            }
        }
    }

    private void sanitizeParameters(InvocationContext ctx) {
        Object[] params = ctx.getParameters();
        if (params == null) {
            return;
        }

        int maxLen = config.getMaxArgumentLength();
        boolean modified = false;

        for (int i = 0; i < params.length; i++) {
            if (params[i] instanceof String s) {
                String clean = stripControlChars(s);
                if (clean.length() > maxLen) {
                    clean = clean.substring(0, maxLen);
                }
                if (!clean.equals(s)) {
                    params[i] = clean;
                    modified = true;
                }
            }
        }

        if (modified) {
            ctx.setParameters(params);
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

    private String summarizeParameters(InvocationContext ctx) {
        Object[] params = ctx.getParameters();
        if (params == null || params.length == 0) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < params.length; i++) {
            if (params[i] == null) {
                continue;
            }
            String value = params[i].toString();
            if (value.length() > 200) {
                value = value.substring(0, 200) + "...";
            }
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append("arg").append(i).append("=").append(value);
        }
        return sb.toString();
    }
}
