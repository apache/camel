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

import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.util.json.JsonObject;

/**
 * CDI interceptor that enforces access control, audit logging, and output sanitization on MCP tool methods.
 * <p>
 * Applied to all classes annotated with {@link McpSecured}. Only intercepts methods annotated with
 * {@link io.quarkiverse.mcp.server.Tool} — non-tool methods pass through unmodified.
 *
 * @see McpSecurityConfig
 * @see McpAuditLog
 * @see OutputSanitizer
 */
@McpSecured
@Interceptor
@jakarta.annotation.Priority(Interceptor.Priority.APPLICATION)
public class McpSecurityInterceptor {

    @Inject
    McpSecurityConfig securityConfig;

    @Inject
    McpAuditLog auditLog;

    @AroundInvoke
    public Object intercept(InvocationContext ctx) throws Exception {
        Method method = ctx.getMethod();
        Tool toolAnnotation = method.getAnnotation(Tool.class);

        if (toolAnnotation == null) {
            return ctx.proceed();
        }

        String toolName = method.getName();
        Tool.Annotations hints = toolAnnotation.annotations();
        boolean readOnly = hints.readOnlyHint();
        boolean destructive = hints.destructiveHint();

        McpAccessLevel accessLevel = securityConfig.getAccessLevel();
        if (!accessLevel.isToolAllowed(readOnly, destructive)) {
            if (securityConfig.isAuditEnabled()) {
                auditLog.logAccessDenied(toolName, accessLevel, readOnly, destructive);
            }
            throw new ToolCallException(
                    "Access denied: tool '" + toolName + "' requires higher access level than '"
                                        + accessLevel + "'. Current policy blocks "
                                        + (destructive ? "destructive" : "non-read-only") + " tools.",
                    null);
        }

        long start = System.nanoTime();
        try {
            Object result = ctx.proceed();

            if (securityConfig.isRedactSecretsEnabled() && result instanceof JsonObject jo) {
                OutputSanitizer.redact(jo);
            }

            if (securityConfig.isAuditEnabled()) {
                long durationMs = (System.nanoTime() - start) / 1_000_000;
                auditLog.logToolCall(toolName, ctx, durationMs);
            }

            return result;
        } catch (Exception e) {
            if (securityConfig.isAuditEnabled()) {
                long durationMs = (System.nanoTime() - start) / 1_000_000;
                auditLog.logToolError(toolName, ctx, e, durationMs);
            }
            throw e;
        }
    }
}
