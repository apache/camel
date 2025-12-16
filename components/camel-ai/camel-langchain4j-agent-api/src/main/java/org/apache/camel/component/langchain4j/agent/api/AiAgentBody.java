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
package org.apache.camel.component.langchain4j.agent.api;

import dev.langchain4j.data.message.Content;

/**
 * Request body class for AI agent chat interactions in the Apache Camel LangChain4j integration.
 *
 * <p>
 * This class encapsulates all the information needed for a chat interaction with an AI agent, including the user's
 * message, optional system instructions, memory identification for stateful conversations, and content for non-text
 * media supported by LangChain4j.
 * </p>
 *
 * <p>
 * The class provides both constructor-based initialization and fluent builder-style methods for convenient object
 * creation and configuration. The generic type parameter {@code C} allows for type-safe handling of various content
 * types such as TextContent, ImageContent, AudioContent, VideoContent, or PdfFileContent.
 * </p>
 *
 * <p>
 * Usage examples:
 * </p>
 *
 * <pre>{@code
 * // Simple user message
 * AiAgentBody body = new AiAgentBody("Hello, how are you?");
 *
 * // With system message and memory ID
 * AiAgentBody body = new AiAgentBody(
 *         "What's the weather like?",
 *         "You are a helpful weather assistant",
 *         "user123");
 *
 * // Using fluent API with content
 * AiAgentBody<ImageContent> body = new AiAgentBody<ImageContent>()
 *         .withUserMessage("What do you see in this image?")
 *         .withSystemMessage("You are an image analysis assistant")
 *         .withMemoryId("session456")
 *         .withContent(imageContent);
 * }</pre>
 *
 * @param <C> the type of content (e.g., TextContent, ImageContent, AudioContent, VideoContent, PdfFileContent)
 * @since     4.9.0
 */
public class AiAgentBody<C extends Content> {
    private String userMessage;
    private String systemMessage;
    private Object memoryId;
    private C content;

    /**
     * Creates an empty AI agent body. Use the fluent setter methods to configure the instance.
     */
    public AiAgentBody() {
    }

    /**
     * Creates an AI agent body with a user message.
     *
     * @param userMessage the message from the user
     */
    public AiAgentBody(String userMessage) {
        this.userMessage = userMessage;
    }

    /**
     * Creates an AI agent body with all parameters.
     *
     * @param userMessage   the message from the user
     * @param systemMessage the system instructions for the AI agent
     * @param memoryId      the identifier for conversation memory (for stateful agents)
     */
    public AiAgentBody(String userMessage, String systemMessage, Object memoryId) {
        this.userMessage = userMessage;
        this.systemMessage = systemMessage;
        this.memoryId = memoryId;
    }

    /**
     * Sets the user message and returns this instance for method chaining.
     *
     * @param  userMessage the message from the user to send to the AI agent
     * @return             this AiAgentBody instance for fluent method chaining
     */
    public AiAgentBody<C> withUserMessage(String userMessage) {
        this.userMessage = userMessage;
        return this;
    }

    /**
     * Sets the system message and returns this instance for method chaining.
     *
     * <p>
     * The system message provides instructions or context to the AI agent about how it should behave or respond. This
     * is optional and may be {@code null}.
     * </p>
     *
     * @param  systemMessage the system instructions for the AI agent behavior
     * @return               this AiAgentBody instance for fluent method chaining
     */
    public AiAgentBody<C> withSystemMessage(String systemMessage) {
        this.systemMessage = systemMessage;
        return this;
    }

    /**
     * Sets the memory identifier and returns this instance for method chaining.
     *
     * <p>
     * The memory ID is used by stateful agents to maintain conversation history across multiple interactions. Different
     * memory IDs represent separate conversation sessions. This is optional and only relevant for agents with memory
     * support.
     * </p>
     *
     * @param  memoryId the identifier for conversation memory (typically a string or number)
     * @return          this AiAgentBody instance for fluent method chaining
     */
    public AiAgentBody<C> withMemoryId(Object memoryId) {
        this.memoryId = memoryId;
        return this;
    }

    /**
     * Gets the user message.
     *
     * @return the message from the user, or {@code null} if not set
     */
    public String getUserMessage() {
        return userMessage;
    }

    /**
     * Sets the user message.
     *
     * @param userMessage the message from the user to send to the AI agent
     */
    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    /**
     * Gets the system message.
     *
     * @return the system instructions for the AI agent, or {@code null} if not set
     */
    public String getSystemMessage() {
        return systemMessage;
    }

    /**
     * Sets the system message.
     *
     * <p>
     * The system message provides instructions or context to the AI agent about how it should behave or respond.
     * </p>
     *
     * @param systemMessage the system instructions for the AI agent behavior
     */
    public void setSystemMessage(String systemMessage) {
        this.systemMessage = systemMessage;
    }

    /**
     * Gets the memory identifier.
     *
     * @return the identifier for conversation memory, or {@code null} if not set
     */
    public Object getMemoryId() {
        return memoryId;
    }

    /**
     * Sets the memory identifier.
     *
     * <p>
     * The memory ID is used by stateful agents to maintain conversation history across multiple interactions. Different
     * memory IDs represent separate conversation sessions.
     * </p>
     *
     * @param memoryId the identifier for conversation memory (typically a string or number)
     */
    public void setMemoryId(Object memoryId) {
        this.memoryId = memoryId;
    }

    /**
     * Gets the content.
     *
     * <p>
     * The content can be any non-text media supported by LangChain4j, such as TextContent, ImageContent, AudioContent,
     * VideoContent, or PdfFileContent. This is not used by default by Camel, instead, it can be used by custom agents.
     * </p>
     *
     * @return the content object, or {@code null} if not set
     */
    public C getContent() {
        return content;
    }

    /**
     * Sets the content.
     *
     * <p>
     * The content can be any non-text media supported by LangChain4j, such as TextContent, ImageContent, AudioContent,
     * VideoContent, or PdfFileContent. This is not used by default by Camel, instead, it can be used by custom agents.
     * </p>
     *
     * @param content the content object to set
     */
    public void setContent(C content) {
        this.content = content;
    }

    /**
     * Sets the content and returns this instance for method chaining.
     *
     * <p>
     * The content can be any non-text media supported by LangChain4j, such as TextContent, ImageContent, AudioContent,
     * VideoContent, or PdfFileContent.This is not used by default by Camel, instead, it can be used by custom agents.
     * </p>
     *
     * @param  content the content object to set
     * @return         this AiAgentBody instance for fluent method chaining
     */
    public AiAgentBody<C> withContent(C content) {
        this.content = content;
        return this;
    }

}
