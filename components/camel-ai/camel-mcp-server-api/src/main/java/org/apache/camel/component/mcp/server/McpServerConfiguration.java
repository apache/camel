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
 * Configuration for the {@link McpServerBridge}.
 * <p>
 * Bridge-owned options ({@code tags}, {@code toolTimeout}) are honored on every runtime. Engine-owned options
 * ({@code path}, {@code serverName}) are consumed only by engines that serve through Camel — native engines (Quarkus,
 * Spring Boot) use their own runtime configuration instead.
 *
 * @since 4.22
 */
public class McpServerConfiguration {

    private String tags;
    private long toolTimeout = McpServerConstants.DEFAULT_TOOL_TIMEOUT;
    private String path = McpServerConstants.DEFAULT_PATH;
    private String serverName;

    /**
     * Comma-separated list of ai-tool tags to expose as MCP tools. Only tools registered under one of these tags are
     * published; the untagged default pool is never exposed. When not set, no tools are published.
     */
    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    /**
     * Per-call tool execution timeout in milliseconds. A call exceeding the timeout returns an error result to the MCP
     * client; the underlying route keeps running until it completes on its own.
     */
    public long getToolTimeout() {
        return toolTimeout;
    }

    public void setToolTimeout(long toolTimeout) {
        this.toolTimeout = toolTimeout;
    }

    /**
     * HTTP path where the MCP endpoint is served. Engine-owned: ignored by native engines.
     */
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    /**
     * MCP server name advertised to clients. Defaults to the CamelContext name. Engine-owned hint: native engines MAY
     * ignore it.
     */
    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }
}
