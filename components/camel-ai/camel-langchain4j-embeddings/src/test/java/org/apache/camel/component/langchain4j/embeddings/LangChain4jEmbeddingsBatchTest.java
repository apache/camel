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
import dev.langchain4j.data.segment.TextSegment;
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
    @DisplayName("Batch embedding with List<String> body produces EMBEDDINGS and TEXT_SEGMENTS headers")
    @SuppressWarnings("unchecked")
    void batchEmbeddingFromStringList() {
        List<String> texts = Arrays.asList("hello world", "goodbye world");

        Message result = fluentTemplate.to("langchain4j-embeddings:batch")
                .withBody(texts)
                .request(Message.class);

        List<Embedding> embeddings = result.getHeader(LangChain4jEmbeddingsHeaders.EMBEDDINGS, List.class);
        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(0).vector()).hasSize(384);
        assertThat(embeddings.get(1).vector()).hasSize(384);

        List<TextSegment> segments = result.getHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENTS, List.class);
        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).text()).isEqualTo("hello world");
        assertThat(segments.get(1).text()).isEqualTo("goodbye world");
    }

    @Test
    @DisplayName("Batch embedding sets token usage headers")
    void batchEmbeddingTokenUsage() {
        List<String> texts = Arrays.asList("test one", "test two");

        Message result = fluentTemplate.to("langchain4j-embeddings:batch")
                .withBody(texts)
                .request(Message.class);

        // Token usage should be reported for batch operations
        assertThat(result.getHeader(LangChain4jEmbeddingsHeaders.EMBEDDINGS)).isNotNull();
        assertThat(result.getHeader(LangChain4jEmbeddingsHeaders.INPUT_TOKEN_COUNT, Integer.class)).isNotNull().isPositive();
        assertThat(result.getHeader(LangChain4jEmbeddingsHeaders.TOTAL_TOKEN_COUNT, Integer.class)).isNotNull().isPositive();
    }

    @Test
    @DisplayName("Single item still uses single path")
    void singleItemStillUsesSinglePath() {
        Message result = fluentTemplate.to("langchain4j-embeddings:single")
                .withBody("hello")
                .request(Message.class);

        // Single path should set EMBEDDING header (not EMBEDDINGS)
        assertThat(result.getHeader(LangChain4jEmbeddingsHeaders.EMBEDDING, Embedding.class)).isNotNull();
        assertThat(result.getHeader(LangChain4jEmbeddingsHeaders.EMBEDDINGS)).isNull();
    }
}
