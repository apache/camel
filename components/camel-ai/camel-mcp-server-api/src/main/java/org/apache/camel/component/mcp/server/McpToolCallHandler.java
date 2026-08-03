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
}
