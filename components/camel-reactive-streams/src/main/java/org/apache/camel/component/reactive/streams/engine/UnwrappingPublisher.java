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
package org.apache.camel.component.reactive.streams.engine;

import java.util.Objects;

import org.apache.camel.Exchange;
import org.apache.camel.component.reactive.streams.ReactiveStreamsHelper;
import org.apache.camel.component.reactive.streams.api.DispatchCallback;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * A publisher that extracts the item from the payload as soon as it is delivered to the subscriber.
 * It calls the dispatch callback if defined.
 */
public class UnwrappingPublisher implements Publisher<Exchange> {
    private Publisher<Exchange> delegate;

    public UnwrappingPublisher(Publisher<Exchange> delegate) {
        Objects.requireNonNull(delegate, "delegate publisher cannot be null");
        this.delegate = delegate;
    }

    @Override
    public void subscribe(Subscriber<? super Exchange> subscriber) {
        delegate.subscribe(new Subscriber<Exchange>() {

            private Subscription subscription;

            @Override
            public void onSubscribe(Subscription newSubscription) {
                if (newSubscription == null) {
                    throw new NullPointerException("subscription is null");
                } else if (newSubscription == this.subscription) {
                    throw new IllegalArgumentException("already subscribed to the subscription: " + newSubscription);
                }

                if (this.subscription != null) {
                    newSubscription.cancel();
                } else {
                    this.subscription = newSubscription;
                    subscriber.onSubscribe(newSubscription);
                }
            }

            @Override
            public void onNext(Exchange payload) {
                Throwable error = null;
                try {
                    subscriber.onNext(payload);
                } catch (Throwable t) {
                    error = t;
                }

                DispatchCallback<Exchange> callback = ReactiveStreamsHelper.getCallback(payload);
                if (callback != null) {
                    callback.processed(payload, error);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                subscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        });
    }
}
