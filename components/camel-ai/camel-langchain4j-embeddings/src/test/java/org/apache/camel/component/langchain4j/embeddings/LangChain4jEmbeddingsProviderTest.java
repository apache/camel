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

import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24820: the embedding model is declared by its provider and options, without an EmbeddingModel bean.
 */
public class LangChain4jEmbeddingsProviderTest extends CamelTestSupport {

    @Test
    void modelFromProviderOptions() {
        LangChain4jEmbeddingsEndpoint endpoint = context.getEndpoint(
                "langchain4j-embeddings:test?provider=openai&apiKey=demo&modelName=text-embedding-3-small",
                LangChain4jEmbeddingsEndpoint.class);
        assertThat(endpoint.getConfiguration().getEmbeddingModel()).isInstanceOf(OpenAiEmbeddingModel.class);
    }

    @Test
    void modelFromComponentOptionsIsSharedByEndpoints() {
        LangChain4jEmbeddingsComponent component
                = context.getComponent("langchain4j-embeddings", LangChain4jEmbeddingsComponent.class);
        component.getConfiguration().setProvider("openai");
        component.getConfiguration().setApiKey("demo");
        component.getConfiguration().setModelName("text-embedding-3-small");

        LangChain4jEmbeddingsEndpoint one
                = context.getEndpoint("langchain4j-embeddings:one", LangChain4jEmbeddingsEndpoint.class);
        LangChain4jEmbeddingsEndpoint two
                = context.getEndpoint("langchain4j-embeddings:two", LangChain4jEmbeddingsEndpoint.class);
        assertThat(one.getConfiguration().getEmbeddingModel()).isInstanceOf(OpenAiEmbeddingModel.class)
                .isSameAs(two.getConfiguration().getEmbeddingModel());
    }
}
