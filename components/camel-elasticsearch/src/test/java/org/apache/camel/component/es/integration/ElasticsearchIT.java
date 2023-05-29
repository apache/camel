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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.MsearchRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.CreateOperation;
import co.elastic.clients.elasticsearch.core.bulk.DeleteOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.bulk.UpdateAction;
import co.elastic.clients.elasticsearch.core.bulk.UpdateOperation;
import co.elastic.clients.elasticsearch.core.mget.MultiGetResponseItem;
import co.elastic.clients.elasticsearch.core.msearch.MultiSearchResponseItem;
import co.elastic.clients.elasticsearch.core.msearch.MultisearchBody;
import co.elastic.clients.elasticsearch.core.msearch.MultisearchHeader;
import co.elastic.clients.elasticsearch.core.msearch.RequestItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.es.ElasticsearchConstants;
import org.apache.camel.component.es.ElasticsearchOperation;
import org.apache.camel.component.es.ElasticsearchScrollRequestIterator;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.http.impl.client.BasicResponseHandler;
import org.awaitility.Awaitility;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.apache.camel.component.es.ElasticsearchConstants.PARAM_SCROLL;
import static org.apache.camel.component.es.ElasticsearchConstants.PARAM_SCROLL_KEEP_ALIVE_MS;
import static org.apache.camel.component.es.ElasticsearchConstants.PROPERTY_SCROLL_ES_QUERY_COUNT;
import static org.apache.camel.test.junit5.TestSupport.assertCollectionSize;
import static org.apache.camel.test.junit5.TestSupport.assertStringContains;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ElasticsearchIT extends ElasticsearchTestSupport {

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    abstract class NestedTest extends CamelTestSupport {

        @Override
        protected CamelContext createCamelContext() throws Exception {
            CamelContext context = super.createCamelContext();
            addElasticsearchComponent(context);
            return context;
        }
    }

    @Nested
    class Bulk extends NestedTest {

        @Test
        void testBulkWithMap() {
            List<Map<String, String>> documents = new ArrayList<>();
            Map<String, String> document1 = createIndexedData("1");
            Map<String, String> document2 = createIndexedData("2");

            documents.add(document1);
            documents.add(document2);

            List<?> indexIds = template.requestBody("direct:bulk", documents, List.class);
            assertNotNull(indexIds, "indexIds should be set");
            assertCollectionSize("Indexed documents should match the size of documents", indexIds, documents.size());
        }

        @Test
        void testBulkWithString() {
            List<String> documents = List.of(
                    "{\"testBulkWithString1\": \"some-value\"}", "{\"testBulkWithString2\": \"some-value\"}");

            List<?> indexIds = template.requestBody("direct:bulk", documents, List.class);
            assertNotNull(indexIds, "indexIds should be set");
            assertCollectionSize("Indexed documents should match the size of documents", indexIds, documents.size());
        }

        @Test
        void testBulkWithBytes() {
            List<byte[]> documents = List.of(
                    "{\"testBulkWithBytes1\": \"some-value\"}".getBytes(StandardCharsets.UTF_8),
                    "{\"testBulkWithBytes2\": \"some-value\"}".getBytes(StandardCharsets.UTF_8));

            List<?> indexIds = template.requestBody("direct:bulk", documents, List.class);
            assertNotNull(indexIds, "indexIds should be set");
            assertCollectionSize("Indexed documents should match the size of documents", indexIds, documents.size());
        }

        @Test
        void testBulkWithReader() {
            List<Reader> documents = List.of(
                    new StringReader("{\"testBulkWithReader1\": \"some-value\"}"),
                    new StringReader("{\"testBulkWithReader2\": \"some-value\"}"));

            List<?> indexIds = template.requestBody("direct:bulk", documents, List.class);
            assertNotNull(indexIds, "indexIds should be set");
            assertCollectionSize("Indexed documents should match the size of documents", indexIds, documents.size());
        }

        @Test
        void testBulkWithInputStream() {
            List<InputStream> documents = List.of(
                    new ByteArrayInputStream(
                            "{\"testBulkWithInputStream1\": \"some-value\"}".getBytes(StandardCharsets.UTF_8)),
                    new ByteArrayInputStream(
                            "{\"testBulkWithInputStream2\": \"some-value\"}".getBytes(StandardCharsets.UTF_8)));

            List<?> indexIds = template.requestBody("direct:bulk", documents, List.class);
            assertNotNull(indexIds, "indexIds should be set");
            assertCollectionSize("Indexed documents should match the size of documents", indexIds, documents.size());
        }

        @Test
        void testBulkListRequestBody() {
            String prefix = createPrefix();

            // given
            List<Map<String, String>> request = new ArrayList<>();
            final HashMap<String, String> valueMap = new HashMap<>();
            valueMap.put("id", prefix + "baz");
            valueMap.put("content", prefix + "hello");
            request.add(valueMap);
            // when
            List<?> indexedDocumentIds = template.requestBody("direct:bulk", request, List.class);

            // then
            assertThat(indexedDocumentIds, notNullValue());
            assertThat(indexedDocumentIds.size(), equalTo(1));
        }

        @Test
        void testBulkRequestBody() {
            String prefix = createPrefix();

            // given
            BulkRequest.Builder builder = new BulkRequest.Builder();
            builder.operations(
                    new BulkOperation.Builder()
                            .index(new IndexOperation.Builder<>().index(prefix + "foo").id(prefix + "baz")
                                    .document(Map.of(prefix + "content", prefix + "hello")).build())
                            .build());

            // when
            @SuppressWarnings("unchecked")
            List<BulkResponseItem> response = template.requestBody("direct:bulk", builder, List.class);

            // then
            assertThat(response, notNullValue());
            assertThat(response.size(), equalTo(1));
            assertThat(response.get(0).error(), nullValue());
            assertThat(response.get(0).id(), equalTo(prefix + "baz"));
        }

        @Test
        void bulkRequestBody() {
            String prefix = createPrefix();

            // given
            BulkRequest.Builder builder = new BulkRequest.Builder();
            builder.operations(
                    new BulkOperation.Builder()
                            .index(new IndexOperation.Builder<>().index(prefix + "foo").id(prefix + "baz")
                                    .document(Map.of(prefix + "content", prefix + "hello")).build())
                            .build());
            // when
            @SuppressWarnings("unchecked")
            List<BulkResponseItem> response = template.requestBody("direct:bulk", builder, List.class);

            // then
            assertThat(response, notNullValue());
            assertEquals(prefix + "baz", response.get(0).id());
        }

        @Test
        void bulkDeleteOperation() {
            // given
            Map<String, String> map = createIndexedData();
            String indexId = template.requestBody("direct:index", map, String.class);
            assertNotNull(indexId, "indexId should be set");

            DeleteOperation.Builder builder = new DeleteOperation.Builder().index("twitter").id(indexId);
            // when
            @SuppressWarnings("unchecked")
            List<BulkResponseItem> response = template.requestBody("direct:bulk", List.of(builder), List.class);

            // then
            assertThat(response, notNullValue());
            assertEquals(indexId, response.get(0).id());
            GetResponse<?> resp = template.requestBody("direct:get", indexId, GetResponse.class);
            assertNotNull(resp, "response should not be null");
            assertNull(resp.source(), "response source should be null");
        }

        @Test
        void bulkCreateOperation() {
            // given
            String prefix = createPrefix();

            CreateOperation.Builder<?> builder
                    = new CreateOperation.Builder<>().index("twitter").document(Map.of(prefix + "content", prefix + "hello"));
            // when
            @SuppressWarnings("unchecked")
            List<BulkResponseItem> response = template.requestBody("direct:bulk", List.of(builder), List.class);

            // then
            assertThat(response, notNullValue());
            GetResponse<?> resp = template.requestBody("direct:get", response.get(0).id(), GetResponse.class);
            assertNotNull(resp, "response should not be null");
            assertNotNull(resp.source(), "response source should not be null");
        }

        @Test
        void bulkUpdateOperation() {
            Map<String, String> map = createIndexedData();
            String indexId = template.requestBody("direct:index", map, String.class);
            assertNotNull(indexId, "indexId should be set");

            UpdateOperation.Builder<?, ?> builder = new UpdateOperation.Builder<>()
                    .index("twitter").id(indexId)
                    .action(
                            new UpdateAction.Builder<>()
                                    .withJson(
                                            new StringReader(
                                                    String.format("{ \"doc\": {\"%skey2\": \"%svalue2\"}}",
                                                            createPrefix(), createPrefix())))
                                    .build());
            @SuppressWarnings("unchecked")
            List<BulkResponseItem> response = template.requestBody("direct:bulk", List.of(builder), List.class);

            //now, verify GET succeeded
            assertThat(response, notNullValue());
            GetResponse<?> resp = template.requestBody("direct:get", indexId, GetResponse.class);
            assertNotNull(resp, "response should not be null");
            assertNotNull(resp.source(), "response source should not be null");
            assertInstanceOf(ObjectNode.class, resp.source(), "response source should be a ObjectNode");
            assertTrue(((ObjectNode) resp.source()).has(createPrefix() + "key2"));
            assertEquals(createPrefix() + "value2", ((ObjectNode) resp.source()).get(createPrefix() + "key2").asText());
        }

        @Override
        protected RouteBuilder createRouteBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:index")
                            .to("elasticsearch://elasticsearch?operation=Index&indexName=twitter");
                    from("direct:get")
                            .to("elasticsearch://elasticsearch?operation=GetById&indexName=twitter");
                    from("direct:bulk")
                            .to("elasticsearch://elasticsearch?operation=Bulk&indexName=twitter");
                }
            };
        }
    }

    @Nested
    class ClusterIndex extends NestedTest {
        @Test
        void indexWithIpAndPort() throws Exception {
            Map<String, String> map = createIndexedData();
            Map<String, Object> headers = new HashMap<>();
            headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.Index);
            headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
            headers.put(ElasticsearchConstants.PARAM_INDEX_ID, "1");

            String indexId = template.requestBodyAndHeaders("direct:indexWithIpAndPort", map, headers, String.class);
            assertNotNull(indexId, "indexId should be set");

            indexId = template.requestBodyAndHeaders("direct:indexWithIpAndPort", map, headers, String.class);
            assertNotNull(indexId, "indexId should be set");

            assertTrue(client.get(new GetRequest.Builder().index("twitter").id("1").build(), ObjectNode.class).found(),
                    "Index id 1 must exists");
        }

        @Test
        void indexWithSnifferEnable() throws Exception {
            Map<String, String> map = createIndexedData();
            Map<String, Object> headers = new HashMap<>();
            headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.Index);
            headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "facebook");
            headers.put(ElasticsearchConstants.PARAM_INDEX_ID, "4");

            String indexId = template.requestBodyAndHeaders("direct:indexWithSniffer", map, headers, String.class);
            assertNotNull(indexId, "indexId should be set");

            assertTrue(client.get(new GetRequest.Builder().index("facebook").id("4").build(), ObjectNode.class).found(),
                    "Index id 4 must exists");

            final BasicResponseHandler responseHandler = new BasicResponseHandler();
            Request request = new Request("GET", "/_cluster/health?pretty");
            String body = responseHandler.handleEntity(restClient.performRequest(request).getEntity());
            assertStringContains(body, "\"number_of_data_nodes\" : 1");
        }

        @Override
        protected RouteBuilder createRouteBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:indexWithIpAndPort")
                            .to("elasticsearch://" + clusterName + "?operation=Index&indexName=twitter");
                    from("direct:indexWithSniffer")
                            .to("elasticsearch://" + clusterName + "?operation=Index&indexName=twitter&enableSniffer=true");
                }
            };
        }
    }

    @Nested
    class GetSearchDeleteExistsUpdate extends NestedTest {

        @Test
        void testIndexWithMap() {
            //first, Index a value
            Map<String, String> map = createIndexedData();
            String indexId = template.requestBody("direct:index", map, String.class);
            assertNotNull(indexId, "indexId should be set");

            //now, verify GET succeeded
            GetResponse<?> response = template.requestBody("direct:get", indexId, GetResponse.class);
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
            String indexId = template.requestBody("direct:index", "{\"testIndexWithString\": \"some-value\"}", String.class);
            assertNotNull(indexId, "indexId should be set");

            //now, verify GET succeeded
            GetResponse<?> response = template.requestBody("direct:get", indexId, GetResponse.class);
            assertNotNull(response, "response should not be null");
            assertNotNull(response.source(), "response source should not be null");
            assertInstanceOf(ObjectNode.class, response.source(), "response source should be a ObjectNode");
            assertTrue(((ObjectNode) response.source()).has("testIndexWithString"));
            assertEquals("some-value", ((ObjectNode) response.source()).get("testIndexWithString").asText());
        }

        @Test
        void testIndexWithReader() {
            //first, Index a value
            String indexId = template.requestBody("direct:index", new StringReader("{\"testIndexWithReader\": \"some-value\"}"),
                    String.class);
            assertNotNull(indexId, "indexId should be set");

            //now, verify GET succeeded
            GetResponse<?> response = template.requestBody("direct:get", indexId, GetResponse.class);
            assertNotNull(response, "response should not be null");
            assertNotNull(response.source(), "response source should not be null");
            assertInstanceOf(ObjectNode.class, response.source(), "response source should be a ObjectNode");
            assertTrue(((ObjectNode) response.source()).has("testIndexWithReader"));
            assertEquals("some-value", ((ObjectNode) response.source()).get("testIndexWithReader").asText());
        }

        @Test
        void testIndexWithBytes() {
            //first, Index a value
            String indexId = template.requestBody("direct:index",
                    "{\"testIndexWithBytes\": \"some-value\"}".getBytes(StandardCharsets.UTF_8), String.class);
            assertNotNull(indexId, "indexId should be set");

            //now, verify GET succeeded
            GetResponse<?> response = template.requestBody("direct:get", indexId, GetResponse.class);
            assertNotNull(response, "response should not be null");
            assertNotNull(response.source(), "response source should not be null");
            assertInstanceOf(ObjectNode.class, response.source(), "response source should be a ObjectNode");
            assertTrue(((ObjectNode) response.source()).has("testIndexWithBytes"));
            assertEquals("some-value", ((ObjectNode) response.source()).get("testIndexWithBytes").asText());
        }

        @Test
        void testIndexWithInputStream() {
            //first, Index a value
            String indexId = template.requestBody("direct:index",
                    new ByteArrayInputStream("{\"testIndexWithInputStream\": \"some-value\"}".getBytes(StandardCharsets.UTF_8)),
                    String.class);
            assertNotNull(indexId, "indexId should be set");

            //now, verify GET succeeded
            GetResponse<?> response = template.requestBody("direct:get", indexId, GetResponse.class);
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
            String indexId = template.requestBody("direct:index-product", product, String.class);
            assertNotNull(indexId, "indexId should be set");

            //now, verify GET succeeded
            GetResponse<?> response = template.requestBodyAndHeader("direct:get", indexId,
                    ElasticsearchConstants.PARAM_DOCUMENT_CLASS,
                    Product.class,
                    GetResponse.class);
            assertNotNull(response, "response should not be null");
            assertNotNull(response.source(), "response source should not be null");
            assertInstanceOf(Product.class,
                    response.source(), "response source should be a Product");
            Product actual = (Product) response.source();
            assertNotSame(product, actual);
            assertEquals(product, actual);
        }

        @Test
        void testGetWithString() {
            //first, Index a value
            Map<String, String> map = createIndexedData();
            String indexId = template.requestBody("direct:index", map, String.class);
            assertNotNull(indexId, "indexId should be set");

            //now, verify GET succeeded
            GetResponse<?> response = template.requestBody("direct:get", indexId, GetResponse.class);
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

            String indexId = template.requestBody("direct:index", product, String.class);
            assertNotNull(indexId, "indexId should be set");

            //now, verify GET succeeded
            GetResponse<?> response = template.requestBodyAndHeader(
                    "direct:get", indexId, ElasticsearchConstants.PARAM_DOCUMENT_CLASS,
                    Product.class,
                    GetResponse.class);
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
            String indexId = template.requestBody("direct:index", map, String.class);
            assertNotNull(indexId, "indexId should be set");

            //now, verify GET succeeded
            @SuppressWarnings("unchecked")
            List<MultiGetResponseItem<?>> response = template.requestBody("direct:multiget", List.of(indexId), List.class);
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

            String indexId = template.requestBody("direct:index", product, String.class);
            assertNotNull(indexId, "indexId should be set");

            //now, verify GET succeeded
            @SuppressWarnings("unchecked")
            List<MultiGetResponseItem<?>> response = template.requestBodyAndHeader(
                    "direct:multiget", List.of(indexId), ElasticsearchConstants.PARAM_DOCUMENT_CLASS,
                    Product.class,
                    List.class);
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
            String indexId = template.requestBody("direct:index", map, String.class);
            assertNotNull(indexId, "indexId should be set");

            //now, verify GET succeeded
            GetResponse<?> response = template.requestBody("direct:get", indexId, GetResponse.class);
            assertNotNull(response, "response should not be null");
            assertNotNull(response.source(), "response source should not be null");

            //now, perform Delete
            Result deleteResponse = template.requestBody("direct:delete", indexId, Result.class);
            assertNotNull(deleteResponse, "response should not be null");

            //now, verify GET fails to find the indexed value
            response = template.requestBody("direct:get", indexId, GetResponse.class);
            assertNotNull(response, "response should not be null");
            assertNull(response.source(), "response source should be null");
        }

        @Test
        void testSearchWithMapQuery() {
            //first, Index a value
            Map<String, String> map1 = Map.of("testSearchWithMapQuery1", "foo");
            Map<String, String> map2 = Map.of("testSearchWithMapQuery2", "bar");
            Map<String, Object> headers = new HashMap<>();
            headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.Bulk);
            headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
            template.requestBodyAndHeaders("direct:start", List.of(Map.of("doc", map1), Map.of("doc", map2)), headers,
                    String.class);

            // No match
            Map<String, Object> actualQuery = new HashMap<>();
            actualQuery.put("doc.testSearchWithMapQuery1", "bar");
            Map<String, Object> match = new HashMap<>();
            match.put("match", actualQuery);
            Map<String, Object> query = new HashMap<>();
            query.put("query", match);
            HitsMetadata<?> response = template.requestBody("direct:search", query, HitsMetadata.class);
            assertNotNull(response, "response should not be null");
            assertNotNull(response.total());
            assertEquals(0, response.total().value(), "response hits should be == 0");

            // Match
            actualQuery.put("doc.testSearchWithMapQuery1", "foo");
            // the result may see stale data so use Awaitility
            Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                HitsMetadata<?> resp = template.requestBody("direct:search", query, HitsMetadata.class);
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
        void testSearchWithStringQuery() {
            //first, Index a value
            Map<String, String> map1 = Map.of("testSearchWithStringQuery1", "foo");
            Map<String, String> map2 = Map.of("testSearchWithStringQuery2", "bar");
            Map<String, Object> headers = new HashMap<>();
            headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.Bulk);
            headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
            template.requestBodyAndHeaders("direct:start", List.of(Map.of("doc", map1), Map.of("doc", map2)), headers,
                    String.class);

            // No match
            String query = "{\n"
                           + "    \"query\" : { \"match\" : { \"doc.testSearchWithStringQuery1\" : \"bar\" }}\n"
                           + "}\n";

            HitsMetadata<?> response = template.requestBody("direct:search", query, HitsMetadata.class);
            assertNotNull(response, "response should not be null");
            assertNotNull(response.total());
            assertEquals(0, response.total().value(), "response hits should be == 0");

            // Match
            String q = "{\n"
                       + "    \"query\" : { \"match\" : { \"doc.testSearchWithStringQuery1\" : \"foo\" }}\n"
                       + "}\n";
            // the result may see stale data so use Awaitility
            Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                HitsMetadata<?> resp = template.requestBody("direct:search", q, HitsMetadata.class);
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
        void testSearchWithBuilder() {
            //first, Index a value
            Map<String, String> map1 = Map.of("testSearchWithBuilder1", "foo");
            Map<String, String> map2 = Map.of("testSearchWithBuilder2", "bar");
            Map<String, Object> headers = new HashMap<>();
            headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.Bulk);
            headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
            template.requestBodyAndHeaders("direct:start", List.of(Map.of("doc", map1), Map.of("doc", map2)), headers,
                    String.class);

            // No match
            SearchRequest.Builder builder = new SearchRequest.Builder()
                    .query(new Query.Builder()
                            .match(new MatchQuery.Builder().field("doc.testSearchWithBuilder1").query("bar").build()).build());
            HitsMetadata<?> response = template.requestBody("direct:search", builder, HitsMetadata.class);
            assertNotNull(response, "response should not be null");
            assertNotNull(response.total());
            assertEquals(0, response.total().value(), "response hits should be == 0");

            // Match
            // the result may see stale data so use Awaitility
            Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                SearchRequest.Builder b = new SearchRequest.Builder()
                        .query(new Query.Builder()
                                .match(new MatchQuery.Builder().field("doc.testSearchWithBuilder1").query("foo").build())
                                .build());

                HitsMetadata<?> resp = template.requestBody("direct:search", b, HitsMetadata.class);
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
        void testSearchWithDocumentType() {
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
            headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.Bulk);
            headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
            template.requestBodyAndHeaders("direct:start", List.of(product1, product2), headers, String.class);

            // No match
            SearchRequest.Builder builder = new SearchRequest.Builder()
                    .query(new Query.Builder().match(new MatchQuery.Builder().field("doc.id").query("bar").build()).build());
            HitsMetadata<?> response = template.requestBodyAndHeader(
                    "direct:search", builder, ElasticsearchConstants.PARAM_DOCUMENT_CLASS,
                    Product.class,
                    HitsMetadata.class);
            assertNotNull(response, "response should not be null");
            assertNotNull(response.total());
            assertEquals(0, response.total().value(), "response hits should be == 0");

            // Match
            // the result may see stale data so use Awaitility
            Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                SearchRequest.Builder b = new SearchRequest.Builder()
                        .query(new Query.Builder().match(new MatchQuery.Builder().field("id").query("2020").build()).build());

                HitsMetadata<?> resp = template.requestBodyAndHeader(
                        "direct:search", b, ElasticsearchConstants.PARAM_DOCUMENT_CLASS,
                        Product.class,
                        HitsMetadata.class);
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
        void testMultiSearch() {
            //first, Index a value
            Map<String, String> map = createIndexedData();
            String indexId = template.requestBody("direct:index", map, String.class);
            assertNotNull(indexId, "indexId should be set");

            // the result may see stale data so use Awaitility
            Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                //now, verify GET succeeded
                MsearchRequest.Builder builder = new MsearchRequest.Builder().index("twitter").searches(
                        new RequestItem.Builder().header(new MultisearchHeader.Builder().build())
                                .body(new MultisearchBody.Builder().query(b -> b.matchAll(x -> x)).build()).build(),
                        new RequestItem.Builder().header(new MultisearchHeader.Builder().build())
                                .body(new MultisearchBody.Builder().query(b -> b.matchAll(x -> x)).build()).build());
                @SuppressWarnings("unchecked")
                List<MultiSearchResponseItem<?>> response = template.requestBody("direct:multiSearch", builder, List.class);
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
        void testMultiSearchWithDocumentType() {
            //first, Index a value
            Product product = new Product();
            product.setId("book-world-records-2022");
            product.setStockAvailable(1);
            product.setPrice(100);
            product.setDescription("The book of the year!");
            product.setName("Guinness book of records 2022");
            String indexId = template.requestBodyAndHeader("direct:index", product, ElasticsearchConstants.PARAM_INDEX_NAME,
                    "multi-search", String.class);
            assertNotNull(indexId, "indexId should be set");

            // the result may see stale data so use Awaitility
            Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                //now, verify GET succeeded
                MsearchRequest.Builder builder = new MsearchRequest.Builder().index("multi-search").searches(
                        new RequestItem.Builder().header(new MultisearchHeader.Builder().build())
                                .body(new MultisearchBody.Builder().query(b -> b.matchAll(x -> x)).build()).build(),
                        new RequestItem.Builder().header(new MultisearchHeader.Builder().build())
                                .body(new MultisearchBody.Builder().query(b -> b.matchAll(x -> x)).build()).build());
                @SuppressWarnings("unchecked")
                List<MultiSearchResponseItem<?>> response = template.requestBodyAndHeaders(
                        "direct:multiSearch", builder,
                        Map.of(
                                ElasticsearchConstants.PARAM_INDEX_NAME, "multi-search",
                                ElasticsearchConstants.PARAM_DOCUMENT_CLASS,
                                Product.class),
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
            String indexId = template.requestBody("direct:index", map, String.class);
            assertNotNull(indexId, "indexId should be set");

            Map<String, String> newMap = new HashMap<>();
            newMap.put(createPrefix() + "key2", createPrefix() + "value2");
            Map<String, Object> headers = new HashMap<>();
            headers.put(ElasticsearchConstants.PARAM_INDEX_ID, indexId);
            indexId = template.requestBodyAndHeaders("direct:update", Map.of("doc", newMap), headers, String.class);
            assertNotNull(indexId, "indexId should be set");

            //now, verify GET succeeded
            GetResponse<?> response = template.requestBody("direct:get", indexId, GetResponse.class);
            assertNotNull(response, "response should not be null");
            assertNotNull(response.source(), "response source should not be null");
            assertInstanceOf(ObjectNode.class, response.source(), "response source should be a ObjectNode");
            assertTrue(((ObjectNode) response.source()).has(createPrefix() + "key2"));
            assertEquals(createPrefix() + "value2", ((ObjectNode) response.source()).get(createPrefix() + "key2").asText());
        }

        @Test
        void testGetWithHeaders() {
            //first, Index a value
            Map<String, String> map = createIndexedData();
            Map<String, Object> headers = new HashMap<>();
            headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.Index);
            headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");

            String indexId = template.requestBodyAndHeaders("direct:start", map, headers, String.class);

            //now, verify GET
            headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.GetById);
            GetResponse<?> response = template.requestBodyAndHeaders("direct:start", indexId, headers, GetResponse.class);
            assertNotNull(response, "response should not be null");
            assertNotNull(response.source(), "response source should not be null");
        }

        @Test
        void testExistsWithHeaders() {
            //first, Index a value
            Map<String, String> map = createIndexedData();
            Map<String, Object> headers = new HashMap<>();
            headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.Index);
            headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");

            template.requestBodyAndHeaders("direct:start", map, headers, String.class);

            //now, verify GET
            headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.Exists);
            headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
            Boolean exists = template.requestBodyAndHeaders("direct:exists", "", headers, Boolean.class);
            assertNotNull(exists, "response should not be null");
            assertTrue(exists, "Index should exists");
        }

        @Test
        void testNotExistsWithHeaders() {
            Map<String, Object> headers = new HashMap<>();
            headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.Exists);
            headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter-tweet");
            Boolean exists = template.requestBodyAndHeaders("direct:exists", "", headers, Boolean.class);
            assertNotNull(exists, "response should not be null");
            assertFalse(exists, "Index should not exists");
        }

        @Test
        void testDeleteWithHeaders() {
            //first, Index a value
            Map<String, String> map = createIndexedData();
            Map<String, Object> headers = new HashMap<>();
            headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.Index);
            headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");

            String indexId = template.requestBodyAndHeaders("direct:start", map, headers, String.class);

            //now, verify GET
            headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.GetById);
            GetResponse<?> response = template.requestBodyAndHeaders("direct:start", indexId, headers, GetResponse.class);
            assertNotNull(response, "response should not be null");
            assertNotNull(response.source(), "response source should not be null");

            //now, perform Delete
            headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.Delete);
            Result deleteResponse
                    = template.requestBodyAndHeaders("direct:start", indexId, headers, Result.class);
            assertEquals(Result.Deleted, deleteResponse, "response should not be null");

            //now, verify GET fails to find the indexed value
            headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.GetById);
            response = template.requestBodyAndHeaders("direct:start", indexId, headers, GetResponse.class);
            assertNotNull(response, "response should not be null");
            assertNull(response.source(), "response source should be null");
        }

        @Test
        void testUpdateWithIDInHeader() {
            Map<String, String> map = createIndexedData();
            Map<String, Object> headers = new HashMap<>();
            headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.Index);
            headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
            headers.put(ElasticsearchConstants.PARAM_INDEX_ID, "123");

            String indexId = template.requestBodyAndHeaders("direct:start", map, headers, String.class);
            assertNotNull(indexId, "indexId should be set");
            assertEquals("123", indexId, "indexId should be equals to the provided id");

            headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.Update);

            indexId = template.requestBodyAndHeaders("direct:start", Map.of("doc", map), headers, String.class);
            assertNotNull(indexId, "indexId should be set");
            assertEquals("123", indexId, "indexId should be equals to the provided id");
        }

        @Test
        void testGetRequestBody() {
            String prefix = createPrefix();

            // given
            GetRequest.Builder builder = new GetRequest.Builder().index(prefix + "foo");

            // when
            String documentId = template.requestBody("direct:index",
                    new IndexRequest.Builder<>()
                            .index(prefix + "foo")
                            .id(prefix + "testId")
                            .document(Map.of(prefix + "content", prefix + "hello")),
                    String.class);
            GetResponse<?> response = template.requestBody("direct:get",
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
            String prefix = createPrefix();

            // given
            String documentId = template.requestBody("direct:index",
                    new IndexRequest.Builder<>()
                            .index(prefix + "foo")
                            .id(prefix + "testId")
                            .document(Map.of(prefix + "content", prefix + "hello")),
                    String.class);

            GetResponse<?> getResponse = template.requestBodyAndHeader(
                    "direct:get", documentId, ElasticsearchConstants.PARAM_INDEX_NAME, prefix + "foo", GetResponse.class);
            assertNotNull(getResponse, "response should not be null");
            assertNotNull(getResponse.source(), "response source should not be null");

            // when
            Result response
                    = template.requestBody("direct:delete", new DeleteRequest.Builder().index(prefix + "foo").id(documentId),
                            Result.class);

            // then
            assertThat(response, equalTo(Result.Deleted));
            getResponse = template.requestBodyAndHeader(
                    "direct:get", documentId, ElasticsearchConstants.PARAM_INDEX_NAME, prefix + "foo", GetResponse.class);
            assertNotNull(getResponse, "response should not be null");
            assertNull(getResponse.source(), "response source should be null");
        }

        @Test
        void testUpdateWithString() {
            Map<String, String> map = createIndexedData();
            String indexId = template.requestBody("direct:index", map, String.class);
            assertNotNull(indexId, "indexId should be set");
            String key = map.keySet().iterator().next();
            Object body = String.format("{ \"doc\": {\"%s\" : \"testUpdateWithString-updated\"}}", key);

            Map<String, Object> headers = new HashMap<>();
            headers.put(ElasticsearchConstants.PARAM_INDEX_ID, indexId);
            indexId = template.requestBodyAndHeaders("direct:update", body, headers, String.class);
            assertNotNull(indexId, "indexId should be set");

            GetResponse<?> response = template.requestBody("direct:get", indexId, GetResponse.class);
            assertThat(response.source(), notNullValue());
            ObjectNode node = (ObjectNode) response.source();
            assertThat(node.has(key), equalTo(true));
            assertThat(node.get(key).asText(), equalTo("testUpdateWithString-updated"));
        }

        @Test
        void testUpdateWithReader() {
            Map<String, String> map = createIndexedData();
            String indexId = template.requestBody("direct:index", map, String.class);
            assertNotNull(indexId, "indexId should be set");
            String key = map.keySet().iterator().next();
            Object body = new StringReader(String.format("{ \"doc\": {\"%s\" : \"testUpdateWithReader-updated\"}}", key));

            Map<String, Object> headers = new HashMap<>();
            headers.put(ElasticsearchConstants.PARAM_INDEX_ID, indexId);
            indexId = template.requestBodyAndHeaders("direct:update", body, headers, String.class);
            assertNotNull(indexId, "indexId should be set");

            GetResponse<?> response = template.requestBody("direct:get", indexId, GetResponse.class);
            assertThat(response.source(), notNullValue());
            ObjectNode node = (ObjectNode) response.source();
            assertThat(node.has(key), equalTo(true));
            assertThat(node.get(key).asText(), equalTo("testUpdateWithReader-updated"));
        }

        @Test
        void testUpdateWithBytes() {
            Map<String, String> map = createIndexedData();
            String indexId = template.requestBody("direct:index", map, String.class);
            assertNotNull(indexId, "indexId should be set");
            String key = map.keySet().iterator().next();
            Object body
                    = String.format("{ \"doc\": {\"%s\" : \"testUpdateWithBytes-updated\"}}", key)
                            .getBytes(StandardCharsets.UTF_8);

            Map<String, Object> headers = new HashMap<>();
            headers.put(ElasticsearchConstants.PARAM_INDEX_ID, indexId);
            indexId = template.requestBodyAndHeaders("direct:update", body, headers, String.class);
            assertNotNull(indexId, "indexId should be set");

            GetResponse<?> response = template.requestBody("direct:get", indexId, GetResponse.class);
            assertThat(response.source(), notNullValue());
            ObjectNode node = (ObjectNode) response.source();
            assertThat(node.has(key), equalTo(true));
            assertThat(node.get(key).asText(), equalTo("testUpdateWithBytes-updated"));
        }

        @Test
        void testUpdateWithInputStream() {
            Map<String, String> map = createIndexedData();
            String indexId = template.requestBody("direct:index", map, String.class);
            assertNotNull(indexId, "indexId should be set");
            String key = map.keySet().iterator().next();
            Object body = new ByteArrayInputStream(
                    String.format("{ \"doc\": {\"%s\" : \"testUpdateWithInputStream-updated\"}}", key)
                            .getBytes(StandardCharsets.UTF_8));

            Map<String, Object> headers = new HashMap<>();
            headers.put(ElasticsearchConstants.PARAM_INDEX_ID, indexId);
            indexId = template.requestBodyAndHeaders("direct:update", body, headers, String.class);
            assertNotNull(indexId, "indexId should be set");

            GetResponse<?> response = template.requestBody("direct:get", indexId, GetResponse.class);
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

            String indexId = template.requestBody("direct:index", product, String.class);
            assertNotNull(indexId, "indexId should be set");

            Product productUpdate = new Product();
            productUpdate.setStockAvailable(250);
            productUpdate.setPrice(82);
            productUpdate.setName("Guinness book of records 2010 2nd edition");

            Map<String, Object> headers = new HashMap<>();
            headers.put(ElasticsearchConstants.PARAM_INDEX_ID, indexId);
            headers.put(ElasticsearchConstants.PARAM_DOCUMENT_CLASS,
                    Product.class);
            indexId = template.requestBodyAndHeaders("direct:update", productUpdate, headers, String.class);
            assertNotNull(indexId, "indexId should be set");

            GetResponse<?> response = template.requestBodyAndHeader(
                    "direct:get", indexId, ElasticsearchConstants.PARAM_DOCUMENT_CLASS,
                    Product.class,
                    GetResponse.class);
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
                            .to("elasticsearch://elasticsearch?operation=Index");
                    from("direct:index")
                            .to("elasticsearch://elasticsearch?operation=Index&indexName=twitter");
                    from("direct:index-product")
                            .toF("elasticsearch://elasticsearch?operation=Index&indexName=twitter&documentClass=%s",
                                    Product.class.getName());
                    from("direct:get")
                            .to("elasticsearch://elasticsearch?operation=GetById&indexName=twitter");
                    from("direct:multiget")
                            .to("elasticsearch://elasticsearch?operation=MultiGet&indexName=twitter");
                    from("direct:delete")
                            .to("elasticsearch://elasticsearch?operation=Delete&indexName=twitter");
                    from("direct:search")
                            .to("elasticsearch://elasticsearch?operation=Search&indexName=twitter");
                    from("direct:search-1")
                            .to("elasticsearch://elasticsearch?operation=Search");
                    from("direct:multiSearch")
                            .to("elasticsearch://elasticsearch?operation=MultiSearch");
                    from("direct:update")
                            .to("elasticsearch://elasticsearch?operation=Update&indexName=twitter");
                    from("direct:exists")
                            .to("elasticsearch://elasticsearch?operation=Exists");
                }
            };
        }
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

    @Nested
    class Index extends NestedTest {

        @Test
        void testIndex() {
            Map<String, String> map = createIndexedData();
            String indexId = template.requestBody("direct:index", map, String.class);
            assertNotNull(indexId, "indexId should be set");
        }

        @Test
        void testIndexDeleteWithBuilder() {
            Map<String, String> map = createIndexedData();
            String indexId = template.requestBody("direct:index", map, String.class);
            assertNotNull(indexId, "indexId should be set");

            boolean exists = template.requestBody("direct:exists", null, Boolean.class);
            assertTrue(exists, "index should be present");

            DeleteIndexRequest.Builder builder = new DeleteIndexRequest.Builder().index("twitter");
            Boolean status = template.requestBody("direct:deleteIndex", builder, Boolean.class);
            assertEquals(true, status, "status should be 200");

            exists = template.requestBody("direct:exists", null, Boolean.class);
            assertFalse(exists, "index should be absent");
        }

        @Test
        void testIndexDeleteWithString() {
            Map<String, String> map = createIndexedData();
            String indexId = template.requestBody("direct:index", map, String.class);
            assertNotNull(indexId, "indexId should be set");

            boolean exists = template.requestBody("direct:exists", null, Boolean.class);
            assertTrue(exists, "index should be present");

            Boolean status = template.requestBody("direct:deleteIndex", "twitter", Boolean.class);
            assertEquals(true, status, "status should be 200");

            exists = template.requestBody("direct:exists", null, Boolean.class);
            assertFalse(exists, "index should be absent");
        }

        @Test
        void testIndexWithHeaders() {
            Map<String, String> map = createIndexedData();
            Map<String, Object> headers = new HashMap<>();
            headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.Index);
            headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");

            String indexId = template.requestBodyAndHeaders("direct:start", map, headers, String.class);
            assertNotNull(indexId, "indexId should be set");
        }

        @Test
        void testIndexWithIDInHeader() {
            Map<String, String> map = createIndexedData();
            Map<String, Object> headers = new HashMap<>();
            headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.Index);
            headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
            headers.put(ElasticsearchConstants.PARAM_INDEX_ID, "123");

            String indexId = template.requestBodyAndHeaders("direct:start", map, headers, String.class);
            assertNotNull(indexId, "indexId should be set");
            assertEquals("123", indexId, "indexId should be equals to the provided id");
        }

        @Test
        void testExists() {
            boolean exists = template.requestBodyAndHeader(
                    "direct:exists", null, ElasticsearchConstants.PARAM_INDEX_NAME, "test_exists", Boolean.class);
            assertFalse(exists, "index should be absent");

            Map<String, String> map = createIndexedData();
            template.sendBodyAndHeader("direct:index", map, ElasticsearchConstants.PARAM_INDEX_NAME, "test_exists");

            exists = template.requestBodyAndHeader(
                    "direct:exists", null, ElasticsearchConstants.PARAM_INDEX_NAME, "test_exists", Boolean.class);
            assertTrue(exists, "index should be present");
        }

        @Override
        protected RouteBuilder createRouteBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start")
                            .to("elasticsearch://elasticsearch");
                    from("direct:index")
                            .to("elasticsearch://elasticsearch?operation=Index&indexName=twitter");
                    from("direct:exists")
                            .to("elasticsearch://elasticsearch?operation=Exists&indexName=twitter");
                    from("direct:deleteIndex")
                            .to("elasticsearch://elasticsearch?operation=DeleteIndex&indexName=twitter");
                }
            };
        }
    }

    @Nested
    class Ping extends NestedTest {

        @Test
        void testPing() {
            boolean pingResult = template.requestBody("direct:ping", "test", Boolean.class);
            assertTrue(pingResult, "indexId should be set");
        }

        @Override
        protected RouteBuilder createRouteBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:ping")
                            .to("elasticsearch://elasticsearch?operation=Ping");
                }
            };
        }
    }

    @Nested
    class ScrollSearch extends NestedTest {

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
            Response refreshResponse
                    = getClient().performRequest(new Request("post", "/" + TWITTER_ES_INDEX_NAME + "/_refresh"));
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

    @Nested
    class SizeLimit extends NestedTest {

        @Test
        void testSize() {
            //put 4
            template.requestBody("direct:index", getContent("content"), String.class);
            template.requestBody("direct:index", getContent("content1"), String.class);
            template.requestBody("direct:index", getContent("content2"), String.class);
            template.requestBody("direct:index", getContent("content3"), String.class);

            String query = "{\"query\":{\"match_all\": {}}}";

            // the result may see stale data so use Awaitility
            Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> {
                HitsMetadata<?> searchWithSizeTwo = template.requestBody("direct:searchWithSizeTwo", query, HitsMetadata.class);
                HitsMetadata<?> searchFrom3 = template.requestBody("direct:searchFrom3", query, HitsMetadata.class);
                return searchWithSizeTwo.hits().size() == 2 && searchFrom3.hits().size() == 1;
            });
        }

        @Override
        protected RouteBuilder createRouteBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:index")
                            .to("elasticsearch://elasticsearch?operation=Index&indexName=twitter");
                    from("direct:searchWithSizeTwo")
                            .to("elasticsearch://elasticsearch?operation=Search&indexName=twitter&size=2");
                    from("direct:searchFrom3")
                            .to("elasticsearch://elasticsearch?operation=Search&indexName=twitter&from=3");
                }
            };
        }

        private Map<String, String> getContent(String content) {
            Map<String, String> map = new HashMap<>();
            map.put("content", content);
            return map;
        }
    }
}
