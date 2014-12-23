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
package org.apache.camel.component.elasticsearch.converter;

import java.util.List;
import java.util.Map;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.component.elasticsearch.ElasticsearchConfiguration;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.xcontent.XContentBuilder;

@Converter
public class ElasticsearchActionRequestConverter {

	// Index requests
	private static IndexRequest createIndexRequest(Object document,
			Exchange exchange) {
		IndexRequest indexRequest = new IndexRequest();
		if (document instanceof byte[]) {
			indexRequest.source((byte[]) document);
		} else if (document instanceof Map) {
			indexRequest.source((Map<String, Object>) document);
		} else if (document instanceof String) {
			indexRequest.source((String) document);
		} else if (document instanceof XContentBuilder) {
			indexRequest.source((XContentBuilder) document);
		} else {
			return null;
		}

		return indexRequest.index(
				exchange.getIn().getHeader(
						ElasticsearchConfiguration.PARAM_INDEX_NAME,
						String.class)).type(
				exchange.getIn().getHeader(
						ElasticsearchConfiguration.PARAM_INDEX_TYPE,
						String.class));
	}

	@Converter
	public static IndexRequest toIndexRequest(Object document, Exchange exchange) {
		if (document == null)
			return null;

		return createIndexRequest(document, exchange).id(
				exchange.getIn()
						.getHeader(ElasticsearchConfiguration.PARAM_INDEX_ID,
								String.class));
	}

	@Converter
	public static GetRequest toGetRequest(String id, Exchange exchange) {
		if (id == null)
			return null;

		return new GetRequest(exchange.getIn().getHeader(
				ElasticsearchConfiguration.PARAM_INDEX_NAME, String.class))
				.type(exchange.getIn().getHeader(
						ElasticsearchConfiguration.PARAM_INDEX_TYPE,
						String.class)).id(id);
	}

	@Converter
	public static DeleteRequest toDeleteRequest(String id, Exchange exchange) {
		if (id == null)
			return null;

		return new DeleteRequest()
				.index(exchange.getIn().getHeader(
						ElasticsearchConfiguration.PARAM_INDEX_NAME,
						String.class))
				.type(exchange.getIn().getHeader(
						ElasticsearchConfiguration.PARAM_INDEX_TYPE,
						String.class)).id(id);
	}

	@Converter
	public static BulkRequest toBulkRequest(List<Object> documents,
			Exchange exchange) {
		if (documents == null)
			return null;

		BulkRequest request = new BulkRequest();
		for (Object document : documents) {
			request.add(createIndexRequest(document, exchange));
		}
		return request;
	}
}
