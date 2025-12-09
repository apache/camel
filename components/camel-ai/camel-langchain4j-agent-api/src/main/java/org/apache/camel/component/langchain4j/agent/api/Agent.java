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

import dev.langchain4j.service.tool.ToolProvider;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadRuntimeException;

import static org.apache.camel.component.langchain4j.agent.api.Headers.MEMORY_ID;
import static org.apache.camel.component.langchain4j.agent.api.Headers.SYSTEM_MESSAGE;

/**
 * Core agent interface that abstracts different types of AI agents within the Apache Camel LangChain4j integration.
 *
 * <p>
 * This interface provides a unified abstraction for interacting with AI agents, regardless of whether they support
 * memory persistence or operate in a stateless manner. It serves as the primary contract for chat interactions between
 * Camel routes and LangChain4j AI services.
 * </p>
 *
 * <p>
 * Implementations handle the complexity of configuring LangChain4j AI services, including:
 * </p>
 * <ul>
 * <li>Chat model integration</li>
 * <li>Memory management (for stateful agents)</li>
 * <li>Tool provider integration</li>
 * <li>Retrieval-Augmented Generation (RAG) support</li>
 * <li>Input and output guardrails</li>
 * </ul>
 *
 * @since 4.9.0
 * @see   AgentWithMemory
 * @see   AgentWithoutMemory
 */
public interface Agent {

    /**
     * Processes and normalizes the exchange message payload into an {@link AiAgentBody} instance.
     *
     * <p>
     * This method serves as a payload adapter that ensures consistent input format for AI agent chat interactions. It
     * handles different payload types and automatically extracts relevant headers to construct a properly formatted
     * {@link AiAgentBody} object.
     * </p>
     *
     * <p>
     * The method performs the following transformations:
     * </p>
     * <ul>
     * <li>If the payload is already an {@link AiAgentBody}, it returns it unchanged</li>
     * <li>If the payload is a {@link String}, it creates a new {@link AiAgentBody} with:
     * <ul>
     * <li>The string as the user message</li>
     * <li>The {@link Headers#SYSTEM_MESSAGE} header value as the system message (if present)</li>
     * <li>The {@link Headers#MEMORY_ID} header value as the memory identifier (if present)</li>
     * </ul>
     * </li>
     * <li>For other payload types (WrappedFile, byte[], InputStream), it uses the Camel TypeConverter to convert to an
     * {@link AiAgentBody} with the appropriate content type. This supports file, ftp, sftp, aws2-s3,
     * azure-storage-blob, and other components.</li>
     * <li>If no conversion is possible, it throws an {@link InvalidPayloadRuntimeException}</li>
     * </ul>
     *
     * <p>
     * This method is typically called automatically by the LangChain4j agent component before invoking the
     * {@link #chat(AiAgentBody, ToolProvider)} method, ensuring that the agent always receives a properly structured
     * request body regardless of how the original message was formatted.
     * </p>
     *
     * @param  messagePayload                 the message payload from the exchange body; must be either an
     *                                        {@link AiAgentBody} or a {@link String}
     * @param  exchange                       the Camel exchange containing headers and context information
     * @return                                an {@link AiAgentBody} instance ready for agent processing; returns the
     *                                        original payload if it's already an {@link AiAgentBody}, or creates a new
     *                                        one from a string payload and relevant headers
     * @throws InvalidPayloadRuntimeException if the payload is neither an {@link AiAgentBody} nor a {@link String}
     * @throws Exception                      if any other error occurs during payload processing
     */
    default AiAgentBody<?> processBody(Object messagePayload, Exchange exchange) throws Exception {
        if (messagePayload instanceof AiAgentBody<?> payload) {
            return payload;
        }

        if (messagePayload instanceof String) {
            String systemMessage = exchange.getIn().getHeader(SYSTEM_MESSAGE, String.class);
            Object memoryId = exchange.getIn().getHeader(MEMORY_ID);
            return new AiAgentBody<>((String) messagePayload, systemMessage, memoryId);
        }

        // Try to convert using TypeConverter (supports WrappedFile, byte[], InputStream, etc.)
        AiAgentBody<?> body = exchange.getContext().getTypeConverter()
                .tryConvertTo(AiAgentBody.class, exchange, messagePayload);
        if (body != null) {
            return body;
        }

        throw new InvalidPayloadRuntimeException(exchange, AiAgentBody.class);
    }

    /**
     * Executes a chat interaction with the AI agent using the provided request body and tool provider.
     *
     * <p>
     * This method processes user messages and optional system messages through the configured LangChain4j AI service.
     * For agents with memory support, the memory ID in the request body is used to maintain conversation context across
     * multiple interactions.
     * </p>
     *
     * @param  aiAgentBody      the request body containing the user message, optional system message, and memory ID
     *                          (for stateful agents)
     * @param  toolProvider     the tool provider that enables the agent to execute functions and interact with external
     *                          systems; may be {@code null} if no tools are needed
     * @return                  the AI agent's response as a string
     * @throws RuntimeException if the chat interaction fails due to model errors, configuration issues, or tool
     *                          execution failures
     */
    String chat(AiAgentBody<?> aiAgentBody, ToolProvider toolProvider);

}
