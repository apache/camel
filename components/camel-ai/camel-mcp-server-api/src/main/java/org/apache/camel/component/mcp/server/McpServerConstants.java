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
 * Constants of the Camel MCP server.
 *
 * @since 4.22
 */
public final class McpServerConstants {

    /**
     * FactoryFinder key (under {@code META-INF/services/org/apache/camel/}) used to discover the
     * {@link McpServerEngine} implementation on the classpath.
     */
    public static final String MCP_SERVER_ENGINE_FACTORY = "mcp-server-engine";

    /**
     * Default HTTP path where the MCP endpoint is served by engines that consume the serving configuration.
     */
    public static final String DEFAULT_PATH = "/mcp";

    /**
     * Default per-call tool execution timeout in milliseconds.
     */
    public static final long DEFAULT_TOOL_TIMEOUT = 20_000;

    /**
     * Default per-read resource execution timeout in milliseconds.
     */
    public static final long DEFAULT_RESOURCE_TIMEOUT = 20_000;

    /**
     * Default MCP session keep-alive ping interval in milliseconds for the Vert.x streamable transport. {@code 0}
     * disables keep-alive pings.
     */
    public static final long DEFAULT_SESSION_KEEP_ALIVE_INTERVAL = 30_000;

    /**
     * Default MCP session idle TTL in milliseconds for the Vert.x streamable transport. Sessions with no activity for
     * longer than this interval are evicted. {@code 0} disables idle eviction.
     */
    public static final long DEFAULT_SESSION_IDLE_TTL = 300_000;

    private McpServerConstants() {
    }
}
