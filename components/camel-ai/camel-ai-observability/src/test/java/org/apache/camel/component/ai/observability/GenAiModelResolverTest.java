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

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenAiModelResolverTest extends ExchangeTestSupport {

    private ClassResolver classResolver;

    @BeforeEach
    void setUpClassResolver() {
        classResolver = context.getClassResolver();
    }

    @Test
    void shouldResolveOpenAiProviderFromChatModel() {
        assertThat(GenAiModelResolver.resolveSystem(classResolver, new FakeOpenAiChatModel())).isEqualTo("openai");
    }

    @Test
    void shouldResolveOllamaProviderFromChatModel() {
        assertThat(GenAiModelResolver.resolveSystem(classResolver, new FakeOllamaChatModel())).isEqualTo("ollama");
    }

    @Test
    void shouldResolveModelNameFromChatModelDefaults() {
        assertThat(GenAiModelResolver.resolveModelName(classResolver, new FakeOpenAiChatModel())).isEqualTo("gpt-4o");
    }

    @Test
    void shouldResolveResponseModelNameFromChatResponse() {
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("ok"))
                .modelName("gpt-4o-mini")
                .build();
        assertThat(GenAiModelResolver.resolveResponseModelName(classResolver, response, "gpt-4o")).isEqualTo("gpt-4o-mini");
    }

    @Test
    void shouldNotMatchOpenAiFromUnrelatedPackageName() {
        assertThat(GenAiModelResolver.resolveSystem(classResolver, new UnrelatedPackageModel())).isEqualTo("unknown");
    }

    @Test
    void shouldReturnUnknownForNullModel() {
        assertThat(GenAiModelResolver.resolveSystem(classResolver, null)).isEqualTo("unknown");
        assertThat(GenAiModelResolver.resolveModelName(classResolver, null)).isEqualTo("unknown");
    }

    @Test
    void shouldResolveGoogleProviderFromPackageName() {
        assertThat(GenAiModelResolver.resolveSystem(classResolver, new dev.langchain4j.model.google.FakeGoogleModel()))
                .isEqualTo("google");
    }

    @Test
    void shouldResolveVertexAiProviderFromPackageName() {
        assertThat(GenAiModelResolver.resolveSystem(classResolver, new dev.langchain4j.model.vertexai.FakeVertexAiModel()))
                .isEqualTo("gcp.vertex_ai");
    }

    static class FakeOpenAiChatModel implements ChatModel {
        @Override
        public ModelProvider provider() {
            return ModelProvider.OPEN_AI;
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return DefaultChatRequestParameters.builder().modelName("gpt-4o").build();
        }
    }

    static class FakeOllamaChatModel implements ChatModel {
        @Override
        public ModelProvider provider() {
            return ModelProvider.OLLAMA;
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return DefaultChatRequestParameters.builder().modelName("llama3").build();
        }
    }

    static class UnrelatedPackageModel implements EmbeddingModel {
        @Override
        public ModelProvider provider() {
            return ModelProvider.OTHER;
        }

        @Override
        public String modelName() {
            return "custom";
        }
    }

    @Test
    void shouldResolveSpringAiProviderFromPackageName() {
        assertThat(GenAiModelResolver.resolveSystem(classResolver, new org.springframework.ai.openai.FakeOpenAiChatModel()))
                .isEqualTo("openai");
    }

    @Test
    void shouldResolveSpringAiModelNameFromOptions() {
        assertThat(GenAiModelResolver.resolveModelName(classResolver, new org.springframework.ai.openai.FakeOpenAiChatModel()))
                .isEqualTo("gpt-4o");
    }

    @Test
    void shouldResolveSpringAiResponseModelNameFromMetadata() {
        Object response = new FakeSpringAiChatResponse("gpt-4o-mini");
        assertThat(GenAiModelResolver.resolveSpringAiResponseModelName(classResolver, response, "gpt-4o"))
                .isEqualTo("gpt-4o-mini");
    }

    static class FakeSpringAiChatResponse {
        private final FakeSpringAiResponseMetadata metadata;

        FakeSpringAiChatResponse(String model) {
            this.metadata = new FakeSpringAiResponseMetadata(model);
        }

        public FakeSpringAiResponseMetadata getMetadata() {
            return metadata;
        }
    }

    static class FakeSpringAiResponseMetadata {
        private final String model;

        FakeSpringAiResponseMetadata(String model) {
            this.model = model;
        }

        public String getModel() {
            return model;
        }
    }
}
