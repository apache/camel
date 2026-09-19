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

    private static final String PROVIDERS
            = "ollama, openai, anthropic, azure-openai, mistral, gemini, vertex-ai, github, hugging-face, bedrock";

    private static ModelSpec spec(String provider, String modelName, String baseUrl, String apiKey) {
        return new ModelSpec(provider, null, modelName, baseUrl, apiKey, null, null, null);
    }

    private static ModelSpec custom(String className, String modelName, String baseUrl, String apiKey) {
        return new ModelSpec(null, className, modelName, baseUrl, apiKey, null, null, null);
    }

    @Test
    void ollamaChatModel() {
        ModelSpec spec = new ModelSpec(
                "ollama", null, "qwen2.5", "http://localhost:11434", null, 0.0,
                Duration.ofMinutes(2), Map.of("numPredict", "512"));
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
        ModelSpec spec = new ModelSpec("ollama", null, "qwen2.5", null, null, null, null, Map.of("maxTokens", "10"));
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
                .hasMessage("Unknown LangChain4j provider: llama. Use one of: " + PROVIDERS + ", or set customProvider"
                            + " to the fully qualified class name of a model class with a builder()");
        // a class name goes in customProvider, not in provider
        assertThatThrownBy(() -> LangChain4jModelFactory.createChatModel(context,
                spec("dev.langchain4j.model.ollama.OllamaChatModel", "x", null, null)))
                .hasMessage("Unknown LangChain4j provider: dev.langchain4j.model.ollama.OllamaChatModel. A class name is"
                            + " set as customProvider, not as provider; provider is one of: " + PROVIDERS);
        assertThatThrownBy(() -> LangChain4jModelFactory.createChatModel(context,
                new ModelSpec("ollama", "com.acme.Model", "x", null, null, null, null, null)))
                .hasMessage("Set either provider (ollama) or customProvider (com.acme.Model), not both");
    }

    @Test
    void providerAliases() {
        assertThat(Provider.of("OpenAI")).isEqualTo(Provider.OPENAI);
        assertThat(Provider.of("open-ai")).isEqualTo(Provider.OPENAI);
        assertThat(Provider.of("azure")).isEqualTo(Provider.AZURE_OPENAI);
        assertThat(Provider.of("google-ai-gemini")).isEqualTo(Provider.GEMINI);
        assertThat(Provider.of("huggingface")).isEqualTo(Provider.HUGGING_FACE);
        assertThat(Provider.of("aws-bedrock")).isEqualTo(Provider.BEDROCK);
        assertThat(Provider.of("com.acme.Model")).isNull();
        assertThat(LangChain4jModelFactory.modelClassName("gemini", null, ChatModel.class))
                .isEqualTo("dev.langchain4j.model.googleai.GoogleAiGeminiChatModel");
        assertThat(LangChain4jModelFactory.modelClassName("azure", null, EmbeddingModel.class))
                .isEqualTo("dev.langchain4j.model.azure.AzureOpenAiEmbeddingModel");
        assertThat(LangChain4jModelFactory.modelClassName("bedrock", null, EmbeddingModel.class))
                .isEqualTo("dev.langchain4j.model.bedrock.BedrockTitanEmbeddingModel");
        assertThat(LangChain4jModelFactory.modelClassName(null, "com.acme.Model", ChatModel.class))
                .isEqualTo("com.acme.Model");
        // the names the catalog offers for the provider option are the providers
        assertThat(Provider.NAMES.split(",")).hasSize(Provider.values().length)
                .allSatisfy(name -> assertThat(Provider.of(name)).isNotNull());
    }

    @Test
    void aCustomProviderIsAClassNameAndTheCommonOptionsFollowTheBuildersNames() {
        // as Hugging Face (accessToken, modelId) and Azure OpenAI (endpoint, deploymentName)
        ModelSpec spec = new ModelSpec(
                null, HostedModel.class.getName(), "tiny", "https://hub.example", "token", 0.5,
                Duration.ofSeconds(30), Map.of("waitForModel", "true"));
        ChatModel model = LangChain4jModelFactory.createChatModel(context, spec);

        HostedModel hosted = (HostedModel) model;
        assertThat(hosted.values).containsExactlyInAnyOrder("modelId=tiny", "endpoint=https://hub.example",
                "accessToken=token", "temperature=0.5", "timeout=PT30S", "waitForModel=true");
    }

    @Test
    void aCustomProviderThatIsNotAModelOfTheKind() {
        assertThatThrownBy(() -> LangChain4jModelFactory.createEmbeddingModel(context,
                custom(HostedModel.class.getName(), "x", null, null)))
                .hasMessage("LangChain4j provider " + HostedModel.class.getName() + ": " + HostedModel.class.getName()
                            + " is not a dev.langchain4j.model.embedding.EmbeddingModel");
    }

    @Test
    void aModelClassThatCannotInitializeNamesItselfAndTheCause() {
        // the class resolves, so a missing dependency of its module only shows when builder() initializes it
        assertThatThrownBy(() -> LangChain4jModelFactory.createChatModel(context,
                custom(UninitializableModel.class.getName(), "x", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                // the JVM rethrows an Error from a static initializer as-is (UnsatisfiedLinkError here) and reports
                // NoClassDefFoundError once the class has failed before, so only the family is pinned down
                .hasMessageStartingWith("Cannot initialize " + UninitializableModel.class.getName() + " (java.lang.")
                .hasMessageEndingWith("a dependency of its LangChain4j module may be missing from the classpath")
                .hasCauseInstanceOf(LinkageError.class);
    }

    @Test
    void aBuilderThatFailsNamesTheModelAndTheCause() {
        assertThatThrownBy(() -> LangChain4jModelFactory.createChatModel(context,
                custom(BuilderlessModel.class.getName(), "x", null, null)))
                .hasMessage("Cannot create " + BuilderlessModel.class.getName()
                            + " as its builder() failed: java.lang.IllegalStateException: no builder today")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    /** A chat model whose static initializer fails, as when a native library of the module is absent */
    public static final class UninitializableModel implements ChatModel {
        static {
            if (true) {
                throw new UnsatisfiedLinkError("no native library");
            }
        }

        public static Object builder() {
            return null;
        }
    }

    /** A chat model whose builder() throws */
    public static final class BuilderlessModel implements ChatModel {
        public static Object builder() {
            throw new IllegalStateException("no builder today");
        }
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
