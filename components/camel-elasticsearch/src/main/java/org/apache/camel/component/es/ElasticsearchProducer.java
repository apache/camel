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

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.MgetRequest;
import co.elastic.clients.elasticsearch.core.MsearchRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
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
public class ElasticsearchProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchProducer.class);

    protected final ElasticsearchConfiguration configuration;
    private volatile RestClient client;
    private volatile Sniffer sniffer;

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
                    ElasticsearchConstants.PARAM_OPERATION + " value '" + operationConfig + "' is not supported");
        }
        return operationConfig;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (configuration.isDisconnect() && client == null) {
            startClient();
        }
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        ElasticsearchTransport transport = new RestClientTransport(client, new JacksonJsonpMapper(mapper));
        ElasticsearchClient esClient = new ElasticsearchClient(transport);
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

        switch (operation) {
            case Index: {
                IndexRequest.Builder<?> indexRequestBuilder = message.getBody(IndexRequest.Builder.class);
                message.setBody(esClient.index(indexRequestBuilder.build()).id());
                break;
            }
            case Update: {
                UpdateRequest.Builder updateRequestBuilder = message.getBody(UpdateRequest.Builder.class);
                message.setBody(esClient.update(updateRequestBuilder.build(), documentClass).id());
                break;
            }
            case GetById: {
                GetRequest.Builder getRequestBuilder = message.getBody(GetRequest.Builder.class);
                if (getRequestBuilder == null) {
                    throw new IllegalArgumentException(
                            "Wrong body type. Only String or GetRequest.Builder is allowed as a type");
                }
                message.setBody(esClient.get(getRequestBuilder.build(), documentClass));
                break;
            }
            case Bulk: {
                BulkRequest.Builder bulkRequestBuilder = message.getBody(BulkRequest.Builder.class);
                if (bulkRequestBuilder == null) {
                    throw new IllegalArgumentException(
                            "Wrong body type. Only Iterable or BulkRequest.Builder is allowed as a type");
                }
                message.setBody(esClient.bulk(bulkRequestBuilder.build()).items());
                break;
            }
            case Delete: {
                DeleteRequest.Builder deleteRequestBuilder = message.getBody(DeleteRequest.Builder.class);
                if (deleteRequestBuilder == null) {
                    throw new IllegalArgumentException(
                            "Wrong body type. Only String or DeleteRequest.Builder is allowed as a type");
                }
                message.setBody(esClient.delete(deleteRequestBuilder.build()).result());
                break;
            }
            case DeleteIndex: {
                DeleteIndexRequest.Builder deleteIndexRequestBuilder = message.getBody(DeleteIndexRequest.Builder.class);
                if (deleteIndexRequestBuilder == null) {
                    throw new IllegalArgumentException(
                            "Wrong body type. Only String or DeleteIndexRequest.Builder is allowed as a type");
                }
                message.setBody(esClient.indices().delete(deleteIndexRequestBuilder.build()).acknowledged());
                break;
            }
            case Exists: {
                ExistsRequest.Builder builder = new ExistsRequest.Builder();
                builder.index(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class));
                message.setBody(esClient.indices().exists(builder.build()).value());
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
                    int scrollKeepAliveMs
                            = message.getHeader(PARAM_SCROLL_KEEP_ALIVE_MS, configuration.getScrollKeepAliveMs(),
                                    Integer.class);
                    ElasticsearchScrollRequestIterator<?> scrollRequestIterator = new ElasticsearchScrollRequestIterator<>(
                            searchRequestBuilder, esClient, scrollKeepAliveMs, exchange, documentClass);
                    exchange.getIn().setBody(scrollRequestIterator);
                } else {
                    message.setBody(esClient.search(searchRequestBuilder.build(), documentClass).hits());
                }
                break;
            }
            case MultiSearch: {
                MsearchRequest.Builder msearchRequestBuilder = message.getBody(MsearchRequest.Builder.class);
                if (msearchRequestBuilder == null) {
                    throw new IllegalArgumentException("Wrong body type. Only MsearchRequest.Builder is allowed as a type");
                }
                message.setBody(esClient.msearch(msearchRequestBuilder.build(), documentClass).responses());
                break;
            }
            case MultiGet: {
                MgetRequest.Builder mgetRequestBuilder = message.getBody(MgetRequest.Builder.class);
                if (mgetRequestBuilder == null) {
                    throw new IllegalArgumentException("Wrong body type. Only MgetRequest.Builder is allowed as a type");
                }
                message.setBody(esClient.mget(mgetRequestBuilder.build(), documentClass).docs());
                break;
            }
            case Ping: {
                message.setBody(esClient.ping().value());
                break;
            }
            default: {
                throw new IllegalArgumentException(
                        ElasticsearchConstants.PARAM_OPERATION + " value '" + operation + "' is not supported");
            }
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
            IOHelper.close(transport);
            IOHelper.close(client);
            client = null;
            if (configuration.isEnableSniffer()) {
                IOHelper.close(sniffer);
                sniffer = null;
            }
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
            LOG.info("Connecting to the ElasticSearch cluster: {}", configuration.getClusterName());
            if (configuration.getHostAddressesList() != null
                    && !configuration.getHostAddressesList().isEmpty()) {
                client = createClient();
            } else {
                LOG.warn("Incorrect ip address and port parameters settings for ElasticSearch cluster");
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
            Certificate trustedCa = factory.generateCertificate(
                    new ByteArrayInputStream(Files.readAllBytes(Paths.get(configuration.getCertificatePath()))));
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
}
