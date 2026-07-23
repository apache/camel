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

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.component.langchain4j.embeddings.LangChain4jEmbeddingsHeaders;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LangChain4jEmbeddingStoreMissingEmbeddingHeaderTest extends CamelTestSupport {

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

    @Test
    @DisplayName("ADD without embedding header fails fast with NoSuchHeaderException")
    void addWithoutEmbeddingHeaderFailsFast() {
        Exchange result = fluentTemplate.to("langchain4j-embeddingstore:test")
                .withHeader(LangChain4jEmbeddingStoreHeaders.ACTION, LangChain4jEmbeddingStoreAction.ADD)
                .request(Exchange.class);

        assertMissingEmbeddingHeader(result);
        assertThat(embeddingStore.getAddInvocations()).isZero();
    }

    @Test
    @DisplayName("ADD with endpoint default action but no embedding header fails fast")
    void addWithEndpointDefaultActionAndMissingEmbeddingHeaderFailsFast() {
        Exchange result = fluentTemplate.to("langchain4j-embeddingstore:test?action=ADD")
                .request(Exchange.class);

        assertMissingEmbeddingHeader(result);
        assertThat(embeddingStore.getAddInvocations()).isZero();
    }

    @Test
    @DisplayName("ADD with text segment but no embedding header still fails fast")
    void addWithTextSegmentButNoEmbeddingHeaderFailsFast() {
        Exchange result = fluentTemplate.to("langchain4j-embeddingstore:test")
                .withHeader(LangChain4jEmbeddingStoreHeaders.ACTION, LangChain4jEmbeddingStoreAction.ADD)
                .withHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENT, TextSegment.from("hello"))
                .request(Exchange.class);

        assertMissingEmbeddingHeader(result);
        assertThat(embeddingStore.getAddInvocations()).isZero();
    }

    @Test
    @DisplayName("ADD with embedding header stores the embedding")
    void addWithEmbeddingHeaderSucceeds() {
        Embedding embedding = Embedding.from(new float[] { 0.1f, 0.2f, 0.3f });

        Exchange result = fluentTemplate.to("langchain4j-embeddingstore:test")
                .withHeader(LangChain4jEmbeddingStoreHeaders.ACTION, LangChain4jEmbeddingStoreAction.ADD)
                .withHeader(LangChain4jEmbeddingsHeaders.EMBEDDING, embedding)
                .request(Exchange.class);

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isNotBlank();
        assertThat(embeddingStore.getAddInvocations()).isEqualTo(1);
        assertThat(embeddingStore.getLastEmbedding()).isSameAs(embedding);
        assertThat(embeddingStore.getLastTextSegment()).isNull();
    }

    @Test
    @DisplayName("ADD with embedding and text segment stores both values")
    void addWithEmbeddingAndTextSegmentSucceeds() {
        Embedding embedding = Embedding.from(new float[] { 0.4f, 0.5f });
        TextSegment textSegment = TextSegment.from("segment");

        Exchange result = fluentTemplate.to("langchain4j-embeddingstore:test")
                .withHeader(LangChain4jEmbeddingStoreHeaders.ACTION, LangChain4jEmbeddingStoreAction.ADD)
                .withHeader(LangChain4jEmbeddingsHeaders.EMBEDDING, embedding)
                .withHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENT, textSegment)
                .request(Exchange.class);

        assertThat(result.getException()).isNull();
        assertThat(embeddingStore.getAddInvocations()).isEqualTo(1);
        assertThat(embeddingStore.getLastEmbedding()).isSameAs(embedding);
        assertThat(embeddingStore.getLastTextSegment()).isSameAs(textSegment);
    }

    @Test
    @DisplayName("Missing action header is still rejected")
    void missingActionHeaderFailsFast() {
        Exchange result = fluentTemplate.to("langchain4j-embeddingstore:test")
                .withHeader(LangChain4jEmbeddingsHeaders.EMBEDDING, Embedding.from(new float[] { 1.0f }))
                .request(Exchange.class);

        assertThat(result.getException()).isInstanceOf(NoSuchHeaderException.class);
        assertThat(((NoSuchHeaderException) result.getException()).getHeaderName())
                .isEqualTo(LangChain4jEmbeddingStoreHeaders.ACTION);
        assertThat(embeddingStore.getAddInvocations()).isZero();
    }

    @Test
    @DisplayName("REMOVE does not require the embedding header")
    void removeWithoutEmbeddingHeaderSucceeds() {
        String id = embeddingStore.add(Embedding.from(new float[] { 0.9f }));

        Exchange result = fluentTemplate.to("langchain4j-embeddingstore:test")
                .withHeader(LangChain4jEmbeddingStoreHeaders.ACTION, LangChain4jEmbeddingStoreAction.REMOVE)
                .withBody(id)
                .request(Exchange.class);

        assertThat(result.getException()).isNull();
        assertThat(embeddingStore.getRemoveInvocations()).isEqualTo(1);
        assertThat(embeddingStore.getLastRemovedId()).isEqualTo(id);
    }

    private void assertMissingEmbeddingHeader(Exchange result) {
        assertThat(result.getException()).isInstanceOf(NoSuchHeaderException.class);
        NoSuchHeaderException exception = (NoSuchHeaderException) result.getException();
        assertThat(exception.getHeaderName()).isEqualTo(LangChain4jEmbeddingsHeaders.EMBEDDING);
        assertThat(exception.getMessage()).contains("required header");
    }

    private static final class RecordingEmbeddingStore extends InMemoryEmbeddingStore<TextSegment> {

        private int addInvocations;
        private int removeInvocations;
        private Embedding lastEmbedding;
        private TextSegment lastTextSegment;
        private String lastRemovedId;

        @Override
        public String add(Embedding embedding) {
            addInvocations++;
            lastEmbedding = embedding;
            lastTextSegment = null;
            return super.add(embedding);
        }

        @Override
        public String add(Embedding embedding, TextSegment embedded) {
            addInvocations++;
            lastEmbedding = embedding;
            lastTextSegment = embedded;
            return super.add(embedding, embedded);
        }

        @Override
        public void remove(String id) {
            removeInvocations++;
            lastRemovedId = id;
            super.remove(id);
        }

        int getAddInvocations() {
            return addInvocations;
        }

        int getRemoveInvocations() {
            return removeInvocations;
        }

        Embedding getLastEmbedding() {
            return lastEmbedding;
        }

        TextSegment getLastTextSegment() {
            return lastTextSegment;
        }

        String getLastRemovedId() {
            return lastRemovedId;
        }
    }
}
