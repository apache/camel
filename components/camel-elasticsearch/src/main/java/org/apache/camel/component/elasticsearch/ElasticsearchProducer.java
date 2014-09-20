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
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ExpectedBodyTypeException;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.elasticsearch.action.ListenableActionFuture;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentBuilder;

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

    public void process(Exchange exchange) throws Exception {
        String operation = exchange.getIn().getHeader(ElasticsearchConfiguration.PARAM_OPERATION, String.class);
        if (operation == null) {
            operation = getEndpoint().getConfig().getOperation();
        }

        if (operation == null) {
            throw new IllegalArgumentException(ElasticsearchConfiguration.PARAM_OPERATION + " is missing");
        }

        Client client = getEndpoint().getClient();

        if (operation.equalsIgnoreCase(ElasticsearchConfiguration.OPERATION_INDEX)) {
            addToIndex(client, exchange);
        } else if (operation.equalsIgnoreCase(ElasticsearchConfiguration.OPERATION_GET_BY_ID)) {
            getById(client, exchange);
        } else if (operation.equalsIgnoreCase(ElasticsearchConfiguration.OPERATION_DELETE)) {
            deleteById(client, exchange);
        } else if (operation.equalsIgnoreCase(ElasticsearchConfiguration.OPERATION_BULK_INDEX)) {
            addToIndexUsingBulk(client, exchange);
        } else {
            throw new IllegalArgumentException(ElasticsearchConfiguration.PARAM_OPERATION + " value '" + operation + "' is not supported");
        }
    }

    public void getById(Client client, Exchange exchange) {
        String indexName = exchange.getIn().getHeader(ElasticsearchConfiguration.PARAM_INDEX_NAME, String.class);
        if (indexName == null) {
            indexName = getEndpoint().getConfig().getIndexName();
        }

        String indexType = exchange.getIn().getHeader(ElasticsearchConfiguration.PARAM_INDEX_TYPE, String.class);
        if (indexType == null) {
            indexType = getEndpoint().getConfig().getIndexType();
        }

        String indexId = exchange.getIn().getBody(String.class);

        GetResponse response = client.prepareGet(indexName, indexType, indexId).execute().actionGet();
        exchange.getIn().setBody(response);
    }

    public void deleteById(Client client, Exchange exchange) {
        String indexName = exchange.getIn().getHeader(ElasticsearchConfiguration.PARAM_INDEX_NAME, String.class);
        if (indexName == null) {
            indexName = getEndpoint().getConfig().getIndexName();
        }

        String indexType = exchange.getIn().getHeader(ElasticsearchConfiguration.PARAM_INDEX_TYPE, String.class);
        if (indexType == null) {
            indexType = getEndpoint().getConfig().getIndexType();
        }

        String indexId = exchange.getIn().getBody(String.class);

        DeleteResponse response = client.prepareDelete(indexName, indexType, indexId).execute().actionGet();
        exchange.getIn().setBody(response);
    }

    public void addToIndex(Client client, Exchange exchange) {
        String indexName = exchange.getIn().getHeader(ElasticsearchConfiguration.PARAM_INDEX_NAME, String.class);
        if (indexName == null) {
            indexName = getEndpoint().getConfig().getIndexName();
        }

        String indexType = exchange.getIn().getHeader(ElasticsearchConfiguration.PARAM_INDEX_TYPE, String.class);
        if (indexType == null) {
            indexType = getEndpoint().getConfig().getIndexType();
        }

        IndexRequestBuilder prepareIndex = client.prepareIndex(indexName, indexType);

        Object document = extractDocumentFromMessage(exchange.getIn());

        if (!setIndexRequestSource(document, prepareIndex)) {
            throw new ExpectedBodyTypeException(exchange, XContentBuilder.class);
        }
        ListenableActionFuture<IndexResponse> future = prepareIndex.execute();
        IndexResponse response = future.actionGet();
        exchange.getIn().setBody(response.getId());
    }

    public void addToIndexUsingBulk(Client client, Exchange exchange) {
        String indexName = exchange.getIn().getHeader(ElasticsearchConfiguration.PARAM_INDEX_NAME, String.class);
        if (indexName == null) {
            indexName = getEndpoint().getConfig().getIndexName();
        }

        String indexType = exchange.getIn().getHeader(ElasticsearchConfiguration.PARAM_INDEX_TYPE, String.class);
        if (indexType == null) {
            indexType = getEndpoint().getConfig().getIndexType();
        }

        log.debug("Preparing Bulk Request");
        BulkRequestBuilder bulkRequest = client.prepareBulk();

        List<?> body = exchange.getIn().getBody(List.class);

        for (Object document : body) {
            IndexRequestBuilder prepareIndex = client.prepareIndex(indexName, indexType);
            log.trace("Indexing document : {}", document);
            if (!setIndexRequestSource(document, prepareIndex)) {
                throw new ExpectedBodyTypeException(exchange, XContentBuilder.class);
            }
            bulkRequest.add(prepareIndex);
        }

        ListenableActionFuture<BulkResponse> future = bulkRequest.execute();
        BulkResponse bulkResponse = future.actionGet();

        List<String> indexedIds = new LinkedList<String>();
        for (BulkItemResponse response : bulkResponse.getItems()) {
            indexedIds.add(response.getId());
        }
        log.debug("List of successfully indexed document ids : {}", indexedIds);
        exchange.getIn().setBody(indexedIds);
    }


    private Object extractDocumentFromMessage(Message msg) {
        Object body = null;

        // order is important
        Class<?>[] types = new Class[] {XContentBuilder.class, Map.class, byte[].class, String.class};

        for (int i = 0; i < types.length && body == null; i++) {
            Class<?> type = types[i];
            body = msg.getBody(type);
        }

        return body;

    }


    @SuppressWarnings("unchecked")
    private boolean setIndexRequestSource(Object document, IndexRequestBuilder builder) {
        boolean converted = false;

        if (document != null) {
            converted = true;
            if (document instanceof byte[]) {
                builder.setSource((byte[])document);
            } else if (document instanceof Map) {
                builder.setSource((Map<String, Object>) document);
            } else if (document instanceof String) {
                builder.setSource((String)document);
            } else if (document instanceof XContentBuilder) {
                builder.setSource((XContentBuilder)document);
            } else {
                converted = false;
            }
        }
        return converted;
    }
}
