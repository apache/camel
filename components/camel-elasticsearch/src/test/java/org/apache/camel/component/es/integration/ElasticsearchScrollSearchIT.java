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
package org.apache.camel.component.es.integration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.es.ElasticsearchScrollRequestIterator;
import org.apache.camel.component.mock.MockEndpoint;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.es.ElasticsearchConstants.PARAM_SCROLL;
import static org.apache.camel.component.es.ElasticsearchConstants.PARAM_SCROLL_KEEP_ALIVE_MS;
import static org.apache.camel.component.es.ElasticsearchConstants.PROPERTY_SCROLL_ES_QUERY_COUNT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticsearchScrollSearchIT extends ElasticsearchTestSupport {

    private static final String TWITTER_ES_INDEX_NAME = "scroll-search";
    private static final String SPLIT_TWITTER_ES_INDEX_NAME = "split-" + TWITTER_ES_INDEX_NAME;

    @Test
    void testScrollSearch() throws IOException {
        // add some documents
        for (int i = 0; i < 10; i++) {
            Map<String, String> map = createIndexedData();
            String indexId = template.requestBody("direct:scroll-index", map, String.class);
            assertNotNull(indexId, "indexId should be set");
        }

        // perform a refresh
        Response refreshResponse = getClient().performRequest(new Request("post", "/" + TWITTER_ES_INDEX_NAME + "/_refresh"));
        assertEquals(200, refreshResponse.getStatusLine().getStatusCode(), "Cannot perform a refresh");

        SearchRequest.Builder req = getScrollSearchRequestBuilder(TWITTER_ES_INDEX_NAME);

        Exchange exchange = ExchangeBuilder.anExchange(context)
                .withHeader(PARAM_SCROLL_KEEP_ALIVE_MS, 50000)
                .withHeader(PARAM_SCROLL, true)
                .withBody(req)
                .build();

        exchange = template.send("direct:scroll-search", exchange);

        try (ElasticsearchScrollRequestIterator<?> scrollRequestIterator
                = exchange.getIn().getBody(ElasticsearchScrollRequestIterator.class)) {
            assertNotNull(scrollRequestIterator, "response should not be null");

            List<Hit<?>> result = new ArrayList<>();
            scrollRequestIterator.forEachRemaining(result::add);

            assertEquals(10, result.size(), "response hits should be == 10");
            assertEquals(11, scrollRequestIterator.getRequestCount(), "11 request should have been send to Elasticsearch");
        }

        ElasticsearchScrollRequestIterator<?> scrollRequestIterator
                = exchange.getIn().getBody(ElasticsearchScrollRequestIterator.class);
        assertTrue(scrollRequestIterator.isClosed(), "iterator should be closed");
        assertEquals(11, (int) exchange.getProperty(PROPERTY_SCROLL_ES_QUERY_COUNT, Integer.class),
                "11 request should have been send to Elasticsearch");
    }

    @Test
    void testScrollAndSplitSearch() throws IOException, InterruptedException {
        // add some documents
        for (int i = 0; i < 10; i++) {
            Map<String, String> map = createIndexedData();
            String indexId = template.requestBody("direct:scroll-n-split-index", map, String.class);
            assertNotNull(indexId, "indexId should be set");
        }

        // perform a refresh
        Response refreshResponse
                = getClient().performRequest(new Request("post", "/" + SPLIT_TWITTER_ES_INDEX_NAME + "/_refresh"));
        assertEquals(200, refreshResponse.getStatusLine().getStatusCode(), "Cannot perform a refresh");

        MockEndpoint mock = getMockEndpoint("mock:output");
        mock.expectedMessageCount(1);
        mock.setResultWaitTime(8000);

        SearchRequest.Builder req = getScrollSearchRequestBuilder(SPLIT_TWITTER_ES_INDEX_NAME);

        Exchange exchange = ExchangeBuilder.anExchange(context).withBody(req).build();
        exchange = template.send("direct:scroll-n-split-search", exchange);

        // wait for aggregation
        mock.assertIsSatisfied();
        Iterator<Exchange> iterator = mock.getReceivedExchanges().iterator();
        assertTrue(iterator.hasNext(), "response should contain 1 exchange");
        Collection<?> aggregatedExchanges = iterator.next().getIn().getBody(Collection.class);

        assertEquals(10, aggregatedExchanges.size(), "response hits should be == 10");

        ElasticsearchScrollRequestIterator<?> scrollRequestIterator
                = exchange.getIn().getBody(ElasticsearchScrollRequestIterator.class);
        assertTrue(scrollRequestIterator.isClosed(), "iterator should be closed");
        assertEquals(11, scrollRequestIterator.getRequestCount(), "11 request should have been send to Elasticsearch");
        assertEquals(11, (int) exchange.getProperty(PROPERTY_SCROLL_ES_QUERY_COUNT, Integer.class),
                "11 request should have been send to Elasticsearch");
    }

    private SearchRequest.Builder getScrollSearchRequestBuilder(String indexName) {
        SearchRequest.Builder builder = new SearchRequest.Builder().index(indexName);
        builder.size(1);
        builder.query(new Query.Builder().matchAll(new MatchAllQuery.Builder().build()).build());
        return builder;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:scroll-index")
                        .to("elasticsearch://elasticsearch?operation=Index&indexName=" + TWITTER_ES_INDEX_NAME);
                from("direct:scroll-search")
                        .to("elasticsearch://elasticsearch?operation=Search&indexName=" + TWITTER_ES_INDEX_NAME);

                from("direct:scroll-n-split-index")
                        .to("elasticsearch://elasticsearch?operation=Index&indexName=" + SPLIT_TWITTER_ES_INDEX_NAME);
                from("direct:scroll-n-split-search")
                        .to("elasticsearch://elasticsearch?"
                            + "useScroll=true&scrollKeepAliveMs=50000&operation=Search&indexName="
                            + SPLIT_TWITTER_ES_INDEX_NAME)
                        .split()
                        .body()
                        .streaming()
                        .parallelProcessing()
                        .threads(12)
                        .aggregate(AggregationStrategies.groupedExchange())
                        .constant(true)
                        .completionSize(20)
                        .completionTimeout(2000)
                        .to("mock:output")
                        .end();
            }
        };
    }
}
