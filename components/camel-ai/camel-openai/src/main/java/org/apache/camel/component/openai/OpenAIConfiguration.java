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
import org.apache.camel.support.jsse.SSLContextParameters;

/**
 * Configuration for OpenAI component.
 */
@UriParams
public class OpenAIConfiguration implements Cloneable {

    @UriParam(security = "secret")
    @Metadata(description = "OpenAI API key. Can also be set via OPENAI_API_KEY environment variable.", security = "secret")
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

    @UriParam(defaultValue = "0")
    @Metadata(description = "Overall HTTP request timeout in milliseconds for the OpenAI SDK client. "
                            + "When 0 or negative, the SDK default (10 minutes) is used. "
                            + "Acts as the fallback for readTimeout and writeTimeout when those are not set.")
    private long requestTimeout;

    @UriParam(defaultValue = "0")
    @Metadata(description = "Timeout in milliseconds for establishing the TCP connection to the API. "
                            + "A connect timeout means the endpoint was unreachable, so the request never ran and is "
                            + "safe to retry. When 0 or negative, the SDK default (1 minute) is used.")
    private long connectTimeout;

    @UriParam(defaultValue = "0")
    @Metadata(description = "Timeout in milliseconds for reading the response. A read timeout means the model was slow "
                            + "mid-generation, so the request may have been processed. "
                            + "When 0 or negative, requestTimeout applies.")
    private long readTimeout;

    @UriParam(defaultValue = "0")
    @Metadata(description = "Timeout in milliseconds for writing the request body, which matters for large payloads "
                            + "such as audio and image uploads. When 0 or negative, requestTimeout applies.")
    private long writeTimeout;

    @UriParam(defaultValue = "2")
    @Metadata(description = "Maximum number of times the OpenAI SDK client retries failed requests. "
                            + "The SDK retry is rate-limit aware (honors Retry-After on 429).")
    private int maxRetries = 2;

    @UriParam(prefix = "additionalHeader.", multiValue = true)
    @Metadata(description = "Additional HTTP request headers to send with every API call "
                            + "(e.g. additionalHeader.OpenAI-Organization=my-org or additionalHeader.api-key=secret). "
                            + "Values may contain secrets.",
              security = "secret")
    private Map<String, Object> additionalHeader;

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

    @UriParam
    @Metadata(description = "Previous response id for OpenAI server-side conversation state (Responses API only)")
    private String previousResponseId;

    @UriParam
    @Metadata(description = "Comma-separated hosted tools for the Responses API: web_search, file_search, code_interpreter")
    private String builtinTools;

    @UriParam
    @Metadata(description = "JSON array of hosted MCP tool definitions (OpenAI Tool.Mcp) passed through to the Responses API",
              inputLanguage = "json", largeInput = true)
    private String hostedMcpTools;

    @UriParam
    @Metadata(description = "Comma-separated vector store ids required when builtinTools includes file_search")
    private String fileSearchVectorStoreIds;

    @UriParam(defaultValue = "false")
    @Metadata(description = "Enable conversation memory per Exchange")
    private boolean conversationMemory = false;

    @UriParam(defaultValue = "CamelOpenAIConversationHistory")
    @Metadata(description = "Exchange property name for storing conversation history")
    private String conversationHistoryProperty = "CamelOpenAIConversationHistory";

    @UriParam(defaultValue = "0")
    @Metadata(description = "When conversationMemory is enabled, retain at most this many messages in the exchange "
                            + "conversation history. System and developer messages are prepended separately and are not "
                            + "stored in history. Assistant tool-call blocks are kept intact and may retain slightly "
                            + "more than this limit to preserve tool result pairing. When 0, no message limit is applied.")
    private int maxHistoryMessages;

    @UriParam(defaultValue = "0")
    @Metadata(description = "When conversationMemory is enabled, trim conversation history using a token estimate "
                            + "(character count / 4, including image payload size for multi-modal user messages). "
                            + "Oldest segments are dropped first until the estimated tokens are within this limit. "
                            + "Assistant tool-call blocks are removed as a unit with their tool results. The most recent "
                            + "segment is always retained, even when it alone exceeds this limit. When 0, "
                            + "no token limit is applied.")
    private int maxHistoryTokens;

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
    @Metadata(description = "Store the full SDK response in non-streaming mode: chat-completion uses exchange property "
                            + "'CamelOpenAIResponse'; responses uses 'CamelOpenAIResponsesResponse'; "
                            + "moderation uses 'CamelOpenAIModerationResponse'; "
                            + "image-generation and image-edit use 'CamelOpenAIImageResponse'; "
                            + "embeddings uses 'CamelOpenAIEmbeddingsResponse'; "
                            + "audio transcription uses 'CamelOpenAIAudioTranscriptionResponse'; "
                            + "audio translation uses 'CamelOpenAIAudioTranslationResponse'")
    private boolean storeFullResponse = false;

    @UriParam(defaultValue = "false")
    @Metadata(description = "Strip <think>...</think> blocks from model responses (used by reasoning models like Qwen3, DeepSeek-R1). "
                            + "The thinking content is stored in the CamelOpenAIThinkingContent header.")
    private boolean stripThinking = false;

    @UriParam(prefix = "additionalBodyProperty.", multiValue = true)
    @Metadata(description = "Additional JSON properties to include in the request body (e.g. additionalBodyProperty.traceId=123)")
    private Map<String, Object> additionalBodyProperty;

    @UriParam(prefix = "additionalResponseHeader.", multiValue = true)
    @Metadata(description = "Map additional fields from the response message to Camel headers. "
                            + "The key is the field name in the API response, the value is the Camel header name "
                            + "(e.g. additionalResponseHeader.reasoning_content=CamelMyReasoningHeader)")
    private Map<String, Object> additionalResponseHeader;

    @UriParam(prefix = "mcpServer.", multiValue = true)
    @Metadata(description = "MCP (Model Context Protocol) server configurations. "
                            + "Define servers using prefix notation: mcpServer.<name>.transportType=stdio|sse|streamableHttp, (Note that sse is deprecated) "
                            + "mcpServer.<name>.command=<cmd> (stdio), mcpServer.<name>.args=<comma-separated> (stdio), "
                            + "mcpServer.<name>.url=<url> (sse/streamableHttp), "
                            + "mcpServer.<name>.oauthProfile=<profile> (OAuth profile for HTTP auth, requires camel-oauth), "
                            + "mcpServer.<name>.toolNames=<comma-separated> (optional include list to restrict which tools are registered from this server)")
    private Map<String, Object> mcpServer;

    @UriParam(defaultValue = "50")
    @Metadata(description = "Maximum number of tool call loop iterations to prevent infinite loops")
    private int maxToolIterations = 50;

    @UriParam(defaultValue = "0")
    @Metadata(description = "Maximum cumulative prompt plus completion tokens allowed across the MCP agentic loop. "
                            + "When 0 or negative, no token budget is enforced. Enforcement runs after each API call "
                            + "that requests further tool execution, so actual spend may exceed the configured budget "
                            + "by up to one call (typically the largest, as the prompt grows each iteration). "
                            + "A final text response is returned even when cumulative usage exceeds the budget.")
    private long maxAgenticTokens;

    @UriParam(defaultValue = "true")
    @Metadata(description = "When true and MCP servers are configured, automatically execute tool calls "
                            + "and loop back to the model. When false, tool calls are returned as the message body for manual handling.")
    private boolean autoToolExecution = true;

    @UriParam
    @Metadata(description = "Comma-separated tags for discovering route-based tools registered via the ai-tool component. "
                            + "When set, matching tools from the shared AiToolRegistry are exposed to the model alongside MCP tools.")
    private String tags;

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

    @UriParam(defaultValue = "true")
    @Metadata(description = "Refresh the advertised tool list when an MCP server notifies that its tools changed. "
                            + "Set to false to keep the tool list fixed to what was listed when the endpoint started, "
                            + "for deployments that require a deterministic set of tools.")
    private boolean mcpToolRefresh = true;

    @UriParam(enums = "failExchange,repromptModel", defaultValue = "failExchange")
    @Metadata(description = "Strategy for handling exceptions thrown during MCP tool execution. "
                            + "'failExchange' (default) propagates the exception to the Camel exchange so that standard Camel "
                            + "error handling (onException, dead-letter channel) can process it. This is the safer default "
                            + "because 'repromptModel' sends raw exception messages (which may contain connection strings, "
                            + "hostnames, or internal paths) to a third-party LLM provider. "
                            + "'repromptModel' catches the error and sends it back to the model as a tool result "
                            + "so the model can attempt to recover.")
    private ToolExecutionErrorStrategy toolExecutionErrorStrategy = ToolExecutionErrorStrategy.FAIL_EXCHANGE;

    @UriParam(enums = "failExchange,repromptModel", defaultValue = "failExchange")
    @Metadata(description = "Strategy for handling tool names hallucinated by the model (tool not found in any MCP server). "
                            + "'failExchange' (default) throws an IllegalStateException, failing the exchange immediately. "
                            + "'repromptModel' sends a corrective tool result listing the available tools so the model "
                            + "can self-correct and retry. The maxToolIterations option bounds retries.")
    private HallucinatedToolNameStrategy hallucinatedToolNameStrategy = HallucinatedToolNameStrategy.FAIL_EXCHANGE;

    @UriParam(defaultValue = "false")
    @Metadata(description = "Execute the tool calls returned by the model in a single response concurrently instead of "
                            + "sequentially. Tool calls in the same batch are independent by design, so this reduces the "
                            + "latency of a batch to that of its slowest tool. Results are always fed back to the model in "
                            + "the original tool call order. Note that with toolExecutionErrorStrategy=failExchange the "
                            + "sibling tool calls already dispatched complete before the exchange fails.")
    private boolean parallelToolExecution;

    @UriParam(defaultValue = "0")
    @Metadata(description = "Timeout in milliseconds for a batch of parallel tool calls, so that one slow tool cannot "
                            + "block the whole batch. The timeout applies to the batch as a whole, not per tool call. "
                            + "A tool call that exceeds it is cancelled and handled according to toolExecutionErrorStrategy. "
                            + "The default of 0 disables the batch timeout and relies on mcpTimeout, which already bounds "
                            + "each individual MCP request. Only used when parallelToolExecution=true.")
    private long parallelToolTimeout;

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

    // ========== AUDIO TRANSCRIPTION CONFIGURATION ==========

    @UriParam
    @Metadata(description = "The model to use for audio transcription (e.g., whisper-1, gpt-4o-transcribe)")
    private String audioModel;

    @UriParam
    @Metadata(description = "The language of the input audio in ISO-639-1 format (e.g., 'en'). Improves accuracy and latency.")
    private String audioLanguage;

    @UriParam
    @Metadata(description = "Optional text to guide the model's style or continue a previous audio segment")
    private String audioPrompt;

    @UriParam(enums = "json,text,srt,verbose_json,vtt", defaultValue = "json")
    @Metadata(description = "The format of the transcription output")
    private String audioResponseFormat = "json";

    @UriParam
    @Metadata(description = "Sampling temperature for transcription (0.0 to 1.0)")
    private Double audioTemperature;

    @UriParam
    @Metadata(description = "Comma-separated timestamp granularities: 'word', 'segment', or 'word,segment'. "
                            + "Only applicable with verbose_json response format.")
    private String audioTimestampGranularities;

    // ========== MODERATION CONFIGURATION ==========

    @UriParam(defaultValue = "omni-moderation-latest")
    @Metadata(description = "The model to use for moderation")
    private String moderationModel = "omni-moderation-latest";

    // ========== AUDIO SPEECH (TEXT-TO-SPEECH) CONFIGURATION ==========

    @UriParam
    @Metadata(description = "The model to use for text-to-speech (e.g., gpt-4o-mini-tts, tts-1, tts-1-hd)")
    private String speechModel;

    @UriParam(defaultValue = "alloy")
    @Metadata(description = "The voice to use for text-to-speech (e.g., alloy, echo, fable, onyx, nova, shimmer). "
                            + "See the OpenAI documentation for the full list of supported voices.")
    private String speechVoice = "alloy";

    @UriParam(enums = "mp3,opus,aac,flac,wav,pcm", defaultValue = "mp3")
    @Metadata(description = "The audio format for text-to-speech output")
    private String speechResponseFormat = "mp3";

    @UriParam
    @Metadata(description = "The speed of the generated audio, from 0.25 to 4.0 where 1.0 is normal speed")
    private Double speechSpeed;

    @UriParam
    @Metadata(description = "Optional instructions to control the voice of the generated audio. "
                            + "Does not work with tts-1 or tts-1-hd.")
    private String speechInstructions;

    // ========== IMAGE GENERATION/EDIT CONFIGURATION ==========

    @UriParam
    @Metadata(description = "The model to use for image generation or editing (e.g., gpt-image-1, gpt-image-1-mini, "
                            + "gpt-image-1.5, gpt-image-2). Required for the image-generation and image-edit "
                            + "operations, because the model determines which of the other image options are "
                            + "accepted. The DALL-E models are no longer offered by OpenAI, but remain valid values "
                            + "for OpenAI-compatible providers.")
    private String imageModel;

    @UriParam
    @Metadata(description = "The prompt describing the image to generate, or the edit to apply. For image-generation "
                            + "the message body is used when this is not set; for image-edit the body carries the "
                            + "input image, so the prompt must come from this option or from the "
                            + "CamelOpenAIImagePrompt header.",
              largeInput = true)
    private String imagePrompt;

    @UriParam
    @Metadata(description = "The size of the generated image (e.g., 1024x1024, 1536x1024, 1024x1536, auto). "
                            + "The accepted values depend on the model.")
    private String imageSize;

    @UriParam(enums = "auto,high,medium,low,hd,standard")
    @Metadata(description = "The quality of the generated image. GPT image models accept auto, high, medium and "
                            + "low; hd and standard are DALL-E values kept for OpenAI-compatible providers.")
    private String imageQuality;

    @UriParam(enums = "url,b64_json")
    @Metadata(description = "The response format of the generated image. The OpenAI images endpoint rejects this "
                            + "option: the GPT image models always return base64, and the DALL-E models that used to "
                            + "accept it are no longer offered. It is only sent when explicitly set, and is kept for "
                            + "OpenAI-compatible providers that still implement the older images API.")
    private String imageResponseFormat;

    @UriParam
    @Metadata(description = "The number of images to generate, between 1 and 10. dall-e-3 only supports 1.")
    private Integer imageCount;

    @UriParam(enums = "transparent,opaque,auto")
    @Metadata(description = "The background of the generated image. Only supported by the GPT image models, and a "
                            + "transparent background requires the png or webp output format.")
    private String imageBackground;

    @UriParam(enums = "png,jpeg,webp")
    @Metadata(description = "The output format of the generated image. Only supported by the GPT image models, "
                            + "which default to png.")
    private String imageOutputFormat;

    @UriParam
    @Metadata(description = "The compression level from 0 to 100 for the webp and jpeg output formats. "
                            + "Only supported by the GPT image models.")
    private Integer imageOutputCompression;

    @UriParam(enums = "vivid,natural")
    @Metadata(description = "The style of the generated image. A dall-e-3 option, so only useful with "
                            + "OpenAI-compatible providers.")
    private String imageStyle;

    @UriParam(enums = "low,auto")
    @Metadata(description = "The content moderation level applied to image generation. Only supported by the "
                            + "GPT image models.")
    private String imageModeration;

    @UriParam(enums = "high,low")
    @Metadata(description = "How closely the edit must match the style and features of the input image. "
                            + "Only supported by the image-edit operation on gpt-image-1 and gpt-image-1.5.")
    private String imageInputFidelity;

    // ========== SSL CONFIGURATION ==========

    @UriParam(label = "security")
    @Metadata(description = "SSLContextParameters to use for configuring SSL/TLS. "
                            + "When set, takes precedence over the individual sslTruststore*, sslKeystore*, and sslProtocol options.")
    private SSLContextParameters sslContextParameters;

    @UriParam(label = "security")
    @Metadata(description = "The location of the trust store file, used to validate the server's certificate")
    private String sslTruststoreLocation;

    @UriParam(label = "security", security = "secret")
    @Metadata(description = "The password for the trust store file. If a password is not set, the configured trust store can still "
                            + "be used, but integrity checking is disabled")
    private String sslTruststorePassword;

    @UriParam(label = "security", defaultValue = "JKS")
    @Metadata(description = "The file format of the trust store file")
    private String sslTruststoreType = "JKS";

    @UriParam(label = "security")
    @Metadata(description = "The location of the key store file. This is optional and can be used for two-way authentication "
                            + "for the OpenAI API")
    private String sslKeystoreLocation;

    @UriParam(label = "security", security = "secret")
    @Metadata(description = "The store password for the key store file")
    private String sslKeystorePassword;

    @UriParam(label = "security", defaultValue = "JKS")
    @Metadata(description = "The file format of the key store file")
    private String sslKeystoreType = "JKS";

    @UriParam(label = "security", security = "secret")
    @Metadata(description = "The password of the private key in the key store file")
    private String sslKeyPassword;

    @UriParam(label = "security", defaultValue = "TLSv1.3")
    @Metadata(description = "The SSL protocol used to generate the SSLContext")
    private String sslProtocol = "TLSv1.3";

    @UriParam(label = "security", defaultValue = "SunX509")
    @Metadata(description = "The algorithm used by the key manager factory for SSL connections")
    private String sslKeymanagerAlgorithm = "SunX509";

    @UriParam(label = "security", defaultValue = "PKIX")
    @Metadata(description = "The algorithm used by the trust manager factory for SSL connections")
    private String sslTrustmanagerAlgorithm = "PKIX";

    @UriParam(label = "security", defaultValue = "https", security = "insecure:ssl", insecureValue = "none")
    @Metadata(description = "The endpoint identification algorithm to validate the server hostname using the server certificate. "
                            + "Set to an empty string or 'none' to disable hostname verification")
    private String sslEndpointAlgorithm = "https";

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

    public long getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(long requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public long getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(long readTimeout) {
        this.readTimeout = readTimeout;
    }

    public long getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(long writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Map<String, Object> getAdditionalHeader() {
        return additionalHeader;
    }

    public void setAdditionalHeader(Map<String, Object> additionalHeader) {
        this.additionalHeader = additionalHeader;
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

    public String getPreviousResponseId() {
        return previousResponseId;
    }

    public void setPreviousResponseId(String previousResponseId) {
        this.previousResponseId = previousResponseId;
    }

    public String getBuiltinTools() {
        return builtinTools;
    }

    public void setBuiltinTools(String builtinTools) {
        this.builtinTools = builtinTools;
    }

    public String getHostedMcpTools() {
        return hostedMcpTools;
    }

    public void setHostedMcpTools(String hostedMcpTools) {
        this.hostedMcpTools = hostedMcpTools;
    }

    public String getFileSearchVectorStoreIds() {
        return fileSearchVectorStoreIds;
    }

    public void setFileSearchVectorStoreIds(String fileSearchVectorStoreIds) {
        this.fileSearchVectorStoreIds = fileSearchVectorStoreIds;
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

    public int getMaxHistoryMessages() {
        return maxHistoryMessages;
    }

    public void setMaxHistoryMessages(int maxHistoryMessages) {
        this.maxHistoryMessages = maxHistoryMessages;
    }

    public int getMaxHistoryTokens() {
        return maxHistoryTokens;
    }

    public void setMaxHistoryTokens(int maxHistoryTokens) {
        this.maxHistoryTokens = maxHistoryTokens;
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

    public boolean isStripThinking() {
        return stripThinking;
    }

    public void setStripThinking(boolean stripThinking) {
        this.stripThinking = stripThinking;
    }

    public Map<String, Object> getAdditionalBodyProperty() {
        return additionalBodyProperty;
    }

    public void setAdditionalBodyProperty(Map<String, Object> additionalBodyProperty) {
        this.additionalBodyProperty = additionalBodyProperty;
    }

    public Map<String, Object> getAdditionalResponseHeader() {
        return additionalResponseHeader;
    }

    public void setAdditionalResponseHeader(Map<String, Object> additionalResponseHeader) {
        this.additionalResponseHeader = additionalResponseHeader;
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

    public String getAudioModel() {
        return audioModel;
    }

    public void setAudioModel(String audioModel) {
        this.audioModel = audioModel;
    }

    public String getAudioLanguage() {
        return audioLanguage;
    }

    public void setAudioLanguage(String audioLanguage) {
        this.audioLanguage = audioLanguage;
    }

    public String getAudioPrompt() {
        return audioPrompt;
    }

    public void setAudioPrompt(String audioPrompt) {
        this.audioPrompt = audioPrompt;
    }

    public String getAudioResponseFormat() {
        return audioResponseFormat;
    }

    public void setAudioResponseFormat(String audioResponseFormat) {
        this.audioResponseFormat = audioResponseFormat;
    }

    public Double getAudioTemperature() {
        return audioTemperature;
    }

    public void setAudioTemperature(Double audioTemperature) {
        this.audioTemperature = audioTemperature;
    }

    public String getAudioTimestampGranularities() {
        return audioTimestampGranularities;
    }

    public void setAudioTimestampGranularities(String audioTimestampGranularities) {
        this.audioTimestampGranularities = audioTimestampGranularities;
    }

    public String getModerationModel() {
        return moderationModel;
    }

    public void setModerationModel(String moderationModel) {
        this.moderationModel = moderationModel;
    }

    public String getSpeechModel() {
        return speechModel;
    }

    public void setSpeechModel(String speechModel) {
        this.speechModel = speechModel;
    }

    public String getSpeechVoice() {
        return speechVoice;
    }

    public void setSpeechVoice(String speechVoice) {
        this.speechVoice = speechVoice;
    }

    public String getSpeechResponseFormat() {
        return speechResponseFormat;
    }

    public void setSpeechResponseFormat(String speechResponseFormat) {
        this.speechResponseFormat = speechResponseFormat;
    }

    public Double getSpeechSpeed() {
        return speechSpeed;
    }

    public void setSpeechSpeed(Double speechSpeed) {
        this.speechSpeed = speechSpeed;
    }

    public String getSpeechInstructions() {
        return speechInstructions;
    }

    public void setSpeechInstructions(String speechInstructions) {
        this.speechInstructions = speechInstructions;
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

    public long getMaxAgenticTokens() {
        return maxAgenticTokens;
    }

    public void setMaxAgenticTokens(long maxAgenticTokens) {
        this.maxAgenticTokens = maxAgenticTokens;
    }

    public boolean isAutoToolExecution() {
        return autoToolExecution;
    }

    public void setAutoToolExecution(boolean autoToolExecution) {
        this.autoToolExecution = autoToolExecution;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
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

    public boolean isMcpToolRefresh() {
        return mcpToolRefresh;
    }

    public void setMcpToolRefresh(boolean mcpToolRefresh) {
        this.mcpToolRefresh = mcpToolRefresh;
    }

    public ToolExecutionErrorStrategy getToolExecutionErrorStrategy() {
        return toolExecutionErrorStrategy;
    }

    public void setToolExecutionErrorStrategy(ToolExecutionErrorStrategy toolExecutionErrorStrategy) {
        this.toolExecutionErrorStrategy = toolExecutionErrorStrategy;
    }

    public HallucinatedToolNameStrategy getHallucinatedToolNameStrategy() {
        return hallucinatedToolNameStrategy;
    }

    public void setHallucinatedToolNameStrategy(HallucinatedToolNameStrategy hallucinatedToolNameStrategy) {
        this.hallucinatedToolNameStrategy = hallucinatedToolNameStrategy;
    }

    public boolean isParallelToolExecution() {
        return parallelToolExecution;
    }

    public void setParallelToolExecution(boolean parallelToolExecution) {
        this.parallelToolExecution = parallelToolExecution;
    }

    public long getParallelToolTimeout() {
        return parallelToolTimeout;
    }

    public void setParallelToolTimeout(long parallelToolTimeout) {
        this.parallelToolTimeout = parallelToolTimeout;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public String getSslTruststoreLocation() {
        return sslTruststoreLocation;
    }

    public void setSslTruststoreLocation(String sslTruststoreLocation) {
        this.sslTruststoreLocation = sslTruststoreLocation;
    }

    public String getSslTruststorePassword() {
        return sslTruststorePassword;
    }

    public void setSslTruststorePassword(String sslTruststorePassword) {
        this.sslTruststorePassword = sslTruststorePassword;
    }

    public String getSslTruststoreType() {
        return sslTruststoreType;
    }

    public void setSslTruststoreType(String sslTruststoreType) {
        this.sslTruststoreType = sslTruststoreType;
    }

    public String getSslKeystoreLocation() {
        return sslKeystoreLocation;
    }

    public void setSslKeystoreLocation(String sslKeystoreLocation) {
        this.sslKeystoreLocation = sslKeystoreLocation;
    }

    public String getSslKeystorePassword() {
        return sslKeystorePassword;
    }

    public void setSslKeystorePassword(String sslKeystorePassword) {
        this.sslKeystorePassword = sslKeystorePassword;
    }

    public String getSslKeystoreType() {
        return sslKeystoreType;
    }

    public void setSslKeystoreType(String sslKeystoreType) {
        this.sslKeystoreType = sslKeystoreType;
    }

    public String getSslKeyPassword() {
        return sslKeyPassword;
    }

    public void setSslKeyPassword(String sslKeyPassword) {
        this.sslKeyPassword = sslKeyPassword;
    }

    public String getSslProtocol() {
        return sslProtocol;
    }

    public void setSslProtocol(String sslProtocol) {
        this.sslProtocol = sslProtocol;
    }

    public String getSslKeymanagerAlgorithm() {
        return sslKeymanagerAlgorithm;
    }

    public void setSslKeymanagerAlgorithm(String sslKeymanagerAlgorithm) {
        this.sslKeymanagerAlgorithm = sslKeymanagerAlgorithm;
    }

    public String getSslTrustmanagerAlgorithm() {
        return sslTrustmanagerAlgorithm;
    }

    public void setSslTrustmanagerAlgorithm(String sslTrustmanagerAlgorithm) {
        this.sslTrustmanagerAlgorithm = sslTrustmanagerAlgorithm;
    }

    public String getSslEndpointAlgorithm() {
        return sslEndpointAlgorithm;
    }

    public void setSslEndpointAlgorithm(String sslEndpointAlgorithm) {
        this.sslEndpointAlgorithm = sslEndpointAlgorithm;
    }

    public String getImageModel() {
        return imageModel;
    }

    public void setImageModel(String imageModel) {
        this.imageModel = imageModel;
    }

    public String getImagePrompt() {
        return imagePrompt;
    }

    public void setImagePrompt(String imagePrompt) {
        this.imagePrompt = imagePrompt;
    }

    public String getImageSize() {
        return imageSize;
    }

    public void setImageSize(String imageSize) {
        this.imageSize = imageSize;
    }

    public String getImageQuality() {
        return imageQuality;
    }

    public void setImageQuality(String imageQuality) {
        this.imageQuality = imageQuality;
    }

    public String getImageResponseFormat() {
        return imageResponseFormat;
    }

    public void setImageResponseFormat(String imageResponseFormat) {
        this.imageResponseFormat = imageResponseFormat;
    }

    public Integer getImageCount() {
        return imageCount;
    }

    public void setImageCount(Integer imageCount) {
        this.imageCount = imageCount;
    }

    public String getImageBackground() {
        return imageBackground;
    }

    public void setImageBackground(String imageBackground) {
        this.imageBackground = imageBackground;
    }

    public String getImageOutputFormat() {
        return imageOutputFormat;
    }

    public void setImageOutputFormat(String imageOutputFormat) {
        this.imageOutputFormat = imageOutputFormat;
    }

    public Integer getImageOutputCompression() {
        return imageOutputCompression;
    }

    public void setImageOutputCompression(Integer imageOutputCompression) {
        this.imageOutputCompression = imageOutputCompression;
    }

    public String getImageStyle() {
        return imageStyle;
    }

    public void setImageStyle(String imageStyle) {
        this.imageStyle = imageStyle;
    }

    public String getImageModeration() {
        return imageModeration;
    }

    public void setImageModeration(String imageModeration) {
        this.imageModeration = imageModeration;
    }

    public String getImageInputFidelity() {
        return imageInputFidelity;
    }

    public void setImageInputFidelity(String imageInputFidelity) {
        this.imageInputFidelity = imageInputFidelity;
    }

    public OpenAIConfiguration copy() {
        try {
            return (OpenAIConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}
