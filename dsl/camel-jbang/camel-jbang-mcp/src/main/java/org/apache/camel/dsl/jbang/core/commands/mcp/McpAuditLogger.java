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

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

/**
 * Structured audit logger for MCP tool invocations.
 * <p>
 * Produces single-line JSON log entries to a dedicated logger category, allowing operators to route audit logs
 * independently via Quarkus logging configuration.
 */
@ApplicationScoped
public class McpAuditLogger {

    static final String AUDIT_LOGGER_NAME = "org.apache.camel.mcp.security.audit";

    private static final Logger AUDIT_LOG = Logger.getLogger(AUDIT_LOGGER_NAME);

    public void logToolCall(
            String tool, String connectionId, String clientName,
            String accessLevel, String arguments) {
        AUDIT_LOG.infof(
                "{\"event\":\"tool_call\",\"tool\":\"%s\",\"connectionId\":\"%s\","
                        + "\"clientName\":\"%s\",\"accessLevel\":\"%s\",\"arguments\":%s,"
                        + "\"timestamp\":\"%s\"}",
                escape(tool), escape(connectionId), escape(clientName),
                escape(accessLevel), arguments != null ? "\"" + escape(arguments) + "\"" : "null",
                Instant.now().toString());
    }

    public void logToolResult(
            String tool, String connectionId, boolean isError,
            boolean redacted, long durationMs) {
        AUDIT_LOG.infof(
                "{\"event\":\"tool_result\",\"tool\":\"%s\",\"connectionId\":\"%s\","
                        + "\"outcome\":\"%s\",\"redacted\":%s,\"durationMs\":%d,"
                        + "\"timestamp\":\"%s\"}",
                escape(tool), escape(connectionId),
                isError ? "error" : "success", redacted, durationMs,
                Instant.now().toString());
    }

    public void logAccessDenied(
            String tool, String connectionId, String clientName,
            String requiredLevel, String currentLevel) {
        AUDIT_LOG.warnf(
                "{\"event\":\"access_denied\",\"tool\":\"%s\",\"connectionId\":\"%s\","
                        + "\"clientName\":\"%s\",\"requiredLevel\":\"%s\",\"currentLevel\":\"%s\","
                        + "\"timestamp\":\"%s\"}",
                escape(tool), escape(connectionId), escape(clientName),
                escape(requiredLevel), escape(currentLevel),
                Instant.now().toString());
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\f", "\\f");
    }
}
