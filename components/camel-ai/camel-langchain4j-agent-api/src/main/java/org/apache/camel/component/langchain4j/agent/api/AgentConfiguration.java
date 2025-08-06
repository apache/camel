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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(AgentConfiguration.class);

    private ChatModel chatModel;
    private ChatMemoryProvider chatMemoryProvider;
    private RetrievalAugmentor retrievalAugmentor;
    private List<Class<?>> inputGuardrailClasses;
    private List<Class<?>> outputGuardrailClasses;

    public ChatModel getChatModel() {
        return chatModel;
    }

    public AgentConfiguration withChatModel(ChatModel chatModel) {
        this.chatModel = chatModel;
        return this;
    }

    public ChatMemoryProvider getChatMemoryProvider() {
        return chatMemoryProvider;
    }

    public AgentConfiguration withChatMemoryProvider(ChatMemoryProvider chatMemoryProvider) {
        this.chatMemoryProvider = chatMemoryProvider;
        return this;
    }

    public RetrievalAugmentor getRetrievalAugmentor() {
        return retrievalAugmentor;
    }

    public AgentConfiguration withRetrievalAugmentor(RetrievalAugmentor retrievalAugmentor) {
        this.retrievalAugmentor = retrievalAugmentor;
        return this;
    }

    public List<Class<?>> getInputGuardrailClasses() {
        return inputGuardrailClasses;
    }

    public AgentConfiguration withInputGuardrailClassesList(String inputGuardrailClasses) {
        return withInputGuardrailClasses(parseGuardrailClasses(inputGuardrailClasses));
    }

    public AgentConfiguration withInputGuardrailClasses(List<Class<?>> inputGuardrailClasses) {
        this.inputGuardrailClasses = inputGuardrailClasses;
        return this;
    }

    public List<Class<?>> getOutputGuardrailClasses() {
        return outputGuardrailClasses;
    }

    public AgentConfiguration withOutputGuardrailClassesList(String outputGuardrailClasses) {
        return withOutputGuardrailClasses(parseGuardrailClasses(outputGuardrailClasses));
    }

    public AgentConfiguration withOutputGuardrailClasses(List<Class<?>> outputGuardrailClasses) {
        this.outputGuardrailClasses = outputGuardrailClasses;
        return this;
    }

    /**
     * Parse comma-separated guardrail class names into a list of loaded classes.
     *
     * @param  guardrailClassNames comma-separated class names, can be null or empty
     * @return                     list of loaded classes, empty list if input is null or empty
     */
    public static List<Class<?>> parseGuardrailClasses(String guardrailClassNames) {
        if (guardrailClassNames == null || guardrailClassNames.trim().isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.stream(guardrailClassNames.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(AgentConfiguration::loadGuardrailClass)
                .filter(clazz -> clazz != null)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Load a guardrail class by name.
     *
     * @param  className the fully qualified class name
     * @return           the loaded class, or null if loading failed
     */
    protected static Class<?> loadGuardrailClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            LOG.warn("Failed to load guardrail class: {}", className, e);
            return null;
        }
    }
}
