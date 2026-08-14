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

import java.util.HashSet;
import java.util.Set;

/**
 * Resolves GenAI provider and model metadata from LangChain4j and Spring AI model beans.
 * <p/>
 * LangChain4j types are resolved reflectively so callers such as {@code camel-spring-ai-chat} do not require
 * {@code langchain4j-core} on the classpath.
 */
public final class GenAiModelResolver {

    private static final String UNKNOWN = "unknown";
    private static final int MAX_MODEL_UNWRAP_DEPTH = 4;

    private static final String LANGCHAIN4J_CHAT_MODEL = "dev.langchain4j.model.chat.ChatModel";
    private static final String LANGCHAIN4J_EMBEDDING_MODEL = "dev.langchain4j.model.embedding.EmbeddingModel";
    private static final String LANGCHAIN4J_CHAT_RESPONSE = "dev.langchain4j.model.chat.response.ChatResponse";

    private static volatile Boolean langChain4jPresent;

    private GenAiModelResolver() {
    }

    public static String resolveSystem(Object model) {
        return resolveSystem(model, new HashSet<>(), 0);
    }

    public static String resolveModelName(Object model) {
        return resolveModelName(model, new HashSet<>(), 0);
    }

    /**
     * Resolves the response model from a LangChain4j or Spring AI chat response, falling back when absent.
     */
    public static String resolveResponseModelName(Object chatResponse, String fallback) {
        if (chatResponse == null) {
            return fallback;
        }
        if (isLangChain4jPresent() && isInstanceOf(chatResponse, LANGCHAIN4J_CHAT_RESPONSE)) {
            String modelName = invokeToString(chatResponse, "modelName");
            if (modelName != null && !modelName.isBlank()) {
                return modelName;
            }
        }
        return resolveSpringAiResponseModelName(chatResponse, fallback);
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

    private static String resolveSystem(Object model, Set<Integer> visited, int depth) {
        if (model == null || depth > MAX_MODEL_UNWRAP_DEPTH) {
            return UNKNOWN;
        }
        if (!markVisited(model, visited)) {
            return UNKNOWN;
        }
        if (isLangChain4jPresent() && isInstanceOf(model, LANGCHAIN4J_CHAT_MODEL)) {
            return mapLangChain4jProvider(invokeToString(model, "provider"));
        }
        if (isLangChain4jPresent() && isInstanceOf(model, LANGCHAIN4J_EMBEDDING_MODEL)) {
            return mapLangChain4jProvider(invokeToString(model, "provider"));
        }
        if (isSpringAiType(model)) {
            String fromUnderlyingModel = resolveSystemFromSpringAiUnderlyingModel(model, visited, depth + 1);
            if (!UNKNOWN.equals(fromUnderlyingModel)) {
                return fromUnderlyingModel;
            }
            String fromPackage = resolveSystemFromSpringAiPackage(resolveSpringAiPackageName(model));
            if (!UNKNOWN.equals(fromPackage)) {
                return fromPackage;
            }
        }
        return resolveSystemFromPackage(model.getClass().getPackageName());
    }

    private static String resolveModelName(Object model, Set<Integer> visited, int depth) {
        if (model == null || depth > MAX_MODEL_UNWRAP_DEPTH) {
            return UNKNOWN;
        }
        if (!markVisited(model, visited)) {
            return UNKNOWN;
        }
        if (isLangChain4jPresent() && isInstanceOf(model, LANGCHAIN4J_CHAT_MODEL)) {
            String modelName = resolveLangChain4jChatModelName(model);
            if (modelName != null && !modelName.isBlank()) {
                return modelName;
            }
        }
        if (isLangChain4jPresent() && isInstanceOf(model, LANGCHAIN4J_EMBEDDING_MODEL)) {
            String modelName = invokeToString(model, "modelName");
            if (modelName != null && !modelName.isBlank()) {
                return modelName;
            }
        }
        if (isSpringAiType(model)) {
            return resolveSpringAiModelName(model, visited, depth + 1);
        }
        return UNKNOWN;
    }

    private static String resolveLangChain4jChatModelName(Object model) {
        try {
            Object parameters = invokeNoArg(model, "defaultRequestParameters");
            if (parameters != null) {
                Object modelName = invokeNoArgOptional(parameters, "modelName");
                if (modelName != null && !modelName.toString().isBlank()) {
                    return modelName.toString();
                }
            }
        } catch (ReflectiveOperationException e) {
            // ignore
        }
        return null;
    }

    private static String resolveSystemFromSpringAiUnderlyingModel(Object model, Set<Integer> visited, int depth) {
        try {
            Object chatModel = invokeNoArgOptional(model, "getChatModel");
            if (chatModel != null && chatModel != model) {
                return resolveSystem(chatModel, visited, depth);
            }
        } catch (ReflectiveOperationException e) {
            // ignore
        }
        return UNKNOWN;
    }

    private static String resolveSpringAiModelName(Object model, Set<Integer> visited, int depth) {
        try {
            Object chatModel = invokeNoArgOptional(model, "getChatModel");
            if (chatModel != null && chatModel != model) {
                String resolved = resolveModelName(chatModel, visited, depth);
                if (!UNKNOWN.equals(resolved)) {
                    return resolved;
                }
            }
        } catch (ReflectiveOperationException e) {
            // ignore
        }
        try {
            Object options = invokeNoArgOptional(model, "getDefaultOptions");
            if (options == null) {
                options = invokeNoArgOptional(model, "getOptions");
            }
            if (options == null) {
                options = invokeNoArgOptional(model, "getDefaultChatOptions");
            }
            if (options != null) {
                Object modelName = invokeNoArgOptional(options, "getModel");
                if (modelName != null && !modelName.toString().isBlank()) {
                    return modelName.toString();
                }
            }
        } catch (ReflectiveOperationException e) {
            // ignore
        }
        return UNKNOWN;
    }

    private static boolean isSpringAiType(Object model) {
        if (resolveSpringAiPackageName(model) != null) {
            return true;
        }
        for (Class<?> iface : model.getClass().getInterfaces()) {
            if (iface.getName().startsWith("org.springframework.ai.")) {
                return true;
            }
        }
        return false;
    }

    private static String resolveSpringAiPackageName(Object model) {
        String packageName = model.getClass().getPackageName();
        if (packageName.startsWith("org.springframework.ai.")) {
            return packageName;
        }
        return null;
    }

    private static boolean markVisited(Object model, Set<Integer> visited) {
        return visited.add(System.identityHashCode(model));
    }

    private static boolean isLangChain4jPresent() {
        if (langChain4jPresent == null) {
            try {
                Class.forName(LANGCHAIN4J_CHAT_MODEL, false, GenAiModelResolver.class.getClassLoader());
                langChain4jPresent = true;
            } catch (ClassNotFoundException e) {
                langChain4jPresent = false;
            }
        }
        return langChain4jPresent;
    }

    private static boolean isInstanceOf(Object model, String className) {
        try {
            Class<?> type = Class.forName(className, false, model.getClass().getClassLoader());
            return type.isInstance(model);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static String invokeToString(Object target, String methodName) {
        try {
            Object value = invokeNoArg(target, methodName);
            return value != null ? value.toString() : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Object invokeNoArgOptional(Object target, String methodName) throws ReflectiveOperationException {
        try {
            return invokeNoArg(target, methodName);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String methodName) throws ReflectiveOperationException {
        return target.getClass().getMethod(methodName).invoke(target);
    }

    private static String mapLangChain4jProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return UNKNOWN;
        }
        return switch (provider) {
            case "OPEN_AI" -> "openai";
            case "ANTHROPIC" -> "anthropic";
            case "OLLAMA" -> "ollama";
            case "AZURE_OPEN_AI" -> "azure.ai.openai";
            case "GOOGLE_VERTEX_AI_GEMINI", "GOOGLE_VERTEX_AI_ANTHROPIC" -> "gcp.vertex_ai";
            case "GOOGLE_AI_GEMINI", "GOOGLE_GENAI" -> "google";
            case "MISTRAL_AI" -> "mistral_ai";
            case "AMAZON_BEDROCK" -> "aws.bedrock";
            default -> UNKNOWN;
        };
    }

    private static String resolveSystemFromSpringAiPackage(String packageName) {
        if (packageName == null) {
            return UNKNOWN;
        }
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
        if (packageName.startsWith("org.springframework.ai.chat.client")) {
            return UNKNOWN;
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
