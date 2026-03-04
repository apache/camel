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
package org.apache.camel.component.openai;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpTransportException;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.OAuthHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenAI endpoint for chat completion and embeddings.
 */
@UriEndpoint(firstVersion = "4.17.0",
             scheme = "openai",
             title = "OpenAI",
             syntax = "openai:operation",
             category = { Category.AI },
             producerOnly = true,
             headersClass = OpenAIConstants.class)
public class OpenAIEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAIEndpoint.class);

    @UriPath
    @Metadata(required = true, description = "The operation to perform: 'chat-completion', 'embeddings', or 'tool-execution'",
              enums = "chat-completion,embeddings,tool-execution")
    private String operation;

    @UriParam
    private OpenAIConfiguration configuration;

    private OpenAIClient client;

    private List<ChatCompletionFunctionTool> cachedMcpTools;
    private Map<String, McpSyncClient> toolClientMap;
    private Set<String> returnDirectTools;
    private Map<String, Map<String, String>> serverConfigs;
    private Map<String, String> toolToServerName;

    public OpenAIEndpoint(String uri, OpenAIComponent component, OpenAIConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return switch (operation) {
            case "chat-completion" -> new OpenAIProducer(this);
            case "embeddings" -> new OpenAIEmbeddingsProducer(this);
            case "tool-execution" -> new OpenAIToolExecutionProducer(this);
            default -> throw new IllegalArgumentException(
                    "Unknown operation: " + operation + ". Supported: chat-completion, embeddings, tool-execution");
        };
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported for OpenAI component");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        client = createClient();
        initializeMcpServers();
    }

    @Override
    protected void doStop() throws Exception {
        if (toolClientMap != null) {
            for (McpSyncClient mcpClient : new HashSet<>(toolClientMap.values())) {
                try {
                    mcpClient.closeGracefully();
                } catch (Exception e) {
                    LOG.warn("Error closing MCP client: {}", e.getMessage(), e);
                }
            }
            toolClientMap.clear();
        }
        if (cachedMcpTools != null) {
            cachedMcpTools.clear();
        }
        if (returnDirectTools != null) {
            returnDirectTools.clear();
        }
        if (client != null) {
            client = null;
        }
        super.doStop();
    }

    private void initializeMcpServers() throws Exception {
        Map<String, Object> mcpServerConfig = configuration.getMcpServer();
        if (mcpServerConfig == null || mcpServerConfig.isEmpty()) {
            LOG.debug("No MCP server configuration found, skipping MCP initialization");
            return;
        }
        LOG.debug("Initializing MCP servers from configuration: {}", mcpServerConfig.keySet());

        cachedMcpTools = new ArrayList<>();
        toolClientMap = new HashMap<>();
        returnDirectTools = new HashSet<>();
        toolToServerName = new HashMap<>();

        // Group flat keys by server name: "fs.transportType" -> {"fs": {"transportType": ...}}
        serverConfigs = new HashMap<>();
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

        for (Map.Entry<String, Map<String, String>> entry : serverConfigs.entrySet()) {
            String serverName = entry.getKey();
            Map<String, String> props = entry.getValue();

            String transportType = props.get("transportType");
            if (transportType == null) {
                throw new IllegalArgumentException("mcpServer." + serverName + ".transportType is required");
            }

            LOG.debug("Creating MCP transport for server '{}' with type '{}'", serverName, transportType);
            McpClientTransport transport = createMcpTransport(serverName, transportType, props);
            Duration timeout = Duration.ofSeconds(configuration.getMcpTimeout());
            McpSyncClient mcpClient = McpClient.sync(transport)
                    .requestTimeout(timeout)
                    .initializationTimeout(timeout)
                    .build();
            mcpClient.initialize();
            LOG.debug("MCP server '{}' initialized, listing tools", serverName);

            McpSchema.ListToolsResult toolsResult = mcpClient.listTools();
            List<McpSchema.Tool> tools = toolsResult.tools();

            cachedMcpTools.addAll(McpToolConverter.convert(tools));

            for (McpSchema.Tool tool : tools) {
                if (toolClientMap.putIfAbsent(tool.name(), mcpClient) != null) {
                    LOG.warn("Duplicate MCP tool name '{}' from server '{}', using first registered", tool.name(),
                            serverName);
                } else {
                    toolToServerName.put(tool.name(), serverName);
                }

                if (tool.annotations() != null && Boolean.TRUE.equals(tool.annotations().returnDirect())) {
                    returnDirectTools.add(tool.name());
                }
            }

            LOG.info("Initialized MCP server '{}' with {} tools: {}", serverName, tools.size(),
                    tools.stream().map(McpSchema.Tool::name).toList());
        }
    }

    private McpClientTransport createMcpTransport(String serverName, String transportType, Map<String, String> props)
            throws Exception {
        // Resolve per-server OAuth token if configured
        String mcpOauthProfile = props.get("oauthProfile");
        HttpRequest.Builder authRequestBuilder = null;
        if (ObjectHelper.isNotEmpty(mcpOauthProfile)) {
            String token = OAuthHelper.resolveOAuthToken(getCamelContext(), mcpOauthProfile);
            authRequestBuilder = HttpRequest.newBuilder()
                    .header("Authorization", "Bearer " + token);
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
            case "streamableHttp" -> {
                String url = props.get("url");
                if (url == null) {
                    throw new IllegalArgumentException(
                            "mcpServer." + serverName + ".url is required for streamableHttp transport");
                }
                HttpClientStreamableHttpTransport.Builder transportBuilder
                        = HttpClientStreamableHttpTransport.builder(url);
                List<String> protocolVersions = parseMcpProtocolVersions();
                if (protocolVersions != null) {
                    transportBuilder.supportedProtocolVersions(protocolVersions);
                }
                if (authRequestBuilder != null) {
                    transportBuilder.requestBuilder(authRequestBuilder);
                }
                yield transportBuilder.build();
            }
            default -> throw new IllegalArgumentException(
                    "Unknown transport type '" + transportType + "' for mcpServer." + serverName
                                                          + ". Supported: stdio, sse, streamableHttp");
        };
    }

    private List<String> parseMcpProtocolVersions() {
        String versions = configuration.getMcpProtocolVersions();
        if (versions == null || versions.isBlank()) {
            return null;
        }
        return List.of(versions.split(","));
    }

    /**
     * Reconnects the MCP server for the given tool name. Closes the old client, creates a new transport and client,
     * re-lists tools, and updates all internal mappings.
     *
     * @param  toolName the tool whose server needs reconnecting
     * @return          the new McpSyncClient, or null if reconnection failed
     */
    McpSyncClient reconnectMcpServer(String toolName) {
        String serverName = toolToServerName.get(toolName);
        if (serverName == null || serverConfigs == null) {
            LOG.warn("Cannot reconnect: no server configuration found for tool '{}'", toolName);
            return null;
        }

        Map<String, String> props = serverConfigs.get(serverName);
        if (props == null) {
            LOG.warn("Cannot reconnect: no configuration found for server '{}'", serverName);
            return null;
        }

        LOG.info("Reconnecting MCP server '{}' for tool '{}'", serverName, toolName);

        // Close the old client for this server
        McpSyncClient oldClient = toolClientMap.get(toolName);
        if (oldClient != null) {
            try {
                oldClient.closeGracefully();
            } catch (Exception e) {
                LOG.debug("Error closing old MCP client for server '{}': {}", serverName, e.getMessage());
            }
        }

        try {
            String transportType = props.get("transportType");
            McpClientTransport transport = createMcpTransport(serverName, transportType, props);
            Duration timeout = Duration.ofSeconds(configuration.getMcpTimeout());
            McpSyncClient newClient = McpClient.sync(transport)
                    .requestTimeout(timeout)
                    .initializationTimeout(timeout)
                    .build();
            newClient.initialize();

            // Re-list tools and update mappings for all tools from this server
            McpSchema.ListToolsResult toolsResult = newClient.listTools();
            List<McpSchema.Tool> tools = toolsResult.tools();

            // Remove old mappings for this server
            toolClientMap.entrySet().removeIf(e -> e.getValue() == oldClient);
            cachedMcpTools.removeIf(t -> {
                String name = t.function().name();
                return serverName.equals(toolToServerName.get(name));
            });

            // Add new mappings
            cachedMcpTools.addAll(McpToolConverter.convert(tools));
            for (McpSchema.Tool tool : tools) {
                toolClientMap.put(tool.name(), newClient);
                toolToServerName.put(tool.name(), serverName);

                if (tool.annotations() != null && Boolean.TRUE.equals(tool.annotations().returnDirect())) {
                    returnDirectTools.add(tool.name());
                }
            }

            LOG.info("Reconnected MCP server '{}' with {} tools: {}", serverName, tools.size(),
                    tools.stream().map(McpSchema.Tool::name).toList());
            return newClient;
        } catch (Exception e) {
            LOG.error("Failed to reconnect MCP server '{}': {}", serverName, e.getMessage(), e);
            return null;
        }
    }

    McpSchema.CallToolResult callTool(McpSyncClient mcpClient, String toolName, Map<String, Object> argsMap) {
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(toolName, argsMap);
        try {
            return mcpClient.callTool(request);
        } catch (McpTransportException e) {
            if (!configuration.isMcpReconnect()) {
                throw e;
            }
            LOG.info("Transport error calling tool '{}', attempting reconnect: {}", toolName, e.getMessage());
            McpSyncClient newClient = reconnectMcpServer(toolName);
            if (newClient == null) {
                throw e;
            }
            return newClient.callTool(request);
        }
    }

    protected OpenAIClient createClient() throws Exception {
        String apiKey = resolveApiKey();

        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder();

        if (ObjectHelper.isNotEmpty(apiKey)) {
            builder.apiKey(apiKey);
        }

        builder.baseUrl(ObjectHelper.notNullOrEmpty(configuration.getBaseUrl(), "baseUrl"));

        return builder.build();
    }

    protected String resolveApiKey() throws Exception {
        // Priority: URI parameter > OAuth profile > environment variable > system property
        if (ObjectHelper.isNotEmpty(configuration.getApiKey())) {
            return configuration.getApiKey();
        }

        // Try OAuth profile if configured
        if (ObjectHelper.isNotEmpty(configuration.getOauthProfile())) {
            return resolveOAuthToken();
        }

        String envApiKey = System.getenv("OPENAI_API_KEY");
        if (ObjectHelper.isNotEmpty(envApiKey)) {
            return envApiKey;
        }

        return System.getProperty("openai.api.key");
    }

    private String resolveOAuthToken() throws Exception {
        return OAuthHelper.resolveOAuthToken(getCamelContext(), configuration.getOauthProfile());
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public OpenAIConfiguration getConfiguration() {
        return configuration;
    }

    public OpenAIClient getClient() {
        return client;
    }

    public List<ChatCompletionFunctionTool> getMcpTools() {
        return cachedMcpTools;
    }

    public Map<String, McpSyncClient> getToolClientMap() {
        return toolClientMap;
    }

    public Set<String> getReturnDirectTools() {
        return returnDirectTools;
    }

    // Package-private setters for testing
    void setMcpTools(List<ChatCompletionFunctionTool> tools) {
        this.cachedMcpTools = tools;
    }

    void setToolClientMap(Map<String, McpSyncClient> map) {
        this.toolClientMap = map;
    }

    void setReturnDirectTools(Set<String> tools) {
        this.returnDirectTools = tools;
    }
}
