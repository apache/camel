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
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import static org.assertj.core.api.Assertions.assertThat;

public class SpringAiVectorStoreComponentTest extends CamelTestSupport {

    private SimpleVectorStore vectorStore;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        // Create a simple mock embedding model for testing
        EmbeddingModel embeddingModel = new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
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

        // Create SimpleVectorStore with the mock embedding model
        vectorStore = SimpleVectorStore.builder(embeddingModel).build();

        SpringAiVectorStoreComponent component
                = context.getComponent(SpringAiVectorStore.SCHEME, SpringAiVectorStoreComponent.class);
        component.getConfiguration().setVectorStore(vectorStore);

        return context;
    }

    @BeforeEach
    public void clearVectorStore() {
        if (vectorStore != null) {
            // SimpleVectorStore doesn't have a clear method, so we need to recreate it
            // For test isolation, we'll rely on the fact that each test method creates fresh documents
            // and we can query by ID or use delete operations if needed
        }
    }

    @Test
    public void testAddDocument() {
        String text = "This is a test document";

        Message result = fluentTemplate.to("spring-ai-vector-store:test?operation=ADD")
                .withBody(text)
                .request(Message.class);

        Integer documentsAdded = result.getHeader(SpringAiVectorStoreHeaders.DOCUMENTS_ADDED, Integer.class);
        assertThat(documentsAdded).isEqualTo(1);

        // Verify document IDs header is set
        List<String> documentIds = result.getHeader(SpringAiVectorStoreHeaders.DOCUMENT_IDS, List.class);
        assertThat(documentIds).isNotNull();
        assertThat(documentIds).hasSize(1);
        assertThat(documentIds.get(0)).isNotNull();

        // Verify document was added by performing a similarity search
        List<Document> searchResults
                = vectorStore.similaritySearch(SearchRequest.builder().query(text).topK(1).build());
        assertThat(searchResults).hasSize(1);
        assertThat(searchResults.get(0).getText()).isEqualTo(text);
        assertThat(searchResults.get(0).getId()).isEqualTo(documentIds.get(0));
    }

    @Test
    public void testAddFromEmbedding() {
        // Simulate output from embeddings component
        String inputText = "Hello world";

        Message result = fluentTemplate.to("spring-ai-vector-store:test?operation=ADD")
                .withBody(inputText)
                .withHeader("CamelSpringAiEmbeddingInputText", inputText)
                .request(Message.class);

        Integer documentsAdded = result.getHeader(SpringAiVectorStoreHeaders.DOCUMENTS_ADDED, Integer.class);
        assertThat(documentsAdded).isEqualTo(1);

        // Verify document was added by performing a similarity search
        List<Document> searchResults
                = vectorStore.similaritySearch(SearchRequest.builder().query(inputText).topK(1).build());
        assertThat(searchResults).hasSize(1);
        assertThat(searchResults.get(0).getText()).isEqualTo(inputText);
    }

    @Test
    public void testAddFromEmbeddingWithPrecomputedEmbedding() {
        // Simulate output from embeddings component with pre-computed embedding
        String inputText = "Hello world with precomputed embedding";
        float[] embedding = new float[] { 0.1f, 0.2f, 0.3f, 0.4f, 0.5f };

        Message result = fluentTemplate.to("spring-ai-vector-store:test?operation=ADD")
                .withBody(embedding)
                .withHeader("CamelSpringAiEmbeddingInputText", inputText)
                .request(Message.class);

        Integer documentsAdded = result.getHeader(SpringAiVectorStoreHeaders.DOCUMENTS_ADDED, Integer.class);
        assertThat(documentsAdded).isEqualTo(1);

        // Verify document was added by performing a similarity search
        List<Document> searchResults
                = vectorStore.similaritySearch(SearchRequest.builder().query(inputText).topK(1).build());
        assertThat(searchResults).hasSize(1);

        Document doc = searchResults.get(0);
        assertThat(doc.getText()).isEqualTo(inputText);
        assertThat(doc.getMetadata()).containsKey("embedding");
        assertThat(doc.getMetadata()).containsEntry("precomputed_embedding", true);

        float[] storedEmbedding = (float[]) doc.getMetadata().get("embedding");
        assertThat(storedEmbedding).isEqualTo(embedding);
    }

    @Test
    public void testAddFromEmbeddingWithMultiplePrecomputedEmbeddings() {
        // Simulate output from embeddings component with multiple pre-computed embeddings
        List<String> inputTexts = List.of("First batch document", "Second batch document");
        List<float[]> embeddings = List.of(
                new float[] { 0.1f, 0.2f, 0.3f },
                new float[] { 0.4f, 0.5f, 0.6f });

        Message result = fluentTemplate.to("spring-ai-vector-store:test?operation=ADD")
                .withBody(embeddings)
                .withHeader("CamelSpringAiEmbeddingInputTexts", inputTexts)
                .request(Message.class);

        Integer documentsAdded = result.getHeader(SpringAiVectorStoreHeaders.DOCUMENTS_ADDED, Integer.class);
        assertThat(documentsAdded).isEqualTo(2);

        // Verify documents were added by performing a similarity search that returns all documents
        List<Document> searchResults = vectorStore
                .similaritySearch(SearchRequest.builder().query("batch document").topK(10).build());
        assertThat(searchResults).hasSizeGreaterThanOrEqualTo(2);

        // Verify that both documents are present and have the correct metadata
        for (int i = 0; i < inputTexts.size(); i++) {
            final String expectedText = inputTexts.get(i);
            final float[] expectedEmbedding = embeddings.get(i);

            // Find the document that matches our text
            Document doc = searchResults.stream()
                    .filter(d -> d.getText().equals(expectedText))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Document not found: " + expectedText));

            assertThat(doc.getMetadata()).containsKey("embedding");
            assertThat(doc.getMetadata()).containsEntry("precomputed_embedding", true);

            float[] storedEmbedding = (float[]) doc.getMetadata().get("embedding");
            assertThat(storedEmbedding).isEqualTo(expectedEmbedding);
        }
    }

    @Test
    public void testSimilaritySearch() {
        // Add some documents first
        vectorStore.add(List.of(
                new Document("The cat sat on the mat"),
                new Document("The dog played in the park"),
                new Document("The bird flew in the sky")));

        Message result = fluentTemplate.to("spring-ai-vector-store:test?operation=SIMILARITY_SEARCH&topK=2")
                .withBody("cat")
                .request(Message.class);

        List<Document> results = result.getBody(List.class);
        assertThat(results).isNotNull();
        assertThat(results).hasSize(2);

        // Verify document IDs header is set
        List<String> documentIds = result.getHeader(SpringAiVectorStoreHeaders.DOCUMENT_IDS, List.class);
        assertThat(documentIds).isNotNull();
        assertThat(documentIds).hasSize(2);

        // Verify document IDs match the returned documents
        assertThat(documentIds.get(0)).isEqualTo(results.get(0).getId());
        assertThat(documentIds.get(1)).isEqualTo(results.get(1).getId());
    }

    @Test
    public void testSimilaritySearchWithTopKAndThreshold() {
        // Add some documents first
        vectorStore.add(List.of(
                new Document("What is artificial intelligence and machine learning"),
                new Document("The cat sat on the mat"),
                new Document("AI is transforming technology"),
                new Document("The dog played in the park"),
                new Document("Deep learning neural networks")));

        // Perform similarity search with topK and similarityThreshold
        Message result = fluentTemplate
                .to("spring-ai-vector-store:test?operation=SIMILARITY_SEARCH&topK=5&similarityThreshold=0.7")
                .withBody("What is AI?")
                .request(Message.class);

        List<Document> results = result.getBody(List.class);
        assertThat(results).isNotNull();
        assertThat(results).isNotEmpty();

        // Verify header contains similar documents
        List<Document> similarDocuments
                = result.getHeader(SpringAiVectorStoreHeaders.SIMILAR_DOCUMENTS, List.class);
        assertThat(similarDocuments).isNotNull();
        assertThat(similarDocuments).isEqualTo(results);

        // Verify document IDs header is set
        List<String> documentIds = result.getHeader(SpringAiVectorStoreHeaders.DOCUMENT_IDS, List.class);
        assertThat(documentIds).isNotNull();
        assertThat(documentIds).hasSize(results.size());

        // Verify document IDs match the returned documents
        for (int i = 0; i < results.size(); i++) {
            assertThat(documentIds.get(i)).isEqualTo(results.get(i).getId());
        }

        // Since our mock embeddings are all the same, we'll get results based on the topK
        assertThat(results.size()).isLessThanOrEqualTo(5);
    }

    @Test
    public void testDeleteDocuments() {
        // Add some documents first
        Document doc1 = new Document("doc-1", "First document", java.util.Map.of());
        Document doc2 = new Document("doc-2", "Second document", java.util.Map.of());
        Document doc3 = new Document("doc-3", "Third document", java.util.Map.of());

        vectorStore.add(List.of(doc1, doc2, doc3));

        // Verify documents were added
        List<Document> searchResults
                = vectorStore.similaritySearch(SearchRequest.builder().query("document").topK(10).build());
        assertThat(searchResults).hasSizeGreaterThanOrEqualTo(3);

        // Delete two documents using header
        Message result = fluentTemplate.to("spring-ai-vector-store:test?operation=DELETE")
                .withHeader(SpringAiVectorStoreHeaders.DOCUMENT_IDS, List.of("doc-1", "doc-2"))
                .request(Message.class);

        // Verify delete count header
        Integer documentsDeleted = result.getHeader(SpringAiVectorStoreHeaders.DOCUMENTS_DELETED, Integer.class);
        assertThat(documentsDeleted).isEqualTo(2);

        // Verify documents were deleted by searching again
        List<Document> afterDeleteResults
                = vectorStore.similaritySearch(SearchRequest.builder().query("document").topK(10).build());

        // Should not find the deleted documents
        assertThat(afterDeleteResults.stream().noneMatch(d -> d.getId().equals("doc-1"))).isTrue();
        assertThat(afterDeleteResults.stream().noneMatch(d -> d.getId().equals("doc-2"))).isTrue();

        // Should still find doc-3
        assertThat(afterDeleteResults.stream().anyMatch(d -> d.getId().equals("doc-3"))).isTrue();
    }

    @Test
    public void testDeleteDocumentsWithBodyAsList() {
        // Add some documents first
        Document doc1 = new Document("doc-10", "Document A", java.util.Map.of());
        Document doc2 = new Document("doc-20", "Document B", java.util.Map.of());

        vectorStore.add(List.of(doc1, doc2));

        // Delete using body as list of IDs
        Message result = fluentTemplate.to("spring-ai-vector-store:test?operation=DELETE")
                .withBody(List.of("doc-10", "doc-20"))
                .request(Message.class);

        // Verify delete count header
        Integer documentsDeleted = result.getHeader(SpringAiVectorStoreHeaders.DOCUMENTS_DELETED, Integer.class);
        assertThat(documentsDeleted).isEqualTo(2);
    }

    @Test
    public void testDeleteSingleDocumentWithBodyAsString() {
        // Add a document
        Document doc = new Document("doc-single", "Single document to delete", java.util.Map.of());
        vectorStore.add(List.of(doc));

        // Delete using body as single string ID
        Message result = fluentTemplate.to("spring-ai-vector-store:test?operation=DELETE")
                .withBody("doc-single")
                .request(Message.class);

        // Verify delete count header
        Integer documentsDeleted = result.getHeader(SpringAiVectorStoreHeaders.DOCUMENTS_DELETED, Integer.class);
        assertThat(documentsDeleted).isEqualTo(1);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Routes are created via fluent template
            }
        };
    }
}
