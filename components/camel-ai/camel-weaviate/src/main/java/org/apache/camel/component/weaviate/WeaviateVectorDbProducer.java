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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.CollectionHandle;
import io.weaviate.client6.v1.api.collections.Vectors;
import io.weaviate.client6.v1.api.collections.WeaviateObject;
import io.weaviate.client6.v1.api.collections.query.Filter;
import io.weaviate.client6.v1.api.collections.query.QueryResponse;
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
        final WeaviateVectorDbAction action = in.getHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.class);

        try {
            if (action == null) {
                throw new NoSuchHeaderException("The action is a required header", exchange, WeaviateVectorDbHeaders.ACTION);
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

        String collectionName = resolveCollectionName(in);

        client.collections.create(collectionName);
        populateResponse(true, exchange);
    }

    private void create(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        List elements = in.getMandatoryBody(List.class);

        String collectionName = resolveCollectionName(in);

        HashMap<String, Object> props = in.getHeader(WeaviateVectorDbHeaders.PROPERTIES, HashMap.class);

        Float[] vectors = (Float[]) elements.toArray(new Float[0]);
        float[] primitiveVectors = toPrimitiveFloatArray(vectors);

        CollectionHandle<Map<String, Object>> collection = client.collections.use(collectionName);

        WeaviateObject<Map<String, Object>> result;
        if (props != null) {
            result = collection.data.insert(props, obj -> obj.vectors(Vectors.of(primitiveVectors)));
        } else {
            result = collection.data.insert(Map.of(), obj -> obj.vectors(Vectors.of(primitiveVectors)));
        }

        populateResponse(result, exchange);
    }

    private void updateById(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        List elements = in.getMandatoryBody(List.class);
        String indexId = ExchangeHelper.getMandatoryHeader(exchange, WeaviateVectorDbHeaders.INDEX_ID, String.class);

        String collectionName = resolveCollectionName(in);

        Float[] vectors = (Float[]) elements.toArray(new Float[0]);
        float[] primitiveVectors = toPrimitiveFloatArray(vectors);
        HashMap<String, Object> props = in.getHeader(WeaviateVectorDbHeaders.PROPERTIES, HashMap.class);

        CollectionHandle<Map<String, Object>> collection = client.collections.use(collectionName);

        boolean updateWithMerge = in.getHeader(WeaviateVectorDbHeaders.UPDATE_WITH_MERGE, true, Boolean.class);
        if (updateWithMerge) {
            collection.data.update(indexId,
                    u -> u.properties(props != null ? props : Map.of()).vectors(Vectors.of(primitiveVectors)));
        } else {
            collection.data.replace(indexId,
                    r -> r.properties(props != null ? props : Map.of()).vectors(Vectors.of(primitiveVectors)));
        }

        populateResponse(true, exchange);
    }

    private void deleteById(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        String indexId = ExchangeHelper.getMandatoryHeader(exchange, WeaviateVectorDbHeaders.INDEX_ID, String.class);

        String collectionName = resolveCollectionName(in);

        CollectionHandle<Map<String, Object>> collection = client.collections.use(collectionName);
        collection.data.deleteMany(Filter.uuid().containsAny(indexId));
        populateResponse(true, exchange);
    }

    private void deleteCollection(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();

        String collectionName = resolveCollectionName(in);

        client.collections.delete(collectionName);
        populateResponse(true, exchange);
    }

    private void query(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        List elements = in.getMandatoryBody(List.class);

        // topK default of 10
        int topK = 10;
        if (in.getHeader(WeaviateVectorDbHeaders.QUERY_TOP_K, Integer.class) != null) {
            topK = in.getHeader(WeaviateVectorDbHeaders.QUERY_TOP_K, Integer.class);
        }

        String collectionName = resolveCollectionName(in);

        Float[] vectors = (Float[]) elements.toArray(new Float[0]);
        float[] primitiveVectors = toPrimitiveFloatArray(vectors);

        CollectionHandle<Map<String, Object>> collection = client.collections.use(collectionName);

        final int limit = topK;

        QueryResponse<Map<String, Object>> result;

        if (in.getHeader(WeaviateVectorDbHeaders.FIELDS, HashMap.class) != null) {
            HashMap<String, Object> fieldToSearch = in.getHeader(WeaviateVectorDbHeaders.FIELDS, HashMap.class);
            String[] returnProps = fieldToSearch.keySet().toArray(new String[0]);

            result = collection.query.nearVector(
                    primitiveVectors,
                    nv -> nv.limit(limit).returnProperties(returnProps));
        } else {
            result = collection.query.nearVector(
                    primitiveVectors,
                    nv -> nv.limit(limit));
        }

        populateResponse(result, exchange);
    }

    private void queryById(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        String indexId = ExchangeHelper.getMandatoryHeader(exchange, WeaviateVectorDbHeaders.INDEX_ID, String.class);

        String collectionName = resolveCollectionName(in);

        CollectionHandle<Map<String, Object>> collection = client.collections.use(collectionName);

        Optional<WeaviateObject<Map<String, Object>>> result = collection.query.fetchObjectById(indexId);

        populateResponse(result, exchange);
    }

    // ***************************************
    //
    // Helpers
    //
    // ***************************************

    private String resolveCollectionName(Message in) {
        if (in.getHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, String.class) != null) {
            return in.getHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, String.class);
        }
        return getEndpoint().getCollection();
    }

    private CamelContext getCamelContext() {
        return getEndpoint().getCamelContext();
    }

    private void populateResponse(Object r, Exchange exchange) {
        Message out = exchange.getMessage();
        out.setBody(r);
    }

    private static float[] toPrimitiveFloatArray(Float[] boxed) {
        float[] result = new float[boxed.length];
        for (int i = 0; i < boxed.length; i++) {
            result[i] = boxed[i];
        }
        return result;
    }
}
