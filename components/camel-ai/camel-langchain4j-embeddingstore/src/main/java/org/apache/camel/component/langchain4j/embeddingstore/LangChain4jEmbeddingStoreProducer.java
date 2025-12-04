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
import java.util.concurrent.ExecutorService;

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

        final LangChain4jEmbeddingStoreAction action =
                in.getHeader(LangChain4jEmbeddingStoreHeaders.ACTION, LangChain4jEmbeddingStoreAction.class);

        try {
            if (action == null) {
                throw new NoSuchHeaderException(
                        "The action is a required header", exchange, LangChain4jEmbeddingStoreHeaders.ACTION);
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
     * Adds an embedding to the store with optional text segment.
     *
     * <p>
     * Expects the following headers:
     * </p>
     * <ul>
     * <li>{@code CamelLangchain4jEmbeddingEmbedding} - The embedding vector (required)</li>
     * <li>{@code CamelLangchain4jEmbeddingTextSegment} - Associated text segment (optional)</li>
     * </ul>
     *
     * <p>
     * Returns the generated embedding ID in the message body.
     * </p>
     *
     * @param  exchange  the Camel exchange containing the embedding data
     * @throws Exception if the add operation fails
     */
    private void add(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        Embedding embedding = null;
        TextSegment text = null;
        String id = null;

        if (in.getHeader(LangChain4jEmbeddingsHeaders.EMBEDDING) != null) {
            embedding = in.getHeader(LangChain4jEmbeddingsHeaders.EMBEDDING, Embedding.class);
        }

        if (in.getHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENT) != null) {
            text = in.getHeader(
                    LangChain4jEmbeddingsHeaders.TEXT_SEGMENT, dev.langchain4j.data.segment.TextSegment.class);
            id = getEndpoint().getConfiguration().getEmbeddingStore().add(embedding, text);
        } else {
            id = getEndpoint().getConfiguration().getEmbeddingStore().add(embedding);
        }

        Message out = exchange.getMessage();
        out.setBody(id);
    }

    /**
     * Removes an embedding from the store by its ID.
     *
     * <p>
     * Expects the embedding ID as the message body (String).
     * </p>
     *
     * @param  exchange  the Camel exchange containing the embedding ID to remove
     * @throws Exception if the remove operation fails
     */
    private void remove(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        String id = in.getBody(String.class);

        getEndpoint().getConfiguration().getEmbeddingStore().remove(id);

        Message out = exchange.getMessage();
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
        Embedding embedding = null;
        if (in.getHeader(LangChain4jEmbeddingsHeaders.EMBEDDING) != null) {
            embedding = in.getHeader(LangChain4jEmbeddingsHeaders.EMBEDDING, Embedding.class);
        }

        int maxResults = Integer.parseInt(LangChain4jEmbeddingStore.DEFAULT_MAX_RESULTS);
        if (in.getHeader(LangChain4jEmbeddingStoreHeaders.MAX_RESULTS, Integer.class) != null) {
            maxResults = in.getHeader(LangChain4jEmbeddingStoreHeaders.MAX_RESULTS, Integer.class);
        }

        EmbeddingSearchRequestBuilder esrb =
                EmbeddingSearchRequest.builder().queryEmbedding(embedding).maxResults(maxResults);

        if (in.getHeader(LangChain4jEmbeddingStoreHeaders.MIN_SCORE, Integer.class) != null) {
            Double minScore = in.getHeader(LangChain4jEmbeddingStoreHeaders.MIN_SCORE, Double.class);
            esrb = esrb.minScore(minScore);
        }

        if (in.getHeader(LangChain4jEmbeddingStoreHeaders.FILTER, Filter.class) != null) {
            Filter filter = in.getHeader(LangChain4jEmbeddingStoreHeaders.FILTER, Filter.class);
            esrb = esrb.filter(filter);
        }
        EmbeddingSearchRequest embeddingSearchRequest = esrb.build();

        List<EmbeddingMatch<TextSegment>> result = getEndpoint()
                .getConfiguration()
                .getEmbeddingStore()
                .search(embeddingSearchRequest)
                .matches();
        Message out = exchange.getMessage();
        out.setBody(result);
    }

    private CamelContext getCamelContext() {
        return getEndpoint().getCamelContext();
    }
}
