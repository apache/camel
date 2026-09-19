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
package org.apache.camel.component.langchain4j.core;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.apache.camel.component.langchain4j.core.LangChain4jModelFactory.ModelSpec;
import org.apache.camel.component.langchain4j.core.LangChain4jModelFactory.Provider;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CAMEL-24820: a model is created from a provider name and the options every provider has, without a bean of the
 * provider's class.
 */
public class LangChain4jModelFactoryTest extends CamelTestSupport {

    private static ModelSpec spec(String provider, String modelName, String baseUrl, String apiKey) {
        return new ModelSpec(provider, modelName, baseUrl, apiKey, null, null, null);
    }

    @Test
    void ollamaChatModel() {
        ModelSpec spec = new ModelSpec(
                "ollama", "qwen2.5", "http://localhost:11434", null, 0.0, Duration.ofMinutes(2),
                Map.of("numPredict", "512"));
        ChatModel model = LangChain4jModelFactory.createChatModel(context, spec);
        assertThat(model).isInstanceOf(OllamaChatModel.class);

        EmbeddingModel embeddings = LangChain4jModelFactory.createEmbeddingModel(context,
                spec("Ollama", "nomic-embed-text", "http://localhost:11434", null));
        assertThat(embeddings).isInstanceOf(OllamaEmbeddingModel.class);
    }

    @Test
    void anOptionTheProviderDoesNotHaveNamesWhatItAccepts() {
        // Ollama has no API key
        assertThatThrownBy(() -> LangChain4jModelFactory.createChatModel(context, spec("ollama", "qwen2.5", null, "secret")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("LangChain4j provider ollama (dev.langchain4j.model.ollama.OllamaChatModel) has no"
                                        + " apiKey option; its builder accepts: baseUrl, ")
                .hasMessageContaining("modelName, numCtx, numPredict");
    }

    @Test
    void aModelPropertyTheBuilderDoesNotHaveNamesWhatItAccepts() {
        ModelSpec spec = new ModelSpec("ollama", "qwen2.5", null, null, null, null, Map.of("maxTokens", "10"));
        assertThatThrownBy(() -> LangChain4jModelFactory.createChatModel(context, spec))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Cannot configure dev.langchain4j.model.ollama.OllamaChatModel of LangChain4j"
                                        + " provider ollama: ")
                .hasMessageContaining("maxTokens=10")
                .hasMessageContaining("The builder dev.langchain4j.model.ollama.OllamaChatModel$OllamaChatModelBuilder"
                                      + " accepts: baseUrl, ");
    }

    @Test
    void aProviderThatIsNotOnTheClasspathNamesItsModule() {
        assertThatThrownBy(() -> LangChain4jModelFactory.createChatModel(context, spec("anthropic", "claude", null, "k")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("LangChain4j provider anthropic needs dev.langchain4j:langchain4j-anthropic on the classpath"
                            + " (with Camel JBang: camel.jbang.dependencies=dev.langchain4j:langchain4j-anthropic in"
                            + " application.properties, or --dep)");
        // and a provider without embedding models says so
        assertThatThrownBy(() -> LangChain4jModelFactory.createEmbeddingModel(context, spec("anthropic", "x", null, "k")))
                .hasMessage("LangChain4j provider anthropic has no embedding model; use another provider for embeddings");
    }

    @Test
    void anUnknownProviderListsTheKnownOnes() {
        assertThatThrownBy(() -> LangChain4jModelFactory.createChatModel(context, spec("llama", "x", null, null)))
                .hasMessage("Unknown LangChain4j provider: llama. Use one of: ollama, openai, anthropic, azure-openai,"
                            + " mistral, gemini, vertex-ai, github, hugging-face, or the fully qualified class name of a"
                            + " model class with a builder()");
    }

    @Test
    void providerAliases() {
        assertThat(Provider.of("OpenAI")).isEqualTo(Provider.OPENAI);
        assertThat(Provider.of("open-ai")).isEqualTo(Provider.OPENAI);
        assertThat(Provider.of("azure")).isEqualTo(Provider.AZURE_OPENAI);
        assertThat(Provider.of("google-ai-gemini")).isEqualTo(Provider.GEMINI);
        assertThat(Provider.of("huggingface")).isEqualTo(Provider.HUGGING_FACE);
        assertThat(Provider.of("com.acme.Model")).isNull();
        assertThat(LangChain4jModelFactory.modelClassName("gemini", ChatModel.class))
                .isEqualTo("dev.langchain4j.model.googleai.GoogleAiGeminiChatModel");
        assertThat(LangChain4jModelFactory.modelClassName("azure", EmbeddingModel.class))
                .isEqualTo("dev.langchain4j.model.azure.AzureOpenAiEmbeddingModel");
    }

    @Test
    void aClassNameIsAProviderAndTheCommonOptionsFollowTheBuildersNames() {
        // as Hugging Face (accessToken, modelId) and Azure OpenAI (endpoint, deploymentName)
        ModelSpec spec = new ModelSpec(
                HostedModel.class.getName(), "tiny", "https://hub.example", "token", 0.5,
                Duration.ofSeconds(30), Map.of("waitForModel", "true"));
        ChatModel model = LangChain4jModelFactory.createChatModel(context, spec);

        HostedModel hosted = (HostedModel) model;
        assertThat(hosted.values).containsExactlyInAnyOrder("modelId=tiny", "endpoint=https://hub.example", "accessToken=token",
                "temperature=0.5", "timeout=PT30S", "waitForModel=true");
    }

    @Test
    void aClassNameThatIsNotAModelOfTheKind() {
        assertThatThrownBy(
                () -> LangChain4jModelFactory.createEmbeddingModel(context, spec(HostedModel.class.getName(), "x", null, null)))
                .hasMessage("LangChain4j provider " + HostedModel.class.getName() + ": " + HostedModel.class.getName()
                            + " is not a dev.langchain4j.model.embedding.EmbeddingModel");
    }

    /** A chat model whose builder names the common options as Hugging Face and Azure do */
    public static final class HostedModel implements ChatModel {
        final List<String> values;

        private HostedModel(List<String> values) {
            this.values = values;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private final List<String> values = new java.util.ArrayList<>();

            public Builder modelId(String v) {
                values.add("modelId=" + v);
                return this;
            }

            public Builder endpoint(String v) {
                values.add("endpoint=" + v);
                return this;
            }

            public Builder accessToken(String v) {
                values.add("accessToken=" + v);
                return this;
            }

            public Builder temperature(Double v) {
                values.add("temperature=" + v);
                return this;
            }

            public Builder timeout(Duration v) {
                values.add("timeout=" + v);
                return this;
            }

            public Builder waitForModel(Boolean v) {
                values.add("waitForModel=" + v);
                return this;
            }

            public HostedModel build() {
                return new HostedModel(values);
            }
        }
    }
}
