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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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
    private static final long DISCARD_LOG_INTERVAL = 100;

    private final GoogleFirestoreEndpoint endpoint;
    private ListenerRegistration listenerRegistration;
    private final BlockingQueue<Exchange> pendingExchanges;
    private final AtomicLong discardedChanges = new AtomicLong();

    public GoogleFirestoreConsumer(GoogleFirestoreEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;

        int max = endpoint.getConfiguration().getMaxPendingChanges();
        this.pendingExchanges = max > 0 ? new LinkedBlockingQueue<>(max) : new LinkedBlockingQueue<>();
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

        List<Exchange> remaining = new ArrayList<>();
        pendingExchanges.drainTo(remaining);
        if (!remaining.isEmpty()) {
            LOG.debug("Releasing {} buffered document changes that were not polled", remaining.size());
            remaining.forEach(exchange -> releaseExchange(exchange, false));
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
                            bufferChange(exchange);
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

    /**
     * The document changes buffered by the realtime listener and not yet picked up by a poll.
     */
    BlockingQueue<Exchange> pendingChanges() {
        return pendingExchanges;
    }

    /**
     * Buffers a document change until the next poll picks it up. The listener callback runs on a Firestore client
     * thread, so it must never block waiting for the route to catch up: when the buffer is bounded and full, the oldest
     * buffered change is discarded instead, leaving the route with the most recent state of the collection.
     */
    void bufferChange(Exchange exchange) {
        while (!pendingExchanges.offer(exchange)) {
            Exchange discarded = pendingExchanges.poll();
            if (discarded == null) {
                // the poll drained the buffer in the meantime, so there is room again
                continue;
            }

            releaseExchange(discarded, false);
            long total = discardedChanges.incrementAndGet();
            if (total == 1 || total % DISCARD_LOG_INTERVAL == 0) {
                LOG.warn("The realtime buffer of collection {} is full (maxPendingChanges={}), discarding the oldest"
                         + " buffered change. {} changes discarded so far. Raise maxPendingChanges, or make the route"
                         + " consume faster.",
                        endpoint.getConfiguration().getCollectionName(),
                        endpoint.getConfiguration().getMaxPendingChanges(), total);
            }
        }
    }

    @Override
    protected int poll() throws Exception {
        Queue<Exchange> exchanges;

        if (endpoint.getConfiguration().isRealtimeUpdates()) {
            // Drain pending exchanges from realtime listener
            exchanges = new LinkedList<>();
            pendingExchanges.drainTo(exchanges);
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

        // the batch is interrupted when the consumer is stopping, so release what is left over
        Exchange remaining;
        while ((remaining = (Exchange) exchanges.poll()) != null) {
            releaseExchange(remaining, false);
        }

        return total;
    }

    @Override
    public GoogleFirestoreEndpoint getEndpoint() {
        return endpoint;
    }
}
