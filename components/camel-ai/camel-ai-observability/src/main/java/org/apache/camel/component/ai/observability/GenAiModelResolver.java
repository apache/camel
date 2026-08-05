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

import java.lang.reflect.Method;

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
        String className = model.getClass().getName().toLowerCase();
        if (className.contains("openai")) {
            return "openai";
        }
        if (className.contains("anthropic")) {
            return "anthropic";
        }
        if (className.contains("ollama")) {
            return "ollama";
        }
        if (className.contains("azure")) {
            return "azure.ai.openai";
        }
        if (className.contains("vertex") || className.contains("google")) {
            return "gcp.vertex_ai";
        }
        if (className.contains("mistral")) {
            return "mistral_ai";
        }
        if (className.contains("huggingface") || className.contains("hugging")) {
            return "huggingface";
        }
        if (className.contains("bedrock") || className.contains("amazon")) {
            return "aws.bedrock";
        }
        return UNKNOWN;
    }

    public static String resolveModelName(Object model) {
        if (model == null) {
            return UNKNOWN;
        }
        String fromMethod = invokeStringMethod(model, "modelName");
        if (fromMethod != null && !fromMethod.isBlank()) {
            return fromMethod;
        }
        fromMethod = invokeStringMethod(model, "getModelName");
        if (fromMethod != null && !fromMethod.isBlank()) {
            return fromMethod;
        }
        fromMethod = invokeStringMethod(model, "model");
        if (fromMethod != null && !fromMethod.isBlank()) {
            return fromMethod;
        }
        return UNKNOWN;
    }

    private static String invokeStringMethod(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value != null ? value.toString() : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
