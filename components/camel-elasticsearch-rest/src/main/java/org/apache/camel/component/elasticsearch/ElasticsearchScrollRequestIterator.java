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
package org.apache.camel.component.elasticsearch;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;

import org.apache.camel.Exchange;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.SearchHit;

import static org.apache.camel.component.elasticsearch.ElasticsearchConstants.PROPERTY_SCROLL_ES_QUERY_COUNT;

public class ElasticsearchScrollRequestIterator implements Iterator<SearchHit>, Closeable {
    private final SearchRequest searchRequest;
    private final RestHighLevelClient restHighLevelClient;
    private Iterator<SearchHit> currentSearchHits;
    private final int scrollKeepAliveMs;
    private Exchange exchange;
    private String scrollId;
    private boolean closed;
    private int requestCount;

    public ElasticsearchScrollRequestIterator(SearchRequest searchRequest, RestHighLevelClient restHighLevelClient, int scrollKeepAliveMs, Exchange exchange) throws IOException {
        this.searchRequest = searchRequest;
        this.restHighLevelClient = restHighLevelClient;
        this.scrollKeepAliveMs = scrollKeepAliveMs;
        this.exchange = exchange;
        this.closed = false;
        this.requestCount = 0;

        // add scroll option on the the first query
        searchRequest.scroll(TimeValue.timeValueMillis(scrollKeepAliveMs));

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
    public SearchHit next() {
        return closed ? null : currentSearchHits.next();
    }

    /**
     * Execute next Elasticsearch scroll request and update the current scroll result.
     */
    private void updateCurrentSearchHits() {
        SearchResponse searchResponse = scrollSearch();
        this.currentSearchHits = searchResponse.getHits().iterator();
    }

    private void setFirstCurrentSearchHits() {
        SearchResponse searchResponse = firstSearch();
        this.currentSearchHits = searchResponse.getHits().iterator();
        this.scrollId = searchResponse.getScrollId();
    }

    private SearchResponse firstSearch() {
        SearchResponse searchResponse;
        try {
            searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
            requestCount++;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return searchResponse;
    }

    private SearchResponse scrollSearch() {
        SearchResponse searchResponse;
        try {
            SearchScrollRequest searchScrollRequest = new SearchScrollRequest()
                    .scroll(new Scroll(TimeValue.timeValueMillis(scrollKeepAliveMs)))
                    .scrollId(scrollId);

            searchResponse = restHighLevelClient.scroll(searchScrollRequest, RequestOptions.DEFAULT);
            requestCount++;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return searchResponse;
    }

    @Override
    public void close() {
        if (!closed) {
            try {
                ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
                clearScrollRequest.addScrollId(scrollId);

                restHighLevelClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
                closed = true;
                exchange.setProperty(PROPERTY_SCROLL_ES_QUERY_COUNT, requestCount);
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
