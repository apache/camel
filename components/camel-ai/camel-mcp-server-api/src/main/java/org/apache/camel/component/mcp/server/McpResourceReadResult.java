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

/**
 * Result of an MCP resource read, pre-sanitized by the bridge: the error message is safe to return to a remote MCP
 * client and never contains raw route exception messages.
 * <p>
 * Exactly one of {@code text} and {@code blob} is set on success; on error both are null and {@code errorMessage}
 * carries a generic message. Unlike {@code tools/call}, {@code resources/read} has no in-band error flag, so engines
 * turn a failed read into a JSON-RPC error.
 *
 * @param text         textual contents, or null for a binary or failed read
 * @param blob         binary contents, or null for a textual or failed read
 * @param errorMessage a safe error message when the read failed, otherwise null
 *
 * @since              4.23
 */
public record McpResourceReadResult(String text, byte[] blob, String errorMessage) {

    public static McpResourceReadResult text(String text) {
        return new McpResourceReadResult(text, null, null);
    }

    public static McpResourceReadResult blob(byte[] blob) {
        return new McpResourceReadResult(null, blob, null);
    }

    public static McpResourceReadResult error(String errorMessage) {
        return new McpResourceReadResult(null, null, errorMessage);
    }

    public boolean isError() {
        return errorMessage != null;
    }
}
