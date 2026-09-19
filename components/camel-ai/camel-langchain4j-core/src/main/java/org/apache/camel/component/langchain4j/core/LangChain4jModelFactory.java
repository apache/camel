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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.apache.camel.CamelContext;
import org.apache.camel.PropertyBindingException;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.PropertiesHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates a LangChain4j {@link ChatModel} or {@link EmbeddingModel} from a provider name and the options every provider
 * has (model name, base URL, API key, temperature, timeout), so a model is configured in properties or on the endpoint
 * without a bean of the provider's class (CAMEL-24820):
 *
 * <pre>
 * camel.component.langchain4j-chat.provider = ollama
 * camel.component.langchain4j-chat.model-name = qwen2.5
 * camel.component.langchain4j-chat.base-url = http://localhost:11434
 * </pre>
 *
 * The provider names the model class of a LangChain4j module ({@code ollama} is
 * {@code dev.langchain4j.model.ollama.OllamaChatModel}), which is resolved through the Camel class resolver, so Camel
 * JBang downloads the module ({@code dev.langchain4j:langchain4j-ollama}) as it does for any known class. The class is
 * created through its {@code builder()}: the common options are set with the name the builder uses for them
 * ({@code apiKey} is {@code accessToken} on Hugging Face and {@code gitHubToken} on GitHub Models, {@code modelName} is
 * {@code deploymentName} on Azure OpenAI), and the provider-specific options of {@link ModelSpec#modelProperties()} are
 * set as they are. An option the builder does not have is an error naming what the builder accepts. The provider can
 * also be the fully qualified class name of any model class with a {@code builder()}.
 */
public final class LangChain4jModelFactory {

    private static final Logger LOG = LoggerFactory.getLogger(LangChain4jModelFactory.class);

    /**
     * The options every provider has, and the provider-specific extras.
     *
     * @param provider        the provider name (see {@link Provider}), or null when a custom provider is given
     * @param customProvider  the fully qualified class name of a model class with a builder(), for a provider that has
     *                        no name; null otherwise
     * @param modelName       the model, such as qwen2.5 or gpt-4o-mini
     * @param baseUrl         the URL of the provider's API, for a local or self-hosted provider
     * @param apiKey          the API key or access token of the provider
     * @param temperature     the sampling temperature
     * @param timeout         the request timeout
     * @param modelProperties provider-specific properties of the model's builder, set as they are (numPredict on
     *                        Ollama, maxTokens on OpenAI); may be null
     */
    public record ModelSpec(String provider, String customProvider, String modelName, String baseUrl, String apiKey,
            Double temperature, Duration timeout, Map<String, Object> modelProperties) {

        /** The provider as named in messages: the provider name, or the custom class name. */
        public String name() {
            return provider != null ? provider : customProvider;
        }
    }

    /** The LangChain4j providers with a short name, and the module each is in. */
    public enum Provider {
        OLLAMA("ollama", "dev.langchain4j.model.ollama", "OllamaChatModel", "OllamaEmbeddingModel", "langchain4j-ollama"),
        OPENAI("openai", "dev.langchain4j.model.openai", "OpenAiChatModel", "OpenAiEmbeddingModel", "langchain4j-open-ai",
               "open-ai"),
        ANTHROPIC("anthropic", "dev.langchain4j.model.anthropic", "AnthropicChatModel", null, "langchain4j-anthropic"),
        AZURE_OPENAI("azure-openai", "dev.langchain4j.model.azure", "AzureOpenAiChatModel", "AzureOpenAiEmbeddingModel",
                     "langchain4j-azure-open-ai", "azure-open-ai", "azure"),
        MISTRAL("mistral", "dev.langchain4j.model.mistralai", "MistralAiChatModel", "MistralAiEmbeddingModel",
                "langchain4j-mistral-ai", "mistral-ai", "mistralai"),
        GEMINI("gemini", "dev.langchain4j.model.googleai", "GoogleAiGeminiChatModel", "GoogleAiEmbeddingModel",
               "langchain4j-google-ai-gemini", "google-ai-gemini", "google-ai", "googleai"),
        VERTEX_AI("vertex-ai", "dev.langchain4j.model.vertexai", "VertexAiGeminiChatModel", "VertexAiEmbeddingModel",
                  "langchain4j-vertex-ai", "vertexai", "vertex-ai-gemini"),
        GITHUB("github", "dev.langchain4j.model.github", "GitHubModelsChatModel", "GitHubModelsEmbeddingModel",
               "langchain4j-github-models", "github-models"),
        HUGGING_FACE("hugging-face", "dev.langchain4j.model.huggingface", "HuggingFaceChatModel",
                     "HuggingFaceEmbeddingModel", "langchain4j-hugging-face", "huggingface"),
        BEDROCK("bedrock", "dev.langchain4j.model.bedrock", "BedrockChatModel", "BedrockTitanEmbeddingModel",
                "langchain4j-bedrock", "aws-bedrock", "amazon-bedrock");

        /** The provider names, for the enums of the provider option. */
        public static final String NAMES
                = "ollama,openai,anthropic,azure-openai,mistral,gemini,vertex-ai,github,hugging-face,bedrock";

        private final String id;
        private final String pkg;
        private final String chatModel;
        private final String embeddingModel;
        private final String artifactId;
        private final String[] aliases;

        Provider(String id, String pkg, String chatModel, String embeddingModel, String artifactId, String... aliases) {
            this.id = id;
            this.pkg = pkg;
            this.chatModel = chatModel;
            this.embeddingModel = embeddingModel;
            this.artifactId = artifactId;
            this.aliases = aliases;
        }

        public String id() {
            return id;
        }

        /** The Maven artifact of the LangChain4j module, in the dev.langchain4j group. */
        public String artifactId() {
            return artifactId;
        }

        public String chatModelClass() {
            return pkg + "." + chatModel;
        }

        /** The embedding model class, or null for a provider without embedding models. */
        public String embeddingModelClass() {
            return embeddingModel != null ? pkg + "." + embeddingModel : null;
        }

        /** The provider with the given name or alias, case-insensitively, or null. */
        public static Provider of(String name) {
            if (name == null) {
                return null;
            }
            String n = name.trim().toLowerCase(Locale.ROOT);
            for (Provider p : values()) {
                if (p.id.equals(n)) {
                    return p;
                }
                for (String alias : p.aliases) {
                    if (alias.equals(n)) {
                        return p;
                    }
                }
            }
            return null;
        }

        static String ids() {
            return NAMES.replace(",", ", ");
        }
    }

    /** The names the builders of the providers use for the common options, tried in order. */
    private static final Map<String, String[]> ALIASES = Map.of(
            "modelName", new String[] { "modelName", "modelId", "deploymentName", "model" },
            "baseUrl", new String[] { "baseUrl", "endpoint" },
            "apiKey", new String[] { "apiKey", "accessToken", "gitHubToken" },
            "temperature", new String[] { "temperature" },
            "timeout", new String[] { "timeout" });

    /** The prefix of the provider-specific model properties in an endpoint URI: {@code model.numPredict=512}. */
    public static final String MODEL_PROPERTY_PREFIX = "model.";

    private LangChain4jModelFactory() {
    }

    /**
     * Takes the {@code model.*} parameters out of the endpoint parameters and adds them, without the prefix, to the
     * model properties of the configuration, as a component does before setting the other parameters.
     *
     * @param  parameters      the endpoint parameters; the {@code model.*} ones are removed
     * @param  modelProperties the model properties configured so far (on the component), or null
     * @return                 the model properties to set on the configuration, or null when there are none
     */
    public static Map<String, Object> extractModelProperties(
            Map<String, Object> parameters, Map<String, Object> modelProperties) {
        Map<String, Object> extra = PropertiesHelper.extractProperties(parameters, MODEL_PROPERTY_PREFIX);
        if (extra.isEmpty()) {
            return modelProperties;
        }
        Map<String, Object> answer = new LinkedHashMap<>();
        if (modelProperties != null) {
            answer.putAll(modelProperties);
        }
        answer.putAll(extra);
        return answer;
    }

    /**
     * Creates the chat model of the provider.
     */
    public static ChatModel createChatModel(CamelContext camelContext, ModelSpec spec) {
        return create(camelContext, spec, ChatModel.class);
    }

    /**
     * Creates the embedding model of the provider.
     */
    public static EmbeddingModel createEmbeddingModel(CamelContext camelContext, ModelSpec spec) {
        return create(camelContext, spec, EmbeddingModel.class);
    }

    /**
     * The class of the model: the chat or embedding model class of the named provider, or the custom provider's class.
     *
     * @param provider       the provider name, or null
     * @param customProvider the fully qualified class name of the model, or null
     * @param kind           {@link ChatModel} or {@link EmbeddingModel}
     */
    public static String modelClassName(String provider, String customProvider, Class<?> kind) {
        if (customProvider != null && !customProvider.isBlank()) {
            if (provider != null && !provider.isBlank()) {
                throw new IllegalArgumentException(
                        "Set either provider (" + provider + ") or customProvider (" + customProvider + "), not both");
            }
            return customProvider.trim();
        }
        Provider p = Provider.of(provider);
        if (p == null) {
            String hint = provider != null && provider.contains(".")
                    ? ". A class name is set as customProvider, not as provider; provider is one of: " + Provider.ids()
                    : ". Use one of: " + Provider.ids() + ", or set customProvider to the fully qualified class name"
                      + " of a model class with a builder()";
            throw new IllegalArgumentException("Unknown LangChain4j provider: " + provider + hint);
        }
        String name = kind == EmbeddingModel.class ? p.embeddingModelClass() : p.chatModelClass();
        if (name == null) {
            throw new IllegalArgumentException(
                    "LangChain4j provider " + p.id() + " has no embedding model; use another provider for embeddings");
        }
        return name;
    }

    private static <T> T create(CamelContext camelContext, ModelSpec spec, Class<T> kind) {
        String className = modelClassName(spec.provider(), spec.customProvider(), kind);
        Provider provider = spec.customProvider() != null ? null : Provider.of(spec.provider());
        Class<?> clazz;
        try {
            clazz = camelContext.getClassResolver().resolveMandatoryClass(className);
        } catch (LinkageError e) {
            throw cannotInitialize(className, e);
        } catch (ClassNotFoundException e) {
            String dep = provider != null
                    ? "dev.langchain4j:" + provider.artifactId() : "the LangChain4j module of " + className;
            throw new IllegalArgumentException(
                    "LangChain4j provider " + spec.name() + " needs " + dep + " on the classpath (with Camel JBang:"
                                               + " camel.jbang.dependencies=" + dep + " in application.properties, or --dep)",
                    e);
        }
        if (!kind.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException(
                    "LangChain4j provider " + spec.name() + ": " + className + " is not a " + kind.getName());
        }
        Object builder = newBuilder(clazz);
        List<String> accepts = PropertyBindingSupport.builderPropertyNames(builder.getClass());

        Map<String, Object> properties = new LinkedHashMap<>();
        common(properties, accepts, clazz, spec.name(), "modelName", spec.modelName());
        common(properties, accepts, clazz, spec.name(), "baseUrl", spec.baseUrl());
        common(properties, accepts, clazz, spec.name(), "apiKey", spec.apiKey());
        common(properties, accepts, clazz, spec.name(), "temperature", spec.temperature());
        common(properties, accepts, clazz, spec.name(), "timeout", spec.timeout());
        if (spec.modelProperties() != null) {
            properties.putAll(spec.modelProperties());
        }
        LOG.debug("Creating {} of provider {} with: {}", className, spec.name(), properties.keySet());
        try {
            Object model = PropertyBindingSupport.build()
                    .withCamelContext(camelContext)
                    .withTarget(builder)
                    .withMandatory(true)
                    .withProperties(properties)
                    .build(Object.class, PropertyBindingSupport.findBuilderMethod(builder, clazz, null));
            return kind.cast(model);
        } catch (PropertyBindingException e) {
            throw new IllegalArgumentException(
                    "Cannot configure " + className + " of LangChain4j provider " + spec.name() + ": "
                                               + e.getMessage() + ". The builder " + builder.getClass().getName()
                                               + " accepts: " + String.join(", ", accepts),
                    e);
        }
    }

    /** Sets a common option under the name the builder has for it, failing when the builder has none. */
    private static void common(
            Map<String, Object> properties, List<String> accepts, Class<?> clazz, String provider, String option,
            Object value) {
        if (value == null) {
            return;
        }
        for (String alias : ALIASES.get(option)) {
            if (accepts.contains(alias)) {
                properties.put(alias, value);
                return;
            }
        }
        throw new IllegalArgumentException(
                "LangChain4j provider " + provider + " (" + clazz.getName() + ") has no " + option
                                           + " option; its builder accepts: " + String.join(", ", accepts));
    }

    /** The builder of the model class from its public static builder(), which every LangChain4j model has. */
    private static Object newBuilder(Class<?> clazz) {
        try {
            Method m = clazz.getMethod("builder");
            if (!Modifier.isStatic(m.getModifiers())) {
                throw new NoSuchMethodException("builder");
            }
            return m.invoke(null);
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException(
                    "Cannot create " + clazz.getName() + " as its builder() failed: " + e.getCause(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(
                    "Cannot create " + clazz.getName() + " as it has no public static builder() method", e);
        } catch (LinkageError e) {
            throw cannotInitialize(clazz.getName(), e);
        }
    }

    /**
     * The model class is there but cannot link or initialize (a NoClassDefFoundError, or the error its static
     * initializer threw: wrapped in an ExceptionInInitializerError when it is an exception, rethrown as-is when it is
     * an error such as UnsatisfiedLinkError), typically because a dependency of its LangChain4j module is missing;
     * resolving the model class itself does not detect that.
     */
    private static IllegalArgumentException cannotInitialize(String className, LinkageError e) {
        return new IllegalArgumentException(
                "Cannot initialize " + className + " (" + e + "); a dependency of its LangChain4j module may be"
                                            + " missing from the classpath",
                e);
    }
}
