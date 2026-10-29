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
package org.apache.camel.component.qdrant;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
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

/**
 * Perform operations on the Qdrant Vector Database.
 */
@UriEndpoint(
             firstVersion = "4.5.0",
             scheme = Qdrant.SCHEME,
             title = "Qdrant",
             syntax = "qdrant:collection",
             producerOnly = true,
             category = {
                     Category.DATABASE,
                     Category.AI
             },
             headersClass = QdrantHeaders.class)
@BodyTypes(producerIn = @BodyType(value = { Carrier.POJO, Carrier.LIST, Carrier.NULL },
                                  byOption = {
                                          @BodyType.When(option = "action", value = "CREATE_COLLECTION",
                                                         carriers = Carrier.POJO,
                                                         javaType = "io.qdrant.client.grpc.Collections.VectorParams"),
                                          @BodyType.When(option = "action", value = "DELETE_COLLECTION",
                                                         carriers = Carrier.ANY),
                                          @BodyType.When(option = "action", value = "UPSERT",
                                                         carriers = { Carrier.POJO, Carrier.LIST }),
                                          @BodyType.When(option = "action", value = "RETRIEVE",
                                                         carriers = { Carrier.POJO, Carrier.LIST }),
                                          @BodyType.When(option = "action", value = "DELETE", carriers = Carrier.POJO),
                                          @BodyType.When(option = "action", value = "SIMILARITY_SEARCH",
                                                         carriers = { Carrier.LIST, Carrier.POJO }),
                                          @BodyType.When(option = "action", value = "COLLECTION_INFO",
                                                         carriers = Carrier.ANY) },
                                  description = "By the action in the action option or the CamelQdrantAction header: a VectorParams for"
                                                + " CREATE_COLLECTION, a PointStruct or a List of them for UPSERT, a PointId or a List of"
                                                + " them for RETRIEVE, a PointsSelector, PointId, Filter or Condition for DELETE, and the"
                                                + " query vector as a List of Float, a float array or a PointStruct for SIMILARITY_SEARCH."
                                                + " DELETE_COLLECTION and COLLECTION_INFO do not read the body. Another body fails, such as"
                                                + " a number as the id (use PointIdFactory.id) or a List of Double. The qdrant:embeddings"
                                                + " data type transformer builds the PointStruct from the output of langchain4j-embeddings"),
           producerOut = @BodyType(value = { Carrier.LIST, Carrier.POJO, Carrier.UNCHANGED },
                                   byOption = {
                                           @BodyType.When(option = "action", value = "CREATE_COLLECTION",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "action", value = "DELETE_COLLECTION",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "action", value = "UPSERT", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "action", value = "RETRIEVE", carriers = Carrier.LIST,
                                                          javaType = "java.util.List<io.qdrant.client.grpc.Points.RetrievedPoint>"),
                                           @BodyType.When(option = "action", value = "DELETE", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "action", value = "SIMILARITY_SEARCH",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<io.qdrant.client.grpc.Points.ScoredPoint>"),
                                           @BodyType.When(option = "action", value = "COLLECTION_INFO", carriers = Carrier.POJO,
                                                          javaType = "io.qdrant.client.grpc.Collections.CollectionInfo") },
                                   description = "By the action: a List of RetrievedPoint for RETRIEVE, a List of ScoredPoint for"
                                                 + " SIMILARITY_SEARCH and a CollectionInfo for COLLECTION_INFO. The other actions keep the"
                                                 + " body, UPSERT and DELETE with the operation id and status in headers"))
public class QdrantEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @Metadata(required = true)
    @UriPath(description = "The collection Name")
    private final String collection;

    @UriParam
    private QdrantConfiguration configuration;

    private volatile boolean closeClient;
    private volatile QdrantClient client;

    public QdrantEndpoint(
                          String endpointUri,
                          Component component,
                          String collection,
                          QdrantConfiguration configuration) {

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

    public QdrantConfiguration getConfiguration() {
        return configuration;
    }

    public String getCollection() {
        return collection;
    }

    public QdrantClient getClient() {
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
        return new QdrantProducer(this);
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

    private QdrantClient createClient() {
        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(createChannel(), true);
        builder.withTimeout(configuration.getTimeout());

        if (configuration.getApiKey() != null) {
            builder.withApiKey(configuration.getApiKey());
        }

        return new QdrantClient(builder.build());
    }

    private ManagedChannel createChannel() {
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(configuration.getHost(), configuration.getPort());

        if (configuration.isTls()) {
            builder.useTransportSecurity();
        } else {
            builder.usePlaintext();
        }

        return builder.build();
    }
}
