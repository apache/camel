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
package org.apache.camel.component.langchain4j.embeddings;

import java.time.Duration;
import java.util.Map;

import dev.langchain4j.model.embedding.EmbeddingModel;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.langchain4j.core.LangChain4jModelFactory;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@Configurer
@UriParams
public class LangChain4jEmbeddingsConfiguration implements Cloneable {

    @Metadata(autowired = true)
    @UriParam
    private EmbeddingModel embeddingModel;

    @UriParam(label = "model", enums = LangChain4jModelFactory.Provider.NAMES)
    private String provider;
    @UriParam(label = "model,advanced")
    private String customProvider;
    @UriParam(label = "model")
    private String modelName;
    @UriParam(label = "model")
    private String baseUrl;
    @UriParam(label = "model,security", secret = true)
    private String apiKey;
    @UriParam(label = "model")
    private Double temperature;
    @UriParam(label = "model")
    private Duration timeout;
    @UriParam(label = "model,advanced", prefix = "model.", multiValue = true)
    private Map<String, Object> modelProperties;

    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }

    /**
     * The {@link EmbeddingModel} engine to use. Either this or a provider is required.
     */
    public void setEmbeddingModel(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getProvider() {
        return provider;
    }

    /**
     * The LangChain4j provider of the embedding model, to create the model from the options here (modelName, baseUrl,
     * apiKey, temperature, timeout, and provider-specific model.* properties) instead of a EmbeddingModel bean. The
     * LangChain4j module of the provider (dev.langchain4j:langchain4j-ollama, ...) must be on the classpath; Camel
     * JBang downloads it. Ignored when a EmbeddingModel is configured. For a provider not listed, set customProvider
     * instead.
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getCustomProvider() {
        return customProvider;
    }

    /**
     * The fully qualified class name of the LangChain4j model class of a provider that is not listed in provider
     * (dev.langchain4j.model.jlama.JlamaChatModel), created from the options here through its builder() as a listed
     * provider is. Set either provider or customProvider.
     */
    public void setCustomProvider(String customProvider) {
        this.customProvider = customProvider;
    }

    public String getModelName() {
        return modelName;
    }

    /**
     * The name of the model at the provider (qwen2.5, gpt-4o-mini, ...), when the model is created from the provider.
     */
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * The URL of the provider's API (http://localhost:11434 for a local Ollama), when the model is created from the
     * provider. The provider's default when not set.
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    /**
     * The API key or access token of the provider, when the model is created from the provider.
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Double getTemperature() {
        return temperature;
    }

    /**
     * The sampling temperature of the model, when the model is created from the provider.
     */
    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Duration getTimeout() {
        return timeout;
    }

    /**
     * The request timeout of the model (30s, 2m), when the model is created from the provider.
     */
    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Map<String, Object> getModelProperties() {
        return modelProperties;
    }

    /**
     * Provider-specific properties of the model, set on the model's builder as they are (model.numPredict=512 for
     * Ollama, model.maxTokens=1024 for OpenAI), when the model is created from the provider.
     */
    public void setModelProperties(Map<String, Object> modelProperties) {
        this.modelProperties = modelProperties;
    }

    /**
     * The model to create from the provider, or null when no provider is set.
     */
    public LangChain4jModelFactory.ModelSpec modelSpec() {
        if (provider == null && customProvider == null) {
            return null;
        }
        return new LangChain4jModelFactory.ModelSpec(
                provider, customProvider, modelName, baseUrl, apiKey, temperature, timeout,
                modelProperties != null ? Map.copyOf(modelProperties) : null);
    }

    public LangChain4jEmbeddingsConfiguration copy() {
        try {
            return (LangChain4jEmbeddingsConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
