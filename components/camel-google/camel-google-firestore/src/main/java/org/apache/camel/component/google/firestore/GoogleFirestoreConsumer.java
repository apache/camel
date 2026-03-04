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
package org.apache.camel.component.google.firestore;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentChange;
import com.google.cloud.firestore.EventListener;
import com.google.cloud.firestore.FirestoreException;
import com.google.cloud.firestore.ListenerRegistration;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.support.EmptyAsyncCallback;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumer for Google Firestore that can either poll for documents or listen for real-time updates.
 */
public class GoogleFirestoreConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleFirestoreConsumer.class);

    private final GoogleFirestoreEndpoint endpoint;
    private ListenerRegistration listenerRegistration;
    private volatile Queue<Exchange> pendingExchanges;

    public GoogleFirestoreConsumer(GoogleFirestoreEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.pendingExchanges = new LinkedList<>();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (endpoint.getConfiguration().isRealtimeUpdates()) {
            startRealtimeListener();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
            LOG.debug("Realtime listener removed");
        }
        super.doStop();
    }

    private void startRealtimeListener() {
        String collectionName = endpoint.getConfiguration().getCollectionName();
        CollectionReference collection = endpoint.getFirestoreClient().collection(collectionName);

        CountDownLatch latch = new CountDownLatch(1);

        listenerRegistration = collection.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(QuerySnapshot snapshots, FirestoreException e) {
                if (e != null) {
                    LOG.error("Error listening to collection: {}", collectionName, e);
                    return;
                }

                if (snapshots != null) {
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        try {
                            Exchange exchange = createExchangeFromDocument(dc.getDocument(), dc.getType());
                            synchronized (pendingExchanges) {
                                pendingExchanges.add(exchange);
                            }
                        } catch (Exception ex) {
                            LOG.error("Error creating exchange from document change", ex);
                        }
                    }
                }
                latch.countDown();
            }
        });

        try {
            latch.await(30, TimeUnit.SECONDS);
            LOG.debug("Realtime listener started for collection: {}", collectionName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while starting realtime listener");
        }
    }

    @Override
    protected int poll() throws Exception {
        Queue<Exchange> exchanges;

        if (endpoint.getConfiguration().isRealtimeUpdates()) {
            // Get pending exchanges from realtime listener
            synchronized (pendingExchanges) {
                exchanges = new LinkedList<>(pendingExchanges);
                pendingExchanges.clear();
            }
        } else {
            // Poll the collection
            exchanges = pollCollection();
        }

        if (exchanges.isEmpty()) {
            return 0;
        }

        return processBatch(CastUtils.cast(exchanges));
    }

    private Queue<Exchange> pollCollection() throws Exception {
        Queue<Exchange> exchanges = new LinkedList<>();
        String collectionName = endpoint.getConfiguration().getCollectionName();

        CollectionReference collection = endpoint.getFirestoreClient().collection(collectionName);
        QuerySnapshot querySnapshot = collection.get().get();

        for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
            Exchange exchange = createExchangeFromDocument(document, null);
            exchanges.add(exchange);
        }

        LOG.debug("Polled {} documents from collection: {}", exchanges.size(), collectionName);
        return exchanges;
    }

    private Exchange createExchangeFromDocument(QueryDocumentSnapshot document, DocumentChange.Type changeType)
            throws Exception {
        Exchange exchange = createExchange(true);
        Message message = exchange.getIn();

        Map<String, Object> data = document.getData();
        message.setBody(data);

        message.setHeader(GoogleFirestoreConstants.RESPONSE_DOCUMENT_ID, document.getId());
        message.setHeader(GoogleFirestoreConstants.RESPONSE_DOCUMENT_PATH, document.getReference().getPath());
        message.setHeader(GoogleFirestoreConstants.RESPONSE_CREATE_TIME, document.getCreateTime());
        message.setHeader(GoogleFirestoreConstants.RESPONSE_UPDATE_TIME, document.getUpdateTime());
        message.setHeader(GoogleFirestoreConstants.RESPONSE_READ_TIME, document.getReadTime());

        if (changeType != null) {
            message.setHeader("CamelGoogleFirestoreChangeType", changeType.name());
        }

        return exchange;
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            Exchange exchange = (Exchange) exchanges.poll();
            if (exchange == null) {
                break;
            }

            exchange.setProperty(ExchangePropertyKey.BATCH_INDEX, index);
            exchange.setProperty(ExchangePropertyKey.BATCH_SIZE, total);
            exchange.setProperty(ExchangePropertyKey.BATCH_COMPLETE, index == total - 1);

            getAsyncProcessor().process(exchange, EmptyAsyncCallback.get());
        }

        return total;
    }

    @Override
    public GoogleFirestoreEndpoint getEndpoint() {
        return endpoint;
    }
}
