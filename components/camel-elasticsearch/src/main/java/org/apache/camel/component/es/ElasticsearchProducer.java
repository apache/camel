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
package org.apache.camel.component.es;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.concurrent.CompletableFuture;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.WriteResponseBase;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.MgetRequest;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import co.elastic.clients.elasticsearch.core.MsearchRequest;
import co.elastic.clients.elasticsearch.core.MsearchResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexResponse;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.sniff.Sniffer;
import org.elasticsearch.client.sniff.SnifferBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.es.ElasticsearchConstants.PARAM_SCROLL;
import static org.apache.camel.component.es.ElasticsearchConstants.PARAM_SCROLL_KEEP_ALIVE_MS;

/**
 * Represents an Elasticsearch producer.
 */
class ElasticsearchProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchProducer.class);

    protected final ElasticsearchConfiguration configuration;
    private final Object mutex = new Object();
    private volatile RestClient client;
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
        } else if (request instanceof MgetRequest) {
            return ElasticsearchOperation.MultiGet;
        } else if (request instanceof UpdateRequest) {
            return ElasticsearchOperation.Update;
        } else if (request instanceof BulkRequest) {
            return ElasticsearchOperation.Bulk;
        } else if (request instanceof DeleteRequest) {
            return ElasticsearchOperation.Delete;
        } else if (request instanceof SearchRequest) {
            return ElasticsearchOperation.Search;
        } else if (request instanceof MsearchRequest) {
            return ElasticsearchOperation.MultiSearch;
        } else if (request instanceof DeleteIndexRequest) {
            return ElasticsearchOperation.DeleteIndex;
        }

        ElasticsearchOperation operationConfig
                = exchange.getIn().getHeader(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.class);
        if (operationConfig == null) {
            operationConfig = configuration.getOperation();
        }
        if (operationConfig == null) {
            throw new IllegalArgumentException(
                    ElasticsearchConstants.PARAM_OPERATION + " value is mandatory");
        }
        return operationConfig;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            if (configuration.isDisconnect() && client == null) {
                startClient();
            }
            final ObjectMapper mapper = new ObjectMapper();
            mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
            ElasticsearchTransport transport = new RestClientTransport(client, new JacksonJsonpMapper(mapper));
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

            Class<?> documentClass = message.getHeader(ElasticsearchConstants.PARAM_DOCUMENT_CLASS, Class.class);
            if (documentClass == null) {
                documentClass = configuration.getDocumentClass();
            }

            ActionContext ctx = new ActionContext(exchange, callback, transport, configIndexName, configWaitForActiveShards);

            switch (operation) {
                case Index: {
                    processIndexAsync(ctx);
                    break;
                }
                case Update: {
                    processUpdateAsync(ctx, documentClass);
                    break;
                }
                case GetById: {
                    processGetByIdAsync(ctx, documentClass);
                    break;
                }
                case Bulk: {
                    processBulkAsync(ctx);
                    break;
                }
                case Delete: {
                    processDeleteAsync(ctx);
                    break;
                }
                case DeleteIndex: {
                    processDeleteIndexAsync(ctx);
                    break;
                }
                case Exists: {
                    processExistsAsync(ctx);
                    break;
                }
                case Search: {
                    SearchRequest.Builder searchRequestBuilder = message.getBody(SearchRequest.Builder.class);
                    if (searchRequestBuilder == null) {
                        throw new IllegalArgumentException(
                                "Wrong body type. Only Map, String or SearchRequest.Builder is allowed as a type");
                    }
                    // is it a scroll request ?
                    boolean useScroll = message.getHeader(PARAM_SCROLL, configuration.isUseScroll(), Boolean.class);
                    if (useScroll) {
                        // As a scroll request is expected, for the sake of simplicity, the synchronous mode is preserved
                        int scrollKeepAliveMs
                                = message.getHeader(PARAM_SCROLL_KEEP_ALIVE_MS, configuration.getScrollKeepAliveMs(),
                                        Integer.class);
                        ElasticsearchScrollRequestIterator<?> scrollRequestIterator = new ElasticsearchScrollRequestIterator<>(
                                searchRequestBuilder, new ElasticsearchClient(transport), scrollKeepAliveMs, exchange,
                                documentClass);
                        exchange.getIn().setBody(scrollRequestIterator);
                        cleanup(ctx);
                        callback.done(true);
                        return true;
                    } else {
                        onComplete(
                                new ElasticsearchAsyncClient(transport).search(searchRequestBuilder.build(), documentClass)
                                        .thenApply(SearchResponse::hits),
                                ctx);
                    }
                    break;
                }
                case MultiSearch: {
                    processMultiSearchAsync(ctx, documentClass);
                    break;
                }
                case MultiGet: {
                    processMultiGetAsync(ctx, documentClass);
                    break;
                }
                case Ping: {
                    processPingAsync(ctx);
                    break;
                }
                default: {
                    throw new IllegalArgumentException(
                            ElasticsearchConstants.PARAM_OPERATION + " value '" + operation + "' is not supported");
                }
            }
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
        return false;
    }

    /**
     * Executes asynchronously a ping to the Elastic cluster.
     */
    private void processPingAsync(ActionContext ctx) {
        onComplete(
                ctx.getClient().ping()
                        .thenApply(BooleanResponse::value),
                ctx);
    }

    /**
     * Executes asynchronously a multi-get request.
     */
    private void processMultiGetAsync(ActionContext ctx, Class<?> documentClass) {
        MgetRequest.Builder mgetRequestBuilder = ctx.getMessage().getBody(MgetRequest.Builder.class);
        if (mgetRequestBuilder == null) {
            throw new IllegalArgumentException("Wrong body type. Only MgetRequest.Builder is allowed as a type");
        }
        onComplete(
                ctx.getClient().mget(mgetRequestBuilder.build(), documentClass)
                        .thenApply(MgetResponse::docs),
                ctx);
    }

    /**
     * Executes asynchronously a multi-search request.
     */
    private void processMultiSearchAsync(ActionContext ctx, Class<?> documentClass) {
        MsearchRequest.Builder msearchRequestBuilder = ctx.getMessage().getBody(MsearchRequest.Builder.class);
        if (msearchRequestBuilder == null) {
            throw new IllegalArgumentException("Wrong body type. Only MsearchRequest.Builder is allowed as a type");
        }
        onComplete(
                ctx.getClient().msearch(msearchRequestBuilder.build(), documentClass)
                        .thenApply(MsearchResponse::responses),
                ctx);
    }

    /**
     * Checks asynchronously if a given index exists.
     */
    private void processExistsAsync(ActionContext ctx) {
        ExistsRequest.Builder builder = new ExistsRequest.Builder();
        builder.index(ctx.getMessage().getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class));
        onComplete(
                ctx.getClient().indices().exists(builder.build())
                        .thenApply(BooleanResponse::value),
                ctx);
    }

    /**
     * Deletes asynchronously an index.
     */
    private void processDeleteIndexAsync(ActionContext ctx) {
        DeleteIndexRequest.Builder deleteIndexRequestBuilder = ctx.getMessage().getBody(DeleteIndexRequest.Builder.class);
        if (deleteIndexRequestBuilder == null) {
            throw new IllegalArgumentException(
                    "Wrong body type. Only String or DeleteIndexRequest.Builder is allowed as a type");
        }
        onComplete(
                ctx.getClient().indices().delete(deleteIndexRequestBuilder.build())
                        .thenApply(DeleteIndexResponse::acknowledged),
                ctx);
    }

    /**
     * Deletes asynchronously a document.
     */
    private void processDeleteAsync(ActionContext ctx) {
        DeleteRequest.Builder deleteRequestBuilder = ctx.getMessage().getBody(DeleteRequest.Builder.class);
        if (deleteRequestBuilder == null) {
            throw new IllegalArgumentException(
                    "Wrong body type. Only String or DeleteRequest.Builder is allowed as a type");
        }
        onComplete(
                ctx.getClient().delete(deleteRequestBuilder.build())
                        .thenApply(DeleteResponse::result),
                ctx);
    }

    /**
     * Executes asynchronously bulk operations.
     */
    private void processBulkAsync(ActionContext ctx) {
        BulkRequest.Builder bulkRequestBuilder = ctx.getMessage().getBody(BulkRequest.Builder.class);
        if (bulkRequestBuilder == null) {
            throw new IllegalArgumentException(
                    "Wrong body type. Only Iterable or BulkRequest.Builder is allowed as a type");
        }
        onComplete(
                ctx.getClient().bulk(bulkRequestBuilder.build())
                        .thenApply(BulkResponse::items),
                ctx);
    }

    /**
     * Finds asynchronously a document by id.
     */
    private void processGetByIdAsync(ActionContext ctx, Class<?> documentClass) {
        GetRequest.Builder getRequestBuilder = ctx.getMessage().getBody(GetRequest.Builder.class);
        if (getRequestBuilder == null) {
            throw new IllegalArgumentException(
                    "Wrong body type. Only String or GetRequest.Builder is allowed as a type");
        }
        onComplete(
                ctx.getClient().get(getRequestBuilder.build(), documentClass),
                ctx);
    }

    /**
     * Updates asynchronously a document.
     */
    private void processUpdateAsync(ActionContext ctx, Class<?> documentClass) {
        UpdateRequest.Builder<?, ?> updateRequestBuilder = ctx.getMessage().getBody(UpdateRequest.Builder.class);
        onComplete(
                ctx.getClient().update(updateRequestBuilder.build(), documentClass)
                        .thenApply(WriteResponseBase::id),
                ctx);
    }

    /**
     * Indexes asynchronously a document.
     */
    private void processIndexAsync(ActionContext ctx) {
        IndexRequest.Builder<?> indexRequestBuilder = ctx.getMessage().getBody(IndexRequest.Builder.class);
        onComplete(
                ctx.getClient().index(indexRequestBuilder.build())
                        .thenApply(WriteResponseBase::id),
                ctx);
    }

    /**
     * Add actions to perform once the given future is complete.
     *
     * @param future the future to complete with specific actions.
     * @param ctx    the context of the asynchronous task.
     * @param <T>    the result type returned by the future.
     */
    private <T> void onComplete(CompletableFuture<T> future, ActionContext ctx) {
        final Exchange exchange = ctx.getExchange();
        future.thenAccept(r -> exchange.getIn().setBody(r))
                .thenAccept(r -> cleanup(ctx))
                .whenComplete(
                        (r, e) -> {
                            try {
                                if (e != null) {
                                    exchange.setException(new CamelExchangeException(
                                            "An error occurred while executing the action", exchange, e));
                                }
                            } finally {
                                ctx.getCallback().done(false);
                            }
                        });
    }

    /**
     * The cleanup task to execute once everything is done.
     */
    private void cleanup(ActionContext ctx) {

        try {
            Message message = ctx.getMessage();

            // If we set params via the configuration on this exchange, remove them
            // now. This preserves legacy behavior for this component and enables a
            // use case where one message can be sent to multiple elasticsearch
            // endpoints where the user is relying on the endpoint configuration
            // (index/type) rather than header values. If we do not clear this out
            // sending the same message (index request, for example) to multiple
            // elasticsearch endpoints would have the effect overriding any
            // subsequent endpoint index/type with the first endpoint index/type.
            if (ctx.isConfigIndexName()) {
                message.removeHeader(ElasticsearchConstants.PARAM_INDEX_NAME);
            }

            if (ctx.isConfigWaitForActiveShards()) {
                message.removeHeader(ElasticsearchConstants.PARAM_WAIT_FOR_ACTIVE_SHARDS);
            }
            if (configuration.isDisconnect()) {
                IOHelper.close(ctx.getTransport());
                if (configuration.isEnableSniffer()) {
                    IOHelper.close(sniffer);
                    sniffer = null;
                }
                IOHelper.close(client);
                client = null;
            }
        } catch (Exception e) {
            LOG.warn("Could not execute the cleanup task", e);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (!configuration.isDisconnect()) {
            startClient();
        }
    }

    private void startClient() {
        if (client == null) {
            synchronized (mutex) {
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
        }
    }

    private RestClient createClient() {
        final RestClientBuilder builder = RestClient.builder(configuration.getHostAddressesList().toArray(new HttpHost[0]));

        builder.setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                .setConnectTimeout(configuration.getConnectionTimeout()).setSocketTimeout(configuration.getSocketTimeout()));
        if (configuration.getUser() != null && configuration.getPassword() != null) {
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(configuration.getUser(), configuration.getPassword()));
            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                if (configuration.getCertificatePath() != null) {
                    httpClientBuilder.setSSLContext(createSslContextFromCa());
                }
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

    /**
     * A SSL context based on the self-signed CA, so that using this SSL Context allows to connect to the Elasticsearch
     * service
     *
     * @return a customized SSL Context
     */
    private SSLContext createSslContextFromCa() {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            InputStream resolveMandatoryResourceAsInputStream
                    = ResourceHelper.resolveMandatoryResourceAsInputStream(getEndpoint().getCamelContext(),
                            configuration.getCertificatePath());
            Certificate trustedCa = factory.generateCertificate(resolveMandatoryResourceAsInputStream);
            KeyStore trustStore = KeyStore.getInstance("pkcs12");
            trustStore.load(null, null);
            trustStore.setCertificateEntry("ca", trustedCa);

            final SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
            TrustManagerFactory trustManagerFactory
                    = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * An inner class providing all the information that an asynchronous action could need.
     */
    private static class ActionContext {

        private final Exchange exchange;
        private final AsyncCallback callback;
        private final ElasticsearchTransport transport;
        private final boolean configIndexName;
        private final boolean configWaitForActiveShards;

        ActionContext(Exchange exchange, AsyncCallback callback, ElasticsearchTransport transport, boolean configIndexName,
                      boolean configWaitForActiveShards) {
            this.exchange = exchange;
            this.callback = callback;
            this.transport = transport;
            this.configIndexName = configIndexName;
            this.configWaitForActiveShards = configWaitForActiveShards;
        }

        ElasticsearchTransport getTransport() {
            return transport;
        }

        ElasticsearchAsyncClient getClient() {
            return new ElasticsearchAsyncClient(transport);
        }

        boolean isConfigIndexName() {
            return configIndexName;
        }

        boolean isConfigWaitForActiveShards() {
            return configWaitForActiveShards;
        }

        Exchange getExchange() {
            return exchange;
        }

        AsyncCallback getCallback() {
            return callback;
        }

        Message getMessage() {
            return exchange.getIn();
        }
    }
}
