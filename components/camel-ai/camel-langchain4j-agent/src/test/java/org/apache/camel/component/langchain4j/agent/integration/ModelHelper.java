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

import static java.time.Duration.ofSeconds;

import java.util.Optional;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.apache.camel.test.infra.ollama.services.OllamaService;

public class ModelHelper {

    public static final String API_KEY = "API_KEY";
    public static final String MODEL_PROVIDER = "MODEL_PROVIDER";
    public static final String MODEL_BASE_URL = "MODEL_BASE_URL";
    public static final String MODEL_NAME = "MODEL_NAME";
    public static final String DEFAULT_OLLAMA_MODEL_NAME = "granite4:tiny-h";
    public static final String DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434/";

    protected static ChatModel createGeminiModel(String apiKey) {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gemini-2.5-flash")
                .temperature(1.0)
                .timeout(ofSeconds(60))
                .logRequestsAndResponses(true)
                .build();
    }

    protected static ChatModel createOpenAiModel(String apiKey) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(OpenAiChatModelName.GPT_4_O_MINI)
                .temperature(1.0)
                .timeout(ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    protected static ChatModel createExternalChatModel(String name, String apiKey) {
        return switch (name) {
            case "gemini" -> createGeminiModel(apiKey);
            case "openai" -> createOpenAiModel(apiKey);
            case "ollama" -> createOllamaModel(apiKey);
            default -> throw new IllegalArgumentException("Unknown chat model: " + name);
        };
    }

    private static ChatModel createOllamaModel(String apiKey) {
        String baseUrl = Optional.ofNullable(System.getenv(MODEL_BASE_URL)).orElse(DEFAULT_OLLAMA_BASE_URL);
        String modelName = Optional.ofNullable(System.getenv(MODEL_NAME)).orElse(DEFAULT_OLLAMA_MODEL_NAME);

        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(1.0)
                .timeout(ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    public static ChatModel loadFromEnv() {
        var apiKey = System.getenv(API_KEY);
        var modelProvider = System.getenv(MODEL_PROVIDER);

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("API_KEY system property is required for testing");
        }
        if (modelProvider == null || modelProvider.trim().isEmpty()) {
            throw new IllegalStateException("MODEL_PROVIDER system property is required for testing");
        }

        return ModelHelper.createExternalChatModel(modelProvider, apiKey);
    }

    public static EmbeddingModel createEmbeddingModel() {
        var apiKey = System.getenv(API_KEY);

        // Create embeddings
        if ("ollama".equals(System.getenv(MODEL_PROVIDER))) {
            String baseUrl = Optional.ofNullable(System.getenv(MODEL_BASE_URL)).orElse(DEFAULT_OLLAMA_BASE_URL);
            String modelName = Optional.ofNullable(System.getenv(MODEL_NAME)).orElse(DEFAULT_OLLAMA_MODEL_NAME);

            return OllamaEmbeddingModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .build();
        } else {
            return OpenAiEmbeddingModel.builder()
                    .apiKey(apiKey)
                    .modelName("text-embedding-ada-002")
                    .timeout(ofSeconds(30))
                    .build();
        }
    }

    public static boolean environmentWithoutEmbeddings() {
        var apiKey = System.getenv(API_KEY);
        if (apiKey == null) {
            return false;
        }

        var modelProvider = System.getenv(MODEL_PROVIDER);
        if (modelProvider == null) {
            return false;
        }

        return true;
    }

    public static boolean isEmbeddingCapable() {
        var modelProvider = System.getenv(MODEL_PROVIDER);

        if ("openai".equals(modelProvider)) {
            return true;
        } else {
            modelProvider = System.getenv(MODEL_PROVIDER);
        }

        return "openai".equals(modelProvider) || "ollama".equals(modelProvider);
    }

    public static String getApiKey() {
        return System.getenv(API_KEY);
    }

    /**
     * Load chat model from environment variables if configured, otherwise use OllamaService. This allows tests to run
     * without requiring external API keys.
     */
    public static ChatModel loadChatModel(OllamaService ollamaService) {
        var apiKey = System.getenv(API_KEY);
        var modelProvider = System.getenv(MODEL_PROVIDER);

        if (apiKey != null
                && !apiKey.trim().isEmpty()
                && modelProvider != null
                && !modelProvider.trim().isEmpty()) {
            // Use environment-configured model
            return loadFromEnv();
        }

        // Fallback to Ollama service
        return OllamaChatModel.builder()
                .baseUrl(ollamaService.baseUrl())
                .modelName(ollamaService.modelName())
                .temperature(0.3)
                .timeout(ofSeconds(60))
                .build();
    }

    /**
     * Load embedding model from environment variables if configured, otherwise use OllamaService. This allows tests to
     * run without requiring external API keys.
     */
    public static EmbeddingModel loadEmbeddingModel(OllamaService ollamaService) {
        var apiKey = System.getenv(API_KEY);
        var modelProvider = System.getenv(MODEL_PROVIDER);

        if (apiKey != null
                && !apiKey.trim().isEmpty()
                && modelProvider != null
                && !modelProvider.trim().isEmpty()) {
            // Use environment-configured embedding model
            return createEmbeddingModel();
        }

        // Fallback to Ollama service
        return OllamaEmbeddingModel.builder()
                .baseUrl(ollamaService.baseUrl())
                .modelName(ollamaService.modelName())
                .timeout(ofSeconds(60))
                .build();
    }

    /**
     * Check if environment variables are configured for testing. If false, tests should use OllamaService instead.
     */
    public static boolean hasEnvironmentConfiguration() {
        var apiKey = System.getenv(API_KEY);
        var modelProvider = System.getenv(MODEL_PROVIDER);
        return apiKey != null
                && !apiKey.trim().isEmpty()
                && modelProvider != null
                && !modelProvider.trim().isEmpty();
    }
}
