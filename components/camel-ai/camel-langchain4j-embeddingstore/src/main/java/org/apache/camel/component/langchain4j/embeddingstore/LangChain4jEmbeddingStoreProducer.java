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

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest.EmbeddingSearchRequestBuilder;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.component.langchain4j.embeddings.LangChain4jEmbeddingsHeaders;
import org.apache.camel.support.DefaultProducer;

/**
 * Producer for LangChain4j embedding store operations.
 *
 * <p>
 * Handles the actual processing of embedding store operations including ADD, REMOVE, and SEARCH. The producer supports
 * both direct embedding store instances and factory-based creation for dynamic store configuration.
 * </p>
 *
 * <p>
 * Operations are determined by the {@code CamelLangchain4jEmbeddingStoreAction} header and can include additional
 * parameters like max results, minimum score, and search filters.
 * </p>
 */
public class LangChain4jEmbeddingStoreProducer extends DefaultProducer {
    private ExecutorService executor;
    private EmbeddingStoreFactory embeddingStoreFactory;

    public LangChain4jEmbeddingStoreProducer(LangChain4jEmbeddingStoreEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public LangChain4jEmbeddingStoreEndpoint getEndpoint() {
        return (LangChain4jEmbeddingStoreEndpoint) super.getEndpoint();
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        embeddingStoreFactory = getEndpoint().getConfiguration().getEmbeddingStoreFactory();
        if (embeddingStoreFactory != null) {
            embeddingStoreFactory.setCamelContext(getEndpoint().getCamelContext());
            EmbeddingStore es = embeddingStoreFactory.createEmbeddingStore();
            getEndpoint().getConfiguration().setEmbeddingStore(es);
        }
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();

        // Get action from header, fallback to endpoint configuration
        LangChain4jEmbeddingStoreAction action
                = in.getHeader(LangChain4jEmbeddingStoreHeaders.ACTION, LangChain4jEmbeddingStoreAction.class);
        if (action == null) {
            action = getEndpoint().getConfiguration().getAction();
        }

        try {
            if (action == null) {
                throw new NoSuchHeaderException(
                        "The action is a required header or endpoint property", exchange,
                        LangChain4jEmbeddingStoreHeaders.ACTION);
            }

            switch (action) {
                case ADD:
                    add(exchange);
                    break;
                case REMOVE:
                    remove(exchange);
                    break;
                case SEARCH:
                    search(exchange);
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported action: " + action.name());
            }
        } catch (Exception e) {
            exchange.setException(e);
        }
    }

    /**
     * Adds embeddings to the store with optional text segments and caller-supplied IDs.
     *
     * <p>
     * Supports both single and batch operations:
     * </p>
     *
     * <p>
     * <b>Single operation</b> - when the {@code CamelLangChain4jEmbeddingsEmbedding} header contains a single
     * {@link Embedding}:
     * </p>
     * <ul>
     * <li>With caller-supplied ID header ({@code CamelLangchain4jEmbeddingStoreEmbeddingId}) and text segment: calls
     * {@code addAll(singletonList(id), singletonList(embedding), singletonList(textSegment))}</li>
     * <li>With caller-supplied ID header but no text segment: calls {@code add(id, embedding)}</li>
     * <li>With text segment header: calls {@code add(embedding, textSegment)}</li>
     * <li>Without text segment: calls {@code add(embedding)}</li>
     * </ul>
     *
     * <p>
     * <b>Batch operation</b> - when the {@code CamelLangChain4jEmbeddingsEmbeddings} header contains a
     * {@code List<Embedding>}:
     * </p>
     * <ul>
     * <li>With IDs header ({@code CamelLangchain4jEmbeddingStoreEmbeddingIds}) and text segments: calls
     * {@code addAll(ids, embeddings, textSegments)}</li>
     * <li>With IDs header but no text segments: loops with {@code add(id, embedding)}</li>
     * <li>With text segments: calls {@code addAll(embeddings, textSegments)}</li>
     * <li>Without text segments: calls {@code addAll(embeddings)}</li>
     * </ul>
     *
     * @param  exchange  the Camel exchange containing the embedding data
     * @throws Exception if the add operation fails
     */
    @SuppressWarnings("unchecked")
    private void add(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        LangChain4jEmbeddingStoreConfiguration config = getEndpoint().getConfiguration();
        EmbeddingStore<TextSegment> store = config.getEmbeddingStore();

        // Check for batch embeddings header first
        List<Embedding> embeddings = in.getHeader(LangChain4jEmbeddingsHeaders.EMBEDDINGS, List.class);
        if (embeddings != null) {
            addBatch(in, store, embeddings);
            return;
        }

        // Single embedding path — use resolveEmbedding for auto-embedding support
        EmbeddingResult resolved = resolveEmbedding(exchange);

        // Check for caller-supplied ID
        String callerId = in.getHeader(LangChain4jEmbeddingStoreHeaders.EMBEDDING_ID, String.class);
        String id;

        if (callerId != null) {
            if (resolved.textSegment() != null) {
                // Use addAll with singleton lists to preserve both ID and text segment
                store.addAll(List.of(callerId), List.of(resolved.embedding()), List.of(resolved.textSegment()));
            } else {
                store.add(callerId, resolved.embedding());
            }
            id = callerId;
        } else if (resolved.textSegment() != null) {
            id = store.add(resolved.embedding(), resolved.textSegment());
        } else {
            id = store.add(resolved.embedding());
        }

        in.setBody(id);
    }

    @SuppressWarnings("unchecked")
    private void addBatch(Message in, EmbeddingStore<TextSegment> store, List<Embedding> embeddings) {
        List<String> callerIds = in.getHeader(LangChain4jEmbeddingStoreHeaders.EMBEDDING_IDS, List.class);

        // Resolve text segments from header (set by batch embeddings producer) or body
        List<TextSegment> textSegments = in.getHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENTS, List.class);
        if (textSegments == null) {
            Object body = in.getBody();
            if (body instanceof List && !((List<?>) body).isEmpty() && ((List<?>) body).get(0) instanceof TextSegment) {
                textSegments = (List<TextSegment>) body;
            }
        }

        // Validate sizes match when caller IDs are provided
        if (callerIds != null && callerIds.size() != embeddings.size()) {
            throw new IllegalArgumentException(
                    "EMBEDDING_IDS size (" + callerIds.size() + ") must match EMBEDDINGS size (" + embeddings.size() + ")");
        }
        if (textSegments != null && textSegments.size() != embeddings.size()) {
            throw new IllegalArgumentException(
                    "TEXT_SEGMENTS size (" + textSegments.size() + ") must match EMBEDDINGS size (" + embeddings.size() + ")");
        }

        List<String> ids;
        if (callerIds != null && textSegments != null) {
            store.addAll(callerIds, embeddings, textSegments);
            ids = callerIds;
        } else if (callerIds != null) {
            // No addAll(ids, embeddings) overload in langchain4j, so loop with add(id, embedding)
            for (int i = 0; i < embeddings.size(); i++) {
                store.add(callerIds.get(i), embeddings.get(i));
            }
            ids = callerIds;
        } else if (textSegments != null) {
            ids = store.addAll(embeddings, textSegments);
        } else {
            ids = store.addAll(embeddings);
        }

        in.setBody(ids);
    }

    /**
     * Removes embeddings from the store. Supports multiple removal strategies:
     *
     * <ul>
     * <li><b>By filter</b>: when the {@code CamelLangchain4jEmbeddingStoreFilter} header is set, removes all embeddings
     * matching the filter via {@code removeAll(Filter)}</li>
     * <li><b>By ID list</b>: when the body is a {@code Collection<String>}, removes all specified embeddings via
     * {@code removeAll(Collection)}</li>
     * <li><b>By single ID</b>: when the body is a single {@code String}, removes that embedding via
     * {@code remove(id)}</li>
     * </ul>
     *
     * @param  exchange  the Camel exchange containing removal parameters
     * @throws Exception if the remove operation fails
     */
    @SuppressWarnings("unchecked")
    private void remove(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        EmbeddingStore<TextSegment> store = getEndpoint().getConfiguration().getEmbeddingStore();

        // Check for filter-based removal first
        Filter filter = in.getHeader(LangChain4jEmbeddingStoreHeaders.FILTER, Filter.class);
        if (filter != null) {
            store.removeAll(filter);
            return;
        }

        Object body = in.getBody();

        // Batch removal by collection of IDs
        if (body instanceof Collection) {
            Collection<String> ids = (Collection<String>) body;
            if (ids.isEmpty()) {
                throw new IllegalArgumentException(
                        "REMOVE action requires a non-empty Collection<String> body for batch ID removal");
            }
            store.removeAll(ids);
            return;
        }

        // Single ID removal
        String id = in.getBody(String.class);
        if (id != null && !id.isEmpty()) {
            store.remove(id);
            return;
        }

        throw new IllegalArgumentException(
                "REMOVE action requires either: a String body (single ID), a Collection<String> body (batch IDs), "
                                           + "or a CamelLangchain4jEmbeddingStoreFilter header (filter-based removal)");
    }

    /**
     * Performs similarity search in the embedding store.
     *
     * <p>
     * Expects the following headers:
     * </p>
     * <ul>
     * <li>{@code CamelLangchain4jEmbeddingEmbedding} - Query embedding vector (required)</li>
     * <li>{@code CamelLangchain4jEmbeddingStoreMaxResults} - Maximum results to return (optional, default: 5)</li>
     * <li>{@code CamelLangchain4jEmbeddingStoreMinScore} - Minimum similarity score threshold (optional)</li>
     * <li>{@code CamelLangchain4jEmbeddingStoreFilter} - Search filter for metadata (optional)</li>
     * </ul>
     *
     * <p>
     * Returns a List of {@link EmbeddingMatch} objects in the message body, ordered by similarity score.
     * </p>
     *
     * @param  exchange  the Camel exchange containing the search parameters
     * @throws Exception if the search operation fails
     */
    private void search(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        LangChain4jEmbeddingStoreConfiguration config = getEndpoint().getConfiguration();

        Embedding embedding = resolveEmbedding(exchange).embedding();

        // Get maxResults from header, fallback to endpoint config
        Integer maxResults = in.getHeader(LangChain4jEmbeddingStoreHeaders.MAX_RESULTS, Integer.class);
        if (maxResults == null) {
            maxResults = config.getMaxResults();
        }

        EmbeddingSearchRequestBuilder esrb = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(maxResults);

        // Get minScore from header, fallback to endpoint config
        Double minScore = in.getHeader(LangChain4jEmbeddingStoreHeaders.MIN_SCORE, Double.class);
        if (minScore == null) {
            minScore = config.getMinScore();
        }
        if (minScore != null) {
            esrb = esrb.minScore(minScore);
        }

        Filter filter = in.getHeader(LangChain4jEmbeddingStoreHeaders.FILTER, Filter.class);
        if (filter != null) {
            esrb = esrb.filter(filter);
        }

        EmbeddingSearchRequest embeddingSearchRequest = esrb.build();
        List<EmbeddingMatch<TextSegment>> matches
                = config.getEmbeddingStore().search(embeddingSearchRequest).matches();

        Message out = exchange.getMessage();

        // Return text content if configured
        if (config.isReturnTextContent()) {
            List<String> texts = matches.stream()
                    .filter(m -> m.embedded() != null)
                    .map(m -> m.embedded().text())
                    .collect(Collectors.toList());
            out.setBody(texts);
        } else {
            out.setBody(matches);
        }
    }

    private EmbeddingResult resolveEmbedding(Exchange exchange) throws NoSuchHeaderException {
        final Message in = exchange.getMessage();
        LangChain4jEmbeddingStoreConfiguration config = getEndpoint().getConfiguration();

        Embedding embedding = in.getHeader(LangChain4jEmbeddingsHeaders.EMBEDDING, Embedding.class);
        TextSegment textSegment = in.getHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENT, TextSegment.class);

        if (embedding == null && config.getEmbeddingModel() != null) {
            String text = in.getBody(String.class);
            if (text == null) {
                throw new IllegalArgumentException(
                        "Message body cannot be converted to String for auto-embedding. "
                                                   + "Either set the body to a text value or provide a pre-computed embedding via the "
                                                   + LangChain4jEmbeddingsHeaders.EMBEDDING + " header.");
            }
            if (textSegment == null) {
                textSegment = TextSegment.from(text);
            }
            embedding = config.getEmbeddingModel().embed(textSegment).content();
        }

        if (embedding == null) {
            throw new NoSuchHeaderException(
                    "The embedding is a required header (or configure embeddingModel for auto-embedding)",
                    exchange,
                    LangChain4jEmbeddingsHeaders.EMBEDDING);
        }

        return new EmbeddingResult(embedding, textSegment);
    }

    private record EmbeddingResult(Embedding embedding, TextSegment textSegment) {
    }

    private CamelContext getCamelContext() {
        return getEndpoint().getCamelContext();
    }
}
