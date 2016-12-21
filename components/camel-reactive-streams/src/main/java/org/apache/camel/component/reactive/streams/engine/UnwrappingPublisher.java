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

import java.util.Objects;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A publisher that extracts the item from the payload as soon as it is delivered to the subscriber.
 * It calls the dispatch callback if defined.
 */
public class UnwrappingPublisher<R> implements Publisher<R> {

    private static final Logger LOG = LoggerFactory.getLogger(UnwrappingPublisher.class);

    private Publisher<StreamPayload<R>> delegate;

    public UnwrappingPublisher(Publisher<StreamPayload<R>> delegate) {
        Objects.requireNonNull(delegate, "delegate publisher cannot be null");
        this.delegate = delegate;
    }

    @Override
    public void subscribe(Subscriber<? super R> subscriber) {
        delegate.subscribe(new Subscriber<StreamPayload<R>>() {

            private Subscription subscription;

            @Override
            public void onSubscribe(Subscription subscription) {
                if (subscription == null) {
                    throw new NullPointerException("subscription is null");
                }

                if (this.subscription != null) {
                    subscription.cancel();
                } else {
                    this.subscription = subscription;
                    subscriber.onSubscribe(subscription);
                }
            }

            @Override
            public void onNext(StreamPayload<R> payload) {
                Throwable error = null;
                try {
                    subscriber.onNext(payload.getItem());
                } catch (Throwable t) {
                    error = t;
                }

                if (payload.getCallback() != null) {
                    payload.getCallback().processed(payload.getItem(), error);
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
