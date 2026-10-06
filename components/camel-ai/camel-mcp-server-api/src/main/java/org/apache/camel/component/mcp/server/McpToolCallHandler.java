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

import java.util.Map;

/**
 * Executes a single MCP tool call. Implemented by the bridge; engines invoke it when an MCP client calls the tool.
 * <p>
 * The call is blocking and bounded: the bridge applies the configured per-call timeout and maps every outcome
 * (including route exceptions and timeouts) to a pre-sanitized {@link McpToolCallResult} — it never throws and never
 * exposes route internals.
 *
 * @since 4.22
 */
@FunctionalInterface
public interface McpToolCallHandler {

    /**
     * Invokes the tool with the given arguments.
     *
     * @param  arguments the tool arguments as parsed from the MCP {@code tools/call} request, never null
     * @return           the sanitized result, never null
     */
    McpToolCallResult call(Map<String, Object> arguments);

    /**
     * Invokes the tool with the given arguments and the transport-supplied caller context, so a tool route's
     * {@link org.apache.camel.spi.AuthorizationPolicy} can authorize on the caller's identity. The default ignores the
     * context and calls {@link #call(Map)}; the bridge overrides it to stamp the caller principal onto the tool
     * exchange. Engines that can determine a caller identity (such as the Vert.x streamable HTTP transport) call this
     * overload; others keep calling {@link #call(Map)}.
     *
     * @param  arguments the tool arguments as parsed from the MCP {@code tools/call} request, never null
     * @param  context   the transport-supplied caller context, never null (use {@link McpToolCallContext#EMPTY} when
     *                   there is no caller identity)
     * @return           the sanitized result, never null
     * @since            4.23
     */
    default McpToolCallResult call(Map<String, Object> arguments, McpToolCallContext context) {
        return call(arguments);
    }
}
