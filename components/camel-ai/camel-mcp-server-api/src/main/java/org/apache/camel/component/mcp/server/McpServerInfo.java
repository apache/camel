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
 * Identity and serving hints passed to an {@link McpServerEngine} before it is started.
 * <p>
 * Engines backed by a native runtime MCP server (Quarkus, Spring Boot) MAY ignore serving hints such as {@code path};
 * their own runtime configuration decides how the server is exposed. OAuth options are accepted only by engines that
 * explicitly support OAuth configuration.
 *
 * @param serverName                 the MCP server name advertised to clients (defaults to the CamelContext name)
 * @param version                    the MCP server version advertised to clients
 * @param path                       the HTTP path where the MCP endpoint should be served
 * @param sessionKeepAliveIntervalMs keep-alive ping interval in milliseconds for the Vert.x streamable transport;
 *                                   {@code 0} disables keep-alive pings
 * @param sessionIdleTtlMs           idle TTL in milliseconds; sessions with no activity for longer are evicted;
 *                                   {@code 0} disables idle eviction
 * @param title                      optional display title advertised in {@code serverInfo}
 * @param description                optional human-readable description advertised in {@code serverInfo}
 * @param websiteUrl                 optional documentation or home page URL advertised in {@code serverInfo}
 * @param instructions               optional top-level instructions returned to MCP clients on initialize
 * @param icons                      optional icons advertised in {@code serverInfo}
 * @param allowedOrigins             origins allowed to call the MCP endpoint (exact, or {@code scheme://host:*} for any
 *                                   port); {@code null} allows loopback origins only. A request without an
 *                                   {@code Origin} header is not affected. Since 4.24.
 * @param oauthProfile               the camel-oauth profile used for bearer-token validation, or {@code null} when
 *                                   disabled
 * @param requiredScopes             comma-separated OAuth scopes required by the MCP HTTP endpoint
 * @param resourceUri                explicit public HTTPS URI of the protected MCP resource for RFC 9728 metadata
 *
 * @since                            4.22
 */
public record McpServerInfo(
        String serverName,
        String version,
        String path,
        long sessionKeepAliveIntervalMs,
        long sessionIdleTtlMs,
        String title,
        String description,
        String websiteUrl,
        String instructions,
        List<McpServerIcon> icons,
        List<String> allowedOrigins,
        String oauthProfile,
        String requiredScopes,
        String resourceUri) {

    public McpServerInfo(String serverName, String version, String path) {
        this(serverName, version, path,
             McpServerConstants.DEFAULT_SESSION_KEEP_ALIVE_INTERVAL,
             McpServerConstants.DEFAULT_SESSION_IDLE_TTL,
             null, null, null, null, null, null, null, null, null);
    }

    public McpServerInfo(String serverName, String version, String path,
                         long sessionKeepAliveIntervalMs, long sessionIdleTtlMs) {
        this(serverName, version, path, sessionKeepAliveIntervalMs, sessionIdleTtlMs,
             null, null, null, null, null, null, null, null, null);
    }

    public McpServerInfo(String serverName, String version, String path,
                         long sessionKeepAliveIntervalMs, long sessionIdleTtlMs,
                         String title, String description, String websiteUrl, String instructions,
                         List<McpServerIcon> icons) {
        this(serverName, version, path, sessionKeepAliveIntervalMs, sessionIdleTtlMs,
             title, description, websiteUrl, instructions, icons, null, null, null, null);
    }

    /**
     * Compatibility constructor preserving the serving-hints signature from before OAuth support. OAuth remains
     * disabled when this constructor is used.
     *
     * @param serverName                 the MCP server name
     * @param version                    the MCP server version
     * @param path                       the HTTP endpoint path
     * @param sessionKeepAliveIntervalMs keep-alive interval in milliseconds
     * @param sessionIdleTtlMs           session idle TTL in milliseconds
     * @param title                      optional display title
     * @param description                optional server description
     * @param websiteUrl                 optional server website URL
     * @param instructions               optional server instructions
     * @param icons                      optional server icons
     * @param allowedOrigins             allowed HTTP origins, or null for the default
     */
    public McpServerInfo(String serverName, String version, String path,
                         long sessionKeepAliveIntervalMs, long sessionIdleTtlMs,
                         String title, String description, String websiteUrl, String instructions,
                         List<McpServerIcon> icons, List<String> allowedOrigins) {
        this(serverName, version, path, sessionKeepAliveIntervalMs, sessionIdleTtlMs,
             title, description, websiteUrl, instructions, icons, allowedOrigins, null, null, null);
    }

    public McpServerInfo {
        if (sessionKeepAliveIntervalMs < 0) {
            throw new IllegalArgumentException("sessionKeepAliveIntervalMs must be >= 0");
        }
        if (sessionIdleTtlMs < 0) {
            throw new IllegalArgumentException("sessionIdleTtlMs must be >= 0");
        }
        if (icons != null) {
            icons = List.copyOf(icons);
        }
        if (allowedOrigins != null) {
            allowedOrigins = List.copyOf(allowedOrigins);
        }
    }
}
