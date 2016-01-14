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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

/**
 * Implementation of CRUD and search operations using HTTP calls rather than the
 * Java Client API
 *
 */
public class ElasticsearchHttpClient {

	private static final String MSEARCH_API_PATH = "_msearch";

	private static final String QUOTE = ":";

	private static final String ESCAPED_DOUBLE_QUOTE = "\"";

	private static final String TAB_CHAR = "\t";

	private static final String CARRIAGE_RETURN = "\r";

	private static final String END_OF_LINE = "\n";

	private static final String JSON_OBJECT_END = "}";

	private static final String JSON_OBJECT_COMMA = ",";

	private static final String JSON_OBJECT_START = "{";

	private static final String HTTPS_PROTOCOL = "https";

	private static final String HTTP_PROTOCOL = "http";

	private static final String CONSISTENCY_REQUEST_PARAM = "consistency";

	private static final String PARENT_REQUEST_PARAM = "parent";

	private static final String ID_ATTR = "_id";

	private static final String ITEMS_ATTR = "items";

	private static final String UPDATE_API_PATH = "_update";

	private static final String MGET_API_PATH = "_mget";

	private static final String[] MSEARCH_HEADERS_TO_CHECK = new String[] {
			"index", "type", "search_type", "preference", "routing" };

	private static final Logger LOG = LoggerFactory
			.getLogger(ElasticsearchHttpClient.class);

	private Client client = null;
	private String host = "localhost";
	private String port = "9200";
	private Boolean secure = false;
	private WebTarget rootTarget = null;

	public ElasticsearchHttpClient() {
		this.client = ClientBuilder.newClient().register(
				JacksonJsonProvider.class);
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getPort() {
		return port;
	}

	public void setPort(String port) {
		this.port = port;
	}

	public Boolean isSecure() {
		return secure;
	}

	public void setSecure(Boolean secure) {
		this.secure = secure;
	}

	private WebTarget getRootTarget() {
		if (rootTarget == null) {
			String protocol = HTTP_PROTOCOL;
			if (isSecure())
				protocol = HTTPS_PROTOCOL;

			rootTarget = client.target(protocol + "://" + host + QUOTE + port);
		}
		return rootTarget;
	}

	/**
	 * Index API for when parent and consistency are not specified
	 * 
	 * @param indexName
	 * @param type
	 * @param body
	 * @return
	 */
	public String index(String indexName, String type, String body) {
		return index(indexName, type, body, null, null);
	}

	/**
	 * Index API given an indexName, type, body, and optional parent and
	 * consistency
	 * 
	 * @param indexName
	 * @param type
	 * @param body
	 * @param parent
	 * @param consistency
	 * @return
	 */
	public String index(String indexName, String type, String body,
			String parent, String consistency) {
		WebTarget target = getRootTarget().path(indexName).path(type);
		if (parent != null)
			target = target.queryParam(PARENT_REQUEST_PARAM, parent);
		if (consistency != null)
			target = target.queryParam(CONSISTENCY_REQUEST_PARAM, consistency);

		ESDocumentResponse response = target.request().post(Entity.json(body),
				ESDocumentResponse.class);
		// TODO need to rethink this approach of responding with just the ID
		return response.getId();
	}

	/**
	 * Exists API given an indexName, type, id
	 * 
	 */
	public boolean indexExists(String indexName) {
		WebTarget target = getRootTarget().path(indexName);
		Response response = target.request().head();
		return (response.getStatus() == 200);
	}

	/**
	 * Bulk index API using the bulk api format
	 * 
	 * @param indexName
	 * @param indexType
	 * @param documents
	 * @return
	 */
	public List<String> bulkIndex(String indexName, String indexType,
			List<String> documents) {
		StringBuilder bodyBuilder = new StringBuilder();
		for (String doc : documents) {
			bodyBuilder.append("{\"index\":{\"_index\":\"").append(indexName)
					.append("\",").append("\"_type\":\"").append(indexType)
					.append("\"}}\n");
			String strippedDoc = doc.trim().replaceAll(CARRIAGE_RETURN, "")
					.replaceAll(END_OF_LINE, "").replaceAll(TAB_CHAR, "");
			bodyBuilder.append(strippedDoc).append(END_OF_LINE);
		}
		WebTarget target = getRootTarget().path(indexName).path(indexType)
				.path("_bulk");
		LOG.info("Bulk Indexing \n" + bodyBuilder.toString());
		Response response = target.request().post(
				Entity.text(bodyBuilder.toString()));
		JsonNode responseNode = response.readEntity(JsonNode.class);
		JsonNode itemsNode = responseNode.get(ITEMS_ATTR);
		List<String> ids = itemsNode.findValuesAsText(ID_ATTR);

		// TODO this returning of List<String> doesn't actually tell you which
		// ones failed

		return ids;
	}

	/**
	 * Get document by ID API
	 * 
	 * @param indexName
	 * @param indexType
	 * @param docId
	 * @return
	 */
	public String getById(String indexName, String indexType, String docId) {
		WebTarget target = getRootTarget().path(indexName).path(indexType)
				.path(docId);

		Response response = target.request().get();
		return response.readEntity(String.class);
	}

	/**
	 * Delete Document By ID API
	 * 
	 * @param indexName
	 * @param indexType
	 * @param docId
	 * @return
	 */
	public String delete(String indexName, String indexType, String docId) {
		WebTarget target = getRootTarget().path(indexName).path(indexType)
				.path(docId);

		Response response = target.request().delete();
		return response.readEntity(String.class);
	}

	/**
	 * Update API by document given a Map of changed fields
	 * 
	 * @param indexName
	 * @param indexType
	 * @param docId
	 * @param body
	 * @return
	 */
	public Object update(String indexName, String indexType, String docId,
			Map body) {
		WebTarget target = getRootTarget().path(indexName).path(indexType)
				.path(docId).path(UPDATE_API_PATH);
		// if(parent!=null)
		// target = target.queryParam("parent", parent);
		// if(consistency!=null)
		// target = target.queryParam("consistency", consistency);
		//
		StringBuilder bodyStringBuilder = new StringBuilder();

		try {
			bodyStringBuilder.append("{\"doc\":")
					.append(new ObjectMapper().writeValueAsString(body))
					.append(JSON_OBJECT_END);
			Response response = target.request().post(
					Entity.json(bodyStringBuilder.toString()));
			if (response.getStatus() == 200)
				return docId;

		} catch (JsonProcessingException e) {
			LOG.error("Error converting map of changes to JSON String", e);
		}
		return null;

	}

	/**
	 * Returns a list of Strings representing the documents requested in the
	 * mget request The request body is a list of Maps which may or may not
	 * contain the indexName, indexType and will contain the document IDs
	 * requested
	 * 
	 * @param indexName
	 * @param indexType
	 * @param body
	 * @return
	 */
	public List<String> multiget(String indexName, String indexType, List body) {
		WebTarget target = getRootTarget();
		if (indexName != null) {
			target = target.path(indexName);
			if (indexType != null) {
				target = target.path(indexType);
			}
		}
		target = target.path(MGET_API_PATH);

		Map<String, Object> wrapper = new HashMap<String, Object>();
		wrapper.put("docs", body);

		ObjectMapper objectMapper = new ObjectMapper();
		String bodyAsString;
		try {
			bodyAsString = objectMapper.writeValueAsString(wrapper);

			Response response = target.request()
					.post(Entity.json(bodyAsString));

			JsonNode responseJsonNode = response.readEntity(JsonNode.class);
			if (responseJsonNode != null) {

				List<String> result = new ArrayList<String>();
				Iterator<JsonNode> jsonNodeIterator = responseJsonNode.get(
						"docs").iterator();
				while (jsonNodeIterator.hasNext()) {
					JsonNode jsonNode = jsonNodeIterator.next();
					result.add(jsonNode.toString());
				}
				return result;
			}

		} catch (JsonProcessingException e) {
			throw new RuntimeException("Could not process  of IDs", e);
		}

		return new ArrayList<String>();
	}

	/**
	 * Executes an elasticsearch query represented by the queryObject and
	 * optionally sets the index and index type in the URL
	 * 
	 * @param indexName
	 * @param indexType
	 * @param queryObject
	 * @return query results as a map representation
	 */
	public Object search(String indexName, String indexType, Map queryObject) {
		WebTarget target = getRootTarget();
		if (indexName != null) {
			target = target.path(indexName);
			if (indexType != null) {
				target = target.path(indexType);
			}
		}
		target = target.path("_search");
		try {
			String searchBody = new ObjectMapper()
					.writeValueAsString(queryObject);
			Response response = target.request().post(Entity.json(searchBody));
			return response.readEntity(Map.class);

		} catch (JsonProcessingException e) {
			throw new RuntimeException("Could not process query body map", e);
		}
	}

	/**
	 * Multisearch API taking in multiple queries as a List of Maps and
	 * returning the result as a List of Map objects
	 * 
	 * Each Map in the List will contain at least a "query" object and
	 * optionally index and type
	 * 
	 * @param indexName
	 * @param indexType
	 * @param queryObject
	 * @return
	 */
	public Object multisearch(String indexName, String indexType,
			List queryObjects) {
		WebTarget target = getRootTarget();
		if (indexName != null) {
			target = target.path(indexName);
			if (indexType != null) {
				target = target.path(indexType);
			}
		}

		target = target.path(MSEARCH_API_PATH);
		
		StringBuilder bodyBuilder = new StringBuilder();
		ObjectMapper objectMapper = new ObjectMapper();
		for (Object queryObj : queryObjects) {
			Map queryObjMap = (Map) queryObj;
			// add header
			bodyBuilder.append(JSON_OBJECT_START);
			// this is not standard but really the only way to pass in the index
			// and type
			// headers in the same object as the query
			boolean commaRequired = false;
			for (String header : MSEARCH_HEADERS_TO_CHECK) {
				
				if (queryObjMap.containsKey(header)) {
					if (commaRequired) {
						bodyBuilder.append(JSON_OBJECT_COMMA);
					}
					bodyBuilder.append(ESCAPED_DOUBLE_QUOTE)
							   .append(header)
							   .append(ESCAPED_DOUBLE_QUOTE)
							   .append(QUOTE).append(ESCAPED_DOUBLE_QUOTE)
							.append(queryObjMap.get(header)).append(ESCAPED_DOUBLE_QUOTE);
					
					commaRequired = true;
				}
				queryObjMap.remove(header);
			}
			bodyBuilder.append(JSON_OBJECT_END).append(END_OF_LINE);

			// now add the query portion
			String queryString;
			try {
				queryString = objectMapper.writeValueAsString(queryObjMap);
				String strippedDoc = queryString.trim().replaceAll(CARRIAGE_RETURN, "")
						.replaceAll(END_OF_LINE, "").replaceAll(TAB_CHAR, "");
				bodyBuilder.append(strippedDoc).append(END_OF_LINE);
			} catch (JsonProcessingException e) {
				LOG.warn("Error processing JSON", e);
			}

		}

		Response response = target.request().post(
				Entity.json(bodyBuilder.toString()));
		Map responseMap = response.readEntity(Map.class);
		if (responseMap.containsKey("responses")) {
			return responseMap.get("responses");
		}

		return null;
	}

}
