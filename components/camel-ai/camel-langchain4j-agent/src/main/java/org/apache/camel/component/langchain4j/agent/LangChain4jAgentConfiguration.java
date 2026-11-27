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

import java.time.Duration;
import java.util.List;
import java.util.Map;

import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.ai.tool.AiToolSearch;
import org.apache.camel.component.ai.tool.AiToolSearchStrategy;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AgentFactory;
import org.apache.camel.component.langchain4j.core.LangChain4jModelFactory;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@Configurer
@UriParams
public class LangChain4jAgentConfiguration implements Cloneable {

    @UriParam(description = "The agent that answers the messages, an"
                            + " org.apache.camel.component.langchain4j.agent.api.Agent. It takes precedence over"
                            + " agentConfiguration, and agentFactory takes precedence over it.")
    @Metadata(autowired = true)
    private Agent agent;

    @UriParam(description = "The factory whose createAgent method the producer calls for each message, to create the"
                            + " agent that answers it. It takes precedence over agent and agentConfiguration.")
    @Metadata(autowired = true)
    private AgentFactory agentFactory;

    @UriParam(description = "The settings of LangChain4j (chat model, chat memory, tools, RAG, guardrails, and so on) from"
                            + " which the producer creates the agent: an AgentWithMemory when a ChatMemoryProvider is set,"
                            + " otherwise an AgentWithoutMemory. The agent and agentFactory options take precedence over"
                            + " it.")
    @Metadata(autowired = true)
    private AgentConfiguration agentConfiguration;

    @UriParam(label = "model", enums = LangChain4jModelFactory.Provider.NAMES,
              description = "The LangChain4j provider of the chat model that drives the agent, to create the model from"
                            + " the options here (modelName, baseUrl, apiKey, temperature, timeout, and provider-specific"
                            + " model.* properties) instead of an AgentConfiguration bean. The LangChain4j module of the"
                            + " provider (dev.langchain4j:langchain4j-ollama, ...) must be on the classpath; the Camel CLI"
                            + " downloads it. Ignored when agent, agentFactory or agentConfiguration is set. For a"
                            + " provider not listed, set customProvider instead.")
    private String provider;
    @UriParam(label = "model,advanced")
    private String customProvider;
    @UriParam(label = "model")
    private String modelName;
    @UriParam(label = "model")
    private String baseUrl;
    @UriParam(label = "model,security", security = "secret")
    private String apiKey;
    @UriParam(label = "model")
    private Double temperature;
    @UriParam(label = "model")
    private Duration timeout;
    @UriParam(label = "model,advanced", prefix = "model.", multiValue = true)
    private Map<String, Object> modelProperties;

    @UriParam(description = "A comma-separated list of tags. The routes that start from an ai-tool endpoint with one of"
                            + " these tags are offered to the model as tools. The CamelLangChain4jAgentExcludeTags header"
                            + " removes tags from this list for one message.")
    private String tags;

    @UriParam(description = "The MCP (Model Context Protocol) clients, of type dev.langchain4j.mcp.client.McpClient, whose"
                            + " tools are offered to the model, as references to beans in the registry, for example"
                            + " #myMcpClient1,#myMcpClient2. The producer does not close them.",
              label = "advanced")
    private List<McpClient> mcpClients;

    @UriParam
    @Metadata(description = "The JSON schema that the answer of the model must follow, inline or as a resource (classpath:,"
                            + " file:, ...). Camel sends it to the model as the response format, and the body is the answer"
                            + " as JSON text. Only works when Camel creates the agent, from agentConfiguration or provider,"
                            + " and not together with outputClass: otherwise the producer fails to start.",
              supportFileReference = true, largeInput = true, inputLanguage = "json")
    private String jsonSchema;

    @UriParam
    @Metadata(description = "The Java class, a POJO with public fields or getters, from which Camel derives the JSON"
                            + " schema that the answer of the model must follow. The body is the answer as JSON text, not an"
                            + " instance of the class. Only works when Camel creates the agent, from agentConfiguration or"
                            + " provider, and not together with jsonSchema: otherwise the producer fails to start.")
    private Class<?> outputClass;

    @UriParam(description = "The MCP servers whose tools are offered to the model, as mcpServer.myServer.myProperty=myValue,"
                            + " where myServer is the name of the server; the producer creates a client per server when it"
                            + " starts and closes it when it stops. The properties are transportType (stdio by default, or"
                            + " http or streamableHttp, which both use the Streamable HTTP transport), command"
                            + " (comma-separated, for stdio), environment.myKey=myValue (for stdio), url (for HTTP), timeout"
                            + " (in seconds, 60 by default), logRequests, logResponses, and oauthProfile (the camel-oauth"
                            + " profile that supplies a bearer token for HTTP).",
              prefix = "mcpServer.", multiValue = true, label = "advanced")
    private Map<String, Object> mcpServer;

    @UriParam
    @Metadata(label = "producer", defaultValue = "0",
              description = "The maximum number of tool-calling round trips per message, each one model call and the"
                            + " tools that this call requests. A value above 0 replaces the value of the agentConfiguration;"
                            + " with 0, that value applies, or the LangChain4j default when it is not set either. Only works"
                            + " when Camel creates the agent, from agentConfiguration or provider.")
    private int maxToolCallingRoundTrips;

    @UriParam
    @Metadata(label = "producer",
              description = "Whether LangChain4j runs the compensating actions when a tool fails: the methods annotated"
                            + " with @CompensateFor for the tools that already ran. When set, it replaces the value of the"
                            + " agentConfiguration. Only works when Camel creates the agent, from agentConfiguration or"
                            + " provider.")
    private Boolean compensateOnToolErrors;

    @UriParam
    @Metadata(label = "producer",
              description = "Whether LangChain4j runs the tools that the model requests in one response concurrently, on"
                            + " the executor of the agentConfiguration or else on a Camel thread pool; each route tool runs"
                            + " on a copy of the exchange. When set, it replaces the value of the agentConfiguration. Only"
                            + " works when Camel creates the agent, from agentConfiguration or provider.")
    private Boolean executeToolsConcurrently;

    @UriParam(defaultValue = "false")
    @Metadata(label = "producer",
              description = "Whether to leave the route tools with searchable=true and the MCP tools out of the first"
                            + " request to the model, and give the model one search tool (tool_search_tool) to find them;"
                            + " the other route tools and the customTools of the agentConfiguration are always sent. It"
                            + " controls what the model sees, not what it may call, and does not search a tool provider"
                            + " set outside Camel. Only works when Camel creates the agent, from agentConfiguration or"
                            + " provider.")
    private boolean toolSearch;

    @UriParam(defaultValue = "5")
    @Metadata(label = "producer",
              description = "The maximum number of tools that one call of the search tool returns, at least 1. Route"
                            + " tools and MCP tools are ranked together.")
    private int toolSearchMaxResults = AiToolSearch.DEFAULT_MAX_RESULTS;

    @UriParam(enums = "KEYWORD,VECTOR", defaultValue = "KEYWORD")
    @Metadata(label = "producer",
              description = "How tool search ranks route and MCP tools. 'KEYWORD' matches the tool name and description. "
                            + "'VECTOR' uses cosine similarity and requires toolSearchEmbeddingModel. Vector search "
                            + "does not fall back to keyword search.")
    private AiToolSearchStrategy toolSearchStrategy = AiToolSearchStrategy.KEYWORD;

    @UriParam
    @Metadata(label = "producer", autowired = true,
              description = "The LangChain4j embedding model that turns the tool descriptions and the search query into"
                            + " vectors. Required when toolSearchStrategy is VECTOR, and not used otherwise.")
    private EmbeddingModel toolSearchEmbeddingModel;

    public LangChain4jAgentConfiguration() {
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getCustomProvider() {
        return customProvider;
    }

    /**
     * The fully qualified class name of the LangChain4j model class of a provider that is not listed in provider
     * (dev.langchain4j.model.jlama.JlamaChatModel), created from the options here through its builder() as a listed
     * provider is. Set either provider or customProvider.
     */
    public void setCustomProvider(String customProvider) {
        this.customProvider = customProvider;
    }

    public String getModelName() {
        return modelName;
    }

    /**
     * The name of the model at the provider (qwen2.5, gpt-4o-mini, ...), when the model is created from the provider.
     */
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * The URL of the provider's API (http://localhost:11434 for a local Ollama), when the model is created from the
     * provider. The provider's default when not set.
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    /**
     * The API key or access token of the provider, when the model is created from the provider.
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Double getTemperature() {
        return temperature;
    }

    /**
     * The sampling temperature of the model, when the model is created from the provider.
     */
    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Duration getTimeout() {
        return timeout;
    }

    /**
     * The request timeout of the model (30s, 2m), when the model is created from the provider.
     */
    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Map<String, Object> getModelProperties() {
        return modelProperties;
    }

    /**
     * Provider-specific properties of the model, set on the model's builder as they are (model.numPredict=512 for
     * Ollama, model.maxTokens=1024 for OpenAI), when the model is created from the provider.
     */
    public void setModelProperties(Map<String, Object> modelProperties) {
        this.modelProperties = modelProperties;
    }

    /**
     * The model to create from the provider, or null when no provider is set.
     */
    public LangChain4jModelFactory.ModelSpec modelSpec() {
        if (provider == null && customProvider == null) {
            return null;
        }
        return new LangChain4jModelFactory.ModelSpec(
                provider, customProvider, modelName, baseUrl, apiKey, temperature, timeout,
                modelProperties != null ? Map.copyOf(modelProperties) : null);
    }

    public LangChain4jAgentConfiguration copy() {
        try {
            return (LangChain4jAgentConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public Agent getAgent() {
        return agent;
    }

    public void setAgent(Agent agent) {
        this.agent = agent;
    }

    public AgentFactory getAgentFactory() {
        return agentFactory;
    }

    public void setAgentFactory(AgentFactory agentFactory) {
        this.agentFactory = agentFactory;
    }

    public AgentConfiguration getAgentConfiguration() {
        return agentConfiguration;
    }

    public void setAgentConfiguration(AgentConfiguration agentConfiguration) {
        this.agentConfiguration = agentConfiguration;
    }

    public List<McpClient> getMcpClients() {
        return mcpClients;
    }

    public void setMcpClients(List<McpClient> mcpClients) {
        this.mcpClients = mcpClients;
    }

    /**
     * MCP server definitions for inline URI configuration.
     *
     * <p>
     * The map keys are in the form {@code <serverName>.<property>} and are collected from URI parameters with the
     * {@code mcpServer.} prefix. For example:
     * </p>
     *
     * <pre>
     * mcpServer.weather.transportType=http&amp;mcpServer.weather.url=http://localhost:8080
     * mcpServer.fs.transportType=stdio&amp;mcpServer.fs.command=npx,-y,@modelcontextprotocol/server-filesystem
     * </pre>
     *
     * @return the map of MCP server properties
     */
    public Map<String, Object> getMcpServer() {
        return mcpServer;
    }

    public void setMcpServer(Map<String, Object> mcpServer) {
        this.mcpServer = mcpServer;
    }

    public String getJsonSchema() {
        return jsonSchema;
    }

    public void setJsonSchema(String jsonSchema) {
        this.jsonSchema = jsonSchema;
    }

    public Class<?> getOutputClass() {
        return outputClass;
    }

    public void setOutputClass(Class<?> outputClass) {
        this.outputClass = outputClass;
    }

    public int getMaxToolCallingRoundTrips() {
        return maxToolCallingRoundTrips;
    }

    public void setMaxToolCallingRoundTrips(int maxToolCallingRoundTrips) {
        if (maxToolCallingRoundTrips < 0) {
            throw new IllegalArgumentException(
                    "maxToolCallingRoundTrips must be zero (unset) or positive, but was: " + maxToolCallingRoundTrips);
        }
        this.maxToolCallingRoundTrips = maxToolCallingRoundTrips;
    }

    public Boolean getCompensateOnToolErrors() {
        return compensateOnToolErrors;
    }

    public void setCompensateOnToolErrors(Boolean compensateOnToolErrors) {
        this.compensateOnToolErrors = compensateOnToolErrors;
    }

    public Boolean getExecuteToolsConcurrently() {
        return executeToolsConcurrently;
    }

    public void setExecuteToolsConcurrently(Boolean executeToolsConcurrently) {
        this.executeToolsConcurrently = executeToolsConcurrently;
    }

    public boolean isToolSearch() {
        return toolSearch;
    }

    public void setToolSearch(boolean toolSearch) {
        this.toolSearch = toolSearch;
    }

    public int getToolSearchMaxResults() {
        return toolSearchMaxResults;
    }

    public void setToolSearchMaxResults(int toolSearchMaxResults) {
        this.toolSearchMaxResults = toolSearchMaxResults;
    }

    public AiToolSearchStrategy getToolSearchStrategy() {
        return toolSearchStrategy;
    }

    public void setToolSearchStrategy(AiToolSearchStrategy toolSearchStrategy) {
        this.toolSearchStrategy = toolSearchStrategy;
    }

    public EmbeddingModel getToolSearchEmbeddingModel() {
        return toolSearchEmbeddingModel;
    }

    public void setToolSearchEmbeddingModel(EmbeddingModel toolSearchEmbeddingModel) {
        this.toolSearchEmbeddingModel = toolSearchEmbeddingModel;
    }
}
