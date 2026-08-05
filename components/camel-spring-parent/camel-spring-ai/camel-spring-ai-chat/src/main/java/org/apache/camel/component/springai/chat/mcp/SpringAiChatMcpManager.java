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

import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
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
 * Supports stdio, sse, and sse transport types.
 * </p>
 */
public class SpringAiChatMcpManager {

    private static final Logger LOG = LoggerFactory.getLogger(SpringAiChatMcpManager.class);

    private final List<McpSyncClient> mcpClients = new ArrayList<>();
    private SyncMcpToolCallbackProvider toolCallbackProvider;

    /**
     * Initialize MCP clients from flat configuration map.
     * <p>
     * Configuration keys follow the pattern: {@code <serverName>.<property>}. Supported properties:
     * <ul>
     * <li>{@code transportType} - Required. One of: stdio, sse</li>
     * <li>{@code command} - Required for stdio. The command to execute</li>
     * <li>{@code args} - Optional for stdio. Comma-separated arguments</li>
     * <li>{@code url} - Required for sse. The server URL</li>
     * <li>{@code oauthProfile} - Optional for sse. OAuth profile name for obtaining a Bearer token (requires
     * camel-oauth)</li>
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

    private McpClientTransport createTransport(
            String serverName, String transportType, Map<String, String> props, CamelContext camelContext)
            throws Exception {

        // Resolve per-server OAuth token if configured
        String oauthProfile = props.get("oauthProfile");
        HttpRequest.Builder authRequestBuilder = null;
        if (ObjectHelper.isNotEmpty(oauthProfile)) {
            String token = OAuthHelper.resolveOAuthToken(camelContext, oauthProfile);
            authRequestBuilder = HttpRequest.newBuilder()
                    .header("Authorization", "Bearer " + token);
            LOG.debug("OAuth token resolved for MCP server '{}' using profile '{}'", serverName, oauthProfile);
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
            case "sse" -> {
                String url = props.get("url");
                if (url == null) {
                    throw new IllegalArgumentException("mcpServer." + serverName + ".url is required for sse transport");
                }
                HttpClientSseClientTransport.Builder sseBuilder = HttpClientSseClientTransport.builder(url);
                if (authRequestBuilder != null) {
                    sseBuilder.requestBuilder(authRequestBuilder);
                }
                yield sseBuilder.build();
            }
            default -> throw new IllegalArgumentException(
                    "Unknown transport type '" + transportType + "' for mcpServer." + serverName
                                                          + ". Supported: stdio, sse");
        };
    }
}
