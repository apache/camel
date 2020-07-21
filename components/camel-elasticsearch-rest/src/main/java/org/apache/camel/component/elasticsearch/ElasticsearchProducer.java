/*
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

import java.lang.reflect.InvocationTargetException;
import java.net.UnknownHostException;
import java.util.Collections;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.IOHelper;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.sniff.Sniffer;
import org.elasticsearch.client.sniff.SnifferBuilder;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.elasticsearch.ElasticsearchConstants.PARAM_SCROLL;
import static org.apache.camel.component.elasticsearch.ElasticsearchConstants.PARAM_SCROLL_KEEP_ALIVE_MS;

/**
 * Represents an Elasticsearch producer.
 */
public class ElasticsearchProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchProducer.class);

    protected final ElasticsearchConfiguration configuration;
    private RestClient client;
    private Sniffer sniffer;

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
            return ElasticsearchOperation.Index;
        } else if (request instanceof GetRequest) {
            return ElasticsearchOperation.GetById;
        } else if (request instanceof MultiGetRequest) {
            return ElasticsearchOperation.MultiGet;
        } else if (request instanceof UpdateRequest) {
            return ElasticsearchOperation.Update;
        } else if (request instanceof BulkRequest) {
            // do we want bulk or bulk_index?
            if (configuration.getOperation() == ElasticsearchOperation.BulkIndex) {
                return ElasticsearchOperation.BulkIndex;
            } else {
                return ElasticsearchOperation.Bulk;
            }
        } else if (request instanceof DeleteRequest) {
            return ElasticsearchOperation.Delete;
        } else if (request instanceof SearchRequest) {
            return ElasticsearchOperation.Search;
        } else if (request instanceof MultiSearchRequest) {
            return ElasticsearchOperation.MultiSearch;
        } else if (request instanceof DeleteIndexRequest) {
            return ElasticsearchOperation.DeleteIndex;
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

    @Override
    public void process(Exchange exchange) throws Exception {
        if (configuration.isDisconnect() && client == null) {
            startClient();
        }
        RestHighLevelClient restHighLevelClient = new HighLevelClient(client);
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

        Integer size = message.getHeader(ElasticsearchConstants.PARAM_SIZE, Integer.class);
        if (size == null) {
            message.setHeader(ElasticsearchConstants.PARAM_SIZE, configuration.getSize());
        }

        Integer from = message.getHeader(ElasticsearchConstants.PARAM_FROM, Integer.class);
        if (from == null) {
            message.setHeader(ElasticsearchConstants.PARAM_FROM, configuration.getFrom());
        }

        boolean configWaitForActiveShards = false;
        Integer waitForActiveShards = message.getHeader(ElasticsearchConstants.PARAM_WAIT_FOR_ACTIVE_SHARDS, Integer.class);
        if (waitForActiveShards == null) {
            message.setHeader(ElasticsearchConstants.PARAM_WAIT_FOR_ACTIVE_SHARDS, configuration.getWaitForActiveShards());
            configWaitForActiveShards = true;
        }

        if (operation == ElasticsearchOperation.Index) {
            IndexRequest indexRequest = message.getBody(IndexRequest.class);
            if (indexRequest == null) {
                throw new IllegalArgumentException("Wrong body type. Only Map, String, byte[], XContentBuilder or IndexRequest is allowed as a type");
            }
            message.setBody(restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT).getId());
        } else if (operation == ElasticsearchOperation.Update) {
            UpdateRequest updateRequest = message.getBody(UpdateRequest.class);
            if (updateRequest == null) {
                throw new IllegalArgumentException("Wrong body type. Only Map, String, byte[], XContentBuilder or UpdateRequest is allowed as a type");
            }
            message.setBody(restHighLevelClient.update(updateRequest, RequestOptions.DEFAULT).getId());
        } else if (operation == ElasticsearchOperation.GetById) {
            GetRequest getRequest = message.getBody(GetRequest.class);
            if (getRequest == null) {
                throw new IllegalArgumentException("Wrong body type. Only String or GetRequest is allowed as a type");
            }
            message.setBody(restHighLevelClient.get(getRequest, RequestOptions.DEFAULT));
        } else if (operation == ElasticsearchOperation.Bulk) {
            BulkRequest bulkRequest = message.getBody(BulkRequest.class);
            if (bulkRequest == null) {
                throw new IllegalArgumentException("Wrong body type. Only List, Collection or BulkRequest is allowed as a type");
            }
            message.setBody(restHighLevelClient.bulk(bulkRequest, RequestOptions.DEFAULT).getItems());
        } else if (operation == ElasticsearchOperation.BulkIndex) {
            BulkRequest bulkRequest = message.getBody(BulkRequest.class);
            if (bulkRequest == null) {
                throw new IllegalArgumentException("Wrong body type. Only List, Collection or BulkRequest is allowed as a type");
            }
            message.setBody(restHighLevelClient.bulk(bulkRequest, RequestOptions.DEFAULT).getItems());
        } else if (operation == ElasticsearchOperation.Delete) {
            DeleteRequest deleteRequest = message.getBody(DeleteRequest.class);
            if (deleteRequest == null) {
                throw new IllegalArgumentException("Wrong body type. Only String or DeleteRequest is allowed as a type");
            }
            message.setBody(restHighLevelClient.delete(deleteRequest, RequestOptions.DEFAULT).getResult());
        } else if (operation == ElasticsearchOperation.DeleteIndex) {
            DeleteIndexRequest deleteIndexRequest = message.getBody(DeleteIndexRequest.class);
            if (deleteIndexRequest == null) {
                throw new IllegalArgumentException("Wrong body type. Only String or DeleteIndexRequest is allowed as a type");
            }
            message.setBody(restHighLevelClient.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT).isAcknowledged());
        } else if (operation == ElasticsearchOperation.Exists) {
            // ExistsRequest API is deprecated, using SearchRequest instead with size=0 and terminate_after=1
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            sourceBuilder.size(0);
            sourceBuilder.terminateAfter(1);
            SearchRequest searchRequest = new SearchRequest(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class));
            searchRequest.source(sourceBuilder);
            try {
                restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
                message.setBody(true);
            } catch (ElasticsearchStatusException e) {
                if (e.status().equals(RestStatus.NOT_FOUND)) {
                    message.setBody(false);
                } else {
                    throw new IllegalStateException(e);
                }

            }
        } else if (operation == ElasticsearchOperation.Search) {
            SearchRequest searchRequest = message.getBody(SearchRequest.class);
            if (searchRequest == null) {
                throw new IllegalArgumentException("Wrong body type. Only Map, String or SearchRequest is allowed as a type");
            }
            // is it a scroll request ?
            boolean useScroll = message.getHeader(PARAM_SCROLL, configuration.isUseScroll(), Boolean.class);
            if (useScroll) {
                int scrollKeepAliveMs = message.getHeader(PARAM_SCROLL_KEEP_ALIVE_MS, configuration.getScrollKeepAliveMs(), Integer.class);
                ElasticsearchScrollRequestIterator scrollRequestIterator = new ElasticsearchScrollRequestIterator(searchRequest, restHighLevelClient, scrollKeepAliveMs, exchange);
                exchange.getIn().setBody(scrollRequestIterator);
            } else {
                message.setBody(restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT).getHits());
            }
        } else if (operation == ElasticsearchOperation.MultiSearch) {
            MultiSearchRequest searchRequest = message.getBody(MultiSearchRequest.class);
            if (searchRequest == null) {
                throw new IllegalArgumentException("Wrong body type. Only MultiSearchRequest is allowed as a type");
            }
            message.setBody(restHighLevelClient.msearch(searchRequest, RequestOptions.DEFAULT).getResponses());
        } else if (operation == ElasticsearchOperation.Ping) {
            message.setBody(restHighLevelClient.ping(RequestOptions.DEFAULT));
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

        if (configWaitForActiveShards) {
            message.removeHeader(ElasticsearchConstants.PARAM_WAIT_FOR_ACTIVE_SHARDS);
        }
        if (configuration.isDisconnect()) {
            IOHelper.close(client);
            IOHelper.close(restHighLevelClient);
            client = null;
            if (configuration.isEnableSniffer()) {
                IOHelper.close(sniffer);
                sniffer = null;
            }
        }

    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doStart() throws Exception {
        super.doStart();
        if (!configuration.isDisconnect()) {
            startClient();
        }
    }

    private void startClient() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException, UnknownHostException {
        if (client == null) {
            LOG.info("Connecting to the ElasticSearch cluster: {}", configuration.getClusterName());
            if (configuration.getHostAddressesList() != null
                && !configuration.getHostAddressesList().isEmpty()) {
                client = createClient();
            } else {
                LOG.warn("Incorrect ip address and port parameters settings for ElasticSearch cluster");
            }
        }
    }

    private RestClient createClient() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        final RestClientBuilder builder = RestClient.builder(configuration.getHostAddressesList().toArray(new HttpHost[0]));

        builder.setRequestConfigCallback(requestConfigBuilder ->
            requestConfigBuilder.setConnectTimeout(configuration.getConnectionTimeout()).setSocketTimeout(configuration.getSocketTimeout()));
        if (configuration.getUser() != null && configuration.getPassword() != null) {
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(configuration.getUser(), configuration.getPassword()));
            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                return httpClientBuilder;
            });
        }
        final RestClient restClient = builder.build();
        if (configuration.isEnableSniffer()) {
            SnifferBuilder snifferBuilder = Sniffer.builder(restClient);
            snifferBuilder.setSniffIntervalMillis(configuration.getSnifferInterval());
            snifferBuilder.setSniffAfterFailureDelayMillis(configuration.getSniffAfterFailureDelay());
            sniffer = snifferBuilder.build();
        }
        return restClient;
    }


    @Override
    protected void doStop() throws Exception {
        if (client != null) {
            LOG.info("Disconnecting from ElasticSearch cluster: {}", configuration.getClusterName());
            client.close();
            if (sniffer != null) {
                sniffer.close();
            }
        }
        super.doStop();
    }

    public RestClient getClient() {
        return client;
    }

    private final class HighLevelClient extends RestHighLevelClient {
        private HighLevelClient(RestClient restClient) {
            super(restClient, client -> { }, Collections.emptyList());
        }
    }
}
