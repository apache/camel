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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ElasticsearchGetSearchDeleteExistsUpdateTest extends
		ElasticsearchBaseTest {

	@Test
	public void testGet() throws Exception {
		// first, INDEX a value
		Map<String, String> map = createIndexedData();
		sendBody("direct:index", map);
		String indexId = template
				.requestBody("direct:index", map, String.class);
		assertNotNull("indexId should be set", indexId);

		// now, verify GET succeeded
		GetResponse response = template.requestBody("direct:get", indexId,
				GetResponse.class);

		assertNotNull("response should not be null", response);
		assertNotNull("response source should not be null",
				response.getSource());

		// http client GET
		String response2 = template.requestBody("direct:get2", indexId,
				String.class);
		assertNotNull("response should not be null", response2);
		assertNotNull("response source should not be null", response2);
		assertNotEquals("response should not equal id", indexId, response2);
		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode response2JsonNode = objectMapper.readValue(response2,
				JsonNode.class);
		assertEquals("response source should equal created source",
				response.getSourceAsString(),
				response2JsonNode.findValue("_source").toString());
	}

	@Test
	public void testDelete() throws Exception {
		// first, INDEX a value
		Map<String, String> map = createIndexedData();
		sendBody("direct:index", map);
		String indexId = template
				.requestBody("direct:index", map, String.class);
		assertNotNull("indexId should be set", indexId);

		// now, verify GET succeeded
		GetResponse response = template.requestBody("direct:get", indexId,
				GetResponse.class);
		assertNotNull("response should not be null", response);
		assertNotNull("response source should not be null",
				response.getSource());

		// now, perform DELETE
		DeleteResponse deleteResponse = template.requestBody("direct:delete",
				indexId, DeleteResponse.class);
		assertNotNull("response should not be null", deleteResponse);

		// now, verify GET fails to find the indexed value
		response = template.requestBody("direct:get", indexId,
				GetResponse.class);
		assertNotNull("response should not be null", response);
		assertNull("response source should be null", response.getSource());
	}

	@Test
	public void testHttpDelete() throws Exception {
		// first, INDEX a value
		Map<String, String> map = createIndexedData();
		sendBody("direct:index", map);
		String indexId = template
				.requestBody("direct:index", map, String.class);
		assertNotNull("indexId should be set", indexId);

		// now, verify GET succeeded
		GetResponse response = template.requestBody("direct:get", indexId,
				GetResponse.class);
		assertNotNull("response should not be null", response);
		assertNotNull("response source should not be null",
				response.getSource());

		// now, perform DELETE
		String deleteResponse = template.requestBody("direct:delete2", indexId,
				String.class);
		assertNotNull("response should not be null", deleteResponse);

		// now, verify GET fails to find the indexed value
		response = template.requestBody("direct:get", indexId,
				GetResponse.class);
		assertNotNull("response should not be null", response);
		assertNull("response source should be null", response.getSource());
	}

	@Test
	public void testSearch() throws Exception {
		// first, INDEX a value
		Map<String, String> map = createIndexedData();
		sendBody("direct:index", map);

		// now, verify GET succeeded
		Map<String, Object> actualQuery = new HashMap<String, Object>();
		actualQuery.put("testsearch-key", "testsearch-value"); // the field is
																// actually
																// called
																// testsearch-key,
																// not content
		Map<String, Object> match = new HashMap<String, Object>();
		match.put("match", actualQuery);
		Map<String, Object> query = new HashMap<String, Object>();
		query.put("query", match);
		Thread.sleep(1000); // need to wait for the data to be refreshed for
							// searching
		SearchResponse response = template.requestBody("direct:search", query,
				SearchResponse.class);
		assertNotNull("response should not be null", response);
		assertNotNull("response hits should not be null", response.getHits()
				.totalHits());
		assertTrue("response hits should be == 1", response.getHits()
				.getTotalHits() == 1);

		Map responseMap = template.requestBody("direct:search2", query,
				Map.class);
		assertNotNull("response should not be null", responseMap);
		assertNotNull("response hits should not be null",
				responseMap.get("hits"));
		assertTrue("response hits should be == 1",
				((Map) responseMap.get("hits")).get("total").equals(1));
	}

	@Test
	public void testUpdate() throws Exception {
		Map<String, String> map = createIndexedData();
		String indexId = template
				.requestBody("direct:index", map, String.class);
		assertNotNull("indexId should be set", indexId);

		Map<String, String> newMap = new HashMap<>();
		newMap.put(createPrefix() + "key2", createPrefix() + "value2");
		Map<String, Object> headers = new HashMap<>();
		headers.put(ElasticsearchConstants.PARAM_INDEX_ID, indexId);
		indexId = template.requestBodyAndHeaders("direct:update", newMap,
				headers, String.class);
		assertNotNull("indexId should be set", indexId);

		Map<String, String> newMap2 = new HashMap<>();
		newMap2.put(createPrefix() + "key2", createPrefix() + "value3");

		indexId = template.requestBodyAndHeaders("direct:update2", newMap2,
				headers, String.class);
		assertNotNull("indexId should be set", indexId);
	}

	@Test
	public void testGetWithHeaders() throws Exception {
		// first, INDEX a value
		Map<String, String> map = createIndexedData();
		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put(ElasticsearchConstants.PARAM_OPERATION,
				ElasticsearchConstants.OPERATION_INDEX);
		headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
		headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");

		String indexId = template.requestBodyAndHeaders("direct:start", map,
				headers, String.class);

		// now, verify GET
		headers.put(ElasticsearchConstants.PARAM_OPERATION,
				ElasticsearchConstants.OPERATION_GET_BY_ID);
		GetResponse response = template.requestBodyAndHeaders("direct:start",
				indexId, headers, GetResponse.class);
		assertNotNull("response should not be null", response);
		assertNotNull("response source should not be null",
				response.getSource());
	}

	@Test
	public void testExistsWithHeaders() throws Exception {
		// first, INDEX a value
		Map<String, String> map = createIndexedData();
		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put(ElasticsearchConstants.PARAM_OPERATION,
				ElasticsearchConstants.OPERATION_INDEX);
		headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
		headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");

		String indexId = template.requestBodyAndHeaders("direct:start", map,
				headers, String.class);

		// now, verify GET
		headers.put(ElasticsearchConstants.PARAM_OPERATION,
				ElasticsearchConstants.OPERATION_EXISTS);
		headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
		Boolean exists = template.requestBodyAndHeaders("direct:exists", "",
				headers, Boolean.class);

		assertNotNull("response should not be null", exists);
		assertTrue("Index should exists", exists);

		Boolean exists2 = template.requestBodyAndHeaders("direct:exists2", "",
				headers, Boolean.class);
		assertNotNull("response should not be null", exists2);
		assertTrue("Index should exists", exists2);

	}

	@Test
	public void testMultiGet() throws Exception {
		// first, INDEX two values
		Map<String, String> map = createIndexedData();
		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put(ElasticsearchConstants.PARAM_OPERATION,
				ElasticsearchConstants.OPERATION_INDEX);
		headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
		headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");
		headers.put(ElasticsearchConstants.PARAM_INDEX_ID, "1");

		template.requestBodyAndHeaders("direct:start", map, headers,
				String.class);

		headers.clear();
		headers.put(ElasticsearchConstants.PARAM_OPERATION,
				ElasticsearchConstants.OPERATION_INDEX);
		headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "facebook");
		headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "status");
		headers.put(ElasticsearchConstants.PARAM_INDEX_ID, "2");

		template.requestBodyAndHeaders("direct:start", map, headers,
				String.class);
		headers.clear();

		// now, verify MULTIGET
		headers.put(ElasticsearchConstants.PARAM_OPERATION,
				ElasticsearchConstants.OPERATION_MULTIGET);
		Item item1 = new Item("twitter", "tweet", "1");
		Item item2 = new Item("facebook", "status", "2");
		Item item3 = new Item("instagram", "latest", "3");
		List<Item> list = new ArrayList<Item>();
		list.add(item1);
		list.add(item2);
		list.add(item3);
		MultiGetResponse response = template.requestBodyAndHeaders(
				"direct:multiget", list, headers, MultiGetResponse.class);
		MultiGetItemResponse[] responses = response.getResponses();

		assertNotNull("response should not be null", response);
		assertEquals("response should contains three multiGetResponse object",
				3, response.getResponses().length);
		assertEquals("response 1 should contains tweet as type", "tweet",
				responses[0].getResponse().getType().toString());
		assertEquals("response 2 should contains status as type", "status",
				responses[1].getResponse().getType().toString());
		assertFalse("response 1 should be ok", responses[0].isFailed());
		assertFalse("response 2 should be ok", responses[1].isFailed());
		assertTrue("response 3 should be failed", responses[2].isFailed());

		// Now for the http version of multiget
		Map<String, String> item1Map = new HashMap<String, String>();
		item1Map.put("_index", "twitter");
		item1Map.put("_type", "tweet");
		item1Map.put("_id", "1");

		Map<String, String> item2Map = new HashMap<String, String>();
		item2Map.put("_index", "facebook");
		item2Map.put("_type", "status");
		item2Map.put("_id", "2");

		Map<String, String> item3Map = new HashMap<String, String>();
		item3Map.put("_index", "instagram");
		item3Map.put("_type", "latest");
		item3Map.put("_id", "3");

		List<Map> list2 = new ArrayList<Map>();
		list2.add(item1Map);
		list2.add(item2Map);
		list2.add(item3Map);
		List<String> multiGetRequestResults = (List<String>) template
				.requestBodyAndHeaders("direct:multiget2", list2, headers);
		assertNotNull("response should not be null", multiGetRequestResults);
		assertTrue("response size greater than 0",
				multiGetRequestResults.size() > 0);
		assertTrue("response size equal to 3",
				multiGetRequestResults.size() == 3);
		String result1 = multiGetRequestResults.get(0);
		ObjectMapper objectMapper = new ObjectMapper();
		assertTrue("response 1 should be ok", objectMapper.readTree(result1)
				.get("_source") != null);

		String result2 = multiGetRequestResults.get(1);
		assertTrue("response 2 should be ok", objectMapper.readTree(result2)
				.get("_source") != null);

		String result3 = multiGetRequestResults.get(2);
		assertTrue("response 3 should fail", objectMapper.readTree(result3)
				.get("_source") == null);

	}

	@Test
	public void testMultiSearch() throws Exception {
		// first, INDEX two values
		Map<String, Object> headers = new HashMap<String, Object>();

		node.client().prepareIndex("test", "type", "1")
				.setSource("field", "xxx").execute().actionGet();
		node.client().prepareIndex("test", "type", "2")
				.setSource("field", "yyy").execute().actionGet();

		// now, verify MULTISEARCH
		headers.put(ElasticsearchConstants.PARAM_OPERATION,
				ElasticsearchConstants.OPERATION_MULTISEARCH);
		SearchRequestBuilder srb1 = node.client().prepareSearch("test")
				.setTypes("type")
				.setQuery(QueryBuilders.termQuery("field", "xxx"));
		SearchRequestBuilder srb2 = node.client().prepareSearch("test")
				.setTypes("type")
				.setQuery(QueryBuilders.termQuery("field", "yyy"));
		SearchRequestBuilder srb3 = node
				.client()
				.prepareSearch("instagram")
				.setTypes("type")
				.setQuery(
						QueryBuilders.termQuery("test-multisearchkey",
								"test-multisearchvalue"));
		List<SearchRequest> list = new ArrayList<>();
		list.add(srb1.request());
		list.add(srb2.request());
		list.add(srb3.request());
		MultiSearchResponse response = template.requestBodyAndHeaders(
				"direct:multisearch", list, headers, MultiSearchResponse.class);
		MultiSearchResponse.Item[] responses = response.getResponses();
		assertNotNull("response should not be null", response);
		assertEquals(
				"response should contains three multiSearchResponse object", 3,
				response.getResponses().length);
		assertFalse("response 1 should be ok", responses[0].isFailure());
		assertFalse("response 2 should be ok", responses[1].isFailure());
		assertTrue("response 3 should be failed", responses[2].isFailure());

		List<Map> queriesList = new ArrayList<Map>();

		// http version of multisearch
		ObjectMapper objectMapper = new ObjectMapper();
		ObjectNode objectNode = objectMapper.createObjectNode()
				.put("index", "test").put("type", "type");
		ObjectNode termNode = objectMapper.createObjectNode();
		termNode.put("field", "xxx");
		ObjectNode matchNode = objectMapper.createObjectNode();
		matchNode.set("match", termNode);
		objectNode.set("query", matchNode);

		Map queryObjectMap1 = objectMapper.convertValue(objectNode, Map.class);
		queriesList.add(queryObjectMap1);

		ObjectNode objectNode2 = objectMapper.createObjectNode()
				.put("index", "test").put("type", "type");

		ObjectNode termNode2 = objectMapper.createObjectNode();
		termNode2.put("field", "yyy");
		ObjectNode matchNode2 = objectMapper.createObjectNode();
		matchNode2.set("match", termNode);
		objectNode2.set("query", matchNode);
		Map queryObjectMap2 = objectMapper.convertValue(objectNode2, Map.class);
		queriesList.add(queryObjectMap2);

		ObjectNode objectNode3 = objectMapper.createObjectNode()
				.put("index", "instagram").put("type", "type");

		ObjectNode termNode3 = objectMapper.createObjectNode();
		termNode3.put("test-multisearchkey", "test-multisearchvalue");
		ObjectNode matchNode3 = objectMapper.createObjectNode();
		matchNode3.set("match", termNode);
		objectNode3.set("query", matchNode);
		Map queryObjectMap3 = objectMapper.convertValue(objectNode3, Map.class);
		queriesList.add(queryObjectMap3);

		Thread.sleep(1000);
		List<Map> response2 = template.requestBodyAndHeaders(
				"direct:multisearch2", queriesList, headers, List.class);

		assertNotNull("response should not be null", response2);
		assertEquals(
				"response should contains three multiSearchResponse object", 3,
				response2.size());

		assertEquals("response 1 should be ok",
				((Map) response2.get(0).get("hits")).get("total"), 1);
		assertEquals("response 2 should be ok",
				((Map) response2.get(1).get("hits")).get("total"), 1);
		assertTrue("response 3 should have error", response2.get(2)
				.containsKey("error"));

	}

	@Test
	public void testDeleteWithHeaders() throws Exception {
		// first, INDEX a value
		Map<String, String> map = createIndexedData();
		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put(ElasticsearchConstants.PARAM_OPERATION,
				ElasticsearchConstants.OPERATION_INDEX);
		headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
		headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");

		String indexId = template.requestBodyAndHeaders("direct:start", map,
				headers, String.class);

		// now, verify GET
		headers.put(ElasticsearchConstants.PARAM_OPERATION,
				ElasticsearchConstants.OPERATION_GET_BY_ID);
		GetResponse response = template.requestBodyAndHeaders("direct:start",
				indexId, headers, GetResponse.class);
		assertNotNull("response should not be null", response);
		assertNotNull("response source should not be null",
				response.getSource());

		// now, perform DELETE
		headers.put(ElasticsearchConstants.PARAM_OPERATION,
				ElasticsearchConstants.OPERATION_DELETE);
		DeleteResponse deleteResponse = template.requestBodyAndHeaders(
				"direct:start", indexId, headers, DeleteResponse.class);
		assertNotNull("response should not be null", deleteResponse);

		// now, verify GET fails to find the indexed value
		headers.put(ElasticsearchConstants.PARAM_OPERATION,
				ElasticsearchConstants.OPERATION_GET_BY_ID);
		response = template.requestBodyAndHeaders("direct:start", indexId,
				headers, GetResponse.class);
		assertNotNull("response should not be null", response);
		assertNull("response source should be null", response.getSource());
	}

	@Test
	public void testUpdateWithIDInHeader() throws Exception {
		Map<String, String> map = createIndexedData();
		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put(ElasticsearchConstants.PARAM_OPERATION,
				ElasticsearchConstants.OPERATION_INDEX);
		headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
		headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");
		headers.put(ElasticsearchConstants.PARAM_INDEX_ID, "123");

		String indexId = template.requestBodyAndHeaders("direct:start", map,
				headers, String.class);
		assertNotNull("indexId should be set", indexId);
		assertEquals("indexId should be equals to the provided id", "123",
				indexId);

		headers.put(ElasticsearchConstants.PARAM_OPERATION,
				ElasticsearchConstants.OPERATION_UPDATE);

		indexId = template.requestBodyAndHeaders("direct:start", map, headers,
				String.class);
		assertNotNull("indexId should be set", indexId);
		assertEquals("indexId should be equals to the provided id", "123",
				indexId);
	}

	@Test
	public void getRequestBody() throws Exception {
		String prefix = createPrefix();

		// given
		GetRequest request = new GetRequest(prefix + "foo")
				.type(prefix + "bar");

		// when
		String documentId = template.requestBody(
				"direct:index",
				new IndexRequest(prefix + "foo", prefix + "bar", prefix
						+ "testId").source("{\"" + prefix + "content\": \""
						+ prefix + "hello\"}"), String.class);
		GetResponse response = template.requestBody("direct:get",
				request.id(documentId), GetResponse.class);

		// then
		assertThat(response, notNullValue());
		assertThat(prefix + "hello",
				equalTo(response.getSourceAsMap().get(prefix + "content")));
	}

	@Test
	public void deleteRequestBody() throws Exception {
		String prefix = createPrefix();

		// given
		DeleteRequest request = new DeleteRequest(prefix + "foo").type(prefix
				+ "bar");

		// when
		String documentId = template.requestBody(
				"direct:index",
				new IndexRequest("" + prefix + "foo", "" + prefix + "bar", ""
						+ prefix + "testId").source("{\"" + prefix
						+ "content\": \"" + prefix + "hello\"}"), String.class);
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
				from("direct:start")
						.to("elasticsearch://local?operation=INDEX");
				from("direct:index")
						.to("elasticsearch://local?operation=INDEX&indexName=twitter&indexType=tweet");
				from("direct:get")
						.to("elasticsearch://local?operation=GET_BY_ID&indexName=twitter&indexType=tweet");
				from("direct:get2")
						.to("elasticsearch://elasticsearch?ip=localhost&port=9201&operation=GET_BY_ID&indexName=twitter&indexType=tweet&useHttpClient=true");

				from("direct:multiget")
						.to("elasticsearch://local?operation=MULTIGET&indexName=twitter&indexType=tweet");
				from("direct:multiget2")
						.to("elasticsearch://elasticsearch?ip=localhost&port=9201&operation=MULTIGET&indexName=twitter&indexType=tweet&useHttpClient=true");

				from("direct:delete")
						.to("elasticsearch://local?operation=DELETE&indexName=twitter&indexType=tweet");
				from("direct:delete2")
						.to("elasticsearch://elasticsearch?ip=localhost&port=9201&operation=DELETE&indexName=twitter&indexType=tweet&useHttpClient=true");

				from("direct:search")
						.to("elasticsearch://local?operation=SEARCH&indexName=twitter&indexType=tweet");
				from("direct:search2")
						.to("elasticsearch://elasticsearch?ip=localhost&port=9201&useHttpClient=true&operation=SEARCH&indexName=twitter&indexType=tweet");

				from("direct:update")
						.to("elasticsearch://local?operation=UPDATE&indexName=twitter&indexType=tweet");
				from("direct:update2")
						.to("elasticsearch://elasticsearch?ip=localhost&port=9201&operation=UPDATE&indexName=twitter&indexType=tweet&useHttpClient=true");

				from("direct:exists").to(
						"elasticsearch://local?operation=EXISTS");
				from("direct:exists2")
						.to("elasticsearch://elasticsearch?ip=localhost&port=9201&useHttpClient=true&operation=EXISTS");

				from("direct:multisearch")
						.to("elasticsearch://local?operation=MULTISEARCH&indexName=test");
				from("direct:multisearch2")
						.to("elasticsearch://elasticsearch?ip=localhost&port=9201&useHttpClient=true&operation=MULTISEARCH&indexName=test");

			}
		};
	}
}
