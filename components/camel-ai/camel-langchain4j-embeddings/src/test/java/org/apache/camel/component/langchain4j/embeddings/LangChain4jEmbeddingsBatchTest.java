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

import java.util.Arrays;
import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LangChain4jEmbeddingsBatchTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        LangChain4jEmbeddingsComponent component
                = context.getComponent(LangChain4jEmbeddings.SCHEME, LangChain4jEmbeddingsComponent.class);

        component.getConfiguration().setEmbeddingModel(new AllMiniLmL6V2EmbeddingModel());

        return context;
    }

    @Test
    @DisplayName("Batch embedAll with List<String> body produces List<Embedding> result")
    void batchEmbedAllWithStringList() {
        List<String> texts = Arrays.asList("hello", "world", "test");

        Message result = fluentTemplate.to("langchain4j-embeddings:batch")
                .withBody(texts)
                .request(Message.class);

        @SuppressWarnings("unchecked")
        List<Embedding> embeddings = result.getHeader(LangChain4jEmbeddingsHeaders.EMBEDDINGS, List.class);
        assertThat(embeddings).hasSize(3);
        assertThat(embeddings.get(0).vector()).hasSize(384);
        assertThat(embeddings.get(1).vector()).hasSize(384);
        assertThat(embeddings.get(2).vector()).hasSize(384);

        // Body should also be set to the list of embeddings
        assertThat(result.getBody()).isInstanceOf(List.class);
    }

    @Test
    @DisplayName("Single string body still works as before")
    void singleStringBodyStillWorks() {
        Message result = fluentTemplate.to("langchain4j-embeddings:single")
                .withBody("hello")
                .request(Message.class);

        Embedding embedding = result.getHeader(LangChain4jEmbeddingsHeaders.EMBEDDING, Embedding.class);
        assertThat(embedding).isNotNull();
        assertThat(embedding.vector()).hasSize(384);

        // EMBEDDINGS header should NOT be set for single operation
        assertThat(result.getHeader(LangChain4jEmbeddingsHeaders.EMBEDDINGS)).isNull();
    }

    @Test
    @DisplayName("Batch embedAll preserves token usage headers")
    void batchEmbedAllPreservesTokenUsage() {
        List<String> texts = Arrays.asList("hi", "bye");

        Message result = fluentTemplate.to("langchain4j-embeddings:batch-tokens")
                .withBody(texts)
                .request(Message.class);

        @SuppressWarnings("unchecked")
        List<Embedding> embeddings = result.getHeader(LangChain4jEmbeddingsHeaders.EMBEDDINGS, List.class);
        assertThat(embeddings).hasSize(2);

        // Token usage may or may not be provided by the model, but headers should be set if available
        // AllMiniLmL6V2 provides token usage, so verify it's there
        Integer inputTokens = result.getHeader(LangChain4jEmbeddingsHeaders.INPUT_TOKEN_COUNT, Integer.class);
        assertThat(inputTokens).isNotNull();
    }
}
