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
import org.apache.camel.RuntimeCamelException;
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

    @UriParam(description = "The agent to use for the component")
    @Metadata(autowired = true)
    private Agent agent;

    @UriParam(description = "The agent factory to use for creating agents if no Agent is provided")
    @Metadata(autowired = true)
    private AgentFactory agentFactory;

    @UriParam(description = "AgentConfiguration used by Camel to create the agent internally."
                            + " When set, Camel creates an AgentWithMemory if a ChatMemoryProvider is configured,"
                            + " otherwise an AgentWithoutMemory."
                            + " If an agentFactory is also configured, the factory takes precedence.")
    @Metadata(autowired = true)
    private AgentConfiguration agentConfiguration;

    @UriParam(label = "model", enums = LangChain4jModelFactory.Provider.NAMES)
    private String provider;
    @UriParam(label = "model,advanced")
    private String customProvider;
    @UriParam(label = "model")
    private String modelName;
    @UriParam(label = "model")
    private String baseUrl;
    @UriParam(label = "model,security", secret = true)
    private String apiKey;
    @UriParam(label = "model")
    private Double temperature;
    @UriParam(label = "model")
    private Duration timeout;
    @UriParam(label = "model,advanced", prefix = "model.", multiValue = true)
    private Map<String, Object> modelProperties;

    @UriParam(description = "Tags for discovering and calling Camel route tools")
    private String tags;

    @UriParam(description = "Pre-built MCP (Model Context Protocol) client instances for external tool integration."
                            + " Reference beans from the registry, e.g., #myMcpClient1,#myMcpClient2",
              label = "advanced")
    private List<McpClient> mcpClients;

    @UriParam
    @Metadata(description = "JSON schema for structured output validation. "
                            + "Only supported in inline agent creation mode: agentConfiguration must be set and neither agent nor agentFactory may be configured. "
                            + "Mutually exclusive with outputClass.",
              supportFileReference = true, largeInput = true, inputLanguage = "json")
    private String jsonSchema;

    @UriParam
    @Metadata(description = "Java class to use for structured output. "
                            + "Camel derives the JSON schema from the class and instructs the model to produce matching JSON; the response body is left as a raw JSON string. "
                            + "Only supported in inline agent creation mode: agentConfiguration must be set and neither agent nor agentFactory may be configured. "
                            + "The class must be a POJO with public fields or getters; simple types, enums, and collections are not supported. "
                            + "Mutually exclusive with jsonSchema.")
    private Class<?> outputClass;

    @UriParam(description = "MCP server definitions in the form of mcpServer.<name>.<property>=<value>."
                            + " Supported properties: transportType (stdio, http or streamableHttp, default: stdio),"
                            + " command (comma-separated, for stdio), url (for http/streamableHttp),"
                            + " environment.<key>=<value> (for stdio), timeout (in seconds, default: 60),"
                            + " logRequests, logResponses,"
                            + " oauthProfile (OAuth profile for HTTP auth, requires camel-oauth).",
              prefix = "mcpServer.", multiValue = true, label = "advanced")
    private Map<String, Object> mcpServer;

    @UriParam
    @Metadata(label = "producer", defaultValue = "0",
              description = "Maximum number of tool-calling round trips allowed per request."
                            + " Each round trip is one LLM call plus execution of the tools requested in that call."
                            + " Set to 0 to leave unset and use the LangChain4j default."
                            + " Only supported in inline agent creation mode (agentConfiguration without agent or agentFactory)."
                            + " URI value overrides the same option on the agentConfiguration bean.")
    private int maxToolCallingRoundTrips;

    @UriParam
    @Metadata(label = "producer",
              description = "Whether LangChain4j should compensate when a tool execution fails."
                            + " Only supported in inline agent creation mode (agentConfiguration without agent or agentFactory)."
                            + " URI value overrides the same option on the agentConfiguration bean.")
    private Boolean compensateOnToolErrors;

    @UriParam
    @Metadata(label = "producer",
              description = "Whether multiple tools requested in a single LLM turn are executed concurrently."
                            + " Camel route tools run on isolated exchange copies."
                            + " Only supported in inline agent creation mode (agentConfiguration without agent or agentFactory)."
                            + " URI value overrides the same option on the agentConfiguration bean.")
    private Boolean executeToolsConcurrently;

    public LangChain4jAgentConfiguration() {
    }

    /**
     * Tags for discovering and calling Camel route tools
     *
     * @return the tags
     */
    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getProvider() {
        return provider;
    }

    /**
     * The LangChain4j provider of the chat model that drives the agent, to create the model from the options here
     * (modelName, baseUrl, apiKey, temperature, timeout, and provider-specific model.* properties) instead of a
     * AgentConfiguration bean. The LangChain4j module of the provider (dev.langchain4j:langchain4j-ollama, ...) must be
     * on the classpath; Camel JBang downloads it. Ignored when a AgentConfiguration is configured. For a provider not
     * listed, set customProvider instead.
     */
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

    /**
     * The agent providing the service
     *
     * @return the instance of the agent providing the service
     */
    public Agent getAgent() {
        return agent;
    }

    public void setAgent(Agent agent) {
        this.agent = agent;
    }

    /**
     * An agent factory creating the agents
     *
     * @return the instance of the agent factory in use
     */
    public AgentFactory getAgentFactory() {
        return agentFactory;
    }

    public void setAgentFactory(AgentFactory agentFactory) {
        this.agentFactory = agentFactory;
    }

    /**
     * AgentConfiguration used by Camel to create the agent internally.
     */
    public AgentConfiguration getAgentConfiguration() {
        return agentConfiguration;
    }

    public void setAgentConfiguration(AgentConfiguration agentConfiguration) {
        this.agentConfiguration = agentConfiguration;
    }

    /**
     * Pre-built MCP client instances for external tool integration
     *
     * @return the list of MCP clients
     */
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

    /**
     * JSON schema for structured output validation
     *
     * @return the JSON schema string or resource reference
     */
    public String getJsonSchema() {
        return jsonSchema;
    }

    public void setJsonSchema(String jsonSchema) {
        this.jsonSchema = jsonSchema;
    }

    /**
     * Java class to use for structured output
     *
     * @return the output class
     */
    public Class<?> getOutputClass() {
        return outputClass;
    }

    public void setOutputClass(Class<?> outputClass) {
        this.outputClass = outputClass;
    }

    /**
     * Maximum number of tool-calling round trips allowed per request.
     */
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

    /**
     * Whether LangChain4j should compensate when a tool execution fails.
     */
    public Boolean getCompensateOnToolErrors() {
        return compensateOnToolErrors;
    }

    public void setCompensateOnToolErrors(Boolean compensateOnToolErrors) {
        this.compensateOnToolErrors = compensateOnToolErrors;
    }

    /**
     * Whether multiple tools requested in a single LLM turn are executed concurrently.
     */
    public Boolean getExecuteToolsConcurrently() {
        return executeToolsConcurrently;
    }

    public void setExecuteToolsConcurrently(Boolean executeToolsConcurrently) {
        this.executeToolsConcurrently = executeToolsConcurrently;
    }
}
