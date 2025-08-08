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
    String chat(AiAgentBody aiAgentBody, ToolProvider toolProvider);

}
