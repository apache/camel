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

import org.apache.camel.RuntimeCamelException;

/**
 * Thrown by an {@link McpServerEngine} from {@link McpServerEngine#toolAdded(McpServerTool)} or
 * {@link McpServerEngine#resourceAdded(McpServerResource)} to refuse a tool or resource it cannot serve, for example
 * because the runtime's MCP server already serves one with the same name or uri.
 * <p>
 * The bridge handles it like its own name clash: it logs an ERROR with the message as the reason (and the cause, if
 * any), the route keeps running, and the tool or resource is not tracked as published.
 *
 * @since 4.24
 */
public class McpServerRefusedException extends RuntimeCamelException {

    public McpServerRefusedException(String message) {
        super(message);
    }

    public McpServerRefusedException(String message, Throwable cause) {
        super(message, cause);
    }
}
