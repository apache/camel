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
package org.apache.camel.component.springai.vectorstore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Message;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.EmbeddedDocument;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UPSERT and SIMILARITY_SEARCH with searchByVector write and search with the vectors of spring-ai-embeddings, without
 * the embedding model of the vector store.
 */
public class SpringAiVectorStoreUpsertTest extends CamelTestSupport {

    private static final String UPSERT = "spring-ai-vector-store:test?operation=UPSERT";
    private static final String SEARCH_BY_VECTOR
            = "spring-ai-vector-store:test?operation=SIMILARITY_SEARCH&searchByVector=true";

    private static final float[] CAT = { 1f, 0f, 0f };
    private static final float[] KITTEN = { 0.9f, 0.1f, 0f };
    private static final float[] CAR = { 0f, 0f, 1f };

    private InMemoryVectorStore vectorStore;
    private SimpleVectorStore simpleVectorStore;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        vectorStore = new InMemoryVectorStore();
        context.getComponent(SpringAiVectorStore.SCHEME, SpringAiVectorStoreComponent.class)
                .getConfiguration().setVectorStore(vectorStore);

        // SimpleVectorStore supports neither UPSERT nor the search with a vector
        simpleVectorStore = SimpleVectorStore.builder(new FixedEmbeddingModel()).build();
        context.getRegistry().bind("simpleVectorStore", simpleVectorStore);

        return context;
    }

    @Test
    public void testUpsertVector() {
        float[] vector = { 0.1f, 0.2f, 0.3f };

        Message result = fluentTemplate.to(UPSERT)
                .withBody(vector)
                .withHeader("CamelSpringAiEmbeddingInputText", "Hello world")
                .withHeader(SpringAiVectorStoreHeaders.UPSERT_DOCUMENT_ID, "doc-1")
                .request(Message.class);

        assertThat(result.getHeader(SpringAiVectorStoreHeaders.DOCUMENTS_ADDED, Integer.class)).isEqualTo(1);
        assertThat(result.getHeader(SpringAiVectorStoreHeaders.DOCUMENT_IDS, List.class)).containsExactly("doc-1");
        assertThat(result.getBody()).isSameAs(vector);

        EmbeddedDocument stored = vectorStore.entries.get("doc-1");
        assertThat(stored.embedding()).containsExactly(vector);
        assertThat(stored.document().getText()).isEqualTo("Hello world");
        // UPSERT does not write the embedding and precomputed_embedding metadata of ADD
        assertThat(stored.document().getMetadata()).isEmpty();
    }

    @Test
    public void testUpsertVectorWithoutIdGetsRandomUuid() {
        Message result = fluentTemplate.to(UPSERT)
                .withBody(CAT)
                .withHeader("CamelSpringAiEmbeddingInputText", "A cat")
                .request(Message.class);

        List<String> ids = result.getHeader(SpringAiVectorStoreHeaders.DOCUMENT_IDS, List.class);
        assertThat(ids).hasSize(1);
        assertThat(UUID.fromString(ids.get(0))).isNotNull();
        assertThat(vectorStore.entries).containsOnlyKeys(ids.get(0));
    }

    @Test
    public void testUpsertReplacesDocumentWithSameId() {
        fluentTemplate.to(UPSERT)
                .withBody(CAT)
                .withHeader("CamelSpringAiEmbeddingInputText", "Old text")
                .withHeader(SpringAiVectorStoreHeaders.UPSERT_DOCUMENT_ID, "doc-1")
                .send();
        fluentTemplate.to(UPSERT)
                .withBody(CAR)
                .withHeader("CamelSpringAiEmbeddingInputText", "New text")
                .withHeader(SpringAiVectorStoreHeaders.UPSERT_DOCUMENT_ID, "doc-1")
                .send();

        assertThat(vectorStore.entries).containsOnlyKeys("doc-1");
        assertThat(vectorStore.entries.get("doc-1").document().getText()).isEqualTo("New text");
        assertThat(vectorStore.entries.get("doc-1").embedding()).containsExactly(CAR);
    }

    @Test
    public void testUpsertVectors() {
        Message result = fluentTemplate.to(UPSERT)
                .withBody(List.of(CAT, CAR))
                .withHeader("CamelSpringAiEmbeddingInputTexts", List.of("A cat", "A car"))
                .withHeader(SpringAiVectorStoreHeaders.UPSERT_DOCUMENT_IDS, List.of("doc-cat", "doc-car"))
                .request(Message.class);

        assertThat(result.getHeader(SpringAiVectorStoreHeaders.DOCUMENTS_ADDED, Integer.class)).isEqualTo(2);
        assertThat(result.getHeader(SpringAiVectorStoreHeaders.DOCUMENT_IDS, List.class))
                .containsExactly("doc-cat", "doc-car");
        assertThat(vectorStore.entries.get("doc-cat").document().getText()).isEqualTo("A cat");
        assertThat(vectorStore.entries.get("doc-cat").embedding()).containsExactly(CAT);
        assertThat(vectorStore.entries.get("doc-car").document().getText()).isEqualTo("A car");
        assertThat(vectorStore.entries.get("doc-car").embedding()).containsExactly(CAR);
    }

    @Test
    public void testUpsertVectorsWithoutIdsGetRandomUuids() {
        Message result = fluentTemplate.to(UPSERT)
                .withBody(List.of(CAT, CAR))
                .withHeader("CamelSpringAiEmbeddingInputTexts", List.of("A cat", "A car"))
                .request(Message.class);

        List<String> ids = result.getHeader(SpringAiVectorStoreHeaders.DOCUMENT_IDS, List.class);
        assertThat(ids).hasSize(2).doesNotHaveDuplicates();
        ids.forEach(id -> assertThat(UUID.fromString(id)).isNotNull());
        assertThat(vectorStore.entries.keySet()).containsExactlyElementsOf(ids);
    }

    @Test
    public void testUpsertDoesNotReadDocumentIdsHeader() {
        // ADD, UPSERT and SIMILARITY_SEARCH set the DocumentIds header to their output ids: a later UPSERT must not
        // replace these documents
        Message result = fluentTemplate.to(UPSERT)
                .withBody(List.of(CAT, CAR))
                .withHeader("CamelSpringAiEmbeddingInputTexts", List.of("A cat", "A car"))
                .withHeader(SpringAiVectorStoreHeaders.DOCUMENT_IDS, List.of("found-1", "found-2"))
                .request(Message.class);

        assertThat(result.getHeader(SpringAiVectorStoreHeaders.DOCUMENT_IDS, List.class))
                .doesNotContain("found-1", "found-2");
        assertThat(vectorStore.entries).doesNotContainKeys("found-1", "found-2");
    }

    @Test
    public void testUpsertVectorsWithMismatchedIdsFails() {
        assertThatThrownBy(() -> fluentTemplate.to(UPSERT)
                .withBody(List.of(CAT, CAR))
                .withHeader("CamelSpringAiEmbeddingInputTexts", List.of("A cat", "A car"))
                .withHeader(SpringAiVectorStoreHeaders.UPSERT_DOCUMENT_IDS, List.of("doc-cat"))
                .request())
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SpringAiVectorStoreHeaders.UPSERT_DOCUMENT_IDS)
                .hasMessageContaining("1 ids for 2 embeddings");

        assertThat(vectorStore.entries).isEmpty();
    }

    @Test
    public void testUpsertVectorWithoutInputTextFails() {
        assertThatThrownBy(() -> fluentTemplate.to(UPSERT)
                .withBody(CAT)
                .request())
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CamelSpringAiEmbeddingInputText");

        assertThat(vectorStore.entries).isEmpty();
    }

    @Test
    public void testUpsertVectorsWithMismatchedInputTextsFails() {
        assertThatThrownBy(() -> fluentTemplate.to(UPSERT)
                .withBody(List.of(CAT, CAR))
                .withHeader("CamelSpringAiEmbeddingInputTexts", List.of("Only one text"))
                .request())
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CamelSpringAiEmbeddingInputTexts")
                .hasMessageContaining("1 texts for 2 embeddings");

        assertThat(vectorStore.entries).isEmpty();
    }

    @Test
    public void testUpsertEmbeddedDocument() {
        Document document = new Document("doc-1", "A cat", Map.of("source", "test"));

        Message result = fluentTemplate.to(UPSERT)
                .withBody(new EmbeddedDocument(document, CAT))
                // the id headers go with a vector body only
                .withHeader(SpringAiVectorStoreHeaders.UPSERT_DOCUMENT_ID, "other-id")
                .request(Message.class);

        assertThat(result.getHeader(SpringAiVectorStoreHeaders.DOCUMENT_IDS, List.class)).containsExactly("doc-1");
        assertThat(vectorStore.entries).containsOnlyKeys("doc-1");
        assertThat(vectorStore.entries.get("doc-1").document().getMetadata()).containsEntry("source", "test");
        assertThat(vectorStore.entries.get("doc-1").embedding()).containsExactly(CAT);
    }

    @Test
    public void testUpsertListOfEmbeddedDocuments() {
        Message result = fluentTemplate.to(UPSERT)
                .withBody(List.of(
                        new EmbeddedDocument(new Document("doc-cat", "A cat", Map.of()), CAT),
                        new EmbeddedDocument(new Document("doc-car", "A car", Map.of()), CAR)))
                .request(Message.class);

        assertThat(result.getHeader(SpringAiVectorStoreHeaders.DOCUMENTS_ADDED, Integer.class)).isEqualTo(2);
        assertThat(result.getHeader(SpringAiVectorStoreHeaders.DOCUMENT_IDS, List.class))
                .containsExactly("doc-cat", "doc-car");
        assertThat(vectorStore.entries).containsOnlyKeys("doc-cat", "doc-car");
    }

    @Test
    public void testUpsertEmptyListWritesNothing() {
        // spring-ai-embeddings of an empty List gives an empty List
        Message result = fluentTemplate.to(UPSERT + "&vectorStore=#simpleVectorStore")
                .withBody(new ArrayList<float[]>())
                .withHeader("CamelSpringAiEmbeddingInputTexts", List.of())
                .request(Message.class);

        assertThat(result.getHeader(SpringAiVectorStoreHeaders.DOCUMENTS_ADDED, Integer.class)).isZero();
        assertThat(result.getHeader(SpringAiVectorStoreHeaders.DOCUMENT_IDS, List.class)).isEmpty();
    }

    @Test
    public void testUpsertListOfTextsFails() {
        assertThatThrownBy(() -> fluentTemplate.to(UPSERT)
                .withBody(List.of("A text"))
                .request())
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a List of float[] or of EmbeddedDocument")
                .hasMessageContaining("java.lang.String");
    }

    @Test
    public void testUpsertTextFails() {
        assertThatThrownBy(() -> fluentTemplate.to(UPSERT)
                .withBody("A text")
                .request())
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UPSERT takes a float[]")
                .hasMessageContaining("java.lang.String");
    }

    @Test
    public void testUpsertToVectorStoreWithoutUpsertFails() {
        assertThatThrownBy(() -> fluentTemplate.to(UPSERT + "&vectorStore=#simpleVectorStore")
                .withBody(CAT)
                .withHeader("CamelSpringAiEmbeddingInputText", "A cat")
                .request())
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("SimpleVectorStore does not support UPSERT")
                .hasMessageContaining("Use ADD")
                .cause()
                .isInstanceOf(UnsupportedOperationException.class);

        assertThat(simpleVectorStore.similaritySearch(SearchRequest.builder().query("any").topK(100).build()))
                .isEmpty();
    }

    @Test
    public void testSearchByVector() {
        upsert("doc-cat", "A cat", CAT);
        upsert("doc-kitten", "A kitten", KITTEN);
        upsert("doc-car", "A car", CAR);

        Message result = fluentTemplate.to(SEARCH_BY_VECTOR + "&topK=2")
                .withBody(new float[] { 1f, 0.05f, 0f })
                .request(Message.class);

        List<Document> found = result.getBody(List.class);
        assertThat(found).extracting(Document::getId).containsExactly("doc-cat", "doc-kitten");
        assertThat(result.getHeader(SpringAiVectorStoreHeaders.SIMILAR_DOCUMENTS, List.class)).isEqualTo(found);
        assertThat(result.getHeader(SpringAiVectorStoreHeaders.DOCUMENT_IDS, List.class))
                .containsExactly("doc-cat", "doc-kitten");
    }

    @Test
    public void testSearchByVectorIgnoresInputTextHeader() {
        upsert("doc-cat", "A cat", CAT);
        upsert("doc-car", "A car", CAR);

        // the vector store would embed the text with its embedding model, which fails
        Message result = fluentTemplate.to(SEARCH_BY_VECTOR + "&topK=1")
                .withBody(CAR)
                .withHeader("CamelSpringAiEmbeddingInputText", "A cat")
                .request(Message.class);

        assertThat(result.getHeader(SpringAiVectorStoreHeaders.DOCUMENT_IDS, List.class)).containsExactly("doc-car");
    }

    @Test
    public void testSearchByVectorWithSearchParameters() {
        upsert("doc-cat", "A cat", CAT);
        upsert("doc-kitten", "A kitten", KITTEN);
        upsert("doc-car", "A car", CAR);

        Message result = fluentTemplate.to(SEARCH_BY_VECTOR + "&topK=5")
                .withBody(CAT)
                .withHeader(SpringAiVectorStoreHeaders.TOP_K, 3)
                .withHeader(SpringAiVectorStoreHeaders.SIMILARITY_THRESHOLD, 0.5)
                .withHeader(SpringAiVectorStoreHeaders.FILTER_EXPRESSION, "source == 'test'")
                .request(Message.class);

        assertThat(result.getHeader(SpringAiVectorStoreHeaders.DOCUMENT_IDS, List.class))
                .containsExactly("doc-cat", "doc-kitten");
        assertThat(vectorStore.lastSearchRequest.getTopK()).isEqualTo(3);
        assertThat(vectorStore.lastSearchRequest.getSimilarityThreshold()).isEqualTo(0.5);
        assertThat(vectorStore.lastSearchRequest.getFilterExpression()).isNotNull();
    }

    @Test
    public void testSearchByVectorOfTextSearchesText() {
        simpleVectorStore.add(List.of(new Document("doc-1", "A cat", Map.of())));

        Message result = fluentTemplate.to(SEARCH_BY_VECTOR + "&vectorStore=#simpleVectorStore")
                .withBody("cat")
                .request(Message.class);

        assertThat(result.getHeader(SpringAiVectorStoreHeaders.DOCUMENT_IDS, List.class)).containsExactly("doc-1");
    }

    @Test
    public void testSearchByVectorOnVectorStoreWithoutIt() {
        assertThatThrownBy(() -> fluentTemplate.to(SEARCH_BY_VECTOR + "&vectorStore=#simpleVectorStore")
                .withBody(CAT)
                .request())
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("SimpleVectorStore does not support SIMILARITY_SEARCH with a vector")
                .hasMessageContaining("searchByVector")
                .cause()
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private void upsert(String id, String text, float[] vector) {
        vectorStore.upsert(List.of(new EmbeddedDocument(new Document(id, text, Map.of()), vector)));
    }

    /**
     * A vector store with UPSERT and the search with a vector, as PgVector or Qdrant, which keeps the documents in
     * memory. Its embedding model fails, as UPSERT and the search with a vector do not embed a text.
     */
    static final class InMemoryVectorStore extends AbstractObservationVectorStore {

        final Map<String, EmbeddedDocument> entries = new LinkedHashMap<>();
        SearchRequest lastSearchRequest;

        InMemoryVectorStore() {
            super(new Builder());
        }

        @Override
        protected boolean supportsUpsert() {
            return true;
        }

        @Override
        protected void doUpsert(List<EmbeddedDocument> list) {
            list.forEach(entry -> entries.put(entry.document().getId(), entry));
        }

        @Override
        protected boolean supportsQueryEmbeddingSearch() {
            return true;
        }

        @Override
        protected List<Document> doSimilaritySearch(float[] queryEmbedding, SearchRequest request) {
            lastSearchRequest = request;
            return entries.values().stream()
                    .map(entry -> entry.document().mutate().score(cosine(queryEmbedding, entry.embedding())).build())
                    .filter(document -> document.getScore() >= request.getSimilarityThreshold())
                    .sorted(Comparator.comparing(Document::getScore).reversed())
                    .limit(request.getTopK())
                    .toList();
        }

        private static double cosine(float[] a, float[] b) {
            double dot = 0;
            double normA = 0;
            double normB = 0;
            for (int i = 0; i < a.length; i++) {
                dot += a[i] * b[i];
                normA += a[i] * a[i];
                normB += b[i] * b[i];
            }
            return dot / (Math.sqrt(normA) * Math.sqrt(normB));
        }

        @Override
        public void doAdd(List<Document> documents) {
            throw new IllegalStateException("ADD is not expected");
        }

        @Override
        public void doDelete(List<String> idList) {
            idList.forEach(entries::remove);
        }

        @Override
        public List<Document> doSimilaritySearch(SearchRequest request) {
            throw new IllegalStateException("The search with a text is not expected");
        }

        @Override
        public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
            return VectorStoreObservationContext.builder("in-memory", operationName);
        }

        static final class Builder extends AbstractVectorStoreBuilder<Builder> {

            Builder() {
                super(new FailingEmbeddingModel());
            }

            @Override
            public InMemoryVectorStore build() {
                return new InMemoryVectorStore();
            }
        }
    }

    private static final class FailingEmbeddingModel implements EmbeddingModel {

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            throw new IllegalStateException("The embedding model of the vector store must not be called");
        }

        @Override
        public float[] embed(Document document) {
            throw new IllegalStateException("The embedding model of the vector store must not be called");
        }
    }

    private static final class FixedEmbeddingModel implements EmbeddingModel {

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new ArrayList<>();
            for (int i = 0; i < request.getInstructions().size(); i++) {
                embeddings.add(new Embedding(new float[] { 0.1f, 0.2f, 0.3f }, i));
            }
            return new EmbeddingResponse(embeddings, new EmbeddingResponseMetadata());
        }

        @Override
        public float[] embed(Document document) {
            return new float[] { 0.1f, 0.2f, 0.3f };
        }
    }
}
