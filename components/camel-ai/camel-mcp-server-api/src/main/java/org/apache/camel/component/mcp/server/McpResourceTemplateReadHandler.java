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
 * Reads a concrete resource URI matched by an {@link McpServerResourceTemplate}. The returned result is already
 * sanitized for exposure to a remote MCP client.
 *
 * @since 4.24
 */
@FunctionalInterface
public interface McpResourceTemplateReadHandler {

    /**
     * Reads the supplied concrete URI. The URI and its path variables originate from the MCP client and must be treated
     * as untrusted input by the route.
     *
     * @param  resourceUri concrete URI sent in {@code resources/read}
     * @return             sanitized resource read result
     */
    McpResourceReadResult read(String resourceUri);

    /**
     * Reads the supplied concrete URI on behalf of the transport-supplied caller, so the resource route can check who
     * is asking. The default ignores the context and calls {@link #read(String)}; the bridge overrides it to stamp the
     * caller identity onto the resource exchange, as for a tool call. Engines that can determine a caller identity
     * (such as the Vert.x streamable HTTP transport) call this overload; others keep calling {@link #read(String)}.
     *
     * @param  resourceUri concrete URI sent in {@code resources/read}
     * @param  context     the transport-supplied caller context, never null (use {@link McpToolCallContext#EMPTY} when
     *                     there is no caller identity)
     * @return             sanitized resource read result
     * @since              4.24
     */
    default McpResourceReadResult read(String resourceUri, McpToolCallContext context) {
        return read(resourceUri);
    }
}
