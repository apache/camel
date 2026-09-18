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

import java.util.List;

/**
 * Configuration for the {@link McpServerBridge}.
 * <p>
 * Bridge-owned options ({@code tags}, {@code toolTimeout}) are honored on every runtime. Engine-owned options
 * ({@code path}, {@code serverName}, {@code serverTitle}, {@code serverDescription}, {@code serverWebsiteUrl},
 * {@code instructions}, {@code serverIcons}) are consumed only by engines that serve through Camel — native engines
 * (Quarkus, Spring Boot) use their own runtime configuration instead.
 *
 * @since 4.22
 */
public class McpServerConfiguration {

    private String tags;
    private long toolTimeout = McpServerConstants.DEFAULT_TOOL_TIMEOUT;
    private long resourceTimeout = McpServerConstants.DEFAULT_RESOURCE_TIMEOUT;
    private String path = McpServerConstants.DEFAULT_PATH;
    private String serverName;
    private String serverTitle;
    private String serverDescription;
    private String serverWebsiteUrl;
    private String instructions;
    private List<McpServerIcon> serverIcons;
    private long sessionKeepAliveInterval = McpServerConstants.DEFAULT_SESSION_KEEP_ALIVE_INTERVAL;
    private long sessionIdleTtl = McpServerConstants.DEFAULT_SESSION_IDLE_TTL;

    /**
     * Comma-separated list of ai-tool tag patterns to expose as MCP tools. Matching is case-insensitive and supports
     * exact match, wildcard prefix ({@code foo*}), and {@code *} to match all tags. Only tools registered under a
     * matching tag are published; the untagged default pool is never exposed. When not set, no tools are published.
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
     * Per-read resource execution timeout in milliseconds. A read exceeding the timeout returns an error to the MCP
     * client; the underlying route keeps running until it completes on its own.
     */
    public long getResourceTimeout() {
        return resourceTimeout;
    }

    public void setResourceTimeout(long resourceTimeout) {
        this.resourceTimeout = resourceTimeout;
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

    /**
     * MCP server display title advertised to clients in {@code serverInfo.title}. Engine-owned hint: native engines MAY
     * ignore it.
     */
    public String getServerTitle() {
        return serverTitle;
    }

    public void setServerTitle(String serverTitle) {
        this.serverTitle = serverTitle;
    }

    /**
     * MCP server description advertised to clients in {@code serverInfo.description}. Engine-owned hint: native engines
     * MAY ignore it.
     */
    public String getServerDescription() {
        return serverDescription;
    }

    public void setServerDescription(String serverDescription) {
        this.serverDescription = serverDescription;
    }

    /**
     * MCP server website URL advertised to clients in {@code serverInfo.websiteUrl}. Engine-owned hint: native engines
     * MAY ignore it.
     */
    public String getServerWebsiteUrl() {
        return serverWebsiteUrl;
    }

    public void setServerWebsiteUrl(String serverWebsiteUrl) {
        this.serverWebsiteUrl = serverWebsiteUrl;
    }

    /**
     * Top-level MCP instructions returned to clients on initialize. Engine-owned hint: native engines MAY ignore it.
     */
    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    /**
     * Icons advertised to clients in {@code serverInfo.icons}. Engine-owned hint: native engines MAY ignore them.
     */
    public List<McpServerIcon> getServerIcons() {
        return serverIcons;
    }

    public void setServerIcons(List<McpServerIcon> serverIcons) {
        this.serverIcons = serverIcons;
    }

    /**
     * Keep-alive ping interval in milliseconds for the Vert.x streamable transport. Dead sessions are evicted after
     * consecutive ping failures. {@code 0} disables keep-alive pings. Engine-owned: ignored by native engines.
     */
    public long getSessionKeepAliveInterval() {
        return sessionKeepAliveInterval;
    }

    public void setSessionKeepAliveInterval(long sessionKeepAliveInterval) {
        this.sessionKeepAliveInterval = sessionKeepAliveInterval;
    }

    /**
     * Idle TTL in milliseconds for MCP sessions managed by the Vert.x streamable transport. Sessions with no activity
     * for longer than this interval are evicted. {@code 0} disables idle eviction. Engine-owned: ignored by native
     * engines.
     */
    public long getSessionIdleTtl() {
        return sessionIdleTtl;
    }

    public void setSessionIdleTtl(long sessionIdleTtl) {
        this.sessionIdleTtl = sessionIdleTtl;
    }
}
