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
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

import static java.time.Duration.ofSeconds;

public class ModelHelper {

    public static final String API_KEY = "API_KEY";
    public static final String MODEL_PROVIDER = "MODEL_PROVIDER";

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
            default -> throw new IllegalArgumentException("Unknown chat model: " + name);
        };
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
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName("text-embedding-ada-002")
                .timeout(ofSeconds(30))
                .build();
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

        return "openai".equals(modelProvider);
    }

    public static String getApiKey() {
        return System.getenv(API_KEY);
    }
}
