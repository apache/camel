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
package org.apache.camel.component.ai.observability;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * Resolves GenAI provider and model metadata from LangChain4j model beans.
 */
public final class GenAiModelResolver {

    private static final String UNKNOWN = "unknown";

    private GenAiModelResolver() {
    }

    public static String resolveSystem(Object model) {
        if (model == null) {
            return UNKNOWN;
        }
        if (model instanceof ChatModel chatModel) {
            return mapProvider(chatModel.provider());
        }
        if (model instanceof EmbeddingModel embeddingModel) {
            return mapProvider(embeddingModel.provider());
        }
        String packageName = model.getClass().getPackageName();
        if (packageName.startsWith("org.springframework.ai.")) {
            return resolveSystemFromSpringAiPackage(packageName);
        }
        return resolveSystemFromPackage(packageName);
    }

    public static String resolveModelName(Object model) {
        if (model == null) {
            return UNKNOWN;
        }
        if (model instanceof ChatModel chatModel) {
            String modelName = chatModel.defaultRequestParameters().modelName();
            if (modelName != null && !modelName.isBlank()) {
                return modelName;
            }
        }
        if (model instanceof EmbeddingModel embeddingModel) {
            String modelName = embeddingModel.modelName();
            if (modelName != null && !modelName.isBlank()) {
                return modelName;
            }
        }
        if (model.getClass().getName().startsWith("org.springframework.ai.")) {
            return resolveSpringAiModelName(model);
        }
        return UNKNOWN;
    }

    /**
     * Resolves the response model from a LangChain4j {@link ChatResponse}, falling back when absent.
     */
    public static String resolveResponseModelName(ChatResponse chatResponse, String fallback) {
        if (chatResponse == null) {
            return fallback;
        }
        String modelName = chatResponse.modelName();
        return modelName != null && !modelName.isBlank() ? modelName : fallback;
    }

    /**
     * Resolves the response model from a Spring AI {@code ChatResponse}, falling back when absent.
     */
    public static String resolveSpringAiResponseModelName(Object chatResponse, String fallback) {
        if (chatResponse == null) {
            return fallback;
        }
        try {
            Object metadata = chatResponse.getClass().getMethod("getMetadata").invoke(chatResponse);
            if (metadata != null) {
                Object model = metadata.getClass().getMethod("getModel").invoke(metadata);
                if (model != null && !model.toString().isBlank()) {
                    return model.toString();
                }
            }
        } catch (ReflectiveOperationException e) {
            // ignore
        }
        return fallback;
    }

    private static String mapProvider(ModelProvider provider) {
        if (provider == null) {
            return UNKNOWN;
        }
        return switch (provider) {
            case OPEN_AI -> "openai";
            case ANTHROPIC -> "anthropic";
            case OLLAMA -> "ollama";
            case AZURE_OPEN_AI -> "azure.ai.openai";
            case GOOGLE_VERTEX_AI_GEMINI, GOOGLE_VERTEX_AI_ANTHROPIC -> "gcp.vertex_ai";
            case GOOGLE_AI_GEMINI, GOOGLE_GENAI -> "google";
            case MISTRAL_AI -> "mistral_ai";
            case AMAZON_BEDROCK -> "aws.bedrock";
            default -> UNKNOWN;
        };
    }

    private static String resolveSpringAiModelName(Object model) {
        try {
            Object options = invokeNoArg(model, "getDefaultOptions");
            if (options == null) {
                options = invokeNoArg(model, "getOptions");
            }
            if (options != null) {
                Object modelName = invokeNoArg(options, "getModel");
                if (modelName != null && !modelName.toString().isBlank()) {
                    return modelName.toString();
                }
            }
        } catch (ReflectiveOperationException e) {
            // ignore
        }
        return UNKNOWN;
    }

    private static Object invokeNoArg(Object target, String methodName) throws ReflectiveOperationException {
        var method = target.getClass().getMethod(methodName);
        if (!method.canAccess(target)) {
            method.setAccessible(true);
        }
        return method.invoke(target);
    }

    private static String resolveSystemFromSpringAiPackage(String packageName) {
        if (packageName.startsWith("org.springframework.ai.openai")) {
            return "openai";
        }
        if (packageName.startsWith("org.springframework.ai.ollama")) {
            return "ollama";
        }
        if (packageName.startsWith("org.springframework.ai.anthropic")) {
            return "anthropic";
        }
        if (packageName.startsWith("org.springframework.ai.azure")) {
            return "azure.ai.openai";
        }
        if (packageName.startsWith("org.springframework.ai.vertexai")) {
            return "gcp.vertex_ai";
        }
        if (packageName.startsWith("org.springframework.ai.bedrock")) {
            return "aws.bedrock";
        }
        if (packageName.startsWith("org.springframework.ai.mistralai")) {
            return "mistral_ai";
        }
        if (packageName.startsWith("org.springframework.ai.deepseek")) {
            return "deepseek";
        }
        return UNKNOWN;
    }

    private static String resolveSystemFromPackage(String packageName) {
        if (packageName == null || packageName.isBlank()) {
            return UNKNOWN;
        }
        if (packageName.startsWith("dev.langchain4j.model.openai")) {
            return "openai";
        }
        if (packageName.startsWith("dev.langchain4j.model.anthropic")) {
            return "anthropic";
        }
        if (packageName.startsWith("dev.langchain4j.model.ollama")) {
            return "ollama";
        }
        if (packageName.startsWith("dev.langchain4j.model.azure")) {
            return "azure.ai.openai";
        }
        if (packageName.startsWith("dev.langchain4j.model.vertexai")) {
            return "gcp.vertex_ai";
        }
        if (packageName.startsWith("dev.langchain4j.model.google")) {
            return "google";
        }
        if (packageName.startsWith("dev.langchain4j.model.mistralai")) {
            return "mistral_ai";
        }
        if (packageName.startsWith("dev.langchain4j.model.huggingface")) {
            return "huggingface";
        }
        if (packageName.startsWith("dev.langchain4j.model.bedrock")) {
            return "aws.bedrock";
        }
        return UNKNOWN;
    }
}
