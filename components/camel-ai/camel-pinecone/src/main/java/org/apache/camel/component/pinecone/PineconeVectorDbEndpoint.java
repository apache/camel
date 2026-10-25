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
package org.apache.camel.component.pinecone;

import io.pinecone.clients.Pinecone;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Perform operations on the Pinecone Vector Database.
 */
@UriEndpoint(firstVersion = "4.6.0",
             scheme = PineconeVectorDb.SCHEME,
             title = "Pinecone",
             syntax = "pinecone:collection",
             producerOnly = true,
             category = {
                     Category.DATABASE,
                     Category.AI
             },
             headersClass = PineconeVectorDbHeaders.class)
@BodyTypes(producerIn = @BodyType(value = { Carrier.LIST, Carrier.NULL },
                                  byOption = {
                                          @BodyType.When(option = "action", value = "UPSERT", carriers = Carrier.LIST),
                                          @BodyType.When(option = "action", value = "UPDATE", carriers = Carrier.LIST),
                                          @BodyType.When(option = "action", value = "QUERY", carriers = Carrier.LIST),
                                          @BodyType.When(option = "action", value = "FETCH", carriers = Carrier.LIST),
                                          @BodyType.When(option = "action", value = "DELETE_BY_ID", carriers = Carrier.LIST),
                                          @BodyType.When(option = "action", value = "QUERY_BY_ID", carriers = Carrier.ANY),
                                          @BodyType.When(option = "action", value = "CREATE_COLLECTION",
                                                         carriers = Carrier.ANY),
                                          @BodyType.When(option = "action", value = "CREATE_SERVERLESS_INDEX",
                                                         carriers = Carrier.ANY),
                                          @BodyType.When(option = "action", value = "CREATE_POD_INDEX",
                                                         carriers = Carrier.ANY),
                                          @BodyType.When(option = "action", value = "DELETE_INDEX", carriers = Carrier.ANY),
                                          @BodyType.When(option = "action", value = "DELETE_COLLECTION",
                                                         carriers = Carrier.ANY) },
                                  description = "By the action in the action option or the CamelPineconeAction header: the"
                                                + " vector as a List of Float or a float array for UPSERT, UPDATE and QUERY,"
                                                + " with the id in the CamelPineconeIndexId header, and the ids as a List"
                                                + " of String for FETCH and DELETE_BY_ID. The other actions do not read the"
                                                + " body. Another body fails, a List of Double too: the pinecone:embeddings"
                                                + " data type transformer builds the vector from the output of"
                                                + " langchain4j-embeddings"),
           producerOut = @BodyType(value = { Carrier.POJO, Carrier.UNCHANGED },
                                   byOption = {
                                           @BodyType.When(option = "action", value = "UPSERT", carriers = Carrier.POJO,
                                                          javaType = "io.pinecone.proto.UpsertResponse"),
                                           @BodyType.When(option = "action", value = "UPDATE", carriers = Carrier.POJO,
                                                          javaType = "io.pinecone.proto.UpdateResponse"),
                                           @BodyType.When(option = "action", value = "QUERY", carriers = Carrier.POJO,
                                                          javaType = "io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices"),
                                           @BodyType.When(option = "action", value = "QUERY_BY_ID", carriers = Carrier.POJO,
                                                          javaType = "io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices"),
                                           @BodyType.When(option = "action", value = "FETCH", carriers = Carrier.POJO,
                                                          javaType = "io.pinecone.proto.FetchResponse"),
                                           @BodyType.When(option = "action", value = "DELETE_BY_ID", carriers = Carrier.POJO,
                                                          javaType = "io.pinecone.proto.DeleteResponse"),
                                           @BodyType.When(option = "action", value = "CREATE_COLLECTION",
                                                          carriers = Carrier.POJO,
                                                          javaType = "org.openapitools.db_control.client.model.CollectionModel"),
                                           @BodyType.When(option = "action", value = "CREATE_SERVERLESS_INDEX",
                                                          carriers = Carrier.POJO,
                                                          javaType = "org.openapitools.db_control.client.model.IndexModel"),
                                           @BodyType.When(option = "action", value = "CREATE_POD_INDEX",
                                                          carriers = Carrier.POJO,
                                                          javaType = "org.openapitools.db_control.client.model.IndexModel"),
                                           @BodyType.When(option = "action", value = "DELETE_INDEX",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "action", value = "DELETE_COLLECTION",
                                                          carriers = Carrier.UNCHANGED) },
                                   description = "By the action: the response of the Pinecone SDK, such as an"
                                                 + " UpsertResponse, a FetchResponse or a QueryResponseWithUnsignedIndices"
                                                 + " with the matches. DELETE_INDEX and DELETE_COLLECTION keep the body"))
public class PineconeVectorDbEndpoint extends DefaultEndpoint {

    @Metadata(required = true)
    @UriPath(description = "The collection Name. (Only used by some actions)")
    private final String collection;

    @UriParam
    private PineconeVectorDbConfiguration configuration;

    private Pinecone client;

    public PineconeVectorDbEndpoint(String endpointUri,
                                    Component component,
                                    String collection,
                                    PineconeVectorDbConfiguration configuration) {
        super(endpointUri, component);
        this.collection = collection;
        this.configuration = configuration;
    }

    public PineconeVectorDbConfiguration getConfiguration() {
        return configuration;
    }

    public String getCollection() {
        return collection;
    }

    public Pinecone getClient() {
        lock.lock();
        try {
            if (this.client == null) {
                this.client = this.configuration.getClient();
                if (this.client == null) {
                    this.client = createClient();
                }
            }
            return this.client;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Producer createProducer() throws Exception {
        return new PineconeVectorDbProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer is not implemented for this component");
    }

    private Pinecone createClient() {
        Pinecone.Builder builder = new Pinecone.Builder(configuration.getToken());

        // Check to see if a host is configured
        if (getConfiguration().getHost() != null) {
            builder = builder.withHost(getConfiguration().getHost());
        }

        builder = builder.withTlsEnabled(configuration.isTls());

        // Check to see if a proxyHost/proxyPort pair is configured
        if ((getConfiguration().getProxyHost() != null) &&
                (getConfiguration().getProxyPort() != null)) {
            builder = builder.withProxy(getConfiguration().getProxyHost(),
                    getConfiguration().getProxyPort());
        }

        return builder.build();
    }
}
