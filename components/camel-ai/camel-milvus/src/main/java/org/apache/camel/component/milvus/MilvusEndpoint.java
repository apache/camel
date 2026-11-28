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
package org.apache.camel.component.milvus;

import java.util.concurrent.TimeUnit;

import io.milvus.client.MilvusClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Create collections and indexes, and insert, upsert, delete, search and query entities in the Milvus vector database.
 */
@UriEndpoint(
             firstVersion = "4.5.0",
             scheme = Milvus.SCHEME,
             title = "Milvus",
             syntax = "milvus:collection",
             producerOnly = true,
             category = {
                     Category.DATABASE,
                     Category.AI
             },
             headersClass = MilvusHeaders.class)
@BodyTypes(producerIn = @BodyType(value = Carrier.POJO,
                                  byOption = {
                                          @BodyType.When(option = "action", value = "CREATE_COLLECTION",
                                                         carriers = Carrier.POJO,
                                                         javaType = "io.milvus.param.collection.CreateCollectionParam"),
                                          @BodyType.When(option = "action", value = "CREATE_INDEX", carriers = Carrier.POJO,
                                                         javaType = "io.milvus.param.index.CreateIndexParam"),
                                          @BodyType.When(option = "action", value = "UPSERT", carriers = Carrier.POJO,
                                                         javaType = "io.milvus.param.dml.UpsertParam"),
                                          @BodyType.When(option = "action", value = "INSERT", carriers = Carrier.POJO,
                                                         javaType = "io.milvus.param.dml.InsertParam"),
                                          @BodyType.When(option = "action", value = "SEARCH", carriers = Carrier.POJO,
                                                         javaType = "io.milvus.param.highlevel.dml.SearchSimpleParam"),
                                          @BodyType.When(option = "action", value = "QUERY", carriers = Carrier.POJO,
                                                         javaType = "io.milvus.param.dml.QueryParam"),
                                          @BodyType.When(option = "action", value = "DELETE", carriers = Carrier.POJO,
                                                         javaType = "io.milvus.param.dml.DeleteParam") },
                                  description = "The request of the Milvus SDK for the action in the action option or the"
                                                + " CamelMilvusAction header: a CreateCollectionParam, CreateIndexParam,"
                                                + " InsertParam, UpsertParam, SearchSimpleParam, QueryParam or DeleteParam."
                                                + " Another body fails, a vector as a List too: the milvus:embeddings data"
                                                + " type transformer builds the InsertParam or UpsertParam from the output of"
                                                + " langchain4j-embeddings"),
           producerOut = @BodyType(value = Carrier.POJO,
                                   byOption = {
                                           @BodyType.When(option = "action", value = "CREATE_COLLECTION",
                                                          carriers = Carrier.POJO,
                                                          javaType = "io.milvus.param.RpcStatus"),
                                           @BodyType.When(option = "action", value = "CREATE_INDEX", carriers = Carrier.POJO,
                                                          javaType = "io.milvus.param.RpcStatus"),
                                           @BodyType.When(option = "action", value = "UPSERT", carriers = Carrier.POJO,
                                                          javaType = "io.milvus.grpc.MutationResult"),
                                           @BodyType.When(option = "action", value = "INSERT", carriers = Carrier.POJO,
                                                          javaType = "io.milvus.grpc.MutationResult"),
                                           @BodyType.When(option = "action", value = "SEARCH", carriers = Carrier.POJO,
                                                          javaType = "io.milvus.param.highlevel.dml.response.SearchResponse"),
                                           @BodyType.When(option = "action", value = "QUERY", carriers = Carrier.POJO,
                                                          javaType = "io.milvus.grpc.QueryResults"),
                                           @BodyType.When(option = "action", value = "DELETE", carriers = Carrier.POJO,
                                                          javaType = "io.milvus.grpc.MutationResult") },
                                   description = "The result of the Milvus SDK for the action: an RpcStatus for"
                                                 + " CREATE_COLLECTION and CREATE_INDEX, a MutationResult for INSERT, UPSERT"
                                                 + " and DELETE, a SearchResponse for SEARCH and a QueryResults for QUERY"))
public class MilvusEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @Metadata(required = true)
    @UriPath(description = "The name of the collection that the producer loads into memory before SEARCH, QUERY and DELETE."
                           + " The request in the message body names the collection that the action works on, so it must"
                           + " name the same collection.")
    private final String collection;

    @UriParam
    private MilvusConfiguration configuration;

    private volatile boolean closeClient;
    private volatile MilvusClient client;

    public MilvusEndpoint(
                          String endpointUri,
                          Component component,
                          String collection,
                          MilvusConfiguration configuration) {

        super(endpointUri, component);

        this.collection = collection;
        this.configuration = configuration;
    }

    @Override
    public String getServiceUrl() {
        return configuration.getHost() + ":" + configuration.getPort();
    }

    @Override
    public String getServiceProtocol() {
        return "grpc";
    }

    public MilvusConfiguration getConfiguration() {
        return configuration;
    }

    public String getCollection() {
        return collection;
    }

    public MilvusClient getClient() {
        if (this.client == null) {
            lock.lock();
            try {
                if (this.client == null) {
                    this.client = this.configuration.getClient();
                    this.closeClient = false;

                    if (this.client == null) {
                        this.client = createClient();
                        this.closeClient = true;
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        return this.client;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new MilvusProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer is not implemented for this component");
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();
    }

    @Override
    public void doStop() throws Exception {
        super.doStop();

        if (this.client != null && this.closeClient) {
            this.client.close();
            this.client = null;
            this.closeClient = false;
        }
    }

    private MilvusClient createClient() {
        return new MilvusServiceClient(createConnectParam());
    }

    ConnectParam createConnectParam() {
        ConnectParam.Builder parameters = ConnectParam.newBuilder().withHost(configuration.getHost())
                .withPort(configuration.getPort()).withConnectTimeout(configuration.getTimeout(), TimeUnit.MILLISECONDS);

        if (configuration.getToken() != null) {
            parameters.withToken(configuration.getToken());
        }

        parameters.withSecure(configuration.isSecure());

        String caPemPath = configuration.getCaPemPath();
        String clientPemPath = configuration.getClientPemPath();
        String clientKeyPath = configuration.getClientKeyPath();
        boolean mutualTls = ObjectHelper.isNotEmpty(caPemPath) || ObjectHelper.isNotEmpty(clientPemPath)
                || ObjectHelper.isNotEmpty(clientKeyPath);
        if (mutualTls && (ObjectHelper.isEmpty(caPemPath) || ObjectHelper.isEmpty(clientPemPath)
                || ObjectHelper.isEmpty(clientKeyPath))) {
            // the SDK ignores them unless all three are set
            throw new IllegalArgumentException(
                    "The options caPemPath, clientPemPath and clientKeyPath must be set together for mutual TLS");
        }
        if (mutualTls && ObjectHelper.isNotEmpty(configuration.getServerPemPath())) {
            // the SDK uses serverPemPath and ignores the client certificate
            throw new IllegalArgumentException(
                    "The option serverPemPath (one-way TLS) cannot be set together with caPemPath, clientPemPath and"
                                               + " clientKeyPath (mutual TLS)");
        }

        if (ObjectHelper.isNotEmpty(configuration.getServerPemPath())) {
            parameters.withServerPemPath(configuration.getServerPemPath());
            // the SDK always overrides the authority with the server name for one-way TLS
            parameters.withServerName(ObjectHelper.isNotEmpty(configuration.getServerName())
                    ? configuration.getServerName() : configuration.getHost());
        } else if (ObjectHelper.isNotEmpty(configuration.getServerName())) {
            if (!mutualTls) {
                // the SDK ignores it without serverPemPath or mutual TLS
                throw new IllegalArgumentException(
                        "The option serverName can only be used with serverPemPath, or with caPemPath, clientPemPath"
                                                   + " and clientKeyPath");
            }
            parameters.withServerName(configuration.getServerName());
        }
        if (mutualTls) {
            parameters.withCaPemPath(caPemPath).withClientPemPath(clientPemPath).withClientKeyPath(clientKeyPath);
        }

        return parameters.build();
    }
}
