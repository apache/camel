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
import io.qdrant.client.grpc.Points;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.support.DefaultAsyncProducer;

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
        final QdrantAction action = in.getHeader(Qdrant.Headers.ACTION, QdrantAction.class);

        try {
            if (action == null) {
                throw new NoSuchHeaderException("The action is a required header", exchange, Qdrant.Headers.ACTION);
            }

            switch (action) {
                case CREATE_COLLECTION:
                    return createCollection(exchange, callback);
                case UPSERT:
                    return upsert(exchange, callback);
                case RETRIEVE:
                    return retrieve(exchange, callback);
                case DELETE:
                    return delete(exchange, callback);
                default:
                    throw new UnsupportedOperationException("Unsupported action: " + action.name());
            }
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
                        in.setHeader(Qdrant.Headers.OPERATION_ID, r.getOperationId());
                        in.setHeader(Qdrant.Headers.OPERATION_STATUS, r.getStatus().name());
                        in.setHeader(Qdrant.Headers.OPERATION_STATUS_VALUE, r.getStatus().getNumber());
                    }

                    callback.done(false);
                });

        return false;
    }

    @SuppressWarnings({ "unchecked" })
    private boolean retrieve(Exchange exchange, AsyncCallback callback) throws Exception {
        final String collection = getEndpoint().getCollection();
        final Message in = exchange.getMessage();
        final List<Points.PointId> ids = in.getMandatoryBody(List.class);

        call(
                this.client.retrieveAsync(
                        collection,
                        ids,
                        WithPayloadSelectorFactory.enable(in.getHeader(
                                Qdrant.Headers.INCLUDE_PAYLOAD,
                                Qdrant.Headers.DEFAULT_INCLUDE_PAYLOAD,
                                boolean.class)),
                        WithVectorsSelectorFactory.enable(in.getHeader(
                                Qdrant.Headers.INCLUDE_VECTORS,
                                Qdrant.Headers.DEFAULT_INCLUDE_VECTORS,
                                boolean.class)),
                        in.getHeader(
                                Qdrant.Headers.READ_CONSISTENCY,
                                Points.ReadConsistency.class)),
                (r, t) -> {
                    if (t != null) {
                        exchange.setException(new QdrantActionException(QdrantAction.RETRIEVE, t));
                    } else {
                        in.setBody(new ArrayList<>(r));
                        in.setHeader(Qdrant.Headers.SIZE, r.size());
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
                        in.setHeader(Qdrant.Headers.OPERATION_ID, r.getOperationId());
                        in.setHeader(Qdrant.Headers.OPERATION_STATUS, r.getStatus().name());
                        in.setHeader(Qdrant.Headers.OPERATION_STATUS_VALUE, r.getStatus().getNumber());
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
