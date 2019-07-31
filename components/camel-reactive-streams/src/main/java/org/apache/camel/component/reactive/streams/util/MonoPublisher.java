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

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * A publisher that publish a given item, then completes.
 */
public class MonoPublisher<T> implements Publisher<T> {

    private T item;

    public MonoPublisher(T item) {
        this.item = item;
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber must not be null");
        subscriber.onSubscribe(new Subscription() {

            private boolean terminated;

            @Override
            public void request(long l) {
                if (terminated) {
                    // subscription is terminated, ignore
                    return;
                }
                terminated = true;

                if (l <= 0) {
                    subscriber.onError(new IllegalArgumentException("3.9"));
                } else {
                    subscriber.onNext(item);
                    subscriber.onComplete();
                }
            }

            @Override
            public void cancel() {
                terminated = true;
            }
        });
    }
}
