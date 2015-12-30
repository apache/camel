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

import java.util.List;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

public class ElasticsearchHttpClient {

    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchHttpClient.class);

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
			String protocol = "http";
			if (isSecure())
				protocol = "https";

			rootTarget = client.target(protocol + "://" + host + ":" + port);
		}
		return rootTarget;
	}
	
	public String index(String indexName, String type, String body) {		
		return index(indexName, type, body, null, null);
	}

	/**
	 * Call index API with String as body
	 * 
	 * @param body
	 */
	public String index(String indexName, String type, String body, String parent, String consistency) {		
		WebTarget target = getRootTarget()
				.path(indexName).path(type);
		if(parent!=null)
			target = target.queryParam("parent", parent);
		if(consistency!=null)
			target = target.queryParam("consistency", consistency);
		
		ESDocumentResponse response = target.request()
				.post(Entity.json(body), ESDocumentResponse.class);
		//TODO need to rethink this approach of responding with just the ID
		return response.getId();
	}

	public List<String> bulkIndex(String indexName, String indexType,
			List<String> documents) {
		StringBuilder bodyBuilder = new StringBuilder();
		for(String doc: documents) {
			bodyBuilder.append("{\"index\":{\"_index\":\"")
					   .append(indexName).append("\",")
					   .append("\"_type\":\"")
					   .append(indexType).append("\"}}\n");
			String strippedDoc = doc.trim().replaceAll("\r", "").replaceAll("\n", "").replaceAll("\t", "");
			bodyBuilder.append(strippedDoc).append("\n");
		}
		WebTarget target = getRootTarget().path(indexName).path(indexType).path("_bulk");
		LOG.info("Bulk Indexing \n"+bodyBuilder.toString());
		Response response = target.request().post(Entity.text(bodyBuilder.toString()));
		JsonNode responseNode = response.readEntity(JsonNode.class);
		JsonNode itemsNode = responseNode.get("items");
		List<String> ids = itemsNode.findValuesAsText("_id");

		// TODO this returning of List<String> doesn't actually tell you which ones failed
		
		return ids;
	}

	public String getById(String indexName, String indexType, String docId) {
		WebTarget target = getRootTarget()
				.path(indexName).path(indexType).path(docId);
		
		Response response = target.request()
				.get();
		//TODO need to rethink this approach of responding with just the ID
		return response.readEntity(String.class);
	}

}
