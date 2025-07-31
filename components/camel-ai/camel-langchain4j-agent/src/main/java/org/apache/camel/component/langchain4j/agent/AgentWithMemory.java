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
package org.apache.camel.component.langchain4j.agent;

import java.util.List;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import org.apache.camel.Exchange;

/**
 * Implementation of Agent for AI agents with memory support. This agent handles chat interactions while maintaining
 * conversation history.
 *
 * This is an internal class used only within the LangChain4j agent component.
 */
class AgentWithMemory implements Agent {

    private final ChatModel chatModel;
    private final ChatMemoryProvider chatMemoryProvider;
    private final String tags;
    private final RetrievalAugmentor retrievalAugmentor;
    private final List<Class<?>> inputGuardrailClasses;
    private final List<Class<?>> outputGuardrailClasses;
    private final Exchange exchange;
    private final ToolProvider toolProvider;

    public AgentWithMemory(ChatModel chatModel, ChatMemoryProvider chatMemoryProvider, String tags,
                           RetrievalAugmentor retrievalAugmentor, List<Class<?>> inputGuardrailClasses,
                           List<Class<?>> outputGuardrailClasses, Exchange exchange, ToolProvider toolProvider) {
        this.chatModel = chatModel;
        this.chatMemoryProvider = chatMemoryProvider;
        this.tags = tags;
        this.retrievalAugmentor = retrievalAugmentor;
        this.inputGuardrailClasses = inputGuardrailClasses;
        this.outputGuardrailClasses = outputGuardrailClasses;
        this.exchange = exchange;
        this.toolProvider = toolProvider;
    }

    @Override
    public String chat(AiAgentBody aiAgentBody) {
        AiAgentWithMemoryService agentService = createAiAgentService();

        return aiAgentBody.getSystemMessage() != null
                ? agentService.chat(aiAgentBody.getMemoryId(), aiAgentBody.getUserMessage(), aiAgentBody.getSystemMessage())
                : agentService.chat(aiAgentBody.getMemoryId(), aiAgentBody.getUserMessage());
    }

    /**
     * Create AI service with a single universal tool that handles multiple Camel routes and Memory Provider
     */
    private AiAgentWithMemoryService createAiAgentService() {
        var builder = AiServices.builder(AiAgentWithMemoryService.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider);

        // Apache Camel Tool Provider
        if (toolProvider != null) {
            builder.toolProvider(toolProvider);
        }

        // RAG
        if (retrievalAugmentor != null) {
            builder.retrievalAugmentor(retrievalAugmentor);
        }

        // Input Guardrails
        if (inputGuardrailClasses != null && !inputGuardrailClasses.isEmpty()) {
            builder.inputGuardrailClasses((List) inputGuardrailClasses);
        }

        // Output Guardrails
        if (outputGuardrailClasses != null && !outputGuardrailClasses.isEmpty()) {
            builder.outputGuardrailClasses((List) outputGuardrailClasses);
        }

        return builder.build();
    }
}
