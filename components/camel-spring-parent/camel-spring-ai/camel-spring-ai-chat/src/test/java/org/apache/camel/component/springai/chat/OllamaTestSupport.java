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
package org.apache.camel.component.springai.chat;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.test.infra.ollama.services.OllamaService;
import org.apache.camel.test.infra.ollama.services.OllamaServiceFactory;
import org.apache.camel.test.infra.ollama.services.OpenAIService;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;

/**
 * Base test support class for the Spring AI chat integration tests. They run on Ollama by default, and on any
 * OpenAI-compatible server with -Dollama.instance.type=openai.
 */
public class OllamaTestSupport extends CamelTestSupport {

    protected ChatModel chatModel;

    @RegisterExtension
    static OllamaService OLLAMA = OllamaServiceFactory.createSingletonService();

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        chatModel = createModel();
    }

    protected void bindChatModel(CamelContext camelContext) {
        camelContext.getRegistry().bind("chatModel", chatModel);
    }

    protected String modelName() {
        return OLLAMA.modelName();
    }

    protected ChatModel createModel() {
        if (OLLAMA instanceof OpenAIService) {
            return createOpenAiModel();
        }

        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(OLLAMA.baseUrl())
                .build();

        // Reasoning models otherwise spend the whole token budget on the thinking channel and answer with
        // empty content, which makes the assertions in these tests fail or hang. Ollama accepts think=false
        // on models without a thinking mode too, so this is safe for every model.
        OllamaChatOptions ollamaOptions = OllamaChatOptions.builder()
                .model(modelName())
                .temperature(0.3)
                .disableThinking()
                .build();

        ChatModel chatModel = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .options(ollamaOptions)
                .build();

        return chatModel;
    }

    /**
     * The embedding model of the tests that fill a vector store: the embedding model of the OpenAI-compatible server
     * with -Dollama.instance.type=openai (-Dopenai.embedding.model), otherwise embeddinggemma:300m on Ollama.
     */
    protected EmbeddingModel createEmbeddingModel() {
        if (OLLAMA instanceof OpenAIService) {
            OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                    .baseUrl(OLLAMA.baseUrlV1())
                    .apiKey(OLLAMA.apiKey())
                    .model(OLLAMA.embeddingModelName())
                    .build();
            return OpenAiEmbeddingModel.builder().options(options).build();
        }

        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(OLLAMA.baseUrl())
                .build();

        return OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .options(OllamaEmbeddingOptions.builder()
                        .model("embeddinggemma:300m")
                        .build())
                .build();
    }

    /**
     * Any OpenAI-compatible server (-Dollama.instance.type=openai with -Dopenai.endpoint, -Dopenai.model and
     * -Dopenai.api.key). Thinking is turned off as disableThinking() does on Ollama: Ollama's OpenAI API reads
     * reasoning_effort, vLLM reads it too and also takes enable_thinking from the chat template arguments. The OpenAI
     * platform gets neither: it is strict about the request fields it accepts. -Dopenai.disable.thinking=false leaves
     * both out, for servers that reject reasoning_effort=none (vLLM before 0.18, GPT-OSS models).
     */
    private ChatModel createOpenAiModel() {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .baseUrl(OLLAMA.baseUrlV1())
                .apiKey(OLLAMA.apiKey())
                .model(modelName())
                .temperature(0.3);
        if (Boolean.parseBoolean(System.getProperty("openai.disable.thinking", "true"))
                && !isOpenAiPlatform(OLLAMA.baseUrlV1())) {
            options.extraBody(Map.of(
                    "reasoning_effort", "none",
                    "chat_template_kwargs", Map.of("enable_thinking", false)));
        }
        return OpenAiChatModel.builder().options(options.build()).build();
    }

    private static boolean isOpenAiPlatform(String baseUrl) {
        String host = URI.create(baseUrl).getHost();
        if (host == null) {
            return false;
        }
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("api.openai.com") || host.endsWith(".api.openai.com");
    }
}
