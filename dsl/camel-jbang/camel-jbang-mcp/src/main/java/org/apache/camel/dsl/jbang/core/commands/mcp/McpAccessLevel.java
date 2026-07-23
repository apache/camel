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

/**
 * Access levels for the MCP server security policy.
 * <p>
 * Controls which tools are available to connected MCP clients based on their
 * {@link io.quarkiverse.mcp.server.Tool.Annotations} classification.
 */
public enum McpAccessLevel {

    /**
     * Only tools marked with {@code readOnlyHint = true} are allowed. Blocks all tools that can modify state (send
     * messages, control routes, stop processes, write files).
     */
    READONLY,

    /**
     * All non-destructive tools are allowed. Blocks tools marked with {@code destructiveHint = true} (route control,
     * send, stop).
     */
    READWRITE,

    /**
     * All tools are allowed, including destructive ones. This is the default for local development.
     */
    ADMIN;

    /**
     * Parse an access level from a configuration string. Accepts case-insensitive values: {@code readonly},
     * {@code read-only}, {@code readwrite}, {@code read-write}, {@code admin}.
     *
     * @throws IllegalArgumentException if the value is not recognized
     */
    public static McpAccessLevel fromString(String value) {
        if (value == null || value.isBlank()) {
            return ADMIN;
        }
        return switch (value.toLowerCase().replace("-", "").replace("_", "")) {
            case "readonly" -> READONLY;
            case "readwrite" -> READWRITE;
            case "admin" -> ADMIN;
            default -> throw new IllegalArgumentException(
                    "Unknown MCP access level: " + value + ". Use: readonly, readwrite, or admin");
        };
    }

    /**
     * Check whether a tool with the given annotation hints is allowed at this access level.
     */
    public boolean isToolAllowed(boolean readOnlyHint, boolean destructiveHint) {
        return switch (this) {
            case READONLY -> readOnlyHint;
            case READWRITE -> !destructiveHint;
            case ADMIN -> true;
        };
    }
}
