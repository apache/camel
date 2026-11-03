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

import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.CollectionHandle;
import io.weaviate.client6.v1.api.collections.Vectors;
import io.weaviate.client6.v1.api.collections.WeaviateObject;
import io.weaviate.client6.v1.api.collections.aggregate.AggregateResponse;
import io.weaviate.client6.v1.api.collections.data.InsertManyResponse;
import io.weaviate.client6.v1.api.collections.query.NearVector;
import io.weaviate.client6.v1.api.collections.query.QueryResponse;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.ExchangeHelper;

public class WeaviateVectorDbProducer extends DefaultProducer {
    private static final String VECTOR_TYPES = "a List of numbers, such as Float or Double, a float[] or a double[]";

    private WeaviateClient client;

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
        // the header wins over the action option
        final WeaviateVectorDbAction action
                = in.getHeader(WeaviateVectorDbHeaders.ACTION, getEndpoint().getConfiguration().getAction(),
                        WeaviateVectorDbAction.class);

        try {
            if (action == null) {
                throw new NoSuchHeaderException(
                        "The action is required: set the action option or the header", exchange,
                        WeaviateVectorDbHeaders.ACTION);
            }

            switch (action) {
                case CREATE_COLLECTION:
                    createCollection(exchange);
                    break;
                case CREATE:
                    create(exchange);
                    break;
                case BATCH_CREATE:
                    batchCreate(exchange);
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
                case HYBRID_QUERY:
                    hybridQuery(exchange);
                    break;
                case BM25_QUERY:
                    bm25Query(exchange);
                    break;
                case AGGREGATE:
                    aggregate(exchange);
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
        float[] primitiveVectors = toVector(exchange);

        String collectionName = resolveCollectionName(in);

        HashMap<String, Object> props = in.getHeader(WeaviateVectorDbHeaders.PROPERTIES, HashMap.class);

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
        float[] primitiveVectors = toVector(exchange);
        String indexId = ExchangeHelper.getMandatoryHeader(exchange, WeaviateVectorDbHeaders.INDEX_ID, String.class);

        String collectionName = resolveCollectionName(in);

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
        boolean deleted = collection.data.deleteById(indexId);
        populateResponse(deleted, exchange);
    }

    private void deleteCollection(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();

        String collectionName = resolveCollectionName(in);

        client.collections.delete(collectionName);
        populateResponse(true, exchange);
    }

    private void query(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        float[] primitiveVectors = toVector(exchange);

        int topK = in.getHeader(WeaviateVectorDbHeaders.QUERY_TOP_K, 10, Integer.class);

        String collectionName = resolveCollectionName(in);

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

    @SuppressWarnings("unchecked")
    private void batchCreate(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        List<WeaviateObject<Map<String, Object>>> objects = in.getMandatoryBody(List.class);

        String collectionName = resolveCollectionName(in);

        CollectionHandle<Map<String, Object>> collection = client.collections.use(collectionName);

        InsertManyResponse result = collection.data.insertMany(objects);
        populateResponse(result, exchange);
    }

    @SuppressWarnings("unchecked")
    private void hybridQuery(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        String queryText = in.getMandatoryBody(String.class);

        int topK = in.getHeader(WeaviateVectorDbHeaders.QUERY_TOP_K, 10, Integer.class);
        Float alpha = in.getHeader(WeaviateVectorDbHeaders.HYBRID_ALPHA, Float.class);
        List<?> queryVectorElements = in.getHeader(WeaviateVectorDbHeaders.QUERY_VECTOR, List.class);
        float[] queryVector = queryVectorElements != null ? toPrimitiveFloatArray(queryVectorElements) : null;

        String collectionName = resolveCollectionName(in);

        CollectionHandle<Map<String, Object>> collection = client.collections.use(collectionName);

        HashMap<String, Object> fieldToSearch = in.getHeader(WeaviateVectorDbHeaders.FIELDS, HashMap.class);

        QueryResponse<Map<String, Object>> result = (QueryResponse<Map<String, Object>>) collection.query.hybrid(
                queryText, h -> {
                    h.limit(topK);
                    if (alpha != null) {
                        h.alpha(alpha);
                    }
                    if (queryVector != null) {
                        h.nearVector(NearVector.of(queryVector));
                    }
                    if (fieldToSearch != null) {
                        h.returnProperties(fieldToSearch.keySet().toArray(new String[0]));
                    }
                    return h;
                });

        populateResponse(result, exchange);
    }

    @SuppressWarnings("unchecked")
    private void bm25Query(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        String queryText = in.getMandatoryBody(String.class);

        int topK = in.getHeader(WeaviateVectorDbHeaders.QUERY_TOP_K, 10, Integer.class);

        String collectionName = resolveCollectionName(in);

        CollectionHandle<Map<String, Object>> collection = client.collections.use(collectionName);

        HashMap<String, Object> fieldToSearch = in.getHeader(WeaviateVectorDbHeaders.FIELDS, HashMap.class);

        QueryResponse<Map<String, Object>> result;
        if (fieldToSearch != null) {
            String[] returnProps = fieldToSearch.keySet().toArray(new String[0]);
            result = (QueryResponse<Map<String, Object>>) collection.query.bm25(queryText,
                    b -> b.limit(topK).returnProperties(returnProps));
        } else {
            result = (QueryResponse<Map<String, Object>>) collection.query.bm25(queryText,
                    b -> b.limit(topK));
        }

        populateResponse(result, exchange);
    }

    private void aggregate(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();

        String collectionName = resolveCollectionName(in);

        CollectionHandle<Map<String, Object>> collection = client.collections.use(collectionName);

        AggregateResponse result = (AggregateResponse) collection.aggregate.overAll(
                a -> a.includeTotalCount(true));

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

    private void populateResponse(Object r, Exchange exchange) {
        Message out = exchange.getMessage();
        out.setBody(r);
    }

    /**
     * The vector in the body of CREATE, UPDATE_BY_ID and QUERY: a float[], or a List of numbers, such as the List of
     * Double a JSON array unmarshals to, or another body Camel converts to a List, such as a double[] or a Float[].
     */
    private static float[] toVector(Exchange exchange) throws InvalidPayloadException {
        final Message in = exchange.getMessage();
        if (in.getBody() instanceof float[] vector) {
            return vector;
        }
        List<?> elements = in.getBody(List.class);
        if (elements == null) {
            throw new InvalidPayloadException(exchange, List.class, in, "the vector must be " + VECTOR_TYPES);
        }
        return toPrimitiveFloatArray(elements);
    }

    private static float[] toPrimitiveFloatArray(List<?> elements) {
        float[] result = new float[elements.size()];
        int i = 0;
        for (Object element : elements) {
            if (!(element instanceof Number number)) {
                // the type only, not the value, which comes from the message
                throw new IllegalArgumentException(
                        "Vector element at index " + i + " is "
                                                   + (element == null ? "null" : "a " + element.getClass().getName())
                                                   + ": the vector must be " + VECTOR_TYPES);
            }
            result[i++] = number.floatValue();
        }
        return result;
    }
}
