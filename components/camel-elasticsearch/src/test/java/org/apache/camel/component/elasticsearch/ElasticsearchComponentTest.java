/**
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.notNullValue;


public class ElasticsearchComponentTest extends CamelTestSupport {

    @BeforeClass
    public static void cleanupOnce() {
        deleteDirectory("target/data");
    }

    @Override
    public boolean isCreateCamelContextPerClass() {
        // let's speed up the tests using the same context
        return true;
    }

    /**
     * As we don't delete the {@code target/data} folder for <b>each</b> test
     * below (otherwise they would run much slower), we need to make sure
     * there's no side effect of the same used data through creating unique
     * indexes.
     */
    private Map<String, String> createIndexedData(String... additionalPrefixes) {
        String prefix = createPrefix();

        // take over any potential prefixes we may have been asked for
        if (additionalPrefixes.length > 0) {
            StringBuilder sb = new StringBuilder(prefix);
            for (String additionalPrefix : additionalPrefixes) {
                sb.append(additionalPrefix).append("-");
            }
            prefix = sb.toString();
        }

        String key = prefix + "key";
        String value = prefix + "value";
        log.info("Creating indexed data using the key/value pair {} => {}", key, value);

        Map<String, String> map = new HashMap<String, String>();
        map.put(key, value);
        return map;
    }

    private String createPrefix() {
        // make use of the test method name to avoid collision
        return getTestMethodName().toLowerCase() + "-";
    }

    @Test
    public void testIndex() throws Exception {
        Map<String, String> map = createIndexedData();
        String indexId = template.requestBody("direct:index", map, String.class);
        assertNotNull("indexId should be set", indexId);
    }

    @Test
    public void testIndexWithReplication() throws Exception {
        Map<String, String> map = createIndexedData();
        String indexId = template.requestBody("direct:indexWithReplication", map, String.class);
        assertNotNull("indexId should be set", indexId);
    }

    @Test
    public void testIndexWithWriteConsistency() throws Exception {
        Map<String, String> map = createIndexedData();
        String indexId = template.requestBody("direct:indexWithWriteConsistency", map, String.class);
        assertNotNull("indexId should be set", indexId);
    }

    @Test
    public void testBulkIndex() throws Exception {
        List<Map<String, String>> documents = new ArrayList<Map<String, String>>();
        Map<String, String> document1 = createIndexedData("1");
        Map<String, String> document2 = createIndexedData("2");

        documents.add(document1);
        documents.add(document2);

        List<?> indexIds = template.requestBody("direct:bulk_index", documents, List.class);
        assertNotNull("indexIds should be set", indexIds);
        assertCollectionSize("Indexed documents should match the size of documents", indexIds, documents.size());
    }

    @Test
    public void testGet() throws Exception {
        //first, INDEX a value
        Map<String, String> map = createIndexedData();
        sendBody("direct:index", map);
        String indexId = template.requestBody("direct:index", map, String.class);
        assertNotNull("indexId should be set", indexId);

        //now, verify GET succeeded
        GetResponse response = template.requestBody("direct:get", indexId, GetResponse.class);
        assertNotNull("response should not be null", response);
        assertNotNull("response source should not be null", response.getSource());
    }

    @Test
    public void testDelete() throws Exception {
        //first, INDEX a value
        Map<String, String> map = createIndexedData();
        sendBody("direct:index", map);
        String indexId = template.requestBody("direct:index", map, String.class);
        assertNotNull("indexId should be set", indexId);

        //now, verify GET succeeded
        GetResponse response = template.requestBody("direct:get", indexId, GetResponse.class);
        assertNotNull("response should not be null", response);
        assertNotNull("response source should not be null", response.getSource());

        //now, perform DELETE
        DeleteResponse deleteResponse = template.requestBody("direct:delete", indexId, DeleteResponse.class);
        assertNotNull("response should not be null", deleteResponse);

        //now, verify GET fails to find the indexed value
        response = template.requestBody("direct:get", indexId, GetResponse.class);
        assertNotNull("response should not be null", response);
        assertNull("response source should be null", response.getSource());
    }

    @Test
    public void testSearch() throws Exception {
        //first, INDEX a value
        Map<String, String> map = createIndexedData();
        sendBody("direct:index", map);

        //now, verify GET succeeded
        Map<String, Object> actualQuery = new HashMap<String, Object>();
        actualQuery.put("content", "searchtest");
        Map<String, Object> match = new HashMap<String, Object>();
        match.put("match", actualQuery);
        Map<String, Object> query = new HashMap<String, Object>();
        query.put("query", match);
        SearchResponse response = template.requestBody("direct:search", query, SearchResponse.class);
        assertNotNull("response should not be null", response);
        assertNotNull("response hits should be == 1", response.getHits().totalHits());
    }

    @Test
    public void testIndexWithHeaders() throws Exception {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchConstants.OPERATION_INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");

        String indexId = template.requestBodyAndHeaders("direct:start", map, headers, String.class);
        assertNotNull("indexId should be set", indexId);
    }

    @Test
    public void testIndexWithIDInHeader() throws Exception {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchConstants.OPERATION_INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");
        headers.put(ElasticsearchConstants.PARAM_INDEX_ID, "123");

        String indexId = template.requestBodyAndHeaders("direct:start", map, headers, String.class);
        assertNotNull("indexId should be set", indexId);
        assertEquals("indexId should be equals to the provided id", "123", indexId);
    }

    @Test
    @Ignore("need to setup the cluster IP for this test")
    public void indexWithIp()  throws Exception {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchConstants.OPERATION_INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");

        String indexId = template.requestBodyAndHeaders("direct:indexWithIp", map, headers, String.class);
        assertNotNull("indexId should be set", indexId);
    }

    @Test
    @Ignore("need to setup the cluster IP/Port for this test")
    public void indexWithIpAndPort()  throws Exception {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchConstants.OPERATION_INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");

        String indexId = template.requestBodyAndHeaders("direct:indexWithIpAndPort", map, headers, String.class);
        assertNotNull("indexId should be set", indexId);
    }

    @Test
    @Ignore("need to setup the cluster with multiple nodes for this test")
    public void indexWithTransportAddresses()  throws Exception {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchConstants.OPERATION_INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");

        String indexId = template.requestBodyAndHeaders("direct:indexWithTransportAddresses", map, headers, String.class);
        assertNotNull("indexId should be set", indexId);
    }

    @Test
    @Ignore("need to setup the cluster with multiple nodes for this test")
    public void indexWithIpAndTransportAddresses()  throws Exception {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchConstants.OPERATION_INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");

        //should ignore transport addresses configuration
        String indexId = template.requestBodyAndHeaders("direct:indexWithIpAndTransportAddresses", map, headers, String.class);
        assertNotNull("indexId should be set", indexId);
    }

    @Test
    public void testGetWithHeaders() throws Exception {
        //first, INDEX a value
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchConstants.OPERATION_INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");

        String indexId = template.requestBodyAndHeaders("direct:start", map, headers, String.class);

        //now, verify GET
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchConstants.OPERATION_GET_BY_ID);
        GetResponse response = template.requestBodyAndHeaders("direct:start", indexId, headers, GetResponse.class);
        assertNotNull("response should not be null", response);
        assertNotNull("response source should not be null", response.getSource());
    }

    @Test
    public void testDeleteWithHeaders() throws Exception {
        //first, INDEX a value
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchConstants.OPERATION_INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");

        String indexId = template.requestBodyAndHeaders("direct:start", map, headers, String.class);

        //now, verify GET
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchConstants.OPERATION_GET_BY_ID);
        GetResponse response = template.requestBodyAndHeaders("direct:start", indexId, headers, GetResponse.class);
        assertNotNull("response should not be null", response);
        assertNotNull("response source should not be null", response.getSource());

        //now, perform DELETE
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchConstants.OPERATION_DELETE);
        DeleteResponse deleteResponse = template.requestBodyAndHeaders("direct:start", indexId, headers, DeleteResponse.class);
        assertNotNull("response should not be null", deleteResponse);

        //now, verify GET fails to find the indexed value
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchConstants.OPERATION_GET_BY_ID);
        response = template.requestBodyAndHeaders("direct:start", indexId, headers, GetResponse.class);
        assertNotNull("response should not be null", response);
        assertNull("response source should be null", response.getSource());
    }

    @Test
    public void indexRequestBody() throws Exception {
        String prefix = createPrefix();
        
        // given
        IndexRequest request = new IndexRequest(prefix + "foo", prefix + "bar", prefix + "testId");
        request.source("{\"" + prefix + "content\": \"" + prefix + "hello\"}");

        // when
        String documentId = template.requestBody("direct:index", request, String.class);

        // then
        assertThat(documentId, equalTo(prefix + "testId"));
    }

    @Test
    public void getRequestBody() throws Exception {
        String prefix = createPrefix();

        // given
        GetRequest request = new GetRequest(prefix + "foo").type(prefix + "bar");

        // when
        String documentId = template.requestBody("direct:index",
                new IndexRequest(prefix + "foo", prefix + "bar", prefix + "testId")
                        .source("{\"" + prefix + "content\": \"" + prefix + "hello\"}"), String.class);
        GetResponse response = template.requestBody("direct:get",
                request.id(documentId), GetResponse.class);

        // then
        assertThat(response, notNullValue());
        assertThat(prefix + "hello", equalTo(response.getSourceAsMap().get(prefix + "content")));
    }

    @Test
    public void deleteRequestBody() throws Exception {
        String prefix = createPrefix();

        // given
        DeleteRequest request = new DeleteRequest(prefix + "foo").type(prefix + "bar");

        // when
        String documentId = template.requestBody("direct:index",
                new IndexRequest("" + prefix + "foo", "" + prefix + "bar", "" + prefix + "testId")
                        .source("{\"" + prefix + "content\": \"" + prefix + "hello\"}"), String.class);
        DeleteResponse response = template.requestBody("direct:delete",
                request.id(documentId), DeleteResponse.class);

        // then
        assertThat(response, notNullValue());
        assertThat(documentId, equalTo(response.getId()));
    }

    @Test
    public void bulkIndexRequestBody() throws Exception {
        String prefix = createPrefix();

        // given
        BulkRequest request = new BulkRequest();
        request.add(new IndexRequest(prefix + "foo", prefix + "bar", prefix + "baz")
                .source("{\"" + prefix + "content\": \"" + prefix + "hello\"}"));

        // when
        @SuppressWarnings("unchecked")
        List<String> indexedDocumentIds = template.requestBody("direct:bulk_index", request, List.class);

        // then
        assertThat(indexedDocumentIds, notNullValue());
        assertThat(indexedDocumentIds.size(), equalTo(1));
        assertThat(indexedDocumentIds, hasItem(prefix + "baz"));
    }

    @Test
    public void bulkRequestBody() throws Exception {
        String prefix = createPrefix();

        // given
        BulkRequest request = new BulkRequest();
        request.add(new IndexRequest(prefix + "foo", prefix + "bar", prefix + "baz")
                .source("{\"" + prefix + "content\": \"" + prefix + "hello\"}"));

        // when
        BulkResponse response = template.requestBody("direct:bulk", request, BulkResponse.class);

        // then
        assertThat(response, notNullValue());
        assertEquals(prefix + "baz", response.getItems()[0].getId());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("elasticsearch://local");
                from("direct:index").to("elasticsearch://local?operation=INDEX&indexName=twitter&indexType=tweet");
                from("direct:indexWithReplication").to("elasticsearch://local?operation=INDEX&indexName=twitter&indexType=tweet&replicationType=SYNC");
                from("direct:indexWithWriteConsistency").to("elasticsearch://local?operation=INDEX&indexName=twitter&indexType=tweet&consistencyLevel=ONE");
                from("direct:get").to("elasticsearch://local?operation=GET_BY_ID&indexName=twitter&indexType=tweet");
                from("direct:delete").to("elasticsearch://local?operation=DELETE&indexName=twitter&indexType=tweet");
                from("direct:search").to("elasticsearch://local?operation=SEARCH&indexName=twitter&indexType=tweet");
                from("direct:bulk_index").to("elasticsearch://local?operation=BULK_INDEX&indexName=twitter&indexType=tweet");
                from("direct:bulk").to("elasticsearch://local?operation=BULK&indexName=twitter&indexType=tweet");
                //from("direct:indexWithIp").to("elasticsearch://elasticsearch?operation=INDEX&indexName=twitter&indexType=tweet&ip=localhost");
                //from("direct:indexWithIpAndPort").to("elasticsearch://elasticsearch?operation=INDEX&indexName=twitter&indexType=tweet&ip=localhost&port=9300");
                //from("direct:indexWithTransportAddresses").to("elasticsearch://elasticsearch?operation=INDEX&indexName=twitter&indexType=tweet&transportAddresses=localhost:9300,localhost:9301");
                //from("direct:indexWithIpAndTransportAddresses").
                //to("elasticsearch://elasticsearch?operation=INDEX&indexName=twitter&indexType=tweet&ip=localhost&port=9300&transportAddresses=localhost:4444,localhost:5555");
            }
        };
    }
}
