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
package org.apache.camel.component.langchain4j.agent;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.output.JsonSchemas;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.langchain4j.agent.api.AbstractAgent;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AgentFactory;
import org.apache.camel.component.langchain4j.agent.api.AgentWithMemory;
import org.apache.camel.component.langchain4j.agent.api.AgentWithoutMemory;
import org.apache.camel.component.langchain4j.agent.api.AiAgentBody;
import org.apache.camel.component.langchain4j.agent.api.CompositeToolProvider;
import org.apache.camel.component.langchain4j.agent.api.Headers;
import org.apache.camel.component.langchain4j.tools.spec.CamelToolExecutorCache;
import org.apache.camel.component.langchain4j.tools.spec.CamelToolSpecification;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LangChain4jAgentProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(LangChain4jAgentProducer.class);

    private final LangChain4jAgentEndpoint endpoint;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private AgentFactory agentFactory;
    private Agent agent;
    private List<McpClient> materializedMcpClients;

    public LangChain4jAgentProducer(LangChain4jAgentEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        boolean hasStructuredOutput = endpoint.getConfiguration().getOutputClass() != null
                || ObjectHelper.isNotEmpty(endpoint.getConfiguration().getJsonSchema());
        if (hasStructuredOutput) {
            if (endpoint.getConfiguration().getAgentConfiguration() == null) {
                throw new IllegalArgumentException(
                        "outputClass and jsonSchema require agentConfiguration to be set "
                                                   + "(inline agent creation mode). They cannot be used with a user-provided agent bean or agentFactory.");
            }
            if (endpoint.getConfiguration().getAgent() != null) {
                throw new IllegalArgumentException(
                        "outputClass and jsonSchema cannot be combined with a user-provided agent bean. "
                                                   + "They only work in inline agent creation mode (agentConfiguration without agent or agentFactory).");
            }
            if (endpoint.getConfiguration().getAgentFactory() != null) {
                throw new IllegalArgumentException(
                        "outputClass and jsonSchema cannot be combined with agentFactory. "
                                                   + "They only work in inline agent creation mode (agentConfiguration without agent or agentFactory).");
            }
            if (ObjectHelper.isNotEmpty(endpoint.getConfiguration().getJsonSchema())
                    && endpoint.getConfiguration().getOutputClass() != null) {
                throw new IllegalArgumentException(
                        "jsonSchema and outputClass are mutually exclusive. Please specify only one.");
            }
        }

        if (endpoint.getConfiguration().getAgent() != null) {
            agent = endpoint.getConfiguration().getAgent();
        } else if (endpoint.getConfiguration().getAgentConfiguration() != null) {
            AgentConfiguration agentConfiguration = endpoint.getConfiguration().getAgentConfiguration();
            agent = agentConfiguration.getChatMemoryProvider() != null
                    ? new AgentWithMemory(agentConfiguration)
                    : new AgentWithoutMemory(agentConfiguration);
            resolveAndApplyStructuredOutput();
        } else {
            agent = endpoint.getCamelContext().getRegistry().lookupByNameAndType(endpoint.getAgentId(), Agent.class);
        }
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Object messagePayload = exchange.getIn().getBody();
        ObjectHelper.notNull(messagePayload, "body");

        // tags for Camel Routes as Tools
        String tags = endpoint.getConfiguration().getTags();

        Agent agent = agentFactory != null ? agentFactory.createAgent(exchange) : this.agent;

        AiAgentBody<?> aiAgentBody = exchange.getMessage().getMandatoryBody(AiAgentBody.class);

        ToolProvider toolProvider = createComposedToolProvider(tags, exchange);
        Result<String> result = agent.chat(aiAgentBody, toolProvider);
        exchange.getMessage().setBody(result.content());
        populateTokenUsageHeaders(result, exchange);
    }

    private void populateTokenUsageHeaders(Result<String> result, Exchange exchange) {
        Message message = exchange.getMessage();

        if (result.finishReason() != null) {
            message.setHeader(Headers.FINISH_REASON, result.finishReason());
        }

        if (result.tokenUsage() != null) {
            message.setHeader(Headers.INPUT_TOKEN_COUNT, result.tokenUsage().inputTokenCount());
            message.setHeader(Headers.OUTPUT_TOKEN_COUNT, result.tokenUsage().outputTokenCount());
            message.setHeader(Headers.TOTAL_TOKEN_COUNT, result.tokenUsage().totalTokenCount());
        }
    }

    /**
     * Creates a composed tool provider that aggregates tools from all configured sources: Camel route tools (via tags)
     * and MCP tools (via endpoint-level mcpClients and mcpServers configuration).
     *
     * <p>
     * Supports dynamic filtering via exchange headers:
     * <ul>
     * <li>{@link Headers#EXCLUDE_TAGS} — exclude Camel tools by tag (handled in
     * {@link #createCamelToolProvider(String, Exchange)})</li>
     * <li>{@link Headers#EXCLUDE_MCP_SERVERS} — exclude MCP servers by key/name</li>
     * </ul>
     */
    private ToolProvider createComposedToolProvider(String tags, Exchange exchange) {
        List<ToolProvider> providers = new ArrayList<>();

        // 1. Camel route tools from tags (handles EXCLUDE_TAGS header internally)
        ToolProvider camelToolProvider = createCamelToolProvider(tags, exchange);
        if (camelToolProvider != null) {
            providers.add(camelToolProvider);
        }

        // 2. MCP tools from endpoint configuration (pre-built clients + materialized from server definitions)
        List<McpClient> allMcpClients = new ArrayList<>();

        List<McpClient> endpointMcpClients = endpoint.getConfiguration().getMcpClients();
        if (endpointMcpClients != null) {
            allMcpClients.addAll(endpointMcpClients);
        }
        if (materializedMcpClients != null) {
            allMcpClients.addAll(materializedMcpClients);
        }

        // Apply MCP server exclusion from header
        String excludeMcpServers = exchange.getIn().getHeader(Headers.EXCLUDE_MCP_SERVERS, String.class);
        if (excludeMcpServers != null && !excludeMcpServers.trim().isEmpty()) {
            Set<String> excludeSet = new HashSet<>(Arrays.asList(ToolsTagsHelper.splitTags(excludeMcpServers)));
            allMcpClients = allMcpClients.stream()
                    .filter(client -> client.key() == null || !excludeSet.contains(client.key()))
                    .collect(Collectors.toList());
            LOG.debug("After MCP server exclusion (excluded: {}): {} clients remaining",
                    excludeMcpServers, allMcpClients.size());
        }

        if (!allMcpClients.isEmpty()) {
            LOG.debug("Adding {} MCP clients to tool provider", allMcpClients.size());
            providers.add(McpToolProvider.builder().mcpClients(allMcpClients).build());
        }

        if (providers.isEmpty()) {
            return null;
        } else if (providers.size() == 1) {
            return providers.get(0);
        } else {
            return new CompositeToolProvider(providers);
        }
    }

    /**
     * Creates a tool provider for Camel route tools discovered by tags. If the {@link Headers#EXCLUDE_TAGS} header is
     * set on the exchange, the specified tags (comma-separated) are excluded from discovery.
     */
    private ToolProvider createCamelToolProvider(String tags, Exchange exchange) {
        if (tags == null || tags.trim().isEmpty()) {
            return null;
        }

        // Apply tag exclusion from header
        String excludeTags = exchange.getIn().getHeader(Headers.EXCLUDE_TAGS, String.class);
        if (excludeTags != null && !excludeTags.trim().isEmpty()) {
            Set<String> excludeSet = new HashSet<>(Arrays.asList(ToolsTagsHelper.splitTags(excludeTags)));
            String[] allTags = ToolsTagsHelper.splitTags(tags);
            tags = Arrays.stream(allTags)
                    .filter(t -> !excludeSet.contains(t))
                    .collect(Collectors.joining(","));
            LOG.debug("After tag exclusion (excluded: {}): remaining tags: {}", excludeTags, tags);
            if (tags.isEmpty()) {
                LOG.debug("All Camel tool tags excluded by header");
                return null;
            }
        }

        // Discover tools from Camel LangChain4j Tools routes
        Map<String, CamelToolSpecification> availableTools = discoverToolsByName(tags);

        if (!availableTools.isEmpty()) {
            LOG.debug("Creating AI Service with {} tools for tags: {}", availableTools.size(), tags);
            return createCamelToolProvider(availableTools, exchange);
        } else {
            LOG.debug("No tools found for tags: {}, using simple AI Service", tags);
            return null;
        }
    }

    /**
     * Create a dynamic tool provider that returns all Camel route as LangChain4j tools. This uses LangChain4j's
     * ToolProvider API for dynamic tool registration.
     */
    private ToolProvider createCamelToolProvider(Map<String, CamelToolSpecification> availableTools, Exchange exchange) {
        return (ToolProviderRequest toolProviderRequest) -> {
            // Build the tool provider result with all available Camel tools
            ToolProviderResult.Builder resultBuilder = ToolProviderResult.builder();

            for (Map.Entry<String, CamelToolSpecification> entry : availableTools.entrySet()) {
                String toolName = entry.getKey();
                CamelToolSpecification camelToolSpec = entry.getValue();

                // Get the existing ToolSpecification from CamelToolSpecification
                ToolSpecification toolSpecification = camelToolSpec.getToolSpecification();

                // Create a functional tool executor for this specific Camel route
                ToolExecutor toolExecutor = (toolExecutionRequest, memoryId) -> {
                    LOG.info("Executing Camel route tool: '{}' with arguments: {}", toolName, toolExecutionRequest.arguments());

                    try {
                        // Parse JSON arguments if provided
                        String arguments = toolExecutionRequest.arguments();
                        if (arguments != null && !arguments.trim().isEmpty()) {
                            // Get declared parameters from tool specification to filter incoming fields
                            Set<String> declaredParams = Set.of();
                            JsonObjectSchema paramSchema = toolSpecification.parameters();
                            if (paramSchema != null && paramSchema.properties() != null) {
                                declaredParams = paramSchema.properties().keySet();
                            }
                            final Set<String> allowedParams = declaredParams;

                            JsonNode jsonNode = objectMapper.readValue(arguments, JsonNode.class);
                            jsonNode.fieldNames()
                                    .forEachRemaining(name -> {
                                        if (!allowedParams.contains(name)) {
                                            LOG.warn("Skipping undeclared tool argument '{}' for tool '{}'",
                                                    name, toolName);
                                            return;
                                        }
                                        JsonNode value = jsonNode.get(name);
                                        Object headerValue;
                                        if (value.isInt()) {
                                            headerValue = value.intValue();
                                        } else if (value.isLong()) {
                                            headerValue = value.longValue();
                                        } else if (value.isDouble()) {
                                            headerValue = value.doubleValue();
                                        } else if (value.isBoolean()) {
                                            headerValue = value.booleanValue();
                                        } else {
                                            headerValue = value.asText();
                                        }
                                        exchange.getMessage().setHeader(name, headerValue);
                                    });
                        }

                        // Set the tool name as a header for route identification
                        exchange.getMessage().setHeader("CamelToolName", toolName);

                        // Execute the consumer route
                        camelToolSpec.getConsumer().getProcessor().process(exchange);

                        // Return the result
                        String result = exchange.getIn().getBody(String.class);
                        LOG.info("Tool '{}' execution completed successfully", toolName);
                        return result != null ? result : "No result";

                    } catch (Exception e) {
                        LOG.error("Error executing tool '{}': {}", toolName, e.getMessage(), e);
                        return String.format("Error executing tool '%s': %s", toolName, e.getMessage());
                    }
                };

                // Add this tool to the result
                resultBuilder.add(toolSpecification, toolExecutor);

                LOG.info("Added dynamic tool: '{}' - {}", toolSpecification.name(), toolSpecification.description());
            }

            return resultBuilder.build();
        };
    }

    /**
     * Discover Camel routes by tags and create a map of tool specifications by name.
     */
    private Map<String, CamelToolSpecification> discoverToolsByName(String tags) {
        final CamelToolExecutorCache toolCache = CamelToolExecutorCache.getInstance();
        final Map<String, Set<CamelToolSpecification>> tools = toolCache.getTools();
        final String[] tagArray = ToolsTagsHelper.splitTags(tags);

        final Map<String, CamelToolSpecification> toolsByName = Arrays.stream(tagArray)
                .flatMap(tag -> tools.entrySet().stream()
                        .filter(entry -> entry.getKey().equals(tag))
                        .flatMap(entry -> entry.getValue().stream()))
                .collect(Collectors.toMap(
                        camelToolSpec -> camelToolSpec.getToolSpecification().name(),
                        camelToolSpec -> camelToolSpec,
                        (existing, replacement) -> existing // Keep first if duplicate names
                ));

        LOG.info("Discovered {} unique tools for tags: {}", toolsByName.size(), tags);
        return toolsByName;
    }

    /**
     * Resolves and applies structured output configuration (jsonSchema or outputClass) to the agent. Only called when
     * the agent is created internally from agentConfiguration.
     */
    private void resolveAndApplyStructuredOutput() throws Exception {
        String jsonSchema = endpoint.getConfiguration().getJsonSchema();
        Class<?> outputClass = endpoint.getConfiguration().getOutputClass();

        if (ObjectHelper.isEmpty(jsonSchema) && outputClass == null) {
            return;
        }

        JsonSchema schema;
        if (outputClass != null) {
            // JsonSchemas.jsonSchemaFrom() uses rawClass.getSimpleName() as schema name; rename to camel_schema
            // for consistency with the jsonSchema branch and cross-component portability with camel-openai.
            JsonSchema derived = JsonSchemas.jsonSchemaFrom(outputClass)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Cannot derive JSON schema from outputClass: " + outputClass.getName()
                                                                    + ". outputClass must be a POJO class with public fields or getters."
                                                                    + " Simple types, enums, and collections are not supported."));
            schema = JsonSchema.builder()
                    .name("camel_schema")
                    .rootElement(derived.rootElement())
                    .build();
            LOG.debug("Configured structured output with output class: {}", outputClass.getName());
        } else {
            String resolved = endpoint.getCamelContext().resolvePropertyPlaceholders(jsonSchema);

            String content = resolveResourceContent(resolved);
            if (content != null) {
                resolved = content;
            }

            // Validates that resolved is valid JSON (whether loaded from a resource or used as inline content)
            try {
                objectMapper.readTree(resolved);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "jsonSchema endpoint property does not contain valid JSON. Provided value: " + jsonSchema, e);
            }
            // Use a fixed name consistent with camel-openai for cross-component portability
            schema = JsonSchema.builder()
                    .name("camel_schema")
                    .rootElement(JsonRawSchema.from(resolved))
                    .build();
        }

        ResponseFormat responseFormat = ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(schema)
                .build();
        ((AbstractAgent<?>) agent).setResponseFormat(responseFormat);
    }

    /**
     * Tries to load {@code property} as a Camel resource and return its content as a String.
     * <p>
     * If {@code property} has an explicit scheme (e.g. {@code classpath:}, {@code file:}), the resource must exist — a
     * missing resource throws {@link java.io.FileNotFoundException}.
     * <p>
     * If there is no scheme, classpath resolution is attempted. Returns {@code null} on failure, signalling the caller
     * to use {@code property} as-is (inline JSON).
     */
    private String resolveResourceContent(String property) throws IOException {
        if (ResourceHelper.hasScheme(property)) {
            // Explicit scheme: mandatory load — throws FileNotFoundException if the resource does not exist
            try (InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(endpoint.getCamelContext(), property)) {
                return endpoint.getCamelContext().getTypeConverter().convertTo(String.class, is);
            }
        }
        // No scheme: try implicit classpath resolution — fall through and treat as inline JSON content if not found
        try (InputStream is = ResourceHelper.resolveResourceAsInputStream(endpoint.getCamelContext(), property)) {
            if (is != null) {
                return endpoint.getCamelContext().getTypeConverter().convertTo(String.class, is);
            }
        } catch (Exception e) {
            // not a resolvable resource URI — fall through and treat as inline JSON content
        }
        return null;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        agentFactory = endpoint.getConfiguration().getAgentFactory();
        if (agentFactory != null) {
            agentFactory.setCamelContext(this.endpoint.getCamelContext());
        }

        // Materialize MCP clients from inline mcpServer.<name>.<property> configuration
        Map<String, Object> mcpServerMap = endpoint.getConfiguration().getMcpServer();
        if (mcpServerMap != null && !mcpServerMap.isEmpty()) {
            List<LangChain4jMcpServerDefinition> serverDefs = parseMcpServerDefinitions(mcpServerMap);
            if (!serverDefs.isEmpty()) {
                materializedMcpClients = new ArrayList<>();
                for (LangChain4jMcpServerDefinition def : serverDefs) {
                    LOG.debug("Building MCP client '{}' (transport: {})", def.getServerName(), def.getTransportType());
                    materializedMcpClients.add(def.buildClient(endpoint.getCamelContext()));
                }
                LOG.debug("Materialized {} MCP clients from server definitions", materializedMcpClients.size());
            }
        }
    }

    /**
     * Parses the flat mcpServer map (from URI parameters like mcpServer.weather.url=...) into grouped
     * {@link LangChain4jMcpServerDefinition} instances.
     *
     * <p>
     * The map keys are in the form {@code <serverName>.<property>}. For example:
     * </p>
     * <ul>
     * <li>{@code weather.transportType=http}</li>
     * <li>{@code weather.url=http://localhost:8080}</li>
     * <li>{@code filesystem.command=npx,-y,@modelcontextprotocol/server-filesystem}</li>
     * </ul>
     */
    private List<LangChain4jMcpServerDefinition> parseMcpServerDefinitions(Map<String, Object> mcpServerMap) {
        // Group entries by server name (the first dot-separated segment)
        Map<String, Map<String, String>> serverGroups = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : mcpServerMap.entrySet()) {
            String key = entry.getKey();
            int dotIndex = key.indexOf('.');
            if (dotIndex > 0 && dotIndex < key.length() - 1) {
                String serverName = key.substring(0, dotIndex);
                String property = key.substring(dotIndex + 1);
                serverGroups.computeIfAbsent(serverName, k -> new LinkedHashMap<>())
                        .put(property, String.valueOf(entry.getValue()));
            } else {
                LOG.warn("Ignoring invalid mcpServer property key: '{}'. Expected format: <serverName>.<property>", key);
            }
        }

        List<LangChain4jMcpServerDefinition> definitions = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> group : serverGroups.entrySet()) {
            String serverName = group.getKey();
            Map<String, String> props = group.getValue();

            LangChain4jMcpServerDefinition def = new LangChain4jMcpServerDefinition();
            def.setServerName(serverName);

            if (props.containsKey("transportType")) {
                def.setTransportType(props.get("transportType"));
            }
            if (props.containsKey("url")) {
                def.setUrl(props.get("url"));
            }
            if (props.containsKey("command")) {
                def.setCommand(Arrays.asList(props.get("command").split(",")));
            }
            if (props.containsKey("timeout")) {
                def.setTimeout(Duration.ofSeconds(Long.parseLong(props.get("timeout"))));
            }
            if (props.containsKey("logRequests")) {
                def.setLogRequests(Boolean.parseBoolean(props.get("logRequests")));
            }
            if (props.containsKey("logResponses")) {
                def.setLogResponses(Boolean.parseBoolean(props.get("logResponses")));
            }
            if (props.containsKey("oauthProfile")) {
                def.setOauthProfile(props.get("oauthProfile"));
            }

            // Collect environment variables (environment.<key>=<value>)
            Map<String, String> environment = new LinkedHashMap<>();
            for (Map.Entry<String, String> prop : props.entrySet()) {
                if (prop.getKey().startsWith("environment.")) {
                    String envKey = prop.getKey().substring("environment.".length());
                    environment.put(envKey, prop.getValue());
                }
            }
            if (!environment.isEmpty()) {
                def.setEnvironment(environment);
            }

            definitions.add(def);
            LOG.debug("Parsed MCP server definition '{}' (transport: {})", serverName, def.getTransportType());
        }

        return definitions;
    }

    @Override
    protected void doStop() throws Exception {
        // Close only MCP clients that were materialized from server definitions.
        // Pre-built McpClient beans (from mcpClients parameter) are managed by the registry/container.
        if (materializedMcpClients != null) {
            for (McpClient client : materializedMcpClients) {
                try {
                    client.close();
                } catch (Exception e) {
                    LOG.warn("Error closing MCP client: {}", e.getMessage(), e);
                }
            }
            materializedMcpClients = null;
        }
        super.doStop();
    }
}
