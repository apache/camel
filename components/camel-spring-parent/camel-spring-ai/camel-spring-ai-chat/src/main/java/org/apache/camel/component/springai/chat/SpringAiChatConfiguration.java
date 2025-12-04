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

package org.apache.camel.component.springai.chat;

import java.util.List;
import java.util.Map;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.ai.vectorstore.VectorStore;

@UriParams
public class SpringAiChatConfiguration implements Cloneable {

    @UriParam
    @Metadata(autowired = true)
    private ChatClient chatClient;

    @UriParam
    @Metadata(autowired = true)
    private ChatModel chatModel;

    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private ChatMemory chatMemory;

    @UriParam(label = "advanced")
    private VectorStore chatMemoryVectorStore;

    @UriParam(defaultValue = "CHAT_SINGLE_MESSAGE")
    @Metadata(required = true)
    private SpringAiChatOperations chatOperation = SpringAiChatOperations.CHAT_SINGLE_MESSAGE;

    @UriParam(label = "rag")
    @Metadata(autowired = true)
    private VectorStore vectorStore;

    @UriParam(label = "rag", defaultValue = "5")
    private int topK = 5;

    @UriParam(label = "rag", defaultValue = "0.7")
    private double similarityThreshold = 0.7;

    @UriParam(label = "advanced")
    private Double temperature;

    @UriParam(label = "advanced")
    private Integer maxTokens;

    @UriParam(label = "advanced")
    private Double topP;

    @UriParam(label = "advanced")
    private Integer topKSampling;

    @UriParam
    private String userMessage;

    @UriParam
    private String systemMessage;

    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private StructuredOutputConverter<?> structuredOutputConverter;

    @UriParam(label = "advanced")
    private String outputFormat;

    @UriParam(label = "advanced")
    private Class<?> outputClass;

    @UriParam(description = "Tags for discovering and calling Camel route tools")
    private String tags;

    @UriParam(label = "security")
    private String safeguardSensitiveWords;

    @UriParam(label = "security")
    private String safeguardFailureResponse;

    @UriParam(label = "security,advanced")
    private Integer safeguardOrder;

    @UriParam(label = "advanced")
    @Metadata(autowired = false)
    private List<Advisor> advisors;

    @UriParam(label = "advanced")
    private Class<?> entityClass;

    @UriParam(label = "advanced")
    private Map<String, Object> userMetadata;

    @UriParam(label = "advanced")
    private Map<String, Object> systemMetadata;

    @UriParam(label = "rag", defaultValue = "Context:\\n\\{context}\\n\\nQuestion: \\{question}")
    private String ragTemplate = "Context:\n{context}\n\nQuestion: {question}";

    @UriParam(label = "advanced", defaultValue = "1048576")
    private long maxFileSize = 1024 * 1024; // 1MB default

    public ChatClient getChatClient() {
        return chatClient;
    }

    /**
     * The ChatClient instance to use for chat operations. ChatClient provides advanced features like memory management
     * and advisors. If not provided, a ChatClient will be created from the ChatModel.
     */
    public void setChatClient(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public ChatModel getChatModel() {
        return chatModel;
    }

    /**
     * The ChatModel instance to use for chat operations. This is used to create a ChatClient if one is not explicitly
     * provided.
     */
    public void setChatModel(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public ChatMemory getChatMemory() {
        return chatMemory;
    }

    /**
     * ChatMemory instance for maintaining conversation history across requests. When provided, conversation context
     * will be automatically managed using MessageChatMemoryAdvisor.
     */
    public void setChatMemory(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    public VectorStore getChatMemoryVectorStore() {
        return chatMemoryVectorStore;
    }

    /**
     * VectorStore instance for maintaining conversation history using semantic search. When provided, conversation
     * context will be automatically managed using VectorStoreChatMemoryAdvisor. This is an alternative to chatMemory
     * that uses vector embeddings for long-term memory with semantic retrieval.
     */
    public void setChatMemoryVectorStore(VectorStore chatMemoryVectorStore) {
        this.chatMemoryVectorStore = chatMemoryVectorStore;
    }

    public SpringAiChatOperations getChatOperation() {
        return chatOperation;
    }

    /**
     * The chat operation to perform.
     */
    public void setChatOperation(SpringAiChatOperations chatOperation) {
        this.chatOperation = chatOperation;
    }

    public VectorStore getVectorStore() {
        return vectorStore;
    }

    /**
     * VectorStore for automatic RAG retrieval.
     */
    public void setVectorStore(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public int getTopK() {
        return topK;
    }

    /**
     * Number of top documents to retrieve for RAG (default: 5).
     */
    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    /**
     * Similarity threshold for RAG retrieval (default: 0.7).
     */
    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public Double getTemperature() {
        return temperature;
    }

    /**
     * Temperature parameter for response randomness (0.0-2.0).
     */
    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    /**
     * Maximum tokens in the response.
     */
    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Double getTopP() {
        return topP;
    }

    /**
     * Top P parameter for nucleus sampling.
     */
    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Integer getTopKSampling() {
        return topKSampling;
    }

    /**
     * Top K parameter for sampling.
     */
    public void setTopKSampling(Integer topKSampling) {
        this.topKSampling = topKSampling;
    }

    public String getUserMessage() {
        return userMessage;
    }

    /**
     * Default user message text for multimodal requests. Can be combined with media data in the message body.
     */
    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public String getSystemMessage() {
        return systemMessage;
    }

    /**
     * Default system message to set context for the conversation. Can be overridden by the
     * CamelSpringAiChatSystemMessage header.
     */
    public void setSystemMessage(String systemMessage) {
        this.systemMessage = systemMessage;
    }

    public StructuredOutputConverter<?> getStructuredOutputConverter() {
        return structuredOutputConverter;
    }

    /**
     * A StructuredOutputConverter for converting the chat response to structured output (e.g., BeanOutputConverter,
     * MapOutputConverter, ListOutputConverter). When provided, the converter will be used to transform the response
     * into the desired format.
     */
    public void setStructuredOutputConverter(StructuredOutputConverter<?> structuredOutputConverter) {
        this.structuredOutputConverter = structuredOutputConverter;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    /**
     * The output format for structured output conversion (BEAN, MAP, LIST). Used in conjunction with outputClass for
     * BEAN format.
     */
    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    public Class<?> getOutputClass() {
        return outputClass;
    }

    /**
     * The Java class to use for BEAN output format conversion. Required when outputFormat is BEAN.
     */
    public void setOutputClass(Class<?> outputClass) {
        this.outputClass = outputClass;
    }

    public String getTags() {
        return tags;
    }

    /**
     * Tags for discovering and calling Camel route tools. When provided, the chat component will automatically register
     * tools from camel-spring-ai-tools routes matching these tags.
     */
    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getSafeguardSensitiveWords() {
        return safeguardSensitiveWords;
    }

    /**
     * Comma-separated list of sensitive words for SafeGuard advisor. When provided, the SafeGuard advisor will be
     * enabled to prevent generation of content containing these words.
     */
    public void setSafeguardSensitiveWords(String safeguardSensitiveWords) {
        this.safeguardSensitiveWords = safeguardSensitiveWords;
    }

    public String getSafeguardFailureResponse() {
        return safeguardFailureResponse;
    }

    /**
     * Failure response message for SafeGuard advisor when sensitive content is detected. If not specified, a default
     * message will be used.
     */
    public void setSafeguardFailureResponse(String safeguardFailureResponse) {
        this.safeguardFailureResponse = safeguardFailureResponse;
    }

    public Integer getSafeguardOrder() {
        return safeguardOrder;
    }

    /**
     * Order of execution for SafeGuard advisor. Lower numbers execute first. Default is 0.
     */
    public void setSafeguardOrder(Integer safeguardOrder) {
        this.safeguardOrder = safeguardOrder;
    }

    public List<Advisor> getAdvisors() {
        return advisors;
    }

    /**
     * List of custom advisors to add to the ChatClient. These advisors will be added after the built-in advisors
     * (SimpleLogger, SafeGuard, ChatMemory, QuestionAnswer) in the order they are provided in the list.
     */
    public void setAdvisors(List<Advisor> advisors) {
        this.advisors = advisors;
    }

    public Class<?> getEntityClass() {
        return entityClass;
    }

    /**
     * The Java class to use for entity response conversion using ChatClient.entity(Class). When specified, the response
     * will be automatically converted to this type instead of returning a String.
     */
    public void setEntityClass(Class<?> entityClass) {
        this.entityClass = entityClass;
    }

    public Map<String, Object> getUserMetadata() {
        return userMetadata;
    }

    /**
     * Metadata to attach to user messages. This metadata can be used for tracking conversation context, message
     * identifiers, or other application-specific data.
     */
    public void setUserMetadata(Map<String, Object> userMetadata) {
        this.userMetadata = userMetadata;
    }

    public Map<String, Object> getSystemMetadata() {
        return systemMetadata;
    }

    /**
     * Metadata to attach to system messages. This metadata can be used for tracking system prompt versions, model
     * configurations, or other application-specific data.
     */
    public void setSystemMetadata(Map<String, Object> systemMetadata) {
        this.systemMetadata = systemMetadata;
    }

    public String getRagTemplate() {
        return ragTemplate;
    }

    /**
     * Template for formatting RAG (Retrieval Augmented Generation) prompts when augmented data is provided. The
     * template supports two placeholders: {context} for the retrieved documents and {question} for the user's question.
     * Default template is "Context:\n{context}\n\nQuestion: {question}".
     */
    public void setRagTemplate(String ragTemplate) {
        this.ragTemplate = ragTemplate;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    /**
     * Maximum file size in bytes for multimodal content (images, audio, PDFs, etc.). Files exceeding this size will be
     * rejected with an exception. Default is 1048576 bytes (1MB). Set to 0 to disable size checking (not recommended).
     */
    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public SpringAiChatConfiguration copy() {
        try {
            return (SpringAiChatConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}
