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

import java.util.LinkedList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Client;

/**
 * Represents an Elasticsearch producer.
 */
public class ElasticsearchProducer extends DefaultProducer {

	public ElasticsearchProducer(ElasticsearchEndpoint endpoint) {
		super(endpoint);
	}

	@Override
	public ElasticsearchEndpoint getEndpoint() {
		return (ElasticsearchEndpoint) super.getEndpoint();
	}

	private String resolveOperation(Exchange exchange) {
		// 1. Operation can be driven by either (in order of preference):
		// a. If the body is an ActionRequest the operation is set by the type
		// of request.
		// b. If the body is not an ActionRequest, the operation is set by the
		// header if it exists.
		// c. If neither the operation can not be derived from the body or
		// header, the configuration is used.
		// In the event we can't discover the operation from a, b or c we throw
		// an error.

		Object request = exchange.getIn().getBody();
		if (request instanceof IndexRequest)
			return ElasticsearchConfiguration.OPERATION_INDEX;
		else if (request instanceof GetRequest)
			return ElasticsearchConfiguration.OPERATION_GET_BY_ID;
		else if (request instanceof BulkRequest)
			return ElasticsearchConfiguration.OPERATION_BULK_INDEX;
		else if (request instanceof DeleteRequest)
			return ElasticsearchConfiguration.OPERATION_DELETE;

		String operationConfig = exchange.getIn().getHeader(
				ElasticsearchConfiguration.PARAM_OPERATION, String.class);
		if (operationConfig == null) {
			operationConfig = getEndpoint().getConfig().getOperation();
		}
		if (operationConfig == null)
			throw new IllegalArgumentException(
					ElasticsearchConfiguration.PARAM_OPERATION + " value '"
							+ operationConfig + "' is not supported");
		return operationConfig;
	}

	public void process(Exchange exchange) throws Exception {
		// 2. Index and type will be set by:
		// a. If the incoming body is already an action request
		// b. If the body is not an action request we will use headers if they
		// are set.
		// c. If the body is not an action request and the headers aren't set we
		// will use the configuration.
		// No error is thrown by the component in the event none of the above
		// conditions are met. The java es client
		// will throw.

		Message message = exchange.getIn();
		ElasticsearchConfiguration config = getEndpoint().getConfig();
		final String operation = resolveOperation(exchange);

		// Set the index/type headers on the exchange if necessary. This is used
		// for type conversion.
		boolean configIndexName = false;
		String indexName = message.getHeader(
				ElasticsearchConfiguration.PARAM_INDEX_NAME, String.class);
		if (indexName == null) {
			message.setHeader(ElasticsearchConfiguration.PARAM_INDEX_NAME,
					getEndpoint().getConfig().getIndexName());
			configIndexName = true;
		}

		boolean configIndexType = false;
		String indexType = message.getHeader(
				ElasticsearchConfiguration.PARAM_INDEX_TYPE, String.class);
		if (indexType == null) {
			message.setHeader(ElasticsearchConfiguration.PARAM_INDEX_TYPE,
					getEndpoint().getConfig().getIndexName());
			configIndexType = true;
		}

		Client client = getEndpoint().getClient();
		if (ElasticsearchConfiguration.OPERATION_INDEX.equals(operation)) {
			IndexRequest indexRequest = message.getBody(IndexRequest.class);
			message.setBody(client.index(indexRequest).actionGet().getId());
		} else if (ElasticsearchConfiguration.OPERATION_GET_BY_ID
				.equals(operation)) {
			GetRequest getRequest = message.getBody(GetRequest.class);
			message.setBody(client.get(getRequest));
		} else if (ElasticsearchConfiguration.OPERATION_BULK_INDEX
				.equals(operation)) {
			BulkRequest bulkRequest = message.getBody(BulkRequest.class);
			List<String> indexedIds = new LinkedList<String>();
			for (BulkItemResponse response : client.bulk(bulkRequest)
					.actionGet().getItems()) {
				indexedIds.add(response.getId());
			}
			log.debug("List of successfully indexed document ids : {}",
					indexedIds);
			message.setBody(indexedIds);
		} else if (ElasticsearchConfiguration.OPERATION_DELETE
				.equals(operation)) {
			DeleteRequest deleteRequest = message.getBody(DeleteRequest.class);
			message.setBody(client.delete(deleteRequest).actionGet());
		} else {
			throw new IllegalArgumentException(
					ElasticsearchConfiguration.PARAM_OPERATION + " value '"
							+ operation + "' is not supported");
		}

		// If we set params via the configuration on this exchange, remove them
		// now. This preserves legacy behavior for this component and enables a
		// use case where one message can be sent to multiple elasticsearch
		// endpoints where the user is relying on the endpoint configuration
		// (index/type) rather than header values. If we do not clear this out
		// sending the same message (index request, for example) to multiple
		// elasticsearch endpoints would have the effect overriding any
		// subsequent endpoint index/type with the first endpoint index/type.
		if (configIndexName) {
			message.removeHeader(ElasticsearchConfiguration.PARAM_INDEX_NAME);
		}

		if (configIndexType) {
			message.removeHeader(ElasticsearchConfiguration.PARAM_INDEX_TYPE);
		}
	}
}
