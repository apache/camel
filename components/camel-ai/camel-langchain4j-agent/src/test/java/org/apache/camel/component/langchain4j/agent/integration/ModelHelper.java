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

package org.apache.camel.component.langchain4j.agent.integration;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.apache.camel.test.infra.ollama.services.OllamaService;
import org.apache.camel.test.infra.ollama.services.OpenAIService;

import static java.time.Duration.ofSeconds;

public class ModelHelper {

    /**
     * Load chat model from OllamaService. Detects if the service is OpenAIService and creates the appropriate model
     * type.
     */
    public static ChatModel loadChatModel(OllamaService ollamaService) {
        // Detect OpenAI service and create OpenAI model
        if (ollamaService instanceof OpenAIService openaiService) {
            return OpenAiChatModel.builder()
                    .apiKey(openaiService.apiKey())
                    .baseUrl(openaiService.baseUrl())
                    .modelName(openaiService.modelName())
                    .temperature(1.0)
                    .timeout(ofSeconds(120))
                    .maxTokens(500)
                    .maxCompletionTokens(500)
                    .logRequests(true)
                    .logResponses(true)
                    .build();
        }

        // Standard Ollama model
        return OllamaChatModel.builder()
                .baseUrl(ollamaService.baseUrl())
                .modelName(ollamaService.modelName())
                .temperature(0.3)
                .timeout(ofSeconds(120))
                .build();
    }

    /**
     * Load embedding model from OllamaService. Detects if the service is OpenAIService and creates the appropriate
     * model type.
     */
    public static EmbeddingModel loadEmbeddingModel(OllamaService ollamaService) {
        // Detect OpenAI service and create OpenAI embedding model
        if (ollamaService instanceof OpenAIService openaiService) {
            return OpenAiEmbeddingModel.builder()
                    .apiKey(openaiService.apiKey())
                    .baseUrl(openaiService.baseUrl())
                    .modelName(openaiService.embeddingModelName())
                    .timeout(ofSeconds(30))
                    .build();
        }

        // Standard Ollama embedding model
        return OllamaEmbeddingModel.builder()
                .baseUrl(ollamaService.baseUrl())
                .modelName(ollamaService.embeddingModelName())
                .timeout(ofSeconds(60))
                .build();
    }
}
