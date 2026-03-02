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

import java.util.Map;

import com.openai.core.ClientOptions;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Configuration for OpenAI component.
 */
@UriParams
public class OpenAIConfiguration implements Cloneable {

    @UriParam(secret = true)
    @Metadata(description = "OpenAI API key. Can also be set via OPENAI_API_KEY environment variable.", secret = true)
    private String apiKey;

    @UriParam(label = "security")
    @Metadata(description = "OAuth profile name for obtaining an access token via the OAuth 2.0 Client Credentials grant. "
                            + "When set, the token is acquired from the configured identity provider and used instead of apiKey. "
                            + "Requires camel-oauth on the classpath. The profile properties are resolved from "
                            + "camel.oauth.<profileName>.client-id, camel.oauth.<profileName>.client-secret, "
                            + "and camel.oauth.<profileName>.token-endpoint.")
    private String oauthProfile;

    @UriParam
    @Metadata(description = "Base URL for OpenAI API. Defaults to OpenAI's official endpoint. Can be used for local or third-party providers.",
              defaultValue = ClientOptions.PRODUCTION_URL)
    private String baseUrl = ClientOptions.PRODUCTION_URL;

    @UriParam
    @Metadata(description = "The model to use for chat completion")
    private String model;

    @UriParam
    @Metadata(description = "Temperature for response generation (0.0 to 2.0)")
    private Double temperature;

    @UriParam
    @Metadata(description = "Top P for response generation (0.0 to 1.0)")
    private Double topP;

    @UriParam
    @Metadata(description = "Maximum number of tokens to generate")
    private Integer maxTokens;

    @UriParam(defaultValue = "false")
    @Metadata(description = "Enable streaming responses")
    private boolean streaming = false;

    @UriParam
    @Metadata(description = "Fully qualified class name for structured output using response format")
    private String outputClass;

    @UriParam
    @Metadata(description = "JSON schema for structured output validation", supportFileReference = true, largeInput = true,
              inputLanguage = "json")
    private String jsonSchema;

    @UriParam(defaultValue = "false")
    @Metadata(description = "Enable conversation memory per Exchange")
    private boolean conversationMemory = false;

    @UriParam(defaultValue = "CamelOpenAIConversationHistory")
    @Metadata(description = "Exchange property name for storing conversation history")
    private String conversationHistoryProperty = "CamelOpenAIConversationHistory";

    @UriParam
    @Metadata(description = "Default user message text to use when no prompt is provided", largeInput = true)
    private String userMessage;

    @UriParam
    @Metadata(description = "System message to prepend. When set and conversationMemory is enabled, the conversation history is reset.",
              largeInput = true)
    private String systemMessage;

    @UriParam
    @Metadata(description = "Developer message to prepend before user messages", largeInput = true)
    private String developerMessage;

    @UriParam(defaultValue = "false")
    @Metadata(description = "Store the full response in the exchange property 'CamelOpenAIResponse' in non-streaming mode")
    private boolean storeFullResponse = false;

    @UriParam(prefix = "additionalBodyProperty.", multiValue = true)
    @Metadata(description = "Additional JSON properties to include in the request body (e.g. additionalBodyProperty.traceId=123)")
    private Map<String, Object> additionalBodyProperty;

    @UriParam(prefix = "mcpServer.", multiValue = true)
    @Metadata(description = "MCP (Model Context Protocol) server configurations. "
                            + "Define servers using prefix notation: mcpServer.<name>.transportType=stdio|sse|streamableHttp, "
                            + "mcpServer.<name>.command=<cmd> (stdio), mcpServer.<name>.args=<comma-separated> (stdio), "
                            + "mcpServer.<name>.url=<url> (sse/streamableHttp), "
                            + "mcpServer.<name>.oauthProfile=<profile> (OAuth profile for HTTP auth, requires camel-oauth)")
    private Map<String, Object> mcpServer;

    @UriParam(defaultValue = "50")
    @Metadata(description = "Maximum number of tool call loop iterations to prevent infinite loops")
    private int maxToolIterations = 50;

    @UriParam(defaultValue = "true")
    @Metadata(description = "When true and MCP servers are configured, automatically execute tool calls "
                            + "and loop back to the model. When false, tool calls are returned as the message body for manual handling.")
    private boolean autoToolExecution = true;

    @UriParam
    @Metadata(description = "Comma-separated list of MCP protocol versions to advertise when connecting to MCP servers "
                            + "using Streamable HTTP transport. When not set, the SDK default is used. "
                            + "Example: 2024-11-05,2025-03-26,2025-06-18")
    private String mcpProtocolVersions;

    @UriParam(defaultValue = "20")
    @Metadata(description = "Timeout in seconds for MCP tool call requests. Applies to all MCP operations including "
                            + "tool execution and initialization.")
    private int mcpTimeout = 20;

    @UriParam(defaultValue = "true")
    @Metadata(description = "Automatically reconnect to MCP servers when a tool call fails due to a transport error, "
                            + "and retry the call once.")
    private boolean mcpReconnect = true;

    // ========== EMBEDDINGS CONFIGURATION ==========

    @UriParam
    @Metadata(description = "The model to use for embeddings")
    private String embeddingModel;

    @UriParam
    @Metadata(description = "Number of dimensions for the embedding output. Only supported by text-embedding-3 models. " +
                            "Reducing dimensions can lower costs and improve performance without significant quality loss.")
    private Integer dimensions;

    @UriParam(enums = "float,base64", defaultValue = "base64")
    @Metadata(description = "The format for embedding output: 'float' for list of floats, 'base64' for compressed format")
    private String encodingFormat = "base64";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getOauthProfile() {
        return oauthProfile;
    }

    public void setOauthProfile(String oauthProfile) {
        this.oauthProfile = oauthProfile;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }

    public String getOutputClass() {
        return outputClass;
    }

    public void setOutputClass(String outputClass) {
        this.outputClass = outputClass;
    }

    public String getJsonSchema() {
        return jsonSchema;
    }

    public void setJsonSchema(String jsonSchema) {
        this.jsonSchema = jsonSchema;
    }

    public boolean isConversationMemory() {
        return conversationMemory;
    }

    public void setConversationMemory(boolean conversationMemory) {
        this.conversationMemory = conversationMemory;
    }

    public String getConversationHistoryProperty() {
        return conversationHistoryProperty;
    }

    public void setConversationHistoryProperty(String conversationHistoryProperty) {
        this.conversationHistoryProperty = conversationHistoryProperty;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public String getSystemMessage() {
        return systemMessage;
    }

    public void setSystemMessage(String systemMessage) {
        this.systemMessage = systemMessage;
    }

    public String getDeveloperMessage() {
        return developerMessage;
    }

    public void setDeveloperMessage(String developerMessage) {
        this.developerMessage = developerMessage;
    }

    public boolean isStoreFullResponse() {
        return storeFullResponse;
    }

    public void setStoreFullResponse(boolean storeFullResponse) {
        this.storeFullResponse = storeFullResponse;
    }

    public Map<String, Object> getAdditionalBodyProperty() {
        return additionalBodyProperty;
    }

    public void setAdditionalBodyProperty(Map<String, Object> additionalBodyProperty) {
        this.additionalBodyProperty = additionalBodyProperty;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public Integer getDimensions() {
        return dimensions;
    }

    public void setDimensions(Integer dimensions) {
        this.dimensions = dimensions;
    }

    public String getEncodingFormat() {
        return encodingFormat;
    }

    public void setEncodingFormat(String encodingFormat) {
        this.encodingFormat = encodingFormat;
    }

    public Map<String, Object> getMcpServer() {
        return mcpServer;
    }

    public void setMcpServer(Map<String, Object> mcpServer) {
        this.mcpServer = mcpServer;
    }

    public int getMaxToolIterations() {
        return maxToolIterations;
    }

    public void setMaxToolIterations(int maxToolIterations) {
        this.maxToolIterations = maxToolIterations;
    }

    public boolean isAutoToolExecution() {
        return autoToolExecution;
    }

    public void setAutoToolExecution(boolean autoToolExecution) {
        this.autoToolExecution = autoToolExecution;
    }

    public String getMcpProtocolVersions() {
        return mcpProtocolVersions;
    }

    public void setMcpProtocolVersions(String mcpProtocolVersions) {
        this.mcpProtocolVersions = mcpProtocolVersions;
    }

    public int getMcpTimeout() {
        return mcpTimeout;
    }

    public void setMcpTimeout(int mcpTimeout) {
        this.mcpTimeout = mcpTimeout;
    }

    public boolean isMcpReconnect() {
        return mcpReconnect;
    }

    public void setMcpReconnect(boolean mcpReconnect) {
        this.mcpReconnect = mcpReconnect;
    }

    public OpenAIConfiguration copy() {
        try {
            return (OpenAIConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}
