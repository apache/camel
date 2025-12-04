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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Integration test for Spring AI Embeddings component using Ollama.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Disabled unless running in CI")
public class SpringAiEmbeddingsIT extends OllamaTestSupport {

    @Test
    public void testSimpleEmbeddingWithOllama() {
        var exchange = template.request("direct:embeddings", e -> {
            e.getIn().setBody("The quick brown fox jumps over the lazy dog");
        });

        float[] embeddingVector = exchange.getMessage().getBody(float[].class);

        assertThat(embeddingVector).isNotNull();
        assertThat(embeddingVector).isNotEmpty();
        assertThat(embeddingVector.length).isGreaterThan(0);
    }

    @Test
    public void testEmbeddingWithInputTextHeader() {
        var exchange = template.request("direct:embeddings", e -> {
            e.getIn().setBody("Hello world");
        });

        String inputText = exchange.getMessage().getHeader(SpringAiEmbeddingsHeaders.INPUT_TEXT, String.class);
        assertThat(inputText).isEqualTo("Hello world");

        float[] embeddingVector = exchange.getMessage().getBody(float[].class);
        assertThat(embeddingVector).isNotNull();
    }

    @Test
    public void testMultipleEmbeddingRequests() {
        var exchange1 = template.request("direct:embeddings", e -> {
            e.getIn().setBody("cat");
        });

        var exchange2 = template.request("direct:embeddings", e -> {
            e.getIn().setBody("dog");
        });

        float[] embedding1 = exchange1.getMessage().getBody(float[].class);
        float[] embedding2 = exchange2.getMessage().getBody(float[].class);

        assertThat(embedding1).isNotNull();
        assertThat(embedding2).isNotNull();

        // Both embeddings should have the same dimensionality
        assertThat(embedding1.length).isEqualTo(embedding2.length);

        // The embeddings should be different (not identical vectors)
        assertThat(embedding1).isNotEqualTo(embedding2);
    }

    @Test
    public void testEmbeddingMetadata() {
        var exchange = template.request("direct:embeddings", e -> {
            e.getIn().setBody("test embedding");
        });

        Integer embeddingIndex =
                exchange.getMessage().getHeader(SpringAiEmbeddingsHeaders.EMBEDDING_INDEX, Integer.class);
        assertThat(embeddingIndex).isNotNull();
        assertThat(embeddingIndex).isEqualTo(0);
    }

    @Test
    public void testBatchEmbeddings() {
        List<String> texts = List.of("cat", "dog", "bird");

        var exchange = template.request("direct:embeddings", e -> {
            e.getIn().setBody(texts);
        });

        List<float[]> vectors = exchange.getMessage().getBody(List.class);
        assertThat(vectors).isNotNull();
        assertThat(vectors).hasSize(3);

        List<String> inputTexts = exchange.getMessage().getHeader(SpringAiEmbeddingsHeaders.INPUT_TEXTS, List.class);
        assertThat(inputTexts).isEqualTo(texts);

        // Verify all vectors have the same dimensionality
        assertThat(vectors.get(0).length).isEqualTo(vectors.get(1).length);
        assertThat(vectors.get(0).length).isEqualTo(vectors.get(2).length);

        // Verify vectors are not identical
        assertThat(vectors.get(0)).isNotEqualTo(vectors.get(1));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                this.getCamelContext().getRegistry().bind("embeddingModel", embeddingModel);

                from("direct:embeddings").to("spring-ai-embeddings:test?embeddingModel=#embeddingModel");
            }
        };
    }
}
