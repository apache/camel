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

import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an Elasticsearch producer.
 */
public class ElasticsearchProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchProducer.class);

    protected final ElasticsearchConfiguration configuration;
    private TransportClient client;

    public ElasticsearchProducer(ElasticsearchEndpoint endpoint, ElasticsearchConfiguration configuration) {
        super(endpoint);
        this.configuration = configuration;
        this.client = endpoint.getClient();
    }

    private ElasticsearchOperation resolveOperation(Exchange exchange) {
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
        if (request instanceof IndexRequest) {
            return ElasticsearchOperation.INDEX;
        } else if (request instanceof GetRequest) {
            return ElasticsearchOperation.GET_BY_ID;
        } else if (request instanceof MultiGetRequest) {
            return ElasticsearchOperation.MULTIGET;
        } else if (request instanceof UpdateRequest) {
            return ElasticsearchOperation.UPDATE;
        } else if (request instanceof BulkRequest) {
            // do we want bulk or bulk_index?
            if (configuration.getOperation() == ElasticsearchOperation.BULK_INDEX) {
                return ElasticsearchOperation.BULK_INDEX;
            } else {
                return ElasticsearchOperation.BULK;
            }
        } else if (request instanceof DeleteRequest) {
            return ElasticsearchOperation.DELETE;
        } else if (request instanceof SearchRequest) {
            return ElasticsearchOperation.SEARCH;
        } else if (request instanceof MultiSearchRequest) {
            return ElasticsearchOperation.MULTISEARCH;
        } else if (request instanceof DeleteIndexRequest) {
            return ElasticsearchOperation.DELETE_INDEX;
        }

        ElasticsearchOperation operationConfig = exchange.getIn().getHeader(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.class);
        if (operationConfig == null) {
            operationConfig = configuration.getOperation();
        }
        if (operationConfig == null) {
            throw new IllegalArgumentException(ElasticsearchConstants.PARAM_OPERATION + " value '" + operationConfig + "' is not supported");
        }
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
        final ElasticsearchOperation operation = resolveOperation(exchange);

        // Set the index/type headers on the exchange if necessary. This is used
        // for type conversion.
        boolean configIndexName = false;
        String indexName = message.getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class);
        if (indexName == null) {
            message.setHeader(ElasticsearchConstants.PARAM_INDEX_NAME, configuration.getIndexName());
            configIndexName = true;
        }

        boolean configIndexType = false;
        String indexType = message.getHeader(ElasticsearchConstants.PARAM_INDEX_TYPE, String.class);
        if (indexType == null) {
            message.setHeader(ElasticsearchConstants.PARAM_INDEX_TYPE, configuration.getIndexType());
            configIndexType = true;
        }

        boolean configWaitForActiveShards = false;
        Integer waitForActiveShards = message.getHeader(ElasticsearchConstants.PARAM_WAIT_FOR_ACTIVE_SHARDS, Integer.class);
        if (waitForActiveShards == null) {
            message.setHeader(ElasticsearchConstants.PARAM_WAIT_FOR_ACTIVE_SHARDS, configuration.getWaitForActiveShards());
            configWaitForActiveShards = true;
        }

        if (operation == ElasticsearchOperation.INDEX) {
            IndexRequest indexRequest = message.getBody(IndexRequest.class);
            message.setBody(client.index(indexRequest).actionGet().getId());
        } else if (operation == ElasticsearchOperation.UPDATE) {
            UpdateRequest updateRequest = message.getBody(UpdateRequest.class);
            message.setBody(client.update(updateRequest).actionGet().getId());
        } else if (operation == ElasticsearchOperation.GET_BY_ID) {
            GetRequest getRequest = message.getBody(GetRequest.class);
            message.setBody(client.get(getRequest));
        } else if (operation == ElasticsearchOperation.MULTIGET) {
            MultiGetRequest multiGetRequest = message.getBody(MultiGetRequest.class);
            message.setBody(client.multiGet(multiGetRequest));
        } else if (operation == ElasticsearchOperation.BULK) {
            BulkRequest bulkRequest = message.getBody(BulkRequest.class);
            message.setBody(client.bulk(bulkRequest).actionGet());
        } else if (operation == ElasticsearchOperation.BULK_INDEX) {
            BulkRequest bulkRequest = message.getBody(BulkRequest.class);
            List<String> indexedIds = new ArrayList<String>();
            for (BulkItemResponse response : client.bulk(bulkRequest).actionGet().getItems()) {
                indexedIds.add(response.getId());
            }
            message.setBody(indexedIds);
        } else if (operation == ElasticsearchOperation.DELETE) {
            DeleteRequest deleteRequest = message.getBody(DeleteRequest.class);
            message.setBody(client.delete(deleteRequest).actionGet());
        } else if (operation == ElasticsearchOperation.EXISTS) {
            // ExistsRequest API is deprecated, using SearchRequest instead with size=0 and terminate_after=1
            SearchRequest searchRequest = new SearchRequest(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class));
            try {
                client.prepareSearch(searchRequest.indices()).setSize(0).setTerminateAfter(1).get();
                message.setBody(true);
            } catch (IndexNotFoundException e) {
                message.setBody(false);
            }
        } else if (operation == ElasticsearchOperation.SEARCH) {
            SearchRequest searchRequest = message.getBody(SearchRequest.class);
            message.setBody(client.search(searchRequest).actionGet());
        } else if (operation == ElasticsearchOperation.MULTISEARCH) {
            MultiSearchRequest multiSearchRequest = message.getBody(MultiSearchRequest.class);
            message.setBody(client.multiSearch(multiSearchRequest));
        } else if (operation == ElasticsearchOperation.DELETE_INDEX) {
            DeleteIndexRequest deleteIndexRequest = message.getBody(DeleteIndexRequest.class);
            message.setBody(client.admin().indices().delete(deleteIndexRequest).actionGet());
        } else {
            throw new IllegalArgumentException(ElasticsearchConstants.PARAM_OPERATION + " value '" + operation + "' is not supported");
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
            message.removeHeader(ElasticsearchConstants.PARAM_INDEX_NAME);
        }

        if (configIndexType) {
            message.removeHeader(ElasticsearchConstants.PARAM_INDEX_TYPE);
        }

        if (configWaitForActiveShards) {
            message.removeHeader(ElasticsearchConstants.PARAM_WAIT_FOR_ACTIVE_SHARDS);
        }

    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doStart() throws Exception {
        super.doStart();

        if (client == null) {
            LOG.info("Connecting to the ElasticSearch cluster: {}", configuration.getClusterName());
            if (configuration.getIp() != null) {
                client = createClient().addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(configuration.getIp()), configuration.getPort()));
            } else if (configuration.getTransportAddressesList() != null && !configuration.getTransportAddressesList().isEmpty()) {
                List<TransportAddress> addresses = new ArrayList<TransportAddress>(configuration.getTransportAddressesList().size());
                addresses.addAll(configuration.getTransportAddressesList());
                client = createClient().addTransportAddresses(addresses.toArray(new TransportAddress[addresses.size()]));
            } else {
                LOG.info("Incorrect ip address and port parameters settings for ElasticSearch cluster");
            }
        }
    }

    private TransportClient createClient() throws Exception {
        final Settings.Builder settings = getSettings();
        final CamelContext camelContext = getEndpoint().getCamelContext();
        final Class<?> clazz = camelContext.getClassResolver().resolveClass("org.elasticsearch.xpack.client.PreBuiltXPackTransportClient");
        if (clazz != null) {
            Constructor<?> ctor = clazz.getConstructor(Settings.class, Class[].class);
            settings.put("xpack.security.user", configuration.getUser() + ":" + configuration.getPassword())
                .put("xpack.security.transport.ssl.enabled", configuration.getEnableSSL());
            LOG.info("XPack Client was found on the classpath");
            return (TransportClient) ctor.newInstance(new Object[]{settings.build(), new Class[0]});
        } else {
            LOG.debug("XPack Client was not found on the classpath, using the standard client.");
            return new PreBuiltTransportClient(settings.build());
        }
    }

    private Settings.Builder getSettings() {
        final Settings.Builder settings = Settings.builder()
            .put("cluster.name", configuration.getClusterName())
            .put("client.transport.sniff", configuration.getClientTransportSniff())
            .put("transport.ping_schedule", configuration.getPingSchedule())
            .put("client.transport.ping_timeout", configuration.getPingTimeout())
            .put("client.transport.sniff", configuration.getClientTransportSniff())
            .put("request.headers.X-Found-Cluster", configuration.getClusterName());
        return settings;
    }

    @Override
    protected void doStop() throws Exception {
        if (client != null) {
            LOG.info("Disconnecting from ElasticSearch cluster: " + configuration.getClusterName());
            client.close();
            client = null;
        }
        super.doStop();
    }

    public TransportClient getClient() {
        return client;
    }
}