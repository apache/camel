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

import java.util.List;

/**
 * Result of a prompt request. Errors are sanitized by the bridge before they reach an MCP engine.
 *
 * @since 4.24
 */
public record McpPromptResult(
        String description, List<McpPromptMessage> messages, String errorMessage, boolean argumentError) {

    public McpPromptResult {
        messages = messages != null ? List.copyOf(messages) : List.of();
    }

    public static McpPromptResult success(String description, List<McpPromptMessage> messages) {
        return new McpPromptResult(description, messages, null, false);
    }

    public static McpPromptResult argumentError(String message) {
        return new McpPromptResult(null, List.of(), message, true);
    }

    public static McpPromptResult error(String message) {
        return new McpPromptResult(null, List.of(), message, false);
    }

    public boolean isError() {
        return errorMessage != null;
    }
}
