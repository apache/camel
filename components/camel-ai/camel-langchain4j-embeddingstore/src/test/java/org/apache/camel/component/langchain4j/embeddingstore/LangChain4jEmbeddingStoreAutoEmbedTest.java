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

import java.util.List;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.embeddings.LangChain4jEmbeddingsHeaders;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LangChain4jEmbeddingStoreAutoEmbedTest extends CamelTestSupport {

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
    private final InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        LangChain4jEmbeddingStoreComponent component
                = context.getComponent(LangChain4jEmbeddingStore.SCHEME, LangChain4jEmbeddingStoreComponent.class);
        component.getConfiguration().setEmbeddingStore(embeddingStore);
        component.getConfiguration().setEmbeddingModel(embeddingModel);

        LangChain4jEmbeddingStoreComponent noModelComponent = new LangChain4jEmbeddingStoreComponent();
        noModelComponent.getConfiguration().setEmbeddingStore(new InMemoryEmbeddingStore<>());
        context.addComponent("no-model-store", noModelComponent);

        return context;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:add")
                        .to("langchain4j-embeddingstore:test?action=ADD");

                from("direct:search")
                        .to("langchain4j-embeddingstore:test?action=SEARCH&returnTextContent=true");

                from("direct:search-matches")
                        .to("langchain4j-embeddingstore:test?action=SEARCH");

                from("direct:add-no-model")
                        .to("no-model-store:test?action=ADD");
            }
        };
    }

    @Test
    void addWithAutoEmbed() {
        fluentTemplate.to("direct:add")
                .withBody("I like football.")
                .send();

        List<String> results = fluentTemplate.to("direct:search")
                .withBody("sports")
                .request(List.class);

        assertThat(results).hasSize(1);
        assertThat(results.get(0)).isEqualTo("I like football.");
    }

    @Test
    void addWithExplicitHeaderTakesPrecedence() {
        TextSegment segment = TextSegment.from("I like football.");
        Embedding precomputed = embeddingModel.embed(segment).content();

        fluentTemplate.to("direct:add")
                .withBody("this body text should be ignored")
                .withHeader(LangChain4jEmbeddingsHeaders.EMBEDDING, precomputed)
                .withHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENT, segment)
                .send();

        List<String> results = fluentTemplate.to("direct:search")
                .withBody("sports")
                .request(List.class);

        assertThat(results).hasSize(1);
        assertThat(results.get(0)).isEqualTo("I like football.");
    }

    @Test
    void addWithoutModelOrHeaderFails() {
        Exchange result = fluentTemplate.to("direct:add-no-model")
                .withBody("some text")
                .send();

        assertThat(result.getException())
                .isInstanceOf(NoSuchHeaderException.class)
                .hasMessageContaining("embeddingModel");
    }

    @Test
    void addWithNullBodyAndAutoEmbedFails() {
        Exchange result = fluentTemplate.to("direct:add")
                .withBody(null)
                .send();

        assertThat(result.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("body cannot be converted to String");
    }

    @Test
    void addWithTextSegmentHeaderPreservedDuringAutoEmbed() {
        TextSegment headerSegment = TextSegment.from("XYZZY-marker-42");

        fluentTemplate.to("direct:add")
                .withBody("I like football.")
                .withHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENT, headerSegment)
                .send();

        Embedding queryEmbedding = embeddingModel.embed("XYZZY-marker-42").content();
        var matches = embeddingStore.search(
                EmbeddingSearchRequest.builder().queryEmbedding(queryEmbedding).maxResults(1).build()).matches();

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).embedded().text()).isEqualTo("XYZZY-marker-42");
    }

    @Test
    void searchWithAutoEmbed() {
        TextSegment segment = TextSegment.from("Apache Camel is a powerful integration framework");
        Embedding embedding = embeddingModel.embed(segment).content();
        embeddingStore.add(embedding, segment);

        List<String> results = fluentTemplate.to("direct:search")
                .withBody("integration framework")
                .request(List.class);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0)).contains("Apache Camel");
    }

    @Test
    void addTextSegmentBodyStoresTheSegment() {
        TextSegment segment = TextSegment.from("I like football.", Metadata.from("source", "notes"));

        String id = fluentTemplate.to("direct:add")
                .withBody(segment)
                .request(String.class);

        List<EmbeddingMatch<TextSegment>> matches = search("I like football.", 5);
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).embeddingId()).isEqualTo(id);
        assertThat(matches.get(0).embedded().text()).isEqualTo("I like football.");
        assertThat(matches.get(0).embedded().metadata().getString("source")).isEqualTo("notes");
        // the embedding is the one of the segment text, not of its toString
        assertThat(matches.get(0).score()).isGreaterThan(0.99);
    }

    @Test
    void addListOfStringsBodyAddsOneEntryPerElement() {
        List<String> ids = fluentTemplate.to("direct:add")
                .withBody(List.of("I like football.", "The weather is good today."))
                .request(List.class);

        assertThat(ids).hasSize(2);
        List<EmbeddingMatch<TextSegment>> matches = search("sports", 5);
        assertThat(matches).hasSize(2);
        assertThat(matches).extracting(m -> m.embedded().text())
                .containsExactly("I like football.", "The weather is good today.");
        assertThat(matches).extracting(EmbeddingMatch::embeddingId).containsExactlyInAnyOrderElementsOf(ids);
    }

    @Test
    void addListOfTextSegmentsBodyKeepsTheMetadata() {
        List<TextSegment> segments = List.of(
                TextSegment.from("I like football.", Metadata.from("index", "1")),
                TextSegment.from("The weather is good today.", Metadata.from("index", "2")));

        List<String> ids = fluentTemplate.to("direct:add")
                .withBody(segments)
                .withHeader(LangChain4jEmbeddingStoreHeaders.EMBEDDING_IDS, List.of("id-1", "id-2"))
                .request(List.class);

        assertThat(ids).containsExactly("id-1", "id-2");
        List<EmbeddingMatch<TextSegment>> matches = search("sports", 5);
        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).embeddingId()).isEqualTo("id-1");
        assertThat(matches.get(0).embedded().metadata().getString("index")).isEqualTo("1");
        assertThat(matches.get(1).embeddingId()).isEqualTo("id-2");
        assertThat(matches.get(1).embedded().metadata().getString("index")).isEqualTo("2");
    }

    @Test
    void addListBodyWithTextSegmentHeaderStoresTheHeaderSegment() {
        String id = fluentTemplate.to("direct:add")
                .withBody(List.of("I like football.", "The weather is good today."))
                .withHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENT, TextSegment.from("XYZZY-marker-42"))
                .request(String.class);

        List<EmbeddingMatch<TextSegment>> matches = search("XYZZY-marker-42", 5);
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).embeddingId()).isEqualTo(id);
        assertThat(matches.get(0).embedded().text()).isEqualTo("XYZZY-marker-42");
    }

    @Test
    void addListWithAnElementThatIsNotTextFails() {
        Exchange result = fluentTemplate.to("direct:add")
                .withBody(List.of("I like football.", 42))
                .send();

        assertThat(result.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("index 1")
                .hasMessageContaining("java.lang.Integer");
        assertThat(search("sports", 5)).isEmpty();
    }

    @Test
    void addEmptyListFails() {
        Exchange result = fluentTemplate.to("direct:add")
                .withBody(List.of())
                .send();

        assertThat(result.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-empty List body");
    }

    @Test
    void searchWithTextSegmentBody() {
        TextSegment segment = TextSegment.from("Apache Camel is a powerful integration framework");
        embeddingStore.add(embeddingModel.embed(segment).content(), segment);

        List<EmbeddingMatch<TextSegment>> matches = fluentTemplate.to("direct:search-matches")
                .withBody(segment)
                .request(List.class);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).score()).isGreaterThan(0.99);
    }

    @Test
    void searchWithListBodyFails() {
        Exchange result = fluentTemplate.to("direct:search")
                .withBody(List.of("sports", "weather"))
                .send();

        assertThat(result.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one text");
    }

    private List<EmbeddingMatch<TextSegment>> search(String text, int maxResults) {
        Embedding queryEmbedding = embeddingModel.embed(text).content();
        return embeddingStore.search(
                EmbeddingSearchRequest.builder().queryEmbedding(queryEmbedding).maxResults(maxResults).build()).matches();
    }
}
