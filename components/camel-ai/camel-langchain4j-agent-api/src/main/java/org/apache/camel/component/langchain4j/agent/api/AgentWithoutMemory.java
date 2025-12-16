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
 * Implementation of Agent for AI agents without memory support. This agent handles chat interactions without
 * maintaining conversation history.
 *
 * <p>
 * This is an internal class used only within the LangChain4j agent component.
 */
public class AgentWithoutMemory extends AbstractAgent<AiAgentWithoutMemoryService> {

    public AgentWithoutMemory(AgentConfiguration configuration) {
        super(configuration);
    }

    @Override
    public String chat(AiAgentBody<?> aiAgentBody, ToolProvider toolProvider) {
        AiAgentWithoutMemoryService agentService = createAiAgentService(toolProvider);

        String userMessage = aiAgentBody.getUserMessage();
        String systemMessage = aiAgentBody.getSystemMessage();
        Content content = aiAgentBody.getContent();

        if (content != null) {
            // Multi-modal message with content
            return systemMessage != null
                    ? agentService.chat(userMessage, content, systemMessage)
                    : agentService.chat(userMessage, content);
        } else {
            // Text-only message
            return systemMessage != null
                    ? agentService.chat(userMessage, systemMessage)
                    : agentService.chat(userMessage);
        }
    }

    /**
     * Create AI service with common configurations (no memory provider).
     */
    private AiAgentWithoutMemoryService createAiAgentService(ToolProvider toolProvider) {
        var builder = AiServices.builder(AiAgentWithoutMemoryService.class)
                .chatModel(configuration.getChatModel());

        configureBuilder(builder, toolProvider);

        return builder.build();
    }
}
