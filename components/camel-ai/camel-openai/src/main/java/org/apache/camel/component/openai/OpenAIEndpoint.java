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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
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
import com.openai.core.Timeout;
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
import org.apache.camel.component.ai.tool.AiToolParameterHelper;
import org.apache.camel.component.ai.tool.AiToolRegistry;
import org.apache.camel.component.ai.tool.AiToolRegistryListener;
import org.apache.camel.component.ai.tool.AiToolSpec;
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
 * OpenAI endpoint for chat completion, Responses API, embeddings, audio transcription, audio translation, and
 * text-to-speech.
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
              description = "The operation to perform: 'chat-completion', 'responses', 'embeddings', 'tool-execution', "
                            + "'audio-transcription', 'audio-translation', 'audio-speech', 'moderation', "
                            + "'image-generation', or 'image-edit'")
    private OpenAIOperations operation;

    @UriParam
    private OpenAIConfiguration configuration;

    private OpenAIClient client;

    private final ReentrantLock globalMcpLock = new ReentrantLock();
    private Map<String, ReentrantLock> mcpClientLocks;

    private final Set<String> manualReturnDirectAdded = ConcurrentHashMap.newKeySet();
    private final Set<String> manualReturnDirectRemoved = ConcurrentHashMap.newKeySet();

    /**
     * MCP servers that were unreachable when the endpoint started. Initialization is retried lazily on first use so
     * that an application acting as MCP client of itself (or of another service starting concurrently) does not fail
     * route startup on runtimes where the HTTP server only accepts connections after the application has started.
     */
    private final Set<String> pendingMcpServers = ConcurrentHashMap.newKeySet();

    private volatile McpToolState mcpToolState = McpToolState.empty();
    private volatile Map<String, AiToolSpec> routeTools = Map.of();
    private AiToolRegistryListener routeToolRegistryListener;
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
            case responses -> new OpenAIResponsesProducer(this);
            case embeddings -> new OpenAIEmbeddingsProducer(this);
            case toolExecution -> new OpenAIToolExecutionProducer(this);
            case audioTranscription -> new OpenAIAudioTranscriptionProducer(this);
            case audioTranslation -> new OpenAIAudioTranslationProducer(this);
            case audioSpeech -> new OpenAIAudioSpeechProducer(this);
            case moderation -> new OpenAIModerationProducer(this);
            case imageGeneration -> new OpenAIImageGenerationProducer(this);
            case imageEdit -> new OpenAIImageEditProducer(this);
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
        registerRouteToolRegistryListener();
        initializeMcpServers();
        refreshRouteTools();
    }

    @Override
    protected void doStop() throws Exception {
        Set<McpSyncClient> toClose;
        globalMcpLock.lock();
        try {
            mcpStopped = true;
            pendingMcpServers.clear();
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

        unregisterRouteToolRegistryListener();
        routeTools = Map.of();

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

            McpSyncClient mcpClient;
            List<McpSchema.Tool> serverTools;
            try {
                mcpClient = createMcpClient(serverName, props);
                LOG.debug("MCP server '{}' initialized, listing tools", serverName);

                McpSchema.ListToolsResult toolsResult = mcpClient.listTools();
                serverTools = filterTools(toolsResult.tools(), serverName, props);
            } catch (Exception e) {
                // do not fail route startup on an unreachable server: the server may simply not be
                // accepting connections yet (e.g. this application's own MCP endpoint on runtimes
                // where the HTTP server starts after the CamelContext); retry lazily on first use
                pendingMcpServers.add(serverName);
                LOG.warn("MCP server '{}' is unreachable at endpoint startup ({}); "
                         + "initialization deferred to first use",
                        serverName, e.getMessage());
                continue;
            }

            for (McpSchema.Tool tool : serverTools) {
                if (toolClientMap.putIfAbsent(tool.name(), mcpClient) != null) {
                    LOG.warn("Duplicate MCP tool name '{}' from server '{}', using first registered", tool.name(),
                            serverName);
                } else {
                    toolToServerName.put(tool.name(), serverName);
                    tools.addAll(McpToolConverter.convert(List.of(tool)));

                    if (isReturnDirect(tool)) {
                        returnDirectTools.add(tool.name());
                    }
                }
            }

            LOG.info("Initialized MCP server '{}' with {} tools: {}", serverName, serverTools.size(),
                    serverTools.stream().map(McpSchema.Tool::name).toList());
        }

        mcpToolState = new McpToolState(tools, toolClientMap, toolToServerName, returnDirectTools, Map.of());
    }

    void refreshRouteTools() {
        if (configuration == null || ObjectHelper.isEmpty(configuration.getTags())) {
            globalMcpLock.lock();
            try {
                if (mcpStopped) {
                    return;
                }
                routeTools = Map.of();
                republishCombinedState();
            } finally {
                globalMcpLock.unlock();
            }
            return;
        }

        Map<String, AiToolSpec> discovered
                = OpenAIRouteToolSupport.discoverRouteTools(getCamelContext(), configuration.getTags());
        globalMcpLock.lock();
        try {
            if (mcpStopped) {
                return;
            }
            routeTools = discovered;
            republishCombinedState();
        } finally {
            globalMcpLock.unlock();
        }
    }

    private void registerRouteToolRegistryListener() {
        if (ObjectHelper.isEmpty(configuration.getTags()) || routeToolRegistryListener != null) {
            return;
        }
        AiToolRegistry registry = AiToolRegistry.getOrCreate(getCamelContext());
        routeToolRegistryListener = new AiToolRegistryListener() {
            @Override
            public void toolRegistered(String tag, AiToolSpec spec) {
                if (matchesConfiguredTags(tag)) {
                    refreshRouteTools();
                }
            }

            @Override
            public void toolDeregistered(String tag, AiToolSpec spec) {
                if (matchesConfiguredTags(tag)) {
                    refreshRouteTools();
                }
            }
        };
        registry.addListener(routeToolRegistryListener);
    }

    private void unregisterRouteToolRegistryListener() {
        if (routeToolRegistryListener == null) {
            return;
        }
        AiToolRegistry registry = AiToolRegistry.getOrCreate(getCamelContext());
        registry.removeListener(routeToolRegistryListener);
        routeToolRegistryListener = null;
    }

    private boolean matchesConfiguredTags(String tag) {
        if (ObjectHelper.isEmpty(configuration.getTags())) {
            return false;
        }
        for (String configured : AiToolParameterHelper.splitTags(configuration.getTags())) {
            String trimmed = configured.trim();
            if (trimmed.isEmpty() && tag == null) {
                return true;
            }
            if (tag != null && tag.equals(trimmed)) {
                return true;
            }
        }
        return false;
    }

    private void republishCombinedState() {
        McpToolState current = mcpToolState;
        Set<String> routeNames = routeTools.keySet();

        // Keep only MCP-backed tools from the current snapshot; route entries are rebuilt below
        List<ChatCompletionFunctionTool> mcpTools = current.tools().stream()
                .filter(tool -> current.toolClientMap().containsKey(tool.function().name()))
                .filter(tool -> !routeNames.contains(tool.function().name()))
                .toList();

        Set<String> mcpReturnDirect = current.returnDirectTools().stream()
                .filter(name -> current.toolClientMap().containsKey(name))
                .filter(name -> !routeNames.contains(name))
                .collect(Collectors.toCollection(HashSet::new));

        Set<String> allReturnDirect = new HashSet<>(mcpReturnDirect);
        allReturnDirect.addAll(OpenAIRouteToolSupport.returnDirectToolNames(routeTools));

        Set<String> knownTools = new HashSet<>(current.toolClientMap().keySet());
        knownTools.addAll(routeNames);

        List<ChatCompletionFunctionTool> allTools = new ArrayList<>(mcpTools);
        allTools.addAll(OpenAIRouteToolSupport.toOpenAiTools(routeTools));

        mcpToolState = new McpToolState(
                allTools,
                current.toolClientMap(),
                current.toolToServerName(),
                applyManualReturnDirectOverrides(allReturnDirect, knownTools),
                routeTools);
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
        McpClient.SyncSpec spec = McpClient.sync(transport)
                .requestTimeout(timeout)
                .initializationTimeout(timeout);

        // The callback needs the client, which does not exist until build(), so hand it a holder that is
        // populated below. Notifications can only arrive once the transport is connected by initialize().
        AtomicReference<McpSyncClient> clientHolder = new AtomicReference<>();
        if (configuration.isMcpToolRefresh()) {
            spec.toolsChangeConsumer(tools -> onToolsChanged(serverName, clientHolder.get(), tools));
        }

        McpSyncClient mcpClient = spec.build();
        clientHolder.set(mcpClient);
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

            List<McpSchema.Tool> tools = filterTools(newClient.listTools().tools(), serverName, props);

            if (!republishServerTools(serverName, newClient, tools)) {
                newClient.closeGracefully();
                return null;
            }

            LOG.info("Reconnected MCP server '{}' with {} tools: {}", serverName, tools.size(),
                    tools.stream().map(McpSchema.Tool::name).toList());
            return newClient;
        } catch (Exception e) {
            LOG.error("Failed to reconnect MCP server '{}': {}", serverName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Rebuilds and publishes the shared tool state, replacing the tools currently registered for {@code serverName}
     * with the given ones. Shared by the reconnect and the runtime tool refresh paths so that duplicate name handling,
     * {@code returnDirect} detection and the manual {@code returnDirect} overrides stay identical between the two.
     *
     * @param  serverName the MCP server whose tools are being replaced
     * @param  client     the client owning the given tools
     * @param  tools      the tools to register, already filtered by the per-server {@code toolNames} include list
     * @return            {@code false} when the endpoint is stopping and the state was left untouched
     */
    private boolean republishServerTools(String serverName, McpSyncClient client, List<McpSchema.Tool> tools) {
        globalMcpLock.lock();
        try {
            if (mcpStopped) {
                return false;
            }

            Set<String> oldServerTools = toolsForServer(serverName);
            List<ChatCompletionFunctionTool> mcpTools = mcpToolState.tools().stream()
                    .filter(tool -> mcpToolState.toolClientMap().containsKey(tool.function().name()))
                    .filter(tool -> !oldServerTools.contains(tool.function().name()))
                    .collect(Collectors.toCollection(ArrayList::new));
            Map<String, McpSyncClient> newClientMap = new HashMap<>(mcpToolState.toolClientMap());
            Map<String, String> newToolToServer = new HashMap<>(mcpToolState.toolToServerName());
            Set<String> mcpReturnDirect = mcpToolState.returnDirectTools().stream()
                    .filter(name -> mcpToolState.toolClientMap().containsKey(name))
                    .filter(name -> !oldServerTools.contains(name))
                    .collect(Collectors.toCollection(HashSet::new));

            newClientMap.keySet().removeIf(oldServerTools::contains);
            newToolToServer.keySet().removeIf(oldServerTools::contains);

            for (McpSchema.Tool tool : tools) {
                if (newClientMap.containsKey(tool.name())) {
                    LOG.warn("Duplicate MCP tool name '{}' from server '{}', using first registered", tool.name(),
                            serverName);
                } else {
                    mcpTools.addAll(McpToolConverter.convert(List.of(tool)));
                    newClientMap.put(tool.name(), client);
                    newToolToServer.put(tool.name(), serverName);
                    if (isReturnDirect(tool)) {
                        mcpReturnDirect.add(tool.name());
                    }
                }
            }

            // Publish MCP-only snapshot, then merge route tools with the same shadowing rules
            mcpToolState = new McpToolState(
                    mcpTools, newClientMap, newToolToServer,
                    applyManualReturnDirectOverrides(mcpReturnDirect, knownToolsFrom(newClientMap, Map.of())),
                    mcpToolState.routeTools());
            republishCombinedState();
            return true;
        } finally {
            globalMcpLock.unlock();
        }
    }

    /**
     * Handles a {@code tools/list_changed} notification from an MCP server. The SDK has already re-listed the tools and
     * passes them here, but without the per-server {@code toolNames} filter, which is re-applied before publishing.
     *
     * @param serverName the server that reported the change
     * @param client     the client that received the notification
     * @param tools      the unfiltered tool list as re-listed by the SDK
     */
    void onToolsChanged(String serverName, McpSyncClient client, List<McpSchema.Tool> tools) {
        if (!configuration.isMcpToolRefresh()) {
            return;
        }

        if (client == null) {
            // Notifications only flow after initialize(), which runs once the holder is populated
            LOG.debug("Ignoring tools list change from MCP server '{}' received before the client was ready", serverName);
            return;
        }

        Map<String, String> props = serverConfigs != null ? serverConfigs.get(serverName) : null;
        if (props == null) {
            LOG.warn("Cannot refresh tools: no configuration found for MCP server '{}'", serverName);
            return;
        }

        globalMcpLock.lock();
        try {
            McpSyncClient current = currentClientForServer(serverName);
            if (current != null && current != client) {
                // A reconnect already replaced this client, so its view of the tools is stale
                LOG.debug("Ignoring tools list change from a superseded client of MCP server '{}'", serverName);
                return;
            }

            List<McpSchema.Tool> filtered = filterTools(tools, serverName, props);
            if (republishServerTools(serverName, client, filtered)) {
                LOG.info("Refreshed MCP server '{}' with {} tools: {}", serverName, filtered.size(),
                        filtered.stream().map(McpSchema.Tool::name).toList());
            }
        } finally {
            globalMcpLock.unlock();
        }
    }

    private McpSyncClient currentClientForServer(String serverName) {
        return toolsForServer(serverName).stream()
                .map(toolName -> mcpToolState.toolClientMap().get(toolName))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * Re-applies the {@code returnDirect} flags set programmatically through {@link #addReturnDirectTool(String)} and
     * {@link #removeReturnDirectTool(String)}, so that a reconnect or a tool refresh does not discard them. Overrides
     * for tools that no longer exist are not reinstated, so that a tool which vanished server-side leaves no stale
     * entry behind.
     *
     * @param returnDirectTools the flags rebuilt from the tool annotations, modified in place
     * @param knownTools        the names of the tools registered in the new state
     */
    private Set<String> applyManualReturnDirectOverrides(Set<String> returnDirectTools, Set<String> knownTools) {
        manualReturnDirectAdded.stream().filter(knownTools::contains).forEach(returnDirectTools::add);
        returnDirectTools.removeAll(manualReturnDirectRemoved);
        return returnDirectTools;
    }

    /**
     * Filters the tools listed from an MCP server according to the per-server {@code toolNames} include list. When no
     * {@code toolNames} property is configured, all tools are returned unchanged.
     *
     * @param  allTools   the full list of tools from the server
     * @param  serverName the logical server name (for logging)
     * @param  props      the per-server configuration properties
     * @return            the filtered tool list (or the original list if no filter is configured)
     */
    private List<McpSchema.Tool> filterTools(List<McpSchema.Tool> allTools, String serverName, Map<String, String> props) {
        String toolNamesCsv = props.get("toolNames");
        if (toolNamesCsv == null || toolNamesCsv.isBlank()) {
            return allTools;
        }

        Set<String> allowed = new HashSet<>();
        for (String name : toolNamesCsv.split(",")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                allowed.add(trimmed);
            }
        }

        List<McpSchema.Tool> filtered = allTools.stream()
                .filter(t -> allowed.contains(t.name()))
                .toList();

        Set<String> found = filtered.stream().map(McpSchema.Tool::name).collect(Collectors.toSet());
        Set<String> missing = new HashSet<>(allowed);
        missing.removeAll(found);
        if (!missing.isEmpty()) {
            LOG.warn("MCP server '{}' does not provide the following toolNames: {}", serverName, missing);
        }

        LOG.info("MCP server '{}': filtered {} tools to {} via toolNames include list",
                serverName, allTools.size(), filtered.size());
        return filtered;
    }

    private Set<String> toolsForServer(String serverName) {
        return mcpToolState.toolToServerName().entrySet().stream()
                .filter(e -> serverName.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private static Set<String> knownToolsFrom(Map<String, McpSyncClient> toolClientMap, Map<String, AiToolSpec> routeTools) {
        Set<String> knownTools = new HashSet<>(toolClientMap.keySet());
        knownTools.addAll(routeTools.keySet());
        return knownTools;
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

        configureHttpClient(builder);
        configureSsl(builder);

        return builder.build();
    }

    private void configureHttpClient(OpenAIOkHttpClient.Builder builder) {
        Timeout timeout = buildTimeout();
        if (timeout != null) {
            builder.timeout(timeout);
        }
        builder.maxRetries(configuration.getMaxRetries());
        Map<String, Object> additionalHeaders = configuration.getAdditionalHeader();
        if (additionalHeaders != null) {
            additionalHeaders.forEach((name, value) -> {
                if (value != null) {
                    builder.putHeader(name, value.toString());
                }
            });
        }
    }

    /**
     * Builds the SDK timeout from the configured phases, or null when none is set so the SDK defaults apply. Setting
     * only requestTimeout produces the same result as the single-duration builder method it replaces.
     */
    private Timeout buildTimeout() {
        long request = configuration.getRequestTimeout();
        long connect = configuration.getConnectTimeout();
        long read = configuration.getReadTimeout();
        long write = configuration.getWriteTimeout();
        if (request <= 0 && connect <= 0 && read <= 0 && write <= 0) {
            return null;
        }

        Timeout.Builder timeout = Timeout.builder();
        if (request > 0) {
            timeout.request(Duration.ofMillis(request));
        }
        if (connect > 0) {
            timeout.connect(Duration.ofMillis(connect));
        }
        if (read > 0) {
            timeout.read(Duration.ofMillis(read));
        }
        if (write > 0) {
            timeout.write(Duration.ofMillis(write));
        }
        return timeout.build();
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
            // Remembered so a reconnect or a tool refresh, which rebuild the flags from the tool annotations,
            // does not silently discard the override
            manualReturnDirectAdded.add(toolName);
            manualReturnDirectRemoved.remove(toolName);

            Set<String> newReturnDirect = new HashSet<>(mcpToolState.returnDirectTools());
            newReturnDirect.add(toolName);
            mcpToolState = new McpToolState(
                    mcpToolState.tools(), mcpToolState.toolClientMap(),
                    mcpToolState.toolToServerName(), newReturnDirect, mcpToolState.routeTools());
        } finally {
            globalMcpLock.unlock();
        }
    }

    public void removeReturnDirectTool(String toolName) {
        globalMcpLock.lock();
        try {
            manualReturnDirectRemoved.add(toolName);
            manualReturnDirectAdded.remove(toolName);

            Set<String> newReturnDirect = new HashSet<>(mcpToolState.returnDirectTools());
            newReturnDirect.remove(toolName);
            mcpToolState = new McpToolState(
                    mcpToolState.tools(), mcpToolState.toolClientMap(),
                    mcpToolState.toolToServerName(), newReturnDirect, mcpToolState.routeTools());
        } finally {
            globalMcpLock.unlock();
        }
    }

    McpToolState getMcpToolState() {
        if (!pendingMcpServers.isEmpty()) {
            initializePendingMcpServers();
        }
        return mcpToolState;
    }

    /**
     * Retries the initialization of MCP servers that were unreachable when the endpoint started. Runs at most one
     * initialization attempt per server at a time via the per-server locks; a thread that finds a server being
     * initialized by another thread simply skips it and serves the current tool state.
     */
    private void initializePendingMcpServers() {
        for (String serverName : pendingMcpServers) {
            ReentrantLock lock = mcpClientLocks.get(serverName);
            if (lock == null || !lock.tryLock()) {
                continue;
            }
            try {
                if (mcpStopped || !pendingMcpServers.contains(serverName)) {
                    continue;
                }
                if (doReconnectMcpServer(null, serverName) != null) {
                    pendingMcpServers.remove(serverName);
                }
            } finally {
                lock.unlock();
            }
        }
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
