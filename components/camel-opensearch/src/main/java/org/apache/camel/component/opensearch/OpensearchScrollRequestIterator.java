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
package org.apache.camel.component.opensearch;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.Exchange;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch.core.ClearScrollRequest;
import org.opensearch.client.opensearch.core.ScrollRequest;
import org.opensearch.client.opensearch.core.ScrollResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;

public class OpensearchScrollRequestIterator<TDocument> implements Iterator<Hit<TDocument>>, Closeable {

    private final SearchRequest searchRequest;
    private final OpenSearchClient esClient;
    private final Class<TDocument> documentClass;
    private Iterator<? extends Hit<TDocument>> currentSearchHits;
    private final int scrollKeepAliveMs;
    private final Exchange exchange;
    private String scrollId;
    private boolean closed;
    private int requestCount;

    public OpensearchScrollRequestIterator(SearchRequest.Builder searchRequestBuilder, OpenSearchClient esClient,
                                           int scrollKeepAliveMs, Exchange exchange, Class<TDocument> documentClass) {
        // add scroll option on the first query
        this.searchRequest = searchRequestBuilder
                .scroll(Time.of(b -> b.time(String.format("%sms", scrollKeepAliveMs))))
                .build();
        this.esClient = esClient;
        this.scrollKeepAliveMs = scrollKeepAliveMs;
        this.exchange = exchange;
        this.closed = false;
        this.requestCount = 0;
        this.documentClass = documentClass;

        setFirstCurrentSearchHits();
    }

    @Override
    public boolean hasNext() {
        if (closed) {
            return false;
        }

        boolean hasNext = currentSearchHits.hasNext();
        if (!hasNext) {
            updateCurrentSearchHits();

            hasNext = currentSearchHits.hasNext();
        }

        return hasNext;
    }

    @Override
    public Hit<TDocument> next() {
        return closed ? null : currentSearchHits.next();
    }

    /**
     * Execute next OpenSearch scroll request and update the current scroll result.
     */
    private void updateCurrentSearchHits() {
        ScrollResponse<TDocument> scrollResponse = scrollSearch();
        this.currentSearchHits = scrollResponse.hits().hits().iterator();
    }

    private void setFirstCurrentSearchHits() {
        SearchResponse<TDocument> searchResponse = firstSearch();
        this.currentSearchHits = searchResponse.hits().hits().iterator();
        this.scrollId = searchResponse.scrollId();
    }

    private SearchResponse<TDocument> firstSearch() {
        SearchResponse<TDocument> searchResponse;
        try {
            searchResponse = esClient.search(searchRequest, documentClass);
            requestCount++;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return searchResponse;
    }

    private ScrollResponse<TDocument> scrollSearch() {
        ScrollResponse<TDocument> scrollResponse;
        try {
            ScrollRequest searchScrollRequest = new ScrollRequest.Builder()
                    .scroll(Time.of(b -> b.time(String.format("%sms", scrollKeepAliveMs))))
                    .scrollId(scrollId)
                    .build();

            scrollResponse = esClient.scroll(searchScrollRequest, documentClass);
            requestCount++;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return scrollResponse;
    }

    @Override
    public void close() {
        if (!closed) {
            try {
                ClearScrollRequest clearScrollRequest = new ClearScrollRequest.Builder()
                        .scrollId(List.of(scrollId))
                        .build();

                esClient.clearScroll(clearScrollRequest);
                closed = true;
                exchange.setProperty(OpensearchConstants.PROPERTY_SCROLL_OPENSEARCH_QUERY_COUNT, requestCount);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public int getRequestCount() {
        return requestCount;
    }

    public boolean isClosed() {
        return closed;
    }
}
