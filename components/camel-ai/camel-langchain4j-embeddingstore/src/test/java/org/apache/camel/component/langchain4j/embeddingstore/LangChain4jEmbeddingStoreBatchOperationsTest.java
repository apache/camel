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
package org.apache.camel.component.langchain4j.embeddingstore;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.langchain4j.embeddings.LangChain4jEmbeddingsHeaders;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LangChain4jEmbeddingStoreBatchOperationsTest extends CamelTestSupport {

    private RecordingEmbeddingStore embeddingStore;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        embeddingStore = new RecordingEmbeddingStore();

        LangChain4jEmbeddingStoreComponent component = context.getComponent(
                LangChain4jEmbeddingStore.SCHEME, LangChain4jEmbeddingStoreComponent.class);
        component.getConfiguration().setEmbeddingStore(embeddingStore);

        return context;
    }

    // ---- ADD: caller-supplied ID ----

    @Test
    @DisplayName("ADD with caller-supplied ID uses add(id, embedding)")
    void addWithCallerSuppliedId() {
        Embedding embedding = Embedding.from(new float[] { 0.1f, 0.2f, 0.3f });

        Exchange result = fluentTemplate.to("langchain4j-embeddingstore:test")
                .withHeader(LangChain4jEmbeddingStoreHeaders.ACTION, LangChain4jEmbeddingStoreAction.ADD)
                .withHeader(LangChain4jEmbeddingsHeaders.EMBEDDING, embedding)
                .withHeader(LangChain4jEmbeddingStoreHeaders.EMBEDDING_ID, "my-custom-id")
                .request(Exchange.class);

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo("my-custom-id");
        assertThat(embeddingStore.getAddWithIdInvocations()).isEqualTo(1);
        assertThat(embeddingStore.getLastCallerSuppliedId()).isEqualTo("my-custom-id");
    }

    // ---- ADD: caller-supplied ID with text segment ----

    @Test
    @DisplayName("ADD with caller-supplied ID and text segment uses addAll(singletonList) to preserve both")
    void addWithCallerSuppliedIdAndTextSegment() {
        Embedding embedding = Embedding.from(new float[] { 0.1f, 0.2f, 0.3f });
        TextSegment segment = TextSegment.from("hello world");

        Exchange result = fluentTemplate.to("langchain4j-embeddingstore:test")
                .withHeader(LangChain4jEmbeddingStoreHeaders.ACTION, LangChain4jEmbeddingStoreAction.ADD)
                .withHeader(LangChain4jEmbeddingsHeaders.EMBEDDING, embedding)
                .withHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENT, segment)
                .withHeader(LangChain4jEmbeddingStoreHeaders.EMBEDDING_ID, "my-custom-id")
                .request(Exchange.class);

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo("my-custom-id");
        // Should use addAll with singleton lists to preserve text segment with caller ID
        assertThat(embeddingStore.getAddAllWithIdsInvocations()).isEqualTo(1);
    }

    // ---- ADD: batch operations ----

    @Test
    @DisplayName("ADD with EMBEDDINGS header and no body calls addAll(embeddings)")
    void addBatchEmbeddingsOnly() {
        List<Embedding> embeddings = Arrays.asList(
                Embedding.from(new float[] { 0.1f, 0.2f }),
                Embedding.from(new float[] { 0.3f, 0.4f }));

        Exchange result = fluentTemplate.to("langchain4j-embeddingstore:test")
                .withHeader(LangChain4jEmbeddingStoreHeaders.ACTION, LangChain4jEmbeddingStoreAction.ADD)
                .withHeader(LangChain4jEmbeddingsHeaders.EMBEDDINGS, embeddings)
                .request(Exchange.class);

        assertThat(result.getException()).isNull();
        assertThat(embeddingStore.getAddAllInvocations()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        List<String> ids = result.getMessage().getBody(List.class);
        assertThat(ids).hasSize(2);
    }

    @Test
    @DisplayName("ADD with EMBEDDINGS header and TextSegment body calls addAll(embeddings, textSegments)")
    void addBatchEmbeddingsWithTextSegments() {
        List<Embedding> embeddings = Arrays.asList(
                Embedding.from(new float[] { 0.1f, 0.2f }),
                Embedding.from(new float[] { 0.3f, 0.4f }));
        List<TextSegment> segments = Arrays.asList(
                TextSegment.from("hello"),
                TextSegment.from("world"));

        Exchange result = fluentTemplate.to("langchain4j-embeddingstore:test")
                .withHeader(LangChain4jEmbeddingStoreHeaders.ACTION, LangChain4jEmbeddingStoreAction.ADD)
                .withHeader(LangChain4jEmbeddingsHeaders.EMBEDDINGS, embeddings)
                .withBody(segments)
                .request(Exchange.class);

        assertThat(result.getException()).isNull();
        assertThat(embeddingStore.getAddAllWithSegmentsInvocations()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        List<String> ids = result.getMessage().getBody(List.class);
        assertThat(ids).hasSize(2);
    }

    @Test
    @DisplayName("ADD with EMBEDDINGS header and TEXT_SEGMENTS header calls addAll(embeddings, textSegments)")
    void addBatchEmbeddingsWithTextSegmentsHeader() {
        List<Embedding> embeddings = Arrays.asList(
                Embedding.from(new float[] { 0.1f, 0.2f }),
                Embedding.from(new float[] { 0.3f, 0.4f }));
        List<TextSegment> segments = Arrays.asList(
                TextSegment.from("hello"),
                TextSegment.from("world"));

        Exchange result = fluentTemplate.to("langchain4j-embeddingstore:test")
                .withHeader(LangChain4jEmbeddingStoreHeaders.ACTION, LangChain4jEmbeddingStoreAction.ADD)
                .withHeader(LangChain4jEmbeddingsHeaders.EMBEDDINGS, embeddings)
                .withHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENTS, segments)
                .request(Exchange.class);

        assertThat(result.getException()).isNull();
        assertThat(embeddingStore.getAddAllWithSegmentsInvocations()).isEqualTo(1);
    }

    @Test
    @DisplayName("ADD with EMBEDDINGS header, caller IDs, and TextSegment body calls addAll(ids, embeddings, textSegments)")
    void addBatchWithCallerIdsAndTextSegments() {
        List<String> callerIds = Arrays.asList("id-1", "id-2");
        List<Embedding> embeddings = Arrays.asList(
                Embedding.from(new float[] { 0.1f, 0.2f }),
                Embedding.from(new float[] { 0.3f, 0.4f }));
        List<TextSegment> segments = Arrays.asList(
                TextSegment.from("hello"),
                TextSegment.from("world"));

        Exchange result = fluentTemplate.to("langchain4j-embeddingstore:test")
                .withHeader(LangChain4jEmbeddingStoreHeaders.ACTION, LangChain4jEmbeddingStoreAction.ADD)
                .withHeader(LangChain4jEmbeddingsHeaders.EMBEDDINGS, embeddings)
                .withHeader(LangChain4jEmbeddingStoreHeaders.EMBEDDING_IDS, callerIds)
                .withBody(segments)
                .request(Exchange.class);

        assertThat(result.getException()).isNull();
        assertThat(embeddingStore.getAddAllWithIdsInvocations()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        List<String> ids = result.getMessage().getBody(List.class);
        assertThat(ids).containsExactly("id-1", "id-2");
    }

    @Test
    @DisplayName("ADD with EMBEDDINGS header and caller IDs but no text segments loops with add(id, embedding)")
    void addBatchWithCallerIdsNoTextSegments() {
        List<String> callerIds = Arrays.asList("id-a", "id-b");
        List<Embedding> embeddings = Arrays.asList(
                Embedding.from(new float[] { 0.5f, 0.6f }),
                Embedding.from(new float[] { 0.7f, 0.8f }));

        Exchange result = fluentTemplate.to("langchain4j-embeddingstore:test")
                .withHeader(LangChain4jEmbeddingStoreHeaders.ACTION, LangChain4jEmbeddingStoreAction.ADD)
                .withHeader(LangChain4jEmbeddingsHeaders.EMBEDDINGS, embeddings)
                .withHeader(LangChain4jEmbeddingStoreHeaders.EMBEDDING_IDS, callerIds)
                .request(Exchange.class);

        assertThat(result.getException()).isNull();
        // Should call add(id, embedding) for each pair, not addAll(embeddings)
        assertThat(embeddingStore.getAddWithIdInvocations()).isEqualTo(2);
        assertThat(embeddingStore.getAddAllInvocations()).isEqualTo(0);

        @SuppressWarnings("unchecked")
        List<String> ids = result.getMessage().getBody(List.class);
        assertThat(ids).containsExactly("id-a", "id-b");
    }

    // ---- ADD: size mismatch validation ----

    @Test
    @DisplayName("ADD with mismatched EMBEDDING_IDS and EMBEDDINGS sizes throws IllegalArgumentException")
    void addBatchSizeMismatchThrows() {
        List<String> callerIds = Arrays.asList("id-1"); // 1 ID
        List<Embedding> embeddings = Arrays.asList(
                Embedding.from(new float[] { 0.1f }),
                Embedding.from(new float[] { 0.2f })); // 2 embeddings

        Exchange result = fluentTemplate.to("langchain4j-embeddingstore:test")
                .withHeader(LangChain4jEmbeddingStoreHeaders.ACTION, LangChain4jEmbeddingStoreAction.ADD)
                .withHeader(LangChain4jEmbeddingsHeaders.EMBEDDINGS, embeddings)
                .withHeader(LangChain4jEmbeddingStoreHeaders.EMBEDDING_IDS, callerIds)
                .request(Exchange.class);

        assertThat(result.getException()).isInstanceOf(IllegalArgumentException.class);
        assertThat(result.getException().getMessage()).contains("EMBEDDING_IDS size");
    }

    // ---- REMOVE: batch by ID list ----

    @Test
    @DisplayName("REMOVE with Collection<String> body calls removeAll(ids)")
    void removeBatchByIds() {
        // Pre-populate
        String id1 = embeddingStore.add(Embedding.from(new float[] { 0.1f }));
        String id2 = embeddingStore.add(Embedding.from(new float[] { 0.2f }));
        embeddingStore.resetCounters();

        List<String> ids = Arrays.asList(id1, id2);
        Exchange result = fluentTemplate.to("langchain4j-embeddingstore:test")
                .withHeader(LangChain4jEmbeddingStoreHeaders.ACTION, LangChain4jEmbeddingStoreAction.REMOVE)
                .withBody(ids)
                .request(Exchange.class);

        assertThat(result.getException()).isNull();
        assertThat(embeddingStore.getRemoveAllByIdsInvocations()).isEqualTo(1);
    }

    // ---- REMOVE: by filter ----

    @Test
    @DisplayName("REMOVE with FILTER header calls removeAll(filter)")
    void removeByFilter() {
        Filter filter = new Filter() {
            @Override
            public boolean test(Object object) {
                return true;
            }
        };

        Exchange result = fluentTemplate.to("langchain4j-embeddingstore:test")
                .withHeader(LangChain4jEmbeddingStoreHeaders.ACTION, LangChain4jEmbeddingStoreAction.REMOVE)
                .withHeader(LangChain4jEmbeddingStoreHeaders.FILTER, filter)
                .request(Exchange.class);

        assertThat(result.getException()).isNull();
        assertThat(embeddingStore.getRemoveAllByFilterInvocations()).isEqualTo(1);
    }

    // ---- REMOVE: null body and no filter throws ----

    @Test
    @DisplayName("REMOVE with null body and no filter throws IllegalArgumentException")
    void removeWithNoBodyOrFilterThrows() {
        Exchange result = fluentTemplate.to("langchain4j-embeddingstore:test")
                .withHeader(LangChain4jEmbeddingStoreHeaders.ACTION, LangChain4jEmbeddingStoreAction.REMOVE)
                .request(Exchange.class);

        assertThat(result.getException()).isInstanceOf(IllegalArgumentException.class);
        assertThat(result.getException().getMessage()).contains("REMOVE action requires");
    }

    // ---- REMOVE: single ID still works ----

    @Test
    @DisplayName("REMOVE with single String body still calls remove(id)")
    void removeSingleIdStillWorks() {
        String id = embeddingStore.add(Embedding.from(new float[] { 0.9f }));
        embeddingStore.resetCounters();

        Exchange result = fluentTemplate.to("langchain4j-embeddingstore:test")
                .withHeader(LangChain4jEmbeddingStoreHeaders.ACTION, LangChain4jEmbeddingStoreAction.REMOVE)
                .withBody(id)
                .request(Exchange.class);

        assertThat(result.getException()).isNull();
        assertThat(embeddingStore.getRemoveSingleInvocations()).isEqualTo(1);
    }

    /**
     * Extended recording store that tracks which method variants are called.
     */
    private static final class RecordingEmbeddingStore extends InMemoryEmbeddingStore<TextSegment> {

        private int addWithIdInvocations;
        private int addAllInvocations;
        private int addAllWithSegmentsInvocations;
        private int addAllWithIdsInvocations;
        private int removeSingleInvocations;
        private int removeAllByIdsInvocations;
        private int removeAllByFilterInvocations;
        private int removeAllInvocations;
        private String lastCallerSuppliedId;

        @Override
        public String add(Embedding embedding) {
            return super.add(embedding);
        }

        @Override
        public void add(String id, Embedding embedding) {
            addWithIdInvocations++;
            lastCallerSuppliedId = id;
            super.add(id, embedding);
        }

        @Override
        public List<String> addAll(List<Embedding> embeddings) {
            addAllInvocations++;
            return super.addAll(embeddings);
        }

        @Override
        public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
            addAllWithSegmentsInvocations++;
            return super.addAll(embeddings, embedded);
        }

        @Override
        public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
            addAllWithIdsInvocations++;
            super.addAll(ids, embeddings, embedded);
        }

        @Override
        public void remove(String id) {
            removeSingleInvocations++;
            super.remove(id);
        }

        @Override
        public void removeAll(Collection<String> ids) {
            removeAllByIdsInvocations++;
            super.removeAll(ids);
        }

        @Override
        public void removeAll(Filter filter) {
            removeAllByFilterInvocations++;
            super.removeAll(filter);
        }

        @Override
        public void removeAll() {
            removeAllInvocations++;
            super.removeAll();
        }

        void resetCounters() {
            addWithIdInvocations = 0;
            addAllInvocations = 0;
            addAllWithSegmentsInvocations = 0;
            addAllWithIdsInvocations = 0;
            removeSingleInvocations = 0;
            removeAllByIdsInvocations = 0;
            removeAllByFilterInvocations = 0;
            removeAllInvocations = 0;
            lastCallerSuppliedId = null;
        }

        int getAddWithIdInvocations() {
            return addWithIdInvocations;
        }

        int getAddAllInvocations() {
            return addAllInvocations;
        }

        int getAddAllWithSegmentsInvocations() {
            return addAllWithSegmentsInvocations;
        }

        int getAddAllWithIdsInvocations() {
            return addAllWithIdsInvocations;
        }

        int getRemoveSingleInvocations() {
            return removeSingleInvocations;
        }

        int getRemoveAllByIdsInvocations() {
            return removeAllByIdsInvocations;
        }

        int getRemoveAllByFilterInvocations() {
            return removeAllByFilterInvocations;
        }

        int getRemoveAllInvocations() {
            return removeAllInvocations;
        }

        String getLastCallerSuppliedId() {
            return lastCallerSuppliedId;
        }
    }
}
