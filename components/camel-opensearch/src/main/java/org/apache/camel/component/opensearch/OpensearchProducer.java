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
package org.apache.camel.component.opensearch;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.concurrent.CompletableFuture;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

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
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.WriteResponseBase;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.MgetRequest;
import org.opensearch.client.opensearch.core.MgetResponse;
import org.opensearch.client.opensearch.core.MsearchRequest;
import org.opensearch.client.opensearch.core.MsearchResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.UpdateResponse;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexResponse;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.opensearch.client.sniff.Sniffer;
import org.opensearch.client.sniff.SnifferBuilder;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.endpoints.BooleanResponse;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.opensearch.OpensearchConstants.PARAM_SCROLL;
import static org.apache.camel.component.opensearch.OpensearchConstants.PARAM_SCROLL_KEEP_ALIVE_MS;

/**
 * Represents an Opensearch producer.
 */
class OpensearchProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(OpensearchProducer.class);

    protected final OpensearchConfiguration configuration;
    private final Object mutex = new Object();
    private volatile RestClient client;
    private Sniffer sniffer;

    public OpensearchProducer(OpensearchEndpoint endpoint, OpensearchConfiguration configuration) {
        super(endpoint);
        this.configuration = configuration;
        this.client = endpoint.getClient();
    }

    private OpensearchOperation resolveOperation(Exchange exchange) {
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
        if (request != null) {
            LOG.debug("Operation request body: {}", request);
        }

        if (request instanceof IndexRequest) {
            return OpensearchOperation.Index;
        } else if (request instanceof GetRequest) {
            return OpensearchOperation.GetById;
        } else if (request instanceof MgetRequest) {
            return OpensearchOperation.MultiGet;
        } else if (request instanceof UpdateRequest) {
            return OpensearchOperation.Update;
        } else if (request instanceof BulkRequest) {
            return OpensearchOperation.Bulk;
        } else if (request instanceof DeleteRequest) {
            return OpensearchOperation.Delete;
        } else if (request instanceof SearchRequest) {
            return OpensearchOperation.Search;
        } else if (request instanceof MsearchRequest) {
            return OpensearchOperation.MultiSearch;
        } else if (request instanceof DeleteIndexRequest) {
            return OpensearchOperation.DeleteIndex;
        }

        OpensearchOperation operationConfig
                = exchange.getIn().getHeader(OpensearchConstants.PARAM_OPERATION, OpensearchOperation.class);

        LOG.debug("Operation obtained from header {}: {}", OpensearchConstants.PARAM_OPERATION, operationConfig);

        if (operationConfig == null) {
            operationConfig = configuration.getOperation();
        }

        LOG.debug("Operation obtained from config: {}", operationConfig);

        if (operationConfig == null) {
            throw new IllegalArgumentException(
                    OpensearchConstants.PARAM_OPERATION + " value is mandatory");
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
            OpenSearchTransport transport = new RestClientTransport(client, new JacksonJsonpMapper(mapper));
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
            final OpensearchOperation operation = resolveOperation(exchange);

            // Set the index/type headers on the exchange if necessary. This is used
            // for type conversion.
            boolean configIndexName = false;
            String indexName = message.getHeader(OpensearchConstants.PARAM_INDEX_NAME, String.class);
            if (indexName == null) {
                message.setHeader(OpensearchConstants.PARAM_INDEX_NAME, configuration.getIndexName());
                configIndexName = true;
            }

            Integer size = message.getHeader(OpensearchConstants.PARAM_SIZE, Integer.class);
            if (size == null) {
                message.setHeader(OpensearchConstants.PARAM_SIZE, configuration.getSize());
            }

            Integer from = message.getHeader(OpensearchConstants.PARAM_FROM, Integer.class);
            if (from == null) {
                message.setHeader(OpensearchConstants.PARAM_FROM, configuration.getFrom());
            }

            boolean configWaitForActiveShards = false;
            Integer waitForActiveShards = message.getHeader(OpensearchConstants.PARAM_WAIT_FOR_ACTIVE_SHARDS, Integer.class);
            if (waitForActiveShards == null) {
                message.setHeader(OpensearchConstants.PARAM_WAIT_FOR_ACTIVE_SHARDS, configuration.getWaitForActiveShards());
                configWaitForActiveShards = true;
            }

            Class<?> documentClass = message.getHeader(OpensearchConstants.PARAM_DOCUMENT_CLASS, Class.class);
            if (documentClass == null) {
                documentClass = configuration.getDocumentClass();
            }

            ActionContext ctx = new ActionContext(exchange, callback, transport, configIndexName, configWaitForActiveShards);

            switch (operation) {
                case Index -> processIndexAsync(ctx);
                case Update -> processUpdateAsync(ctx, documentClass);
                case GetById -> processGetByIdAsync(ctx, documentClass);
                case Bulk -> processBulkAsync(ctx);
                case Delete -> processDeleteAsync(ctx);
                case DeleteIndex -> processDeleteIndexAsync(ctx);
                case Exists -> processExistsAsync(ctx);
                case Search -> {
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
                        OpensearchScrollRequestIterator<?> scrollRequestIterator = new OpensearchScrollRequestIterator<>(
                                searchRequestBuilder, new OpenSearchClient(transport), scrollKeepAliveMs, exchange,
                                documentClass);
                        exchange.getIn().setBody(scrollRequestIterator);
                        cleanup(ctx);
                        callback.done(true);
                        return true;
                    } else {
                        onComplete(
                                ctx.getClient().search(searchRequestBuilder.build(), documentClass)
                                        .thenApply(SearchResponse::hits),
                                ctx);
                    }
                }
                case MultiSearch -> processMultiSearchAsync(ctx, documentClass);
                case MultiGet -> processMultiGetAsync(ctx, documentClass);
                case Ping -> processPingAsync(ctx);
                default -> throw new IllegalArgumentException(
                        OpensearchConstants.PARAM_OPERATION + " value '" + operation + "' is not supported");
            }
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
        return false;
    }

    /**
     * Executes asynchronously a ping to the OpenSearch cluster.
     */
    private void processPingAsync(ActionContext ctx) throws IOException {
        onComplete(
                ctx.getClient().ping()
                        .thenApply(BooleanResponse::value),
                ctx);
    }

    /**
     * Executes asynchronously a multi-get request.
     */
    private void processMultiGetAsync(ActionContext ctx, Class<?> documentClass) throws IOException {
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
    private void processMultiSearchAsync(ActionContext ctx, Class<?> documentClass) throws IOException {
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
    private void processExistsAsync(ActionContext ctx) throws IOException {
        ExistsRequest.Builder builder = new ExistsRequest.Builder();
        builder.index(ctx.getMessage().getHeader(OpensearchConstants.PARAM_INDEX_NAME, String.class));
        onComplete(
                ctx.getClient().indices().exists(builder.build())
                        .thenApply(BooleanResponse::value),
                ctx);
    }

    /**
     * Deletes asynchronously an index.
     */
    private void processDeleteIndexAsync(ActionContext ctx) throws IOException {
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
    private void processDeleteAsync(ActionContext ctx) throws IOException {
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
    private void processBulkAsync(ActionContext ctx) throws IOException {
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
    private void processGetByIdAsync(ActionContext ctx, Class<?> documentClass) throws IOException {
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
    private void processUpdateAsync(ActionContext ctx, Class<?> documentClass) throws IOException {
        var updateRequestBuilder = ctx.getMessage().getBody(UpdateRequest.Builder.class);
        onComplete(
                ctx.getClient().update(updateRequestBuilder.build(), documentClass)
                        .thenApply(r -> ((UpdateResponse<?>) r).id()),
                ctx);
    }

    /**
     * Indexes asynchronously a document.
     */
    private void processIndexAsync(ActionContext ctx) throws IOException {
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
        final Exchange exchange = ctx.exchange();
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
                                ctx.callback().done(false);
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
            // use case where one message can be sent to multiple OpenSearch
            // endpoints where the user is relying on the endpoint configuration
            // (index/type) rather than header values. If we do not clear this out
            // sending the same message (index request, for example) to multiple
            // OpenSearch endpoints would have the effect overriding any
            // subsequent endpoint index/type with the first endpoint index/type.
            if (ctx.configIndexName()) {
                message.removeHeader(OpensearchConstants.PARAM_INDEX_NAME);
            }

            if (ctx.configWaitForActiveShards()) {
                message.removeHeader(OpensearchConstants.PARAM_WAIT_FOR_ACTIVE_SHARDS);
            }
            if (configuration.isDisconnect()) {
                IOHelper.close(ctx.transport());
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
                    LOG.info("Connecting to the OpenSearch cluster: {}", configuration.getClusterName());
                    if (configuration.getHostAddressesList() != null
                            && !configuration.getHostAddressesList().isEmpty()) {
                        client = createClient();
                    } else {
                        LOG.warn("Incorrect ip address and port parameters settings for OpenSearch cluster");
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
            LOG.info("Disconnecting from OpenSearch cluster: {}", configuration.getClusterName());
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
     * An SSL context based on the self-signed CA, so that using this SSL Context allows to connect to the OpenSearch
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
    private record ActionContext(Exchange exchange, AsyncCallback callback, OpenSearchTransport transport,
            boolean configIndexName, boolean configWaitForActiveShards) {

        OpenSearchAsyncClient getClient() {
            return new OpenSearchAsyncClient(transport);
        }

        Message getMessage() {
            return exchange.getIn();
        }
    }
}
