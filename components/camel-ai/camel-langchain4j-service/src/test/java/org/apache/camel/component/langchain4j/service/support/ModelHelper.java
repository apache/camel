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
package org.apache.camel.component.langchain4j.service.support;

import java.util.Map;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.apache.camel.test.infra.ollama.services.OllamaService;
import org.apache.camel.test.infra.ollama.services.OpenAIService;

import static java.time.Duration.ofSeconds;

/**
 * Builds the real chat model the integration tests run against.
 * <p>
 * The Ollama test-infra can hand back an {@link OpenAIService} instead of an Ollama one, so the model type has to
 * follow the service: pointing an {@link OllamaChatModel} at an OpenAI endpoint fails with no API key. Mirrors
 * {@code camel-langchain4j-agent}'s own {@code ModelHelper}.
 */
public final class ModelHelper {

    private ModelHelper() {
    }

    /**
     * The chat model the conformance unit tests run against: a real {@link OpenAiChatModel} talking to the in-repo
     * OpenAI mock server over HTTP.
     * <p>
     * Going through the real provider is the point. The prompt LangChain4j rendered, the tool-call protocol and the
     * provider's own mapping of an HTTP error onto a LangChain4j exception are all exercised for real, with nothing
     * standing in for the client. Only the server is a mock, and it runs in-process so a full scenario still takes
     * milliseconds.
     *
     * @param  baseUrl the mock server's base URL
     * @return         a chat model talking to it
     */
    public static ChatModel mockedChatModel(String baseUrl) {
        return OpenAiChatModel.builder()
                .apiKey("mock-api-key")
                .baseUrl(baseUrl)
                .modelName("gpt-4o-mini")
                .temperature(0.0)
                .timeout(ofSeconds(30))
                .build();
    }

    /**
     * The chat model for the service's own model name, at temperature 0 so the content assertions are as stable as a
     * model allows.
     *
     * @param  service the Ollama test-infra service, possibly OpenAI-backed
     * @return         a chat model matching the service
     */
    public static ChatModel loadChatModel(OllamaService service) {
        return loadChatModel(service, null);
    }

    /**
     * The same, with the model name overridden. Passing a name the server does not host is how a test makes the
     * invocation fail for real rather than by stubbing.
     *
     * @param  service   the Ollama test-infra service, possibly OpenAI-backed
     * @param  modelName the model to use, or null for the service's own
     * @return           a chat model matching the service
     */
    public static ChatModel loadChatModel(OllamaService service, String modelName) {
        if (service instanceof OpenAIService openaiService) {
            return OpenAiChatModel.builder()
                    .apiKey(openaiService.apiKey())
                    .baseUrl(openaiService.baseUrl())
                    .modelName(modelName != null ? modelName : openaiService.modelName())
                    .temperature(0.0)
                    .customParameters(Map.of("reasoning_effort", "none"))
                    .timeout(ofSeconds(120))
                    .maxTokens(500)
                    .maxCompletionTokens(500)
                    .logRequests(true)
                    .logResponses(true)
                    .build();
        }

        return OllamaChatModel.builder()
                .baseUrl(service.baseUrl())
                .modelName(modelName != null ? modelName : service.modelName())
                .temperature(0.0)
                .timeout(ofSeconds(120))
                .build();
    }
}
