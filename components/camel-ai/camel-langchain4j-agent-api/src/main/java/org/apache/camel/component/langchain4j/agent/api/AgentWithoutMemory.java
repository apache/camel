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
 * Implementation of Agent for AI agents without memory support. This agent handles chat interactions without
 * maintaining conversation history.
 *
 * This is an internal class used only within the LangChain4j agent component.
 */
public class AgentWithoutMemory implements Agent {

    private final AgentConfiguration configuration;

    public AgentWithoutMemory(AgentConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public String chat(AiAgentBody<?> aiAgentBody, ToolProvider toolProvider) {
        AiAgentWithoutMemoryService agentService = createAiAgentService(toolProvider);

        return aiAgentBody.getSystemMessage() != null
                ? agentService.chat(aiAgentBody.getUserMessage(), aiAgentBody.getSystemMessage())
                : agentService.chat(aiAgentBody.getUserMessage());
    }

    /**
     * Create AI service with a single universal tool that handles multiple Camel routes and additional tools
     */
    private AiAgentWithoutMemoryService createAiAgentService(
            ToolProvider toolProvider) {
        var builder = AiServices.builder(AiAgentWithoutMemoryService.class)
                .chatModel(configuration.getChatModel());

        // Apache Camel Tool Provider
        if (toolProvider != null) {
            builder.toolProvider(toolProvider);
        }

        // MCP Clients - create MCP ToolProvider if MCP clients are configured
        // import org.apache.camel.util.ObjectHelper
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

        return builder.build();
    }
}
