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
package org.apache.camel.component.reactive.streams.util;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Exchange;
import org.apache.camel.TypeConversionException;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A publisher that converts Camel {@code Exchange}s into the given type.
 */
public class ConvertingPublisher<R> implements Publisher<R> {

    private static final Logger LOG = LoggerFactory.getLogger(ConvertingPublisher.class);

    private Publisher<Exchange> delegate;

    private Class<R> type;
    private BodyConverter<R> converter;

    public ConvertingPublisher(Publisher<Exchange> delegate, Class<R> type) {
        Objects.requireNonNull(delegate, "delegate publisher cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        this.delegate = delegate;
        this.type = type;
        this.converter = BodyConverter.forType(type);
    }

    @Override
    public void subscribe(Subscriber<? super R> subscriber) {
        delegate.subscribe(new Subscriber<Exchange>() {

            private AtomicBoolean active = new AtomicBoolean(true);
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
            public void onNext(Exchange ex) {
                if (!active.get()) {
                    return;
                }

                R r;
                try {
                    r = converter.apply(ex);
                } catch (TypeConversionException e) {
                    LOG.warn("Unable to convert body to the specified type: {}", type.getName(), e);
                    r = null;
                }

                if (r == null && ex.getIn().getBody() != null) {
                    this.onError(new ClassCastException("Unable to convert body to the specified type: " + type.getName()));

                    active.set(false);
                    subscription.cancel();
                } else {
                    subscriber.onNext(r);
                }


            }

            @Override
            public void onError(Throwable throwable) {
                if (!active.get()) {
                    return;
                }

                subscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
                if (!active.get()) {
                    return;
                }

                subscriber.onComplete();
            }
        });
    }
}
