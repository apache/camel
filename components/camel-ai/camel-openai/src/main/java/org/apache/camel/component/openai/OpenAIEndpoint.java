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

import java.io.FileInputStream;
import java.net.http.HttpRequest;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

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
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenAI endpoint for chat completion, embeddings, and audio transcription.
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
    @Metadata(required = true,
              description = "The operation to perform: 'chat-completion', 'embeddings', 'tool-execution', or 'audio-transcription'")
    private OpenAIOperations operation;

    @UriParam
    private OpenAIConfiguration configuration;

    private OpenAIClient client;

    private final ReentrantLock globalMcpLock = new ReentrantLock();
    private Map<String, ReentrantLock> mcpClientLocks;

    private volatile McpToolState mcpToolState = McpToolState.empty();
    private volatile boolean mcpStopped;
    private Map<String, Map<String, String>> serverConfigs;

    public OpenAIEndpoint(String uri, OpenAIComponent component, OpenAIConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return switch (operation) {
            case chatCompletion -> new OpenAIProducer(this);
            case embeddings -> new OpenAIEmbeddingsProducer(this);
            case toolExecution -> new OpenAIToolExecutionProducer(this);
            case audioTranscription -> new OpenAIAudioTranscriptionProducer(this);
        };
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported for OpenAI component");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        mcpStopped = false;
        client = createClient();
        initializeMcpServers();
    }

    @Override
    protected void doStop() throws Exception {
        Set<McpSyncClient> toClose;
        globalMcpLock.lock();
        try {
            mcpStopped = true;
            toClose = new HashSet<>(mcpToolState.toolClientMap().values());
            mcpToolState = McpToolState.empty();
        } finally {
            globalMcpLock.unlock();
        }

        for (McpSyncClient mcpClient : toClose) {
            try {
                mcpClient.closeGracefully();
            } catch (Exception e) {
                LOG.warn("Error closing MCP client: {}", e.getMessage(), e);
            }
        }

        if (client != null) {
            client.close();
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

        mcpClientLocks = new HashMap<>();

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
            mcpClientLocks.putIfAbsent(serverName, new ReentrantLock());
        }

        List<ChatCompletionFunctionTool> tools = new ArrayList<>();
        Map<String, McpSyncClient> toolClientMap = new HashMap<>();
        Map<String, String> toolToServerName = new HashMap<>();
        Set<String> returnDirectTools = new HashSet<>();

        for (Map.Entry<String, Map<String, String>> entry : serverConfigs.entrySet()) {
            String serverName = entry.getKey();
            Map<String, String> props = entry.getValue();

            if (props.get("transportType") == null) {
                throw new IllegalArgumentException("mcpServer." + serverName + ".transportType is required");
            }

            McpSyncClient mcpClient = createMcpClient(serverName, props);
            LOG.debug("MCP server '{}' initialized, listing tools", serverName);

            McpSchema.ListToolsResult toolsResult = mcpClient.listTools();
            List<McpSchema.Tool> serverTools = toolsResult.tools();

            tools.addAll(McpToolConverter.convert(serverTools));

            for (McpSchema.Tool tool : serverTools) {
                if (toolClientMap.putIfAbsent(tool.name(), mcpClient) != null) {
                    LOG.warn("Duplicate MCP tool name '{}' from server '{}', using first registered", tool.name(),
                            serverName);
                } else {
                    toolToServerName.put(tool.name(), serverName);
                }

                if (isReturnDirect(tool)) {
                    returnDirectTools.add(tool.name());
                }
            }

            LOG.info("Initialized MCP server '{}' with {} tools: {}", serverName, serverTools.size(),
                    serverTools.stream().map(McpSchema.Tool::name).toList());
        }

        mcpToolState = new McpToolState(tools, toolClientMap, toolToServerName, returnDirectTools);
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

    /**
     * Creates and initializes an MCP client for the given server.
     */
    McpSyncClient createMcpClient(String serverName, Map<String, String> props) throws Exception {
        String transportType = props.get("transportType");
        LOG.debug("Creating MCP transport for server '{}' with type '{}'", serverName, transportType);
        McpClientTransport transport = createMcpTransport(serverName, transportType, props);
        Duration timeout = Duration.ofSeconds(configuration.getMcpTimeout());
        McpSyncClient mcpClient = McpClient.sync(transport)
                .requestTimeout(timeout)
                .initializationTimeout(timeout)
                .build();
        mcpClient.initialize();
        return mcpClient;
    }

    private List<String> parseMcpProtocolVersions() {
        String versions = configuration.getMcpProtocolVersions();
        if (versions == null || versions.isBlank()) {
            return null;
        }
        return List.of(versions.split(","));
    }

    /**
     * Reconnects the MCP server that owns the given tool. Serializes concurrent reconnects of the same server via a
     * per-server lock and skips the reconnect if another thread already replaced the failed client.
     *
     * @param  oldClient the client that failed and should be replaced
     * @param  toolName  the tool whose server needs reconnecting
     * @return           the new (or already-reconnected) McpSyncClient, or null if reconnection failed
     */
    McpSyncClient reconnectMcpServer(McpSyncClient oldClient, String toolName) {
        String serverName = mcpToolState.toolToServerName().get(toolName);
        if (serverName == null || serverConfigs == null) {
            LOG.warn("Cannot reconnect: no server configuration found for tool '{}'", toolName);
            return null;
        }

        ReentrantLock lock = mcpClientLocks.get(serverName);
        if (lock == null) {
            LOG.warn("Cannot reconnect: no lock found for server '{}'", serverName);
            return null;
        }

        lock.lock();
        try {
            McpSyncClient currClient = mcpToolState.toolClientMap().get(toolName);
            if (currClient != null && currClient != oldClient) {
                return currClient;
            }

            LOG.info("Reconnecting MCP server '{}' for tool '{}'", serverName, toolName);
            return doReconnectMcpServer(oldClient, serverName);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Performs the actual reconnect work: closes the old client, creates a new transport and client, re-lists tools,
     * and republishes the shared tool state. The caller must hold the per-server lock for {@code serverName}.
     *
     * @param  oldClient  the old McpSyncClient to close
     * @param  serverName the name of the MCP server to reconnect
     * @return            the new McpSyncClient, or null if reconnection failed
     */
    private McpSyncClient doReconnectMcpServer(McpSyncClient oldClient, String serverName) {
        Map<String, String> props = serverConfigs.get(serverName);
        if (props == null) {
            LOG.warn("Cannot reconnect: no configuration found for server '{}'", serverName);
            return null;
        }

        // Close the old client for this server
        if (oldClient != null) {
            try {
                oldClient.closeGracefully();
            } catch (Exception e) {
                LOG.debug("Error closing old MCP client for server '{}': {}", serverName, e.getMessage());
            }
        }

        try {
            McpSyncClient newClient = createMcpClient(serverName, props);

            List<McpSchema.Tool> tools = newClient.listTools().tools();

            globalMcpLock.lock();
            try {
                if (mcpStopped) {
                    newClient.closeGracefully();
                    return null;
                }
                // Rebuild shared tool state, replacing this server's old tools with the freshly listed ones
                Set<String> oldServerTools = toolsForServer(serverName);
                List<ChatCompletionFunctionTool> newTools = new ArrayList<>(mcpToolState.tools());
                Map<String, McpSyncClient> newClientMap = new HashMap<>(mcpToolState.toolClientMap());
                Map<String, String> newToolToServer = new HashMap<>(mcpToolState.toolToServerName());
                Set<String> newReturnDirect = new HashSet<>(mcpToolState.returnDirectTools());

                newTools.removeIf(t -> oldServerTools.contains(t.function().name()));
                newClientMap.keySet().removeIf(oldServerTools::contains);
                newToolToServer.keySet().removeIf(oldServerTools::contains);
                newReturnDirect.removeIf(oldServerTools::contains);

                newTools.addAll(McpToolConverter.convert(tools));
                for (McpSchema.Tool tool : tools) {
                    newClientMap.put(tool.name(), newClient);
                    newToolToServer.put(tool.name(), serverName);
                    if (isReturnDirect(tool)) {
                        newReturnDirect.add(tool.name());
                    }
                }

                // Publish immutable snapshots for lock-free readers
                mcpToolState = new McpToolState(newTools, newClientMap, newToolToServer, newReturnDirect);
            } finally {
                globalMcpLock.unlock();
            }

            LOG.info("Reconnected MCP server '{}' with {} tools: {}", serverName, tools.size(),
                    tools.stream().map(McpSchema.Tool::name).toList());
            return newClient;
        } catch (Exception e) {
            LOG.error("Failed to reconnect MCP server '{}': {}", serverName, e.getMessage(), e);
            return null;
        }
    }

    private Set<String> toolsForServer(String serverName) {
        return mcpToolState.toolToServerName().entrySet().stream()
                .filter(e -> serverName.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private static boolean isReturnDirect(McpSchema.Tool tool) {
        return tool.annotations() != null && Boolean.TRUE.equals(tool.annotations().returnDirect());
    }

    McpSchema.CallToolResult callTool(McpSyncClient mcpClient, String toolName, Map<String, Object> argsMap) {
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(toolName, argsMap, null);
        try {
            return mcpClient.callTool(request);
        } catch (McpTransportException e) {
            if (!configuration.isMcpReconnect()) {
                throw e;
            }
            LOG.info("Transport error calling tool '{}', attempting reconnect: {}", toolName, e.getMessage());
            McpSyncClient newClient = reconnectMcpServer(mcpClient, toolName);
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

        configureSsl(builder);

        return builder.build();
    }

    private void configureSsl(OpenAIOkHttpClient.Builder builder) throws Exception {
        // SSLContextParameters takes precedence over individual SSL properties
        if (configuration.getSslContextParameters() != null) {
            configureSslFromContextParameters(builder, configuration.getSslContextParameters());
            return;
        }

        configureSslFromProperties(builder);
    }

    private void configureSslFromContextParameters(
            OpenAIOkHttpClient.Builder builder,
            SSLContextParameters sslContextParameters)
            throws Exception {
        SSLContext sslContext = sslContextParameters.createSSLContext(getCamelContext());

        // OpenAIOkHttpClient requires both sslSocketFactory and trustManager to be set together
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null);
        X509TrustManager x509TrustManager = (X509TrustManager) tmf.getTrustManagers()[0];

        // If SSLContextParameters has trust managers configured, try to extract them
        if (sslContextParameters.getTrustManagers() != null) {
            TrustManager[] trustManagers = sslContextParameters.getTrustManagers().createTrustManagers();
            if (trustManagers != null && trustManagers.length > 0 && trustManagers[0] instanceof X509TrustManager) {
                x509TrustManager = (X509TrustManager) trustManagers[0];
            }
        }

        builder.sslSocketFactory(sslContext.getSocketFactory());
        builder.trustManager(x509TrustManager);
    }

    private void configureSslFromProperties(OpenAIOkHttpClient.Builder builder) throws Exception {
        boolean hasTrustStore = ObjectHelper.isNotEmpty(configuration.getSslTruststoreLocation());
        boolean hasKeyStore = ObjectHelper.isNotEmpty(configuration.getSslKeystoreLocation());

        if (!hasTrustStore && !hasKeyStore) {
            return;
        }

        TrustManager[] trustManagers = null;
        if (hasTrustStore) {
            KeyStore trustStore = KeyStore.getInstance(configuration.getSslTruststoreType());
            char[] trustStorePassword = configuration.getSslTruststorePassword() != null
                    ? configuration.getSslTruststorePassword().toCharArray() : null;
            try (FileInputStream fis = new FileInputStream(configuration.getSslTruststoreLocation())) {
                trustStore.load(fis, trustStorePassword);
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(configuration.getSslTrustmanagerAlgorithm());
            tmf.init(trustStore);
            trustManagers = tmf.getTrustManagers();
        }

        KeyManager[] keyManagers = null;
        if (hasKeyStore) {
            KeyStore keyStore = KeyStore.getInstance(configuration.getSslKeystoreType());
            char[] keyStorePassword = configuration.getSslKeystorePassword() != null
                    ? configuration.getSslKeystorePassword().toCharArray() : null;
            try (FileInputStream fis = new FileInputStream(configuration.getSslKeystoreLocation())) {
                keyStore.load(fis, keyStorePassword);
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(configuration.getSslKeymanagerAlgorithm());
            char[] keyPassword = configuration.getSslKeyPassword() != null
                    ? configuration.getSslKeyPassword().toCharArray() : keyStorePassword;
            kmf.init(keyStore, keyPassword);
            keyManagers = kmf.getKeyManagers();
        }

        SSLContext sslContext = SSLContext.getInstance(configuration.getSslProtocol());
        sslContext.init(keyManagers, trustManagers, null);

        // OpenAIOkHttpClient requires both sslSocketFactory and trustManager to be set together
        X509TrustManager x509TrustManager;
        if (trustManagers != null) {
            x509TrustManager = (X509TrustManager) trustManagers[0];
        } else {
            // When only keystore is configured, use the default trust manager
            TrustManagerFactory defaultTmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            defaultTmf.init((KeyStore) null);
            x509TrustManager = (X509TrustManager) defaultTmf.getTrustManagers()[0];
        }

        builder.sslSocketFactory(sslContext.getSocketFactory());
        builder.trustManager(x509TrustManager);

        // Configure hostname verification
        String endpointAlgorithm = configuration.getSslEndpointAlgorithm();
        if (ObjectHelper.isEmpty(endpointAlgorithm) || "none".equalsIgnoreCase(endpointAlgorithm)) {
            builder.hostnameVerifier((hostname, session) -> true);
        }
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

    public OpenAIOperations getOperation() {
        return operation;
    }

    public void setOperation(OpenAIOperations operation) {
        this.operation = operation;
    }

    public OpenAIConfiguration getConfiguration() {
        return configuration;
    }

    public OpenAIClient getClient() {
        return client;
    }

    public void addReturnDirectTool(String toolName) {
        globalMcpLock.lock();
        try {
            Set<String> newReturnDirect = new HashSet<>(mcpToolState.returnDirectTools());
            newReturnDirect.add(toolName);
            mcpToolState = new McpToolState(
                    mcpToolState.tools(), mcpToolState.toolClientMap(),
                    mcpToolState.toolToServerName(), newReturnDirect);
        } finally {
            globalMcpLock.unlock();
        }
    }

    public void removeReturnDirectTool(String toolName) {
        globalMcpLock.lock();
        try {
            Set<String> newReturnDirect = new HashSet<>(mcpToolState.returnDirectTools());
            newReturnDirect.remove(toolName);
            mcpToolState = new McpToolState(
                    mcpToolState.tools(), mcpToolState.toolClientMap(),
                    mcpToolState.toolToServerName(), newReturnDirect);
        } finally {
            globalMcpLock.unlock();
        }
    }

    McpToolState getMcpToolState() {
        return mcpToolState;
    }

    // Package-private setters for testing

    void setMcpToolState(McpToolState state) {
        this.mcpToolState = state;
    }

    void setServerConfigs(Map<String, Map<String, String>> configs) {
        this.serverConfigs = configs;
    }

    void setMcpClientLocks(Map<String, ReentrantLock> locks) {
        this.mcpClientLocks = locks;
    }
}
