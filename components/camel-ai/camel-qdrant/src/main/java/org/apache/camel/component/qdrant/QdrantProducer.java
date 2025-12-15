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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.WithPayloadSelectorFactory;
import io.qdrant.client.WithVectorsSelectorFactory;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.Points;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.ObjectHelper;

import static io.qdrant.client.QueryFactory.nearest;
import static io.qdrant.client.WithPayloadSelectorFactory.enable;

public class QdrantProducer extends DefaultAsyncProducer {

    private QdrantClient client;
    private ExecutorService executor;

    public QdrantProducer(QdrantEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public QdrantEndpoint getEndpoint() {
        return (QdrantEndpoint) super.getEndpoint();
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        this.client = getEndpoint().getClient();

        this.executor = getCamelContext()
                .getExecutorServiceManager()
                .newSingleThreadExecutor(this, "producer:" + getEndpoint().getId());
    }

    @Override
    public void doStop() throws Exception {
        super.doStop();

        if (this.executor != null) {
            getCamelContext().getExecutorServiceManager().shutdownNow(this.executor);
            this.executor = null;
        }
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        final Message in = exchange.getMessage();
        final QdrantAction action = in.getHeader(QdrantHeaders.ACTION, QdrantAction.class);

        try {
            if (action == null) {
                throw new NoSuchHeaderException("The action is a required header", exchange, QdrantHeaders.ACTION);
            }

            return switch (action) {
                case CREATE_COLLECTION -> createCollection(exchange, callback);
                case DELETE_COLLECTION -> deleteCollection(exchange, callback);
                case UPSERT -> upsert(exchange, callback);
                case RETRIEVE -> retrieve(exchange, callback);
                case DELETE -> delete(exchange, callback);
                case SIMILARITY_SEARCH -> similaritySearch(exchange, callback);
                case COLLECTION_INFO -> collectionInfo(exchange, callback);
                default -> throw new UnsupportedOperationException("Unsupported action: " + action.name());
            };
        } catch (Exception e) {
            exchange.setException(e);

            callback.done(true);

            return true;
        }
    }

    // ***************************************
    //
    // Actions
    //
    // ***************************************

    @SuppressWarnings({ "unchecked" })
    private boolean upsert(Exchange exchange, AsyncCallback callback) throws Exception {
        final String collection = getEndpoint().getCollection();
        final Message in = exchange.getMessage();
        final List<Points.PointStruct> points = in.getMandatoryBody(List.class);

        Points.UpsertPoints value = Points.UpsertPoints.newBuilder()
                .setCollectionName(collection)
                .addAllPoints(points)
                .setWait(true)
                .build();

        call(
                this.client.upsertAsync(value),
                (r, t) -> {
                    if (t != null) {
                        exchange.setException(new QdrantActionException(QdrantAction.UPSERT, t));
                    } else {
                        in.setHeader(QdrantHeaders.OPERATION_ID, r.getOperationId());
                        in.setHeader(QdrantHeaders.OPERATION_STATUS, r.getStatus().name());
                        in.setHeader(QdrantHeaders.OPERATION_STATUS_VALUE, r.getStatus().getNumber());
                    }

                    callback.done(false);
                });

        return false;
    }

    @SuppressWarnings({ "unchecked" })
    private boolean retrieve(Exchange exchange, AsyncCallback callback) throws Exception {
        final String collection = getEndpoint().getCollection();
        final Message in = exchange.getMessage();
        final List<Common.PointId> ids = in.getMandatoryBody(List.class);

        call(
                this.client.retrieveAsync(
                        collection,
                        ids,
                        WithPayloadSelectorFactory.enable(in.getHeader(
                                QdrantHeaders.INCLUDE_PAYLOAD,
                                QdrantHeaders.DEFAULT_INCLUDE_PAYLOAD,
                                boolean.class)),
                        WithVectorsSelectorFactory.enable(in.getHeader(
                                QdrantHeaders.INCLUDE_VECTORS,
                                QdrantHeaders.DEFAULT_INCLUDE_VECTORS,
                                boolean.class)),
                        in.getHeader(
                                QdrantHeaders.READ_CONSISTENCY,
                                Points.ReadConsistency.class)),
                (r, t) -> {
                    if (t != null) {
                        exchange.setException(new QdrantActionException(QdrantAction.RETRIEVE, t));
                    } else {
                        in.setBody(new ArrayList<>(r));
                        in.setHeader(QdrantHeaders.SIZE, r.size());
                    }

                    callback.done(false);
                });

        return false;
    }

    private boolean collectionInfo(Exchange exchange, AsyncCallback callback) throws Exception {
        final String collection = getEndpoint().getCollection();
        final Message in = exchange.getMessage();

        call(
                this.client.getCollectionInfoAsync(collection),

                (r, t) -> {
                    if (t != null) {
                        exchange.setException(new QdrantActionException(QdrantAction.COLLECTION_INFO, t));
                    } else {
                        in.setBody(r);
                    }

                    callback.done(false);
                });

        return false;
    }

    private boolean delete(Exchange exchange, AsyncCallback callback) throws Exception {
        final String collection = getEndpoint().getCollection();
        final Message in = exchange.getMessage();
        final Points.PointsSelector selector = in.getMandatoryBody(Points.PointsSelector.class);

        Points.DeletePoints value = Points.DeletePoints.newBuilder()
                .setCollectionName(collection)
                .setPoints(selector)
                .setWait(true)
                .build();

        call(
                this.client.deleteAsync(value),
                (r, t) -> {
                    if (t != null) {
                        exchange.setException(new QdrantActionException(QdrantAction.DELETE, t));
                    } else {
                        in.setHeader(QdrantHeaders.OPERATION_ID, r.getOperationId());
                        in.setHeader(QdrantHeaders.OPERATION_STATUS, r.getStatus().name());
                        in.setHeader(QdrantHeaders.OPERATION_STATUS_VALUE, r.getStatus().getNumber());
                    }

                    callback.done(false);
                });

        return false;
    }

    private boolean createCollection(Exchange exchange, AsyncCallback callback) throws Exception {
        final Message in = exchange.getMessage();
        final VectorParams body = in.getMandatoryBody(VectorParams.class);
        final String collection = getEndpoint().getCollection();

        call(
                this.client.createCollectionAsync(collection, body),
                (r, t) -> {
                    if (t != null) {
                        exchange.setException(new QdrantActionException(QdrantAction.CREATE_COLLECTION, t));
                    }

                    callback.done(false);
                });

        return false;
    }

    private boolean deleteCollection(Exchange exchange, AsyncCallback callback) {
        final String collection = getEndpoint().getCollection();

        call(
                this.client.deleteCollectionAsync(collection),
                (r, t) -> {
                    if (t != null) {
                        exchange.setException(new QdrantActionException(QdrantAction.DELETE_COLLECTION, t));
                    }

                    callback.done(false);
                });

        return false;
    }

    private boolean similaritySearch(Exchange exchange, AsyncCallback callback) throws Exception {
        final String collection = getEndpoint().getCollection();
        // Vector List
        final Message in = exchange.getMessage();
        Object body = in.getMandatoryBody();

        List<Float> vectors = null;
        if (body instanceof Points.PointStruct) {
            Points.Vectors resultVector = ((Points.PointStruct) body).getVectors();
            vectors = resultVector.getVector().getDense().getDataList();
        } else {
            vectors = in.getMandatoryBody(List.class);
        }

        ObjectHelper.notNull(vectors, "vectors");
        final int maxResults = getEndpoint().getConfiguration().getMaxResults();
        final Common.Filter filter = getEndpoint().getConfiguration().getFilter();
        final Duration timeout = getEndpoint().getConfiguration().getTimeout();

        var queryRequestBuilder = Points.QueryPoints.newBuilder()
                .setCollectionName(collection)
                .setQuery(nearest(vectors))
                .setLimit(maxResults)
                .setWithVectors(WithVectorsSelectorFactory.enable(in.getHeader(
                        QdrantHeaders.INCLUDE_VECTORS,
                        QdrantHeaders.DEFAULT_INCLUDE_VECTORS,
                        boolean.class)))
                .setWithPayload(enable(in.getHeader(
                        QdrantHeaders.INCLUDE_PAYLOAD,
                        QdrantHeaders.DEFAULT_INCLUDE_PAYLOAD,
                        boolean.class)));

        if (filter != null) {
            queryRequestBuilder.setFilter(filter);
        }

        call(
                this.client.queryAsync(queryRequestBuilder.build(), timeout),
                (r, t) -> {
                    if (t != null) {
                        exchange.setException(new QdrantActionException(QdrantAction.SIMILARITY_SEARCH, t));
                    } else {
                        in.setBody(new ArrayList<>(r));
                        in.setHeader(QdrantHeaders.SIZE, r.size());
                    }

                    callback.done(false);
                });

        return false;
    }

    // ***************************************
    //
    // Helpers
    //
    // ***************************************

    private CamelContext getCamelContext() {
        return getEndpoint().getCamelContext();
    }

    private <T> void call(ListenableFuture<T> future, BiConsumer<T, Throwable> consumer) {
        Futures.addCallback(
                future,
                new FutureCallback<T>() {
                    @Override
                    public void onSuccess(T result) {
                        consumer.accept(result, null);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        consumer.accept(null, t);
                    }
                },
                this.executor);
    }
}
