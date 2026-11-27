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
package org.apache.camel.component.springai.chat.mcp;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.apache.camel.CamelContext;
import org.apache.camel.support.OAuthHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;

/**
 * Manages MCP (Model Context Protocol) client lifecycle for the Spring AI Chat component.
 * <p>
 * Handles initialization, tool discovery via {@link SyncMcpToolCallbackProvider}, and graceful shutdown of MCP clients.
 * Supports the streamableHttp, stdio and sse (deprecated) transport types.
 * </p>
 */
public class SpringAiChatMcpManager {

    private static final Logger LOG = LoggerFactory.getLogger(SpringAiChatMcpManager.class);

    /**
     * The endpoint path of a Streamable HTTP server whose URL has no path, as in the MCP SDK.
     */
    private static final String DEFAULT_STREAMABLE_HTTP_ENDPOINT = "/mcp";
    /**
     * The endpoint path of an SSE server whose URL has no path, as in the MCP SDK.
     */
    private static final String DEFAULT_SSE_ENDPOINT = "/sse";

    private final List<McpSyncClient> mcpClients = new ArrayList<>();
    private SyncMcpToolCallbackProvider toolCallbackProvider;

    /**
     * Initialize MCP clients from flat configuration map.
     * <p>
     * Configuration keys follow the pattern: {@code <serverName>.<property>}. Supported properties:
     * <ul>
     * <li>{@code transportType} - Required. One of: streamableHttp, stdio, sse (deprecated)</li>
     * <li>{@code command} - Required for stdio. The command to execute</li>
     * <li>{@code args} - Optional for stdio. Comma-separated arguments</li>
     * <li>{@code url} - Required for streamableHttp and sse. The URL of the endpoint of the server, path included</li>
     * <li>{@code oauthProfile} - Optional for streamableHttp and sse. OAuth profile name for obtaining a Bearer token
     * (requires camel-oauth)</li>
     * </ul>
     *
     * @param  mcpServerConfig flat configuration map with dotted keys
     * @param  mcpTimeout      timeout in seconds for MCP operations
     * @param  camelContext    the CamelContext for OAuth token resolution
     * @throws Exception       if initialization fails
     */
    public void initialize(Map<String, Object> mcpServerConfig, int mcpTimeout, CamelContext camelContext)
            throws Exception {
        // Group flat keys by server name: "fs.transportType" -> {"fs": {"transportType": ...}}
        Map<String, Map<String, String>> serverConfigs = new HashMap<>();
        for (Map.Entry<String, Object> entry : mcpServerConfig.entrySet()) {
            String key = entry.getKey();
            int dot = key.indexOf('.');
            if (dot < 0) {
                continue;
            }
            String serverName = key.substring(0, dot);
            String property = key.substring(dot + 1);
            serverConfigs.computeIfAbsent(serverName, k -> new HashMap<>()).put(property, String.valueOf(entry.getValue()));
        }

        Duration timeout = Duration.ofSeconds(mcpTimeout);

        for (Map.Entry<String, Map<String, String>> entry : serverConfigs.entrySet()) {
            String serverName = entry.getKey();
            Map<String, String> props = entry.getValue();

            String transportType = props.get("transportType");
            if (transportType == null) {
                throw new IllegalArgumentException("mcpServer." + serverName + ".transportType is required");
            }

            LOG.debug("Creating MCP transport for server '{}' with type '{}'", serverName, transportType);
            McpClientTransport transport = createTransport(serverName, transportType, props, camelContext);
            McpSyncClient mcpClient = McpClient.sync(transport)
                    .requestTimeout(timeout)
                    .initializationTimeout(timeout)
                    .build();
            mcpClient.initialize();
            mcpClients.add(mcpClient);
            LOG.info("Initialized MCP server '{}'", serverName);
        }

        if (!mcpClients.isEmpty()) {
            toolCallbackProvider = SyncMcpToolCallbackProvider.builder()
                    .mcpClients(mcpClients)
                    .build();
            LOG.info("MCP tool callback provider created with {} tool callbacks from {} servers",
                    toolCallbackProvider.getToolCallbacks().length, mcpClients.size());
        }
    }

    /**
     * Returns the tool callback provider that provides MCP tools as Spring AI ToolCallbacks.
     */
    public ToolCallbackProvider getToolCallbackProvider() {
        return toolCallbackProvider;
    }

    /**
     * Close all MCP clients gracefully.
     */
    public void close() {
        for (McpSyncClient client : mcpClients) {
            try {
                client.close();
            } catch (Exception e) {
                LOG.warn("Error closing MCP client: {}", e.getMessage());
            }
        }
        mcpClients.clear();
        toolCallbackProvider = null;
        LOG.debug("All MCP clients closed");
    }

    /**
     * Creates the MCP transport for the given server configuration.
     *
     * <p>
     * When an {@code oauthProfile} is configured, the OAuth token is resolved lazily via a per-request customizer
     * rather than eagerly at startup. This enables short-lived tokens to be refreshed when they expire, at the cost of
     * deferring misconfiguration errors (wrong profile name, unreachable OIDC server) to the first request rather than
     * surfacing them at endpoint start.
     */
    private McpClientTransport createTransport(
            String serverName, String transportType, Map<String, String> props, CamelContext camelContext)
            throws Exception {

        // Build a per-request customizer so the OAuth token is re-resolved on every request,
        // allowing short-lived tokens to be refreshed when they expire.
        String oauthProfile = props.get("oauthProfile");
        McpSyncHttpClientRequestCustomizer oauthCustomizer = null;
        if (ObjectHelper.isNotEmpty(oauthProfile)) {
            final String profile = oauthProfile;
            oauthCustomizer = (requestBuilder, method, uri, body, context) -> {
                try {
                    String token = OAuthHelper.resolveOAuthToken(camelContext, profile);
                    requestBuilder.header("Authorization", "Bearer " + token);
                } catch (Exception e) {
                    throw new RuntimeException(
                            "Failed to resolve OAuth token for MCP server '" + serverName + "': " + e.getMessage(), e);
                }
            };
            LOG.debug("OAuth customizer registered for MCP server '{}' using profile '{}'", serverName, profile);
        }

        return switch (transportType) {
            case "stdio" -> {
                String command = props.get("command");
                if (command == null) {
                    throw new IllegalArgumentException("mcpServer." + serverName + ".command is required for stdio transport");
                }
                ServerParameters.Builder paramsBuilder = ServerParameters.builder(command);
                String args = props.get("args");
                if (args != null) {
                    paramsBuilder.args(List.of(args.split(",")));
                }
                yield new StdioClientTransport(paramsBuilder.build(), McpJsonDefaults.getMapper());
            }
            case "streamableHttp" -> {
                String url = props.get("url");
                if (url == null) {
                    throw new IllegalArgumentException(
                            "mcpServer." + serverName + ".url is required for streamableHttp transport");
                }
                yield createStreamableHttpTransport(serverName, url, oauthCustomizer);
            }
            case "sse" -> {
                String url = props.get("url");
                if (url == null) {
                    throw new IllegalArgumentException("mcpServer." + serverName + ".url is required for sse transport");
                }
                LOG.info("MCP server '{}' uses the sse transport, which the MCP specification has replaced by Streamable HTTP."
                         + " Use transportType=streamableHttp if the server supports it.",
                        serverName);
                McpServerUrl serverUrl = splitUrl(serverName, url, DEFAULT_SSE_ENDPOINT);
                HttpClientSseClientTransport.Builder sseBuilder = HttpClientSseClientTransport.builder(serverUrl.baseUri())
                        .sseEndpoint(serverUrl.endpoint());
                if (oauthCustomizer != null) {
                    sseBuilder.addHttpRequestCustomizer(oauthCustomizer);
                }
                yield sseBuilder.build();
            }
            default -> throw new IllegalArgumentException(
                    "Unknown transport type '" + transportType + "' for mcpServer." + serverName
                                                          + ". Supported: streamableHttp, stdio, sse");
        };
    }

    /**
     * Creates the Streamable HTTP transport for the MCP endpoint at the given URL.
     */
    private static McpClientTransport createStreamableHttpTransport(
            String serverName, String url, McpSyncHttpClientRequestCustomizer oauthCustomizer) {
        McpServerUrl serverUrl = splitUrl(serverName, url, DEFAULT_STREAMABLE_HTTP_ENDPOINT);
        HttpClientStreamableHttpTransport.Builder builder
                = HttpClientStreamableHttpTransport.builder(serverUrl.baseUri())
                        .endpoint(serverUrl.endpoint());
        if (oauthCustomizer != null) {
            builder.addHttpRequestCustomizer(oauthCustomizer);
        }
        return builder.build();
    }

    /**
     * The URL of an MCP server, split into the base URI and the endpoint that the HTTP transports of the MCP SDK take.
     * Package-private for tests.
     */
    record McpServerUrl(String baseUri, String endpoint) {
    }

    /**
     * Splits the URL of an MCP server into the base URI (scheme and authority) and the endpoint that the HTTP
     * transports of the MCP SDK take.
     * <p>
     * The transports resolve their endpoint, {@code /mcp} or {@code /sse} by default, against the base URI they are
     * given, which replaces the path and the query of that URI. The URL is therefore not passed as the base URI, so
     * that a server served under another path, for example {@code http://host:8080/api/mcp}, is reached. The endpoint
     * is the absolute URL of the MCP endpoint, path and query included, as a path that starts with {@code //} would
     * otherwise be read as the authority of another server. A URL without a path, such as {@code http://host:8080},
     * uses the default endpoint of the transport. Package-private for tests.
     */
    static McpServerUrl splitUrl(String serverName, String url, String defaultEndpoint) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(
                    "mcpServer." + serverName + ".url is not a valid URL: " + e.getReason(), e);
        }
        if (uri.getScheme() == null || uri.getRawAuthority() == null) {
            throw new IllegalArgumentException(
                    "mcpServer." + serverName + ".url must be an absolute URL, such as http://localhost:8080"
                                               + defaultEndpoint);
        }

        String baseUri = uri.getScheme() + "://" + uri.getRawAuthority();
        String path = uri.getRawPath();
        if (path == null || path.isEmpty() || "/".equals(path)) {
            path = defaultEndpoint;
        }
        String query = uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "";
        return new McpServerUrl(baseUri, baseUri + path + query);
    }
}
