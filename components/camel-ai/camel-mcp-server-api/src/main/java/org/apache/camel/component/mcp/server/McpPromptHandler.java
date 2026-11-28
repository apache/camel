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
 * Executes a prompt using the arguments supplied by an MCP client.
 *
 * @since 4.24
 */
@FunctionalInterface
public interface McpPromptHandler {

    /**
     * Gets the prompt messages for the given arguments.
     *
     * @param  arguments the prompt arguments sent in {@code prompts/get}
     * @return           the sanitized result
     */
    McpPromptResult get(Map<String, String> arguments);

    /**
     * Gets the prompt messages for the given arguments on behalf of the transport-supplied caller, so the prompt route
     * can check who is asking. The default ignores the context and calls {@link #get(Map)}; the bridge overrides it to
     * stamp the caller identity onto the prompt exchange, as for a tool call. Engines that can determine a caller
     * identity (such as the Vert.x streamable HTTP transport) call this overload; others keep calling
     * {@link #get(Map)}.
     *
     * @param  arguments the prompt arguments sent in {@code prompts/get}
     * @param  context   the transport-supplied caller context, never null (use {@link McpToolCallContext#EMPTY} when
     *                   there is no caller identity)
     * @return           the sanitized result
     * @since            4.24
     */
    default McpPromptResult get(Map<String, String> arguments, McpToolCallContext context) {
        return get(arguments);
    }
}
