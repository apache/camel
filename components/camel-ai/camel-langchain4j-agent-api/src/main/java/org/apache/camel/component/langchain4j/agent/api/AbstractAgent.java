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

import java.util.List;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import org.apache.camel.util.ObjectHelper;

/**
 * Abstract base class for AI agents that provides common configuration logic for building LangChain4j AI services.
 *
 * <p>
 * This class encapsulates the shared logic for configuring:
 * <ul>
 * <li>Apache Camel Tool Provider</li>
 * <li>MCP (Model Context Protocol) clients</li>
 * <li>Custom LangChain4j tools</li>
 * <li>RAG (Retrieval Augmented Generation)</li>
 * <li>Input and Output Guardrails</li>
 * </ul>
 *
 * <p>
 * Subclasses must implement the {@link #chat(AiAgentBody, ToolProvider)} method to provide specific chat behavior.
 *
 * @param <S> the type of the AI service interface (e.g., AiAgentWithMemoryService or AiAgentWithoutMemoryService)
 */
public abstract class AbstractAgent<S> implements Agent {

    protected final AgentConfiguration configuration;

    protected AbstractAgent(AgentConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Gets the agent configuration.
     *
     * @return the agent configuration
     */
    protected AgentConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Configures the common aspects of the AI service builder.
     *
     * <p>
     * This method applies the following configurations to the builder:
     * <ul>
     * <li>Apache Camel Tool Provider (if provided)</li>
     * <li>MCP Tool Provider (if MCP clients are configured)</li>
     * <li>Custom LangChain4j tools (if configured)</li>
     * <li>RAG Retrieval Augmentor (if configured)</li>
     * <li>Input Guardrails (if configured)</li>
     * <li>Output Guardrails (if configured)</li>
     * </ul>
     *
     * @param builder      the AI services builder to configure
     * @param toolProvider the Apache Camel tool provider (may be null)
     */
    @SuppressWarnings("unchecked")
    protected void configureBuilder(AiServices<S> builder, ToolProvider toolProvider) {
        // Apache Camel Tool Provider
        if (toolProvider != null) {
            builder.toolProvider(toolProvider);
        }

        // MCP Clients - create MCP ToolProvider if MCP clients are configured
        if (ObjectHelper.isNotEmpty(configuration.getMcpClients())) {
            McpToolProvider.Builder mcpBuilder = McpToolProvider.builder()
                    .mcpClients(configuration.getMcpClients());

            // Apply MCP tool filter if configured
            if (configuration.getMcpToolProviderFilter() != null) {
                mcpBuilder.filter(configuration.getMcpToolProviderFilter());
            }

            builder.toolProvider(mcpBuilder.build());
        }

        // Additional custom LangChain4j Tool Instances (objects with @Tool methods)
        if (configuration.getCustomTools() != null && !configuration.getCustomTools().isEmpty()) {
            builder.tools(configuration.getCustomTools());
        }

        // RAG
        if (configuration.getRetrievalAugmentor() != null) {
            builder.retrievalAugmentor(configuration.getRetrievalAugmentor());
        }

        // Input Guardrails
        if (configuration.getInputGuardrailClasses() != null && !configuration.getInputGuardrailClasses().isEmpty()) {
            builder.inputGuardrailClasses((List) configuration.getInputGuardrailClasses());
        }

        // Output Guardrails
        if (configuration.getOutputGuardrailClasses() != null && !configuration.getOutputGuardrailClasses().isEmpty()) {
            builder.outputGuardrailClasses((List) configuration.getOutputGuardrailClasses());
        }
    }
}
