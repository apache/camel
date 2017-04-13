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
package org.apache.camel.component.elasticsearch5;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequest.Item;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

public class ElasticsearchGetSearchDeleteExistsUpdateTest extends ElasticsearchBaseTest {

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
    public void testSearchWithMapQuery() throws Exception {
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
    public void testSearchWithStringQuery() throws Exception {
        //first, INDEX a value
        Map<String, String> map = createIndexedData();
        sendBody("direct:index", map);

        //now, verify GET succeeded
        String query = "{\"query\":{\"match\":{\"content\":\"searchtest\"}}}";
        SearchResponse response = template.requestBody("direct:search", query, SearchResponse.class);
        assertNotNull("response should not be null", response);
        assertNotNull("response hits should be == 1", response.getHits().totalHits());
    }
    
    @Test
    public void testUpdate() throws Exception {
        Map<String, String> map = createIndexedData();
        String indexId = template.requestBody("direct:index", map, String.class);
        assertNotNull("indexId should be set", indexId);

        Map<String, String> newMap = new HashMap<>();
        newMap.put(createPrefix() + "key2", createPrefix() + "value2");
        Map<String, Object> headers = new HashMap<>();
        headers.put(ElasticsearchConstants.PARAM_INDEX_ID, indexId);
        indexId = template.requestBodyAndHeaders("direct:update", newMap, headers, String.class);
        assertNotNull("indexId should be set", indexId);
    }

    @Test
    public void testGetWithHeaders() throws Exception {
        //first, INDEX a value
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");

        String indexId = template.requestBodyAndHeaders("direct:start", map, headers, String.class);

        //now, verify GET
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.GET_BY_ID);
        GetResponse response = template.requestBodyAndHeaders("direct:start", indexId, headers, GetResponse.class);
        assertNotNull("response should not be null", response);
        assertNotNull("response source should not be null", response.getSource());
    }
    
    @Test
    public void testExistsWithHeaders() throws Exception {
        //first, INDEX a value
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");

        String indexId = template.requestBodyAndHeaders("direct:start", map, headers, String.class);

        //now, verify GET
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.EXISTS);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        Boolean exists = template.requestBodyAndHeaders("direct:exists", "", headers, Boolean.class);
        assertNotNull("response should not be null", exists);
        assertTrue("Index should exists", exists);
    }
    
    @Test
    public void testNotExistsWithHeaders() throws Exception {
        //first, INDEX a value
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");

        String indexId = template.requestBodyAndHeaders("direct:start", map, headers, String.class);

        //now, verify GET
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.EXISTS);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter-tweet");
        Boolean exists = template.requestBodyAndHeaders("direct:exists", "", headers, Boolean.class);
        assertNotNull("response should not be null", exists);
        assertFalse("Index should not exists", exists);
    }
    
    @Test
    public void testMultiGet() throws Exception {
        //first, INDEX two values
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");
        headers.put(ElasticsearchConstants.PARAM_INDEX_ID, "1");

        template.requestBodyAndHeaders("direct:start", map, headers, String.class);
        
        headers.clear();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "facebook");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "status");
        headers.put(ElasticsearchConstants.PARAM_INDEX_ID, "2");
        
        template.requestBodyAndHeaders("direct:start", map, headers, String.class);
        headers.clear();

        //now, verify MULTIGET
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.MULTIGET);
        Item item1 = new Item("twitter", "tweet", "1");
        Item item2 = new Item("facebook", "status", "2");
        Item item3 = new Item("instagram", "latest", "3");
        List<Item> list = new ArrayList<Item>();
        list.add(item1);
        list.add(item2);
        list.add(item3);
        MultiGetResponse response = template.requestBodyAndHeaders("direct:start", list, headers, MultiGetResponse.class);
        MultiGetItemResponse[] responses = response.getResponses();
        assertNotNull("response should not be null", response);
        assertEquals("response should contains three multiGetResponse object", 3, response.getResponses().length);
        assertEquals("response 1 should contains tweet as type", "tweet", responses[0].getResponse().getType().toString());
        assertEquals("response 2 should contains status as type", "status", responses[1].getResponse().getType().toString());
        assertFalse("response 1 should be ok", responses[0].isFailed());
        assertFalse("response 2 should be ok", responses[1].isFailed());
        assertTrue("response 3 should be failed", responses[2].isFailed());
    }
    
    @Test
    public void testMultiSearch() throws Exception {
        //first, INDEX two values
        Map<String, Object> headers = new HashMap<String, Object>();
        
        node.client().prepareIndex("test", "type", "1").setSource("field", "xxx").execute().actionGet();
        node.client().prepareIndex("test", "type", "2").setSource("field", "yyy").execute().actionGet();

        //now, verify MULTISEARCH
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.MULTISEARCH);
        SearchRequestBuilder srb1 = node.client().prepareSearch("test").setTypes("type").setQuery(QueryBuilders.termQuery("field", "xxx"));
        SearchRequestBuilder srb2 = node.client().prepareSearch("test").setTypes("type").setQuery(QueryBuilders.termQuery("field", "yyy"));
        SearchRequestBuilder srb3 = node.client().prepareSearch("instagram")
            .setTypes("type").setQuery(QueryBuilders.termQuery("test-multisearchkey", "test-multisearchvalue"));
        List<SearchRequest> list = new ArrayList<>();
        list.add(srb1.request());
        list.add(srb2.request());
        list.add(srb3.request());
        MultiSearchResponse response = template.requestBodyAndHeaders("direct:multisearch", list, headers, MultiSearchResponse.class);
        MultiSearchResponse.Item[] responses = response.getResponses();
        assertNotNull("response should not be null", response);
        assertEquals("response should contains three multiSearchResponse object", 3, response.getResponses().length);
        assertFalse("response 1 should be ok", responses[0].isFailure());
        assertFalse("response 2 should be ok", responses[1].isFailure());
        assertTrue("response 3 should be failed", responses[2].isFailure());
    }

    @Test
    public void testDeleteWithHeaders() throws Exception {
        //first, INDEX a value
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");

        String indexId = template.requestBodyAndHeaders("direct:start", map, headers, String.class);

        //now, verify GET
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.GET_BY_ID);
        GetResponse response = template.requestBodyAndHeaders("direct:start", indexId, headers, GetResponse.class);
        assertNotNull("response should not be null", response);
        assertNotNull("response source should not be null", response.getSource());

        //now, perform DELETE
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.DELETE);
        DeleteResponse deleteResponse = template.requestBodyAndHeaders("direct:start", indexId, headers, DeleteResponse.class);
        assertNotNull("response should not be null", deleteResponse);

        //now, verify GET fails to find the indexed value
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.GET_BY_ID);
        response = template.requestBodyAndHeaders("direct:start", indexId, headers, GetResponse.class);
        assertNotNull("response should not be null", response);
        assertNull("response source should be null", response.getSource());
    }

    @Test
    public void testUpdateWithIDInHeader() throws Exception {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");
        headers.put(ElasticsearchConstants.PARAM_INDEX_ID, "123");

        String indexId = template.requestBodyAndHeaders("direct:start", map, headers, String.class);
        assertNotNull("indexId should be set", indexId);
        assertEquals("indexId should be equals to the provided id", "123", indexId);

        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.UPDATE);

        indexId = template.requestBodyAndHeaders("direct:start", map, headers, String.class);
        assertNotNull("indexId should be set", indexId);
        assertEquals("indexId should be equals to the provided id", "123", indexId);
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

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("elasticsearch5://elasticsearch?operation=INDEX&ip=localhost&port=" + ES_TRANSPORT_PORT);
                from("direct:index").to("elasticsearch5://elasticsearch?operation=INDEX&indexName=twitter&indexType=tweet&ip=localhost&port=" + ES_TRANSPORT_PORT);
                from("direct:get").to("elasticsearch5://elasticsearch?operation=GET_BY_ID&indexName=twitter&indexType=tweet&ip=localhost&port=" + ES_TRANSPORT_PORT);
                from("direct:multiget").to("elasticsearch5://elasticsearch?operation=MULTIGET&indexName=twitter&indexType=tweet&ip=localhost&port=" + ES_TRANSPORT_PORT);
                from("direct:delete").to("elasticsearch5://elasticsearch?operation=DELETE&indexName=twitter&indexType=tweet&ip=localhost&port=" + ES_TRANSPORT_PORT);
                from("direct:search").to("elasticsearch5://elasticsearch?operation=SEARCH&indexName=twitter&indexType=tweet&ip=localhost&port=" + ES_TRANSPORT_PORT);
                from("direct:update").to("elasticsearch5://elasticsearch?operation=UPDATE&indexName=twitter&indexType=tweet&ip=localhost&port=" + ES_TRANSPORT_PORT);
                from("direct:exists").to("elasticsearch5://elasticsearch?operation=EXISTS&ip=localhost&port=" + ES_TRANSPORT_PORT);
                from("direct:multisearch").to("elasticsearch5://elasticsearch?operation=MULTISEARCH&indexName=test&ip=localhost&port=" + ES_TRANSPORT_PORT);
            }
        };
    }
}
