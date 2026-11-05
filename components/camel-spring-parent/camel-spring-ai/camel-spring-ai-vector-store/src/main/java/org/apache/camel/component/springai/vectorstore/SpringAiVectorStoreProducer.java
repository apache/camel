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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

public class SpringAiVectorStoreProducer extends DefaultProducer {

    // The headers of the spring-ai-embeddings component with the text of the embedding(s) in the body
    private static final String INPUT_TEXT = "CamelSpringAiEmbeddingInputText";
    private static final String INPUT_TEXTS = "CamelSpringAiEmbeddingInputTexts";

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
        Object body = message.getBody();

        // The input text headers of the embeddings component belong to the vector(s) in the body: a leftover
        // header must not replace a Document or text body
        if (body instanceof float[] floats) {
            String inputText = message.getHeader(INPUT_TEXT, String.class);
            if (inputText == null) {
                throw new IllegalArgumentException(
                        "ADD of a float[] body needs the " + INPUT_TEXT + " header with the text of the embedding");
            }
            // Create document from embedding component output with pre-computed embedding
            documents.add(createDocumentWithEmbedding(inputText, floats));
        } else if (body instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof float[]) {
            List<String> inputTexts = message.getHeader(INPUT_TEXTS, List.class);
            if (inputTexts == null || inputTexts.size() != list.size()) {
                throw new IllegalArgumentException(
                        "ADD of a List of float[] body needs the " + INPUT_TEXTS
                                                   + " header with one text per embedding, got "
                                                   + (inputTexts == null ? "no header" : inputTexts.size() + " texts")
                                                   + " for " + list.size() + " embeddings");
            }
            // Create documents with pre-computed embeddings
            for (int i = 0; i < list.size(); i++) {
                if (!(list.get(i) instanceof float[] floats)) {
                    throw new IllegalArgumentException(
                            "ADD of a List of float[] body has an element of type " + typeName(list.get(i))
                                                       + " at index " + i);
                }
                documents.add(createDocumentWithEmbedding(inputTexts.get(i), floats));
            }
        } else if (body instanceof Document doc) {
            documents.add(doc);
        } else if (body instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Document doc) {
                    documents.add(doc);
                } else if (item instanceof String str) {
                    // Create document from text (vector store will generate embeddings)
                    documents.add(new Document(str));
                } else {
                    throw new IllegalArgumentException(
                            "ADD of a List body takes Document or String elements, not " + typeName(item));
                }
            }
        } else {
            String str = message.getBody(String.class);
            if (str != null) {
                documents.add(new Document(str));
            } else {
                throw new IllegalArgumentException(
                        "Message body must be a Document, List<Document>, String, List<String>, " +
                                                   "float[], or List<float[]>, or embeddings must be present in headers");
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

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }

    /**
     * Creates a Document with pre-computed embedding stored in metadata. The embedding is stored in metadata with a
     * special key that can be used by vector stores that support pre-computed embeddings.
     */
    private Document createDocumentWithEmbedding(String text, float[] embedding) {
        Map<String, Object> metadata = new HashMap<>();
        // Store the embedding in metadata with a standard key
        // Note: Most vector stores will ignore this and compute their own embeddings,
        // but custom implementations can use it
        metadata.put("embedding", embedding);
        metadata.put("precomputed_embedding", true);
        return new Document(text, metadata);
    }

    private void processDelete(Exchange exchange, VectorStore vectorStore) {
        final Message message = exchange.getMessage();

        // Get document IDs from the body, else from the header. The body comes first, as ADD and SIMILARITY_SEARCH
        // set the header to their own output ids
        List<String> documentIds = documentIdsFromBody(message);
        if (documentIds.isEmpty()) {
            documentIds = message.getHeader(SpringAiVectorStoreHeaders.DOCUMENT_IDS, List.class);
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

    /**
     * The ids of the documents to delete in the body: an id, a Document, or a List of ids or Documents (such as the
     * result of SIMILARITY_SEARCH).
     */
    private static List<String> documentIdsFromBody(Message message) {
        Object body = message.getBody();
        if (body == null) {
            return List.of();
        }
        if (body instanceof Document doc) {
            return List.of(doc.getId());
        }
        if (body instanceof List<?> list) {
            List<String> ids = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof Document doc) {
                    ids.add(doc.getId());
                } else if (item instanceof String str) {
                    ids.add(str);
                } else {
                    throw new IllegalArgumentException(
                            "DELETE of a List body takes String ids or Documents, not " + typeName(item));
                }
            }
            return ids;
        }
        String str = message.getBody(String.class);
        return str == null || str.isEmpty() ? List.of() : List.of(str);
    }

    private void processSimilaritySearch(Exchange exchange, VectorStore vectorStore) {
        final Message message = exchange.getMessage();

        // Get search parameters. After spring-ai-embeddings the body is the vector of the text, so search for its text
        String query;
        if (message.getBody() instanceof float[]) {
            query = message.getHeader(INPUT_TEXT, String.class);
            if (query == null) {
                throw new IllegalArgumentException(
                        "SIMILARITY_SEARCH of a float[] body needs the " + INPUT_TEXT + " header with the query text");
            }
        } else {
            query = message.getBody(String.class);
        }
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
