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

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
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
}
