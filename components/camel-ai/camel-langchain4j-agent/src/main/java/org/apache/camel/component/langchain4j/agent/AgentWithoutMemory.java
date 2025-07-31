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

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import org.apache.camel.Exchange;

/**
 * Implementation of Agent for AI agents without memory support. This agent handles chat interactions without
 * maintaining conversation history.
 *
 * This is an internal class used only within the LangChain4j agent component.
 */
class AgentWithoutMemory implements Agent {

    private final ChatModel chatModel;
    private final String tags;
    private final RetrievalAugmentor retrievalAugmentor;
    private final List<Class<?>> inputGuardrailClasses;
    private final List<Class<?>> outputGuardrailClasses;
    private final Exchange exchange;
    private final ToolProvider toolProvider;

    public AgentWithoutMemory(ChatModel chatModel, String tags, RetrievalAugmentor retrievalAugmentor,
                              List<Class<?>> inputGuardrailClasses, List<Class<?>> outputGuardrailClasses,
                              Exchange exchange, ToolProvider toolProvider) {
        this.chatModel = chatModel;
        this.tags = tags;
        this.retrievalAugmentor = retrievalAugmentor;
        this.inputGuardrailClasses = inputGuardrailClasses;
        this.outputGuardrailClasses = outputGuardrailClasses;
        this.exchange = exchange;
        this.toolProvider = toolProvider;
    }

    @Override
    public String chat(AiAgentBody aiAgentBody) {
        AiAgentWithoutMemoryService agentService = createAiAgentService();

        return aiAgentBody.getSystemMessage() != null
                ? agentService.chat(aiAgentBody.getUserMessage(), aiAgentBody.getSystemMessage())
                : agentService.chat(aiAgentBody.getUserMessage());
    }

    /**
     * Create AI service with a single universal tool that handles multiple Camel routes
     */
    private AiAgentWithoutMemoryService createAiAgentService() {
        var builder = AiServices.builder(AiAgentWithoutMemoryService.class)
                .chatModel(chatModel);

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
