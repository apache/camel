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
package org.apache.camel.component.springai.embeddings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;

import static org.assertj.core.api.Assertions.assertThat;

public class SpringAiEmbeddingsComponentTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        SpringAiEmbeddingsComponent component
                = context.getComponent(SpringAiEmbeddings.SCHEME, SpringAiEmbeddingsComponent.class);

        // Use a simple mock embedding model for testing
        EmbeddingModel embeddingModel = new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                List<Embedding> embeddings = new ArrayList<>();
                int index = 0;
                for (String text : request.getInstructions()) {
                    float[] vector = new float[] { 0.1f, 0.2f, 0.3f, 0.4f, 0.5f };
                    embeddings.add(new Embedding(vector, index++));
                }
                return new EmbeddingResponse(embeddings, new EmbeddingResponseMetadata());
            }

            @Override
            public float[] embed(Document document) {
                return new float[] { 0.1f, 0.2f, 0.3f, 0.4f, 0.5f };
            }

            @Override
            public List<float[]> embed(List<String> texts) {
                List<float[]> results = new ArrayList<>();
                for (int i = 0; i < texts.size(); i++) {
                    results.add(new float[] { 0.1f, 0.2f, 0.3f, 0.4f, 0.5f });
                }
                return results;
            }

            @Override
            public int dimensions() {
                return 5;
            }
        };

        component.getConfiguration().setEmbeddingModel(embeddingModel);

        return context;
    }

    @Test
    public void testSimpleEmbedding() {

        Message first = fluentTemplate.to("spring-ai-embeddings:first")
                .withBody("hi")
                .request(Message.class);

        float[] firstEmbedding = first.getBody(float[].class);
        assertThat(firstEmbedding).isNotNull();
        assertThat(firstEmbedding).hasSize(5);
        assertThat(firstEmbedding).containsExactly(0.1f, 0.2f, 0.3f, 0.4f, 0.5f);

        String firstInputText = first.getHeader(SpringAiEmbeddingsHeaders.INPUT_TEXT, String.class);
        assertThat(firstInputText).isEqualTo("hi");

        Integer embeddingIndex = first.getHeader(SpringAiEmbeddingsHeaders.EMBEDDING_INDEX, Integer.class);
        assertThat(embeddingIndex).isEqualTo(0);

        Message second = fluentTemplate.to("spring-ai-embeddings:second")
                .withBody("hello")
                .request(Message.class);

        float[] secondEmbedding = second.getBody(float[].class);
        ;
        assertThat(secondEmbedding).isNotNull();
        assertThat(secondEmbedding).hasSize(5);
        assertThat(secondEmbedding).containsExactly(0.1f, 0.2f, 0.3f, 0.4f, 0.5f);

        String secondInputText = second.getHeader(SpringAiEmbeddingsHeaders.INPUT_TEXT, String.class);
        assertThat(secondInputText).isEqualTo("hello");

        // Verify both embeddings have the same dimensionality
        assertThat(firstEmbedding).hasSameSizeAs(secondEmbedding);
    }

    @Test
    public void testBatchEmbedding() {
        List<String> texts = Arrays.asList("hello", "world", "test");

        Message result = fluentTemplate.to("spring-ai-embeddings:batch")
                .withBody(texts)
                .request(Message.class);

        List<float[]> vectors = result.getBody(List.class);
        assertThat(vectors).isNotNull();
        assertThat(vectors).hasSize(3);

        List<String> inputTexts = result.getHeader(SpringAiEmbeddingsHeaders.INPUT_TEXTS, List.class);
        assertThat(inputTexts).isEqualTo(texts);

        // Verify all vectors have the same dimensionality
        assertThat(vectors.get(0)).hasSameSizeAs(vectors.get(1));
        assertThat(vectors.get(0)).hasSameSizeAs(vectors.get(2));
    }
}
