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
import java.util.concurrent.TimeUnit;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.ollama.services.OllamaService;
import org.apache.camel.test.infra.ollama.services.OllamaServiceConfiguration;
import org.apache.camel.test.infra.ollama.services.OllamaServiceFactory;
import org.apache.camel.test.infra.qdrant.services.QdrantService;
import org.apache.camel.test.infra.qdrant.services.QdrantServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Spring AI Vector Store component using Qdrant and Ollama.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Disabled unless running in CI")
public class QdrantVectorStoreIT extends CamelTestSupport {

    private static Collections.Distance distance = Collections.Distance.Cosine;
    private static int dimension = 768;

    @RegisterExtension
    static QdrantService QDRANT = QdrantServiceFactory.createSingletonService();

    @RegisterExtension
    static OllamaService OLLAMA = OllamaServiceFactory.createSingletonServiceWithConfiguration(
            new OllamaServiceConfiguration() {
                @Override
                public String modelName() {
                    return "embeddinggemma:300m";
                }

                @Override
                public String apiKey() {
                    return "";
                }
            });

    private EmbeddingModel embeddingModel;
    private VectorStore vectorStore;
    private QdrantClient qdrantClient;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        String collectionName = "test_collection_" + System.currentTimeMillis();
        QdrantClient client
                = new QdrantClient(QdrantGrpcClient.newBuilder(QDRANT.getGrpcHost(), QDRANT.getGrpcPort(), false).build());
        client.createCollectionAsync(collectionName,
                Collections.VectorParams.newBuilder().setDistance(distance).setSize(dimension).build()).get();

        // Create Ollama embedding model
        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(OLLAMA.baseUrl())
                .build();

        OllamaEmbeddingOptions ollamaOptions = OllamaEmbeddingOptions.builder()
                .model(OLLAMA.modelName())
                .build();

        embeddingModel = OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(ollamaOptions)
                .build();

        // Create Qdrant client
        qdrantClient = new QdrantClient(
                QdrantGrpcClient.newBuilder(
                        QDRANT.getGrpcHost(),
                        QDRANT.getGrpcPort(),
                        false).build());

        // Create Qdrant vector store with Ollama embeddings
        vectorStore = QdrantVectorStore.builder(qdrantClient, embeddingModel)
                .collectionName(collectionName)
                .initializeSchema(true)
                .build();

        // Wait for Qdrant to initialize the collection
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .pollDelay(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    try {
                        List<String> collections = qdrantClient.listCollectionsAsync().get();
                        return collections.contains(collectionName);
                    } catch (Exception e) {
                        return false;
                    }
                });
    }

    @Override
    protected void cleanupResources() throws Exception {
        if (qdrantClient != null) {
            qdrantClient.close();
        }
        super.cleanupResources();
    }

    @Test
    public void testAdd() throws Exception {
        // Send text through the pipeline: embeddings -> vector store
        template.sendBody("direct:addToStore", "Apache Camel is an integration framework");

        // Wait for Qdrant to index the document
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    List<Document> results = vectorStore.similaritySearch(
                            SearchRequest.builder()
                                    .query("integration framework")
                                    .topK(5)
                                    .build());
                    assertThat(results).isNotEmpty();
                    assertThat(results.get(0).getText()).contains("Camel");
                });
    }

    @Test
    public void testBatchAdd() throws Exception {
        // Send multiple texts through the pipeline
        template.sendBody("direct:addToStore", "Apache Camel is an integration framework");
        template.sendBody("direct:addToStore", "Spring AI provides artificial intelligence capabilities");
        template.sendBody("direct:addToStore", "Qdrant is a vector database");

        // Wait for Qdrant to index all documents
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    // Search for integration-related documents
                    List<Document> integrationResults = vectorStore.similaritySearch(
                            SearchRequest.builder()
                                    .query("integration tools")
                                    .topK(5)
                                    .build());

                    assertThat(integrationResults).isNotEmpty();

                    // Search for AI-related documents
                    List<Document> aiResults = vectorStore.similaritySearch(
                            SearchRequest.builder()
                                    .query("artificial intelligence")
                                    .topK(5)
                                    .build());

                    assertThat(aiResults).isNotEmpty();

                    // Search for database-related documents
                    List<Document> dbResults = vectorStore.similaritySearch(
                            SearchRequest.builder()
                                    .query("database systems")
                                    .topK(5)
                                    .build());

                    assertThat(dbResults).isNotEmpty();
                });
    }

    @Test
    public void testSimilaritySearchOperation() throws Exception {
        // First, add some documents directly
        template.sendBody("direct:addToStore", "The cat sat on the mat");
        template.sendBody("direct:addToStore", "The dog played in the park");
        template.sendBody("direct:addToStore", "Machine learning is a subset of AI");

        // Wait for Qdrant to index the documents
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    // Use the vector store component to perform similarity search
                    List<Document> results = template.requestBody(
                            "direct:search",
                            "feline animals",
                            List.class);

                    assertThat(results).isNotEmpty();
                    // The "cat" document should be more relevant to "feline animals"
                    boolean foundCat = results.stream()
                            .anyMatch(doc -> doc.getText().toLowerCase().contains("cat"));
                    assertThat(foundCat).isTrue();
                });
    }

    @Test
    public void testAddDocumentDirectly() throws Exception {
        // Test adding a document using the vector store component directly
        String text = "Vector databases enable semantic search";

        var exchange = template.request("direct:addToStore", e -> {
            e.getIn().setBody(text);
        });

        Integer documentsAdded = exchange.getMessage().getHeader(
                SpringAiVectorStoreHeaders.DOCUMENTS_ADDED,
                Integer.class);

        assertThat(documentsAdded).isEqualTo(1);

        // Wait for Qdrant to index the document
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    // Verify the document was added
                    List<Document> results = vectorStore.similaritySearch(
                            SearchRequest.builder()
                                    .query("semantic search")
                                    .topK(5)
                                    .build());

                    assertThat(results).isNotEmpty();
                    boolean foundDocument = results.stream()
                            .anyMatch(doc -> doc.getText().contains("semantic search"));
                    assertThat(foundDocument).isTrue();
                });
    }

    @Test
    public void testSimilaritySearchWithTopKAndThreshold() throws Exception {
        // Add multiple documents
        template.sendBody("direct:addToStore", "What is artificial intelligence and machine learning");
        template.sendBody("direct:addToStore", "The cat sat on the mat");
        template.sendBody("direct:addToStore", "AI is transforming technology");
        template.sendBody("direct:addToStore", "The dog played in the park");
        template.sendBody("direct:addToStore", "Deep learning neural networks");

        // Wait for Qdrant to index the documents
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    // Perform similarity search with topK and similarityThreshold
                    var exchange = template.request("direct:searchWithParams", e -> {
                        e.getIn().setBody("What is AI?");
                    });

                    List<Document> results = exchange.getMessage().getBody(List.class);
                    assertThat(results).isNotNull();
                    assertThat(results).isNotEmpty();

                    // Verify header contains similar documents
                    List<Document> similarDocuments = exchange.getMessage().getHeader(
                            SpringAiVectorStoreHeaders.SIMILAR_DOCUMENTS,
                            List.class);
                    assertThat(similarDocuments).isNotNull();
                    assertThat(similarDocuments).isEqualTo(results);

                    // Results should be limited by topK
                    assertThat(results.size()).isLessThanOrEqualTo(5);

                    // At least one result should be AI-related
                    boolean foundAIRelated = results.stream()
                            .anyMatch(doc -> doc.getText().toLowerCase().contains("ai")
                                    || doc.getText().toLowerCase().contains("intelligence"));
                    assertThat(foundAIRelated).isTrue();
                });
    }

    @Test
    public void testDeleteDocuments() throws Exception {
        // Add some documents with known IDs
        template.sendBody("direct:addToStore", "Document to be deleted 1");
        template.sendBody("direct:addToStore", "Document to be deleted 2");
        template.sendBody("direct:addToStore", "Document to keep");

        // Wait for Qdrant to index the documents
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    List<Document> results = vectorStore.similaritySearch(
                            SearchRequest.builder()
                                    .query("deleted")
                                    .topK(10)
                                    .build());
                    assertThat(results).hasSizeGreaterThanOrEqualTo(2);
                });

        // Get the document IDs for the first two documents
        List<Document> docsToDelete = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("deleted")
                        .topK(2)
                        .build());

        List<String> idsToDelete = docsToDelete.stream()
                .map(Document::getId)
                .toList();

        // Delete documents using the component
        var exchange = template.request("direct:delete", e -> {
            e.getIn().setHeader(SpringAiVectorStoreHeaders.DOCUMENT_IDS, idsToDelete);
        });

        Integer documentsDeleted = exchange.getMessage().getHeader(
                SpringAiVectorStoreHeaders.DOCUMENTS_DELETED,
                Integer.class);
        assertThat(documentsDeleted).isEqualTo(2);

        // Wait and verify documents were deleted
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    List<Document> afterDeleteResults = vectorStore.similaritySearch(
                            SearchRequest.builder()
                                    .query("deleted keep")
                                    .topK(10)
                                    .build());

                    // Deleted documents should not be present
                    boolean hasDeletedDocs = afterDeleteResults.stream()
                            .anyMatch(doc -> idsToDelete.contains(doc.getId()));
                    assertThat(hasDeletedDocs).isFalse();
                });
    }

    @Test
    public void testDeleteDocumentsWithBodyAsList() throws Exception {
        // Add documents
        template.sendBody("direct:addToStore", "Document A for list deletion");
        template.sendBody("direct:addToStore", "Document B for list deletion");

        // Wait for indexing
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    List<Document> results = vectorStore.similaritySearch(
                            SearchRequest.builder()
                                    .query("list deletion")
                                    .topK(10)
                                    .build());
                    assertThat(results).hasSizeGreaterThanOrEqualTo(2);
                });

        // Get document IDs
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("list deletion")
                        .topK(2)
                        .build());

        List<String> idsToDelete = docs.stream()
                .map(Document::getId)
                .toList();

        // Delete using body as list of IDs
        var exchange = template.request("direct:delete", e -> {
            e.getIn().setBody(idsToDelete);
        });

        Integer documentsDeleted = exchange.getMessage().getHeader(
                SpringAiVectorStoreHeaders.DOCUMENTS_DELETED,
                Integer.class);
        assertThat(documentsDeleted).isEqualTo(2);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Register the models in the registry
                context.getRegistry().bind("embeddingModel", embeddingModel);
                context.getRegistry().bind("vectorStore", vectorStore);

                // Route for similarity search
                from("direct:search")
                        .log("direct:search")
                        .to("spring-ai-vector-store:test?vectorStore=#vectorStore&operation=SIMILARITY_SEARCH&topK=5");

                // Route for similarity search with parameters
                from("direct:searchWithParams")
                        .log("direct:searchWithParams")
                        .to("spring-ai-vector-store:test?vectorStore=#vectorStore&operation=SIMILARITY_SEARCH&topK=5&similarityThreshold=0.7")
                        .log("Found ${header.CamelSpringAiVectorStoreSimilarDocuments.size()} similar documents");

                // Route for adding documents directly to vector store
                from("direct:addToStore")
                        .log("direct:addToStore")
                        .to("spring-ai-vector-store:test?vectorStore=#vectorStore&operation=ADD");

                // Route for deleting documents
                from("direct:delete")
                        .log("direct:delete")
                        .to("spring-ai-vector-store:test?vectorStore=#vectorStore&operation=DELETE");
            }
        };
    }
}
