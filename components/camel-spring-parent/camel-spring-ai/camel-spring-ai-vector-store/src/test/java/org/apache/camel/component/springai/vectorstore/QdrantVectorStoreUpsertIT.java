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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.qdrant.services.QdrantService;
import org.apache.camel.test.infra.qdrant.services.QdrantServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.EmbeddedDocument;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UPSERT and SIMILARITY_SEARCH with searchByVector on Qdrant, with the vectors as they are: the embedding model of the
 * vector store fails if it is called.
 */
public class QdrantVectorStoreUpsertIT extends CamelTestSupport {

    @RegisterExtension
    static QdrantService QDRANT = QdrantServiceFactory.createSingletonService();

    private static final float[] CAT = { 1f, 0f, 0f };
    private static final float[] KITTEN = { 0.9f, 0.1f, 0f };
    private static final float[] CAR = { 0f, 0f, 1f };

    private QdrantClient qdrantClient;
    private String collectionName;
    private QdrantVectorStore vectorStore;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        collectionName = "upsert_collection_" + System.nanoTime();
        qdrantClient = new QdrantClient(
                QdrantGrpcClient.newBuilder(QDRANT.host(), QDRANT.port(), false).build());
        qdrantClient.createCollectionAsync(collectionName,
                Collections.VectorParams.newBuilder().setDistance(Collections.Distance.Cosine).setSize(CAT.length).build())
                .get();

        vectorStore = QdrantVectorStore.builder(qdrantClient, new FailingEmbeddingModel())
                .collectionName(collectionName)
                .build();
    }

    @Override
    protected void cleanupResources() throws Exception {
        if (qdrantClient != null) {
            qdrantClient.close();
        }
        super.cleanupResources();
    }

    @Test
    public void testUpsertVectorsAndSearchByVector() {
        String catId = UUID.randomUUID().toString();
        String kittenId = UUID.randomUUID().toString();
        String carId = UUID.randomUUID().toString();

        Message upserted = fluentTemplate.to("direct:upsert")
                .withBody(List.of(CAT, KITTEN, CAR))
                .withHeader("CamelSpringAiEmbeddingInputTexts", List.of("A cat", "A kitten", "A car"))
                .withHeader(SpringAiVectorStoreHeaders.UPSERT_DOCUMENT_IDS, List.of(catId, kittenId, carId))
                .request(Message.class);

        assertThat(upserted.getHeader(SpringAiVectorStoreHeaders.DOCUMENTS_ADDED, Integer.class)).isEqualTo(3);
        assertThat(upserted.getHeader(SpringAiVectorStoreHeaders.DOCUMENT_IDS, List.class))
                .containsExactly(catId, kittenId, carId);

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Message found = fluentTemplate.to("direct:searchByVector")
                            .withBody(new float[] { 1f, 0.05f, 0f })
                            .request(Message.class);

                    List<Document> documents = found.getBody(List.class);
                    assertThat(documents).extracting(Document::getId).containsExactly(catId, kittenId);
                    assertThat(documents).extracting(Document::getText).containsExactly("A cat", "A kitten");
                    // UPSERT does not write the embedding and precomputed_embedding metadata of ADD
                    assertThat(documents.get(0).getMetadata()).doesNotContainKeys("embedding", "precomputed_embedding");
                });
    }

    @Test
    public void testUpsertReplacesDocumentWithSameId() throws Exception {
        String id = UUID.randomUUID().toString();

        fluentTemplate.to("direct:upsert")
                .withBody(CAT)
                .withHeader("CamelSpringAiEmbeddingInputText", "Old text")
                .withHeader(SpringAiVectorStoreHeaders.UPSERT_DOCUMENT_ID, id)
                .send();
        fluentTemplate.to("direct:upsert")
                .withBody(CAR)
                .withHeader("CamelSpringAiEmbeddingInputText", "New text")
                .withHeader(SpringAiVectorStoreHeaders.UPSERT_DOCUMENT_ID, id)
                .send();

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThat(qdrantClient.countAsync(collectionName).get()).isEqualTo(1L);

                    Message found = fluentTemplate.to("direct:searchByVector")
                            .withBody(CAR)
                            .request(Message.class);
                    List<Document> documents = found.getBody(List.class);
                    assertThat(documents).extracting(Document::getId).containsExactly(id);
                    assertThat(documents).extracting(Document::getText).containsExactly("New text");
                });
    }

    @Test
    public void testUpsertEmbeddedDocumentsAndSearchByVectorWithFilter() {
        String camelId = UUID.randomUUID().toString();
        String otherId = UUID.randomUUID().toString();

        fluentTemplate.to("direct:upsert")
                .withBody(List.of(
                        new EmbeddedDocument(new Document(camelId, "A cat from Camel", Map.of("source", "camel")), CAT),
                        new EmbeddedDocument(
                                new Document(otherId, "A kitten from elsewhere", Map.of("source", "other")),
                                KITTEN)))
                .send();

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Message found = fluentTemplate.to("direct:searchByVector")
                            .withBody(KITTEN)
                            .withHeader(SpringAiVectorStoreHeaders.FILTER_EXPRESSION, "source == 'camel'")
                            .request(Message.class);
                    assertThat(found.getHeader(SpringAiVectorStoreHeaders.DOCUMENT_IDS, List.class))
                            .containsExactly(camelId);
                });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.getRegistry().bind("vectorStore", vectorStore);

                from("direct:upsert")
                        .to("spring-ai-vector-store:test?vectorStore=#vectorStore&operation=UPSERT");

                from("direct:searchByVector")
                        .to("spring-ai-vector-store:test?vectorStore=#vectorStore&operation=SIMILARITY_SEARCH"
                            + "&searchByVector=true&topK=2");
            }
        };
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
}
