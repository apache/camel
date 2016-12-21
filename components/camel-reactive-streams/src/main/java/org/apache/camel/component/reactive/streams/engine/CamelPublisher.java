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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.reactive.streams.ReactiveStreamsBackpressureStrategy;
import org.apache.camel.component.reactive.streams.ReactiveStreamsComponent;
import org.apache.camel.component.reactive.streams.api.DispatchCallback;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Camel publisher. It forwards Camel exchanges to external reactive-streams subscribers.
 */
public class CamelPublisher implements Publisher<StreamPayload<Exchange>>, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(CamelPublisher.class);

    private ExecutorService workerPool;

    private String name;

    private ReactiveStreamsBackpressureStrategy backpressureStrategy;

    private List<CamelSubscription> subscriptions = new CopyOnWriteArrayList<>();

    public CamelPublisher(ExecutorService workerPool, CamelContext context, String name) {
        this.workerPool = workerPool;
        this.backpressureStrategy = ((ReactiveStreamsComponent) context.getComponent("reactive-streams")).getBackpressureStrategy();
        this.name = name;
    }

    @Override
    public void subscribe(Subscriber<? super StreamPayload<Exchange>> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber must not be null");
        CamelSubscription sub = new CamelSubscription(workerPool, this, this.backpressureStrategy, subscriber);
        this.subscriptions.add(sub);
        subscriber.onSubscribe(sub);
    }

    public void unsubscribe(CamelSubscription subscription) {
        subscriptions.remove(subscription);
    }

    public void publish(StreamPayload<Exchange> data) {
        // freeze the subscriptions
        List<CamelSubscription> subs = new LinkedList<>(subscriptions);
        DispatchCallback<Exchange> originalCallback = data.getCallback();
        if (originalCallback != null && subs.size() > 0) {
            // Notify processing once if multiple subscribers are present
            AtomicInteger counter = new AtomicInteger(0);
            AtomicReference<Throwable> thrown = new AtomicReference<>(null);
            data = new StreamPayload<>(data.getItem(), (ex, error) -> {
                int status = counter.incrementAndGet();
                thrown.compareAndSet(null, error);
                if (status == subs.size()) {
                    originalCallback.processed(ex, thrown.get());
                }
            });
        }

        if (subs.size() > 0) {
            LOG.debug("Exchange published to {} subscriptions for the stream {}: {}", subs.size(), name, data.getItem());
            // at least one subscriber
            for (CamelSubscription sub : subs) {
                sub.publish(data);
            }
        } else {
            data.getCallback().processed(data.getItem(), new IllegalStateException("The stream has no active subscriptions"));
        }
    }

    @Override
    public void close() throws Exception {
        for (CamelSubscription sub : subscriptions) {
            sub.signalCompletion();
        }
        subscriptions.clear();
    }
}
