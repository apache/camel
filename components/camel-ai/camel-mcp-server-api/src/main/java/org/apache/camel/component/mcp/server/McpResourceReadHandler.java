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
 * Reads a single MCP resource. Implemented by the bridge; engines invoke it when an MCP client reads the resource.
 * <p>
 * The read is blocking and bounded: the bridge applies the configured per-read timeout and maps every outcome
 * (including route exceptions and timeouts) to a pre-sanitized {@link McpResourceReadResult} — it never throws and
 * never exposes route internals.
 *
 * @since 4.23
 */
@FunctionalInterface
public interface McpResourceReadHandler {

    /**
     * Reads the resource contents.
     *
     * @return the sanitized result, never null
     */
    McpResourceReadResult read();

    /**
     * Reads the resource contents on behalf of the transport-supplied caller, so the resource route can check who is
     * asking. The default ignores the context and calls {@link #read()}; the bridge overrides it to stamp the caller
     * identity onto the resource exchange, as for a tool call. Engines that can determine a caller identity (such as
     * the Vert.x streamable HTTP transport) call this overload; others keep calling {@link #read()}.
     *
     * @param  context the transport-supplied caller context, never null (use {@link McpToolCallContext#EMPTY} when
     *                 there is no caller identity)
     * @return         the sanitized result, never null
     * @since          4.24
     */
    default McpResourceReadResult read(McpToolCallContext context) {
        return read();
    }
}
