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

import java.util.HashMap;
import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.embeddingstore.LangChain4jEmbeddingStore;
import org.apache.camel.component.langchain4j.embeddingstore.LangChain4jEmbeddingStoreAction;
import org.apache.camel.component.langchain4j.embeddingstore.LangChain4jEmbeddingStoreComponent;
import org.apache.camel.component.langchain4j.embeddingstore.LangChain4jEmbeddingStoreHeaders;
import org.apache.camel.test.infra.qdrant.services.QdrantService;
import org.apache.camel.test.infra.qdrant.services.QdrantServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LangChain4jEmbeddingStoreComponentQdrantTargetIT extends CamelTestSupport {
    public static final String QDRANT_URI = "langchain4j-embeddingstore";
    public static final String COLLECTION_NAME = "embeddingstore01";
    private static Collections.Distance distance = Collections.Distance.Cosine;
    private static int dimension = 384;
    private static String CREATEID = null;

    @RegisterExtension
    static QdrantService QDRANT = QdrantServiceFactory.createSingletonService();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        // Need to create the Qdrant collection before we attempt to add/search/remove from it
        QdrantClient client
                = new QdrantClient(QdrantGrpcClient.newBuilder(QDRANT.getGrpcHost(), QDRANT.getGrpcPort(), false).build());
        client.createCollectionAsync(COLLECTION_NAME,
                Collections.VectorParams.newBuilder().setDistance(distance).setSize(dimension).build()).get();

        EmbeddingStore<TextSegment> embeddingStore = QdrantEmbeddingStore.builder()
                .host(QDRANT.getGrpcHost())
                .port(QDRANT.getGrpcPort())
                .collectionName(COLLECTION_NAME)
                .build();

        var qc = context.getComponent(LangChain4jEmbeddingStore.SCHEME, LangChain4jEmbeddingStoreComponent.class);
        qc.getConfiguration().setEmbeddingStore(embeddingStore);

        return context;
    }

    @Test
    @Order(1)
    public void add() {
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("sky", "blue");
        map.put("age", "34");

        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        TextSegment segment1 = TextSegment.from("I like football.");
        Embedding embedding = embeddingModel.embed(segment1).content();

        Exchange result = fluentTemplate.to("direct:add")
                .withHeader(LangChain4jEmbeddingStoreHeaders.ACTION, LangChain4jEmbeddingStoreAction.ADD)
                .withBody(embedding) // ignored
                .withHeader(LangChain4jEmbeddingsHeaders.EMBEDDING, embedding)
                .withHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENT, segment1)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        CREATEID = (String) result.getIn().getBody();
        assertThat(CREATEID).isNotNull();
    }

    @Test
    @Order(2)
    public void search() {

        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        TextSegment segment1 = TextSegment.from("I like football.");
        Embedding embedding = embeddingModel.embed(segment1).content();

        Filter filter = metadataKey("sky").isEqualTo("blue");
        Exchange result = fluentTemplate.to("direct:search")
                .withHeader(LangChain4jEmbeddingStoreHeaders.ACTION, LangChain4jEmbeddingStoreAction.SEARCH)
                .withHeader(LangChain4jEmbeddingsHeaders.EMBEDDING, embedding)
                .withHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENT, segment1)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        List<EmbeddingMatch<TextSegment>> embeddingMatches = (List) result.getIn().getBody();
        assertThat(embeddingMatches).isNotNull();
        assertTrue(embeddingMatches.size() > 0);
        for (EmbeddingMatch<TextSegment> em : embeddingMatches) {
            assertThat(em.score()).isNotNull();
            assertThat(em.embeddingId()).isNotNull();
            assertThat(em.embedded().text()).isEqualTo(segment1.text());
        }
    }

    @Test
    @Order(3)
    public void searchWithEndpointProperties() {
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        TextSegment segment1 = TextSegment.from("I like football.");
        Embedding embedding = embeddingModel.embed(segment1).content();

        // Test using action as endpoint property (no header needed)
        Exchange result = fluentTemplate.to("direct:searchWithEndpointProperties")
                .withHeader(LangChain4jEmbeddingsHeaders.EMBEDDING, embedding)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        List<EmbeddingMatch<TextSegment>> embeddingMatches = (List) result.getIn().getBody();
        assertThat(embeddingMatches).isNotNull();
        assertTrue(embeddingMatches.size() > 0);
    }

    @Test
    @Order(4)
    public void searchWithReturnTextContent() {
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        TextSegment segment1 = TextSegment.from("I like football.");
        Embedding embedding = embeddingModel.embed(segment1).content();

        // Test using returnTextContent option
        Exchange result = fluentTemplate.to("direct:searchWithReturnTextContent")
                .withHeader(LangChain4jEmbeddingsHeaders.EMBEDDING, embedding)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        List<String> textResults = (List<String>) result.getIn().getBody();
        assertThat(textResults).isNotNull();
        assertTrue(textResults.size() > 0);
        assertThat(textResults.get(0)).isEqualTo("I like football.");
    }

    @Test
    @Order(5)
    public void remove() {
        Exchange result = fluentTemplate.to("direct:remove")
                .withHeader(LangChain4jEmbeddingStoreHeaders.ACTION, LangChain4jEmbeddingStoreAction.REMOVE)
                .withBody(CREATEID)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:add")
                        .to("langchain4j-embeddingstore:test")
                        .setHeader(LangChain4jEmbeddingStoreHeaders.ACTION).constant(LangChain4jEmbeddingStoreAction.ADD)
                        .to(QDRANT_URI);

                from("direct:search")
                        .to("langchain4j-embeddingstore:test")
                        .setHeader(LangChain4jEmbeddingStoreHeaders.ACTION, constant(LangChain4jEmbeddingStoreAction.SEARCH))
                        .to(QDRANT_URI);

                from("direct:remove")
                        .to("langchain4j-embeddingstore:test")
                        .setHeader(LangChain4jEmbeddingStoreHeaders.ACTION, constant(LangChain4jEmbeddingStoreAction.REMOVE))
                        .to(QDRANT_URI);

                // Route using action as endpoint property
                from("direct:searchWithEndpointProperties")
                        .to(QDRANT_URI + "?action=SEARCH&maxResults=3");

                // Route using returnTextContent option
                from("direct:searchWithReturnTextContent")
                        .to(QDRANT_URI + "?action=SEARCH&maxResults=5&returnTextContent=true");
            }
        };
    }
}
