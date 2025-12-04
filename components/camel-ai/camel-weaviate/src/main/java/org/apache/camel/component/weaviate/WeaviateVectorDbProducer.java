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

package org.apache.camel.component.weaviate;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.v1.data.api.ObjectUpdater;
import io.weaviate.client.v1.data.model.WeaviateObject;
import io.weaviate.client.v1.graphql.model.GraphQLResponse;
import io.weaviate.client.v1.graphql.query.argument.NearVectorArgument;
import io.weaviate.client.v1.graphql.query.builder.GetBuilder;
import io.weaviate.client.v1.graphql.query.fields.Field;
import io.weaviate.client.v1.graphql.query.fields.Fields;
import io.weaviate.client.v1.schema.model.WeaviateClass;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.ExchangeHelper;

public class WeaviateVectorDbProducer extends DefaultProducer {
    private WeaviateClient client;
    private ExecutorService executor;

    public WeaviateVectorDbProducer(WeaviateVectorDbEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public WeaviateVectorDbEndpoint getEndpoint() {
        return (WeaviateVectorDbEndpoint) super.getEndpoint();
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        this.client = getEndpoint().getClient();
    }

    @Override
    public void process(Exchange exchange) {
        final Message in = exchange.getMessage();
        final WeaviateVectorDbAction action =
                in.getHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.class);

        try {
            if (action == null) {
                throw new NoSuchHeaderException(
                        "The action is a required header", exchange, WeaviateVectorDbHeaders.ACTION);
            }

            switch (action) {
                case CREATE_COLLECTION:
                    createCollection(exchange);
                    break;
                case CREATE:
                    create(exchange);
                    break;
                case UPDATE_BY_ID:
                    updateById(exchange);
                    break;
                case DELETE_BY_ID:
                    deleteById(exchange);
                    break;
                case DELETE_COLLECTION:
                    deleteCollection(exchange);
                    break;
                case QUERY:
                    query(exchange);
                    break;
                case QUERY_BY_ID:
                    queryById(exchange);
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported action: " + action.name());
            }
        } catch (Exception e) {
            exchange.setException(e);
        }
    }

    // ***************************************
    //
    // Actions
    //
    // ***************************************

    private void createCollection(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();

        String collectionName;
        if (in.getHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, String.class) != null) {
            collectionName = in.getHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, String.class);
        } else {
            collectionName = getEndpoint().getCollection();
        }

        WeaviateClass collection =
                WeaviateClass.builder().className(collectionName).build();

        Result<Boolean> res = client.misc().readyChecker().run();

        Result<Boolean> result =
                client.schema().classCreator().withClass(collection).run();
        populateResponse(result, exchange);
    }

    private void create(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        List elements = in.getMandatoryBody(List.class);

        // Check the headers for a collection name.   Use the endpoint's
        // collection name by default
        String collectionName;
        if (in.getHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, String.class) != null) {
            collectionName = in.getHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, String.class);
        } else {
            collectionName = getEndpoint().getCollection();
        }

        HashMap<String, Object> props = in.getHeader(WeaviateVectorDbHeaders.PROPERTIES, HashMap.class);

        Float[] vectors = (Float[]) elements.toArray(new Float[0]);

        Result<WeaviateObject> result = client.data()
                .creator()
                .withClassName(collectionName)
                .withVector(vectors)
                .withProperties(props)
                .run();

        populateResponse(result, exchange);
    }

    private void updateById(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        List elements = in.getMandatoryBody(List.class);
        String indexId = ExchangeHelper.getMandatoryHeader(exchange, WeaviateVectorDbHeaders.INDEX_ID, String.class);

        // Check the headers for a collection name.   Use the endpoint's
        // collection name by default
        String collectionName;
        if (in.getHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, String.class) != null) {
            collectionName = in.getHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, String.class);
        } else {
            collectionName = getEndpoint().getCollection();
        }

        Float[] vectors = (Float[]) elements.toArray(new Float[0]);
        HashMap<String, Object> props = in.getHeader(WeaviateVectorDbHeaders.PROPERTIES, HashMap.class);

        ObjectUpdater ou = client.data().updater();

        boolean updateWithMerge = in.getHeader(WeaviateVectorDbHeaders.UPDATE_WITH_MERGE, true, Boolean.class);
        if (updateWithMerge) {
            ou.withMerge();
        }

        Result<Boolean> result = ou.withID(indexId)
                .withClassName(collectionName)
                .withVector(vectors)
                .withProperties(props)
                .run();

        populateResponse(result, exchange);
    }

    private void deleteById(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        String indexId = ExchangeHelper.getMandatoryHeader(exchange, WeaviateVectorDbHeaders.INDEX_ID, String.class);

        // Check the headers for a collection name.   Use the endpoint's
        // collection name by default
        String collectionName;
        if (in.getHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, String.class) != null) {
            collectionName = in.getHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, String.class);
        } else {
            collectionName = getEndpoint().getCollection();
        }
        Result<Boolean> result = this.client
                .data()
                .deleter()
                .withClassName(collectionName)
                .withID(indexId)
                .run();
        populateResponse(result, exchange);
    }

    private void deleteCollection(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();

        String collectionName;
        if (in.getHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, String.class) != null) {
            collectionName = in.getHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, String.class);
        } else {
            collectionName = getEndpoint().getCollection();
        }

        Result<Boolean> result = this.client
                .schema()
                .classDeleter()
                .withClassName(collectionName)
                .run();

        populateResponse(result, exchange);
    }

    private void query(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        List elements = in.getMandatoryBody(List.class);

        // topK default of 10
        int topK = 10;
        if (in.getHeader(WeaviateVectorDbHeaders.QUERY_TOP_K, Integer.class) != null) {
            topK = in.getHeader(WeaviateVectorDbHeaders.QUERY_TOP_K, Integer.class);
        }

        // Check the headers for a collection name.   Use the endpoint's
        // collection name by default
        String collectionName;
        if (in.getHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, String.class) != null) {
            collectionName = in.getHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, String.class);
        } else {
            collectionName = getEndpoint().getCollection();
        }

        Float[] vectors = (Float[]) elements.toArray(new Float[0]);

        NearVectorArgument nearVectorArg =
                NearVectorArgument.builder().vector(vectors).build();

        Fields fields;

        if (in.getHeader(WeaviateVectorDbHeaders.FIELDS, HashMap.class) != null) {
            HashMap<String, Object> fieldToSearch = in.getHeader(WeaviateVectorDbHeaders.FIELDS, HashMap.class);

            Field[] fieldArray = fieldToSearch.keySet().stream()
                    .map(k -> Field.builder().name(k).build())
                    .toArray(Field[]::new);
            fields = Fields.builder().fields(fieldArray).build();

        } else {
            fields = Fields.builder().fields(new Field[0]).build();
        }

        String query = GetBuilder.builder()
                .className(collectionName)
                .fields(fields)
                .withNearVectorFilter(nearVectorArg)
                .limit(topK)
                .build()
                .buildQuery();

        Result<GraphQLResponse> result = client.graphQL().raw().withQuery(query).run();
        populateResponse(result, exchange);
    }

    private void queryById(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        String indexId = ExchangeHelper.getMandatoryHeader(exchange, WeaviateVectorDbHeaders.INDEX_ID, String.class);

        // Check the headers for a collection name.   Use the endpoint's
        // collection name by default
        String collectionName;
        if (in.getHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, String.class) != null) {
            collectionName = in.getHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, String.class);
        } else {
            collectionName = getEndpoint().getCollection();
        }

        Result<List<WeaviateObject>> result = client.data()
                .objectsGetter()
                .withClassName(collectionName)
                .withID(indexId)
                .run();

        populateResponse(result, exchange);
    }

    // ***************************************
    //
    // Helpers
    //
    // ***************************************

    private CamelContext getCamelContext() {
        return getEndpoint().getCamelContext();
    }

    private void populateResponse(Result<?> r, Exchange exchange) {
        Message out = exchange.getMessage();
        out.setBody(r);
    }
}
