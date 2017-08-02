/**
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
package org.apache.camel.component.reactive.streams.engine;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.reactive.streams.ReactiveStreamsBackpressureStrategy;
import org.apache.camel.component.reactive.streams.ReactiveStreamsComponent;
import org.apache.camel.component.reactive.streams.ReactiveStreamsEndpoint;
import org.apache.camel.component.reactive.streams.ReactiveStreamsHelper;
import org.apache.camel.component.reactive.streams.ReactiveStreamsProducer;
import org.apache.camel.component.reactive.streams.api.DispatchCallback;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Camel publisher. It forwards Camel exchanges to external reactive-streams subscribers.
 */
public class CamelPublisher implements Publisher<Exchange>, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(CamelPublisher.class);

    private ExecutorService workerPool;

    private String name;

    private ReactiveStreamsBackpressureStrategy backpressureStrategy;

    private List<CamelSubscription> subscriptions = new CopyOnWriteArrayList<>();

    private ReactiveStreamsProducer producer;

    public CamelPublisher(ExecutorService workerPool, CamelContext context, String name) {
        this.workerPool = workerPool;
        this.backpressureStrategy = ((ReactiveStreamsComponent) context.getComponent("reactive-streams")).getBackpressureStrategy();
        this.name = name;
    }

    @Override
    public void subscribe(Subscriber<? super Exchange> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber must not be null");
        CamelSubscription sub = new CamelSubscription(UUID.randomUUID().toString(), workerPool, this, name, this.backpressureStrategy, subscriber);
        this.subscriptions.add(sub);
        subscriber.onSubscribe(sub);
    }

    public void unsubscribe(CamelSubscription subscription) {
        subscriptions.remove(subscription);
    }

    public void publish(Exchange data) {
        // freeze the subscriptions
        List<CamelSubscription> subs = new LinkedList<>(subscriptions);

        DispatchCallback<Exchange> originalCallback = ReactiveStreamsHelper.getCallback(data);
        DispatchCallback<Exchange> callback = originalCallback;
        if (originalCallback != null && subs.size() > 0) {
            // When multiple subscribers have an active subscription,
            // we acknowledge the exchange once it has been delivered to every
            // subscriber (or their subscription is cancelled)
            AtomicInteger counter = new AtomicInteger(subs.size());
            // Use just the first exception in the callback when multiple exceptions are thrown
            AtomicReference<Throwable> thrown = new AtomicReference<>(null);

            callback = ReactiveStreamsHelper.attachCallback(data, (exchange, error) -> {
                thrown.compareAndSet(null, error);
                if (counter.decrementAndGet() == 0) {
                    originalCallback.processed(exchange, thrown.get());
                }
            });
        }

        if (subs.size() > 0) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Exchange published to {} subscriptions for the stream {}: {}", subs.size(), name, data);
            }

            // at least one subscriber
            for (CamelSubscription sub : subs) {
                sub.publish(data);
            }
        } else if (callback != null) {
            callback.processed(data, new IllegalStateException("The stream has no active subscriptions"));
        }
    }


    public void attachProducer(ReactiveStreamsProducer producer) {
        Objects.requireNonNull(producer, "producer cannot be null, use the detach method");
        if (this.producer != null) {
            throw new IllegalStateException("A producer is already attached to the stream '" + name + "'");
        }
        this.producer = producer;

        // Apply endpoint options if available
        ReactiveStreamsEndpoint endpoint = producer.getEndpoint();
        if (endpoint.getBackpressureStrategy() != null) {
            this.backpressureStrategy = endpoint.getBackpressureStrategy();
            for (CamelSubscription sub : this.subscriptions) {
                sub.setBackpressureStrategy(endpoint.getBackpressureStrategy());
            }
        }
    }

    public void detachProducer() {
        this.producer = null;
    }

    @Override
    public void close() throws Exception {
        for (CamelSubscription sub : subscriptions) {
            sub.signalCompletion();
        }
        subscriptions.clear();
    }

    public List<CamelSubscription> getSubscriptions() {
        return subscriptions;
    }

}
