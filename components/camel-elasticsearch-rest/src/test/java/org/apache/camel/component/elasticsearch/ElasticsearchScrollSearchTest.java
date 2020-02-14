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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Test;

import static org.apache.camel.component.elasticsearch.ElasticsearchConstants.*;

public class ElasticsearchScrollSearchTest extends ElasticsearchBaseTest {

    private static final String TWITTER_ES_INDEX_NAME = "twitter";
    private static final String SPLIT_TWITTER_ES_INDEX_NAME = "split-" + TWITTER_ES_INDEX_NAME;

    @Test
    public void testScrollSearch() throws IOException {
        // add some documents
        for (int i = 0; i < 10; i++) {
            Map<String, String> map = createIndexedData();
            String indexId = template.requestBody("direct:scroll-index", map, String.class);
            assertNotNull("indexId should be set", indexId);
        }

        // perform a refresh
        Response refreshResponse = getClient().performRequest(new Request("post", "/" + TWITTER_ES_INDEX_NAME + "/_refresh"));
        assertEquals("Cannot perform a refresh", 200, refreshResponse.getStatusLine().getStatusCode());

        SearchRequest req = getScrollSearchRequest(TWITTER_ES_INDEX_NAME);

        Exchange exchange = ExchangeBuilder.anExchange(context)
                .withHeader(PARAM_SCROLL_KEEP_ALIVE_MS, 50000)
                .withHeader(PARAM_SCROLL, true)
                .withBody(req)
                .build();

        exchange = template.send("direct:scroll-search", exchange);

        try (ElasticsearchScrollRequestIterator scrollRequestIterator = exchange.getIn().getBody(ElasticsearchScrollRequestIterator.class)) {
            assertNotNull("response should not be null", scrollRequestIterator);

            List result = new ArrayList();
            scrollRequestIterator.forEachRemaining(result::add);

            assertEquals("response hits should be == 10", 10, result.size());
            assertEquals("11 request should have been send to Elasticsearch", 11, scrollRequestIterator.getRequestCount());
        }

        ElasticsearchScrollRequestIterator scrollRequestIterator = exchange.getIn().getBody(ElasticsearchScrollRequestIterator.class);
        assertTrue("iterator should be closed", scrollRequestIterator.isClosed());
        assertEquals("11 request should have been send to Elasticsearch", 11, (int) exchange.getProperty(PROPERTY_SCROLL_ES_QUERY_COUNT, Integer.class));
    }

    @Test
    public void testScrollAndSplitSearch() throws IOException, InterruptedException {
        // add some documents
        for (int i = 0; i < 10; i++) {
            Map<String, String> map = createIndexedData();
            String indexId = template.requestBody("direct:scroll-n-split-index", map, String.class);
            assertNotNull("indexId should be set", indexId);
        }

        // perform a refresh
        Response refreshResponse = getClient().performRequest(new Request("post", "/" + SPLIT_TWITTER_ES_INDEX_NAME + "/_refresh"));
        assertEquals("Cannot perform a refresh", 200, refreshResponse.getStatusLine().getStatusCode());

        MockEndpoint mock = getMockEndpoint("mock:output");
        mock.expectedMessageCount(1);
        mock.setResultWaitTime(8000);

        SearchRequest req = getScrollSearchRequest(SPLIT_TWITTER_ES_INDEX_NAME);

        Exchange exchange = ExchangeBuilder.anExchange(context).withBody(req).build();
        exchange = template.send("direct:scroll-n-split-search", exchange);

        // wait for aggregation
        mock.assertIsSatisfied();
        Iterator<Exchange> iterator = mock.getReceivedExchanges().iterator();
        assertTrue("response should contain 1 exchange", iterator.hasNext());
        Collection aggregatedExchanges = iterator.next().getIn().getBody(Collection.class);

        assertEquals("response hits should be == 10", 10, aggregatedExchanges.size());

        ElasticsearchScrollRequestIterator scrollRequestIterator = exchange.getIn().getBody(ElasticsearchScrollRequestIterator.class);
        assertTrue("iterator should be closed", scrollRequestIterator.isClosed());
        assertEquals("11 request should have been send to Elasticsearch", 11, scrollRequestIterator.getRequestCount());
        assertEquals("11 request should have been send to Elasticsearch", 11, (int)exchange.getProperty(PROPERTY_SCROLL_ES_QUERY_COUNT, Integer.class));
    }

    private SearchRequest getScrollSearchRequest(String indexName) {
        SearchRequest req = new SearchRequest().indices(indexName);

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchAllQuery()).size(1);
        req.source(searchSourceBuilder);
        return req;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:scroll-index")
                        .to("elasticsearch-rest://elasticsearch?operation=Index&indexName=" + TWITTER_ES_INDEX_NAME);
                from("direct:scroll-search")
                        .to("elasticsearch-rest://elasticsearch?operation=Search&indexName=" + TWITTER_ES_INDEX_NAME);

                from("direct:scroll-n-split-index")
                        .to("elasticsearch-rest://elasticsearch?operation=Index&indexName=" + SPLIT_TWITTER_ES_INDEX_NAME);
                from("direct:scroll-n-split-search")
                        .to("elasticsearch-rest://elasticsearch?"
                                + "useScroll=true&scrollKeepAliveMs=50000&operation=Search&indexName=" + SPLIT_TWITTER_ES_INDEX_NAME)
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
