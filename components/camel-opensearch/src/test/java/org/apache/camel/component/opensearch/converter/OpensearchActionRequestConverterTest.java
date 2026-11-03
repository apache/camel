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
package org.apache.camel.component.opensearch.converter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.opensearch.OpensearchConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.opensearch.client.util.MissingRequiredPropertyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpensearchActionRequestConverterTest {

    private CamelContext context;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    private IndexRequest.Builder<Object> preBuiltIndexBuilder() {
        return new IndexRequest.Builder<>().index("idx").id("original").document(Map.of("k", "v"));
    }

    @Test
    void preBuiltIndexBuilderKeepsItsIdWhenHeaderAbsent() throws Exception {
        Exchange exchange = new DefaultExchange(context);

        IndexRequest.Builder<?> result
                = OpensearchActionRequestConverter.toIndexRequestBuilder(preBuiltIndexBuilder(), exchange);

        // no CamelOpensearchIndexId header -> the caller's id must be preserved, not overwritten with null
        assertEquals("original", result.build().id());
    }

    @Test
    void preBuiltIndexBuilderHeaderOverridesId() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(OpensearchConstants.PARAM_INDEX_ID, "fromHeader");

        IndexRequest.Builder<?> result
                = OpensearchActionRequestConverter.toIndexRequestBuilder(preBuiltIndexBuilder(), exchange);

        assertEquals("fromHeader", result.build().id());
    }

    @Test
    void preBuiltUpdateBuilderKeepsItsIdWhenHeaderAbsent() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        UpdateRequest.Builder<Object, Object> preBuilt
                = new UpdateRequest.Builder<>().index("idx").id("original").doc(Map.of("k", "v"));

        UpdateRequest.Builder<?, ?> result
                = OpensearchActionRequestConverter.toUpdateRequestBuilder(preBuilt, exchange);

        // no CamelOpensearchIndexId header -> the caller's id must be preserved, not overwritten with null
        assertEquals("original", result.build().id());
    }

    @Test
    void aJsonSearchBodyKeepsItsSizeSortAggregationsAndSource() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        String body = """
                {"query": {"match": {"name": "camel"}}, "size": 5, "from": 2,
                 "sort": [{"name": "asc"}], "aggs": {"names": {"terms": {"field": "name"}}},
                 "_source": ["name"], "track_total_hits": true}
                """;

        SearchRequest request = OpensearchActionRequestConverter.toSearchRequestBuilder(body, exchange).build();

        assertEquals(Query.Kind.Match, request.query()._kind());
        assertEquals(5, request.size());
        assertEquals(2, request.from());
        assertEquals(1, request.sort().size());
        assertEquals("name", request.sort().get(0).field().field());
        assertTrue(request.aggregations().containsKey("names"));
        assertNotNull(request.source());
        assertNotNull(request.trackTotalHits());
    }

    @Test
    void aJsonSearchBodyWithoutQueryIsASearchRequest() throws Exception {
        Exchange exchange = new DefaultExchange(context);

        SearchRequest request = OpensearchActionRequestConverter.toSearchRequestBuilder(
                "{\"size\": 0, \"aggs\": {\"names\": {\"terms\": {\"field\": \"name\"}}}}", exchange).build();

        assertEquals(0, request.size());
        assertTrue(request.aggregations().containsKey("names"));
    }

    @Test
    void aJsonQueryAloneIsStillTheQuery() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(OpensearchConstants.PARAM_INDEX_NAME, "idx");

        SearchRequest request = OpensearchActionRequestConverter
                .toSearchRequestBuilder("{\"match\": {\"name\": \"camel\"}}", exchange).build();

        assertEquals(Query.Kind.Match, request.query()._kind());
        assertEquals("name", request.query().match().field());
        assertEquals(List.of("idx"), request.index());
    }

    @Test
    void aMapSearchBodyKeepsItsSizeAndSort() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", Map.of("match", Map.of("name", "camel")));
        body.put("size", 5);
        body.put("sort", List.of(Map.of("name", "asc")));

        SearchRequest request = OpensearchActionRequestConverter.toSearchRequestBuilder(body, exchange).build();

        assertEquals(Query.Kind.Match, request.query()._kind());
        assertEquals(5, request.size());
        assertEquals(1, request.sort().size());
    }

    @Test
    void aMapQueryAloneIsStillTheQuery() throws Exception {
        Exchange exchange = new DefaultExchange(context);

        SearchRequest request = OpensearchActionRequestConverter
                .toSearchRequestBuilder(Map.of("match", Map.of("name", "camel")), exchange).build();

        assertEquals(Query.Kind.Match, request.query()._kind());
    }

    @Test
    void aMapSearchBodyWhoseQueryIsNotAMap() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        Map<String, Object> body = Map.of("query", new ObjectMapper().readTree("{\"match_all\": {}}"));

        SearchRequest request = OpensearchActionRequestConverter.toSearchRequestBuilder(body, exchange).build();

        assertEquals(Query.Kind.MatchAll, request.query()._kind());
    }

    @Test
    void theSizeAndFromHeadersOverrideThoseOfTheSearchBody() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(OpensearchConstants.PARAM_SIZE, 10);
        exchange.getIn().setHeader(OpensearchConstants.PARAM_FROM, 20);

        SearchRequest request = OpensearchActionRequestConverter.toSearchRequestBuilder(
                "{\"query\": {\"match_all\": {}}, \"size\": 5, \"from\": 2}", exchange).build();

        assertEquals(10, request.size());
        assertEquals(20, request.from());
    }

    @Test
    void deleteIndexTakesTheIndexNameOfTheBodyWithoutHeader() {
        Exchange exchange = new DefaultExchange(context);

        DeleteIndexRequest request
                = OpensearchActionRequestConverter.toDeleteIndexRequestBuilder("twitter", exchange).build();

        assertEquals(List.of("twitter"), request.index());
    }

    @Test
    void deleteIndexPrefersTheIndexNameHeader() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(OpensearchConstants.PARAM_INDEX_NAME, "logs");

        DeleteIndexRequest request
                = OpensearchActionRequestConverter.toDeleteIndexRequestBuilder("twitter", exchange).build();

        assertEquals(List.of("logs"), request.index());
    }

    @Test
    void deleteIndexWithoutAnyIndexNameFailsBeforeSending() {
        Exchange exchange = new DefaultExchange(context);

        DeleteIndexRequest.Builder builder = OpensearchActionRequestConverter.toDeleteIndexRequestBuilder(" ", exchange);

        assertThrows(MissingRequiredPropertyException.class, builder::build);
    }
}
