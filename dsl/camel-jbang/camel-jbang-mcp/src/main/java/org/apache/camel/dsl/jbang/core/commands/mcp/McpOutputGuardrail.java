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

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.ToolOutputGuardrail;
import io.quarkiverse.mcp.server.ToolResponse;

/**
 * Global output guardrail that performs secret redaction on tool responses and logs audit trail entries for tool
 * results.
 */
@ApplicationScoped
public class McpOutputGuardrail implements ToolOutputGuardrail {

    @Inject
    McpSecurityConfig config;

    @Inject
    McpSecretRedactor redactor;

    @Inject
    McpAuditLogger auditLogger;

    @Override
    public void apply(ToolOutputContext ctx) {
        if (!config.isEnabled()) {
            return;
        }

        String toolName = ctx.getTool().name();
        String connectionId = ctx.getConnection().id();
        boolean redacted = false;

        // Secret redaction
        if (config.isRedactionEnabled()) {
            ToolResponse response = ctx.getResponse();
            if (response != null && response.content() != null) {
                List<Content> redactedContent = new ArrayList<>();
                boolean anyRedacted = false;

                for (Content content : response.content()) {
                    if (content instanceof TextContent tc) {
                        String original = tc.text();
                        if (original != null && redactor.containsSecret(original)) {
                            redactedContent.add(new TextContent(redactor.redact(original)));
                            anyRedacted = true;
                        } else {
                            redactedContent.add(content);
                        }
                    } else {
                        redactedContent.add(content);
                    }
                }

                if (anyRedacted) {
                    ctx.setResponse(new ToolResponse(response.isError(), redactedContent));
                    redacted = true;
                }
            }
        }

        // Audit logging
        if (config.isAuditEnabled()) {
            boolean isError = ctx.getResponse() != null && ctx.getResponse().isError();
            auditLogger.logToolResult(toolName, connectionId, isError, redacted, 0);
        }
    }
}
