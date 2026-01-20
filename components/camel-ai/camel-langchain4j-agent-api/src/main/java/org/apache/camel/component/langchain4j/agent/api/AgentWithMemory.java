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
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;

/**
 * Implementation of Agent for AI agents with memory support. This agent handles chat interactions while maintaining
 * conversation history.
 *
 * <p>
 * This is an internal class used only within the LangChain4j agent component.
 */
public class AgentWithMemory extends AbstractAgent<AiAgentWithMemoryService> {

    public AgentWithMemory(AgentConfiguration configuration) {
        super(configuration);
    }

    @Override
    public String chat(AiAgentBody<?> aiAgentBody, ToolProvider toolProvider) {
        AiAgentWithMemoryService agentService = createAiAgentService(toolProvider);

        String userMessage = aiAgentBody.getUserMessage();
        Object memoryId = aiAgentBody.getMemoryId();
        String systemMessage = aiAgentBody.getSystemMessage();
        Content content = aiAgentBody.getContent();

        if (content != null) {
            // Multi-modal message with content
            return systemMessage != null
                    ? agentService.chat(memoryId, userMessage, content, systemMessage)
                    : agentService.chat(memoryId, userMessage, content);
        } else {
            // Text-only message
            return systemMessage != null
                    ? agentService.chat(memoryId, userMessage, systemMessage)
                    : agentService.chat(memoryId, userMessage);
        }
    }

    /**
     * Create AI service with memory provider and common configurations.
     */
    private AiAgentWithMemoryService createAiAgentService(ToolProvider toolProvider) {
        var builder = AiServices.builder(AiAgentWithMemoryService.class)
                .chatModel(configuration.getChatModel())
                .chatMemoryProvider(configuration.getChatMemoryProvider());

        configureBuilder(builder, toolProvider);

        return builder.build();
    }
}
