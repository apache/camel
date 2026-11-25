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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result of an MCP tool invocation, pre-sanitized by the bridge: the text is safe to return to a remote MCP client and
 * never contains raw route exception messages.
 *
 * @param text              the tool output, or a safe error message when {@code isError} is true
 * @param isError           whether the invocation failed
 * @param structuredContent typed JSON output when the tool declares an output schema, otherwise {@code null}
 * @param meta              entries for the {@code _meta} field of the result, otherwise {@code null}. Since 4.24.
 *
 * @since                   4.22
 */
public record McpToolCallResult(String text, boolean isError, Object structuredContent, Map<String, Object> meta) {

    /**
     * Canonical constructor; defensively copies the meta entries into an unmodifiable map.
     */
    public McpToolCallResult {
        meta = meta == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(meta));
    }

    public McpToolCallResult(String text, boolean isError) {
        this(text, isError, null, null);
    }

    public McpToolCallResult(String text, boolean isError, Object structuredContent) {
        this(text, isError, structuredContent, null);
    }
}
