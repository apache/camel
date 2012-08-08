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

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ExpectedBodyTypeException;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.elasticsearch.action.ListenableActionFuture;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.replication.ReplicationType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentBuilder;

/**
 * Represents an Elasticsearch producer.
 */
public class ElasticsearchProducer extends DefaultProducer {

    private ElasticsearchEndpoint endpoint;

    public ElasticsearchProducer(ElasticsearchEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) throws Exception {

        String operation = (String) exchange.getIn().getHeader(ElasticsearchConfiguration.PARAM_OPERATION);
        if (operation == null) {
            operation = endpoint.getConfig().getOperation();
        }

        if (operation == null) {
            throw new IllegalArgumentException(ElasticsearchConfiguration.PARAM_OPERATION + " is missing");
        }

        Client client = endpoint.getClient();

        if (operation.equalsIgnoreCase(ElasticsearchConfiguration.OPERATION_INDEX)) {
            addToIndex(client, exchange);
        } else if (operation.equalsIgnoreCase(ElasticsearchConfiguration.OPERATION_GET_BY_ID)) {
            getById(client, exchange);
        } else if (operation.equalsIgnoreCase(ElasticsearchConfiguration.OPERATION_DELETE)) {
            deleteById(client, exchange);
        } else {
            throw new IllegalArgumentException(ElasticsearchConfiguration.PARAM_OPERATION + " value '" + operation + "' is not supported");
        }
    }

    public void getById(Client client, Exchange exchange) {

        String indexName = exchange.getIn().getHeader(ElasticsearchConfiguration.PARAM_INDEX_NAME, String.class);
        if (indexName == null) {
            indexName = endpoint.getConfig().getIndexName();
        }

        String indexType = exchange.getIn().getHeader(ElasticsearchConfiguration.PARAM_INDEX_TYPE, String.class);
        if (indexType == null) {
            indexType = endpoint.getConfig().getIndexType();
        }

        String indexId = exchange.getIn().getBody(String.class);

        GetResponse response = client.prepareGet(indexName, indexType, indexId).execute().actionGet();
        exchange.getIn().setBody(response);
    }

    public void deleteById(Client client, Exchange exchange) {

        String indexName = exchange.getIn().getHeader(ElasticsearchConfiguration.PARAM_INDEX_NAME, String.class);
        if (indexName == null) {
            indexName = endpoint.getConfig().getIndexName();
        }

        String indexType = exchange.getIn().getHeader(ElasticsearchConfiguration.PARAM_INDEX_TYPE, String.class);
        if (indexType == null) {
            indexType = endpoint.getConfig().getIndexType();
        }

        String indexId = exchange.getIn().getBody(String.class);

        DeleteResponse response = client.prepareDelete(indexName, indexType, indexId).execute().actionGet();
        exchange.getIn().setBody(response);
    }

    public void addToIndex(Client client, Exchange exchange) {

        String indexName = exchange.getIn().getHeader(ElasticsearchConfiguration.PARAM_INDEX_NAME, String.class);
        if (indexName == null) {
            indexName = endpoint.getConfig().getIndexName();
        }

        String indexType = exchange.getIn().getHeader(ElasticsearchConfiguration.PARAM_INDEX_TYPE, String.class);
        if (indexType == null) {
            indexType = endpoint.getConfig().getIndexType();
        }

        IndexRequestBuilder prepareIndex = client.prepareIndex(indexName, indexType);

        if (!setIndexRequestSource(exchange.getIn(), prepareIndex)) {
            throw new ExpectedBodyTypeException(exchange, XContentBuilder.class);
        }
        ListenableActionFuture<IndexResponse> future = prepareIndex.execute();
        IndexResponse response = future.actionGet();
        exchange.getIn().setBody(response.getId());
    }

    private boolean setIndexRequestSource(Message msg, IndexRequestBuilder builder) {
        Object body = null;
        boolean converted = false;

        // order is important
        Class<?>[] types = new Class[] {XContentBuilder.class, Map.class, byte[].class, String.class};

        for (int i = 0; i < types.length && body == null; i++) {
            Class<?> type = types[i];
            body = msg.getBody(type);
        }

        if (body != null) {
            converted = true;
            if (body instanceof byte[]) {
                builder.setSource((byte[])body);
            } else if (body instanceof Map) {
                builder.setSource((Map<String, Object>)body);
            } else if (body instanceof String) {
                builder.setSource((String)body);
            } else if (body instanceof XContentBuilder) {
                builder.setSource((XContentBuilder)body);
            } else {
                converted = false;
            }
        }
        return converted;
    }
}
