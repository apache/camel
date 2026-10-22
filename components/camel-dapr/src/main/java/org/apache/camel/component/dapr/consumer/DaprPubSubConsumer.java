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
package org.apache.camel.component.dapr.consumer;

import java.io.Closeable;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.dapr.client.DaprClientBuilder;
import io.dapr.client.DaprPreviewClient;
import io.dapr.client.SubscriptionListener;
import io.dapr.client.domain.CloudEvent;
import io.dapr.utils.TypeRef;
import io.grpc.StatusRuntimeException;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.dapr.DaprConfiguration;
import org.apache.camel.component.dapr.DaprConstants;
import org.apache.camel.component.dapr.DaprEndpoint;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

public class DaprPubSubConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(DaprPubSubConsumer.class);
    private static final Duration RESUBSCRIBE_DELAY = Duration.ofSeconds(1);
    private static final Duration MAX_RESUBSCRIBE_DELAY = Duration.ofSeconds(30);
    private final String pubSubName;
    private final String topic;
    // guards the subscription, its listener and the pending resubscribe against doStop
    private final Lock lock = new ReentrantLock();
    private DaprPreviewClient client;
    private boolean closeClient;
    private Closeable subscription;
    private DaprSubscriptionListener currentListener;
    private Disposable pendingResubscribe;
    private volatile long resubscribeDelay = RESUBSCRIBE_DELAY.toMillis();
    // set when the consumer subscribed again after a failure, until the first event of the new stream is received
    private final AtomicBoolean recovering = new AtomicBoolean();

    public DaprPubSubConsumer(final DaprEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
        pubSubName = endpoint.getConfiguration().getPubSubName();
        topic = endpoint.getConfiguration().getTopic();
        client = endpoint.getConfiguration().getPreviewClient();
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (client == null && (ObjectHelper.isEmpty(pubSubName) || ObjectHelper.isEmpty(topic))) {
            throw new IllegalArgumentException("pubSubName and topic are mandatory for subscribe operation");
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOG.debug("Creating connection to Dapr PubSub");

        if (client == null) {
            client = new DaprClientBuilder().buildPreviewClient();
            // created by this consumer: closed when it stops
            closeClient = true;
        }
        lock.lock();
        try {
            resubscribeDelay = RESUBSCRIBE_DELAY.toMillis();
            recovering.set(false);
            subscribe();
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void doStop() throws Exception {
        lock.lock();
        try {
            currentListener = null;
            if (pendingResubscribe != null) {
                pendingResubscribe.dispose();
                pendingResubscribe = null;
            }
            closeSubscription();
        } finally {
            lock.unlock();
        }
        // only close a client that this consumer created: a configured or autowired client can be shared with other
        // endpoints, and is used again when the route is started again
        if (closeClient && client != null) {
            client.close();
            client = null;
            closeClient = false;
        }

        // shutdown camel consumer
        super.doStop();
    }

    // called with the lock held
    private void subscribe() {
        DaprSubscriptionListener listener = new DaprSubscriptionListener();
        currentListener = listener;
        subscription = client.subscribeToEvents(pubSubName, topic, listener, TypeRef.get(byte[].class));
    }

    // called with the lock held
    private void closeSubscription() {
        if (subscription != null) {
            try {
                subscription.close();
            } catch (Exception e) {
                LOG.debug("Error closing the subscription to topic {} of pub/sub {}: {}", topic, pubSubName, e, e);
            }
            subscription = null;
        }
    }

    // called with the lock held
    private void scheduleResubscribe() {
        long delay = resubscribeDelay;
        resubscribeDelay = Math.min(delay * 2, MAX_RESUBSCRIBE_DELAY.toMillis());
        pendingResubscribe = Mono.delay(Duration.ofMillis(delay)).subscribe(tick -> resubscribe());
        LOG.debug("Subscribing again to topic {} of pub/sub {} in {} ms", topic, pubSubName, delay);
    }

    /**
     * The stream of the subscription failed: the Dapr client does not open it again (it only does so when the stream
     * completes), so close the subscription and subscribe again after a delay, else the consumer stays started and
     * receives no further events.
     */
    private void onStreamFailed(DaprSubscriptionListener listener, RuntimeException cause) {
        lock.lock();
        try {
            if (listener != currentListener || !isRunAllowed()) {
                LOG.debug("The subscription to topic {} of pub/sub {} ended while the consumer is stopping: {}",
                        topic, pubSubName, cause.toString());
                return;
            }
            currentListener = null;
            closeSubscription();
            LOG.warn("The subscription to topic {} of pub/sub {} failed: {}. Subscribing again in {} ms",
                    topic, pubSubName, cause, resubscribeDelay);
            LOG.debug("The subscription to topic {} of pub/sub {} failed", topic, pubSubName, cause);
            scheduleResubscribe();
        } finally {
            lock.unlock();
        }
    }

    private void resubscribe() {
        lock.lock();
        try {
            pendingResubscribe = null;
            if (!isRunAllowed()) {
                return;
            }
            try {
                // this only sends the request: the stream is opened by the Dapr client afterwards, and a sidecar that
                // still cannot be reached fails it again through onError
                subscribe();
                recovering.set(true);
                LOG.debug("Sent a new subscription request to topic {} of pub/sub {}", topic, pubSubName);
            } catch (Exception e) {
                currentListener = null;
                getExceptionHandler().handleException(
                        "Error subscribing again to topic " + topic + " of pub/sub " + pubSubName, e);
                scheduleResubscribe();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * The delay before the next attempt to subscribe again (for tests).
     */
    long getResubscribeDelay() {
        return resubscribeDelay;
    }

    public DaprConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public DaprEndpoint getEndpoint() {
        return (DaprEndpoint) super.getEndpoint();
    }

    private Exchange createServiceBusExchange(final CloudEvent<byte[]> cloudEvent) {
        final Exchange exchange = createExchange(true);
        final Message message = exchange.getIn();

        // set body
        message.setBody(cloudEvent.getData());

        // set headers
        message.setHeader(DaprConstants.ID, cloudEvent.getId());
        message.setHeader(DaprConstants.SOURCE, cloudEvent.getSource());
        message.setHeader(DaprConstants.TYPE, cloudEvent.getType());
        message.setHeader(DaprConstants.SPECIFIC_VERSION, cloudEvent.getSpecversion());
        message.setHeader(DaprConstants.DATA_CONTENT_TYPE, cloudEvent.getDatacontenttype());
        message.setHeader(DaprConstants.BINARY_DATA, cloudEvent.getBinaryData());
        message.setHeader(DaprConstants.TIME, cloudEvent.getTime());
        message.setHeader(DaprConstants.TRACE_PARENT, cloudEvent.getTraceParent());
        message.setHeader(DaprConstants.TRACE_STATE, cloudEvent.getTraceState());

        return exchange;
    }

    protected class DaprSubscriptionListener implements SubscriptionListener<byte[]> {

        @Override
        public Mono<Status> onEvent(CloudEvent<byte[]> cloudEvent) {
            // events are delivered again: the next failure of the stream starts with the shortest delay
            resubscribeDelay = RESUBSCRIBE_DELAY.toMillis();
            if (recovering.compareAndSet(true, false)) {
                LOG.info("Receiving events again from topic {} of pub/sub {} after the subscription failed", topic,
                        pubSubName);
            }
            // the status is the acknowledgement of the event: answer it when the exchange is done, and
            // ask Dapr to redeliver the event when the exchange failed (or was marked rollback only) instead of
            // dropping it
            return Mono.create(sink -> {
                final Exchange exchange = createServiceBusExchange(cloudEvent);

                // use default consumer callback
                AsyncCallback cb = defaultConsumerCallback(exchange, true);
                getAsyncProcessor().process(exchange, doneSync -> {
                    Status status = exchange.isFailed() || exchange.isRollbackOnly() || exchange.isRollbackOnlyLast()
                            ? Status.RETRY : Status.SUCCESS;
                    try {
                        cb.done(doneSync);
                    } finally {
                        sink.success(status);
                    }
                });
            });
        }

        @Override
        public void onError(RuntimeException ex) {
            // the Dapr client also calls onError for an event it could not convert or whose processing failed, while
            // the stream stays open; a failed stream (sidecar restarted or not reachable) comes as a gRPC status
            if (ObjectHelper.getException(StatusRuntimeException.class, ex) != null) {
                onStreamFailed(this, ex);
            } else {
                LOG.error("Error from Dapr client: {}", ex.getMessage(), ex);
            }
        }
    }
}
