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
package org.apache.camel.component.opensearch.integration;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.opensearch.OpensearchConstants;
import org.apache.camel.component.opensearch.OpensearchOperation;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.MsearchRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.mget.MultiGetResponseItem;
import org.opensearch.client.opensearch.core.msearch.MultiSearchResponseItem;
import org.opensearch.client.opensearch.core.msearch.MultisearchBody;
import org.opensearch.client.opensearch.core.msearch.MultisearchHeader;
import org.opensearch.client.opensearch.core.msearch.RequestItem;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpensearchGetSearchDeleteExistsUpdateIT extends OpensearchTestSupport {

    @Test
    void testIndexWithMap() {
        //first, Index a value
        Map<String, String> map = createIndexedData();
        String indexId = template().requestBody("direct:index", map, String.class);
        assertNotNull(indexId, "indexId should be set");

        //now, verify GET succeeded
        GetResponse<?> response = template().requestBody("direct:get", indexId, GetResponse.class);
        assertNotNull(response, "response should not be null");
        assertNotNull(response.source(), "response source should not be null");
        assertInstanceOf(ObjectNode.class, response.source(), "response source should be a ObjectNode");
        String key = map.keySet().iterator().next();
        assertTrue(((ObjectNode) response.source()).has(key));
        assertEquals(map.get(key), ((ObjectNode) response.source()).get(key).asText());
    }

    @Test
    void testIndexWithString() {
        //first, Index a value
        String indexId = template().requestBody("direct:index", "{\"testIndexWithString\": \"some-value\"}", String.class);
        assertNotNull(indexId, "indexId should be set");

        //now, verify GET succeeded
        GetResponse<?> response = template().requestBody("direct:get", indexId, GetResponse.class);
        assertNotNull(response, "response should not be null");
        assertNotNull(response.source(), "response source should not be null");
        assertInstanceOf(ObjectNode.class, response.source(), "response source should be a ObjectNode");
        assertTrue(((ObjectNode) response.source()).has("testIndexWithString"));
        assertEquals("some-value", ((ObjectNode) response.source()).get("testIndexWithString").asText());
    }

    @Test
    void testIndexWithReader() {
        //first, Index a value
        String indexId = template().requestBody("direct:index", new StringReader("{\"testIndexWithReader\": \"some-value\"}"),
                String.class);
        assertNotNull(indexId, "indexId should be set");

        //now, verify GET succeeded
        GetResponse<?> response = template().requestBody("direct:get", indexId, GetResponse.class);
        assertNotNull(response, "response should not be null");
        assertNotNull(response.source(), "response source should not be null");
        assertInstanceOf(ObjectNode.class, response.source(), "response source should be a ObjectNode");
        assertTrue(((ObjectNode) response.source()).has("testIndexWithReader"));
        assertEquals("some-value", ((ObjectNode) response.source()).get("testIndexWithReader").asText());
    }

    @Test
    void testIndexWithBytes() {
        //first, Index a value
        String indexId = template().requestBody("direct:index",
                "{\"testIndexWithBytes\": \"some-value\"}".getBytes(StandardCharsets.UTF_8), String.class);
        assertNotNull(indexId, "indexId should be set");

        //now, verify GET succeeded
        GetResponse<?> response = template().requestBody("direct:get", indexId, GetResponse.class);
        assertNotNull(response, "response should not be null");
        assertNotNull(response.source(), "response source should not be null");
        assertInstanceOf(ObjectNode.class, response.source(), "response source should be a ObjectNode");
        assertTrue(((ObjectNode) response.source()).has("testIndexWithBytes"));
        assertEquals("some-value", ((ObjectNode) response.source()).get("testIndexWithBytes").asText());
    }

    @Test
    void testIndexWithInputStream() {
        //first, Index a value
        String indexId = template().requestBody("direct:index",
                new ByteArrayInputStream("{\"testIndexWithInputStream\": \"some-value\"}".getBytes(StandardCharsets.UTF_8)),
                String.class);
        assertNotNull(indexId, "indexId should be set");

        //now, verify GET succeeded
        GetResponse<?> response = template().requestBody("direct:get", indexId, GetResponse.class);
        assertNotNull(response, "response should not be null");
        assertNotNull(response.source(), "response source should not be null");
        assertInstanceOf(ObjectNode.class, response.source(), "response source should be a ObjectNode");
        assertTrue(((ObjectNode) response.source()).has("testIndexWithInputStream"));
        assertEquals("some-value", ((ObjectNode) response.source()).get("testIndexWithInputStream").asText());
    }

    @Test
    void testIndexWithDocumentType() {
        Product product = new Product();
        product.setId("book-world-records-2021");
        product.setStockAvailable(1);
        product.setPrice(100);
        product.setDescription("The book of the year!");
        product.setName("Guinness book of records 2021");

        //first, Index a value
        String indexId = template().requestBody("direct:index-product", product, String.class);
        assertNotNull(indexId, "indexId should be set");

        //now, verify GET succeeded
        GetResponse<?> response = template().requestBodyAndHeader("direct:get", indexId,
                OpensearchConstants.PARAM_DOCUMENT_CLASS, Product.class, GetResponse.class);
        assertNotNull(response, "response should not be null");
        assertNotNull(response.source(), "response source should not be null");
        assertInstanceOf(Product.class, response.source(), "response source should be a Product");
        Product actual = (Product) response.source();
        assertNotSame(product, actual);
        assertEquals(product, actual);
    }

    @Test
    void testGetWithString() {
        //first, Index a value
        Map<String, String> map = createIndexedData();
        String indexId = template().requestBody("direct:index", map, String.class);
        assertNotNull(indexId, "indexId should be set");

        //now, verify GET succeeded
        GetResponse<?> response = template().requestBody("direct:get", indexId, GetResponse.class);
        assertNotNull(response, "response should not be null");
        assertNotNull(response.source(), "response source should not be null");
        assertInstanceOf(ObjectNode.class, response.source());
    }

    @Test
    void testGetWithDocumentType() {
        //first, Index a value
        Product product = new Product();
        product.setId("book-world-records-1890");
        product.setStockAvailable(0);
        product.setPrice(200);
        product.setDescription("The book of the year!");
        product.setName("Guinness book of records 1890");

        String indexId = template().requestBody("direct:index", product, String.class);
        assertNotNull(indexId, "indexId should be set");

        //now, verify GET succeeded
        GetResponse<?> response = template().requestBodyAndHeader(
                "direct:get", indexId, OpensearchConstants.PARAM_DOCUMENT_CLASS, Product.class, GetResponse.class);
        assertNotNull(response, "response should not be null");
        assertNotNull(response.source(), "response source should not be null");
        assertInstanceOf(Product.class, response.source());
        Product p = (Product) response.source();
        assertEquals(product, p);
    }

    @Test
    void testMGetWithString() {
        //first, Index a value
        Map<String, String> map = createIndexedData();
        String indexId = template().requestBody("direct:index", map, String.class);
        assertNotNull(indexId, "indexId should be set");

        //now, verify GET succeeded
        @SuppressWarnings("unchecked")
        List<MultiGetResponseItem<?>> response = template().requestBody("direct:multiget", List.of(indexId), List.class);
        assertNotNull(response, "response should not be null");
        assertEquals(1, response.size(), "response should contain one result");
        assertTrue(response.get(0).isResult());
        assertNotNull(response.get(0).result().source(), "response source should not be null");
        assertInstanceOf(ObjectNode.class, response.get(0).result().source());
    }

    @Test
    void testMGetWithDocumentType() {
        //first, Index a value
        Product product = new Product();
        product.setId("book-world-records-1890");
        product.setStockAvailable(0);
        product.setPrice(200);
        product.setDescription("The book of the year!");
        product.setName("Guinness book of records 1890");

        String indexId = template().requestBody("direct:index", product, String.class);
        assertNotNull(indexId, "indexId should be set");

        //now, verify GET succeeded
        @SuppressWarnings("unchecked")
        List<MultiGetResponseItem<?>> response = template().requestBodyAndHeader(
                "direct:multiget", List.of(indexId), OpensearchConstants.PARAM_DOCUMENT_CLASS, Product.class, List.class);
        assertNotNull(response, "response should not be null");
        assertEquals(1, response.size(), "response should contain one result");
        assertTrue(response.get(0).isResult());
        assertNotNull(response.get(0).result().source(), "response source should not be null");
        assertInstanceOf(Product.class, response.get(0).result().source());
        Product p = (Product) response.get(0).result().source();
        assertEquals(product, p);
    }

    @Test
    void testDeleteWithString() {
        //first, Index a value
        Map<String, String> map = createIndexedData();
        String indexId = template().requestBody("direct:index", map, String.class);
        assertNotNull(indexId, "indexId should be set");

        //now, verify GET succeeded
        GetResponse<?> response = template().requestBody("direct:get", indexId, GetResponse.class);
        assertNotNull(response, "response should not be null");
        assertNotNull(response.source(), "response source should not be null");

        //now, perform Delete
        Result deleteResponse = template().requestBody("direct:delete", indexId, Result.class);
        assertNotNull(deleteResponse, "response should not be null");

        //now, verify GET fails to find the indexed value
        response = template().requestBody("direct:get", indexId, GetResponse.class);
        assertNotNull(response, "response should not be null");
        assertNull(response.source(), "response source should be null");
    }

    @Test
    void testSearchWithMapQuery() throws Exception {

        //first, Index a value
        Map<String, String> map1 = Map.of("testSearchWithMapQuery1", "foo");
        Map<String, String> map2 = Map.of("testSearchWithMapQuery2", "bar");
        Map<String, Object> headers = Map.of(
                OpensearchConstants.PARAM_OPERATION, OpensearchOperation.Bulk,
                OpensearchConstants.PARAM_INDEX_NAME, "twitter");
        template().requestBodyAndHeaders("direct:start", List.of(Map.of("doc", map1), Map.of("doc", map2)), headers,
                String.class);

        // No match
        Map<String, Object> actualQuery = new HashMap<>();
        actualQuery.put("doc.testSearchWithMapQuery1", "bar");
        Map<String, Object> match = new HashMap<>();
        match.put("match", actualQuery);
        Map<String, Object> query = new HashMap<>();
        query.put("query", match);
        HitsMetadata<?> response = template().requestBody("direct:search", query, HitsMetadata.class);
        assertNotNull(response, "response should not be null");
        assertNotNull(response.total());
        assertEquals(0, response.total().value(), "response hits should be == 0");

        // Match
        actualQuery.put("doc.testSearchWithMapQuery1", "foo");

        // Delay the execution, because the search is getting stale results
        Awaitility.await().pollDelay(2, TimeUnit.SECONDS).untilAsserted(() -> {
            HitsMetadata<?> resp = template().requestBody("direct:search", query, HitsMetadata.class);
            assertNotNull(resp, "response should not be null");
            assertNotNull(resp.total());
            assertEquals(1, resp.total().value(), "response hits should be == 1");
            assertEquals(1, resp.hits().size(), "response hits should be == 1");
            Object result = resp.hits().get(0).source();
            assertInstanceOf(ObjectNode.class, result);
            assertTrue(((ObjectNode) result).has("doc"));
            JsonNode node = ((ObjectNode) result).get("doc");
            assertTrue(node.has("testSearchWithMapQuery1"));
            assertEquals("foo", node.get("testSearchWithMapQuery1").asText());
        });
    }

    @Test
    void testSearchWithStringQuery() throws Exception {
        //first, Index a value
        Map<String, String> map1 = Map.of("testSearchWithStringQuery1", "foo");
        Map<String, String> map2 = Map.of("testSearchWithStringQuery2", "bar");
        Map<String, Object> headers = new HashMap<>();
        headers.put(OpensearchConstants.PARAM_OPERATION, OpensearchOperation.Bulk);
        headers.put(OpensearchConstants.PARAM_INDEX_NAME, "twitter");
        template().requestBodyAndHeaders("direct:start", List.of(Map.of("doc", map1), Map.of("doc", map2)), headers,
                String.class);

        // No match
        String query = """
                {
                    "query" : { "match" : { "doc.testSearchWithStringQuery1" : "bar" }}
                }
                """;

        HitsMetadata<?> response = template().requestBody("direct:search", query, HitsMetadata.class);
        assertNotNull(response, "response should not be null");
        assertNotNull(response.total());
        assertEquals(0, response.total().value(), "response hits should be == 0");

        // Match
        String q = """
                {
                    "query" : { "match" : { "doc.testSearchWithStringQuery1" : "foo" }}
                }
                """;

        // Delay the execution, because the search is getting stale results
        Awaitility.await().pollDelay(2, TimeUnit.SECONDS).untilAsserted(() -> {
            HitsMetadata<?> resp = template().requestBody("direct:search", q, HitsMetadata.class);
            assertNotNull(resp, "response should not be null");
            assertNotNull(resp.total());
            assertEquals(1, resp.total().value(), "response hits should be == 1");
            assertEquals(1, resp.hits().size(), "response hits should be == 1");
            Object result = resp.hits().get(0).source();
            assertInstanceOf(ObjectNode.class, result);
            assertTrue(((ObjectNode) result).has("doc"));
            JsonNode node = ((ObjectNode) result).get("doc");
            assertTrue(node.has("testSearchWithStringQuery1"));
            assertEquals("foo", node.get("testSearchWithStringQuery1").asText());
        });
    }

    @Test
    void testSearchWithBuilder() throws Exception {
        //first, Index a value
        Map<String, String> map1 = Map.of("testSearchWithBuilder1", "foo");
        Map<String, String> map2 = Map.of("testSearchWithBuilder2", "bar");
        Map<String, Object> headers = new HashMap<>();
        headers.put(OpensearchConstants.PARAM_OPERATION, OpensearchOperation.Bulk);
        headers.put(OpensearchConstants.PARAM_INDEX_NAME, "twitter");
        template().requestBodyAndHeaders("direct:start", List.of(Map.of("doc", map1), Map.of("doc", map2)), headers,
                String.class);

        // No match
        SearchRequest.Builder builder = new SearchRequest.Builder()
                .query(new Query.Builder()
                        .match(new MatchQuery.Builder().field("doc.testSearchWithBuilder1").query(FieldValue.of("bar")).build())
                        .build());
        HitsMetadata<?> response = template().requestBody("direct:search", builder, HitsMetadata.class);
        assertNotNull(response, "response should not be null");
        assertNotNull(response.total());

        SearchRequest.Builder b = new SearchRequest.Builder()
                .query(new Query.Builder()
                        .match(new MatchQuery.Builder().field("doc.testSearchWithBuilder1").query(FieldValue.of("foo"))
                                .build())
                        .build());

        // Delay the execution, because the search is getting stale results
        Awaitility.await().pollDelay(2, TimeUnit.SECONDS).untilAsserted(() -> {
            // Match
            HitsMetadata<?> resp = template().requestBody("direct:search", b, HitsMetadata.class);
            assertNotNull(resp, "response should not be null");
            assertNotNull(resp.total());
            assertEquals(1, resp.total().value(), "response hits should be == 1");
            assertEquals(1, resp.hits().size(), "response hits should be == 1");
            Object result = resp.hits().get(0).source();
            assertInstanceOf(ObjectNode.class, result);
            assertTrue(((ObjectNode) result).has("doc"));
            JsonNode node = ((ObjectNode) result).get("doc");
            assertTrue(node.has("testSearchWithBuilder1"));
            assertEquals("foo", node.get("testSearchWithBuilder1").asText());
        });
    }

    @Test
    void testSearchWithDocumentType() throws Exception {
        //first, Index a value
        Product product1 = new Product();
        product1.setId("book-world-records-2020");
        product1.setStockAvailable(1);
        product1.setPrice(100);
        product1.setDescription("The book of the year!");
        product1.setName("Guinness book of records 2020");

        Product product2 = new Product();
        product2.setId("book-world-records-2010");
        product2.setStockAvailable(200);
        product2.setPrice(80);
        product2.setDescription("The book of the year!");
        product2.setName("Guinness book of records 2010");
        Map<String, Object> headers = new HashMap<>();
        headers.put(OpensearchConstants.PARAM_OPERATION, OpensearchOperation.Bulk);
        headers.put(OpensearchConstants.PARAM_INDEX_NAME, "twitter");
        template().requestBodyAndHeaders("direct:start", List.of(product1, product2), headers, String.class);

        // No match
        SearchRequest.Builder builder = new SearchRequest.Builder()
                .query(new Query.Builder().match(new MatchQuery.Builder().field("doc.id").query(FieldValue.of("bar")).build())
                        .build());
        HitsMetadata<?> response = template().requestBodyAndHeader(
                "direct:search", builder, OpensearchConstants.PARAM_DOCUMENT_CLASS, Product.class, HitsMetadata.class);
        assertNotNull(response, "response should not be null");
        assertNotNull(response.total());

        SearchRequest.Builder b = new SearchRequest.Builder()
                .query(new Query.Builder().match(new MatchQuery.Builder().field("id").query(FieldValue.of("2020")).build())
                        .build());

        // Delay the execution, because the search is getting stale results
        Awaitility.await().pollDelay(2, TimeUnit.SECONDS).untilAsserted(() -> {
            //Match
            HitsMetadata<?> resp = template().requestBodyAndHeader("direct:search", b, OpensearchConstants.PARAM_DOCUMENT_CLASS,
                    Product.class, HitsMetadata.class);
            assertNotNull(resp, "response should not be null");
            assertNotNull(resp.total());
            assertEquals(1, resp.total().value(), "response hits should be == 1");
            assertEquals(1, resp.hits().size(), "response hits should be == 1");
            Object result = resp.hits().get(0).source();
            assertInstanceOf(Product.class, result);
            Product p = (Product) result;
            assertEquals(product1, p);
        });
    }

    @Test
    void testMultiSearch() throws Exception {
        //first, Index a value
        Map<String, String> map = createIndexedData();
        String indexId = template().requestBody("direct:index", map, String.class);

        MsearchRequest.Builder builder = new MsearchRequest.Builder().index("twitter").searches(
                new RequestItem.Builder().header(new MultisearchHeader.Builder().build())
                        .body(new MultisearchBody.Builder().query(b -> b.matchAll(x -> x)).build()).build(),
                new RequestItem.Builder().header(new MultisearchHeader.Builder().build())
                        .body(new MultisearchBody.Builder().query(b -> b.matchAll(x -> x)).build()).build());

        // Delay the execution, because the search is getting stale results
        Awaitility.await().pollDelay(2, TimeUnit.SECONDS).untilAsserted(() -> {

            @SuppressWarnings("unchecked")
            List<MultiSearchResponseItem<?>> response = template().requestBody("direct:multiSearch", builder, List.class);

            assertNotNull(response, "response should not be null");
            assertEquals(2, response.size(), "response should be == 2");
            assertInstanceOf(MultiSearchResponseItem.class, response.get(0));
            assertTrue(response.get(0).isResult());
            assertNotNull(response.get(0).result());
            assertTrue(response.get(0).result().hits().total().value() > 0);
            assertInstanceOf(MultiSearchResponseItem.class, response.get(1));
            assertTrue(response.get(1).isResult());
            assertNotNull(response.get(1).result());
            assertTrue(response.get(1).result().hits().total().value() > 0);
        });
    }

    @Test
    void testMultiSearchWithDocumentType() throws Exception {
        //first, Index a value
        Product product = new Product();
        product.setId("book-world-records-2022");
        product.setStockAvailable(1);
        product.setPrice(100);
        product.setDescription("The book of the year!");
        product.setName("Guinness book of records 2022");
        String indexId = template().requestBodyAndHeader("direct:index", product, OpensearchConstants.PARAM_INDEX_NAME,
                "multi-search", String.class);

        MsearchRequest.Builder builder = new MsearchRequest.Builder().index("multi-search").searches(
                new RequestItem.Builder().header(new MultisearchHeader.Builder().build())
                        .body(new MultisearchBody.Builder().query(b -> b.matchAll(x -> x)).build()).build(),
                new RequestItem.Builder().header(new MultisearchHeader.Builder().build())
                        .body(new MultisearchBody.Builder().query(b -> b.matchAll(x -> x)).build()).build());

        // Delay the execution, because the search is getting stale results
        Awaitility.await().pollDelay(2, TimeUnit.SECONDS).untilAsserted(() -> {

            @SuppressWarnings("unchecked")
            List<MultiSearchResponseItem<?>> response = template().requestBodyAndHeaders(
                    "direct:multiSearch", builder,
                    Map.of(
                            OpensearchConstants.PARAM_INDEX_NAME, "multi-search",
                            OpensearchConstants.PARAM_DOCUMENT_CLASS, Product.class),
                    List.class);

            assertNotNull(response, "response should not be null");
            assertEquals(2, response.size(), "response should be == 2");
            assertInstanceOf(MultiSearchResponseItem.class, response.get(0));
            assertTrue(response.get(0).isResult());
            assertNotNull(response.get(0).result());
            assertTrue(response.get(0).result().hits().total().value() > 0);
            assertInstanceOf(MultiSearchResponseItem.class, response.get(1));
            assertTrue(response.get(1).isResult());
            assertNotNull(response.get(1).result());
            assertTrue(response.get(1).result().hits().total().value() > 0);
        });
    }

    @Test
    void testUpdateWithMap() {
        Map<String, String> map = createIndexedData();
        String indexId = template().requestBody("direct:index", map, String.class);
        assertNotNull(indexId, "indexId should be set");

        Map<String, String> newMap = new HashMap<>();
        String prefix = getPrefix();
        newMap.put(prefix + "key2", prefix + "value2");
        Map<String, Object> headers = new HashMap<>();
        headers.put(OpensearchConstants.PARAM_INDEX_ID, indexId);
        indexId = template().requestBodyAndHeaders("direct:update", Map.of("doc", newMap), headers, String.class);
        assertNotNull(indexId, "indexId should be set");

        //now, verify GET succeeded
        GetResponse<?> response = template().requestBody("direct:get", indexId, GetResponse.class);
        assertNotNull(response, "response should not be null");
        assertNotNull(response.source(), "response source should not be null");
        assertInstanceOf(ObjectNode.class, response.source(), "response source should be a ObjectNode");
        assertTrue(((ObjectNode) response.source()).has(prefix + "key2"));
        assertEquals(prefix + "value2", ((ObjectNode) response.source()).get(prefix + "key2").asText());
    }

    @Test
    void testGetWithHeaders() {
        //first, Index a value
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<>();
        headers.put(OpensearchConstants.PARAM_OPERATION, OpensearchOperation.Index);
        headers.put(OpensearchConstants.PARAM_INDEX_NAME, "twitter");

        String indexId = template().requestBodyAndHeaders("direct:start", map, headers, String.class);

        //now, verify GET
        headers.put(OpensearchConstants.PARAM_OPERATION, OpensearchOperation.GetById);
        GetResponse<?> response = template().requestBodyAndHeaders("direct:start", indexId, headers, GetResponse.class);
        assertNotNull(response, "response should not be null");
        assertNotNull(response.source(), "response source should not be null");
    }

    @Test
    void testExistsWithHeaders() {
        //first, Index a value
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<>();
        headers.put(OpensearchConstants.PARAM_OPERATION, OpensearchOperation.Index);
        headers.put(OpensearchConstants.PARAM_INDEX_NAME, "twitter");

        template().requestBodyAndHeaders("direct:start", map, headers, String.class);

        //now, verify GET
        headers.put(OpensearchConstants.PARAM_OPERATION, OpensearchOperation.Exists);
        headers.put(OpensearchConstants.PARAM_INDEX_NAME, "twitter");
        Boolean exists = template().requestBodyAndHeaders("direct:exists", "", headers, Boolean.class);
        assertNotNull(exists, "response should not be null");
        assertTrue(exists, "Index should exists");
    }

    @Test
    void testNotExistsWithHeaders() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(OpensearchConstants.PARAM_OPERATION, OpensearchOperation.Exists);
        headers.put(OpensearchConstants.PARAM_INDEX_NAME, "twitter-tweet");
        Boolean exists = template().requestBodyAndHeaders("direct:exists", "", headers, Boolean.class);
        assertNotNull(exists, "response should not be null");
        assertFalse(exists, "Index should not exists");
    }

    @Test
    void testDeleteWithHeaders() {
        //first, Index a value
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<>();
        headers.put(OpensearchConstants.PARAM_OPERATION, OpensearchOperation.Index);
        headers.put(OpensearchConstants.PARAM_INDEX_NAME, "twitter");

        String indexId = template().requestBodyAndHeaders("direct:start", map, headers, String.class);

        //now, verify GET
        headers.put(OpensearchConstants.PARAM_OPERATION, OpensearchOperation.GetById);
        GetResponse<?> response = template().requestBodyAndHeaders("direct:start", indexId, headers, GetResponse.class);
        assertNotNull(response, "response should not be null");
        assertNotNull(response.source(), "response source should not be null");

        //now, perform Delete
        headers.put(OpensearchConstants.PARAM_OPERATION, OpensearchOperation.Delete);
        Result deleteResponse
                = template().requestBodyAndHeaders("direct:start", indexId, headers, Result.class);
        assertEquals(Result.Deleted, deleteResponse, "response should not be null");

        //now, verify GET fails to find the indexed value
        headers.put(OpensearchConstants.PARAM_OPERATION, OpensearchOperation.GetById);
        response = template().requestBodyAndHeaders("direct:start", indexId, headers, GetResponse.class);
        assertNotNull(response, "response should not be null");
        assertNull(response.source(), "response source should be null");
    }

    @Test
    void testUpdateWithIDInHeader() {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<>();
        headers.put(OpensearchConstants.PARAM_OPERATION, OpensearchOperation.Index);
        headers.put(OpensearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(OpensearchConstants.PARAM_INDEX_ID, "123");

        String indexId = template().requestBodyAndHeaders("direct:start", map, headers, String.class);
        assertNotNull(indexId, "indexId should be set");
        assertEquals("123", indexId, "indexId should be equals to the provided id");

        headers.put(OpensearchConstants.PARAM_OPERATION, OpensearchOperation.Update);

        indexId = template().requestBodyAndHeaders("direct:start", Map.of("doc", map), headers, String.class);
        assertNotNull(indexId, "indexId should be set");
        assertEquals("123", indexId, "indexId should be equals to the provided id");
    }

    @Test
    void testGetRequestBody() {
        String prefix = getPrefix();

        // given
        GetRequest.Builder builder = new GetRequest.Builder().index(prefix + "foo");

        // when
        String documentId = template().requestBody("direct:index",
                new IndexRequest.Builder<>()
                        .index(prefix + "foo")
                        .id(prefix + "testId")
                        .document(Map.of(prefix + "content", prefix + "hello")),
                String.class);
        GetResponse<?> response = template().requestBody("direct:get",
                builder.id(documentId), GetResponse.class);

        // then
        assertThat(response, notNullValue());

        assertThat(response.source(), notNullValue());
        ObjectNode node = (ObjectNode) response.source();
        assertThat(node.has(prefix + "content"), equalTo(true));
        assertThat(node.get(prefix + "content").asText(), equalTo(prefix + "hello"));
    }

    @Test
    void testDeleteWithBuilder() {
        String prefix = getPrefix();

        // given
        String documentId = template().requestBody("direct:index",
                new IndexRequest.Builder<>()
                        .index(prefix + "foo")
                        .id(prefix + "testId")
                        .document(Map.of(prefix + "content", prefix + "hello")),
                String.class);

        GetResponse<?> getResponse = template().requestBodyAndHeader(
                "direct:get", documentId, OpensearchConstants.PARAM_INDEX_NAME, prefix + "foo", GetResponse.class);
        assertNotNull(getResponse, "response should not be null");
        assertNotNull(getResponse.source(), "response source should not be null");

        // when
        Result response
                = template().requestBody("direct:delete", new DeleteRequest.Builder().index(prefix + "foo").id(documentId),
                        Result.class);

        // then
        assertThat(response, equalTo(Result.Deleted));
        getResponse = template().requestBodyAndHeader(
                "direct:get", documentId, OpensearchConstants.PARAM_INDEX_NAME, prefix + "foo", GetResponse.class);
        assertNotNull(getResponse, "response should not be null");
        assertNull(getResponse.source(), "response source should be null");
    }

    @Test
    void testUpdateWithString() {
        Map<String, String> map = createIndexedData();
        String indexId = template().requestBody("direct:index", map, String.class);
        assertNotNull(indexId, "indexId should be set");
        String key = map.keySet().iterator().next();
        Object body = String.format("{ \"doc\": {\"%s\" : \"testUpdateWithString-updated\"}}", key);

        Map<String, Object> headers = new HashMap<>();
        headers.put(OpensearchConstants.PARAM_INDEX_ID, indexId);
        indexId = template().requestBodyAndHeaders("direct:update", body, headers, String.class);
        assertNotNull(indexId, "indexId should be set");

        GetResponse<?> response = template().requestBody("direct:get", indexId, GetResponse.class);
        assertThat(response.source(), notNullValue());
        ObjectNode node = (ObjectNode) response.source();
        assertThat(node.has(key), equalTo(true));
        assertThat(node.get(key).asText(), equalTo("testUpdateWithString-updated"));
    }

    @Test
    void testUpdateWithReader() {
        Map<String, String> map = createIndexedData();
        String indexId = template().requestBody("direct:index", map, String.class);
        assertNotNull(indexId, "indexId should be set");
        String key = map.keySet().iterator().next();
        Object body = new StringReader(String.format("{ \"doc\": {\"%s\" : \"testUpdateWithReader-updated\"}}", key));

        Map<String, Object> headers = new HashMap<>();
        headers.put(OpensearchConstants.PARAM_INDEX_ID, indexId);
        indexId = template().requestBodyAndHeaders("direct:update", body, headers, String.class);
        assertNotNull(indexId, "indexId should be set");

        GetResponse<?> response = template().requestBody("direct:get", indexId, GetResponse.class);
        assertThat(response.source(), notNullValue());
        ObjectNode node = (ObjectNode) response.source();
        assertThat(node.has(key), equalTo(true));
        assertThat(node.get(key).asText(), equalTo("testUpdateWithReader-updated"));
    }

    @Test
    void testUpdateWithBytes() {
        Map<String, String> map = createIndexedData();
        String indexId = template().requestBody("direct:index", map, String.class);
        assertNotNull(indexId, "indexId should be set");
        String key = map.keySet().iterator().next();
        Object body
                = String.format("{ \"doc\": {\"%s\" : \"testUpdateWithBytes-updated\"}}", key).getBytes(StandardCharsets.UTF_8);

        Map<String, Object> headers = new HashMap<>();
        headers.put(OpensearchConstants.PARAM_INDEX_ID, indexId);
        indexId = template().requestBodyAndHeaders("direct:update", body, headers, String.class);
        assertNotNull(indexId, "indexId should be set");

        GetResponse<?> response = template().requestBody("direct:get", indexId, GetResponse.class);
        assertThat(response.source(), notNullValue());
        ObjectNode node = (ObjectNode) response.source();
        assertThat(node.has(key), equalTo(true));
        assertThat(node.get(key).asText(), equalTo("testUpdateWithBytes-updated"));
    }

    @Test
    void testUpdateWithInputStream() {
        Map<String, String> map = createIndexedData();
        String indexId = template().requestBody("direct:index", map, String.class);
        assertNotNull(indexId, "indexId should be set");
        String key = map.keySet().iterator().next();
        Object body = new ByteArrayInputStream(
                String.format("{ \"doc\": {\"%s\" : \"testUpdateWithInputStream-updated\"}}", key)
                        .getBytes(StandardCharsets.UTF_8));

        Map<String, Object> headers = new HashMap<>();
        headers.put(OpensearchConstants.PARAM_INDEX_ID, indexId);
        indexId = template().requestBodyAndHeaders("direct:update", body, headers, String.class);
        assertNotNull(indexId, "indexId should be set");

        GetResponse<?> response = template().requestBody("direct:get", indexId, GetResponse.class);
        assertThat(response.source(), notNullValue());
        ObjectNode node = (ObjectNode) response.source();
        assertThat(node.has(key), equalTo(true));
        assertThat(node.get(key).asText(), equalTo("testUpdateWithInputStream-updated"));
    }

    @Test
    void testUpdateWithDocumentType() {
        Product product = new Product();
        product.setId("book-world-records-2010");
        product.setStockAvailable(200);
        product.setPrice(80);
        product.setDescription("The book of the year!");
        product.setName("Guinness book of records 2010");

        String indexId = template().requestBody("direct:index", product, String.class);
        assertNotNull(indexId, "indexId should be set");

        Product productUpdate = new Product();
        productUpdate.setStockAvailable(250);
        productUpdate.setPrice(82);
        productUpdate.setName("Guinness book of records 2010 2nd edition");

        Map<String, Object> headers = new HashMap<>();
        headers.put(OpensearchConstants.PARAM_INDEX_ID, indexId);
        headers.put(OpensearchConstants.PARAM_DOCUMENT_CLASS, Product.class);
        indexId = template().requestBodyAndHeaders("direct:update", productUpdate, headers, String.class);
        assertNotNull(indexId, "indexId should be set");

        GetResponse<?> response = template().requestBodyAndHeader(
                "direct:get", indexId, OpensearchConstants.PARAM_DOCUMENT_CLASS, Product.class, GetResponse.class);
        assertThat(response.source(), notNullValue());
        Product actual = (Product) response.source();
        assertThat(actual.getId(), equalTo("book-world-records-2010"));
        assertThat(actual.getStockAvailable(), equalTo(250));
        assertThat(actual.getPrice(), equalTo(82d));
        assertThat(actual.getDescription(), equalTo("The book of the year!"));
        assertThat(actual.getName(), equalTo("Guinness book of records 2010 2nd edition"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("opensearch://opensearch?operation=Index");
                from("direct:index")
                        .to("opensearch://opensearch?operation=Index&indexName=twitter");
                from("direct:index-product")
                        .toF("opensearch://opensearch?operation=Index&indexName=twitter&documentClass=%s",
                                Product.class.getName());
                from("direct:get")
                        .to("opensearch://opensearch?operation=GetById&indexName=twitter");
                from("direct:multiget")
                        .to("opensearch://opensearch?operation=MultiGet&indexName=twitter");
                from("direct:delete")
                        .to("opensearch://opensearch?operation=Delete&indexName=twitter");
                from("direct:search")
                        .to("opensearch://opensearch?operation=Search&indexName=twitter");
                from("direct:search-1")
                        .to("opensearch://opensearch?operation=Search");
                from("direct:multiSearch")
                        .to("opensearch://opensearch?operation=MultiSearch");
                from("direct:update")
                        .to("opensearch://opensearch?operation=Update&indexName=twitter");
                from("direct:exists")
                        .to("opensearch://opensearch?operation=Exists");
            }
        };
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Product {

        private String id;
        private String name;
        private String description;
        private double price;
        private int stockAvailable;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public double getPrice() {
            return price;
        }

        public void setPrice(double price) {
            this.price = price;
        }

        public int getStockAvailable() {
            return stockAvailable;
        }

        public void setStockAvailable(int stockAvailable) {
            this.stockAvailable = stockAvailable;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Product product = (Product) o;
            return Double.compare(product.price, price) == 0 && stockAvailable == product.stockAvailable
                    && Objects.equals(id, product.id) && Objects.equals(name, product.name)
                    && Objects.equals(description, product.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name, description, price, stockAvailable);
        }
    }
}
