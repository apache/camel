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

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

public class SpringAiVectorStoreProducer extends DefaultProducer {
    public SpringAiVectorStoreProducer(SpringAiVectorStoreEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public SpringAiVectorStoreEndpoint getEndpoint() {
        return (SpringAiVectorStoreEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final Message message = exchange.getMessage();
        final VectorStore vectorStore = getEndpoint().getConfiguration().getVectorStore();

        // Determine operation from header or configuration
        SpringAiVectorStoreOperation operation = message.getHeader(
                SpringAiVectorStoreHeaders.OPERATION,
                getEndpoint().getConfiguration().getOperation(),
                SpringAiVectorStoreOperation.class);

        switch (operation) {
            case ADD:
                processAdd(exchange, vectorStore);
                break;
            case DELETE:
                processDelete(exchange, vectorStore);
                break;
            case SIMILARITY_SEARCH:
                processSimilaritySearch(exchange, vectorStore);
                break;
            default:
                throw new IllegalArgumentException("Unknown operation: " + operation);
        }
    }

    private void processAdd(Exchange exchange, VectorStore vectorStore) {
        final Message message = exchange.getMessage();
        List<Document> documents = new ArrayList<>();

        // Check if the message contains text and embedding from the embeddings component
        String inputText = message.getHeader("CamelSpringAiEmbeddingInputText", String.class);
        Object body = message.getBody();

        if (inputText != null && body instanceof float[]) {
            // Create document from embedding component output with pre-computed embedding
            Document doc = createDocumentWithEmbedding(inputText, (float[]) body);
            documents.add(doc);
        } else if (inputText != null) {
            // Create document from embedding component output (text only, vector store will embed)
            Document doc = new Document(inputText);
            documents.add(doc);
        } else {
            // Check for batch texts and embeddings
            List<String> inputTexts = message.getHeader("CamelSpringAiEmbeddingInputTexts", List.class);

            if (inputTexts != null && !inputTexts.isEmpty() && body instanceof List) {
                List<?> bodyList = (List<?>) body;
                // Check if body contains float arrays (pre-computed embeddings)
                if (!bodyList.isEmpty() && bodyList.get(0) instanceof float[]) {
                    // Create documents with pre-computed embeddings
                    List<float[]> embeddings = (List<float[]>) bodyList;
                    for (int i = 0; i < inputTexts.size() && i < embeddings.size(); i++) {
                        Document doc = createDocumentWithEmbedding(inputTexts.get(i), embeddings.get(i));
                        documents.add(doc);
                    }
                } else {
                    // Create documents without embeddings
                    for (String text : inputTexts) {
                        Document doc = new Document(text);
                        documents.add(doc);
                    }
                }
            } else if (inputTexts != null && !inputTexts.isEmpty()) {
                // Create documents from texts only
                for (String text : inputTexts) {
                    Document doc = new Document(text);
                    documents.add(doc);
                }
            } else {
                // Try to get documents directly from body
                if (body instanceof Document) {
                    documents.add((Document) body);
                } else if (body instanceof List) {
                    List<?> list = (List<?>) body;
                    for (Object item : list) {
                        if (item instanceof Document) {
                            documents.add((Document) item);
                        } else if (item instanceof String) {
                            // Create document from text (vector store will generate embeddings)
                            documents.add(new Document((String) item));
                        }
                    }
                } else if (body instanceof String) {
                    // Create document from text
                    documents.add(new Document((String) body));
                } else {
                    throw new IllegalArgumentException(
                            "Message body must be a Document, List<Document>, String, List<String>, " +
                                                       "float[], or List<float[]>, or embeddings must be present in headers");
                }
            }
        }

        // Add documents to vector store
        vectorStore.add(documents);
        message.setHeader(SpringAiVectorStoreHeaders.DOCUMENTS_ADDED, documents.size());

        // Set document IDs header
        List<String> documentIds = documents.stream()
                .map(Document::getId)
                .toList();
        message.setHeader(SpringAiVectorStoreHeaders.DOCUMENT_IDS, documentIds);
    }

    /**
     * Creates a Document with pre-computed embedding stored in metadata. The embedding is stored in metadata with a
     * special key that can be used by vector stores that support pre-computed embeddings.
     */
    private Document createDocumentWithEmbedding(String text, float[] embedding) {
        java.util.Map<String, Object> metadata = new java.util.HashMap<>();
        // Store the embedding in metadata with a standard key
        // Note: Most vector stores will ignore this and compute their own embeddings,
        // but custom implementations can use it
        metadata.put("embedding", embedding);
        metadata.put("precomputed_embedding", true);
        return new Document(text, metadata);
    }

    private void processDelete(Exchange exchange, VectorStore vectorStore) {
        final Message message = exchange.getMessage();

        // Get document IDs from header or body
        List<String> documentIds = message.getHeader(SpringAiVectorStoreHeaders.DOCUMENT_IDS, List.class);

        if (documentIds == null) {
            Object body = message.getBody();
            if (body instanceof List) {
                documentIds = (List<String>) body;
            } else if (body instanceof String) {
                documentIds = List.of((String) body);
            }
        }

        if (documentIds != null && !documentIds.isEmpty()) {
            vectorStore.delete(documentIds);
            message.setHeader(SpringAiVectorStoreHeaders.DOCUMENTS_DELETED, documentIds.size());
        } else {
            // Check for filter expression deletion
            String filterExpression = message.getHeader(
                    SpringAiVectorStoreHeaders.FILTER_EXPRESSION,
                    getEndpoint().getConfiguration().getFilterExpression(),
                    String.class);

            if (filterExpression != null) {
                // Delete by filter is not directly supported in the base VectorStore interface
                // It would require implementation-specific handling
                throw new UnsupportedOperationException(
                        "Delete by filter expression is not supported by all vector stores. " +
                                                        "Please use document IDs instead.");
            } else {
                throw new IllegalArgumentException(
                        "Either document IDs or filter expression must be provided for DELETE operation");
            }
        }
    }

    private void processSimilaritySearch(Exchange exchange, VectorStore vectorStore) {
        final Message message = exchange.getMessage();

        // Get search parameters
        String query = message.getBody(String.class);
        if (query == null || query.isEmpty()) {
            throw new IllegalArgumentException("Query text is required for SIMILARITY_SEARCH operation");
        }

        Integer topK = message.getHeader(
                SpringAiVectorStoreHeaders.TOP_K,
                getEndpoint().getConfiguration().getTopK(),
                Integer.class);

        Double similarityThreshold = message.getHeader(
                SpringAiVectorStoreHeaders.SIMILARITY_THRESHOLD,
                getEndpoint().getConfiguration().getSimilarityThreshold(),
                Double.class);

        String filterExpression = message.getHeader(
                SpringAiVectorStoreHeaders.FILTER_EXPRESSION,
                getEndpoint().getConfiguration().getFilterExpression(),
                String.class);

        // Build search request
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold);

        if (filterExpression != null && !filterExpression.isEmpty()) {
            builder.filterExpression(filterExpression);
        }

        SearchRequest searchRequest = builder.build();

        // Perform similarity search
        List<Document> similarDocuments = vectorStore.similaritySearch(searchRequest);

        // Set results in headers
        message.setHeader(SpringAiVectorStoreHeaders.SIMILAR_DOCUMENTS, similarDocuments);

        // Set document IDs header
        List<String> documentIds = similarDocuments.stream()
                .map(Document::getId)
                .toList();
        message.setHeader(SpringAiVectorStoreHeaders.DOCUMENT_IDS, documentIds);

        message.setBody(similarDocuments);
    }
}
