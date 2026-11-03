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

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.ai.content.Media;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class SpringAiEmbeddingsComponentTest extends CamelTestSupport {

    private final List<List<String>> requests = new CopyOnWriteArrayList<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        SpringAiEmbeddingsComponent component
                = context.getComponent(SpringAiEmbeddings.SCHEME, SpringAiEmbeddingsComponent.class);

        // Use a simple mock embedding model for testing
        EmbeddingModel embeddingModel = new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                requests.add(request.getInstructions());
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

    @Test
    public void testListOfOneTextGivesList() {
        Message result = fluentTemplate.to("spring-ai-embeddings:one")
                .withBody(List.of("hello"))
                .request(Message.class);

        assertThat(result.getBody()).isInstanceOf(List.class);
        List<float[]> vectors = result.getBody(List.class);
        assertThat(vectors).hasSize(1);
        assertThat(vectors.get(0)).containsExactly(0.1f, 0.2f, 0.3f, 0.4f, 0.5f);

        assertThat(result.getHeader(SpringAiEmbeddingsHeaders.INPUT_TEXTS, List.class)).containsExactly("hello");
        assertThat(result.getHeader(SpringAiEmbeddingsHeaders.EMBEDDINGS, List.class)).hasSize(1);
        assertThat(result.getHeader(SpringAiEmbeddingsHeaders.INPUT_TEXT)).isNull();
    }

    @Test
    public void testListOfTextCarriersIsConvertedToText() {
        Message result = fluentTemplate.to("spring-ai-embeddings:convert")
                .withBody(List.of(new StringBuilder("hello"), "bytes".getBytes(StandardCharsets.UTF_8),
                        new Document("document text")))
                .request(Message.class);

        assertThat(requests).containsExactly(List.of("hello", "bytes", "document text"));
        List<float[]> vectors = result.getBody(List.class);
        assertThat(vectors).hasSize(3);
        assertThat(result.getHeader(SpringAiEmbeddingsHeaders.INPUT_TEXTS, List.class))
                .containsExactly("hello", "bytes", "document text");
    }

    @Test
    public void testDocumentBodyEmbedsItsText() {
        Message result = fluentTemplate.to("spring-ai-embeddings:document")
                .withBody(new Document("document text", Map.of("source", "test")))
                .request(Message.class);

        assertThat(requests).containsExactly(List.of("document text"));
        assertThat(result.getBody(float[].class)).containsExactly(0.1f, 0.2f, 0.3f, 0.4f, 0.5f);
        assertThat(result.getHeader(SpringAiEmbeddingsHeaders.INPUT_TEXT)).isEqualTo("document text");
    }

    @Test
    public void testDocumentWithoutTextFails() {
        Document media = new Document(
                new Media(MimeTypeUtils.IMAGE_PNG, URI.create("https://example.com/image.png")),
                Map.of());
        Exchange exchange = template.send("spring-ai-embeddings:media", e -> e.getMessage().setBody(media));

        assertThat(exchange.getException())
                .isInstanceOf(InvalidPayloadException.class)
                .hasMessageContaining("the Document has no text");
        assertThat(requests).isEmpty();
    }

    @Test
    public void testStreamBodyIsReadAsText() {
        Message result = fluentTemplate.to("spring-ai-embeddings:stream")
                .withBody(new ByteArrayInputStream("streamed text".getBytes(StandardCharsets.UTF_8)))
                .request(Message.class);

        assertThat(requests).containsExactly(List.of("streamed text"));
        assertThat(result.getHeader(SpringAiEmbeddingsHeaders.INPUT_TEXT)).isEqualTo("streamed text");
    }

    @Test
    public void testMapBodyFails() {
        Exchange exchange = template.send("spring-ai-embeddings:map",
                e -> e.getMessage().setBody(Map.of("a", 1)));

        assertThat(exchange.getException())
                .isInstanceOf(InvalidPayloadException.class)
                .hasMessageContaining("convert the body to text before the endpoint");
        assertThat(requests).isEmpty();
    }

    @Test
    public void testPojoBodyFails() {
        Exchange exchange = template.send("spring-ai-embeddings:pojo",
                e -> e.getMessage().setBody(new Pojo("hello")));

        assertThat(exchange.getException())
                .isInstanceOf(InvalidPayloadException.class)
                .hasMessageContaining("convert the body to text before the endpoint");
        assertThat(requests).isEmpty();
    }

    @Test
    public void testListWithMapElementFails() {
        Exchange exchange = template.send("spring-ai-embeddings:listmap",
                e -> e.getMessage().setBody(List.of("hello", Map.of("a", 1))));

        assertThat(exchange.getException())
                .isInstanceOf(InvalidPayloadException.class)
                .hasMessageContaining("the element at index 1 of the List body is java.util.")
                .hasMessageContaining("convert the body to text before the endpoint");
        assertThat(requests).isEmpty();
    }

    @Test
    public void testListWithNumberElementFails() {
        Exchange exchange = template.send("spring-ai-embeddings:listnumber",
                e -> e.getMessage().setBody(List.of("hello", 42)));

        assertThat(exchange.getException())
                .isInstanceOf(InvalidPayloadException.class)
                .hasMessageContaining("the element at index 1 of the List body is java.lang.Integer");
        assertThat(requests).isEmpty();
    }

    @Test
    public void testListWithNullElementFails() {
        Exchange exchange
                = template.send("spring-ai-embeddings:null", e -> e.getMessage().setBody(Arrays.asList("hello", null)));

        assertThat(exchange.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Element 1 of the List body");
        assertThat(requests).isEmpty();
    }

    @Test
    public void testEmptyListGivesEmptyList() {
        Message result = fluentTemplate.to("spring-ai-embeddings:empty")
                .withBody(new ArrayList<String>())
                .request(Message.class);

        assertThat(result.getBody()).isInstanceOf(List.class);
        assertThat(result.getBody(List.class)).isEmpty();
        assertThat(result.getHeader(SpringAiEmbeddingsHeaders.EMBEDDINGS, List.class)).isEmpty();
        assertThat(result.getHeader(SpringAiEmbeddingsHeaders.INPUT_TEXTS, List.class)).isEmpty();
        assertThat(requests).isEmpty();
    }

    private static final class Pojo {
        private final String text;

        Pojo(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return "Pojo[text=" + text + "]";
        }
    }
}
