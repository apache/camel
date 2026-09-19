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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.langchain4j.model.embedding.EmbeddingModel;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.langchain4j.core.LangChain4jModelFactory;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component(LangChain4jEmbeddings.SCHEME)
public class LangChain4jEmbeddingsComponent extends DefaultComponent {
    @Metadata
    private LangChain4jEmbeddingsConfiguration configuration;

    private final Map<LangChain4jModelFactory.ModelSpec, EmbeddingModel> models = new ConcurrentHashMap<>();

    public LangChain4jEmbeddingsComponent() {
        this(null);
    }

    public LangChain4jEmbeddingsComponent(CamelContext context) {
        super(context);

        this.configuration = new LangChain4jEmbeddingsConfiguration();
    }

    public LangChain4jEmbeddingsConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The configuration.
     */
    public void setConfiguration(LangChain4jEmbeddingsConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        LangChain4jEmbeddingsConfiguration configuration = this.configuration.copy();

        LangChain4jEmbeddingsEndpoint endpoint = new LangChain4jEmbeddingsEndpoint(uri, this, remaining, configuration);
        configuration.setModelProperties(
                LangChain4jModelFactory.extractModelProperties(parameters, configuration.getModelProperties()));
        setProperties(endpoint, parameters);
        if (configuration.getEmbeddingModel() == null && configuration.modelSpec() != null) {
            // the model is declared by its provider and options (CAMEL-24820); endpoints with the same options share it
            LangChain4jModelFactory.ModelSpec spec = configuration.modelSpec();
            configuration.setEmbeddingModel(
                    models.computeIfAbsent(spec, s -> LangChain4jModelFactory.createEmbeddingModel(getCamelContext(), s)));
        }

        return endpoint;
    }
}
