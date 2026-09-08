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
package org.apache.camel.component.langchain4j.ingest;

import java.util.List;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.qdrant.services.QdrantService;
import org.apache.camel.test.infra.qdrant.services.QdrantServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The endpoint against real infrastructure: documents sent to a plain {@code langchain4j-ingest} producer are split,
 * embedded by a real in-process model (all-MiniLM-L6-v2) and written to a real Qdrant instance — and a semantic search
 * over that store then finds each document by meaning, citing it through the {@code camel_ingest_document_id} metadata.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LangChain4jIngestQdrantIT extends CamelTestSupport {

    private static final String PIPELINE = "qdrant-endpoint";
    private static final String COLLECTION = "ingest-endpoint-it";
    /** all-MiniLM-L6-v2 produces 384-dimensional vectors. */
    private static final int DIMENSION = 384;

    // not the singleton service: with two IT classes in this module only the first
    // registered singleton instance is initialized, and the other class would read the
    // mapped port of a container it never started
    @RegisterExtension
    static QdrantService QDRANT = QdrantServiceFactory.createService();

    /** One instance: constructing the model loads the ONNX runtime and the bundled model. */
    private static final EmbeddingModel MODEL = new AllMiniLmL6V2EmbeddingModel();

    private EmbeddingStore<TextSegment> store;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        // the collection must exist before the endpoint writes into it
        try (QdrantClient client = new QdrantClient(
                QdrantGrpcClient.newBuilder(QDRANT.host(), QDRANT.port(), false).build())) {
            client.createCollectionAsync(COLLECTION, Collections.VectorParams.newBuilder()
                    .setDistance(Collections.Distance.Cosine)
                    .setSize(DIMENSION)
                    .build()).get();
        }

        store = QdrantEmbeddingStore.builder()
                .host(QDRANT.host())
                .port(QDRANT.port())
                .collectionName(COLLECTION)
                .build();

        // one bean of each type in the registry, discovered by the endpoint's autowiring
        context.getRegistry().bind("store", store);
        context.getRegistry().bind("model", MODEL);

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:ingest").to("langchain4j-ingest:" + PIPELINE);
            }
        };
    }

    @Test
    void endpointFeedsARealStoreAndSemanticSearchCitesTheDocuments() {
        IngestResult camels = template.requestBodyAndHeader("direct:ingest",
                "Camels are resilient desert animals. They store fat in their humps and can carry"
                                                                             + " heavy loads across long distances without water.",
                LangChain4jIngestHeaders.DOCUMENT_ID, "camels.txt", IngestResult.class);
        IngestResult routing = template.requestBodyAndHeader("direct:ingest",
                "Apache Camel connects systems with routes. Messages flow between endpoints"
                                                                              + " following enterprise integration patterns.",
                LangChain4jIngestHeaders.DOCUMENT_ID, "routing.txt", IngestResult.class);

        assertThat(camels.outcome()).isEqualTo(IngestResult.Outcome.INGESTED);
        assertThat(routing.outcome()).isEqualTo(IngestResult.Outcome.INGESTED);
        assertThat(camels.segmentsWritten()).isGreaterThanOrEqualTo(1);

        // no shared vocabulary with the documents on purpose: only a real embedding model
        // finds these by meaning
        assertTopMatch("Which animal survives long journeys through hot sand?", "camels.txt");
        assertTopMatch("How do I integrate two applications with messaging?", "routing.txt");
    }

    private void assertTopMatch(String question, String expectedDocumentId) {
        List<EmbeddingMatch<TextSegment>> matches = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(MODEL.embed(question).content())
                .maxResults(3)
                .build()).matches();

        assertThat(matches).as("matches for: %s", question).isNotEmpty();
        EmbeddingMatch<TextSegment> top = matches.get(0);
        assertThat(top.embedded().metadata().getString(LangChain4jIngest.METADATA_DOCUMENT_ID))
                .as("top match for '%s' cites the right document", question)
                .isEqualTo(expectedDocumentId);
        assertThat(top.embedded().metadata().getString(LangChain4jIngest.METADATA_PIPELINE))
                .isEqualTo(PIPELINE);
    }
}
