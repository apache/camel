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
package org.apache.camel.component.reactive.streams;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Suspendable;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Camel reactive-streams consumer.
 * <p/>
 * The items received from the stream are queued and routed by the threads of the consumer. A suspended consumer
 * requests no more items from the stream and routes none of the queued ones (nor the items the publisher still sends
 * for the demand requested before the suspend): they stay queued until the consumer is resumed or stopped. The
 * exchanges being routed when the consumer is suspended complete normally, and the suspend does not wait for them.
 */
public class ReactiveStreamsConsumer extends DefaultConsumer implements Suspendable {

    private static final Logger LOG = LoggerFactory.getLogger(ReactiveStreamsConsumer.class);

    /**
     * The consumer whose thread pool runs the exchange that the current thread is routing. It is only set while the
     * pool thread routes the exchange synchronously.
     */
    private static final ThreadLocal<ReactiveStreamsConsumer> ROUTING = new ThreadLocal<>();

    private final ReactiveStreamsEndpoint endpoint;
    private final CamelReactiveStreamsService service;
    /**
     * The items received from the stream that are not routed yet, in the order they were received. Each one has a task
     * in the thread pool that routes the oldest queued item, unless the consumer is suspended.
     */
    private final Queue<QueuedItem> queued = new ConcurrentLinkedQueue<>();
    private ExecutorService executor;
    private ReactiveStreamsCamelSubscriber subscriber;

    public ReactiveStreamsConsumer(ReactiveStreamsEndpoint endpoint, Processor processor, CamelReactiveStreamsService service) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.service = ObjectHelper.notNull(service, "service");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        int poolSize = endpoint.getConcurrentConsumers();
        if (executor == null) {
            executor = getEndpoint().getCamelContext().getExecutorServiceManager().newFixedThreadPool(this,
                    getEndpoint().getEndpointUri(), poolSize);
        }

        // the items left queued by a stop that timed out are routed now
        scheduleQueuedItems();

        this.subscriber = this.service.attachCamelConsumer(endpoint.getStream(), this);
    }

    @Override
    protected void doSuspend() throws Exception {
        // nothing to wait for: while the consumer is suspended, its subscriber requests no more items from the stream
        // and the queued items are not routed (see routeQueuedItem); the exchanges being routed complete normally
    }

    @Override
    protected void doResume() throws Exception {
        if (executor == null) {
            // suspended while it was not started (before its start or after a stop). Call doStart() directly, not
            // start(): resume() has already set the status to STARTING, so start() would return without starting
            // the consumer. resume() sets the status to STARTED once this returns.
            doStart();
            return;
        }

        // route the items queued while suspended, then request more items from the stream
        scheduleQueuedItems();
        ReactiveStreamsCamelSubscriber current = this.subscriber;
        if (current != null) {
            current.refill();
        }
    }

    @Override
    protected void doStop() throws Exception {
        this.service.detachCamelConsumer(endpoint.getStream());
        this.subscriber = null;

        if (executor != null) {
            // the queued exchanges were already taken from the stream (they cannot be requested again),
            // so let them complete before the processor is stopped; dropping them would also leave
            // them counted as inflight by the subscriber, which would then request less or nothing
            // (the tasks of the items queued while the consumer was suspended did not route them)
            scheduleQueuedItems();
            if (ROUTING.get() == this) {
                // stopped by an exchange of this consumer (for example a route policy): the pool cannot be
                // awaited from one of its own threads, the queued exchanges run after this one
                endpoint.getCamelContext().getExecutorServiceManager().shutdown(executor);
            } else {
                endpoint.getCamelContext().getExecutorServiceManager().shutdownGraceful(executor);
            }
            executor = null;
        }

        super.doStop();
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        exchange.getIn().setHeader(ReactiveStreamsConstants.REACTIVE_STREAMS_EVENT_TYPE, "onNext");
        return doSend(exchange, callback);
    }

    public void onComplete() {
        if (endpoint.isForwardOnComplete()) {
            Exchange exchange = endpoint.createExchange();
            exchange.getIn().setHeader(ReactiveStreamsConstants.REACTIVE_STREAMS_EVENT_TYPE, "onComplete");

            doSend(exchange, done -> {
            });
        }
    }

    public void onError(Throwable error) {
        if (endpoint.isForwardOnError()) {
            Exchange exchange = endpoint.createExchange();
            exchange.getIn().setHeader(ReactiveStreamsConstants.REACTIVE_STREAMS_EVENT_TYPE, "onError");
            exchange.getIn().setBody(error);

            doSend(exchange, done -> {
            });
        }
    }

    private boolean doSend(Exchange exchange, AsyncCallback callback) {
        ExecutorService executorService = this.executor;
        if (executorService != null && this.isRunAllowed()) {

            queued.add(new QueuedItem(exchange, callback));
            executorService.execute(this::routeQueuedItem);
            return false;

        } else {
            LOG.warn("Consumer not ready to process exchanges. The exchange {} will be discarded", exchange);
            callback.done(true);
            return true;
        }
    }

    /**
     * Adds a task per queued item to the thread pool: the tasks of the items queued while the consumer was suspended
     * did not route them. A task that finds no item left does nothing.
     */
    private void scheduleQueuedItems() {
        ExecutorService executorService = this.executor;
        for (int i = queued.size(); i > 0; i--) {
            executorService.execute(this::routeQueuedItem);
        }
    }

    private void routeQueuedItem() {
        if (isSuspendingOrSuspended()) {
            // the item stays queued until the consumer is resumed or stopped
            return;
        }
        QueuedItem item = queued.poll();
        if (item == null) {
            return;
        }

        Exchange exchange = item.exchange();
        ROUTING.set(this);
        try {
            this.getAsyncProcessor().process(exchange, doneSync -> {
                Exception cause = exchange.getException();
                if (cause instanceof RejectedExecutionException && !isRunAllowed()) {
                    // an item queued when the consumer was stopped from one of its own exchanges: one line
                    // per item, as there can be up to maxInflightExchanges of them
                    LOG.warn("Item {} of stream {} not routed as the consumer is stopped",
                            exchange.getExchangeId(), endpoint.getStream());
                } else if (cause != null) {
                    getExceptionHandler().handleException("Error processing exchange", exchange, cause);
                }

                item.callback().done(doneSync);
            });
        } finally {
            ROUTING.remove();
        }
    }

    @Override
    public ReactiveStreamsEndpoint getEndpoint() {
        return endpoint;
    }

    private record QueuedItem(Exchange exchange, AsyncCallback callback) {
    }

}
